package com.thinkfree.drive.api.v1.superadmin.loginsetting.request;

import com.thinkfree.drive.api.v1.auth.enums.LoginType;
import com.thinkfree.drive.api.v1.common.loginsetting.request.ProviderItemRequest;
import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;
import com.thinkfree.drive.sso.provider.enums.SsoProviderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.lang3.ObjectUtils;

import java.util.List;
import java.util.Objects;

public record SuperAdminLoginSettingSaveRequest(
        LoginType l,
        @NotNull
        Boolean ie,
        @Valid
        List<ProviderItemRequest> ps
) {

    private boolean isEnforcedTrue() {
        return Boolean.TRUE.equals(ie);
    }

    @AssertTrue(message = "전체적용 시 loginType은 필수입니다")
    private boolean isLoginTypeRequiredWhenEnforced() {
        if (!isEnforcedTrue()) {
            return true;
        }
        return l != null;
    }

    @AssertTrue(message = "SSO 로그인 타입 선택 시 providers는 필수입니다")
    private boolean isProvidersRequired() {
        if (!isEnforcedTrue()) {
            return true;
        }
        if (l == LoginType.LOCAL){
            return true;
        }
        return !ObjectUtils.isEmpty(ps);
    }

    @AssertTrue(message = "loginType 과 protocolType이 일치하지 않습니다.")
    private boolean isLoginTypeEqualProtocolType() {
        if (!isEnforcedTrue()) {
            return true;
        }
        if (l == LoginType.LOCAL){
            return true;
        }
        if (ObjectUtils.isEmpty(ps)){
            return true;
        }
        var protocolType = switch (l) {
            case JWT  -> SsoProviderProtocolType.JWT;
            case OIDC -> SsoProviderProtocolType.OIDC;
            default -> null;
        };
        return ps.stream().allMatch(p -> p.config().type() == protocolType);
    }

    @AssertTrue(message = "JWT는 단건만 허용합니다")
    private boolean isJwtSingleItem() {
        if (!isEnforcedTrue()) {
            return true;
        }
        if (l != LoginType.JWT){
            return true;
        }
        if (ObjectUtils.isEmpty(ps)){
            return true;
        }
        return ps.size() == 1;
    }

    @AssertTrue(message = "active 상태의 provider는 하나만 허용합니다")
    private boolean isOnlyOneActive() {
        if (!isEnforcedTrue()) {
            return true;
        }
        if (l != LoginType.OIDC){
            return true;
        }
        if (ObjectUtils.isEmpty(ps)){
            return true;
        }
        return ps.stream()
                        .filter(p -> p.status() == SsoProviderStatus.ACTIVE)
                        .count() == 1;
    }

    @AssertTrue(message = "displayName 이 중복됩니다")
    private boolean isDisplayNameUnique() {
        if (!isEnforcedTrue()) {
            return true;
        }
        if (ObjectUtils.isEmpty(ps)){
            return true;
        }
        long distinctCount = ps.stream()
                               .map(ProviderItemRequest::displayName)
                               .filter(Objects::nonNull)
                               .map(String::trim)
                               .distinct()
                               .count();
        return distinctCount == ps.size();
    }

}
