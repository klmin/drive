package com.thinkfree.drive.resource.repository;

import com.thinkfree.drive.global.jpa.audit.UpdateStamp;
import com.thinkfree.drive.resource.data.ResourceDetailData;
import com.thinkfree.drive.resource.data.ResourceLinkTokenData;
import com.thinkfree.drive.resource.data.ResourcePathData;
import com.thinkfree.drive.resource.data.ResourceStorageKeyData;
import com.thinkfree.drive.resource.entity.Resource;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.user.enums.UserStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * tb_resource Spring Data JPA 선언 인터페이스 — **패키지 전용**. {@link DefaultResourceRepository} 만 주입받아 위임한다.
 * <p>
 * 컬럼이 고정된 UPDATE·DELETE 만 JPQL {@code @Modifying}(리스너를 타지 않음 — 수정 감사 값은 호출부가 {@link UpdateStamp} 로 정해 넘기고 SpEL 로 꺼낸다. 잠금·만료 잠금 해제·되돌리기는 호출부 값 그대로).
 * Y/N 컬럼 비교·대입은 리터럴 대신 Boolean 파라미터(엔티티의 {@code YesNoConverter} 가 적용됨). 조건은 전부 {@code =} 비교라 null 인자는 0건(구 SQL 동일).
 */
interface ResourceJpaRepository extends JpaRepository<Resource, Long> {

    /** 고칠 행 로드(관리 엔티티, 잠금 없음 — 엔티티 @DynamicUpdate 로 바뀐 컬럼만 씀) — 파생 쿼리 — 파티션 키 user_seq 를 조건에 넣어 한 파티션만(em.find 는 user_seq 를 몰라 전 파티션을 본다) */
    Optional<Resource> findByUserSeqAndResourceSeq(Integer userSeq, Long resourceSeq);

    /** 소유 계정 노출 가능 상태(구 resource-visible-by-active-user EXISTS) — 사용자 상태를 따로 조회하면 왕복이 하나 더 들어 한 문장으로 묶었다 */
    String VISIBLE_OWNER = "and exists (select 1 from User u where u.userSeq = r.userSeq and u.status in :visibleStatuses)";

    /** 고칠 행 로드(관리 엔티티) — {@link #findByUserSeqAndResourceSeq} + 소유 계정 노출 가능 상태(W10 — 구 Query 리포 findForMutation 과 같은 조건, 이동·이름 변경·버전 작업) */
    @Query("select r from Resource r where r.userSeq = :userSeq and r.resourceSeq = :resourceSeq " + VISIBLE_OWNER)
    Optional<Resource> findVisibleByUserSeqAndResourceSeq(@Param("userSeq") Integer userSeq, @Param("resourceSeq") Long resourceSeq,
                                                          @Param("visibleStatuses") Collection<UserStatus> visibleStatuses);

    /**
     * {@link #findVisibleByUserSeqAndResourceSeq} 의 잠금판(FOR UPDATE, 관리 엔티티) — 휴지통 복원(W10 R4): 구 조건 JPQL({@code is_delete = Y} 일 때만 UPDATE)의 동시 복원 보호를
     * 첫 로드 잠금으로 대신한다(늦은 쪽은 대기 뒤 삭제 상태가 아니라 NOT_FOUND). 이동(fileMove·folderMove)도 같은 첫 로드 잠금(10-14 점검 6-c). 호출부 트랜잭션 안에서만.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Resource r where r.userSeq = :userSeq and r.resourceSeq = :resourceSeq " + VISIBLE_OWNER)
    Optional<Resource> lockVisibleByUserSeqAndResourceSeq(@Param("userSeq") Integer userSeq, @Param("resourceSeq") Long resourceSeq,
                                                          @Param("visibleStatuses") Collection<UserStatus> visibleStatuses);

    /** 리소스 한 행 DTO 선택 머리(공유 토큰 제외) — 영속 컨텍스트에 안 올림(10-12 단계 4) */
    String SELECT_DETAIL = "select new com.thinkfree.drive.resource.data.ResourceDetailData(r.resourceSeq, r.userSeq, r.ownerSeq, r.latestOwnerSeq, r.resourceType, "
            + "r.parentFolderSeq, r.deleteFolderSeq, r.resourceName, r.originalFileName, r.resourceSaveName, r.fullPathSeq, r.fullPath, r.currentVersionNumber, "
            + "r.versionRegisterDate, r.copyNumber, r.isDelete, r.isLocked, r.lockedDate, r.ext, r.contentType, r.sizeByte, "
            + "r.registerUserSeq, r.registerDate, r.updateUserSeq, r.updateDate) from Resource r ";

