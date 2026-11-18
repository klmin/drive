package com.thinkfree.drive.userlog.entity;

import com.thinkfree.drive.userlog.enums.ActivityType;
import com.thinkfree.drive.userlog.enums.ActorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * tb_user_log — 사용자 활동 로그(로그인·로그아웃·비밀번호 변경). user_seq 는 tb_user FK.
 * <p>
 * JPA 매핑(§1 v2 "JPA 우선", JdbcClient 0): INSERT 는 {@code save}(IDENTITY 라 저장 순간 INSERT)(identity bigint → Long). **register_date 는 {@code insertable = false} 로 DB DEFAULT CURRENT_TIMESTAMP 에 맡김**(구 XML 동형) — 리스너의 초 절삭 시각을 넣으면 기존 행(µs 정밀도)과 섞여 같은 초 안의 DESC 정렬이 흔들린다(8081 실측). 저장 직후 메모리 값은 null(읽는 곳 없음),
 * 목록은 JPQL 생성자 식 + {@code Limit}, 존재 확인은 파생 {@code existsBy} — {@code UserLogJpaRepository}. enum 2종 {@code @Enumerated(STRING)}, registerDate 는 Instant(Hibernate 기본 매핑).
 * {@code userSeq} 는 로그 주인(감사 등록자 아님) — 정적 팩토리가 항상 채운다. 변경 메서드 없는 불변 엔티티.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "tb_user_log")
public class UserLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userLogSeq;
    private String region;
    private String ipAddress;
    @Column(insertable = false)
    private Instant registerDate;
    @Enumerated(EnumType.STRING)
    private ActivityType activityType;
    @Enumerated(EnumType.STRING)
    private ActorType actorType;
    private Integer userSeq;

    private UserLog(Integer userSeq, String region, String ipAddress, ActivityType activityType, ActorType actorType) {
        this.userSeq = userSeq;
        this.region = region;
        this.ipAddress = ipAddress;
        this.activityType = activityType;
        this.actorType = actorType;
    }

    public static UserLog createUserLog(Integer userSeq, String region, String ipAddress, ActivityType activityType, ActorType actorType) {
        return new UserLog(userSeq, region, ipAddress, activityType, actorType);
    }
}
