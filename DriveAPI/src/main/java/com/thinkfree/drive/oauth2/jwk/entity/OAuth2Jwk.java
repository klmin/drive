package com.thinkfree.drive.oauth2.jwk.entity;

import com.thinkfree.drive.global.jpa.audit.BaseRegisterTimeEntity;
import com.nimbusds.jose.jwk.RSAKey;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

/**
 * tb_oauth2_jwk — OAuth2 RS256 서명 키 (개인키 포함 JWK JSON).
 *
 * expiration_date NULL = 현역(서명+검증), 값 있음 = rotate로 강등된 구 키(만료 전까지 검증만).
 * jwk_json이 원본이고 kid 컬럼은 조회용 발췌 — 항상 JSON 안의 kid와 동일해야 한다.
 * <p>
 * JPA 매핑(§1 v2 "JPA 우선", JdbcClient 0): **할당형 PK**(`@Id kid`, 생성 전략 없음). Spring Data {@code save} 는 id 가 채워진 엔티티를 기존 행으로 보고 merge(SELECT 후 INSERT)하므로
 * {@link Persistable} 로 "새 엔티티" 를 알려 {@code save} 가 persist(INSERT 1문)로 동작하게 한다 — 생성 직후 true, persist·로드 뒤 false.
 * register_date 는 Spring Data Auditing(구 인터셉터 동형), 날짜 2종은 Instant(Hibernate 기본 매핑). 조회·강등·삭제는 JPQL — {@code OAuth2JwkJpaRepository}. 변경 메서드 없는 불변 엔티티.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "tb_oauth2_jwk")
public class OAuth2Jwk extends BaseRegisterTimeEntity implements Persistable<String> {

    @Id
    private String kid;
    private String jwkJson;
    private Instant expirationDate;

    /** 아직 persist 안 된 새 엔티티인지 — {@link #of} 로 만든 객체만 true */
    @Transient
    @Getter(AccessLevel.NONE)
    private boolean newEntity;

    private OAuth2Jwk(String kid, String jwkJson) {
        this.kid = kid;
        this.jwkJson = jwkJson;
        this.newEntity = true;
    }

    public static OAuth2Jwk of(RSAKey rsaKey) {
        return new OAuth2Jwk(rsaKey.getKeyID(), rsaKey.toJSONString());
    }

    public boolean isSigning() {
        return expirationDate == null;
    }

    @Override
    public String getId() {
        return kid;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.newEntity = false;
    }
}
