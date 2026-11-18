package com.thinkfree.drive.file.write.service;

import com.thinkfree.drive.resource.dto.file.FileContentSource;
import com.thinkfree.drive.resource.dto.file.FileUploadDto;
import com.thinkfree.drive.resource.enums.ResourceSaveType;
import com.thinkfree.drive.resource.result.ResourceSaveResult;
import com.thinkfree.drive.storage.service.StorageService;

public interface FileWriteService {
    ResourceSaveType supports();
    ResourceSaveResult prepareMeta(FileUploadDto dto, int requestUserSeq);
    void rollback(ResourceSaveResult result);
    void doStorage(FileUploadDto dto, ResourceSaveResult result);

    /**
     * 스토리지 성공 뒤 메타 마무리(예: 새 버전 PENDING → SUCCESS, 한도에서 밀려난 버전 행 삭제). 실패해도 데이터 롤백하지 않는다 —
     * 스토리지에는 이미 새 내용이 있으므로 메타를 되돌리면 둘이 어긋난다. 오케스트레이터가 로그만 남긴다.
     */
    default void afterStorage(ResourceSaveResult result) {
    }

    /** 출처의 내용을 key 에 쓴다 — 업로드 파일·스트림은 upload, 스토리지 객체는 copy(DUPLICATE·OVERWRITE 공용). */
    static void uploadTo(StorageService storageService, FileContentSource source, String key) {
        if (source instanceof FileContentSource.Upload upload) {
            storageService.upload(upload.file(), key);
        } else if (source instanceof FileContentSource.Stream stream) {
            storageService.upload(stream.data(), key);
        } else if (source instanceof FileContentSource.StorageCopy copy) {
            storageService.copy(copy.sourceKey(), key);
        } else {
            throw new IllegalStateException("Unsupported content source: " + source);
        }
    }
}
