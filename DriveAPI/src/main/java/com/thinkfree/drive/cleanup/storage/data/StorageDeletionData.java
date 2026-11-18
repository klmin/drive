package com.thinkfree.drive.cleanup.storage.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 삭제 대기열(tb_storage_deletion) 한 행 — 스케줄러가 처리할 대상. */
@Getter
@AllArgsConstructor
public class StorageDeletionData {

    private Long storageDeletionSeq;
    private String storageKey;
    private Integer attemptCount;
}
