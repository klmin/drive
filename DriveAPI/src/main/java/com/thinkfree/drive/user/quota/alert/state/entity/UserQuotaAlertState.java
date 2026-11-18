package com.thinkfree.drive.user.quota.alert.state.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * tb_user_quota_alert_state — 사용자별 최근 용량 알림 임계치(%). user_seq 가 PK(사용자당 1행, HASH 파티션), 전부 upsert 로 쓴다.
 * <p>
 * JPA 매핑(§1 v2 "JPA 우선"): 잠금 조회는 스칼라 투영 {@code @Lock(PESSIMISTIC_WRITE)} JPQL(엔티티를 컨텍스트에 올리지 않음, W7), IN 삭제는 JPQL — {@code UserQuotaAlertStateJpaRepository}.
 * **upsert 만 jOOQ**({@code ON CONFLICT (user_seq) DO UPDATE}, JPQL 로 표현 불가) — 감사 값은 {@code JdbcAudit} 규칙으로 바인딩(write-back 없음 — W7). 변경 메서드 없음.
 * PK 자바 타입은 DDL 을 따라 Integer(tb_user_quota_alert_state.user_seq integer — tb_user.user_seq 와 같음. 10-07 이전 Long 은 낡은 bigint 기록을 따른 것).
 * MapStruct {@code UserQuotaAlertStateConverter} 가 Builder 로 만든다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "tb_user_quota_alert_state")
@EntityListeners(AuditingEntityListener.class)
public class UserQuotaAlertState {

    @Id
    private Integer userSeq;
    private Integer lastThresholdPercent;
    @CreatedDate
    private Instant registerDate;
    @LastModifiedBy
    private Integer updateUserSeq;
    @LastModifiedDate
    private Instant updateDate;
}
