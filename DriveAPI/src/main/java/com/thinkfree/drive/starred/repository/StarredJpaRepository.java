package com.thinkfree.drive.starred.repository;

import com.thinkfree.drive.share.enums.ShareType;
import com.thinkfree.drive.starred.entity.Starred;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

/**
 * tb_starred Spring Data JPA 선언 인터페이스 — **패키지 전용**. {@link DefaultStarredRepository} 만 주입받아 위임한다.
 * identity INSERT(save) + 삭제 JPQL {@code @Modifying}(이식 가능) — 선택 조건(excludeUserSeqList)은 있음/없음 두 메서드로 나누고, 테넌트는 User 서브쿼리.
 * (user_seq, resource_seq) 튜플 IN 삭제만 Default 리포의 JdbcClient.
 */
interface StarredJpaRepository extends JpaRepository<Starred, Long> {

    @Transactional
    @Modifying
    @Query("delete from Starred s where s.starredSeq = :starredSeq and s.userSeq = :userSeq")
    int deleteByStarredSeqAndUserSeq(@Param("starredSeq") Long starredSeq, @Param("userSeq") Integer userSeq);

    @Transactional
    @Modifying
    @Query("delete from Starred s where s.sharedByUserSeq = :sharedByUserSeq and s.resourceSeq = :resourceSeq")
    int deleteBySharedByUserSeqAndResourceSeq(@Param("sharedByUserSeq") Integer sharedByUserSeq, @Param("resourceSeq") Long resourceSeq);

    @Transactional
    @Modifying
    @Query("delete from Starred s where s.sharedByUserSeq = :sharedByUserSeq and s.resourceSeq in :resourceSeqList")
    int deleteBySharedByUserSeqAndResourceSeqIn(@Param("sharedByUserSeq") Integer sharedByUserSeq, @Param("resourceSeqList") Collection<Long> resourceSeqList);

    @Transactional
    @Modifying
    @Query("delete from Starred s where s.userSeq in :userSeqList")
    int deleteByUserSeqIn(@Param("userSeqList") Collection<Integer> userSeqList);

    @Transactional
    @Modifying
    @Query("delete from Starred s where s.sharedByUserSeq in :sharedByUserSeqList")
    int deleteBySharedByUserSeqIn(@Param("sharedByUserSeqList") Collection<Integer> sharedByUserSeqList);

    /** 소유자 제외(excludeUserSeqList 없음). */
    @Transactional
    @Modifying
    @Query("delete from Starred s where s.userSeq <> :userSeq and s.sharedByUserSeq = :sharedByUserSeq and s.resourceSeq = :resourceSeq")
    int deleteByResourceExcludingOwner(@Param("userSeq") Integer userSeq, @Param("sharedByUserSeq") Integer sharedByUserSeq, @Param("resourceSeq") Long resourceSeq);

    /** 소유자 + excludeUserSeqList 제외. */
    @Transactional
    @Modifying
    @Query("delete from Starred s where s.userSeq <> :userSeq and s.sharedByUserSeq = :sharedByUserSeq and s.resourceSeq = :resourceSeq "
            + "and s.userSeq not in :excludeUserSeqList")
    int deleteByResourceExcludingOwnerAndUsers(@Param("userSeq") Integer userSeq, @Param("sharedByUserSeq") Integer sharedByUserSeq, @Param("resourceSeq") Long resourceSeq,
                                               @Param("excludeUserSeqList") Collection<Integer> excludeUserSeqList);

    /** 소유자 제외(excludeUserSeqList 없음). */
    @Transactional
    @Modifying
    @Query("delete from Starred s where s.userSeq <> :userSeq and s.sharedByUserSeq = :sharedByUserSeq and s.resourceSeq in :resourceSeqList")
    int deleteByResourcesExcludingOwner(@Param("userSeq") Integer userSeq, @Param("sharedByUserSeq") Integer sharedByUserSeq,
                                        @Param("resourceSeqList") Collection<Long> resourceSeqList);

