package com.thinkfree.drive.pinnedfolder.repository;

import com.thinkfree.drive.pinnedfolder.data.PinnedFolderData;
import com.thinkfree.drive.pinnedfolder.entity.PinnedFolder;
import com.thinkfree.drive.share.enums.ShareType;
import com.thinkfree.drive.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * tb_pinned_folder Spring Data JPA 선언 인터페이스 — **패키지 전용**. {@link DefaultPinnedFolderRepository}·{@link DefaultPinnedFolderQueryRepository}(존재 확인) 가 주입받아 위임한다.
 * identity INSERT(save) + 삭제 JPQL {@code @Modifying}(이식 가능 — 조건부 다건 또는 로드 없는 키 삭제라 변경 감지·remove 대상이 아님) — 선택 조건(excludeUserSeqList)은 있음/없음 두 메서드로 나누고, 테넌트는 User 서브쿼리.
 * 상한 검사 건수({@code countByUserSeq})는 Default 리포가 사용자 행을 잠근 뒤 호출. (user_seq, resource_seq) 튜플 IN 삭제는 없음.
 */
interface PinnedFolderJpaRepository extends JpaRepository<PinnedFolder, Long> {

    /** 고정 폴더 목록 — 리소스는 공유자(shared_by_user_seq) 파티션으로 조인, 등록일·seq 순(구 JdbcClient, 5차 패스 C) */
    @Transactional(readOnly = true)
    @Query("select new com.thinkfree.drive.pinnedfolder.data.PinnedFolderData(p.pinnedFolderSeq, p.resourceSeq, p.sharedByUserSeq, r.resourceName, r.resourceType) "
            + "from PinnedFolder p join Resource r on r.userSeq = p.sharedByUserSeq and r.resourceSeq = p.resourceSeq "
            + "where p.userSeq = :userSeq order by p.registerDate, p.pinnedFolderSeq")
    List<PinnedFolderData> findPinnedFolders(@Param("userSeq") Integer userSeq);

    /**
     * 고정 폴더 상한 검사용 사용자 행 잠금(SELECT … FOR UPDATE 계열) — 행 잠금만 목적이라 받은 User 는 고치지 않는다(트랜잭션 끝까지 잠금 유지, W3 10-13).
     * 사용자 행은 고정 폴더 저장 흐름의 첫 로드라 잠금이 첫 로드에서 걸린다.
     */
    @Transactional
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.userSeq = :userSeq")
    Optional<User> lockUser(@Param("userSeq") Integer userSeq);

    /** 고정 폴더 상한 검사 — 사용자 행 잠금(lockUser) 뒤 별도 문장으로 센다 */
    @Transactional(readOnly = true)
    long countByUserSeq(Integer userSeq);

    @Transactional(readOnly = true)
    @Query("select count(p) > 0 from PinnedFolder p where p.userSeq = :userSeq and p.resourceSeq = :resourceSeq and p.sharedByUserSeq = :sharedByUserSeq")
    boolean existsPinned(@Param("userSeq") Integer userSeq, @Param("resourceSeq") Long resourceSeq, @Param("sharedByUserSeq") Integer sharedByUserSeq);

    @Transactional
    @Modifying
    @Query("delete from PinnedFolder p where p.userSeq = :userSeq and p.pinnedFolderSeq = :pinnedFolderSeq")
    int deleteByUserSeqAndPinnedFolderSeq(@Param("userSeq") Integer userSeq, @Param("pinnedFolderSeq") Long pinnedFolderSeq);

    @Transactional
    @Modifying
    @Query("delete from PinnedFolder p where p.sharedByUserSeq = :sharedByUserSeq and p.resourceSeq = :resourceSeq")
    int deleteBySharedByUserSeqAndResourceSeq(@Param("sharedByUserSeq") Integer sharedByUserSeq, @Param("resourceSeq") Long resourceSeq);

    @Transactional
    @Modifying
    @Query("delete from PinnedFolder p where p.sharedByUserSeq = :sharedByUserSeq and p.resourceSeq in :resourceSeqList")
    int deleteBySharedByUserSeqAndResourceSeqIn(@Param("sharedByUserSeq") Integer sharedByUserSeq, @Param("resourceSeqList") Collection<Long> resourceSeqList);

    @Transactional
    @Modifying
    @Query("delete from PinnedFolder p where p.userSeq in :userSeqList")
    int deleteByUserSeqIn(@Param("userSeqList") Collection<Integer> userSeqList);

