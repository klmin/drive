package com.thinkfree.drive.upload.session.repository;

import com.thinkfree.drive.upload.session.data.UploadSessionActiveUsage;
import com.thinkfree.drive.upload.session.data.UploadSessionCleanupTarget;
import com.thinkfree.drive.upload.session.data.UploadSessionData;
import com.thinkfree.drive.upload.session.enums.UploadSessionFailReason;
import com.thinkfree.drive.upload.session.enums.UploadSessionStatus;
import com.thinkfree.drive.upload.session.query.UploadSessionCreateQuery;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * tb_upload_session 읽기·쓰기. 상태는 {@code WHERE status = :expected} 조건부 UPDATE 로만 바꾸고, 반환 행 수(또는 RETURNING)로 후속 처리를 정한다.
 * 시각은 모두 DB now() — WAS 간 시계 차이를 배제한다.
 */
public interface UploadSessionRepository {

    /** PENDING 으로 넣고 세션 ID(uuidv7) 를 돌려준다. */
    UUID insert(UploadSessionCreateQuery query);

    /** 스토리지 준비 결과를 채운다(PENDING 일 때만). @return 바뀐 행 수 */
    int attachStorage(UUID uploadSessionId, String storageKey, String uploadId);

    /** 요청자 조건 조회 — 다른 사용자의 세션은 없는 것과 같다(존재 숨김). */
    Optional<UploadSessionData> findByIdAndRequester(UUID uploadSessionId, int requestUserSeq);

    /** 요청자의 세션 목록(최근 생성 순). */
    List<UploadSessionData> findByRequesterAndStatus(int requestUserSeq, UploadSessionStatus status, int limit);

    /** 소유자의 진행 중(PENDING·COMPLETING) 세션 합계. */
    UploadSessionActiveUsage sumActive(int userSeq);

    /** last_activity = now() (PENDING 일 때만). @return 바뀐 행 수 — 0 이면 그 사이 끝남 */
    int touch(UUID uploadSessionId);

    /** complete 선점: PENDING → COMPLETING(요청자 조건). @return 바뀐 행 수 */
    int markCompleting(UUID uploadSessionId, int requestUserSeq);

    /** COMPLETING → COMPLETED + 결과(커밋 tx 안에서). @return 바뀐 행 수 */
    int markCompleted(UUID uploadSessionId, long resultResourceSeq, String resultName);

    /**
     * expected → FAILED(reason). 1행이면 정리 대상을, 0행이면 empty 를 돌려준다 — 정리 등록은 1행일 때만.
     */
    Optional<UploadSessionCleanupTarget> markFailed(UUID uploadSessionId, UploadSessionStatus expected, UploadSessionFailReason reason, String lastError);

    /** last_activity 가 ttl 보다 오래된 PENDING 을 limit 개 FAILED(EXPIRED) 로 — 다른 처리와 겹치는 행은 건너뛴다(SKIP LOCKED). */
    List<UploadSessionCleanupTarget> expirePending(Duration ttl, int limit);

    /** completing_at 이 timeout 보다 오래된 COMPLETING 을 limit 개 FAILED(COMPLETING_TIMEOUT) 로. */
    List<UploadSessionCleanupTarget> failStaleCompleting(Duration timeout, int limit);

    /** create_date 가 retention 보다 오래된 COMPLETED·FAILED 행을 limit 개 지운다. @return 지운 행 수 */
    int purgeFinished(Duration retention, int limit);
}
