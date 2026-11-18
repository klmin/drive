package com.thinkfree.drive.recent.repository;

import com.thinkfree.drive.recent.entity.Recent;
import com.thinkfree.drive.share.enums.ShareType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

/**
 * tb_recent Spring Data JPA 선언 인터페이스 — **패키지 전용**. {@link DefaultRecentRepository} 만 주입받아 위임한다.
 * 삭제는 JPQL {@code @Modifying}(이식 가능) — 선택 조건(excludeUserSeqList)은 있음/없음 두 메서드로 나누고, 테넌트는 User 서브쿼리.
 * upsert·(user_seq, resource_seq) 튜플 IN 은 Default 리포의 JdbcClient.
 */
interface RecentJpaRepository extends JpaRepository<Recent, Long> {

    @Transactional
    @Modifying
    @Query("delete from Recent r where r.sharedByUserSeq = :sharedByUserSeq and r.resourceSeq = :resourceSeq")
    int deleteBySharedByUserSeqAndResourceSeq(@Param("sharedByUserSeq") Integer sharedByUserSeq, @Param("resourceSeq") Long resourceSeq);

    @Transactional
    @Modifying
    @Query("delete from Recent r where r.sharedByUserSeq = :sharedByUserSeq and r.resourceSeq in :resourceSeqList")
    int deleteBySharedByUserSeqAndResourceSeqIn(@Param("sharedByUserSeq") Integer sharedByUserSeq, @Param("resourceSeqList") Collection<Long> resourceSeqList);

    @Transactional
    @Modifying
    @Query("delete from Recent r where r.userSeq in :userSeqList")
    int deleteByUserSeqIn(@Param("userSeqList") Collection<Integer> userSeqList);

    @Transactional
    @Modifying
    @Query("delete from Recent r where r.sharedByUserSeq in :sharedByUserSeqList")
    int deleteBySharedByUserSeqIn(@Param("sharedByUserSeqList") Collection<Integer> sharedByUserSeqList);

    /** 소유자 제외(excludeUserSeqList 없음). */
    @Transactional
    @Modifying
    @Query("delete from Recent r where r.userSeq <> :userSeq and r.sharedByUserSeq = :sharedByUserSeq and r.resourceSeq = :resourceSeq")
    int deleteByResourceExcludingOwner(@Param("userSeq") Integer userSeq, @Param("sharedByUserSeq") Integer sharedByUserSeq, @Param("resourceSeq") Long resourceSeq);

    /** 소유자 + excludeUserSeqList 제외. */
    @Transactional
    @Modifying
    @Query("delete from Recent r where r.userSeq <> :userSeq and r.sharedByUserSeq = :sharedByUserSeq and r.resourceSeq = :resourceSeq "
            + "and r.userSeq not in :excludeUserSeqList")
    int deleteByResourceExcludingOwnerAndUsers(@Param("userSeq") Integer userSeq, @Param("sharedByUserSeq") Integer sharedByUserSeq, @Param("resourceSeq") Long resourceSeq,
                                               @Param("excludeUserSeqList") Collection<Integer> excludeUserSeqList);

    /** 소유자 제외(excludeUserSeqList 없음). */
    @Transactional
    @Modifying
    @Query("delete from Recent r where r.userSeq <> :userSeq and r.sharedByUserSeq = :sharedByUserSeq and r.resourceSeq in :resourceSeqList")
    int deleteByResourcesExcludingOwner(@Param("userSeq") Integer userSeq, @Param("sharedByUserSeq") Integer sharedByUserSeq,
                                        @Param("resourceSeqList") Collection<Long> resourceSeqList);

    /** 소유자 + excludeUserSeqList 제외. */
    @Transactional
    @Modifying
    @Query("delete from Recent r where r.userSeq <> :userSeq and r.sharedByUserSeq = :sharedByUserSeq and r.resourceSeq in :resourceSeqList "
            + "and r.userSeq not in :excludeUserSeqList")
    int deleteByResourcesExcludingOwnerAndUsers(@Param("userSeq") Integer userSeq, @Param("sharedByUserSeq") Integer sharedByUserSeq,
                                                @Param("resourceSeqList") Collection<Long> resourceSeqList, @Param("excludeUserSeqList") Collection<Integer> excludeUserSeqList);

    /** 공유자의 리소스 목록에서 테넌트 외부 접근자(excludeUserSeqList 없음). */
    @Transactional
    @Modifying
    @Query("delete from Recent r where r.sharedByUserSeq = :sharedByUserSeq and r.resourceSeq in :resourceSeqList "
            + "and r.userSeq not in (select u.userSeq from User u where u.tenantSeq = :tenantSeq)")
    int deleteOtherTenantByResources(@Param("sharedByUserSeq") Integer sharedByUserSeq, @Param("resourceSeqList") Collection<Long> resourceSeqList,
                                     @Param("tenantSeq") Integer tenantSeq);

    /** 공유자의 리소스 목록에서 테넌트 외부 접근자 + excludeUserSeqList 제외. */
    @Transactional
    @Modifying
    @Query("delete from Recent r where r.sharedByUserSeq = :sharedByUserSeq and r.resourceSeq in :resourceSeqList "
            + "and r.userSeq not in (select u.userSeq from User u where u.tenantSeq = :tenantSeq) and r.userSeq not in :excludeUserSeqList")
    int deleteOtherTenantByResourcesExcludingUsers(@Param("sharedByUserSeq") Integer sharedByUserSeq, @Param("resourceSeqList") Collection<Long> resourceSeqList,
                                                   @Param("tenantSeq") Integer tenantSeq, @Param("excludeUserSeqList") Collection<Integer> excludeUserSeqList);

    /** 테넌트 사용자가 공유한 리소스의 접근자(공유자 본인 제외) 메타데이터. */
    @Transactional
    @Modifying
    @Query("delete from Recent r where r.sharedByUserSeq in (select u.userSeq from User u where u.tenantSeq = :tenantSeq) and r.userSeq <> r.sharedByUserSeq")
    int deleteAllSharedByTenant(@Param("tenantSeq") Integer tenantSeq);

    /** 불허된 scope 공유의 stale 한 접근자 메타데이터: (소유자, resource) 로 살아있는 share 상관 서브쿼리. */
    @Transactional
    @Modifying
    @Query("delete from Recent r where r.sharedByUserSeq in (select u.userSeq from User u where u.tenantSeq = :tenantSeq) and r.userSeq <> r.sharedByUserSeq "
            + "and exists (select s.shareSeq from Share s where s.userSeq = r.sharedByUserSeq and s.resourceSeq = r.resourceSeq and s.shareType in :shareTypes)")
    int deleteSharedByTenantAndShareTypes(@Param("tenantSeq") Integer tenantSeq, @Param("shareTypes") Collection<ShareType> shareTypes);

    /** 외부공유 OFF: 테넌트가 공유한 리소스의 테넌트 외부 접근자 메타데이터. */
    @Transactional
    @Modifying
    @Query("delete from Recent r where r.sharedByUserSeq in (select u.userSeq from User u where u.tenantSeq = :tenantSeq) "
            + "and r.userSeq not in (select u2.userSeq from User u2 where u2.tenantSeq = :tenantSeq)")
    int deleteOtherTenantSharedByTenant(@Param("tenantSeq") Integer tenantSeq);
}
