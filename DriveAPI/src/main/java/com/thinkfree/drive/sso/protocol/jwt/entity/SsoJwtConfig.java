package com.thinkfree.drive.sso.protocol.jwt.entity;

import com.thinkfree.drive.global.jpa.audit.BaseAuditEntity;
import com.thinkfree.drive.sso.protocol.jwt.enums.KeySourceType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * tb_sso_jwt_config — PK(sso_provider_seq)는 tb_sso_provider 와 1:1 인 할당형이라 {@code @Id} 만. 쓰기는 upsert(JdbcClient)·IN 삭제(JPQL)뿐이라 변경 메서드 없음
 * (단건 등록·수정은 호출부가 없어 W4 에서 삭제 — 다시 생기면 Persistable + save·관리 엔티티 변경 감지로). 감사 4필드는 {@link BaseAuditEntity}.
 * MapStruct {@code JwtConfigConverter} 가 Builder 로 만든다.
 */
@SuperBuilder
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "tb_sso_jwt_config")
public class SsoJwtConfig extends BaseAuditEntity {

    @Id
    private Integer ssoProviderSeq;
    private String issuer;
    private String audience;
    @Enumerated(EnumType.STRING)
    private KeySourceType keySourceType;
    private String publicKey;
    private String jwksUri;
    private String emailClaimName;
    private String errorRedirectUrl;
}
