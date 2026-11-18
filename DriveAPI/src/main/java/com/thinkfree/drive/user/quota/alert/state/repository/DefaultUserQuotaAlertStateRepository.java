package com.thinkfree.drive.user.quota.alert.state.repository;

import com.thinkfree.drive.global.jdbc.JdbcAudit;
import com.thinkfree.drive.user.quota.alert.state.entity.UserQuotaAlertState;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

import static com.thinkfree.drive.jooq.Tables.TB_USER_QUOTA_ALERT_STATE;

/**
 * tb_user_quota_alert_state 쓰기 + 쓰기 흐름 안의 잠금 조회 — 구 UserQuotaAlertStateMapper.xml 의 3 statement(§1 v2 "JPA 우선"). FindService 가 없고 소비자는 {@code DefaultUserQuotaAlertStateService}(@Transactional) 하나라 리포도 하나.
 * <ul>
 *   <li>{@code upsert} 만 jOOQ — {@code ON CONFLICT (user_seq) DO UPDATE SET last_threshold_percent, update_user_seq, update_date}(register_date 는 유지, 구 XML 동형)는 JPQL 로 표현할 수 없고,
 *       행이 없을 때 잠글 행이 없어 동시 첫 기록을 충돌 처리로 흡수해야 한다.
 *       감사 값은 {@link JdbcAudit}(시큐리티 사용자 우선(없으면 선언 값), 시각은 선언 값 없으면 현재 시각 초 절삭) — 엔티티 write-back 없음(W7, 호출부가 읽지 않음). 반환 = 영향 행 수(INSERT·UPDATE 모두 1)</li>
 *   <li>{@code lockLastThresholdPercentByUserSeq} = 스칼라 투영 + {@code @Lock(PESSIMISTIC_WRITE)} JPQL(SELECT … FOR UPDATE) — 엔티티를 영속성 컨텍스트에 올리지 않아
 *       이어지는 jOOQ upsert 와 섞이지 않는다(W7 — 구 em.find 잠금 + detach 대체). 없으면 null</li>
 *   <li>IN 삭제는 JPQL. 빈·널 목록은 SQL 없이 0(구 foreach 는 {@code IN ()} 문법 오류)</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class DefaultUserQuotaAlertStateRepository implements UserQuotaAlertStateRepository {

    private final UserQuotaAlertStateJpaRepository jpaRepository;
    private final DSLContext dsl;

    @Override
    public int upsert(UserQuotaAlertState e) {
        Instant registerDate = JdbcAudit.nowOr(e.getRegisterDate());
        Integer updateUserSeq = JdbcAudit.userSeqOr(e.getUpdateUserSeq());
        Instant updateDate = JdbcAudit.nowOr(e.getUpdateDate());
        Short lastThresholdPercent = e.getLastThresholdPercent() == null ? null : e.getLastThresholdPercent().shortValue();
        return dsl.insertInto(TB_USER_QUOTA_ALERT_STATE,
                        TB_USER_QUOTA_ALERT_STATE.USER_SEQ, TB_USER_QUOTA_ALERT_STATE.LAST_THRESHOLD_PERCENT, TB_USER_QUOTA_ALERT_STATE.REGISTER_DATE)
                .values(e.getUserSeq(), lastThresholdPercent, registerDate)
                .onConflict(TB_USER_QUOTA_ALERT_STATE.USER_SEQ)
                .doUpdate()
                .set(TB_USER_QUOTA_ALERT_STATE.LAST_THRESHOLD_PERCENT, lastThresholdPercent)
                .set(TB_USER_QUOTA_ALERT_STATE.UPDATE_USER_SEQ, updateUserSeq)
                .set(TB_USER_QUOTA_ALERT_STATE.UPDATE_DATE, updateDate)
                .execute();
    }

    @Override
    public Integer lockLastThresholdPercentByUserSeq(int userSeq) {
        return jpaRepository.lockLastThresholdPercentByUserSeq(userSeq).orElse(null);
    }

    @Override
    public int deleteByUserSeqList(List<Integer> userSeqList) {
        if (userSeqList == null || userSeqList.isEmpty()) {
            return 0;
        }
        return jpaRepository.deleteByUserSeqIn(userSeqList);
    }
}
