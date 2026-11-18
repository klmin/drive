package com.thinkfree.drive.sso.provider.entity;

import com.thinkfree.drive.global.jpa.audit.BaseAuditEntity;
import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;
import com.thinkfree.drive.sso.provider.enums.SsoProviderScope;
import com.thinkfree.drive.sso.provider.enums.SsoProviderStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * tb_sso_provider — 쓰기는 upsert(JdbcClient, {@code DefaultSsoProviderRepository})·IN 삭제(JPQL)뿐이라 엔티티는 JPQL 매핑·upsert 인자용(변경 메서드 없음).
 * 단건 등록·수정·상태 전환 경로는 호출부가 없어 삭제(W4, 10-13 — 관리자 공급자 API 는 f0f6f683f 에서 삭제됨). 다시 생기면 관리 엔티티 + 변경 감지로.
 * 감사 4필드는 Instant(컨버터 불필요), enum 3종은 문자열.
 */
@SuperBuilder
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "tb_sso_provider")
public class SsoProvider extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer ssoProviderSeq;
    private Integer tenantSeq;
    private String displayName;
    private String iconImageKey;
    @Enumerated(EnumType.STRING)
    private SsoProviderProtocolType protocolType;
    @Enumerated(EnumType.STRING)
    private SsoProviderStatus status;
    @Enumerated(EnumType.STRING)
    private SsoProviderScope scope;
    private Integer sortOrder;
}
