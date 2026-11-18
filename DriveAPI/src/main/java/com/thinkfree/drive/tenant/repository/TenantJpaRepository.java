package com.thinkfree.drive.tenant.repository;

import org.springframework.data.jpa.repository.Modifying;
import com.thinkfree.drive.tenant.data.TenantData;
import com.thinkfree.drive.tenant.entity.Tenant;
import com.thinkfree.drive.tenant.enums.TenantStatus;
import com.thinkfree.drive.tenant.enums.TenantType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * tb_tenant Spring Data JPA 선언 인터페이스 — **패키지 전용**. {@link DefaultTenantRepository}(identity INSERT save, 고칠 행 로드 = 관리 엔티티)·{@link DefaultTenantQueryRepository}(DTO·스칼라 조회 — JPQL 생성자 식) 가 주입받아 위임한다.
 * 서비스에 노출하지 않는다({@code findAll()/deleteAll()} 같은 원치 않는 문이 열리므로).
 */
interface TenantJpaRepository extends JpaRepository<Tenant, Integer> {

    /** FULL — 등록자(INNER)·수정자(LEFT) 사용자 조인 17컬럼(구 JdbcClient FULL_SELECT, 5차 패스 C) */
    String SELECT_FULL = "select new com.thinkfree.drive.tenant.data.TenantData(a.tenantSeq, a.tenantName, a.domain, a.subdomain, a.baseDomainSeq, "
            + "a.tenantType, a.userMode, a.status, a.loginType, a.registerUserSeq, b.userId, b.userName, a.registerDate, a.updateUserSeq, c.userId, c.userName, a.updateDate) "
            + "from Tenant a join User b on b.userSeq = a.registerUserSeq left join User c on c.userSeq = a.updateUserSeq ";

    /** BASIC — 조인 없는 9컬럼(감사 필드 null). 요청마다 타는 판별·캐시 경로 */
    String SELECT_BASIC = "select new com.thinkfree.drive.tenant.data.TenantData(t.tenantSeq, t.tenantName, t.domain, t.subdomain, t.baseDomainSeq, "
            + "t.tenantType, t.userMode, t.status, t.loginType) from Tenant t ";

    @Transactional(readOnly = true)
    @Query(SELECT_FULL + "where a.tenantSeq = :tenantSeq")
    Optional<TenantData> findFullByTenantSeq(@Param("tenantSeq") Integer tenantSeq);

    /** 목록 첫 페이지 — tenant_seq DESC */
    @Transactional(readOnly = true)
    @Query(SELECT_FULL + "order by a.tenantSeq desc")
    List<TenantData> findFullPage(Limit limit);

    /** 목록 다음 페이지 — 커서(tenant_seq) 미만 */
    @Transactional(readOnly = true)
    @Query(SELECT_FULL + "where a.tenantSeq < :cursorId order by a.tenantSeq desc")
    List<TenantData> findFullPageAfter(@Param("cursorId") Integer cursorId, Limit limit);

    /** 유형 필터 목록 첫 페이지 — tenant_seq DESC */
    @Transactional(readOnly = true)
    @Query(SELECT_FULL + "where a.tenantType = :tenantType order by a.tenantSeq desc")
    List<TenantData> findFullPageByType(@Param("tenantType") TenantType tenantType, Limit limit);

    /** 유형 필터 목록 다음 페이지 — 커서(tenant_seq) 미만 */
    @Transactional(readOnly = true)
    @Query(SELECT_FULL + "where a.tenantType = :tenantType and a.tenantSeq < :cursorId order by a.tenantSeq desc")
    List<TenantData> findFullPageByTypeAfter(@Param("tenantType") TenantType tenantType, @Param("cursorId") Integer cursorId, Limit limit);

    /** domain 은 nullable — null 인자는 {@code =} 로 0건(파생 쿼리면 IS NULL 로 domain 없는 테넌트를 잡음) */
    @Transactional(readOnly = true)
    @Query(SELECT_BASIC + "where t.domain = :domain")
    Optional<TenantData> findBasicByDomain(@Param("domain") String domain);

    /** subdomain 유니크 제약이 없어 중복 시 가장 오래된 테넌트(limit 1) */
    @Transactional(readOnly = true)
    @Query(SELECT_BASIC + "where t.subdomain = :subdomain order by t.tenantSeq")
    List<TenantData> findBasicBySubdomain(@Param("subdomain") String subdomain, Limit limit);

    @Transactional(readOnly = true)
    @Query("select count(t) > 0 from Tenant t where t.domain = :domain")
    boolean existsDomain(@Param("domain") String domain);

    /** 수정 시 — 자기 자신 제외 */
    @Transactional(readOnly = true)
    @Query("select count(t) > 0 from Tenant t where t.domain = :domain and t.tenantSeq <> :tenantSeq")
    boolean existsDomainExcept(@Param("domain") String domain, @Param("tenantSeq") Integer tenantSeq);

    @Transactional(readOnly = true)
    @Query("select t.tenantSeq from Tenant t where t.tenantType = :tenantType")
    List<Integer> findSeqsByType(@Param("tenantType") TenantType tenantType);

    @Transactional(readOnly = true)
    @Query("select t.tenantSeq from Tenant t where t.tenantType = :tenantType and t.status = :status")
    List<Integer> findSeqsByTypeAndStatus(@Param("tenantType") TenantType tenantType, @Param("status") TenantStatus status, Limit limit);

    @Transactional(readOnly = true)
    long countByTenantTypeAndStatus(TenantType tenantType, TenantStatus status);

    /** 쓰기 — 테넌트 행 잠금(SELECT ... FOR UPDATE). 테넌트 총 용량 변경 같은 테넌트 단위 판정을 직렬화한다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tenant t where t.tenantSeq = :tenantSeq")
    Optional<Tenant> lockByTenantSeq(@Param("tenantSeq") Integer tenantSeq);

    /** 쓰기 — 베이스 도메인 첫 등록 시 재바인딩 대상(관리 엔티티) */
    @Transactional(readOnly = true)
    List<Tenant> findByTenantTypeAndDomainIsNotNull(TenantType tenantType);

    /** 쓰기 — 베이스 도메인 변경 시 재바인딩 대상(관리 엔티티). 인자는 베이스 도메인 PK 라 null 이 오지 않아 파생 쿼리 */
    @Transactional(readOnly = true)
    List<Tenant> findByBaseDomainSeq(Integer baseDomainSeq);

    /** 테넌트 행 삭제(tb_tenant 를 참조하는 FK 없음) */
    @Transactional
    @Modifying
    @Query("delete from Tenant t where t.tenantSeq = :tenantSeq")
    int deleteByTenantSeq(@Param("tenantSeq") Integer tenantSeq);
}
