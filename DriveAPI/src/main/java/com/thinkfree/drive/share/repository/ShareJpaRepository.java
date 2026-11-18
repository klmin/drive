package com.thinkfree.drive.share.repository;

import com.thinkfree.drive.share.data.SharedResourceData;
import com.thinkfree.drive.share.entity.Share;
import com.thinkfree.drive.share.enums.ShareType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Optional;

/**
 * tb_share Spring Data JPA 선언 인터페이스 — **패키지 전용**. {@link DefaultShareRepository}·{@link DefaultShareQueryRepository}(existsByTenant·단건 DTO 2종) 만 주입받아 위임한다.
 * 서비스에 노출하지 않는다. 위임 범위 = identity INSERT(save) + 변경 감지용 단건 로드(user_seq 조건) + DELETE 6종(테넌트는 User 서브쿼리) JPQL {@code @Modifying}·테넌트 존재 여부.
 */
interface ShareJpaRepository extends JpaRepository<Share, Long> {

    /** 단건 로드(변경 감지용, 잠금 없음) — 파생 쿼리 — 파티션 키 user_seq 를 조건에 넣어 한 파티션만 */
    Optional<Share> findByUserSeqAndShareSeq(Integer userSeq, Long shareSeq);

    // ------------------------------------------------------------------ 단건 DTO(5차 패스 — 구 JdbcClient 조인·손 매퍼). Share·Resource·User 엔티티 조인, 리소스 is_delete 가드

    String SHARE_RESOURCE_OWNER_JOIN = "from Share s join Resource r on r.resourceSeq = s.resourceSeq and r.userSeq = s.userSeq "
            + "join User u on u.userSeq = s.userSeq ";

    String SELECT_SHARED_RESOURCE = "select new com.thinkfree.drive.share.data.SharedResourceData("
            + "s.shareSeq, s.resourceSeq, s.userSeq, u.tenantSeq, s.shareType, r.resourceType, r.resourceName) " + SHARE_RESOURCE_OWNER_JOIN;

    /** 공유 seq 기준 — 공유 + 리소스 타입·이름 + 소유자 테넌트 */
    @Transactional(readOnly = true)
    @Query(SELECT_SHARED_RESOURCE + "where s.shareSeq = :shareSeq and s.userSeq = :userSeq and r.isDelete = :isDelete")
    Optional<SharedResourceData> findSharedResourceByShare(@Param("shareSeq") Long shareSeq, @Param("userSeq") Integer userSeq, @Param("isDelete") Boolean isDelete);

    /** 리소스 seq 기준 — 같은 리소스에 공유가 둘 이상이면 IncorrectResultSizeDataAccessException(구 JdbcClient optional 과 같음) */
    @Transactional(readOnly = true)
    @Query(SELECT_SHARED_RESOURCE + "where s.resourceSeq = :resourceSeq and s.userSeq = :userSeq and r.isDelete = :isDelete")
    Optional<SharedResourceData> findSharedResourceByResource(@Param("resourceSeq") Long resourceSeq, @Param("userSeq") Integer userSeq, @Param("isDelete") Boolean isDelete);

    @Transactional
    @Modifying
    @Query("delete from Share s where s.userSeq = :userSeq and s.shareSeq = :shareSeq")
    int deleteByUserSeqAndShareSeq(@Param("userSeq") Integer userSeq, @Param("shareSeq") Long shareSeq);

    @Transactional
    @Modifying
    @Query("delete from Share s where s.userSeq = :userSeq and s.resourceSeq = :resourceSeq")
    int deleteByUserSeqAndResourceSeq(@Param("userSeq") Integer userSeq, @Param("resourceSeq") Long resourceSeq);

    @Transactional
    @Modifying
    @Query("delete from Share s where s.userSeq = :userSeq and s.resourceSeq in :resourceSeqList")
    int deleteByUserSeqAndResourceSeqIn(@Param("userSeq") Integer userSeq, @Param("resourceSeqList") Collection<Long> resourceSeqList);

    @Transactional
    @Modifying
    @Query("delete from Share s where s.userSeq in :userSeqList")
    int deleteByUserSeqIn(@Param("userSeqList") Collection<Integer> userSeqList);

    /** 테넌트 사용자 소유 공유 전부. */
    @Transactional
    @Modifying
    @Query("delete from Share s where s.userSeq in (select u.userSeq from User u where u.tenantSeq = :tenantSeq)")
    int deleteByTenant(@Param("tenantSeq") Integer tenantSeq);

    /** 테넌트 사용자 소유 공유 중 지정 공유 유형만. */
    @Transactional
    @Modifying
    @Query("delete from Share s where s.userSeq in (select u.userSeq from User u where u.tenantSeq = :tenantSeq) and s.shareType in :shareTypes")
    int deleteByTenantAndShareTypes(@Param("tenantSeq") Integer tenantSeq, @Param("shareTypes") Collection<ShareType> shareTypes);

    /** 테넌트 사용자 소유 공유가 하나라도 있는지. */
    @Transactional(readOnly = true)
    @Query("select case when count(s) > 0 then true else false end from Share s where s.userSeq in (select u.userSeq from User u where u.tenantSeq = :tenantSeq)")
    boolean existsByTenant(@Param("tenantSeq") Integer tenantSeq);
}