    /** {@link #findVisibleByUserSeqAndResourceSeq} 의 DTO 판 — 같은 조건(파티션 단건 + 소유 계정 노출 가능 상태) */
    @Transactional(readOnly = true)
    @Query(SELECT_DETAIL + "where r.userSeq = :userSeq and r.resourceSeq = :resourceSeq " + VISIBLE_OWNER)
    Optional<ResourceDetailData> findVisibleDetailByPartition(@Param("userSeq") Integer userSeq, @Param("resourceSeq") Long resourceSeq,
                                                              @Param("visibleStatuses") Collection<UserStatus> visibleStatuses);

    /** 파티션 단건 DTO(노출 조건 없음) — 잠금 상태 판정(W10: 구 엔티티 로드 후 detach 는 같은 tx 의 관리 엔티티를 떼어낼 수 있어 DTO 로) */
    @Transactional(readOnly = true)
    @Query(SELECT_DETAIL + "where r.userSeq = :userSeq and r.resourceSeq = :resourceSeq")
    Optional<ResourceDetailData> findDetailByUserSeqAndResourceSeq(@Param("userSeq") Integer userSeq, @Param("resourceSeq") Long resourceSeq);

    /**
     * 버전 작업(버전 저장·덮어쓰기·버전 삭제) 전 리소스 행 잠금 — FOR UPDATE(생성자 식에도 붙는 것을 ResourceRowLockTest 로 확인). 호출부 트랜잭션 안에서만.
     * 값(DTO)으로 받는 이유(W10): 잠금 직후 같은 행을 JdbcClient 로 되돌리는 경로(미완료 버전 정리)가 있어, 관리 엔티티로 쥐면 캐시가 옛 값이 된다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(SELECT_DETAIL + "where r.userSeq = :userSeq and r.resourceSeq = :resourceSeq and r.isDelete = :isDelete")
    Optional<ResourceDetailData> lockDetailByUserSeqAndResourceSeq(@Param("userSeq") Integer userSeq, @Param("resourceSeq") Long resourceSeq,
                                                                   @Param("isDelete") Boolean isDelete);

    /**
     * 휴지통 보내기·영구 삭제 전 대상 행 잠금 + 상세(값) 한 번에 — FOR UPDATE, 미삭제 + 소유 계정 노출 가능 상태({@link #findVisibleDetailByPartition} 과 같은 조건).
     * 소유 계정 조건은 EXISTS 서브쿼리라 tb_user 행은 잠기지 않는다. 값(DTO)으로 받는 이유: 뒤이은 휴지통 이동·삭제가 벌크 JPQL 이라 관리 엔티티로 쥐면 캐시가 옛 값이 된다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(SELECT_DETAIL + "where r.userSeq = :userSeq and r.resourceSeq = :resourceSeq and r.isDelete = false " + VISIBLE_OWNER)
    Optional<ResourceDetailData> lockVisibleActiveDetail(@Param("userSeq") Integer userSeq, @Param("resourceSeq") Long resourceSeq,
                                                         @Param("visibleStatuses") Collection<UserStatus> visibleStatuses);

    /**
     * 폴더 하위 트리(자기 자신 포함, 미삭제) 행 잠금 — FOR UPDATE, resource_seq 순(동시 요청끼리 같은 순서로 잡아 교착 방지). 호출부 트랜잭션 안에서만.
     * 영구 삭제 API: 하위 잠금 여부(is_locked) 확인과 삭제 사이에 다른 요청이 하위를 잠그거나 옮기지 못하게 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r.resourceSeq from Resource r where r.userSeq = :userSeq and r.fullPathSeq like :pathPrefix and r.isDelete = false order by r.resourceSeq")
    List<Long> lockSubtreeResourceSeqs(@Param("userSeq") Integer userSeq, @Param("pathPrefix") String pathPrefix);

    /** 단건 행 잠금만(FOR UPDATE) — 폴더 이동 목적지. 조건 검사는 호출부가 잠근 뒤 따로 한다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r.resourceSeq from Resource r where r.userSeq = :userSeq and r.resourceSeq = :resourceSeq")
    Optional<Long> lockResourceSeq(@Param("userSeq") Integer userSeq, @Param("resourceSeq") Long resourceSeq);

    /** 공유 링크 get-or-create — 리소스 토큰 + 소유자 테넌트(구 JdbcClient tb_user INNER JOIN, 5차 패스) */
    @Transactional(readOnly = true)
    @Query("select new com.thinkfree.drive.resource.data.ResourceLinkTokenData(r.shareToken, u.tenantSeq) from Resource r "
            + "join User u on u.userSeq = r.userSeq where r.userSeq = :userSeq and r.resourceSeq = :resourceSeq and r.isDelete = :isDelete")
    Optional<ResourceLinkTokenData> findShareTokenForLink(@Param("userSeq") Integer userSeq, @Param("resourceSeq") Long resourceSeq,
                                                          @Param("isDelete") Boolean isDelete);

