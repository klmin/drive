package com.thinkfree.drive.api.v1.auth.usecase;

import com.thinkfree.drive.api.v1.auth.enums.LoginType;
import com.thinkfree.drive.api.v1.auth.response.LoginConfigResponse;
import com.thinkfree.drive.auth.result.LoginConfigResult;
import com.thinkfree.drive.auth.service.AuthService;
import com.thinkfree.drive.auth.service.LoginConfigService;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthUseCase 단위 테스트")
class AuthUseCaseTest {

    private static final int TENANT = 1;

    @Mock
    private AuthService authService;

    @Mock
    private LoginConfigService loginConfigService;

    @Mock
    private TenantPolicy tenantPolicy;

    @InjectMocks
    private AuthUseCase authUseCase;

    @BeforeEach
    void setTenant() {
        TenantContext.set(TENANT);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("loginConfig() - 비밀번호 찾기 가능 여부")
    class LoginConfig {

        @Test
        @DisplayName("메일 허용(SELF_MANAGED) → passwordResetEnabled=true, 로그인 방식 그대로")
        void mailAllowed_true() {
            given(loginConfigService.getLoginConfig(TENANT)).willReturn(new LoginConfigResult(LoginType.OIDC, 3, "/error"));
            given(tenantPolicy.allows(TENANT, TenantFeature.MAIL)).willReturn(true);

            LoginConfigResponse response = authUseCase.loginConfig();

            assertThat(response.passwordResetEnabled()).isTrue();
            assertThat(response.loginType()).isEqualTo(LoginType.OIDC);
            assertThat(response.ssoProviderSeq()).isEqualTo(3);
            assertThat(response.errorRedirectUrl()).isEqualTo("/error");
        }

        @Test
        @DisplayName("메일 차단(SYSTEM_MANAGED) → passwordResetEnabled=false")
        void mailBlocked_false() {
            given(loginConfigService.getLoginConfig(TENANT)).willReturn(new LoginConfigResult(LoginType.LOCAL, null, null));
            given(tenantPolicy.allows(TENANT, TenantFeature.MAIL)).willReturn(false);

            assertThat(authUseCase.loginConfig().passwordResetEnabled()).isFalse();
        }
    }
}
