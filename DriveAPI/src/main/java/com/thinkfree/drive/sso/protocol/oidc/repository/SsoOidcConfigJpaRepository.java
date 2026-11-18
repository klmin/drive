package com.thinkfree.drive.sso.protocol.oidc.repository;

import com.thinkfree.drive.sso.protocol.oidc.data.SsoOidcConfigData;
import com.thinkfree.drive.sso.protocol.oidc.entity.SsoOidcConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * tb_sso_oidc_config Spring Data JPA 선언 인터페이스 — **패키지 전용**. {@link DefaultSsoOidcConfigQueryRepository}(읽기)·{@link DefaultSsoOidcConfigRepository}(쓰기) 만 주입받아 위임한다.
 * 읽기는 JPQL 생성자 식({@link SsoOidcConfigData}, 감사 없는 설정 6컬럼)이라 영속 컨텍스트에 엔티티가 남지 않는다 — 같은 트랜잭션의 JdbcClient upsert 뒤 재조회도 stale 없음.
 */
interface SsoOidcConfigJpaRepository extends JpaRepository<SsoOidcConfig, Integer> {

    String SELECT_DATA = "select new com.thinkfree.drive.sso.protocol.oidc.data.SsoOidcConfigData("
            + "o.ssoProviderSeq, o.issuerUrl, o.clientId, o.clientSecret, o.scopes, o.allowIdpInitiated) from SsoOidcConfig o ";

    @Transactional(readOnly = true)
    @Query(SELECT_DATA + "where o.ssoProviderSeq = :ssoProviderSeq")
    Optional<SsoOidcConfigData> findDataById(@Param("ssoProviderSeq") Integer ssoProviderSeq);

    @Transactional(readOnly = true)
    @Query(SELECT_DATA + "where o.ssoProviderSeq in :ssoProviderSeqList")
    List<SsoOidcConfigData> findAllDataBySsoProviderSeqIn(@Param("ssoProviderSeqList") Collection<Integer> ssoProviderSeqList);

    /** 조건부 다건 삭제(공급자 정리) — 행마다 로드할 이유가 없어 JPQL {@code @Modifying} */
    @Transactional
    @Modifying
    @Query("delete from SsoOidcConfig o where o.ssoProviderSeq in :ssoProviderSeqList")
    int deleteAllBySsoProviderSeqIn(@Param("ssoProviderSeqList") Collection<Integer> ssoProviderSeqList);
}