    // ------------------------------------------------------------------ 조회(생성자 식 — 영속 컨텍스트에 엔티티를 올리지 않음)

    /** 폴더와 그 하위 전부(full_path_seq 접두 일치) — 파티션 키 user_seq 로 한 파티션만 */
    @Transactional(readOnly = true)
    @Query("select new com.thinkfree.drive.resource.data.ResourcePathData(r.resourceSeq, r.userSeq) from Resource r "
            + "where r.userSeq = :userSeq and r.fullPathSeq like concat(:fullPathSeq, '%') and r.isDelete = :isDelete")
    List<ResourcePathData> findFolderAndDescendants(@Param("userSeq") Integer userSeq, @Param("fullPathSeq") String fullPathSeq, @Param("isDelete") Boolean isDelete);

    /** 계정 완전 삭제 — 스토리지에서 지울 키(저장 이름이 아직 없는 행 제외) */
    @Transactional(readOnly = true)
    @Query("select new com.thinkfree.drive.resource.data.ResourceStorageKeyData(r.resourceSaveName) from Resource r "
            + "where r.userSeq in :userSeqList and r.resourceSaveName is not null")
    List<ResourceStorageKeyData> findStorageKeysByUserSeqIn(@Param("userSeqList") Collection<Integer> userSeqList);

    /** 같은 위치(상위 폴더)·종류·삭제 여부에 같은 이름이 있는지 — 이름 중복 검사·덮어쓰기 대상 찾기(구 where-resource-exists) */
    @Transactional(readOnly = true)
    @Query("select r.resourceSeq from Resource r where r.userSeq = :userSeq and r.resourceType = :resourceType and r.isDelete = :isDelete "
            + "and r.parentFolderSeq = :parentFolderSeq and r.resourceName = :resourceName")
    List<Long> findSeqByName(@Param("userSeq") Integer userSeq, @Param("resourceType") ResourceType resourceType, @Param("isDelete") Boolean isDelete,
                             @Param("parentFolderSeq") Long parentFolderSeq, @Param("resourceName") String resourceName);

