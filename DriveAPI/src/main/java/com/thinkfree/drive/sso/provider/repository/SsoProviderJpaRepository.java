package com.thinkfree.drive.sso.provider.repository;

import com.thinkfree.drive.sso.provider.data.SsoProviderData;
import com.thinkfree.drive.sso.provider.entity.SsoProvider;
import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;
import com.thinkfree.drive.sso.provider.enums.SsoProviderScope;
import com.thinkfree.drive.sso.provider.enums.SsoProviderStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * tb_sso_provider Spring Data JPA 선언 인터페이스 — **패키지 전용**. {@link DefaultSsoProviderRepository}(IN 삭제 JPQL)·
 * {@link DefaultSsoProviderQueryRepository}(조인 DTO·존재 검사 3종·활성 공급자 BASIC DTO — 전부 생성자 식, 엔티티 반환 없음) 가 주입받아 위임한다. 조건은 {@code =} 비교라 null 인자는 false(구 SQL 동일).
 */
interface SsoProviderJpaRepository extends JpaRepository<SsoProvider, Integer> {

    // ------------------------------------------------------------------ 조인 DTO(구 JdbcClient 4종, 5차 패스 C)

    /** FULL 17컬럼 중 tenant_name 앞까지 — 뒤에 테넌트 이름 식 + {@link #FULL_TAIL} */
    String FULL_HEAD = "select new com.thinkfree.drive.sso.provider.data.SsoProviderData(a.ssoProviderSeq, a.tenantSeq, a.displayName, ";
    /** tenant_name 뒤 — 등록자(INNER)·수정자(LEFT) 사용자 조인 */
    String FULL_TAIL = ", a.iconImageKey, a.protocolType, a.status, a.scope, a.sortOrder, "
            + "a.registerUserSeq, c.userName, c.userId, a.registerDate, a.updateUserSeq, d.userName, d.userId, a.updateDate) from SsoProvider a ";
    String USER_JOIN = "join User c on c.userSeq = a.registerUserSeq left join User d on d.userSeq = a.updateUserSeq ";

    /** 테넌트 공급자 목록 — tenantSeq·protocolType·cursorId 는 선택(null 이면 조건 없음), seq DESC */
    @Transactional(readOnly = true)
    @Query(FULL_HEAD + "b.tenantName" + FULL_TAIL + "join Tenant b on b.tenantSeq = a.tenantSeq " + USER_JOIN
            + "where a.scope = :scope and (:tenantSeq is null or a.tenantSeq = :tenantSeq) and (:protocolType is null or a.protocolType = :protocolType) "
            + "and (:cursorId is null or a.ssoProviderSeq < :cursorId) order by a.ssoProviderSeq desc")
    List<SsoProviderData> findFullByTenantScope(@Param("scope") SsoProviderScope scope, @Param("tenantSeq") Integer tenantSeq,
                                                @Param("protocolType") SsoProviderProtocolType protocolType, @Param("cursorId") Integer cursorId, Limit limit);

    /** GLOBAL 공급자 목록 — 테넌트 없음(tenant_name NULL), protocolType 선택, sort_order·seq DESC */
    @Transactional(readOnly = true)
    @Query(FULL_HEAD + "cast(null as String)" + FULL_TAIL + USER_JOIN
            + "where a.scope = :scope and (:protocolType is null or a.protocolType = :protocolType) order by a.sortOrder, a.ssoProviderSeq desc")
    List<SsoProviderData> findFullByGlobalScope(@Param("scope") SsoProviderScope scope, @Param("protocolType") SsoProviderProtocolType protocolType);

    /** 단건 — 테넌트 LEFT JOIN(GLOBAL 이면 tenant_name NULL) */
    @Transactional(readOnly = true)
    @Query(FULL_HEAD + "b.tenantName" + FULL_TAIL + "left join Tenant b on b.tenantSeq = a.tenantSeq " + USER_JOIN + "where a.ssoProviderSeq = :ssoProviderSeq")
    Optional<SsoProviderData> findFullById(@Param("ssoProviderSeq") Integer ssoProviderSeq);

