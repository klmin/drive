package com.thinkfree.drive.superadmin.token.entity;

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
 * tb_tenant_access_token — 슈퍼어드민이 테넌트에 접속할 때 1회용으로 발급·소비되는 접속 토큰(해시만 저장, token_hash UNIQUE).
 * <p>
 * JPA 매핑(§1 v2 "JPA 우선", JdbcClient 0): INSERT 는 {@code save}(IDENTITY 라 저장 순간 INSERT)(identity 키·Spring Data Auditing 가 register_date), 조회는 JPQL, 삭제 3종은 JPQL — {@code TenantAccessTokenJpaRepository}.
 * 변경 메서드가 없는 불변 엔티티라 영속 컨텍스트에 남아도 flush 때 UPDATE 가 나가지 않는다. 날짜는 전부 Instant(timestamptz, 컨버터 불필요), 사용자 감사 컬럼 없음.
 */
@Getter
@SuperBuilder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "tb_tenant_access_token")
public class TenantAccessToken extends BaseRegisterTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer tenantAccessTokenSeq;
    private Integer superAdminUserSeq;
    private Integer tenantSeq;
    private String tokenHash;
    private Instant issuedDate;
    private Instant expirationDate;

    public static TenantAccessToken of(Integer superAdminUserSeq, Integer tenantSeq, String tokenHash, Instant issuedDate, Instant expirationDate) {
        return TenantAccessToken.builder()
                .superAdminUserSeq(superAdminUserSeq)
                .tenantSeq(tenantSeq)
                .tokenHash(tokenHash)
                .issuedDate(issuedDate)
                .expirationDate(expirationDate)
                .build();
    }
}
