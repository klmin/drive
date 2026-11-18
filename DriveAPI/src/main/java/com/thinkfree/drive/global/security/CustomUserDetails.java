package com.thinkfree.drive.global.security;

import com.thinkfree.drive.apikey.enums.ApiKeyScope;
import com.thinkfree.drive.global.enums.AuthSource;
import com.thinkfree.drive.user.enums.RoleType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

public class CustomUserDetails implements UserDetails {
    private final UserDetailDto user;

    public CustomUserDetails(UserDetailDto user) {
        this.user = user;
    }

    /**
     * role 과 scope 를 함께 싣는다.
     * role 은 동작 역할 + 나머지 보유 역할 — 웹 JWT·API Key·MCP OAuth 는 보유 역할 = 동작 역할 1개라 지금은 모두 1개(여러 개를 싣는 경로는 없음).
     * scope 는 {@code SCOPE_api:read} 형태로 들어가며, 외부 API Key 인증에만 존재한다.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Collection<GrantedAuthority> collection = new ArrayList<>();
        collection.add((GrantedAuthority) () -> String.valueOf(user.roleType()));
        user.roles().stream()
                .map(RoleType::getAuthority)
                .filter(authority -> !authority.equals(user.roleType()))
                .forEach(authority -> collection.add((GrantedAuthority) () -> authority));
        user.scopes().forEach(scope -> collection.add((GrantedAuthority) scope::authority));
        return collection;
    }

    public Set<ApiKeyScope> getScopes() {
        return user.scopes();
    }

    public boolean hasScope(ApiKeyScope scope) {
        return user.scopes().contains(scope);
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return user.userName();
    }

    public Integer getUserSeq() {
        return user.userSeq();
    }

    public String getUserId() {
        return user.userId();
    }

    /** 동작 역할(이번 요청이 쓰는 역할 1개, authority 문자열) */
    public String getRoleType() {
        return user.roleType();
    }

    /** 보유 역할 전부 — 동작 역할만 실린 인증 경로면 그 1개 */
    public Set<RoleType> getRoles() {
        return user.roles();
    }

    public boolean holds(RoleType role) {
        return user.roles().contains(role);
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
    
    public AuthSource getAuthSource() {
        return user.authSource();
    }

    public Integer getTenantSeq() {
        return user.tenantSeq();
    }
    
    public boolean isSuperAdmin() {
        return RoleType.SUPER_ADMIN.getAuthority().equals(user.roleType());
    }

    /**
     * 외부 API Key 인증으로 들어온 요청인지 여부.
     * role 은 ADMIN 으로 동일하지만 실제 호출 주체(외부 시스템 vs 사람)는 AuthSource 로 구분한다.
     * 외부 호출만 차단해야 하는 비즈니스 로직이 있다면 이 메서드로 가드.
     */
    public boolean isExternalApi() {
        return AuthSource.EXTERNAL == user.authSource();
    }

    public boolean isAnonymous() {
        return RoleType.ANONYMOUS.getAuthority().equals(user.roleType());
    }
}
