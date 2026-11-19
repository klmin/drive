package com.thinkfree.drive.upload.session.service;

import com.thinkfree.drive.cleanup.storage.service.StorageDeletionService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.quota.exception.QuotaErrorCode;
import com.thinkfree.drive.upload.session.data.UploadSessionCleanupTarget;
import com.thinkfree.drive.upload.session.data.UploadSessionData;
import com.thinkfree.drive.upload.session.enums.UploadSessionFailReason;
import com.thinkfree.drive.upload.session.enums.UploadSessionStatus;
import com.thinkfree.drive.upload.session.exception.UploadSessionErrorCode;
import com.thinkfree.drive.upload.session.properties.UploadSessionProperty;
import com.thinkfree.drive.upload.session.query.UploadSessionCreateQuery;
import com.thinkfree.drive.upload.session.repository.UploadLockRepository;
import com.thinkfree.drive.upload.session.repository.UploadSessionRepository;
import com.thinkfree.drive.user.exception.UserErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 세션 상태를 바꾸는 짧은 트랜잭션들(docs/design/upload-session-redesign.md §6.1·§6.3).
 * 모두 REQUIRES_NEW — 호출자에게 트랜잭션이 있어도 즉시 커밋되어야 다음 단계(스토리지 호출)가 트랜잭션 밖에서 돈다(규칙 7).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadSessionTxService {

    private final UploadSessionRepository uploadSessionRepository;
    private final UploadLockRepository uploadLockRepository;
    private final UploadSessionStorageCleanup uploadSessionStorageCleanup;
    private final StorageDeletionService storageDeletionService;
    private final UploadSessionProperty property;

    /**
     * §6.1 ① — 사용자 행 잠금 → 용량 사전 필터(확정 + 진행 중 + 신고 크기 ≤ 한도) → 진행 중 세션 수 → INSERT(PENDING).
     * 최종 판정은 커밋 tx(규칙 11). 사전 필터가 없으면 수 GB 를 올린 뒤에야 실패한다.
     *
     * @throws ApiRuntimeException 용량 부족 IS_FULL, 세션 수 초과 UPLOAD_SESSION_LIMIT, 사용자 없음 USER_NOT_FOUND
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID create(UploadSessionCreateQuery query) {
        var quota = uploadLockRepository.lockUser(query.userSeq())
                                        .orElseThrow(() -> ApiRuntimeException.type(UserErrorCode.USER_NOT_FOUND));
        var active = uploadSessionRepository.sumActive(query.userSeq());
        if (quota.wouldExceed(active.sizeByte() + query.sizeByte())) {
            throw ApiRuntimeException.type(QuotaErrorCode.IS_FULL);
        }
        if (active.count() >= property.maxActiveSessionsPerUser()) {
            throw ApiRuntimeException.type(UploadSessionErrorCode.UPLOAD_SESSION_LIMIT);
        }
        return uploadSessionRepository.insert(query);
    }

    /**
     * §6.1 ③ — 스토리지 준비 결과 기록(PENDING 일 때만).
     *
     * @throws ApiRuntimeException 0행(그 사이 끝남) UPLOAD_SESSION_FAILED
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void attachStorage(UUID uploadSessionId, String storageKey, String uploadId) {
        if (uploadSessionRepository.attachStorage(uploadSessionId, storageKey, uploadId) != 1) {
            throw ApiRuntimeException.type(UploadSessionErrorCode.UPLOAD_SESSION_FAILED);
        }
    }

    /**
     * §6.3 ① — complete 선점(PENDING → COMPLETING).
     *
     * @throws ApiRuntimeException 0행 UPLOAD_SESSION_FAILED
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleting(UUID uploadSessionId, int requestUserSeq) {
        if (uploadSessionRepository.markCompleting(uploadSessionId, requestUserSeq) != 1) {
            throw ApiRuntimeException.type(UploadSessionErrorCode.UPLOAD_SESSION_FAILED);
        }
    }

    /** 정리 등록 시점 — 조립(객체 생성) 전 / 후 / 불명확 */
    public enum CleanupPhase { BEFORE_ASSEMBLE, AFTER_ASSEMBLE, AMBIGUOUS }

    /**
     * §6.3 ⑦ — expected → FAILED + (1행일 때만) 같은 tx 에서 정리 등록(규칙 5).
     *
     * @return 실제로 FAILED 로 바꿨으면 true. false = 이미 다른 상태(예: 커밋 성공 후 예외·ack 유실이면 COMPLETED) — 아무것도 안 한다
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean fail(UUID uploadSessionId, UploadSessionStatus expected, UploadSessionFailReason reason, String lastError, CleanupPhase phase) {
        Optional<UploadSessionCleanupTarget> target = uploadSessionRepository.markFailed(uploadSessionId, expected, reason, lastError);
        if (target.isEmpty()) {
            return false;
        }
        switch (phase) {
            case BEFORE_ASSEMBLE -> uploadSessionStorageCleanup.registerBeforeAssemble(target.get());
            case AFTER_ASSEMBLE -> uploadSessionStorageCleanup.registerAfterAssemble(target.get());
            case AMBIGUOUS -> uploadSessionStorageCleanup.registerAmbiguous(target.get());
        }
        log.warn("[UploadSession failed] id: {}, from: {}, reason: {}, cleanup: {}, error: {}", uploadSessionId, expected, reason, phase, lastError);
        return true;
    }

    /**
     * §6.1 ③ 실패 — createMultipartUpload 는 됐는데 uploadId 를 세션에 못 남겼다. 세션 행에는 uploadId 가 없으므로
     * 받은 값으로 직접 ABORT 를 등록한다(세션이 이미 다른 이유로 끝났어도 이 업로드는 아무도 모르므로 등록).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failWithAbort(UUID uploadSessionId, String storageKey, String uploadId, UploadSessionFailReason reason, String lastError) {
        uploadSessionRepository.markFailed(uploadSessionId, UploadSessionStatus.PENDING, reason, lastError);
        storageDeletionService.registerAbort(storageKey, uploadId);
        log.warn("[UploadSession failed before attach] id: {}, reason: {}, error: {}", uploadSessionId, reason, lastError);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<UploadSessionData> find(UUID uploadSessionId, int requestUserSeq) {
        return uploadSessionRepository.findByIdAndRequester(uploadSessionId, requestUserSeq);
    }
}
