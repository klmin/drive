package com.thinkfree.drive.application.apikey;

import com.thinkfree.drive.application.apikey.command.ApiKeyIssueCommand;
import com.thinkfree.drive.application.apikey.converter.ApiKeyCommandConverter;
import com.thinkfree.drive.apikey.service.ApiKeyIssuer;
import com.thinkfree.drive.apikey.service.ApiKeyService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.tenant.exception.TenantErrorCode;
import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantPolicy;
import com.thinkfree.drive.user.enums.RoleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 키 발급의 SYSTEM_MANAGED + SINGLE 제한 — 관리자·사용자 발급은 거부, 슈퍼관리자 발급은 판정하지 않음 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApiKeyUseCase.issue — SINGLE 테넌트 발급 제한")
class ApiKeyUseCaseSingleTenantTest {

    private static final int TENANT = 7;

    @Mock
    private ApiKeyService apiKeyService;
    @Mock
    private ApiKeyIssuer apiKeyIssuer;
    @Mock
    private ApiKeyCommandConverter apiKeyCommandConverter;
    @Mock
    private TenantPolicy tenantPolicy;

    @InjectMocks
    private ApiKeyUseCase useCase;

    private final ApiKeyIssueCommand command = ApiKeyIssueCommand.builder().keyName("k").build();

    @Test
    @DisplayName("관리자 발급 — SINGLE 이면 SYSTEM_MANAGED_RESTRICTED, 저장 없음")
    void adminIssue_singleTenant_rejected() {
        doThrow(ApiRuntimeException.type(TenantErrorCode.SYSTEM_MANAGED_RESTRICTED)).when(tenantPolicy).require(TENANT, TenantFeature.API_KEY_MANAGE);

        var e = catchThrowableOfType(ApiRuntimeException.class,
                () -> useCase.issue(command, new ApiKeyActor(2, RoleType.ADMIN, TENANT)));

        assertThat(e.getCode()).isEqualTo(TenantErrorCode.SYSTEM_MANAGED_RESTRICTED);
        verify(apiKeyService, never()).save(any());
    }

    @Test
    @DisplayName("사용자 발급 — 로그인 사용자 테넌트로 판정")
    void userIssue_checksActorTenant() {
        useCase.issue(command, new ApiKeyActor(3, RoleType.USER, TENANT));

        verify(tenantPolicy).require(TENANT, TenantFeature.API_KEY_MANAGE);
    }

    @Test
    @DisplayName("슈퍼관리자 발급 — 판정 없음")
    void superAdminIssue_skipsCheck() {
        useCase.issue(command, new ApiKeyActor(1, RoleType.SUPER_ADMIN, null));

        verify(tenantPolicy, never()).require(any(), any());
    }
}
