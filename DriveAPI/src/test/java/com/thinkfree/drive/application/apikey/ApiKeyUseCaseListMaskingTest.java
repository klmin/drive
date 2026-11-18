package com.thinkfree.drive.application.apikey;

import com.thinkfree.drive.apikey.data.ApiKeyData;
import com.thinkfree.drive.apikey.service.ApiKeyFindService;
import com.thinkfree.drive.application.apikey.command.ApiKeyListCommand;
import com.thinkfree.drive.application.apikey.converter.ApiKeyCommandConverter;
import com.thinkfree.drive.application.apikey.converter.ApiKeyInfoConverter;
import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 목록 키 값 — SYSTEM_MANAGED + SINGLE 테넌트의 관리자·사용자 경로만 블러용 값, 슈퍼관리자 경로는 판정하지 않음 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApiKeyUseCase.findAllPaginated — 키 값 마스킹 판정")
class ApiKeyUseCaseListMaskingTest {

    private static final int TENANT = 7;

    @Mock
    private ApiKeyFindService apiKeyFindService;
    @Mock
    private ApiKeyCommandConverter apiKeyCommandConverter;
    @Mock
    private ApiKeyInfoConverter apiKeyInfoConverter;
    @Mock
    private TenantPolicy tenantPolicy;

    @InjectMocks
    private ApiKeyUseCase useCase;

    private final ApiKeyListCommand command = ApiKeyListCommand.builder().pageSize(20).build();
    private final List<ApiKeyData> keys = List.of(ApiKeyData.builder().apiKeySeq(1).tenantSeq(TENANT).apiKey("k").build());

    @Test
    @DisplayName("관리자 경로 — 키 값 보기가 막힌 테넌트면 블러용 값")
    void tenantScope_restricted_hides() {
        when(apiKeyFindService.findAll(any())).thenReturn(keys);
        when(tenantPolicy.allows(TENANT, TenantFeature.API_KEY_INFO_VIEW)).thenReturn(false);

        useCase.findAllPaginated(command, ApiKeyScope.tenant(TENANT));

        verify(apiKeyInfoConverter).toListInfos(keys, true);
    }

    @Test
    @DisplayName("관리자 경로 — 허용 테넌트면 전체 '*'")
    void tenantScope_allowed_masksOnly() {
        when(apiKeyFindService.findAll(any())).thenReturn(keys);
        when(tenantPolicy.allows(TENANT, TenantFeature.API_KEY_INFO_VIEW)).thenReturn(true);

        useCase.findAllPaginated(command, ApiKeyScope.tenant(TENANT));

        verify(apiKeyInfoConverter).toListInfos(keys, false);
    }

    @Test
    @DisplayName("사용자 경로 — 키의 테넌트로 판정")
    void registerUserScope_checksKeyTenant() {
        when(apiKeyFindService.findAllByRegisterUser(any())).thenReturn(keys);
        when(tenantPolicy.allows(TENANT, TenantFeature.API_KEY_INFO_VIEW)).thenReturn(false);

        useCase.findAllPaginated(command, ApiKeyScope.registerUser(3));

        verify(apiKeyInfoConverter).toListInfos(keys, true);
    }

    @Test
    @DisplayName("슈퍼관리자 경로 — 판정 없이 전체 '*'")
    void allScope_skipsCheck() {
        when(apiKeyFindService.findAllForSuperAdmin(any())).thenReturn(keys);

        useCase.findAllPaginated(command, ApiKeyScope.all());

        verify(apiKeyInfoConverter).toListInfos(keys, false);
        verify(tenantPolicy, never()).allows(any(), eq(TenantFeature.API_KEY_INFO_VIEW));
    }

    @Test
    @DisplayName("빈 목록 — 판정 없음")
    void emptyList_skipsCheck() {
        when(apiKeyFindService.findAll(any())).thenReturn(List.of());

        useCase.findAllPaginated(command, ApiKeyScope.tenant(TENANT));

        verify(apiKeyInfoConverter).toListInfos(List.of(), false);
        verify(tenantPolicy, never()).allows(any(), any());
    }
}