    /** 로그인 경로 — 테넌트의 활성 공급자(BASIC 9컬럼, 테넌트 INNER JOIN) */
    @Transactional(readOnly = true)
    @Query("select new com.thinkfree.drive.sso.provider.data.SsoProviderData(a.ssoProviderSeq, a.tenantSeq, a.displayName, b.tenantName, a.iconImageKey, "
            + "a.protocolType, a.status, a.scope, a.sortOrder) from SsoProvider a join Tenant b on b.tenantSeq = a.tenantSeq "
            + "where a.scope = :scope and a.tenantSeq = :tenantSeq and a.protocolType = :protocolType and a.status = :status")
    Optional<SsoProviderData> findBasicByTenantAndProtocol(@Param("scope") SsoProviderScope scope, @Param("tenantSeq") Integer tenantSeq,
                                                           @Param("protocolType") SsoProviderProtocolType protocolType, @Param("status") SsoProviderStatus status);

    /** 테넌트 범위에 지정 상태·프로토콜 공급자가 있는지(단일 활성 프로토콜 검사) */
    @Transactional(readOnly = true)
    @Query("select case when count(p) > 0 then true else false end from SsoProvider p "
            + "where p.scope = :scope and p.tenantSeq = :tenantSeq and p.status = :status and p.protocolType = :protocolType")
    boolean existsByScopeAndTenant(@Param("scope") SsoProviderScope scope, @Param("tenantSeq") Integer tenantSeq,
                                   @Param("status") SsoProviderStatus status, @Param("protocolType") SsoProviderProtocolType protocolType);

    /** 범위(GLOBAL)에 지정 상태·프로토콜 공급자가 있는지 */
    @Transactional(readOnly = true)
    @Query("select case when count(p) > 0 then true else false end from SsoProvider p "
            + "where p.scope = :scope and p.status = :status and p.protocolType = :protocolType")
    boolean existsByScope(@Param("scope") SsoProviderScope scope, @Param("status") SsoProviderStatus status, @Param("protocolType") SsoProviderProtocolType protocolType);

    /** 같은 범위·테넌트에 같은 표시 이름이 있는지 — excludeSeq 가 있으면 그 공급자 제외(수정 시) */
    @Transactional(readOnly = true)
    @Query("select case when count(p) > 0 then true else false end from SsoProvider p "
            + "where p.scope = :scope and p.tenantSeq = :tenantSeq and p.displayName = :displayName "
            + "and (:excludeSeq is null or p.ssoProviderSeq <> :excludeSeq)")
    boolean existsDisplayNameInTenant(@Param("scope") SsoProviderScope scope, @Param("tenantSeq") Integer tenantSeq,
                                      @Param("displayName") String displayName, @Param("excludeSeq") Integer excludeSeq);

    /** {@link #existsDisplayNameInTenant} 의 테넌트 없음(tenant_seq IS NULL) 판 */
    @Transactional(readOnly = true)
    @Query("select case when count(p) > 0 then true else false end from SsoProvider p "
            + "where p.scope = :scope and p.tenantSeq is null and p.displayName = :displayName "
            + "and (:excludeSeq is null or p.ssoProviderSeq <> :excludeSeq)")
    boolean existsDisplayNameWithoutTenant(@Param("scope") SsoProviderScope scope, @Param("displayName") String displayName,
                                           @Param("excludeSeq") Integer excludeSeq);

    /** GLOBAL 범위의 지정 프로토콜·상태 공급자(BASIC 9컬럼, tenant_name NULL) — 단일 활성 규칙상 0/1건, 여럿이면 seq 가 가장 작은 것 */
    @Transactional(readOnly = true)
    @Query("select new com.thinkfree.drive.sso.provider.data.SsoProviderData(a.ssoProviderSeq, a.tenantSeq, a.displayName, cast(null as String), a.iconImageKey, "
            + "a.protocolType, a.status, a.scope, a.sortOrder) from SsoProvider a "
            + "where a.scope = :scope and a.protocolType = :protocolType and a.status = :status order by a.ssoProviderSeq")
    List<SsoProviderData> findBasicByScopeAndProtocol(@Param("scope") SsoProviderScope scope, @Param("protocolType") SsoProviderProtocolType protocolType,
                                                      @Param("status") SsoProviderStatus status, Limit limit);

    /** 조건부 다건 삭제(동기화에서 빠진 공급자) — 행마다 로드할 이유가 없어 JPQL {@code @Modifying} */
    @Transactional
    @Modifying
    @Query("delete from SsoProvider p where p.ssoProviderSeq in :ssoProviderSeqList")
    int deleteBySsoProviderSeqIn(@Param("ssoProviderSeqList") Collection<Integer> ssoProviderSeqList);
}
