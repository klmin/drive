package com.thinkfree.drive.user.service.impl;

import com.thinkfree.drive.user.converter.UserConverter;
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
import com.thinkfree.drive.user.exception.UserErrorCode;
import com.thinkfree.drive.user.query.UserBulkStatusQuery;
import com.thinkfree.drive.user.query.UserListQuery;
import com.thinkfree.drive.user.repository.UserQueryRepository;
import com.thinkfree.drive.user.service.UserFindService;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DefaultUserFindService implements UserFindService {

    private final UserQueryRepository userQueryRepository;
    private final UserConverter userConverter;

    public Integer findTenantSeqByUserSeq(Integer userSeq) {
        Integer tenantSeq = userQueryRepository.findTenantSeqByUserSeq(userSeq);
        DriveAssert.throwIfNull(tenantSeq, UserErrorCode.USER_NOT_FOUND);
        return tenantSeq;
    }

    @Override
    public Integer findTenantSeqIfProtected(int userSeq) {
        return userQueryRepository.findTenantSeqIfProtected(userSeq);
    }

    // ------------------------------------------------------------------ DTO(JPQL 생성자 식) — ACTIVE·테넌트 판정은 여기서(Java)

    @Override
    public UserDetailData findDetailByUserSeq(Integer userSeq) {
        return requireFound(userQueryRepository.findDetailByUserSeq(userSeq));
    }

    @Override
    public List<UserDetailData> findDetailsByUserSeqs(List<Integer> userSeqs) {
        return userQueryRepository.findDetailsByUserSeqs(userSeqs);
    }

    @Override
    public List<UserDetailData> findUndeletedDetailsByTenantSeq(int tenantSeq) {
        return userQueryRepository.findUndeletedDetailsByTenantSeq(tenantSeq);
    }

    @Override
    public UserDetailData findActiveDetailByUserSeq(Integer userSeq) {
        return requireFound(userQueryRepository.findActiveDetailByUserSeq(userSeq));
    }

    @Override
    public UserDetailWithRolesData findDetailWithRoles(Integer userSeq) {
        return requireFound(userQueryRepository.findDetailWithRoles(userSeq));
    }

    @Override
    public UserDetailWithRolesData findActiveDetailWithRoles(Integer userSeq) {
        return requireFound(userQueryRepository.findActiveDetailWithRoles(userSeq));
    }

    @Override
    public UserDetailData findActiveDetailHoldingRole(Integer userSeq, RoleType roleType) {
        return requireFound(userQueryRepository.findActiveDetailHoldingRole(userSeq, roleType));
    }

    @Override
    public UserDetailData findActiveDetailInTenant(int userSeq, int tenantSeq) {
        var user = findActiveDetailByUserSeq(userSeq);
        requireSameTenant(user.getTenantSeq(), tenantSeq);
        return user;
    }

    @Override
    public UserDetailData findEarliestActiveSuperAdmin() {
        return requireFound(userQueryRepository.findEarliestActiveSuperAdmin());
    }

    @Override
    public UserAuthData findAuthByUserSeq(Integer userSeq) {
        return requireFound(userQueryRepository.findAuthByUserSeq(userSeq));
    }

    @Override
    public UserAuthData findActiveAuthInTenant(int userSeq, int tenantSeq) {
        var user = findAuthByUserSeq(userSeq);
        requireActive(user.getStatus());
        requireSameTenant(user.getTenantSeq(), tenantSeq);
        return user;
    }

    @Override
    public UserAuthData findActiveAuthInTenant(String userId, int tenantSeq) {
        return requireFound(userQueryRepository.findActiveAuthByUserId(tenantSeq, userId, RoleType.USER));
    }

    @Override
    public UserAuthData findActiveAdminAuthInTenant(String adminId, int tenantSeq) {
        return requireFound(userQueryRepository.findActiveAuthByUserId(tenantSeq, adminId, RoleType.ADMIN));
    }

    @Override
    public UserAuthData findSuperAdminAuthByUserId(String superAdminId) {
        return requireFound(userQueryRepository.findActiveSuperAdminAuthByUserId(superAdminId));
    }

    private static <T> T requireFound(T found) {
        DriveAssert.throwIfNull(found, UserErrorCode.USER_NOT_FOUND);
        return found;
    }

    /** 구 findByUserSeqAndIsActive(STATUS='ACTIVE') 와 같은 판정 — 비활성은 없는 계정과 같은 USER_NOT_FOUND */
    private static void requireActive(UserStatus status) {
        DriveAssert.throwIfFalse(status == UserStatus.ACTIVE, UserErrorCode.USER_NOT_FOUND);
    }

    /** 구 엔티티판 verifyTenant 와 같은 판정(user.tenantSeq == tenantSeq — null 이면 NPE 도 같음) */
    private static void requireSameTenant(Integer userTenantSeq, int tenantSeq) {
        DriveAssert.throwIfFalse(userTenantSeq == tenantSeq, UserErrorCode.USER_NOT_FOUND);
    }

    public UserQuotaData findUserQuotaByUserSeq(Integer userSeq) {
        UserQuotaData quotaData = userQueryRepository.findUserQuotaByUserSeq(userSeq);
        DriveAssert.throwIfNull(quotaData, UserErrorCode.USER_NOT_FOUND);
        return quotaData;
    }

    @Override
    public long sumTotalQuotaByteByTenantSeq(Integer tenantSeq) {
        return userQueryRepository.sumTotalQuotaByteByTenantSeq(tenantSeq);
    }

    @Override
    public List<UserListData> findDisplayableUsers(UserListQuery query) {
        return userQueryRepository.findDisplayableUsers(query);
    }
    
    @Override
    public List<UserData> findVisibleUsers(List<Integer> userSeqList) {
        var query = userConverter.toUserVisibleQuery(userSeqList);
        return userQueryRepository.findVisibleUsers(query);
    }

    @Override
    public List<UserData> findUserTenantsByUserIds(List<String> userIds) {
        return userQueryRepository.findUserTenantsByUserIds(userIds);
    }

    @Override
    public List<UserData> findOwnerCandidates(String searchKeyword, RoleType roleType, List<UserStatus> userStatuses, int requestUserSeq) {
        var query = userConverter.toOwnerSearchQuery(searchKeyword, roleType, userStatuses, requestUserSeq);
        return userQueryRepository.findOwnerCandidates(query);
    }

    @Override
    public List<UserData> findShareTargetCandidates(String searchKeyword, RoleType roleType, List<UserStatus> userStatuses, Integer tenantSeq) {
        var query = userConverter.toUserSearchQuery(searchKeyword, roleType, userStatuses, tenantSeq);
        return userQueryRepository.findShareCandidates(query);
    }
    
    @Override
    public Set<String> findExistingUserIds(int tenantSeq, List<String> userIds) {
        return userQueryRepository.findExistingUserIds(tenantSeq, userIds);
    }

    @Override
    public List<UserBulkStatusData> findUserStatusByUserIds(UserBulkStatusQuery query) {
        return userQueryRepository.findUserStatusByUserIds(query);
    }

    @Override
    public UserDetailData findAnonymousUser() {
        return requireFound(userQueryRepository.findAnonymousUser());
    }

    @Override
    public List<UserSearchData> searchTenantUsers(String searchKeyword, Integer tenantSeq, List<RoleType> roleTypes, List<UserStatus> userStatuses) {
        var query = userConverter.toUserTenantSearchQuery(searchKeyword, tenantSeq, roleTypes, userStatuses);
        return userQueryRepository.searchTenantUsers(query);
    }

}
