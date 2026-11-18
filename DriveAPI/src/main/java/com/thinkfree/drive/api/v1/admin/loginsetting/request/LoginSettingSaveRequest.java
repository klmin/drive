package com.thinkfree.drive.api.v1.admin.loginsetting.request;

import io.swagger.v3.oas.annotations.media.Schema;
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

public record LoginSettingSaveRequest(
        @Schema(title = "Sign-in mode", description = "Select local account sign-in or the configured external sign-in mode.")
        @NotNull
        LoginType loginType,
        @Schema(title = "Sign-in providers", description = "Add the external sign-in providers and their protocol settings.")
        @Valid
        List<ProviderItemRequest> providers
) {

    @AssertTrue(message = "SSO 로그인 타입 선택 시 providers는 필수입니다")
    private boolean isProvidersRequired() {
        if (loginType == LoginType.LOCAL){
            return true;
        }
        return !ObjectUtils.isEmpty(providers);
    }

    @AssertTrue(message = "loginType 과 protocolType이 일치하지 않습니다.")
    private boolean isLoginTypeEqualProtocolType() {
        if (loginType == LoginType.LOCAL){
            return true;
        }
        if (ObjectUtils.isEmpty(providers)){
            return true;
        }
        var protocolType = switch (loginType) {
            case JWT  -> SsoProviderProtocolType.JWT;
            case OIDC -> SsoProviderProtocolType.OIDC;
            default -> null;
        };
        return providers.stream().allMatch(p -> p.config().type() == protocolType);
    }

    @AssertTrue(message = "JWT는 단건만 허용합니다")
    private boolean isJwtSingleItem() {
        if (loginType != LoginType.JWT){
            return true;
        }
        if (ObjectUtils.isEmpty(providers)){
            return true;
        }
        return providers.size() == 1;
    }

    @AssertTrue(message = "active 상태의 provider는 하나만 허용합니다")
    private boolean isOnlyOneActive() {
        if (loginType != LoginType.OIDC){
            return true;
        }
        if (ObjectUtils.isEmpty(providers)){
            return true;
        }
        return providers.stream()
                        .filter(p -> p.status() == SsoProviderStatus.ACTIVE)
                        .count() == 1;

    }

    @AssertTrue(message = "displayName 이 중복됩니다")
    private boolean isDisplayNameUnique() {
        if (ObjectUtils.isEmpty(providers)){
            return true;
        }
        long distinctCount = providers.stream()
                                      .map(ProviderItemRequest::displayName)
                                      .filter(Objects::nonNull)
                                      .map(String::trim)
                                      .distinct()
                                      .count();
        return distinctCount == providers.size();
    }

}
