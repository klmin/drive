package com.thinkfree.drive.sso.protocol.jwt.repository;

import com.thinkfree.drive.sso.protocol.jwt.data.SsoJwtConfigData;
import com.thinkfree.drive.sso.protocol.jwt.entity.SsoJwtConfig;
import com.thinkfree.drive.sso.provider.enums.SsoProviderScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * tb_sso_jwt_config Spring Data JPA 선언 인터페이스 — **패키지 전용**. {@link DefaultSsoJwtConfigQueryRepository}(읽기)·{@link DefaultSsoJwtConfigRepository}(쓰기) 만 주입받아 위임한다.
 * 읽기는 JPQL 생성자 식({@link SsoJwtConfigData}, 감사 없는 설정 8컬럼)이라 영속 컨텍스트에 엔티티가 남지 않는다 — 같은 트랜잭션의 JdbcClient upsert 뒤 재조회도 stale 없음.
 */
interface SsoJwtConfigJpaRepository extends JpaRepository<SsoJwtConfig, Integer> {

    String SELECT_DATA = "select new com.thinkfree.drive.sso.protocol.jwt.data.SsoJwtConfigData("
            + "j.ssoProviderSeq, j.issuer, j.audience, j.keySourceType, j.publicKey, j.jwksUri, j.emailClaimName, j.errorRedirectUrl) from SsoJwtConfig j ";

    @Transactional(readOnly = true)
    @Query(SELECT_DATA + "where j.ssoProviderSeq = :ssoProviderSeq")
    Optional<SsoJwtConfigData> findDataById(@Param("ssoProviderSeq") Integer ssoProviderSeq);

    @Transactional(readOnly = true)
    @Query(SELECT_DATA + "where j.issuer = :issuer")
    Optional<SsoJwtConfigData> findDataByIssuer(@Param("issuer") String issuer);

    /** 공급자 scope 로 거른다 — tb_sso_provider 와 엔티티 조인(FK·연관 매핑 없음) */
    @Transactional(readOnly = true)
    @Query(SELECT_DATA + "join SsoProvider p on p.ssoProviderSeq = j.ssoProviderSeq where j.issuer = :issuer and p.scope = :scope")
    Optional<SsoJwtConfigData> findDataByIssuerAndScope(@Param("issuer") String issuer, @Param("scope") SsoProviderScope scope);

    @Transactional(readOnly = true)
    @Query(SELECT_DATA + "where j.ssoProviderSeq in :ssoProviderSeqList")
    List<SsoJwtConfigData> findAllDataBySsoProviderSeqIn(@Param("ssoProviderSeqList") Collection<Integer> ssoProviderSeqList);

    /** 조건부 다건 삭제(공급자 정리) — 행마다 로드할 이유가 없어 JPQL {@code @Modifying} */
    @Transactional
    @Modifying
    @Query("delete from SsoJwtConfig j where j.ssoProviderSeq in :ssoProviderSeqList")
    int deleteAllBySsoProviderSeqIn(@Param("ssoProviderSeqList") Collection<Integer> ssoProviderSeqList);
}
