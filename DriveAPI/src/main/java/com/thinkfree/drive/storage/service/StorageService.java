package com.thinkfree.drive.storage.service;

import com.thinkfree.drive.common.file.model.FileData;
import com.thinkfree.drive.common.file.model.FileMetadata;
import com.thinkfree.drive.storage.result.StorageDeleteObjectsResult;
import com.thinkfree.drive.storage.result.StorageDetailResult;
import org.springframework.web.multipart.MultipartFile;

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
    /** 버전 저장이 남긴 저장 마커(copyAndUpload·backUpAndRestore 의 saveMarker) — 객체가 없거나 마커가 없으면 empty. */
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
    /** key 의 현재 내용을 newKey 로 복사한 뒤 key 에 새 내용 — 새 내용에 saveMarker 를 남겨 업로드 완료를 나중에 확인할 수 있게 한다. */
    void copyAndUpload(MultipartFile file, String key, String newKey, String saveMarker);
    void copyAndUpload(FileData fileData, String key, String newKey, String saveMarker);
    /** key 의 현재 내용을 backUpKey 로 복사한 뒤 restoreKey 내용을 key 로 — 복원한 내용에 saveMarker 를 남긴다. */
    void backUpAndRestore(String key, String backUpKey, String restoreKey, String saveMarker);
    StorageDetailResult getStorageInfo();
}
