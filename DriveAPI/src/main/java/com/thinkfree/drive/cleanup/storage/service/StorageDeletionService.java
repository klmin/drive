package com.thinkfree.drive.cleanup.storage.service;

import com.thinkfree.drive.cleanup.storage.data.StorageDeletionData;
import com.thinkfree.drive.cleanup.storage.query.StorageDeletionFailQuery;
import com.thinkfree.drive.cleanup.storage.repository.StorageDeletionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

/**
 * 스토리지 객체 삭제 대기열(아웃박스) — 등록은 DB 행을 지우는 트랜잭션 안에서, 처리는 {@link StorageDeletionProcessor} 가 한다.
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
        if (storageKeys == null || storageKeys.isEmpty()) {
            return;
        }
        List<String> keys = storageKeys.stream().filter(StringUtils::hasText).distinct().toList();
        if (keys.isEmpty()) {
            return;
        }
        int inserted = storageDeletionRepository.insertAll(keys);
        if (inserted != keys.size()) {
            log.warn("[StorageDeletionService register] requested: {}, inserted: {}", keys.size(), inserted);
            return;
        }
        log.info("[StorageDeletionService register] count: {}", inserted);
    }

    @Transactional(readOnly = true)
    public List<StorageDeletionData> findDue(Instant now, int maxAttemptCount, int limit) {
        return storageDeletionRepository.findDue(now, maxAttemptCount, limit);
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
}
