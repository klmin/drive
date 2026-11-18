package com.thinkfree.drive.oauth2.jwk.repository;

import com.thinkfree.drive.oauth2.jwk.data.OAuth2JwkData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * tb_oauth2_jwk 읽기 — 구 OAuth2JwkMapper.xml 의 findAllValid 를 JPQL 생성자 식으로(§1 v2 "JPA 우선", JdbcClient 0). 쓰기는 {@link DefaultOAuth2JwkRepository}.
 * 기준 시각은 구 XML 의 DB {@code NOW()} 대신 애플리케이션 {@code Instant.now()}. 결과는 DTO — OAuth2KeyManager 가 캐시로 들고 있는 스냅샷이 영속 컨텍스트와 섞이지 않는다(detach 불필요).
 */
@Repository
@RequiredArgsConstructor
public class DefaultOAuth2JwkQueryRepository implements OAuth2JwkQueryRepository {

    private final OAuth2JwkJpaRepository jpaRepository;

    @Override
    public List<OAuth2JwkData> findAllValid() {
        return jpaRepository.findAllValid(Instant.now());
    }
}
