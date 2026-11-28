package com.thinkfree.drive.user.repository;

import com.thinkfree.drive.global.jpa.audit.UpdateStamp;
import com.thinkfree.drive.user.data.UserAuthData;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.data.UserQuotaData;
import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.user.enums.MfaMethod;
import com.thinkfree.drive.user.enums.MfaStatus;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;
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
 * tb_user Spring Data JPA 선언 인터페이스 — **패키지 전용**. {@link DefaultUserQueryRepository}(DTO 단건·스칼라 조회 — 엔티티 반환 없음)·{@link DefaultUserRepository}(고칠 행 로드·잠금·유지 예외 UPDATE) 만 주입받아 위임한다.
 * 서비스에 노출하지 않는다. 시트 검사 INSERT…SELECT 는 jOOQ({@link DefaultUserRepository}).
 * 조회 결과가 2건 이상이면 Spring Data 가 {@code IncorrectResultSizeDataAccessException} 을 던진다(MyBatis TooManyResultsException 과 동일 의미).
 * <p>
 * 단건 변경은 변경 감지(Service 가 로드한 관리 엔티티 + {@link User} 변경 메서드, 감사는 Auditing — W7). 남은 JPQL {@code @Modifying} 은 유지 예외(원자적 증감·빈번한 단일 컬럼·조건부 다건·MFA 조건부 원자 판정 — 메서드마다 이유)이고
 * 리스너를 타지 않아 수정 감사 값은 호출부가 {@link UpdateStamp} 로 넘기고 SpEL 로 꺼낸다. 상태·MFA 상수는 enum/Boolean 파라미터로 받아 엔티티 매핑(문자열 enum·Y/N 컨버터)을 그대로 탄다.
 * 조건은 전부 {@code =}/{@code <>}/{@code in} 비교라 null 인자는 0건(구 SQL 동일).
 */
interface UserJpaRepository extends JpaRepository<User, Integer> {

    // ------------------------------------------------------------------ DTO 단건(JPQL 생성자 식 — 영속 컨텍스트에 안 올림, 10-12 단계 3)
    // PK(userSeq) 조회는 상태 조건 없이 한 메서드 — ACTIVE·테넌트 판정은 FindService(Java). userId 는 상태·role 이 달라 여러 행일 수 있어 WHERE 로 고른다.

    /** 역할 조건 — tb_user_role 보유(다중 role, 10-23). 파라미터 :roleType. */
    String HOLDS_ROLE = "exists (select r from UserRole r where r.userSeq = u.userSeq and r.roleType = :roleType)";

    /** 계정 값 DTO 선택 머리(비밀값 제외) */
    String SELECT_DETAIL = "select new com.thinkfree.drive.user.data.UserDetailData(u.userSeq, u.userId, u.userName, u.status, u.totalQuotaByte, u.useQuotaByte, "
            + "u.registerUserSeq, u.registerDate, u.updateUserSeq, u.updateDate, u.userLanguage, u.countryCode, u.phoneNumber, u.defaultLandingPage, u.profileImageKey, u.tenantSeq, u.mfaStatus) from User u ";
    /** 인증 DTO 선택 머리 = 계정 값 + encPwd(Password 컨버터)·loginFailureCount·loginFailedAt·mfaMethod·mfaSecretKey */
    String SELECT_AUTH = "select new com.thinkfree.drive.user.data.UserAuthData(u.userSeq, u.userId, u.userName, u.status, u.totalQuotaByte, u.useQuotaByte, "
            + "u.registerUserSeq, u.registerDate, u.updateUserSeq, u.updateDate, u.userLanguage, u.countryCode, u.phoneNumber, u.defaultLandingPage, u.profileImageKey, u.tenantSeq, u.mfaStatus, "
            + "u.encPwd, u.loginFailureCount, u.loginFailedAt, u.mfaMethod, u.mfaSecretKey) from User u ";

    @Transactional(readOnly = true)
    @Query(SELECT_DETAIL + "where u.userSeq = :userSeq")
    Optional<UserDetailData> findDetailByUserSeq(@Param("userSeq") Integer userSeq);

    @Transactional(readOnly = true)
    @Query(SELECT_DETAIL + "where u.userSeq in :userSeqs")
    List<UserDetailData> findDetailsByUserSeqs(@Param("userSeqs") List<Integer> userSeqs);

    @Transactional(readOnly = true)
    @Query(SELECT_AUTH + "where u.userSeq = :userSeq")
    Optional<UserAuthData> findAuthByUserSeq(@Param("userSeq") Integer userSeq);

