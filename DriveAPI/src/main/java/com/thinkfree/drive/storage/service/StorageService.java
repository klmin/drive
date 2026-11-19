package com.thinkfree.drive.storage.service;

import com.thinkfree.drive.common.file.model.FileData;
import com.thinkfree.drive.common.file.model.FileMetadata;
import com.thinkfree.drive.storage.result.StorageDeleteObjectsResult;
import com.thinkfree.drive.storage.result.StorageDetailResult;
import com.thinkfree.drive.storage.result.StoragePart;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public interface StorageService {
    long userTotalSize(String userSeqString);
    void upload(MultipartFile file, String key);
    void upload(FileData fileData, String key);
    FileMetadata ensureExistsAndGetMetadata(String key, Supplier<FileData> fileData);
    FileData getFileData(String key);
    FileMetadata getFileMetadata(String key);
    /** 객체의 마지막 수정 시각(스토리지 서버 시각) — 객체가 없으면 empty. */
    Optional<Instant> findLastModified(String key);
    /** 옛 버전 저장 흐름이 남긴 저장 마커 — 전환 기간의 미완료 버전 정리(cleanVersionIfExists)만 쓴다. 객체가 없거나 마커가 없으면 empty. */
    Optional<String> findSaveMarker(String key);
    void delete(String key);
    CompletableFuture<Void> deleteAsync(String key);
    void deleteObjects(List<String> keys);
    CompletableFuture<Void> deleteObjectsAsync(List<String> keys);
    /**
     * 키별로 지우고 실패한 키만 돌려준다(삭제 대기열 스케줄러용) — 예외를 던지지 않는다.
     * 묶음 호출 자체가 실패하면(네트워크·HTTP 오류) 그 묶음의 키를 전부 실패로 담는다. 원래 없던 키는 성공으로 본다.
     */
    StorageDeleteObjectsResult deleteObjectsReportingFailures(List<String> keys);
    void copy(String key, String newKey);
    StorageDetailResult getStorageInfo();

    // ------------------------------------------------------------------ 멀티파트(업로드 세션 — docs/design/upload-session-redesign.md §6.5)
    // 모두 DB 트랜잭션 밖에서 부른다. 정리(abort·delete)는 호출자가 직접 하지 않고 세션 상태를 끝내는 쪽이 삭제 대기열에 등록한다.

    /** 멀티파트 업로드를 시작하고 uploadId 를 돌려준다. */
    String createMultipartUpload(String key, String contentType);

    /**
     * 파트 하나를 올린다 — in 에서 정확히 length 바이트를 읽어 그대로 흘린다(메모리·임시 파일에 통째로 담지 않음). 같은 번호 재업로드는 덮어쓴다.
     *
     * @return 파트 ETag
     * @throws com.thinkfree.drive.storage.exception.StorageException 업로드가 없으면 NO_SUCH_UPLOAD
     */
    String uploadPart(String key, String uploadId, int partNumber, InputStream in, long length);

    /**
     * 올라간 파트 전체(파트 번호 오름차순) — 페이지를 끝까지 넘긴다.
     *
     * @throws com.thinkfree.drive.storage.exception.StorageException 업로드가 없으면 NO_SUCH_UPLOAD
     */
    List<StoragePart> listParts(String key, String uploadId);

    /** 파트들을 조립해 객체를 만든다(이 시점에 객체 생성). parts 는 파트 번호 오름차순. */
    void completeMultipartUpload(String key, String uploadId, List<StoragePart> parts);

    /** 멀티파트 업로드를 중단한다 — 업로드가 이미 없으면(NoSuchUpload) 성공으로 본다(멱등). */
    void abortMultipartUpload(String key, String uploadId);

    /**
     * 기존 uploadId 에 스트림을 partSize 씩 잘라 순서대로 올리고 파트 목록을 돌려준다(complete 는 호출자가) — 서버 내부 쓰기(웹오피스·MCP 등)용.
     * 실패해도 abort 하지 않는다(세션 상태를 끝내는 쪽이 정리 등록).
     */
    List<StoragePart> uploadPartsFromStream(String key, String uploadId, InputStream in, long length, long partSize);

    /** 단일 PUT(작은 파일·0바이트) — Content-Type 은 호출자가 정한 값. */
    void putObject(String key, String contentType, InputStream in, long length);

    /** 객체 크기(byte). 없으면 NO_SUCH_KEY. */
    long headContentLength(String key);

    /** 객체의 [start, endInclusive] 범위를 읽는다(매직바이트 검사용 소범위). 객체가 더 짧으면 있는 만큼. */
    byte[] readRange(String key, long start, long endInclusive);

    /**
     * 멀티파트 파트 하나를 다른 객체의 [start, endInclusive] 범위에서 서버측 복사(UploadPartCopy) — 5GiB 초과 복사를 세션이 직접 나눠 할 때.
     *
     * @return 파트 ETag
     * @throws com.thinkfree.drive.storage.exception.StorageException 업로드가 없으면 NO_SUCH_UPLOAD
     */
    String uploadPartCopy(String sourceKey, String targetKey, String uploadId, int partNumber, long start, long endInclusive);

    /**
     * 서버측 복사 — 5GiB 이하는 CopyObject, 넘으면 멀티파트 UploadPartCopy. Content-Type 은 원본 것을 그대로.
     * 대용량 복사 중 실패하면 만든 멀티파트를 바로 abort 한다(세션과 무관한 내부 임시 업로드라 등록할 곳이 없다).
     */
    void copyObjectAnySize(String sourceKey, String targetKey);
}
