package com.thinkfree.drive.user.quota.alert.state.repository;

import com.thinkfree.drive.user.quota.alert.state.entity.UserQuotaAlertState;

import java.util.List;

/**
 * tb_user_quota_alert_state 쓰기 + 쓰기 흐름 안의 잠금 조회 — {@code DefaultUserQuotaAlertStateService}(@Transactional: lock → 판정 → upsert) 가 주입. 읽기 전용 소비자가 없어 Query 리포를 분리하지 않는다.
 */
public interface UserQuotaAlertStateRepository {

    /** INSERT 또는 (user_seq 충돌 시) last_threshold_percent·update_* 갱신. 감사 값이 비어 있으면 현재 시각·컨텍스트 사용자로 채운다. 영향 행 수(항상 1) */
    int upsert(UserQuotaAlertState userQuotaAlertState);

    /** 행 잠금(FOR UPDATE) 후 마지막 임계치(%) — 호출부 트랜잭션 안에서만 유효, 행이 없으면 null(엔티티를 영속성 컨텍스트에 올리지 않는다) */
    Integer lockLastThresholdPercentByUserSeq(int userSeq);

    /** 빈/널 목록은 SQL 없이 0 */
    int deleteByUserSeqList(List<Integer> userSeqList);
}
