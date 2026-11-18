package com.thinkfree.drive.apikey.service;

import com.thinkfree.drive.apikey.converter.ApiKeyConverter;
import com.thinkfree.drive.apikey.entity.ApiKey;
import com.thinkfree.drive.apikey.enums.ApiKeyStatus;
import com.thinkfree.drive.apikey.exception.ApiKeyErrorCode;
import com.thinkfree.drive.apikey.repository.ApiKeyRepository;
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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 키 조작(만료일·상태·재발급)의 보호 키·SINGLE 제한 — 관리자·사용자 범위만 거부, 슈퍼관리자 범위는 통과, 범위 밖 키는 제한보다 404 가 먼저.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultApiKeyService — 보호 키·SYSTEM_MANAGED SINGLE 조작 제한")
class DefaultApiKeyServiceProtectionTest {

    private static final int KEY_SEQ = 10;
    private static final int TENANT = 7;
    private static final Instant EXPIRE = Instant.parse("2030-01-01T00:00:00Z");

    @Mock
    private ApiKeyRepository apiKeyRepository;
    @Mock
    private ApiKeyConverter apiKeyConverter;
    @Mock
    private TenantPolicy tenantPolicy;

    @InjectMocks
    private DefaultApiKeyService service;

    private ApiKey key(boolean isProtected) {
        ApiKey apiKey = ApiKey.builder().apiKeySeq(KEY_SEQ).tenantSeq(TENANT).roleType(RoleType.USER)
                .status(ApiKeyStatus.ACTIVE).expireDate(EXPIRE).isProtected(isProtected).build();
        when(apiKeyRepository.findById(KEY_SEQ)).thenReturn(apiKey);
        return apiKey;
    }

    @Test
    @DisplayName("관리자 범위 — 보호 키 만료일·상태·재발급 모두 API_KEY_PROTECTED, 값 그대로")
    void tenantScope_protectedKey_rejected() {
        ApiKey apiKey = key(true);

        assertProtected(() -> service.updateExpireDateByTenant(KEY_SEQ, TENANT, Instant.now()));
        assertProtected(() -> service.updateStatusByTenant(KEY_SEQ, TENANT, ApiKeyStatus.INACTIVE));
        assertProtected(() -> service.reissueByTenant(KEY_SEQ, TENANT));

        assertThat(apiKey.getStatus()).isEqualTo(ApiKeyStatus.ACTIVE);
        assertThat(apiKey.getExpireDate()).isEqualTo(EXPIRE);
    }

    @Test
    @DisplayName("사용자 범위 — 보호 키 비활성화 API_KEY_PROTECTED")
    void registerUserScope_protectedKey_rejected() {
        ApiKey apiKey = ApiKey.builder().apiKeySeq(KEY_SEQ).tenantSeq(TENANT).roleType(RoleType.USER).registerUserSeq(3)
                .status(ApiKeyStatus.ACTIVE).isProtected(true).build();
        when(apiKeyRepository.findById(KEY_SEQ)).thenReturn(apiKey);

        assertProtected(() -> service.updateStatusByRegisterUser(KEY_SEQ, 3, ApiKeyStatus.INACTIVE));
    }

    @Test
    @DisplayName("슈퍼관리자 범위 — 보호 키도 변경, SINGLE 판정 안 함")
    void superAdminScope_protectedKey_allowed() {
        ApiKey apiKey = key(true);

        service.updateStatusForSuperAdmin(KEY_SEQ, ApiKeyStatus.INACTIVE);

        assertThat(apiKey.getStatus()).isEqualTo(ApiKeyStatus.INACTIVE);
        verify(tenantPolicy, never()).require(any(), any());
    }

    @Test
    @DisplayName("관리자 범위 — 보호 아닌 키도 SINGLE 테넌트면 SYSTEM_MANAGED_RESTRICTED")
    void tenantScope_singleTenant_rejected() {
        ApiKey apiKey = key(false);
        doThrow(ApiRuntimeException.type(TenantErrorCode.SYSTEM_MANAGED_RESTRICTED)).when(tenantPolicy).require(TENANT, TenantFeature.API_KEY_MANAGE);

        var e = catchThrowableOfType(ApiRuntimeException.class, () -> service.updateStatusByTenant(KEY_SEQ, TENANT, ApiKeyStatus.INACTIVE));

        assertThat(e.getCode()).isEqualTo(TenantErrorCode.SYSTEM_MANAGED_RESTRICTED);
        assertThat(apiKey.getStatus()).isEqualTo(ApiKeyStatus.ACTIVE);
    }

    @Test
    @DisplayName("관리자 범위 — 보호 키도 SINGLE 테넌트면 API_KEY_PROTECTED 가 아니라 SYSTEM_MANAGED_RESTRICTED")
    void tenantScope_singleTenant_protectedKey_restricted() {
        ApiKey apiKey = key(true);
        doThrow(ApiRuntimeException.type(TenantErrorCode.SYSTEM_MANAGED_RESTRICTED)).when(tenantPolicy).require(TENANT, TenantFeature.API_KEY_MANAGE);

        var e = catchThrowableOfType(ApiRuntimeException.class, () -> service.updateStatusByTenant(KEY_SEQ, TENANT, ApiKeyStatus.INACTIVE));

        assertThat(e.getCode()).isEqualTo(TenantErrorCode.SYSTEM_MANAGED_RESTRICTED);
        assertThat(apiKey.getStatus()).isEqualTo(ApiKeyStatus.ACTIVE);
    }

    @Test
    @DisplayName("관리자 범위 — 보호 아닌 키·MULTI 테넌트면 변경")
    void tenantScope_normalKey_allowed() {
        ApiKey apiKey = key(false);

        service.updateStatusByTenant(KEY_SEQ, TENANT, ApiKeyStatus.INACTIVE);

        assertThat(apiKey.getStatus()).isEqualTo(ApiKeyStatus.INACTIVE);
        verify(tenantPolicy).require(TENANT, TenantFeature.API_KEY_MANAGE);
    }

    @Test
    @DisplayName("범위 밖 보호 키 — API_KEY_NOT_FOUND(보호 여부가 드러나지 않음)")
    void outOfScope_protectedKey_notFound() {
        key(true);

        var e = catchThrowableOfType(ApiRuntimeException.class, () -> service.updateStatusByTenant(KEY_SEQ, TENANT + 1, ApiKeyStatus.INACTIVE));

        assertThat(e.getCode()).isEqualTo(ApiKeyErrorCode.API_KEY_NOT_FOUND);
    }

    private static void assertProtected(Runnable call) {
        var e = catchThrowableOfType(ApiRuntimeException.class, call::run);
        assertThat(e.getCode()).isEqualTo(ApiKeyErrorCode.API_KEY_PROTECTED);
    }
}
