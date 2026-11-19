package com.thinkfree.drive.cleanup.storage.service;

import com.thinkfree.drive.cleanup.storage.data.StorageDeletionData;
import com.thinkfree.drive.cleanup.storage.query.StorageDeletionFailQuery;
import com.thinkfree.drive.cleanup.storage.repository.StorageDeletionRepository;
import com.thinkfree.drive.global.jdbc.JdbcAudit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 스토리지 객체 정리 대기열(아웃박스) — 등록은 DB 상태를 바꾸는 트랜잭션 안에서, 처리는 {@link StorageDeletionProcessor} 가 한다.
 * <ul>
 *   <li>DELETE — 객체 삭제(즉시 또는 유예)</li>
 *   <li>ABORT_MULTIPART — 미완성 멀티파트 업로드 중단(업로드 세션을 끝내는 쪽이 같은 tx 에서 등록)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageDeletionService {

    private final StorageDeletionRepository storageDeletionRepository;

    /**
     * 지울 키를 대기열에 넣는다 — 호출한 트랜잭션에 합류하므로 그 트랜잭션이 롤백되면 등록도 없다(없으면 자체 트랜잭션).
     * 빈 키는 버리고, 같은 호출 안의 중복은 하나로 넣는다.
     */
    @Transactional
    public void register(List<String> storageKeys) {
        List<String> keys = normalize(storageKeys);
        if (keys.isEmpty()) {
            return;
        }
        int inserted = storageDeletionRepository.insertAll(keys);
        logInserted("register", keys.size(), inserted);
    }

    /**
     * 교체·밀려난 이전 key 의 삭제를 delay 뒤로 미뤄 등록한다(다운로드 중인 요청·편집 세션 보호) — 트랜잭션 합류는 {@link #register} 와 같다.
     */
    @Transactional
    public void registerDeferred(List<String> storageKeys, Duration delay) {
        List<String> keys = normalize(storageKeys);
        if (keys.isEmpty()) {
            return;
        }
        int inserted = storageDeletionRepository.insertDeferred(keys, JdbcAudit.now().plus(delay));
        logInserted("registerDeferred", keys.size(), inserted);
    }

    /** 미완성 멀티파트 업로드 중단을 등록한다 — 트랜잭션 합류는 {@link #register} 와 같다. 중복 등록돼도 abort 는 멱등. */
    @Transactional
    public void registerAbort(String storageKey, String uploadId) {
        if (!StringUtils.hasText(storageKey) || !StringUtils.hasText(uploadId)) {
            return;
        }
        storageDeletionRepository.insertAbort(storageKey, uploadId);
        log.info("[StorageDeletionService registerAbort] key: {}", storageKey);
    }

    @Transactional(readOnly = true)
    public List<StorageDeletionData> findDue(Instant now, int maxAttemptCount, int limit) {
        return storageDeletionRepository.findDue(now, maxAttemptCount, limit);
    }

    @Transactional(readOnly = true)
    public List<StorageDeletionData> findDueAborts(Instant now, int maxAttemptCount, int limit) {
        return storageDeletionRepository.findDueAborts(now, maxAttemptCount, limit);
    }

    @Transactional(readOnly = true)
    public Set<String> findReferencedKeys(Collection<String> storageKeys) {
        return storageDeletionRepository.findReferencedKeys(storageKeys);
    }

    /** 한 묶음의 결과 반영 — 성공 행은 지우고 실패 행은 시도 횟수·다음 시도 시각·사유를 갱신한다(한 트랜잭션). */
    @Transactional
    public void applyResult(List<Long> succeededSeqList, List<StorageDeletionFailQuery> failQueries) {
        int deleted = storageDeletionRepository.deleteBySeqList(succeededSeqList);
        if (deleted != succeededSeqList.size()) {
            // 처리 중에 행이 다른 곳에서 지워진 경우(수동 정리 등) — 스토리지 삭제는 이미 끝났으므로 결과에는 영향 없음
            log.warn("[StorageDeletionService applyResult] succeeded: {}, deleted rows: {}", succeededSeqList.size(), deleted);
        }
        storageDeletionRepository.markFailed(failQueries);
    }

    private static List<String> normalize(List<String> storageKeys) {
        if (storageKeys == null || storageKeys.isEmpty()) {
            return List.of();
        }
        return storageKeys.stream().filter(StringUtils::hasText).distinct().toList();
    }

    private static void logInserted(String action, int requested, int inserted) {
        if (inserted != requested) {
            log.warn("[StorageDeletionService {}] requested: {}, inserted: {}", action, requested, inserted);
            return;
        }
        log.info("[StorageDeletionService {}] count: {}", action, inserted);
    }
}
