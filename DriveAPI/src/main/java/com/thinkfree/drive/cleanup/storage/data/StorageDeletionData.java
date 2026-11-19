package com.thinkfree.drive.cleanup.storage.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 삭제 대기열(tb_storage_deletion) 한 행 — 스케줄러가 처리할 대상. uploadId 는 ABORT_MULTIPART 행에만 있다. */
@Getter
@AllArgsConstructor
public class StorageDeletionData {

    private Long storageDeletionSeq;
    private String storageKey;
    private Integer attemptCount;
    private String uploadId;

    public StorageDeletionData(Long storageDeletionSeq, String storageKey, Integer attemptCount) {
        this(storageDeletionSeq, storageKey, attemptCount, null);
    }
}