    /** 웹 JWT 필터 — 상태·역할 보유·계정 값을 한 문장으로(구: 활성 count → 보유 count → PK 조회 3문장) */
    @Transactional(readOnly = true)
    @Query(SELECT_DETAIL + "where u.userSeq = :userSeq and u.status = :status and " + HOLDS_ROLE)
    Optional<UserDetailData> findDetailHoldingRole(@Param("userSeq") Integer userSeq, @Param("status") UserStatus status, @Param("roleType") RoleType roleType);

    // userId 는 테넌트 안에서만 유일(테넌트별 user_id 유니크) — 테넌트 계정은 tenantSeq, 슈퍼관리자는 tenantSeq is null 로 함께 고른다.

    /** 테넌트 계정 userId 조건 — 비밀번호 재설정(Detail)과 로그인(Auth)이 같은 계정을 고르도록 공유. 파라미터 :tenantSeq·:userId·:status·:roleType */
    String IN_TENANT = "where u.tenantSeq = :tenantSeq and u.userId = :userId and u.status = :status and " + HOLDS_ROLE;

    /** 구 findByUserIdAndIsActive — TENANT_SEQ + USER_ID + STATUS + ROLE_TYPE */
    @Transactional(readOnly = true)
    @Query(SELECT_DETAIL + IN_TENANT)
    Optional<UserDetailData> findDetailInTenant(@Param("tenantSeq") Integer tenantSeq, @Param("userId") String userId, @Param("status") UserStatus status, @Param("roleType") RoleType roleType);

    /** 구 findByUserIdAndIsActive / findByAdminIdAndIsActive — TENANT_SEQ + USER_ID + STATUS + ROLE_TYPE(로그인 계열) */
    @Transactional(readOnly = true)
    @Query(SELECT_AUTH + IN_TENANT)
    Optional<UserAuthData> findAuthInTenant(@Param("tenantSeq") Integer tenantSeq, @Param("userId") String userId, @Param("status") UserStatus status, @Param("roleType") RoleType roleType);

    /** 구 findBySuperAdminIdAndIsActive — 테넌트 없는(TENANT_SEQ IS NULL) USER_ID + STATUS + ROLE_TYPE(슈퍼관리자 로그인) */
    @Transactional(readOnly = true)
    @Query(SELECT_AUTH + "where u.tenantSeq is null and u.userId = :userId and u.status = :status and " + HOLDS_ROLE)
    Optional<UserAuthData> findTenantlessAuth(@Param("userId") String userId, @Param("status") UserStatus status, @Param("roleType") RoleType roleType);

    /** 구 findAnonymousUser — ROLE_TYPE='ANONYMOUS' + ACTIVE, LIMIT 1(순서 미지정은 XML 과 동일). */
    @Transactional(readOnly = true)
    @Query(SELECT_DETAIL + "where u.status = :status and " + HOLDS_ROLE)
    List<UserDetailData> findDetailsByRoleTypeAndStatus(@Param("roleType") RoleType roleType, @Param("status") UserStatus status, Limit limit);

    /** 구 findEarliestActiveSuperAdmin — ACTIVE SUPER_ADMIN 중 등록일·user_seq 가장 빠른 1명. */
    @Transactional(readOnly = true)
    @Query(SELECT_DETAIL + "where u.status = :status and " + HOLDS_ROLE + " order by u.registerDate asc, u.userSeq asc")
    List<UserDetailData> findOldestDetailsByRoleAndStatus(@Param("roleType") RoleType roleType, @Param("status") UserStatus status, Limit limit);

    /** 테넌트의 삭제되지 않은(status ≠ excluded) 계정 전부 — 테넌트 삭제(영구 삭제) 대상 */
    @Transactional(readOnly = true)
    @Query(SELECT_DETAIL + "where u.tenantSeq = :tenantSeq and u.status <> :excluded order by u.userSeq asc")
    List<UserDetailData> findDetailsByTenantSeqAndStatusNot(@Param("tenantSeq") Integer tenantSeq, @Param("excluded") UserStatus excluded);

    // ------------------------------------------------------------------ 스칼라·부분 컬럼

    @Transactional(readOnly = true)
    @Query("select u.tenantSeq from User u where u.userSeq = :userSeq")
    Optional<Integer> findTenantSeqByUserSeq(@Param("userSeq") Integer userSeq);

    /** 보호 계정이면 그 테넌트(테넌트 없는 계정은 빈 값과 같음), 아니면 빈 값 */
    @Transactional(readOnly = true)
    @Query("select u.tenantSeq from User u where u.userSeq = :userSeq and u.isProtected = :isProtected")
    Optional<Integer> findTenantSeqByUserSeqAndIsProtected(@Param("userSeq") Integer userSeq, @Param("isProtected") Boolean isProtected);

    @Transactional(readOnly = true)
    @Query("select count(u) from User u where u.tenantSeq = :tenantSeq and u.userSeq in :userSeqList")
    long countByTenantSeqAndUserSeqIn(@Param("tenantSeq") Integer tenantSeq, @Param("userSeqList") Collection<Integer> userSeqList);

