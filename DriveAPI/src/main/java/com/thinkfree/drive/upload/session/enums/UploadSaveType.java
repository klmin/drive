package com.thinkfree.drive.upload.session.enums;

/** 업로드 세션의 저장 유형 — 커밋 tx 의 반영 방식(docs/design/upload-session-redesign.md §4.2). */
public enum UploadSaveType {
    /** 신규(같은 이름이면 넘버링) */
    DUPLICATE,
    /** 현재 내용을 버전으로 남기고 새 내용 */
    VERSION,
    /** 현재 내용을 바꾼다(base key 비교로 충돌 감지) */
    OVERWRITE,
    /** 버전 복원 — 버전 객체를 새 key 로 서버측 복사, 커밋은 VERSION 과 같다 */
    RESTORE
}
