package com.thinkfree.drive.user.service;

import com.thinkfree.drive.user.data.UserAuthData;
import com.thinkfree.drive.user.data.UserBulkStatusData;
import com.thinkfree.drive.user.data.UserData;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.data.UserDetailWithRolesData;
import com.thinkfree.drive.user.data.UserListData;
import com.thinkfree.drive.user.data.UserQuotaData;
import com.thinkfree.drive.user.data.UserSearchData;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;
import com.thinkfree.drive.user.query.UserBulkStatusQuery;
import com.thinkfree.drive.user.query.UserListQuery;

import java.util.List;
import java.util.Set;

public interface UserFindService {

    Integer findTenantSeqByUserSeq(Integer userSeq);

    /** 보호 계정이면 그 테넌트, 아니면(없는 계정·테넌트 없는 보호 계정 포함) null — 예외 없음 */
    Integer findTenantSeqIfProtected(int userSeq);

    // --- 단건 DTO(JPQL 생성자 식 — 항상 DB 현재 값, 엔티티를 반환하지 않는다 10-12). 없으면 USER_NOT_FOUND.
    //     ACTIVE·테넌트 판정은 여기서(Java) 한다 — 리포의 PK 조회는 상태 조건이 없다. 비활성·다른 테넌트도 USER_NOT_FOUND(없는 계정과 같은 응답).

    /** 계정 값(비밀값 제외) — 상태 무관 */
    UserDetailData findDetailByUserSeq(Integer userSeq);

    /** 여러 계정 값(비밀값 제외) — 상태 무관, 잠금 없음. 없는 seq 는 결과에서 빠진다 */
    List<UserDetailData> findDetailsByUserSeqs(List<Integer> userSeqs);

    /** 테넌트의 DELETED 가 아닌 계정 전부 — 테넌트 삭제 대상 */
    List<UserDetailData> findUndeletedDetailsByTenantSeq(int tenantSeq);

    /** ACTIVE 계정만 */
    UserDetailData findActiveDetailByUserSeq(Integer userSeq);

    /** 계정 값 + 보유 역할 전부(1회 조회) — 상태 무관, 없으면 USER_NOT_FOUND. 응답에 roles 를 같이 내는 단건 조회 전용 */
    UserDetailWithRolesData findDetailWithRoles(Integer userSeq);

    /** ACTIVE 계정 값 + 보유 역할 전부(1회 조회), 아니면 USER_NOT_FOUND */
    UserDetailWithRolesData findActiveDetailWithRoles(Integer userSeq);

    /** ACTIVE 계정이고 그 역할(tb_user_role)을 보유해야 함 — API Key 인증의 대상 계정 확인 */
    UserDetailData findActiveDetailHoldingRole(Integer userSeq, RoleType roleType);

    /** ACTIVE 계정이고 테넌트가 같아야 함 */
    UserDetailData findActiveDetailInTenant(int userSeq, int tenantSeq);

    /**
     * ACTIVE 상태 SUPER_ADMIN 중 등록일이 가장 빠른 1명.
     * 고정 API Key 인증의 actor 매핑용 — 존재를 전제로 사용한다.
     */
    UserDetailData findEarliestActiveSuperAdmin();

    /** 계정 값 + 비밀값(encPwd·mfaSecretKey) — 인증 계층(로그인·MFA·비밀번호 변경) 전용, 상태 무관 */
    UserAuthData findAuthByUserSeq(Integer userSeq);

    /** ACTIVE 계정이고 테넌트가 같아야 함 — 비밀값 포함 */
    UserAuthData findActiveAuthInTenant(int userSeq, int tenantSeq);

    /** 로그인 — ACTIVE·USER role·같은 테넌트(구 findActiveUserInTenant(String, int)). 비밀값 포함 */
    UserAuthData findActiveAuthInTenant(String userId, int tenantSeq);

    /** 관리자 로그인 — ACTIVE·ADMIN·같은 테넌트(구 findActiveAdminInTenant). 비밀값 포함 */
    UserAuthData findActiveAdminAuthInTenant(String adminId, int tenantSeq);

    /** 슈퍼관리자 로그인 — ACTIVE·SUPER_ADMIN·테넌트 없음(구 findBySuperAdminIdAndIsActive). 비밀값 포함 */
    UserAuthData findSuperAdminAuthByUserId(String superAdminId);

    UserQuotaData findUserQuotaByUserSeq(Integer userSeq);

    /** 테넌트 사용자 할당량(total_quota_byte) 합 */
    long sumTotalQuotaByteByTenantSeq(Integer tenantSeq);

    /** 계정 목록(커서) — 관리자 계정 관리 화면, 역할 필터(보유 기준) */
    List<UserListData> findDisplayableUsers(UserListQuery query);
    
    List<UserData> findVisibleUsers(List<Integer> userSeqList);

    /**
     * userId 목록으로 각 사용자의 tenantSeq 조회 (이메일 수신자 테넌트 판별용).
     */
    List<UserData> findUserTenantsByUserIds(List<String> userIds);

    /**
     * 문서함 소유주 필터 검색 (내 드라이브/휴지통/최근/중요/공유문서함/전체검색).
     */
    List<UserData> findOwnerCandidates(String searchKeyword, RoleType roleType, List<UserStatus> userStatuses, int requestUserSeq);

    /**
     * 공유 등록/수정 모달의 공유 대상 후보 검색.
     * tenantSeq 가 있으면 그 테넌트로 한정(외부공유 불허), null 이면 전 테넌트 검색(외부공유 허용).
     */
    List<UserData> findShareTargetCandidates(String searchKeyword, RoleType roleType, List<UserStatus> userStatuses, Integer tenantSeq);
    
    /** 같은 테넌트의 삭제되지 않은 계정이 이미 쓰는 userId(userId 는 테넌트 안에서만 유일) */
    Set<String> findExistingUserIds(int tenantSeq, List<String> userIds);

    List<UserBulkStatusData> findUserStatusByUserIds(UserBulkStatusQuery query);

    /** ROLE_ANONYMOUS 계정 조회. 시스템에 단 1개 존재하므로 LIMIT 1 보장. */
    UserDetailData findAnonymousUser();

    /**
     * 테넌트 안에서 계정 검색 — userId·userName 부분 일치.
     * 여러 role 을 한 목록으로 받는다 — roleTypes 중 하나라도 보유한 계정, 항목에 보유 역할 전부.
     */
    List<UserSearchData> searchTenantUsers(String searchKeyword, Integer tenantSeq, List<RoleType> roleTypes, List<UserStatus> userStatuses);

}