    /** 테넌트 사용자 할당량(total_quota_byte) 합. 삭제 사용자는 total_quota_byte 가 0 으로 정리돼 상태 조건 없음 */
    @Transactional(readOnly = true)
    @Query("select coalesce(sum(u.totalQuotaByte), 0) from User u where u.tenantSeq = :tenantSeq")
    long sumTotalQuotaByteByTenantSeq(@Param("tenantSeq") Integer tenantSeq);

    @Transactional(readOnly = true)
    @Query("select new com.thinkfree.drive.user.data.UserQuotaData(u.totalQuotaByte, u.useQuotaByte) from User u where u.userSeq = :userSeq and u.status = :status")
    Optional<UserQuotaData> findQuotaByUserSeqAndStatus(@Param("userSeq") Integer userSeq, @Param("status") UserStatus status);

    // ------------------------------------------------------------------ 행 잠금(쓰기 — 관리 엔티티, 그 행의 첫 로드에서, W7)

    /** SELECT … FOR UPDATE 계열 — 호출부 트랜잭션 안에서만. 이미 관리 중인 행이면 캐시의 값을 돌려주므로 그 행의 첫 로드에서 부른다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.userSeq = :userSeq")
    Optional<User> lockByUserSeq(@Param("userSeq") Integer userSeq);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.userSeq in :userSeqList")
    List<User> lockByUserSeqIn(@Param("userSeqList") Collection<Integer> userSeqList);

    // ------------------------------------------------------------------ 용량·로그인 실패(변경 감지가 아닌 이유는 메서드마다)

    /** 원자적 증감 — 변경 감지(읽고-더하고-쓰기)는 동시 요청에서 갱신이 사라진다. 잠근 관리 엔티티를 고치지 않으므로 같은 tx 의 잠금 엔티티와 섞여도 덮어쓰지 않음(@DynamicUpdate) */
    @Transactional
    @Modifying
    @Query("update User u set u.useQuotaByte = u.useQuotaByte + :delta where u.userSeq = :userSeq")
    int addUseQuotaByte(@Param("userSeq") Integer userSeq, @Param("delta") Long delta);

    /** 로드 없이 끝내는 빈번한 단일 컬럼(로그인 성공) — PK 라 1행(10-23 role ④: 로그인 실패 횟수는 user_seq 기준). 비밀번호 변경 경로는 {@link User#changePassword} */
    @Transactional
    @Modifying
    @Query("update User u set u.loginFailureCount = 0 where u.userSeq = :userSeq and u.status = :active")
    int resetLoginFailureCount(@Param("userSeq") Integer userSeq, @Param("active") UserStatus active);

    /**
     * SEAT 좌석 소비(등록·활성화) 직렬화용 테넌트 행 잠금(SELECT … FOR UPDATE 계열) — 스칼라 투영이라 엔티티를 컨텍스트에 올리지 않는다.
     * 좌석 COUNT 는 잠금 뒤 별도 문장(한 문장이면 문장 시작 스냅샷이라 앞 요청의 커밋을 못 센다). 잠금은 호출부 트랜잭션 끝까지.
     */
    @Transactional
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t.tenantSeq from Tenant t where t.tenantSeq = :tenantSeq")
    Optional<Integer> lockTenant(@Param("tenantSeq") Integer tenantSeq);

    // ------------------------------------------------------------------ 활성화(시트 검사 — 테넌트 행 잠금 뒤 조건부 UPDATE)

    /** 활성화 공통 — ACTIVE 가 아닌 행만 ACTIVE 로. SEAT 버전은 뒤에 좌석 조건만 덧붙인다. 파라미터 :userSeq·:active·:stamp */
    String ACTIVATE = "update User u set u.status = :active, u.updateUserSeq = :#{#stamp.userSeq}, u.updateDate = :#{#stamp.date} "
            + "where u.userSeq = :userSeq and u.status <> :active";

    /** 활성화 — ACTIVE 가 아닌 행만 */
    @Transactional
    @Modifying
    @Query(ACTIVATE)
    int activate(@Param("userSeq") Integer userSeq, @Param("active") UserStatus active, @Param("stamp") UpdateStamp stamp);

    /** 활성화(SEAT) — 테넌트 ACTIVE 수 < constrainValue 일 때만 */
    @Transactional
    @Modifying
    @Query(ACTIVATE + " and (select count(c) from User c where c.status = :active and c.tenantSeq = :tenantSeq) < :constrainValue")
    int activateWithSeatCheck(@Param("userSeq") Integer userSeq, @Param("active") UserStatus active,
                              @Param("tenantSeq") Integer tenantSeq, @Param("constrainValue") long constrainValue, @Param("stamp") UpdateStamp stamp);