    /** {@link #findSeqByName} 의 루트(상위 폴더 없음) 판 */
    @Transactional(readOnly = true)
    @Query("select r.resourceSeq from Resource r where r.userSeq = :userSeq and r.resourceType = :resourceType and r.isDelete = :isDelete "
            + "and r.parentFolderSeq is null and r.resourceName = :resourceName")
    List<Long> findRootSeqByName(@Param("userSeq") Integer userSeq, @Param("resourceType") ResourceType resourceType, @Param("isDelete") Boolean isDelete,
                                 @Param("resourceName") String resourceName);

    @Transactional(readOnly = true)
    @Query("select count(r) > 0 from Resource r where r.shareToken = :shareToken")
    boolean existsByShareToken(@Param("shareToken") String shareToken);

    /** 폴더 이동의 하위 파일 소유자 일괄 이전 — 조건부 다건(행마다 로드할 이유 없음) */
    @Transactional
    @Modifying
    @Query("update Resource r set r.userSeq = :changeUserSeq, r.updateUserSeq = :#{#stamp.userSeq}, r.updateDate = :#{#stamp.date} where r.userSeq = :userSeq and r.resourceSeq in :resourceSeqList")
    int updateUserSeqForResources(@Param("userSeq") Integer userSeq, @Param("resourceSeqList") Collection<Long> resourceSeqList, @Param("changeUserSeq") Integer changeUserSeq,
                                  @Param("stamp") UpdateStamp stamp);

    @Transactional
    @Modifying
    @Query("update Resource r set r.sizeByte = :sizeByte, r.resourceSaveName = :newKey, r.updateUserSeq = :#{#stamp.userSeq}, r.updateDate = :#{#stamp.date} where r.userSeq = :userSeq and r.resourceSeq = :resourceSeq and r.resourceSaveName = :baseKey")
    int overwrite(@Param("userSeq") Integer userSeq, @Param("resourceSeq") Long resourceSeq, @Param("sizeByte") Long sizeByte, @Param("newKey") String newKey, @Param("baseKey") String baseKey,
                  @Param("stamp") UpdateStamp stamp);

    /** 덮어쓰기 되돌리기 — 감사 보정 없이 이전 값 그대로, 현재 값(size·수정자·수정일)이 그대로일 때만(낙관적 조건) */
    @Transactional
    @Modifying
    @Query("update Resource r set r.sizeByte = :restoreSizeByte, r.updateUserSeq = :restoreUpdateUserSeq, r.updateDate = :restoreUpdateDate "
            + "where r.userSeq = :userSeq and r.resourceSeq = :resourceSeq and r.sizeByte = :currentSizeByte "
            + "and r.updateUserSeq = :currentUpdateUserSeq and r.updateDate = :currentUpdateDate")
    int revertOverwrite(@Param("userSeq") Integer userSeq, @Param("resourceSeq") Long resourceSeq,
                        @Param("restoreSizeByte") Long restoreSizeByte, @Param("restoreUpdateUserSeq") Integer restoreUpdateUserSeq, @Param("restoreUpdateDate") Instant restoreUpdateDate,
                        @Param("currentSizeByte") Long currentSizeByte, @Param("currentUpdateUserSeq") Integer currentUpdateUserSeq, @Param("currentUpdateDate") Instant currentUpdateDate);

    /** 만료 잠금 일괄 해제(스케줄러) — 수정자·수정일은 호출부 값 그대로 */
    @Transactional
    @Modifying
    @Query("update Resource r set r.isLocked = :no, r.lockedDate = null, r.updateUserSeq = :updateUserSeq, r.updateDate = :updateDate "
            + "where r.isLocked = :yes and r.lockedDate <= :threshold")
    int updateExpiredLocks(@Param("threshold") Instant threshold, @Param("yes") Boolean yes, @Param("no") Boolean no,
                           @Param("updateUserSeq") Integer updateUserSeq, @Param("updateDate") Instant updateDate);

