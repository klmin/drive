package com.thinkfree.drive.oauth2.jwk.repository;

import com.thinkfree.drive.oauth2.jwk.entity.OAuth2Jwk;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;

/**
 * tb_oauth2_jwk 쓰기 — 구 OAuth2JwkMapper.xml 의 save·expireSigningKeys·deleteExpired 를 전부 JPA 로(§1 v2 "JPA 우선", JdbcClient 0). 읽기는 {@link DefaultOAuth2JwkQueryRepository}.
 * <ul>
 *   <li>{@code save} = Spring Data {@code save} — 엔티티가 {@code Persistable} 로 새 엔티티임을 알려 persist(INSERT 1문, merge 의 사전 SELECT 없음). 할당형 PK 라 INSERT 는 flush·커밋 때 나간다(서비스 트랜잭션 커밋 = 메서드 리턴 시점). register_date 는 Spring Data Auditing(현재 시각)</li>
 *   <li>{@code expireSigningKeys}·{@code deleteExpired} = JPQL {@code @Modifying} — 조건으로 여러 행(현역 키 전부 강등·만료 키 전부 삭제)이라 변경 감지 대상이 아님. deleteExpired 의 기준 시각은 구 XML {@code NOW()} 대신 애플리케이션 시각</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class DefaultOAuth2JwkRepository implements OAuth2JwkRepository {

    private final OAuth2JwkJpaRepository jpaRepository;

    @Override
    public void save(OAuth2Jwk jwk) {
        jpaRepository.save(jwk);
    }

    @Override
    public int expireSigningKeys(Instant expirationDate) {
        return jpaRepository.expireSigningKeys(expirationDate);
    }

    @Override
    public int deleteExpired() {
        return jpaRepository.deleteExpired(Instant.now());
    }
}
