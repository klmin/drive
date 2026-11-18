package com.thinkfree.drive.oauth2.jwk.repository;

import com.thinkfree.drive.oauth2.jwk.data.OAuth2JwkData;
import com.thinkfree.drive.oauth2.jwk.entity.OAuth2Jwk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * tb_oauth2_jwk Spring Data JPA 선언 인터페이스 — **패키지 전용**. {@link DefaultOAuth2JwkQueryRepository}(읽기)·{@link DefaultOAuth2JwkRepository}(쓰기) 만 주입받아 위임한다.
 * 구 XML 의 {@code NOW()} 비교 2곳은 DB 시각 대신 **애플리케이션 시각 파라미터**(Instant) — 이식 가능. INSERT 는 할당형 PK + {@code Persistable} 이라 {@code save} 가 persist.
 */
interface OAuth2JwkJpaRepository extends JpaRepository<OAuth2Jwk, String> {

    /** 검증 유효한 키 전부(현역 + 유예기간 중 구 키), 최신 우선 — 동시 기동으로 현역이 2개여도 모든 인스턴스가 같은 서명 키로 수렴하는 결정적 정렬 */
    @Query("select new com.thinkfree.drive.oauth2.jwk.data.OAuth2JwkData(j.kid, j.jwkJson, j.expirationDate) from OAuth2Jwk j "
            + "where j.expirationDate is null or j.expirationDate > :now order by j.registerDate desc, j.kid desc")
    List<OAuth2JwkData> findAllValid(@Param("now") Instant now);

    /** 현역 키 전부를 검증 전용으로 강등(rotate 1단계) — 조건부 다건(동시 기동이면 현역이 2개 이상)이라 벌크 JPQL */
    @Transactional
    @Modifying
    @Query("update OAuth2Jwk j set j.expirationDate = :expirationDate where j.expirationDate is null")
    int expireSigningKeys(@Param("expirationDate") Instant expirationDate);

    /** 유예기간이 끝난 구 키 삭제 — 만료 정리(조건부 다건)라 벌크 JPQL */
    @Transactional
    @Modifying
    @Query("delete from OAuth2Jwk j where j.expirationDate is not null and j.expirationDate < :now")
    int deleteExpired(@Param("now") Instant now);
}
