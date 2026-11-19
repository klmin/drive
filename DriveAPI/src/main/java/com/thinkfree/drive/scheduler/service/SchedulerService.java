package com.thinkfree.drive.scheduler.service;

import com.thinkfree.drive.auditlog.aspect.annotation.AuditLog;
import com.thinkfree.drive.auth.token.service.TokenService;
import com.thinkfree.drive.cleanup.storage.service.StorageDeletionProcessor;
import com.thinkfree.drive.cleanup.trash.service.TrashCleanUpService;
import com.thinkfree.drive.cleanup.user.service.UserPermanentDeletionService;
import com.thinkfree.drive.cleanup.user.service.dto.UserCleanUpDto;
import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.license.auth.service.LicenseAuthService;
import com.thinkfree.drive.mfa.service.MfaService;
import com.thinkfree.drive.resource.query.ResourceLockExpireQuery;
import com.thinkfree.drive.resource.service.ResourceLockUnlockService;
import com.thinkfree.drive.scheduler.annotation.ConditionalOnBatchScheduler;
import com.thinkfree.drive.scheduler.constants.SchedulerConstant;
import com.thinkfree.drive.upload.session.service.UploadSessionCleanupProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

@ConditionalOnBatchScheduler
@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulerService {
    
    private final TrashCleanUpService trashCleanUpService;
    private final UserPermanentDeletionService userPermanentDeletionService;
    private final ResourceLockUnlockService resourceLockUnlockService;
    private final TokenService tokenService;
    private final MfaService mfaService;
    private final LicenseAuthService licenseAuthService;
    private final StorageDeletionProcessor storageDeletionProcessor;
    private final UploadSessionCleanupProcessor uploadSessionCleanupProcessor;

    @Scheduled(cron = SchedulerConstant.CRON_LICENSE_AUTO_AUTH)
    @SchedulerLock(name = SchedulerConstant.LOCK_LICENSE_AUTO_AUTH, lockAtMostFor = SchedulerConstant.LOCK_AT_MOST_LICENSE_AUTO_AUTH)
    public void autoAuthenticateLicenses() {
        runWithSchedulerLog(
                SchedulerConstant.LOCK_LICENSE_AUTO_AUTH,
                licenseAuthService::autoAll
        );
    }

    @AuditLog
    @Scheduled(cron = SchedulerConstant.CRON_CLEANUP_TRASH)
    @SchedulerLock(name = SchedulerConstant.LOCK_CLEANUP_TRASH)
    public void cleanUpTrash() {
        runWithSchedulerLog(
                SchedulerConstant.LOCK_CLEANUP_TRASH,
                trashCleanUpService::cleanUpResourcesByDate
        );
    }

    @AuditLog
    @Scheduled(cron = SchedulerConstant.CRON_CLEANUP_PENDING_DELETE_USERS)
    @SchedulerLock(name=SchedulerConstant.LOCK_CLEANUP_PENDING_DELETE_USERS)
    public void cleanupPendingDeleteUsers() {
        runWithSchedulerLog(
                SchedulerConstant.LOCK_CLEANUP_PENDING_DELETE_USERS,
                () -> {
                    var deleteDueAt = Instant.now();
                    var userCleanUpDto = new UserCleanUpDto(null, deleteDueAt, DriveConstants.SUPER_ADMIN_USER_SEQ);
                    userPermanentDeletionService.deletePermanentlyFromPending(userCleanUpDto);
                }
        );
    }
    
    @Scheduled(cron=SchedulerConstant.CRON_CLEANUP_RESOURCE_LOCKS)
    @SchedulerLock(name=SchedulerConstant.LOCK_CLEANUP_RESOURCE_LOCKS)
    public void cleanupResourceLocks(){
        Instant now = Instant.now();
        Instant threshold = now.minusSeconds(SchedulerConstant.RESOURCE_LOCK_TIMEOUT_SECONDS);
        var resourceLockExpireQuery = new ResourceLockExpireQuery(threshold, DriveConstants.SUPER_ADMIN_USER_SEQ, now);
        var result = resourceLockUnlockService.updateExpiredLocks(resourceLockExpireQuery);
        if(result > 0){
            log.info("[SchedulerService cleanupResourcesLock count : {}]", result);
        }
    }
    
    @Scheduled(cron=SchedulerConstant.CRON_CLEANUP_EXPIRED_TOKENS)
    @SchedulerLock(name=SchedulerConstant.LOCK_CLEANUP_EXPIRED_TOKENS)
    public void cleanupExpiredTokens(){
        runWithSchedulerLog(
                SchedulerConstant.LOCK_CLEANUP_EXPIRED_TOKENS,
                () -> tokenService.deleteExpiredTokens(Instant.now())
        );
    }
    
    @Scheduled(cron = SchedulerConstant.CRON_CLEANUP_PENDING_MFA)
    @SchedulerLock(name = SchedulerConstant.LOCK_CLEANUP_PENDING_MFA)
    public void cleanupPendingMfa() {
        Instant now = Instant.now();
        var result = mfaService.cleanupPendingSetup(now);
        if (result > 0) {
            log.info("[SchedulerService cleanupPendingMfa count : {}]", result);
        }
    }

    /** 스토리지 객체 삭제 대기열(tb_storage_deletion) 처리 — 트랜잭션 없이 호출한다(조회·반영만 처리기 안에서 짧은 트랜잭션). */
    @Scheduled(cron = SchedulerConstant.CRON_STORAGE_DELETION)
    @SchedulerLock(name = SchedulerConstant.LOCK_STORAGE_DELETION,
                   lockAtMostFor = SchedulerConstant.LOCK_AT_MOST_STORAGE_DELETION,
                   lockAtLeastFor = SchedulerConstant.LOCK_AT_LEAST_STORAGE_DELETION)
    public void processStorageDeletions() {
        int result = storageDeletionProcessor.processDue();
        if (result > 0) {
            log.info("[SchedulerService processStorageDeletions count : {}]", result);
        }
    }

    /** 업로드 세션 정리(만료 PENDING·시간 초과 COMPLETING → FAILED + 정리 등록, 오래된 끝난 행 삭제) — 트랜잭션 없이 호출(묶음마다 짧은 트랜잭션). */
    @Scheduled(cron = SchedulerConstant.CRON_UPLOAD_SESSION_CLEANUP)
    @SchedulerLock(name = SchedulerConstant.LOCK_UPLOAD_SESSION_CLEANUP)
    public void cleanupUploadSessions() {
        var result = uploadSessionCleanupProcessor.run();
        if (!result.isEmpty()) {
            log.info("[SchedulerService cleanupUploadSessions] expired: {}, staleCompleting: {}, purged: {}",
                     result.expired(), result.staleCompleting(), result.purged());
        }
    }

    private void runWithSchedulerLog(String jobName, Runnable task) {
        startLog(jobName);
        try {
            task.run();
        } finally {
            endLog(jobName);
        }
    }
    
    private void startLog(String jobName){
        log.info("[SchedulerService - {}] START", jobName);
    }
    private void endLog(String jobName){
        log.info("[SchedulerService - {}] END", jobName);
    }
}