    /** 공유 링크 토큰 — 토큰이 없을(NULL·빈 문자열) 때만, 수정 감사는 건드리지 않는다(구 updateShareToken = SHARE_TOKEN 만 UPDATE) */
    @Transactional
    @Modifying
    @Query("update Resource r set r.shareToken = :shareToken "
            + "where r.userSeq = :userSeq and r.resourceSeq = :resourceSeq and (r.shareToken is null or r.shareToken = '')")
    int applyShareTokenIfAbsent(@Param("userSeq") Integer userSeq, @Param("resourceSeq") Long resourceSeq, @Param("shareToken") String shareToken);

    /** 폴더 휴지통 — 자기 자신 + 경로(full_path_seq) 접두 폴더들의 자식(손자 이하 포함), 미삭제만. 자기 행의 삭제 부모는 NULL(W10d — 구 UPDATE … FROM(UNION ALL)) */
    @Transactional
    @Modifying
    @Query("update Resource r set r.deleteFolderSeq = case when r.resourceSeq = :resourceSeq then null else :resourceSeq end, r.isDelete = :isDelete, "
            + "r.updateUserSeq = :#{#stamp.userSeq}, r.updateDate = :#{#stamp.date} "
            + "where r.userSeq = :userSeq and r.isDelete = :no and (r.resourceSeq = :resourceSeq or r.parentFolderSeq in "
            + "(select p.resourceSeq from Resource p where p.userSeq = :userSeq and p.fullPathSeq like concat(:fullPathSeq, '%')))")
    int updateIsDeleteByFullPathSeq(@Param("userSeq") Integer userSeq, @Param("resourceSeq") Long resourceSeq, @Param("fullPathSeq") String fullPathSeq,
                                    @Param("isDelete") Boolean isDelete, @Param("no") Boolean no, @Param("stamp") UpdateStamp stamp);

    /** 저장 실패 롤백 — 버전 데이터와 수정자·수정일을 NULL 포함 저장 전 값 그대로(감사 보정 없음) */
    @Transactional
    @Modifying
    @Query("update Resource r set r.latestOwnerSeq = :latestOwnerSeq, r.currentVersionNumber = :currentVersionNumber, r.versionRegisterDate = :versionRegisterDate, "
            + "r.sizeByte = :sizeByte, r.updateUserSeq = :updateUserSeq, r.updateDate = :updateDate where r.userSeq = :userSeq and r.resourceSeq = :resourceSeq")
    int rollbackVersionDataRestoringUpdate(@Param("userSeq") Integer userSeq, @Param("resourceSeq") Long resourceSeq, @Param("latestOwnerSeq") Integer latestOwnerSeq,
                                           @Param("currentVersionNumber") Integer currentVersionNumber, @Param("versionRegisterDate") Instant versionRegisterDate,
                                           @Param("sizeByte") Long sizeByte, @Param("updateUserSeq") Integer updateUserSeq, @Param("updateDate") Instant updateDate);

    /** 미완료 버전 정리 — 버전 데이터, 수정자·수정일은 값이 있을 때만(null 이면 그대로 — 구 동적 SET) */
    @Transactional
    @Modifying
    @Query("update Resource r set r.latestOwnerSeq = :latestOwnerSeq, r.currentVersionNumber = :currentVersionNumber, r.versionRegisterDate = :versionRegisterDate, "
            + "r.sizeByte = :sizeByte, r.updateUserSeq = coalesce(:updateUserSeq, r.updateUserSeq), r.updateDate = coalesce(:updateDate, r.updateDate) "
            + "where r.userSeq = :userSeq and r.resourceSeq = :resourceSeq")
    int rollbackVersionData(@Param("userSeq") Integer userSeq, @Param("resourceSeq") Long resourceSeq, @Param("latestOwnerSeq") Integer latestOwnerSeq,
                            @Param("currentVersionNumber") Integer currentVersionNumber, @Param("versionRegisterDate") Instant versionRegisterDate,
                            @Param("sizeByte") Long sizeByte, @Param("updateUserSeq") Integer updateUserSeq, @Param("updateDate") Instant updateDate);

