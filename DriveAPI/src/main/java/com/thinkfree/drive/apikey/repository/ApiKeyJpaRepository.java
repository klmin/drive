package com.thinkfree.drive.apikey.repository;

import com.thinkfree.drive.apikey.data.ApiKeyData;
import com.thinkfree.drive.apikey.entity.ApiKey;
import com.thinkfree.drive.apikey.enums.ApiKeyStatus;
import com.thinkfree.drive.user.enums.RoleType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * tb_api_key Spring Data JPA 선언 인터페이스 — **패키지 전용**. {@link DefaultApiKeyRepository}(INSERT save·고칠 행 findById·IN 삭제·last_used JPQL)·{@link DefaultApiKeyQueryRepository}(목록·단건·인증 조회 — 전부 생성자 식)
 * 가 주입받아 위임한다. 서비스에 노출하지 않는다({@code findAll()/deleteAll()} 같은 원치 않는 문이 열리므로).
 * <p>
 * 인증 조회는 JPQL 생성자 식({@link ApiKeyData})이라 영속 컨텍스트에 엔티티가 남지 않는다 — 같은 트랜잭션의 조작 JPQL 뒤 재조회도 stale 없음.
 * 조작 3종(만료일·상태·재발급)의 고칠 행 로드는 쓰기 리포 {@code findById}(관리 엔티티 — 범위 판정·변경은 Service, W5). last_used_date 는 JPQL(인증마다·감사 컬럼 제외).
 */
interface ApiKeyJpaRepository extends JpaRepository<ApiKey, Integer> {

    String SELECT_DATA = "select new com.thinkfree.drive.apikey.data.ApiKeyData("
            + "k.apiKeySeq, k.tenantSeq, k.userSeq, k.keyName, k.keyPurpose, k.apiKey, k.roleType, k.scope, k.status, "
            + "k.expireDate, k.lastUsedDate, k.registerUserSeq, k.registerDate, k.isProtected) from ApiKey k ";

    // ------------------------------------------------------------------ 목록 3종(구 JdbcClient, 5차 패스 C) — 전체 18컬럼 생성자, api_key_seq DESC 커서(선택)

    /** 목록 공통 앞부분 — 뒤에 userId·userName·tenantName 식 + {@link #LIST_TAIL} */
    String LIST_HEAD = "select new com.thinkfree.drive.apikey.data.ApiKeyData(k.apiKeySeq, k.tenantSeq, k.userSeq, ";
    /** is_expired = 조회 시점 DB 시계(instant = PG CURRENT_TIMESTAMP, 구 NOW() 와 같음 — expire_date NOT NULL 이라 true/false) */
    String LIST_TAIL = ", k.keyName, k.keyPurpose, k.apiKey, k.roleType, k.scope, k.status, k.expireDate, "
            + "case when k.expireDate <= instant then true else false end, k.lastUsedDate, k.registerUserSeq, k.registerDate, k.isProtected) from ApiKey k ";
    String LIST_ORDER = "and (:cursorId is null or k.apiKeySeq < :cursorId) order by k.apiKeySeq desc";

    /** 관리자 목록 — 자기 테넌트의 지정 role(ADMIN·USER) 키, 대상 사용자 표시. registerUserSeq 를 주면 본인 발급 키로 좁힌다 */
    @Transactional(readOnly = true)
    @Query(LIST_HEAD + "u.userId, u.userName, cast(null as String)" + LIST_TAIL + "join User u on u.userSeq = k.userSeq "
            + "where k.tenantSeq = :tenantSeq and k.roleType in :roleTypes "
            + "and (:registerUserSeq is null or k.registerUserSeq = :registerUserSeq) " + LIST_ORDER)
    List<ApiKeyData> findListByTenant(@Param("tenantSeq") Integer tenantSeq, @Param("roleTypes") Collection<RoleType> roleTypes,
                                      @Param("registerUserSeq") Integer registerUserSeq,
                                      @Param("cursorId") Integer cursorId, Limit limit);

