package com.thinkfree.drive.cleanup.storage.enums;

/** 삭제 대기열(tb_storage_deletion) 행의 정리 동작 — 워커가 동작별로 따로 꺼내 처리한다. */
public enum StorageDeletionAction {
    /** 객체 삭제(DeleteObjects 일괄) */
    DELETE,
    /** 미완성 멀티파트 업로드 중단(행마다 AbortMultipartUpload, NoSuchUpload = 성공) */
    ABORT_MULTIPART
}
