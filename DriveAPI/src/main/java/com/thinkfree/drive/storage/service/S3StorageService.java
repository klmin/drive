package com.thinkfree.drive.storage.service;

import com.thinkfree.drive.common.file.model.FileData;
import com.thinkfree.drive.common.file.model.FileMetadata;
import com.thinkfree.drive.storage.condition.ConditionalOnStorageType;
import com.thinkfree.drive.storage.constants.StorageConstants;
import com.thinkfree.drive.storage.enums.StorageType;
import com.thinkfree.drive.storage.exception.StorageErrorCode;
import com.thinkfree.drive.storage.exception.StorageException;
import com.thinkfree.drive.storage.properties.StorageProperty;
import com.thinkfree.drive.storage.result.StorageDeleteObjectsResult;
import com.thinkfree.drive.storage.result.StorageDetailResult;
import com.thinkfree.drive.storage.result.StoragePart;
import com.thinkfree.drive.storage.properties.S3ConnectionProperty;
import com.thinkfree.drive.storage.util.StorageUtils;
import com.thinkfree.drive.util.MaskingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * S3 프로토콜 스토리지(Ceph RGW, RustFS, AWS S3) 공용 구현.
 * 접속 정보는 {@link S3ConnectionProperty}({@code storage.connection.*}) 하나로 받으므로
 * 이 클래스에는 스토리지 종류별 분기가 없다. {@code storage.type}은 라벨로만 쓴다.
 */
@ConditionalOnStorageType({StorageType.CEPH, StorageType.S3, StorageType.RUSTFS})
@Service
@RequiredArgsConstructor
@Slf4j
public class S3StorageService implements StorageService {
    
    private final S3Client s3Client;
    private final S3AsyncClient s3AsyncClient;
    private final S3ConnectionProperty connection;
    private final StorageProperty storageProperty;
    
    @Override
    public long userTotalSize(String userSeqString) {
        
        ListObjectsV2Request listReq = ListObjectsV2Request.builder()
                                                           .bucket(connection.bucketName())
                                                           .prefix(userSeqString + "/")
                                                           .build();
        
        long totalSize = 0;
        
        for (ListObjectsV2Response page : s3Client.listObjectsV2Paginator(listReq)) {
            long pageSize = page.contents().stream()
                    .peek(r -> log.info("Storage Key: {}, LastModified: {}, Size: {}", r.key(), r.lastModified(), r.size()))
                    .mapToLong(S3Object::size)
                    .sum();
            totalSize += pageSize;
        }
        
        log.info("Storage userSeq : {}, total size : {} bytes", userSeqString, totalSize);
        
        return totalSize;
    }
    
    private FileData toFileData(MultipartFile file) {
        try {
            return new FileData(file.getContentType(), file.getInputStream(), file.getSize());
        } catch (IOException e) {
            log.error("[toFileData Exception]", e);
            throw StorageException.type(StorageErrorCode.UPLOAD_FAILED);
        }
    }
    
    @Override
    public void upload(MultipartFile file, String key) {
        var fileData = toFileData(file);
        upload(fileData, key);
    }
    
    @Override
    public void upload(FileData fileData, String key) {
        upload(fileData, key, Map.of());
    }

    private void upload(FileData fileData, String key, Map<String, String> metadata) {

        try (InputStream in = fileData.content()) {

            PutObjectResponse response = s3Client.putObject(
                    b -> b.bucket(connection.bucketName())
                          .key(key)
                          .contentType(fileData.contentType())
                          .metadata(metadata),
                    RequestBody.fromInputStream(in, fileData.contentLength())
            );
            
            int statusCode = response.sdkHttpResponse()
                                     .statusCode();
            log.info("[Storage upload] statusCode: {}", statusCode);
            
            if (!response.sdkHttpResponse().isSuccessful()) {
                log.error("[Storage upload failed] key: {}", key);
                throw StorageException.type(StorageErrorCode.UPLOAD_FAILED);
            }
        }catch(IOException e) {
            log.error("[Storage upload IOException] key: {}", key, e);
            throw StorageException.type(StorageErrorCode.UPLOAD_FAILED);
        }catch(Exception e){
            throw mapAndLog("upload", key, e, StorageErrorCode.UPLOAD_FAILED);
        }
    }
    