    /** 사용자 목록 — 본인이 발급한 키(사용자·테넌트 표시 없음) */
    @Transactional(readOnly = true)
    @Query(LIST_HEAD + "cast(null as String), cast(null as String), cast(null as String)" + LIST_TAIL
            + "where k.registerUserSeq = :registerUserSeq " + LIST_ORDER)
    List<ApiKeyData> findListByRegisterUser(@Param("registerUserSeq") Integer registerUserSeq, @Param("cursorId") Integer cursorId, Limit limit);

    /** 슈퍼관리자 목록 — 전체(tenantSeq·registerUserSeq 선택), 슈퍼관리자 키는 tenant_seq NULL 이라 테넌트 LEFT JOIN */
    @Transactional(readOnly = true)
    @Query(LIST_HEAD + "u.userId, u.userName, t.tenantName" + LIST_TAIL + "join User u on u.userSeq = k.userSeq left join Tenant t on t.tenantSeq = k.tenantSeq "
            + "where (:tenantSeq is null or k.tenantSeq = :tenantSeq) "
            + "and (:registerUserSeq is null or k.registerUserSeq = :registerUserSeq) " + LIST_ORDER)
    List<ApiKeyData> findListForSuperAdmin(@Param("tenantSeq") Integer tenantSeq, @Param("registerUserSeq") Integer registerUserSeq,
                                           @Param("cursorId") Integer cursorId, Limit limit);

    /**
     * 인증용 — 같은 테넌트(슈퍼관리자 키는 tenant_seq NULL 이라 role 로 판단) · ACTIVE · 미만료.
     * 만료는 DB 시계 비교 — HQL {@code instant} 는 PG {@code CURRENT_TIMESTAMP}(timestamptz). {@code current_timestamp} 는 Hibernate 가 {@code localtimestamp} 로 내보내 세션 시간대 변환을 거치므로 쓰지 않는다 — 목록의 is_expired 와 같은 시계.
     */
    /** PK 단건 — 상태·테넌트 조건 없음(판정은 호출부 Java) */
    @Transactional(readOnly = true)
    @Query(SELECT_DATA + "where k.apiKeySeq = :apiKeySeq")
    Optional<ApiKeyData> findDataById(@Param("apiKeySeq") Integer apiKeySeq);

    @Transactional(readOnly = true)
    @Query(SELECT_DATA + "where k.apiKey = :apiKey and (k.tenantSeq = :tenantSeq or k.roleType = :superAdmin) "
            + "and k.status = :active and k.expireDate > instant")
    Optional<ApiKeyData> findActiveData(@Param("apiKey") String apiKey, @Param("tenantSeq") Integer tenantSeq,
                                        @Param("superAdmin") RoleType superAdmin, @Param("active") ApiKeyStatus active);

    /** 계정 완전 삭제 cascade — 소유 키 행 삭제(INACTIVE 로 남기면 재활성화로 되살아나는 모순). 조건부 다건이라 JPQL {@code @Modifying} */
    @Transactional
    @Modifying
    @Query("delete from ApiKey k where k.userSeq in :userSeqs")
    int deleteByUserSeqIn(@Param("userSeqs") Collection<Integer> userSeqs);

    /**
     * 인증 필터가 매 요청 부르는 단일 컬럼 갱신 — 로드 없이 1문이라 JPQL {@code @Modifying} 유지(변경 감지면 로드 SELECT 가 더해지고
     * Auditing 이 update_* 를 덮어써 수정일이 마지막 사용일이 된다)
     */
    @Transactional
    @Modifying
    @Query("update ApiKey k set k.lastUsedDate = :lastUsedDate where k.apiKey = :apiKey")
    int updateLastUsedDate(@Param("apiKey") String apiKey, @Param("lastUsedDate") Instant lastUsedDate);

    /** 테넌트 삭제 — 이 테넌트의 행 전부(조건부 다건이라 JPQL @Modifying) */
    @Transactional
    @Modifying
    @Query("delete from ApiKey a where a.tenantSeq = :tenantSeq")
    int deleteByTenantSeq(@Param("tenantSeq") Integer tenantSeq);
}