    @Transactional
    @Modifying
    @Query("delete from PinnedFolder p where p.sharedByUserSeq in :sharedByUserSeqList")
    int deleteBySharedByUserSeqIn(@Param("sharedByUserSeqList") Collection<Integer> sharedByUserSeqList);

    /** 소유자 제외(excludeUserSeqList 없음). */
    @Transactional
    @Modifying
    @Query("delete from PinnedFolder p where p.userSeq <> :userSeq and p.sharedByUserSeq = :sharedByUserSeq and p.resourceSeq in :resourceSeqList")
    int deleteByResourcesExcludingOwner(@Param("userSeq") Integer userSeq, @Param("sharedByUserSeq") Integer sharedByUserSeq,
                                        @Param("resourceSeqList") Collection<Long> resourceSeqList);

    /** 소유자 + excludeUserSeqList 제외. */
    @Transactional
    @Modifying
    @Query("delete from PinnedFolder p where p.userSeq <> :userSeq and p.sharedByUserSeq = :sharedByUserSeq and p.resourceSeq in :resourceSeqList "
            + "and p.userSeq not in :excludeUserSeqList")
    int deleteByResourcesExcludingOwnerAndUsers(@Param("userSeq") Integer userSeq, @Param("sharedByUserSeq") Integer sharedByUserSeq,
                                                @Param("resourceSeqList") Collection<Long> resourceSeqList, @Param("excludeUserSeqList") Collection<Integer> excludeUserSeqList);

    /** 공유자의 리소스 목록에서 테넌트 외부 접근자(excludeUserSeqList 없음). */
    @Transactional
    @Modifying
    @Query("delete from PinnedFolder p where p.sharedByUserSeq = :sharedByUserSeq and p.resourceSeq in :resourceSeqList "
            + "and p.userSeq not in (select u.userSeq from User u where u.tenantSeq = :tenantSeq)")
    int deleteOtherTenantByResources(@Param("sharedByUserSeq") Integer sharedByUserSeq, @Param("resourceSeqList") Collection<Long> resourceSeqList,
                                     @Param("tenantSeq") Integer tenantSeq);

    /** 공유자의 리소스 목록에서 테넌트 외부 접근자 + excludeUserSeqList 제외. */
    @Transactional
    @Modifying
    @Query("delete from PinnedFolder p where p.sharedByUserSeq = :sharedByUserSeq and p.resourceSeq in :resourceSeqList "
            + "and p.userSeq not in (select u.userSeq from User u where u.tenantSeq = :tenantSeq) and p.userSeq not in :excludeUserSeqList")
    int deleteOtherTenantByResourcesExcludingUsers(@Param("sharedByUserSeq") Integer sharedByUserSeq, @Param("resourceSeqList") Collection<Long> resourceSeqList,
                                                   @Param("tenantSeq") Integer tenantSeq, @Param("excludeUserSeqList") Collection<Integer> excludeUserSeqList);

    /** 테넌트 사용자가 공유한 리소스의 접근자(공유자 본인 제외) 메타데이터. */
    @Transactional
    @Modifying
    @Query("delete from PinnedFolder p where p.sharedByUserSeq in (select u.userSeq from User u where u.tenantSeq = :tenantSeq) and p.userSeq <> p.sharedByUserSeq")
    int deleteAllSharedByTenant(@Param("tenantSeq") Integer tenantSeq);

    /** 불허된 scope 공유의 stale 한 접근자 메타데이터: (소유자, resource) 로 살아있는 share 상관 서브쿼리. */
    @Transactional
    @Modifying
    @Query("delete from PinnedFolder p where p.sharedByUserSeq in (select u.userSeq from User u where u.tenantSeq = :tenantSeq) and p.userSeq <> p.sharedByUserSeq "
            + "and exists (select s.shareSeq from Share s where s.userSeq = p.sharedByUserSeq and s.resourceSeq = p.resourceSeq and s.shareType in :shareTypes)")
    int deleteSharedByTenantAndShareTypes(@Param("tenantSeq") Integer tenantSeq, @Param("shareTypes") Collection<ShareType> shareTypes);

    /** 외부공유 OFF: 테넌트가 공유한 리소스의 테넌트 외부 접근자 메타데이터. */
    @Transactional
    @Modifying
    @Query("delete from PinnedFolder p where p.sharedByUserSeq in (select u.userSeq from User u where u.tenantSeq = :tenantSeq) "
            + "and p.userSeq not in (select u2.userSeq from User u2 where u2.tenantSeq = :tenantSeq)")
    int deleteOtherTenantSharedByTenant(@Param("tenantSeq") Integer tenantSeq);
}