    @Override
    public FileMetadata ensureExistsAndGetMetadata(String key, Supplier<FileData> fileData) {
        try {
            return getFileMetadata(key);
        }catch (StorageException e){
            if(StorageErrorCode.NO_SUCH_KEY != e.getCode()){
                throw e;
            }
            upload(fileData.get(), key);
            return getFileMetadata(key);
        }
    }
    
    @Override
    public FileData getFileData(String key) {
        try {
            
            ResponseInputStream<GetObjectResponse> responseStream = s3Client.getObject(
                    req -> req.bucket(connection.bucketName()).key(key)
            );
            
            String contentType = responseStream.response().contentType();
            long contentLength = responseStream.response().contentLength();

            int statusCode = responseStream.response().sdkHttpResponse().statusCode();
            log.info("[Storage getFileData] statusCode : {}", statusCode);

            if (!responseStream.response().sdkHttpResponse().isSuccessful()) {
                log.error("[Storage getFileData is fail] key : {}", key);
                throw StorageException.type(StorageErrorCode.GET_FAILED);
            }
            
            return new FileData(contentType, responseStream, contentLength);
        }catch(Exception e){
            throw mapAndLog("getFileData", key, e, StorageErrorCode.DOWNLOAD_FAILED);
        }
        
    }

    @Override
    public FileMetadata getFileMetadata(String key) {
        try {
            HeadObjectResponse response = s3Client.headObject(
                    req -> req.bucket(connection.bucketName())
                              .key(key)
            );
            
            int statusCode = response.sdkHttpResponse()
                                     .statusCode();
            log.info("[Storage getFileMetadata] statusCode : {}", statusCode);
            return new FileMetadata(response.contentType(), response.contentLength());
        }catch(Exception e){
            throw mapAndLog("getFileMetadata", key, e, StorageErrorCode.GET_FAILED);
        }
    }

    /** 404(객체 없음)는 정상 결과라 empty — 나머지 실패는 GET_FAILED. */
    @Override
    public Optional<Instant> findLastModified(String key) {
        try {
            HeadObjectResponse response = s3Client.headObject(req -> req.bucket(connection.bucketName()).key(key));
            return Optional.ofNullable(response.lastModified());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw mapAndLog("findLastModified", key, e, StorageErrorCode.GET_FAILED);
        } catch (Exception e) {
            throw mapAndLog("findLastModified", key, e, StorageErrorCode.GET_FAILED);
        }
    }