    /** 소유자 + excludeUserSeqList 제외. */
    @Transactional
    @Modifying
    @Query("delete from Starred s where s.userSeq <> :userSeq and s.sharedByUserSeq = :sharedByUserSeq and s.resourceSeq in :resourceSeqList "
            + "and s.userSeq not in :excludeUserSeqList")
    int deleteByResourcesExcludingOwnerAndUsers(@Param("userSeq") Integer userSeq, @Param("sharedByUserSeq") Integer sharedByUserSeq,
                                                @Param("resourceSeqList") Collection<Long> resourceSeqList, @Param("excludeUserSeqList") Collection<Integer> excludeUserSeqList);

    /** 공유자의 리소스 목록에서 테넌트 외부 접근자(excludeUserSeqList 없음). */
    @Transactional
    @Modifying
    @Query("delete from Starred s where s.sharedByUserSeq = :sharedByUserSeq and s.resourceSeq in :resourceSeqList "
            + "and s.userSeq not in (select u.userSeq from User u where u.tenantSeq = :tenantSeq)")
    int deleteOtherTenantByResources(@Param("sharedByUserSeq") Integer sharedByUserSeq, @Param("resourceSeqList") Collection<Long> resourceSeqList,
                                     @Param("tenantSeq") Integer tenantSeq);

    /** 공유자의 리소스 목록에서 테넌트 외부 접근자 + excludeUserSeqList 제외. */
    @Transactional
    @Modifying
    @Query("delete from Starred s where s.sharedByUserSeq = :sharedByUserSeq and s.resourceSeq in :resourceSeqList "
            + "and s.userSeq not in (select u.userSeq from User u where u.tenantSeq = :tenantSeq) and s.userSeq not in :excludeUserSeqList")
    int deleteOtherTenantByResourcesExcludingUsers(@Param("sharedByUserSeq") Integer sharedByUserSeq, @Param("resourceSeqList") Collection<Long> resourceSeqList,
                                                   @Param("tenantSeq") Integer tenantSeq, @Param("excludeUserSeqList") Collection<Integer> excludeUserSeqList);

    /** 테넌트 사용자가 공유한 리소스의 접근자(공유자 본인 제외) 메타데이터. */
    @Transactional
    @Modifying
    @Query("delete from Starred s where s.sharedByUserSeq in (select u.userSeq from User u where u.tenantSeq = :tenantSeq) and s.userSeq <> s.sharedByUserSeq")
    int deleteAllSharedByTenant(@Param("tenantSeq") Integer tenantSeq);

    /** 불허된 scope 공유의 stale 한 접근자 메타데이터: (소유자, resource) 로 살아있는 share 상관 서브쿼리. */
    @Transactional
    @Modifying
    @Query("delete from Starred s where s.sharedByUserSeq in (select u.userSeq from User u where u.tenantSeq = :tenantSeq) and s.userSeq <> s.sharedByUserSeq "
            + "and exists (select sh.shareSeq from Share sh where sh.userSeq = s.sharedByUserSeq and sh.resourceSeq = s.resourceSeq and sh.shareType in :shareTypes)")
    int deleteSharedByTenantAndShareTypes(@Param("tenantSeq") Integer tenantSeq, @Param("shareTypes") Collection<ShareType> shareTypes);

    /** 외부공유 OFF: 테넌트가 공유한 리소스의 테넌트 외부 접근자 메타데이터. */
    @Transactional
    @Modifying
    @Query("delete from Starred s where s.sharedByUserSeq in (select u.userSeq from User u where u.tenantSeq = :tenantSeq) "
            + "and s.userSeq not in (select u2.userSeq from User u2 where u2.tenantSeq = :tenantSeq)")
    int deleteOtherTenantSharedByTenant(@Param("tenantSeq") Integer tenantSeq);
}
