package com.thinkfree.drive.resource.version.repository;

import com.thinkfree.drive.global.jpa.audit.UpdateStamp;
import com.thinkfree.drive.resource.data.ResourceStorageKeyData;
import com.thinkfree.drive.resource.version.data.ResourceVersionKeyData;
import com.thinkfree.drive.resource.version.data.ResourceVersionRowData;
import com.thinkfree.drive.resource.version.data.ResourceVersionWithResourceData;
import com.thinkfree.drive.resource.version.entity.ResourceVersion;
import com.thinkfree.drive.resource.version.enums.ResourceVersionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
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
 * tb_resource_version Spring Data JPA 선언 인터페이스 — **패키지 전용**. {@link DefaultResourceVersionQueryRepository}(읽기)·{@link DefaultResourceVersionRepository}(쓰기) 만 주입받아 위임한다.
 * 조건은 JPQL 의 {@code =} 비교(파생 쿼리는 null 인자를 {@code IS NULL} 로 바꾸므로 — 구 SQL 과 같이 null 이면 0건). 단 단건 로드 {@link #findByUserSeqAndResourceVersionSeq} 는 파생 쿼리(조건 컬럼 user_seq·PK 가 NOT NULL 이라 null 인자도 0건 — 결과 동일, 10-13).
 * <p>
 * 조회는 DTO·스칼라(생성자 식)라 영속성 컨텍스트에 안 올린다 — 엔티티를 돌려주는 것은 고칠 행 로드 {@link #findByUserSeqAndResourceVersionSeq} 하나(관리 엔티티, W8).
 * 남은 UPDATE(번호 이동·소유자 이전 — 조건부 다건)는 리스너를 타지 않는 JPQL {@code @Modifying} 이라 감사 값은 호출부(Default 리포)가 {@link UpdateStamp}({@code JdbcAudit} 규칙)로 정해 넘기고 SpEL 로 꺼낸다.
 * 구 SQL 의 {@code NOW() ± make_interval(mins => …)} 는 애플리케이션 시각 기준 경계({@code threshold})로 바꿨다(register_date 도 애플리케이션 시각으로 들어간다).
 */
interface ResourceVersionJpaRepository extends JpaRepository<ResourceVersion, Long> {

    /** 계정 완전 삭제 — 스토리지에서 지울 버전 키(생성자 식, 영속 컨텍스트에 안 올림) */
    @Transactional(readOnly = true)
    @Query("select new com.thinkfree.drive.resource.data.ResourceStorageKeyData(v.resourceSaveName) from ResourceVersion v where v.userSeq in :userSeqList")
    List<ResourceStorageKeyData> findStorageKeysByUserSeqIn(@Param("userSeqList") Collection<Integer> userSeqList);

    // ------------------------------------------------------------------ 읽기

    /** 버전 키 DTO 선택 머리 — 영속 컨텍스트에 안 올림(Query 리포 읽기는 항상 DB 현재 값) */
    String SELECT_KEY_DATA = "select new com.thinkfree.drive.resource.version.data.ResourceVersionKeyData("
            + "v.resourceVersionSeq, v.resourceSeq, v.userSeq, v.versionNumber, v.sizeByte, v.resourceSaveName) from ResourceVersion v ";

    @Transactional(readOnly = true)
    @Query(SELECT_KEY_DATA + "where v.resourceSeq = :resourceSeq and v.userSeq = :userSeq")
    List<ResourceVersionKeyData> findKeysByResourceSeqAndUserSeq(@Param("resourceSeq") Long resourceSeq, @Param("userSeq") Integer userSeq);

    @Transactional(readOnly = true)
    @Query(SELECT_KEY_DATA + "where v.resourceSeq in :resourceSeqList and v.userSeq = :userSeq")
    List<ResourceVersionKeyData> findKeysByResourceSeqInAndUserSeq(@Param("resourceSeqList") Collection<Long> resourceSeqList, @Param("userSeq") Integer userSeq);

    /** 단건 — 파티션 키 user_seq 포함 4조건(구 findResourceVersion). 조건이 {@code =} 라 null 인자는 0건 */
    @Transactional(readOnly = true)
    @Query(SELECT_KEY_DATA + "where v.userSeq = :userSeq and v.resourceVersionSeq = :resourceVersionSeq and v.resourceSeq = :resourceSeq and v.status = :status")
    Optional<ResourceVersionKeyData> findKey(@Param("userSeq") Integer userSeq, @Param("resourceVersionSeq") Long resourceVersionSeq,
                                             @Param("resourceSeq") Long resourceSeq, @Param("status") ResourceVersionStatus status);

    /** 버전 + 리소스(이름·저장 이름·크기) — 생성자 식, 엔티티 조인(파티션 키 user_seq 포함). 구 JdbcClient INNER JOIN(5차 패스) */
    @Transactional(readOnly = true)
    @Query("select new com.thinkfree.drive.resource.version.data.ResourceVersionWithResourceData("
            + "v.resourceVersionSeq, v.userSeq, v.resourceSeq, r.resourceName, v.resourceSaveName, r.resourceSaveName, v.versionNumber, v.sizeByte, r.sizeByte, "
            + "v.status, v.versionRegisterDate, v.registerUserSeq, v.registerDate) "
            + "from ResourceVersion v join Resource r on r.resourceSeq = v.resourceSeq and r.userSeq = v.userSeq "
            + "where v.resourceVersionSeq = :resourceVersionSeq and v.resourceSeq = :resourceSeq and v.userSeq = :userSeq and v.status = :status")
    Optional<ResourceVersionWithResourceData> findWithResource(@Param("resourceVersionSeq") Long resourceVersionSeq, @Param("resourceSeq") Long resourceSeq,
                                                               @Param("userSeq") Integer userSeq, @Param("status") ResourceVersionStatus status);

    /** 버전 행 값 선택 머리 — 쓰기 흐름의 잠금·만료 판정 결과(엔티티를 영속성 컨텍스트에 올리지 않음 — detach 불필요, W8) */
    String SELECT_ROW_DATA = "select new com.thinkfree.drive.resource.version.data.ResourceVersionRowData(v.resourceVersionSeq, v.userSeq, v.ownerSeq, v.resourceSeq, "
            + "v.resourceSaveName, v.versionNumber, v.sizeByte, v.status, v.versionRegisterDate, v.updateUserSeq, v.updateDate) from ResourceVersion v ";

    /** 리소스의 모든 버전 행 잠금(FOR UPDATE — 생성자 식에도 붙는 것을 ResourceVersionRowLockTest 로 확인), version_number 순. 호출부 트랜잭션 안에서만 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(SELECT_ROW_DATA + "where v.userSeq = :userSeq and v.resourceSeq = :resourceSeq order by v.versionNumber")
    List<ResourceVersionRowData> lockAllByResource(@Param("userSeq") Integer userSeq, @Param("resourceSeq") Long resourceSeq);

    /** 지정 상태가 아닌 버전이 threshold 이후 등록됐는지(업로드 처리 중 판정) */
    @Transactional(readOnly = true)
    @Query("select case when count(v) > 0 then true else false end from ResourceVersion v where v.resourceSeq = :resourceSeq and v.userSeq = :userSeq and v.status <> :status and v.registerDate >= :threshold")
    boolean existsAlive(@Param("resourceSeq") Long resourceSeq, @Param("userSeq") Integer userSeq, @Param("status") ResourceVersionStatus status, @Param("threshold") Instant threshold);

    /** 지정 상태로 threshold 전에 등록된 버전 중 version_number 가 가장 큰 것 */
    @Transactional(readOnly = true)
    @Query(SELECT_ROW_DATA + "where v.resourceSeq = :resourceSeq and v.userSeq = :userSeq and v.status = :status and v.registerDate < :threshold order by v.versionNumber desc")
    List<ResourceVersionRowData> findExpired(@Param("resourceSeq") Long resourceSeq, @Param("userSeq") Integer userSeq, @Param("status") ResourceVersionStatus status, @Param("threshold") Instant threshold,
                                      Limit limit);

    // ------------------------------------------------------------------ UPDATE

    /** 고칠 행 로드(관리 엔티티 — 변경 감지, @DynamicUpdate 로 바뀐 컬럼만) — 파생 쿼리, 파티션 키 user_seq 를 조건에 넣어 한 파티션만(조건 컬럼 NOT NULL) */
    Optional<ResourceVersion> findByUserSeqAndResourceVersionSeq(Integer userSeq, Long resourceVersionSeq);

    /**
     * 버전 번호 일괄 이동 — version_number + delta. UNIQUE(user_seq, resource_seq, version_number, status) 를 피하는 2단계(임시 오프셋 → 제자리)는 호출부가 delta 로 조합한다.
     * versionNumber 가 있으면 그보다 큰 버전만. 조건부 다건이라 변경 감지가 아니다(행마다 로드할 이유 없음).
     */
    @Transactional
    @Modifying
    @Query("update ResourceVersion v set v.versionNumber = v.versionNumber + :delta, v.updateUserSeq = :#{#stamp.userSeq}, v.updateDate = :#{#stamp.date} "
            + "where v.userSeq = :userSeq and v.resourceSeq = :resourceSeq and v.status = :status "
            + "and (:versionNumber is null or v.versionNumber > :versionNumber)")
    int shiftVersionNumbers(@Param("userSeq") Integer userSeq, @Param("resourceSeq") Long resourceSeq, @Param("status") ResourceVersionStatus status,
                            @Param("versionNumber") Integer versionNumber, @Param("delta") int delta,
                            @Param("stamp") UpdateStamp stamp);

    /** 소유자 이전 — 연관 행 벌크(계획 §7-5, 본 행은 Resource 쪽). 조건부 다건이라 변경 감지가 아니다 */
    @Transactional
    @Modifying
    @Query("update ResourceVersion v set v.userSeq = :changeUserSeq, v.updateUserSeq = :#{#stamp.userSeq}, v.updateDate = :#{#stamp.date} "
            + "where v.userSeq = :userSeq and v.resourceSeq in :resourceSeqList")
    int updateUserSeqForResources(@Param("userSeq") Integer userSeq, @Param("resourceSeqList") Collection<Long> resourceSeqList, @Param("changeUserSeq") Integer changeUserSeq,
                                  @Param("stamp") UpdateStamp stamp);

    // ------------------------------------------------------------------ DELETE

    @Transactional
    @Modifying
    @Query("delete from ResourceVersion v where v.resourceVersionSeq = :resourceVersionSeq and v.userSeq = :userSeq")
    int deleteVersion(@Param("resourceVersionSeq") Long resourceVersionSeq, @Param("userSeq") Integer userSeq);

    @Transactional
    @Modifying
    @Query("delete from ResourceVersion v where v.userSeq = :userSeq and v.resourceSeq in :resourceSeqList")
    int deleteByUserSeqAndResourceSeqIn(@Param("userSeq") Integer userSeq, @Param("resourceSeqList") Collection<Long> resourceSeqList);

    @Transactional
    @Modifying
    @Query("delete from ResourceVersion v where v.userSeq in :userSeqList")
    int deleteByUserSeqIn(@Param("userSeqList") Collection<Integer> userSeqList);
}