    /** 404(객체 없음)·마커 없음은 empty — 나머지 실패는 GET_FAILED. SDK 는 사용자 메타데이터 키를 접두사 없이 돌려준다. */
    @Override
    public Optional<String> findSaveMarker(String key) {
        try {
            HeadObjectResponse response = s3Client.headObject(req -> req.bucket(connection.bucketName()).key(key));
            return response.metadata().entrySet().stream()
                    .filter(entry -> StorageConstants.SAVE_MARKER_METADATA_KEY.equalsIgnoreCase(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst();
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw mapAndLog("findSaveMarker", key, e, StorageErrorCode.GET_FAILED);
        } catch (Exception e) {
            throw mapAndLog("findSaveMarker", key, e, StorageErrorCode.GET_FAILED);
        }
    }

    @Override
    public void delete(String key) {
        try {

            DeleteObjectResponse response = s3Client.deleteObject(req -> req.bucket(connection.bucketName()).key(key));

            int statusCode = response.sdkHttpResponse().statusCode();
            log.info("[Storage delete] statusCode : {}", statusCode);

            if (!response.sdkHttpResponse().isSuccessful()) {
                log.error("[Storage delete is fail] key : {}", key);
                throw StorageException.type(StorageErrorCode.DELETE_FAILED);
            }
        }catch(Exception e){
            throw mapAndLog("delete", key, e, StorageErrorCode.DELETE_FAILED);
        }
    }
    
    @Override
    public CompletableFuture<Void> deleteAsync(String key){
        return s3AsyncClient.deleteObject(req -> req.bucket(connection.bucketName()).key(key))
                            .thenCompose(response -> {
                                     int statusCode = response.sdkHttpResponse().statusCode();
                                     log.info("[Storage deleteAsync] statusCode: {}", statusCode);
                         
                                     if (!response.sdkHttpResponse().isSuccessful()) {
                                         log.error("[Storage deleteAsync is fail] key: {}", key);
                                         return failedFuture(StorageException.type(StorageErrorCode.DELETE_FAILED));
                                     }
                                     return CompletableFuture.completedFuture(null);
                             })
                            .handle((ok, ex) ->{
                                if (ex != null) {
                                    Throwable cause = unwrapCompletionException(ex);
                                    if (cause instanceof StorageException se0) {
                                        throw new CompletionException(se0);
                                    }
                                    StorageException se = mapAndLog("deleteAsync", key, (Exception) cause, StorageErrorCode.DELETE_FAILED);
                                    throw new CompletionException(se);
                                }
                                return null;
                            });
    }
    
    @Override
    public void deleteObjects(List<String> keys) {
        
        if (keys == null || keys.isEmpty()){
            return;
        }
        
        for (List<String> chunk : StorageUtils.partition(keys, StorageConstants.MAX_DELETE_BATCH_SIZE)) {
            try {
                DeleteObjectsResponse response = s3Client.deleteObjects(
                        req -> req.bucket(connection.bucketName()).delete(createDelete(chunk))
                );
                
                int statusCode = response.sdkHttpResponse().statusCode();
                log.info("[Storage deleteObjects] statusCode: {}, deleted: {}", statusCode, response.deleted().size());
    
                if (!response.sdkHttpResponse().isSuccessful() || response.hasErrors()) {
                    log.error("[Storage deleteObjects failed] chunkSize: {}, errors: {}", chunk.size(), response.errors());
                    deleteS3ErrorLog(response.errors());
                    throw StorageException.type(StorageErrorCode.DELETE_FAILED);
                }
            } catch (Exception e) {
                throw mapAndLog("deleteObjects", "(chunk size : " + chunk.size() + ")", e, StorageErrorCode.DELETE_FAILED);
            }
        }
    }
    
    @Override
    public CompletableFuture<Void> deleteObjectsAsync(List<String> keys) {
        
        if (keys == null || keys.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        List<List<String>> chunks = StorageUtils.partition(keys, StorageConstants.MAX_DELETE_BATCH_SIZE);
        List<CompletableFuture<Void>> futures = new ArrayList<>(chunks.size());
        
        for (List<String> chunk : chunks) {
            
            CompletableFuture<Void> f =
                s3AsyncClient.deleteObjects(req -> req.bucket(connection.bucketName()).delete(createDelete(chunk)))
                             .thenAccept(response -> {
                                 int statusCode = response.sdkHttpResponse().statusCode();
                                 log.info("[Storage deleteObjectsAsync] statusCode: {}, deleted: {}", statusCode, response.deleted().size());
                                 
                                 if (!response.sdkHttpResponse().isSuccessful() || response.hasErrors()) {
                                     log.error("[Storage deleteObjectsAsync failed] size: {}, errors: {}", chunk.size(), response.errors());
                                     deleteS3ErrorLog(response.errors());
                                     throw StorageException.type(StorageErrorCode.DELETE_FAILED);
                                 }
                             })
                             .exceptionally(ex -> {
                                 Throwable cause = unwrapCompletionException(ex);
                                 if (cause instanceof StorageException) {
                                     throw (StorageException) cause;
                                 }
                                 StorageException storageException = mapAndLog("deleteAsync", "(chunk size : " + chunk.size() + ")", (Exception) cause, StorageErrorCode.DELETE_FAILED);
                                 throw new CompletionException(storageException);
                             });

            futures.add(f);
        }
    
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    @Override
    public StorageDeleteObjectsResult deleteObjectsReportingFailures(List<String> keys) {
    
        Map<String, String> failedKeys = new LinkedHashMap<>();
        if (keys == null || keys.isEmpty()) {
            return new StorageDeleteObjectsResult(failedKeys);
        }

        for (List<String> chunk : StorageUtils.partition(keys, StorageConstants.MAX_DELETE_BATCH_SIZE)) {
            try {
                DeleteObjectsResponse response = s3Client.deleteObjects(
                        req -> req.bucket(connection.bucketName()).delete(createDelete(chunk))
                );

                if (!response.sdkHttpResponse().isSuccessful()) {
                    String reason = "HTTP " + response.sdkHttpResponse().statusCode();
                    chunk.forEach(key -> failedKeys.put(key, reason));
                    continue;
                }
                for (S3Error error : response.errors()) {
                    String value = error.code() + ": " + error.message();
                    if (error.key() == null) {
                        // 어느 키의 실패인지 모르면 묶음 전체를 실패로(재시도는 멱등) — null 키를 결과에 넣으면 결과 생성이 실패해 백오프 없이 같은 묶음을 계속 다시 집는다
                        chunk.forEach(key -> failedKeys.putIfAbsent(key, value));
                        continue;
                    }
                    failedKeys.put(error.key(), value);
                }
            } catch (Exception e) {
                String reason = e.getClass().getSimpleName() + ": " + e.getMessage();
                chunk.forEach(key -> failedKeys.put(key, reason));
            }
        }
        return new StorageDeleteObjectsResult(failedKeys);
    }

    @Override
    public void copy(String key, String newKey) {
        copy(key, newKey, UnaryOperator.identity());
    }

    private void copy(String key, String newKey, UnaryOperator<CopyObjectRequest.Builder> options) {
        try {

            CopyObjectResponse response = s3Client.copyObject(
                    req -> options.apply(req
                            .sourceBucket(connection.bucketName())
                            .sourceKey(key)
                            .destinationBucket(connection.bucketName())
                            .destinationKey(newKey))
            );
            
            int statusCode = response.sdkHttpResponse()
                                     .statusCode();
            log.info("[Storage copy] statusCode : {}", statusCode);
            
            if (!response.sdkHttpResponse().isSuccessful()) {
                log.error("[Storage copy is fail] from : {}, to : {}", key, newKey);
                throw StorageException.type(StorageErrorCode.COPY_FAILED);
            }
        }catch(Exception e){
            log.error("[Storage copy Exception] from : {}, to : {}", key, newKey);
            throw mapAndLog("copy", key, e, StorageErrorCode.COPY_FAILED);
        }
    }
    
    /**
     * 복사하면서 사용자 메타데이터를 저장 마커로 바꾼다 — REPLACE 는 Content-Type 도 새로 정해야 해서(안 주면 기본값) 원본의 것을 HEAD 로 읽어 넘긴다.
     */
    @Override
    public StorageDetailResult getStorageInfo() {
        var status = checkStorageHealth();
        var accessKey = MaskingUtils.maskFrom(connection.accessKey(), StorageConstants.ACCESS_KEY_VISIBLE_LENGTH, StorageConstants.MASK_CHAR);
        var secretKey = MaskingUtils.maskAll(connection.secretKey(), StorageConstants.MASK_CHAR);
        return new StorageDetailResult(
                storageProperty.type(),
                accessKey,
                secretKey,
                connection.bucketName(),
                connection.region(),
                connection.endpoint(),
                status
        );
    }
    
    public boolean checkStorageHealth() {
        
        try {
            getFileMetadata(StorageConstants.HEALTH_PROBE_KEY);
            return true;
        } catch (StorageException e) {
            
            if (StorageErrorCode.NO_SUCH_KEY != e.getCode()) {
                return false;
            }
            
            try {
                
                PutObjectResponse response = s3Client.putObject(
                        req -> req.bucket(connection.bucketName())
                                  .contentType(StorageConstants.HEALTH_PROBE_CONTENT_TYPE)
                                  .key(StorageConstants.HEALTH_PROBE_KEY),
                        RequestBody.fromBytes(StorageConstants.HEALTH_PROBE_BYTES)
                );
                
                if (!response.sdkHttpResponse().isSuccessful()) {
                    return false;
                }
                
            } catch (SdkException e1) {
                log.error("[Storage checkStorageHealth SdkException]", e1);
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ 멀티파트(업로드 세션)

    @Override
    public String createMultipartUpload(String key, String contentType) {
        try {
            CreateMultipartUploadResponse response = s3Client.createMultipartUpload(
                    req -> req.bucket(connection.bucketName()).key(key).contentType(contentType));
            log.info("[Storage createMultipartUpload] key: {}", key);
            return response.uploadId();
        } catch (Exception e) {
            throw mapAndLog("createMultipartUpload", key, e, StorageErrorCode.MULTIPART_FAILED);
        }
    }

    @Override
    public String uploadPart(String key, String uploadId, int partNumber, InputStream in, long length) {
        try {
            // 호출자의 스트림(서블릿 입력 등)을 SDK 가 닫거나 length 를 넘어 읽지 않게 감싼다
            UploadPartResponse response = s3Client.uploadPart(
                    req -> req.bucket(connection.bucketName()).key(key).uploadId(uploadId).partNumber(partNumber).contentLength(length),
                    RequestBody.fromInputStream(new BoundedNonClosingInputStream(in, length), length));
            return response.eTag();
        } catch (Exception e) {
            throw mapMultipartAndLog("uploadPart", key, uploadId, e);
        }
    }

    @Override
    public List<StoragePart> listParts(String key, String uploadId) {
        try {
            List<StoragePart> parts = new ArrayList<>();
            for (ListPartsResponse page : s3Client.listPartsPaginator(
                    req -> req.bucket(connection.bucketName()).key(key).uploadId(uploadId))) {
                for (Part part : page.parts()) {
                    parts.add(new StoragePart(part.partNumber(), part.eTag(), part.size()));
                }
            }
            parts.sort(Comparator.comparingInt(StoragePart::partNumber));
            return parts;
        } catch (Exception e) {
            throw mapMultipartAndLog("listParts", key, uploadId, e);
        }
    }

    @Override
    public void completeMultipartUpload(String key, String uploadId, List<StoragePart> parts) {
        try {
            List<CompletedPart> completedParts = parts.stream()
                    .map(p -> CompletedPart.builder().partNumber(p.partNumber()).eTag(p.eTag()).build())
                    .toList();
            s3Client.completeMultipartUpload(req -> req.bucket(connection.bucketName()).key(key).uploadId(uploadId)
                                                       .multipartUpload(m -> m.parts(completedParts)));
            log.info("[Storage completeMultipartUpload] key: {}, parts: {}", key, parts.size());
        } catch (Exception e) {
            throw mapMultipartAndLog("completeMultipartUpload", key, uploadId, e);
        }
    }

    @Override
    public void abortMultipartUpload(String key, String uploadId) {
        try {
            s3Client.abortMultipartUpload(req -> req.bucket(connection.bucketName()).key(key).uploadId(uploadId));
            log.info("[Storage abortMultipartUpload] key: {}", key);
        } catch (Exception e) {
            if (isNoSuchUpload(e)) {
                // 이미 없음 = 목적 달성(멱등)
                log.info("[Storage abortMultipartUpload] already gone. key: {}", key);
                return;
            }
            throw mapAndLog("abortMultipartUpload", key, e, StorageErrorCode.MULTIPART_FAILED);
        }
    }

    @Override
    public List<StoragePart> uploadPartsFromStream(String key, String uploadId, InputStream in, long length, long partSize) {
        if (length <= 0 || partSize <= 0) {
            throw new IllegalArgumentException("length and partSize must be positive: length=" + length + ", partSize=" + partSize);
        }
        long partCount = (length + partSize - 1) / partSize;
        if (partCount > StorageConstants.MAX_PART_COUNT) {
            throw new IllegalArgumentException("too many parts: " + partCount);
        }
        List<StoragePart> parts = new ArrayList<>((int) partCount);
        long remaining = length;
        for (int partNumber = 1; remaining > 0; partNumber++) {
            long partLength = Math.min(partSize, remaining);
            String eTag = uploadPart(key, uploadId, partNumber, in, partLength);
            parts.add(new StoragePart(partNumber, eTag, partLength));
            remaining -= partLength;
        }
        return parts;
    }

    @Override
    public void putObject(String key, String contentType, InputStream in, long length) {
        try {
            s3Client.putObject(req -> req.bucket(connection.bucketName()).key(key).contentType(contentType).contentLength(length),
                               RequestBody.fromInputStream(new BoundedNonClosingInputStream(in, length), length));
            log.info("[Storage putObject] key: {}, length: {}", key, length);
        } catch (Exception e) {
            throw mapAndLog("putObject", key, e, StorageErrorCode.UPLOAD_FAILED);
        }
    }

    @Override
    public long headContentLength(String key) {
        try {
            return s3Client.headObject(req -> req.bucket(connection.bucketName()).key(key)).contentLength();
        } catch (Exception e) {
            throw mapAndLog("headContentLength", key, e, StorageErrorCode.GET_FAILED);
        }
    }

    @Override
    public byte[] readRange(String key, long start, long endInclusive) {
        if (start < 0 || endInclusive < start) {
            throw new IllegalArgumentException("invalid range: " + start + "-" + endInclusive);
        }
        try (ResponseInputStream<GetObjectResponse> in = s3Client.getObject(
                req -> req.bucket(connection.bucketName()).key(key).range("bytes=" + start + "-" + endInclusive))) {
            // 범위가 객체보다 길면 있는 만큼, 0바이트 객체·시작이 끝을 넘으면 416 → 빈 배열
            return in.readNBytes((int) Math.min(endInclusive - start + 1, Integer.MAX_VALUE));
        } catch (S3Exception e) {
            if (e.statusCode() == 416) {
                return new byte[0];
            }
            throw mapAndLog("readRange", key, e, StorageErrorCode.GET_FAILED);
        } catch (Exception e) {
            throw mapAndLog("readRange", key, e, StorageErrorCode.GET_FAILED);
        }
    }

    @Override
    public void copyObjectAnySize(String sourceKey, String targetKey) {
        copyObjectAnySize(sourceKey, targetKey, StorageConstants.MAX_SINGLE_COPY_SIZE, StorageConstants.DEFAULT_COPY_PART_SIZE);
    }

    /** singleCopyLimit 이하면 CopyObject, 넘으면 minCopyPartSize 이상 파트로 UploadPartCopy — 테스트가 작은 값으로 멀티파트 경로를 확인한다. */
    void copyObjectAnySize(String sourceKey, String targetKey, long singleCopyLimit, long minCopyPartSize) {
        HeadObjectResponse head;
        try {
            head = s3Client.headObject(req -> req.bucket(connection.bucketName()).key(sourceKey));
        } catch (Exception e) {
            throw mapAndLog("copyObjectAnySize head", sourceKey, e, StorageErrorCode.COPY_FAILED);
        }
        long size = head.contentLength();
        if (size <= singleCopyLimit) {
            copy(sourceKey, targetKey);
            return;
        }
        long partSize = Math.max(minCopyPartSize,
                                 (size + StorageConstants.MAX_PART_COUNT - 1) / StorageConstants.MAX_PART_COUNT);
        String uploadId = createMultipartUpload(targetKey, head.contentType());
        try {
            List<StoragePart> parts = new ArrayList<>();
            long offset = 0;
            for (int partNumber = 1; offset < size; partNumber++) {
                long end = Math.min(offset + partSize, size) - 1;
                String eTag = uploadPartCopy(sourceKey, targetKey, uploadId, partNumber, offset, end);
                parts.add(new StoragePart(partNumber, eTag, end - offset + 1));
                offset = end + 1;
            }
            completeMultipartUpload(targetKey, uploadId, parts);
            log.info("[Storage copyObjectAnySize multipart] from: {}, to: {}, size: {}, parts: {}", sourceKey, targetKey, size, parts.size());
        } catch (Exception e) {
            try {
                abortMultipartUpload(targetKey, uploadId);
            } catch (RuntimeException abortException) {
                e.addSuppressed(abortException);
            }
            if (e instanceof StorageException se) {
                throw se;
            }
            throw mapAndLog("copyObjectAnySize", targetKey, e, StorageErrorCode.COPY_FAILED);
        }
    }

    @Override
    public String uploadPartCopy(String sourceKey, String targetKey, String uploadId, int partNumber, long start, long endInclusive) {
        try {
            String range = "bytes=" + start + "-" + endInclusive;
            UploadPartCopyResponse response = s3Client.uploadPartCopy(req -> req
                    .sourceBucket(connection.bucketName()).sourceKey(sourceKey)
                    .destinationBucket(connection.bucketName()).destinationKey(targetKey)
                    .uploadId(uploadId).partNumber(partNumber).copySourceRange(range));
            return response.copyPartResult().eTag();
        } catch (Exception e) {
            throw mapMultipartAndLog("uploadPartCopy", targetKey, uploadId, e);
        }
    }

    private StorageException mapMultipartAndLog(String action, String key, String uploadId, Exception e) {
        if (isNoSuchUpload(e)) {
            log.warn("[Storage {} NoSuchUpload] key: {}, uploadId: {}", action, key, uploadId);
            return StorageException.type(StorageErrorCode.NO_SUCH_UPLOAD);
        }
        return mapAndLog(action, key, e, StorageErrorCode.MULTIPART_FAILED);
    }

    /** NoSuchUpload — SDK 전용 예외 또는 S3 호환 스토리지가 주는 오류 코드(404 + NoSuchUpload). */
    static boolean isNoSuchUpload(Throwable e) {
        if (e instanceof NoSuchUploadException) {
            return true;
        }
        if (e instanceof S3Exception s3) {
            return s3.awsErrorDetails() != null && "NoSuchUpload".equals(s3.awsErrorDetails().errorCode());
        }
        return false;
    }

    /** 원본 스트림에서 최대 limit 바이트만 읽고 close 는 원본에 전달하지 않는다 — 한 스트림을 파트별로 잘라 쓰고, 서블릿 입력을 SDK 가 닫지 않게. */
    static final class BoundedNonClosingInputStream extends InputStream {
        private final InputStream in;
        private long remaining;

        BoundedNonClosingInputStream(InputStream in, long limit) {
            this.in = in;
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int b = in.read();
            if (b >= 0) {
                remaining--;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int n = in.read(b, off, (int) Math.min(len, remaining));
            if (n > 0) {
                remaining -= n;
            }
            return n;
        }

        @Override
        public int available() throws IOException {
            return (int) Math.min(in.available(), remaining);
        }

        @Override
        public void close() {
            // 원본은 호출자 소유
        }
    }

    private Delete createDelete(List<String> keys) {
        return Delete.builder().objects(keys.stream()
                                            .map(key -> ObjectIdentifier.builder().key(key).build())
                                            .toList()).build();
    }
    
    private void deleteS3ErrorLog(List<S3Error> s3Errors) {
        for (S3Error error : s3Errors) {
            log.error("key: {}, code: {}, message: {}", error.key(), error.code(), error.message());
        }
    }
    
    private Throwable unwrapCompletionException(Throwable ex) {
        return ex instanceof CompletionException && ex.getCause() != null ? ex.getCause() : ex;
    }
    
    private StorageException mapAndLog(String action, String key, Exception ex, StorageErrorCode defaultCode) {
        if (ex instanceof NoSuchKeyException) {
            log.error("[Storage {} NoSuchKeyException] key: {}, requestId: {}", action, key, ((NoSuchKeyException) ex).requestId());
            return StorageException.type(StorageErrorCode.NO_SUCH_KEY);
        }
        if (ex instanceof S3Exception e) {
            int code = e.statusCode();
            String requestId = e.requestId();
            String extendedRequestId = e.extendedRequestId();
            if (code == 404) {
                log.error("[Storage {} 404 - S3Exception] key: {}, requestId: {}, extId: {}", action, key, requestId, extendedRequestId);
                return StorageException.type(StorageErrorCode.NO_SUCH_KEY);
            }
            log.error("[Storage {} S3Exception] status: {}, key: {}, requestId: {}, extId: {}", action, code, key, requestId, extendedRequestId, e);
            return StorageException.type(defaultCode);
        }
        if (ex instanceof SdkException e) {
            log.error("[Storage {} SdkException] key: {}", action, key, e);
            return StorageException.type(defaultCode);
        }
        log.error("[Storage {} UnknownException] key: {}", action, key, ex);
        return StorageException.type(defaultCode);
    }
    
    private <T> CompletableFuture<T> failedFuture(Throwable t) {
        CompletableFuture<T> f = new CompletableFuture<>();
        f.completeExceptionally(t);
        return f;
    }
    
    
}
