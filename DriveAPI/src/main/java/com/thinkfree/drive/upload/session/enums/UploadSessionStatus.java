package com.thinkfree.drive.upload.session.enums;

/**
 * 업로드 세션 상태(docs/design/upload-session-redesign.md §5.1).
 * <pre>
 * PENDING ──complete 선점──▶ COMPLETING ──커밋 tx──▶ COMPLETED
 *    └──────── 취소·만료 ───────┴── 검증·커밋 실패 ──▶ FAILED (fail_reason)
 * </pre>
 * 상태는 {@code WHERE status = :expected} 조건부 UPDATE 로만 바꾼다. COMPLETED 전환은 리소스 반영과 같은 커밋 tx —
 * 그래서 COMPLETING = 미반영, COMPLETED = 반영이 항상 성립한다.
 */
public enum UploadSessionStatus {
    PENDING, COMPLETING, COMPLETED, FAILED;

    /** 진행 중(진행 중 용량 합계·사용자당 동시 세션 수에 잡힌다) */
    public boolean isActive() {
        return this == PENDING || this == COMPLETING;
    }
}
