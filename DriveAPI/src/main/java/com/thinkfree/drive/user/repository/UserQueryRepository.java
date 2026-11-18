package com.thinkfree.drive.user.repository;

import com.thinkfree.drive.user.data.UserAuthData;
import com.thinkfree.drive.user.data.UserBulkStatusData;
import com.thinkfree.drive.user.data.UserData;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.data.UserDetailWithRolesData;
import com.thinkfree.drive.user.data.UserListData;
import com.thinkfree.drive.user.data.UserPendingDeleteData;
import com.thinkfree.drive.user.data.UserQuotaData;
import com.thinkfree.drive.user.data.UserSearchData;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;
import com.thinkfree.drive.user.query.*;

import java.util.List;
import java.util.Set;

/**
 * 읽기 전용(FindService·보안 필터가 주입). {@code *Data} DTO·스칼라 조회(JPQL·jOOQ). 엔티티는 반환하지 않는다 — 고칠 엔티티는 {@link UserRepository}.
 * 쓰기와 쓰기 흐름 안의 읽기(FOR UPDATE·저장 전 count)는 {@link UserRepository}.
 */
public interface UserQueryRepository {

    // --- DTO 단건(JPQL 생성자 식 — 엔티티를 반환하지 않는다, 10-12). 없으면 null.
    //     PK 조회는 상태 조건 없음(ACTIVE 판정은 아래 default 메서드·FindService, 테넌트 판정은 FindService), userId 조회는 상태·role 이 달라 여러 행일 수 있어 WHERE 로 고른다.
    /** 계정 값(비밀값 제외) — 상태 무관 */
    UserDetailData findDetailByUserSeq(Integer userSeq);
    /** 여러 userSeq 의 계정 값(비밀값 제외) — 상태 무관, 없는 seq 는 결과에서 빠진다 */
    List<UserDetailData> findDetailsByUserSeqs(List<Integer> userSeqs);
    /** 테넌트의 DELETED 가 아닌 계정 전부(user_seq ASC) — 테넌트 삭제 대상 */
    List<UserDetailData> findUndeletedDetailsByTenantSeq(int tenantSeq);
    /** userSeq 의 ACTIVE 계정, 아니면 null — PK 조회 한 번 + 상태 판정(Java). 구 findByUserSeqAndIsActive */
    default UserDetailData findActiveDetailByUserSeq(Integer userSeq) {
        UserDetailData user = findDetailByUserSeq(userSeq);
        return user != null && user.getStatus() == UserStatus.ACTIVE ? user : null;
    }
    /** 계정 값 + 보유 역할 전부를 한 번에(ARRAY 상관 서브쿼리) — 상태 무관, 없으면 null. 응답에 roles 를 같이 내는 단건 조회 전용 */
    UserDetailWithRolesData findDetailWithRoles(Integer userSeq);
    /** userSeq 의 ACTIVE 계정 + 보유 역할 전부, 아니면 null — {@link #findDetailWithRoles} 한 번 + 상태 판정(Java) */
    default UserDetailWithRolesData findActiveDetailWithRoles(Integer userSeq) {
        UserDetailWithRolesData user = findDetailWithRoles(userSeq);
        return user != null && user.getStatus() == UserStatus.ACTIVE ? user : null;
    }
    /** userSeq 의 ACTIVE 계정이 roleType 을 보유(tb_user_role)할 때 그 계정 값, 아니면 null — 웹 JWT 필터의 요청당 1회 조회(활성·역할 보유·사용자 로드) */
    UserDetailData findActiveDetailHoldingRole(Integer userSeq, RoleType roleType);
    /** 계정 값 + 비밀값 — 인증 계층 전용, 상태 무관 */
    UserAuthData findAuthByUserSeq(Integer userSeq);
    /** 테넌트 안에서 userId 를 가진 ACTIVE 상태의 USER role 계정(userId 는 테넌트 안에서만 유일) */
    UserDetailData findActiveDetailByUserId(int tenantSeq, String userId);
    /** 테넌트 안에서 userId 를 가진 ACTIVE 상태의 roleType 계정 — 비밀값 포함(로그인 계열) */
    UserAuthData findActiveAuthByUserId(int tenantSeq, String userId, RoleType roleType);
    /** 테넌트 없는(tenant_seq IS NULL) ACTIVE SUPER_ADMIN 계정 — 비밀값 포함(슈퍼관리자 로그인) */
    UserAuthData findActiveSuperAdminAuthByUserId(String userId);
    /** ACTIVE SUPER_ADMIN 중 등록일이 가장 빠른 1명 — 고정 API Key 인증의 actor 매핑용 */
    UserDetailData findEarliestActiveSuperAdmin();
    UserDetailData findAnonymousUser();

    // --- 스칼라
    Integer findTenantSeqByUserSeq(Integer userSeq);
    /** 보호 계정(is_protected='Y')이면 그 테넌트, 아니면(또는 테넌트 없는 보호 계정) null */
    Integer findTenantSeqIfProtected(int userSeq);
    int countByUserSeqsAndTenant(List<Integer> userSeqList, Integer tenantSeq);
    /** 저장 전 중복 검사 — 같은 테넌트(null 이면 테넌트 없는 계정)에 userId 를 쓰는 삭제되지 않은 계정이 있는지 */
    boolean existsUserId(Integer tenantSeq, String userId);
    /** 일괄 등록 중복 검사 — 같은 테넌트(null 이면 테넌트 없는 계정)의 삭제되지 않은 계정이 이미 쓰는 userId */
    Set<String> findExistingUserIds(Integer tenantSeq, List<String> userIds);
    UserQuotaData findUserQuotaByUserSeq(Integer userSeq);
    /** 테넌트 사용자 할당량(total_quota_byte) 합 */
    long sumTotalQuotaByteByTenantSeq(Integer tenantSeq);

    // --- DTO
    /** 계정 목록 — 역할(tb_user_role 보유) 필터, 최근 로그인 포함, user_seq ASC 커서 */
    List<UserListData> findDisplayableUsers(UserListQuery query);
    List<UserData> findVisibleUsers(UserVisibleQuery query);
    List<UserData> findUserTenantsByUserIds(List<String> userIds);
    List<UserData> findShareCandidates(UserSearchQuery query);
    List<UserData> findOwnerCandidates(UserSearchQuery query);
    List<UserSearchData> searchTenantUsers(UserTenantSearchQuery query);
    List<UserPendingDeleteData> findPendingDeleteUsers(UserPendingDeleteListQuery query);
    List<UserPendingDeleteData> findPendingDeleteTargets(UserPendingDeleteTargetQuery query);
    List<UserBulkStatusData> findUserStatusByUserIds(UserBulkStatusQuery query);
}
