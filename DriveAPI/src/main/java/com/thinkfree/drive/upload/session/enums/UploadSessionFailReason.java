package com.thinkfree.drive.upload.session.enums;

/** FAILED 세션의 사유 — 성공 아닌 끝 상태는 정리 방식이 같아 상태 하나 + 사유로 구분한다. */
public enum UploadSessionFailReason {
    /** 요청·내용 검증 실패(파트 수·크기 불일치 등) */
    VALIDATION,
    /** 커밋 시 용량 초과 */
    QUOTA_EXCEEDED,
    /** 매직바이트가 확장자 계열과 맞지 않음·실행 파일 */
    CONTENT_MISMATCH,
    /** PENDING 이 TTL 동안 활동 없음 */
    EXPIRED,
    /** 사용자 취소 */
    CANCELED,
    /** complete 처리 중 오류·COMPLETING 시간 초과 */
    COMMIT_ERROR,
    /** 스토리지에 멀티파트 업로드가 없음(lifecycle 정리 등) */
    NO_SUCH_UPLOAD,
    /** 세션 생성 중 스토리지 준비 실패 */
    STORAGE_ERROR,
    /** 커밋 직전 권한 재확인 실패 — 업로드하는 동안 공유가 해제됨 등(규칙 14) */
    PERMISSION_DENIED,
    /** 업로드하는 동안 대상 파일(버전·덮어쓰기)이나 부모 폴더(신규)가 삭제·휴지통 이동됨 — 파트 PUT 때 미리 끝낸다 */
    TARGET_DELETED
}
