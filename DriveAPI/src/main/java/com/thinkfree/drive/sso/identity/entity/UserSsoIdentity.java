package com.thinkfree.drive.sso.identity.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * tb_user_sso_identity — 내부 User 와 외부 IdP Identity 매핑(프로토콜 무관). UNIQUE (sso_provider_seq, subject, tenant_seq)·(user_seq, sso_provider_seq).
 * {@code tenantSeq} 는 연결된 사용자의 테넌트 — GLOBAL Provider 의 같은 IdP 계정을 테넌트마다 다른 계정에 연결할 수 있게 유니크 범위에 넣는다.
 * <p>
 * JPA 매핑(§1 v2 "JPA 우선", JdbcClient 0): INSERT 는 {@code save}(identity 키·Spring Auditing {@code @CreatedDate} 가 linked_at),
 * 사용자 조인 조회는 JPQL 생성자 식({@code UserSsoIdentityData}), last_callback_at 갱신·IN 삭제는 JPQL — {@code UserSsoIdentityJpaRepository}. 변경 메서드 없는 불변 엔티티.
 * <ul>
 *   <li>{@code lastCallbackAt} ← last_callback_at. 구 엔티티는 {@code lastLoginAt}(구 @RegisterDate) 이었으나 XML 이 INSERT 하지도 읽지도 않던 유령 필드 — 실제 컬럼명으로 바로잡고 INSERT 때 null(구 동작 = 컬럼 미지정 → DEFAULT NULL)</li>
 *   <li>PK 는 DDL bigint → Long. {@code UserSsoIdentityData}·{@code UserSsoIdentityService.updateLastCallbackAt(long)} 까지 Long 으로 통일(10-06 사용자 지적)</li>
 * </ul>
 * MapStruct {@code UserSsoIdentityConverter} 가 Builder 로 만든다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "tb_user_sso_identity")
@EntityListeners(AuditingEntityListener.class)
public class UserSsoIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ssoIdentitySeq;
    private Integer userSeq;
    private Integer tenantSeq;
    private Integer ssoProviderSeq;
    private String subject;
    private String email;
    private String name;
    @CreatedDate
    private Instant linkedAt;
    private Instant lastCallbackAt;
}
