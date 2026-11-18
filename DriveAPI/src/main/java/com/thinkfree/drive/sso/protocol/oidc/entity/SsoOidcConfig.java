package com.thinkfree.drive.sso.protocol.oidc.entity;

import com.thinkfree.drive.global.jpa.audit.BaseAuditEntity;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.type.YesNoConverter;

/**
 * tb_sso_oidc_config — PK(sso_provider_seq)는 tb_sso_provider 와 1:1 인 할당형이라 {@code @Id} 만. 쓰기는 upsert(JdbcClient)·IN 삭제(JPQL)뿐이라 변경 메서드 없음
 * (단건 등록·수정은 호출부가 없어 W4 에서 삭제). allow_idp_initiated 는 NOT NULL char(1) Y/N → {@link YesNoConverter}. 감사 4필드는 {@link BaseAuditEntity}.
 * MapStruct {@code OidcConfigConverter} 가 Builder 로 만든다.
 */
@SuperBuilder
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "tb_sso_oidc_config")
public class SsoOidcConfig extends BaseAuditEntity {

    @Id
    private Integer ssoProviderSeq;
    private String issuerUrl;
    private String clientId;
    private String clientSecret;
    private String scopes;
    @Convert(converter = YesNoConverter.class)
    private Boolean allowIdpInitiated;
}
