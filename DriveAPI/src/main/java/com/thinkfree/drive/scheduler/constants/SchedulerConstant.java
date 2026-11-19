package com.thinkfree.drive.scheduler.constants;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SchedulerConstant {
    
    public static final String CRON_CLEANUP_TRASH = "0 0 5 * * ?";
    public static final String LOCK_CLEANUP_TRASH = "cleanup_trash";
    
    public static final String CRON_CLEANUP_PENDING_DELETE_USERS = "0 1 0 * * ?";
    public static final String LOCK_CLEANUP_PENDING_DELETE_USERS = "cleanup_pending_delete_users";
    
    public static final String CRON_CLEANUP_RESOURCE_LOCKS = "0 * * * * *";
    public static final String LOCK_CLEANUP_RESOURCE_LOCKS = "cleanup_resource_locks";
    public static final int RESOURCE_LOCK_TIMEOUT_SECONDS = 241;
    
    public static final String CRON_CLEANUP_EXPIRED_TOKENS = "0 30 0 * * *";
    public static final String LOCK_CLEANUP_EXPIRED_TOKENS = "cleanup_expired_tokens";
    
    public static final String CRON_CLEANUP_PENDING_MFA = "0 */10 * * * *";
    public static final String LOCK_CLEANUP_PENDING_MFA = "cleanup_pending_mfa";

    public static final String CRON_CLEANUP_OAUTH2_AUTHORIZATIONS = "0 0 2 * * ?";
    public static final String LOCK_CLEANUP_OAUTH2_AUTHORIZATIONS = "cleanup_oauth2_authorizations";

    public static final String CRON_LICENSE_AUTO_AUTH = "0 10 3 * * ?";
    public static final String LOCK_LICENSE_AUTO_AUTH = "license_auto_auth";
    // 테넌트마다 인증 서버를 부르므로 기본 잠금 시간(5분)보다 길게 잡는다
    public static final String LOCK_AT_MOST_LICENSE_AUTO_AUTH = "PT1H";

    // 스토리지 객체 삭제 대기열 — 30초마다(매분 0·30초). 프로퍼티는 테스트 전용 스위치("-" 이면 등록하지 않음 — 테스트가 대기열 행을 직접 확인할 때).
    // 한 실행은 최대 4분(StorageDeletionConstants.RUN_TIME_BUDGET)이라 최대 잠금은 그보다 길게.
    // 최소 잠금은 공통 기본(30초)이면 주기와 같아 한 회차씩 건너뛰므로 이 작업만 10초(WAS 간 시계 차이 대비로는 충분)
    public static final String CRON_STORAGE_DELETION = "${drive.scheduler.storage-deletion.cron:*/30 * * * * *}";
    public static final String LOCK_STORAGE_DELETION = "storage_deletion";
    public static final String LOCK_AT_MOST_STORAGE_DELETION = "PT5M";
    public static final String LOCK_AT_LEAST_STORAGE_DELETION = "PT10S";

    // 업로드 세션 정리 — 10분마다. 상태 변경 + 정리 등록만(S3 호출 없음). 프로퍼티는 테스트 전용 스위치("-" 이면 등록하지 않음)
    public static final String CRON_UPLOAD_SESSION_CLEANUP = "${drive.scheduler.upload-session-cleanup.cron:0 */10 * * * *}";
    public static final String LOCK_UPLOAD_SESSION_CLEANUP = "upload_session_cleanup";

}
