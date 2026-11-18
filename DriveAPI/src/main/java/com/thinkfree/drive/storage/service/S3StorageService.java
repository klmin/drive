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
    private void copyWithSaveMarker(String key, String newKey, String saveMarker) {
        String contentType = getFileMetadata(key).contentType();
        copy(key, newKey, req -> req.metadataDirective(MetadataDirective.REPLACE)
                                    .contentType(contentType)
                                    .metadata(saveMarkerMetadata(saveMarker)));
    }

    private static Map<String, String> saveMarkerMetadata(String saveMarker) {
        return Map.of(StorageConstants.SAVE_MARKER_METADATA_KEY, saveMarker);
    }

    @Override
    public void copyAndUpload(MultipartFile file, String key, String newKey, String saveMarker) {
        var fileData = toFileData(file);
        copyAndUpload(fileData, key, newKey, saveMarker);
    }

    @Override
    public void copyAndUpload(FileData fileData, String key, String newKey, String saveMarker) {
        copy(key, newKey);
        try {
            upload(fileData, key, saveMarkerMetadata(saveMarker));
        }catch(StorageException e){
            delete(newKey);
            log.error("[Storage copyAndUpload Exception] delete newKey : {}", newKey);
            throw StorageException.type(StorageErrorCode.UPLOAD_FAILED);
        }
    }
    
    @Override
    public void backUpAndRestore(String key, String backUpKey, String restoreKey, String saveMarker) {
        copy(key, backUpKey);
        try {
            copyWithSaveMarker(restoreKey, key, saveMarker);
        }catch(StorageException e){
            delete(backUpKey);
            log.error("[Storage backUpAndRestore Exception] delete backUpKey : {}", backUpKey);
            throw StorageException.type(StorageErrorCode.COPY_FAILED);
        }
    }

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
