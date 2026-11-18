package com.thinkfree.drive.share.target.repository;

import com.thinkfree.drive.share.enums.ShareType;
import com.thinkfree.drive.share.target.data.ShareTargetPartitionKeyData;
import com.thinkfree.drive.share.target.data.ShareTargetUserData;
import com.thinkfree.drive.share.target.entity.ShareTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/**
 * tb_share_target Spring Data JPA 선언 인터페이스 — **패키지 전용**. {@link DefaultShareTargetRepository} 만 주입받아 위임한다.
 * 삭제는 JPQL {@code @Modifying}(이식 가능, 테넌트는 User 서브쿼리). 배치 INSERT·upsert·튜플 IN 삭제는 Default 리포의 JdbcClient.
 */
interface ShareTargetJpaRepository extends JpaRepository<ShareTarget, Long> {

    /** 공유 하나의 대상 파티션 키(생성자 식, 영속 컨텍스트에 안 올림) */
    @Transactional(readOnly = true)
    @Query("select new com.thinkfree.drive.share.target.data.ShareTargetPartitionKeyData(t.userSeq, t.resourceSeq, t.sharedByUserSeq) from ShareTarget t "
            + "where t.shareSeq = :shareSeq and t.sharedByUserSeq = :sharedByUserSeq")
    List<ShareTargetPartitionKeyData> findPartitionKeys(@Param("shareSeq") Long shareSeq, @Param("sharedByUserSeq") Integer sharedByUserSeq);

    // ------------------------------------------------------------------ 공유 대상 사용자(구 JdbcClient 3문, 5차 패스 C) — 대상 ⋈ 공유(공유자 파티션·share_seq·resource_seq)

    String TARGET_USER_JOIN_SHARE = "select new com.thinkfree.drive.share.target.data.ShareTargetUserData(t.userSeq) from ShareTarget t "
            + "join Share s on s.userSeq = t.sharedByUserSeq and s.shareSeq = t.shareSeq and s.resourceSeq = t.resourceSeq ";

    /** 공유 목록의 대상 — 리소스는 공유자 파티션, 삭제 여부 일치 */
    @Transactional(readOnly = true)
    @Query(TARGET_USER_JOIN_SHARE + "join Resource r on r.userSeq = s.userSeq and r.resourceSeq = s.resourceSeq and r.isDelete = :isDelete "
            + "where t.sharedByUserSeq = :sharedByUserSeq and t.shareSeq in :shareSeqList")
    List<ShareTargetUserData> findUsersByShares(@Param("sharedByUserSeq") Integer sharedByUserSeq, @Param("shareSeqList") Collection<Long> shareSeqList,
                                                @Param("isDelete") Boolean isDelete);

    /**
     * 리소스의 대상 — 리소스는 targetUserSeq 파티션(구 SQL 그대로). 호출부는 이동 후처리(processUnsharedByMoveAndNotify) 하나로,
     * 이동이 끝난 리소스는 이동 대상 사용자(targetUserSeq) 파티션에 있고 공유·대상 행은 이동 전 공유자(sharedByUserSeq) 기준이라 의도된 조인(작업 E4 확인).
     */
    @Transactional(readOnly = true)
    @Query(TARGET_USER_JOIN_SHARE + "join Resource r on r.userSeq = :targetUserSeq and r.resourceSeq = s.resourceSeq and r.isDelete = :isDelete "
            + "where t.sharedByUserSeq = :sharedByUserSeq and t.resourceSeq = :resourceSeq")
    List<ShareTargetUserData> findUsersByResource(@Param("sharedByUserSeq") Integer sharedByUserSeq, @Param("targetUserSeq") Integer targetUserSeq,
                                                  @Param("resourceSeq") Long resourceSeq, @Param("isDelete") Boolean isDelete);

    /** 삭제된(행이 없을 수 있는) 리소스의 대상 — 리소스 조인 없음 */
    @Transactional(readOnly = true)
    @Query(TARGET_USER_JOIN_SHARE + "where t.sharedByUserSeq = :sharedByUserSeq and t.resourceSeq = :resourceSeq")
    List<ShareTargetUserData> findUsersByDeletedResource(@Param("sharedByUserSeq") Integer sharedByUserSeq, @Param("resourceSeq") Long resourceSeq);

    @Transactional
    @Modifying
    @Query("delete from ShareTarget t where t.sharedByUserSeq = :sharedByUserSeq and t.resourceSeq = :resourceSeq")
    int deleteBySharedByUserSeqAndResourceSeq(@Param("sharedByUserSeq") Integer sharedByUserSeq, @Param("resourceSeq") Long resourceSeq);

    @Transactional
    @Modifying
    @Query("delete from ShareTarget t where t.sharedByUserSeq = :sharedByUserSeq and t.resourceSeq in :resourceSeqList")
    int deleteBySharedByUserSeqAndResourceSeqIn(@Param("sharedByUserSeq") Integer sharedByUserSeq, @Param("resourceSeqList") Collection<Long> resourceSeqList);

    @Transactional
    @Modifying
    @Query("delete from ShareTarget t where t.userSeq in :userSeqList")
    int deleteByUserSeqIn(@Param("userSeqList") Collection<Integer> userSeqList);

    @Transactional
    @Modifying
    @Query("delete from ShareTarget t where t.sharedByUserSeq in :sharedByUserSeqList")
    int deleteBySharedByUserSeqIn(@Param("sharedByUserSeqList") Collection<Integer> sharedByUserSeqList);

    /** 테넌트 사용자가 공유한 대상(공유자 본인 제외). */
    @Transactional
    @Modifying
    @Query("delete from ShareTarget t where t.sharedByUserSeq in (select u.userSeq from User u where u.tenantSeq = :tenantSeq) and t.userSeq <> t.sharedByUserSeq")
    int deleteByTenant(@Param("tenantSeq") Integer tenantSeq);

    /** 불허된 scope 공유에 속한 대상: 테넌트 소유 + (소유자, share_seq) 로 살아있는 share 상관 서브쿼리. */
    @Transactional
    @Modifying
    @Query("delete from ShareTarget t where t.sharedByUserSeq in (select u.userSeq from User u where u.tenantSeq = :tenantSeq) "
            + "and exists (select s.shareSeq from Share s where s.userSeq = t.sharedByUserSeq and s.shareSeq = t.shareSeq and s.shareType in :shareTypes)")
    int deleteByTenantAndShareTypes(@Param("tenantSeq") Integer tenantSeq, @Param("shareTypes") Collection<ShareType> shareTypes);

    /** 외부공유 OFF: 테넌트가 공유한 대상 중 테넌트 외부 접근자. */
    @Transactional
    @Modifying
    @Query("delete from ShareTarget t where t.sharedByUserSeq in (select u.userSeq from User u where u.tenantSeq = :tenantSeq) "
            + "and t.userSeq not in (select u2.userSeq from User u2 where u2.tenantSeq = :tenantSeq)")
    int deleteOtherTenantByTenant(@Param("tenantSeq") Integer tenantSeq);
}