    /**
     * 폴더 이동·이름 변경 뒤 하위 폴더 경로 치환 — 자기 행 제외(자기는 변경 감지), 같은 삭제 상태만. full_path_seq 는 새 seq 경로가 있을 때만(null 이면 그대로),
     * 소유자는 changeUserSeq 가 있을 때만(파티션 키 — 행이 새 소유자 파티션으로). 구 동적 SET 을 case·coalesce 로(W10d).
     */
    @Transactional
    @Modifying
    @Query("update Resource r set r.fullPath = concat(:newFullPath, substring(r.fullPath, length(:oldFullPath) + 1)), "
            + "r.fullPathSeq = case when :newFullPathSeq is null then r.fullPathSeq else concat(:newFullPathSeq, substring(r.fullPathSeq, length(:oldFullPathSeq) + 1)) end, "
            + "r.userSeq = coalesce(:changeUserSeq, r.userSeq), r.updateUserSeq = :#{#stamp.userSeq}, r.updateDate = :#{#stamp.date} "
            + "where r.fullPathSeq like concat(:oldFullPathSeq, '%') and r.userSeq = :userSeq and r.resourceSeq <> :resourceSeq and r.isDelete = :isDelete")
    int updateFullPathForChildFolders(@Param("userSeq") Integer userSeq, @Param("resourceSeq") Long resourceSeq, @Param("oldFullPath") String oldFullPath,
                                      @Param("newFullPath") String newFullPath, @Param("oldFullPathSeq") String oldFullPathSeq, @Param("newFullPathSeq") String newFullPathSeq,
                                      @Param("changeUserSeq") Integer changeUserSeq, @Param("isDelete") Boolean isDelete, @Param("stamp") UpdateStamp stamp);

    /**
     * 휴지통 복원 하위 — 이 폴더가 삭제 부모인 삭제 행만(복원 폴더 자신은 삭제 부모가 NULL 이라 안 걸림 — 자신은 변경 감지). 파일은 경로 NULL·폴더는 치환,
     * full_path_seq 는 새 seq 경로가 있을 때만(null 이면 파일도 그대로). 구 동적 SET 을 case 로(W10d).
     */
    @Transactional
    @Modifying
    @Query("update Resource r set r.deleteFolderSeq = null, "
            + "r.fullPath = case when r.resourceType = :file then null else concat(:newFullPath, substring(r.fullPath, length(:oldFullPath) + 1)) end, "
            + "r.fullPathSeq = case when :newFullPathSeq is null then r.fullPathSeq when r.resourceType = :file then null "
            + "else concat(:newFullPathSeq, substring(r.fullPathSeq, length(:oldFullPathSeq) + 1)) end, "
            + "r.isDelete = :no, r.updateUserSeq = :#{#stamp.userSeq}, r.updateDate = :#{#stamp.date} "
            + "where r.userSeq = :userSeq and r.deleteFolderSeq = :resourceSeq and r.isDelete = :yes")
    int restoreChildResources(@Param("userSeq") Integer userSeq, @Param("resourceSeq") Long resourceSeq, @Param("oldFullPath") String oldFullPath,
                              @Param("newFullPath") String newFullPath, @Param("oldFullPathSeq") String oldFullPathSeq, @Param("newFullPathSeq") String newFullPathSeq,
                              @Param("file") ResourceType file, @Param("yes") Boolean yes, @Param("no") Boolean no, @Param("stamp") UpdateStamp stamp);

    @Transactional
    @Modifying
    @Query("delete from Resource r where r.userSeq = :userSeq and r.resourceSeq in :resourceSeqList")
    int deleteByUserSeqAndResourceSeqIn(@Param("userSeq") Integer userSeq, @Param("resourceSeqList") Collection<Long> resourceSeqList);

    @Transactional
    @Modifying
    @Query("delete from Resource r where r.userSeq in :userSeqList")
    int deleteByUserSeqIn(@Param("userSeqList") Collection<Integer> userSeqList);
}
