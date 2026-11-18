package com.thinkfree.drive.user.token.entity;

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
 * tb_password_reset_token — 비밀번호 재설정 1회용 토큰(해시만 저장, token_hash UNIQUE). 사용자당 1건(재요청 시 기존 삭제 후 발급).
 * <p>
 * JPA 매핑(§1 v2 "JPA 우선", JdbcClient 0 — TenantAccessToken 과 같은 꼴): INSERT 는 {@code save}(IDENTITY 라 저장 순간 INSERT)(identity 키·Spring Data Auditing 가 register_date), 조회는 JPQL, 삭제 3종은 JPQL — {@code PasswordResetTokenJpaRepository}.
 * 변경 메서드가 없는 불변 엔티티라 영속 컨텍스트에 남아도 flush 때 UPDATE 가 나가지 않는다. 날짜는 전부 Instant(timestamptz, 컨버터 불필요), 사용자 감사 컬럼 없음.
 */
@Getter
@SuperBuilder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "tb_password_reset_token")
public class PasswordResetToken extends BaseRegisterTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer passwordResetTokenSeq;
    private Integer userSeq;
    private String tokenHash;
    private Instant issuedDate;
    private Instant expirationDate;

    public static PasswordResetToken of(Integer userSeq, String tokenHash, Instant issuedDate, Instant expirationDate) {
        return PasswordResetToken.builder()
                .userSeq(userSeq)
                .tokenHash(tokenHash)
                .issuedDate(issuedDate)
                .expirationDate(expirationDate)
                .build();
    }
}
