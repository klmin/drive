package com.thinkfree.drive.upload.session.service;

import com.thinkfree.drive.upload.session.data.UploadSessionCleanupTarget;
import com.thinkfree.drive.upload.session.properties.UploadSessionProperty;
import com.thinkfree.drive.upload.session.repository.UploadSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 세션 정리 한 묶음씩(트랜잭션 하나) — 상태 변경과 정리 등록을 같은 트랜잭션으로 묶는다.
 * 정리 등록이 실패하면 상태 변경도 롤백되어 세션은 그대로, 다음 실행이 다시 한다(반쪽 상태 없음). S3 호출 없음.
 */
@Service
@RequiredArgsConstructor
public class UploadSessionCleanupService {

    private final UploadSessionRepository uploadSessionRepository;
    private final UploadSessionStorageCleanup uploadSessionStorageCleanup;
    private final UploadSessionProperty property;

    /** TTL 지난 PENDING → FAILED(EXPIRED) + ABORT(단일 요청이면 DELETE) 등록. @return 처리한 세션 수 */
    @Transactional
    public int expirePendingBatch() {
        List<UploadSessionCleanupTarget> targets = uploadSessionRepository.expirePending(property.sessionTtl(), property.cleanupBatchSize());
        targets.forEach(uploadSessionStorageCleanup::registerBeforeAssemble);
        return targets.size();
    }

    /** 시간 초과 COMPLETING → FAILED(COMPLETING_TIMEOUT) + ABORT·DELETE 등록(COMPLETING = 미반영이 보장되므로 안전). @return 처리한 세션 수 */
    @Transactional
    public int failStaleCompletingBatch() {
        List<UploadSessionCleanupTarget> targets = uploadSessionRepository.failStaleCompleting(property.completingTimeout(), property.cleanupBatchSize());
        targets.forEach(uploadSessionStorageCleanup::registerAmbiguous);
        return targets.size();
    }

    /** 보관 기간 지난 COMPLETED·FAILED 행 삭제. @return 지운 행 수 */
    @Transactional
    public int purgeFinishedBatch() {
        return uploadSessionRepository.purgeFinished(property.finishedRetention(), property.cleanupBatchSize());
    }
}
