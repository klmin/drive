package com.thinkfree.drive.api.v1.superadmin.user.usecase;

import com.thinkfree.drive.api.v1.superadmin.user.converter.SuperAdminUserResponseConverter;
import com.thinkfree.drive.api.v1.superadmin.user.request.SuperAdminUserSearchRequest;
import com.thinkfree.drive.api.v1.superadmin.user.response.SuperAdminUserSearchResponse;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.tenant.service.TenantFindService;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;
import com.thinkfree.drive.user.exception.UserErrorCode;
import com.thinkfree.drive.user.service.UserFindService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SuperAdminUserUseCase {

    private final UserFindService userFindService;
    private final TenantFindService tenantFindService;
    private final SuperAdminUserResponseConverter superAdminUserResponseConverter;

    /** 발급 대상으로 지정할 수 있는 role — 본인 대상 발급은 검색이 필요 없어 SUPER_ADMIN 은 제외 */
    private static final List<RoleType> SEARCHABLE_ROLES = List.of(RoleType.ADMIN, RoleType.USER);

    /**
     * API Key 발급 대상 검색.
     * rt 를 주면 그 role 만, 없으면 관리자·사용자를 함께 내린다.
     */
    public List<SuperAdminUserSearchResponse> search(Integer tenantSeq, SuperAdminUserSearchRequest request) {
        // 없는 테넌트면 예외 — 빈 목록으로 내리면 검색어 문제와 구분되지 않는다
        tenantFindService.findByTenantSeq(tenantSeq);

        var roleTypes = resolveRoleTypes(request.rt());
        var users = userFindService.searchTenantUsers(request.sk(), tenantSeq, roleTypes, List.of(UserStatus.ACTIVE));

        return superAdminUserResponseConverter.toSearchResponse(users);
    }

    private List<RoleType> resolveRoleTypes(RoleType roleType) {
        if (roleType == null) {
            return SEARCHABLE_ROLES;
        }
        if (!SEARCHABLE_ROLES.contains(roleType)) {
            throw new ApiRuntimeException(UserErrorCode.INVALID_ROLE_PARAM);
        }
        return List.of(roleType);
    }
}
