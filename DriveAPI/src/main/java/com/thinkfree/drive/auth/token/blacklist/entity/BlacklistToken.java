package com.thinkfree.drive.auth.token.blacklist.entity;

import com.thinkfree.drive.global.jpa.audit.BaseRegisterTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * tb_blacklist_token — 로그아웃한 access 토큰의 해시(token_hash UNIQUE). 매 인증 요청에서 존재 여부를 확인하고(JwtAuthenticationFilter), 만료분은 스케줄러가 지운다.
 * <p>
 * JPA 매핑(§1 v2 "JPA 우선", JdbcClient 0 — TenantAccessToken·PasswordResetToken 과 같은 꼴): INSERT 는 {@code save}(IDENTITY 라 저장 순간 INSERT)(identity 키·Spring Data Auditing 가 register_date),
 * 존재 확인은 파생 {@code existsBy}, 삭제 2종은 JPQL — {@code BlacklistTokenJpaRepository}. 변경 메서드가 없는 불변 엔티티. 날짜는 전부 Instant(timestamptz), 사용자 감사 컬럼 없음.
 * PK 는 DDL 이 bigint 라 Long(구 엔티티는 Integer 였으나 읽는 곳 없음). MapStruct {@code BlacklistTokenConverter} 가 Builder 로 만든다.
 */
@Getter
@SuperBuilder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "tb_blacklist_token")
public class BlacklistToken extends BaseRegisterTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long blacklistTokenSeq;
    private Integer userSeq;
    private String tokenHash;
    private Instant issuedDate;
    private Instant expirationDate;
}