    // ------------------------------------------------------------------ 상태 전이(조건부 다건 — 삭제 스케줄러·영구 삭제)

    /** 완전 삭제 상태로 바꾸는 SET 공통 — 두 경로(삭제 대기 → 삭제 스케줄러·즉시 완전 삭제)의 삭제 후 상태가 같아야 한다. 파라미터 :deleted·:deleteDate·:stamp·:userSeqList */
    String MARK_DELETED = "update User u set u.status = :deleted, u.totalQuotaByte = 0, u.useQuotaByte = 0, u.deleteScheduleTime = null, u.deleteDate = :deleteDate, "
            + "u.profileImageKey = null, u.updateUserSeq = :#{#stamp.userSeq}, u.updateDate = :#{#stamp.date} where u.userSeq in :userSeqList ";

    /** 삭제 처리 — 삭제 대기만 */
    @Transactional
    @Modifying
    @Query(MARK_DELETED + "and u.status = :pendingDelete")
    int markDeletedFromPendingDelete(@Param("userSeqList") Collection<Integer> userSeqList, @Param("deleted") UserStatus deleted, @Param("pendingDelete") UserStatus pendingDelete,
                                     @Param("deleteDate") Instant deleteDate, @Param("stamp") UpdateStamp stamp);

    /** 삭제 처리 — 이미 삭제된 사용자 제외 전부 */
    @Transactional
    @Modifying
    @Query(MARK_DELETED + "and u.status <> :deleted")
    int markDeleted(@Param("userSeqList") Collection<Integer> userSeqList, @Param("deleted") UserStatus deleted,
                    @Param("deleteDate") Instant deleteDate, @Param("stamp") UpdateStamp stamp);

    // ------------------------------------------------------------------ MFA
    // 설정 시작·확인은 조건부 원자 판정 — 만료 대기 정리(clearPendingMfa 벌크 스케줄러)와 경쟁해서, 로드 → 판정 → 변경 감지 사이에 정리가 끼면
    // @DynamicUpdate 로 상태 컬럼이 빠진 채 비밀키만 저장되는 불일치가 생길 수 있다(W7 사용자 결정 — 변경 감지 아님)

    /** TOTP 설정 시작 — 미사용·대기 중만 대기로 */
    @Transactional
    @Modifying
    @Query("update User u set u.mfaStatus = :pending, u.mfaMethod = :method, u.mfaSecretKey = :mfaSecretKey, u.mfaSetupDate = :mfaSetupDate, u.mfaConfirmDate = null, "
            + "u.updateUserSeq = :#{#stamp.userSeq}, u.updateDate = :#{#stamp.date} where u.userSeq = :userSeq and u.mfaStatus in :from")
    int setupMfa(@Param("userSeq") Integer userSeq, @Param("pending") MfaStatus pending, @Param("method") MfaMethod method, @Param("from") Collection<MfaStatus> from,
                 @Param("mfaSecretKey") String mfaSecretKey, @Param("mfaSetupDate") Instant mfaSetupDate,
                 @Param("stamp") UpdateStamp stamp);

    /** TOTP 확인 — 대기 중이고 설정 시각이 cutoffAt 이후일 때만 사용 중으로 */
    @Transactional
    @Modifying
    @Query("update User u set u.mfaStatus = :enabled, u.mfaConfirmDate = :mfaConfirmDate, u.updateUserSeq = :#{#stamp.userSeq}, u.updateDate = :#{#stamp.date} "
            + "where u.userSeq = :userSeq and u.mfaStatus = :pending and u.mfaSetupDate >= :cutoffAt")
    int confirmMfa(@Param("userSeq") Integer userSeq, @Param("enabled") MfaStatus enabled, @Param("pending") MfaStatus pending, @Param("cutoffAt") Instant cutoffAt,
                   @Param("mfaConfirmDate") Instant mfaConfirmDate, @Param("stamp") UpdateStamp stamp);

    /** 만료된 대기 전부 미사용으로(스케줄러) — user_seq 조건 없음(조건부 다건) */
    @Transactional
    @Modifying
    @Query("update User u set u.mfaStatus = :disabled, u.mfaMethod = null, u.mfaSecretKey = null, u.mfaSetupDate = null, u.mfaConfirmDate = null, "
            + "u.updateUserSeq = :#{#stamp.userSeq}, u.updateDate = :#{#stamp.date} where u.mfaStatus = :pending and u.mfaSetupDate < :cutoffAt")
    int clearPendingMfa(@Param("disabled") MfaStatus disabled, @Param("pending") MfaStatus pending, @Param("cutoffAt") Instant cutoffAt,
                        @Param("stamp") UpdateStamp stamp);
}
