package com.thinkfree.drive.global.security;

import com.thinkfree.drive.apikey.enums.ApiKeyScope;
import com.thinkfree.drive.global.enums.AuthSource;
import com.thinkfree.drive.user.enums.RoleType;

import java.util.EnumSet;
import java.util.Set;

/**
 * @param roleType 동작 역할 — 이번 요청이 쓰는 역할 1개(authority 문자열, 예 {@code ROLE_ADMIN}).
 *                 웹 로그인은 로그인 화면의 역할, API Key 는 키 역할이다.
 * @param scopes   API Key 에 부여된 scope. 외부 API Key 인증에만 값이 있고(role 무관하게 모든 키가 갖는다),
 *                 내부 JWT 인증에서는 항상 비어 있다. scope 는 위임 개념이라 사용자가 직접 행동하는
 *                 내부 로그인에는 적용되지 않는다.
 * @param roles    보유 역할 전부(tb_user_role). null·빈 값이면 동작 역할 1개로 채운다.
 */
public record UserDetailDto(
        Integer userSeq,
        String userId,
        String userName,
        String roleType,
        AuthSource authSource,
        Integer tenantSeq,
        Set<ApiKeyScope> scopes,
        Set<RoleType> roles
) {
    public UserDetailDto {
        scopes = (scopes == null) ? EnumSet.noneOf(ApiKeyScope.class) : Set.copyOf(scopes);
        if (roles == null || roles.isEmpty()) {
            RoleType acting = RoleType.fromAuthority(roleType);
            roles = acting == null ? Set.of() : Set.of(acting);
        } else {
            roles = Set.copyOf(roles);
        }
    }

    /** 보유 역할을 따로 싣지 않는 인증 경로용 — 보유 역할 = 동작 역할 1개 */
    public UserDetailDto(Integer userSeq, String userId, String userName, String roleType,
                         AuthSource authSource, Integer tenantSeq, Set<ApiKeyScope> scopes) {
        this(userSeq, userId, userName, roleType, authSource, tenantSeq, scopes, null);
    }

    /** scope 개념이 없는 인증 경로(내부 JWT 등)용 */
    public UserDetailDto(Integer userSeq, String userId, String userName, String roleType,
                         AuthSource authSource, Integer tenantSeq) {
        this(userSeq, userId, userName, roleType, authSource, tenantSeq, null, null);
    }
}
