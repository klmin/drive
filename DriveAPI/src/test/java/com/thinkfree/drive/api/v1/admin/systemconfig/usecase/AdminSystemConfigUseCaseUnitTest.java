package com.thinkfree.drive.api.v1.admin.systemconfig.usecase;

import com.thinkfree.drive.access.exception.AccessErrorCode;
import com.thinkfree.drive.accessrestriction.service.AccessAllowedCountryService;
import com.thinkfree.drive.accessrestriction.service.AccessAllowedIpService;
import com.thinkfree.drive.api.v1.admin.systemconfig.converter.SystemConfigResponseConverter;
import com.thinkfree.drive.api.v1.admin.systemconfig.request.AccessRestrictionUpdateRequest;
import com.thinkfree.drive.api.v1.admin.systemconfig.response.OfficeResponse;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.global.service.ClientDomainService;
import com.thinkfree.drive.storage.enums.StorageType;
import com.thinkfree.drive.storage.result.StorageDetailResult;
import com.thinkfree.drive.storage.service.StorageService;
import com.thinkfree.drive.systemconfig.service.SystemConfigFindService;
import com.thinkfree.drive.systemconfig.service.SystemConfigService;
import com.thinkfree.drive.tenant.data.TenantData;
import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantPolicy;
import com.thinkfree.drive.tenant.service.TenantFindService;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.service.UserFindService;
import com.thinkfree.drive.util.MaskingUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/** 활성화 시 빈 목록 검증 로직이 useCase에 있어 unit 테스트 별도로 작성 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminSystemConfigUseCase 단위 테스트")
class AdminSystemConfigUseCaseUnitTest {

    private static final int TENANT = 1;

    @Mock
    private SystemConfigService systemConfigService;

    @Mock
    private SystemConfigFindService systemConfigFindService;

    @Mock
    private SystemConfigResponseConverter systemConfigResponseConverter;

    @Mock
    private StorageService storageService;

    @Mock
    private AccessAllowedCountryService accessAllowedCountryService;

    @Mock
    private AccessAllowedIpService accessAllowedIpService;

    @Mock
    private UserFindService userFindService;

    @Mock
    private TenantFindService tenantFindService;

    @Mock
    private ClientDomainService clientDomainService;

    @Mock
    private TenantPolicy tenantPolicy;

    @InjectMocks
    private AdminSystemConfigUseCase adminSystemConfigUseCase;

    // UseCase 가 테넌트를 요청 컨텍스트(TenantContext)에서 읽는다 — 필터 대신 직접 넣는다
    @BeforeEach
    void setTenant() {
        TenantContext.set(TENANT);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("putAccessRestrictionStatus() - 접근 제한 활성화/비활성화")
    class PutAccessRestrictionStatus {

        @Test
        @DisplayName("enabled=true + 국가/IP 목록 모두 비어있으면 → EMPTY_ALLOWED_COUNTRIES 예외")
        void enabledTrue_emptyCountriesAndIps_throwsException() {
            given(accessAllowedCountryService.findAllCountryCodes(TENANT)).willReturn(List.of());
            given(accessAllowedIpService.findAllIpCidrs(TENANT, RoleType.USER)).willReturn(List.of());

            assertThatThrownBy(() ->
                    adminSystemConfigUseCase.putAccessRestrictionStatus(
                            new AccessRestrictionUpdateRequest(true), 1))
                    .isInstanceOf(ApiRuntimeException.class)
                    .satisfies(ex -> {
                        ApiRuntimeException apiEx = (ApiRuntimeException) ex;
                        org.assertj.core.api.Assertions.assertThat(apiEx.getCode())
                                .isEqualTo(AccessErrorCode.EMPTY_ALLOWED_COUNTRIES);
                    });

            then(systemConfigService).should(never())
                    .upsertAccessRestrictionStatus(org.mockito.ArgumentMatchers.any(),
                            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("enabled=true + 허용 국가 목록만 있으면 → 정상 저장")
        void enabledTrue_nonEmptyCountries_savesSuccessfully() {
            given(accessAllowedCountryService.findAllCountryCodes(TENANT)).willReturn(List.of("KR", "US"));

            adminSystemConfigUseCase.putAccessRestrictionStatus(
                    new AccessRestrictionUpdateRequest(true), 1);

            then(systemConfigService).should()
                    .upsertAccessRestrictionStatus(org.mockito.ArgumentMatchers.eq(TENANT),
                            org.mockito.ArgumentMatchers.eq(1),
                            org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("enabled=true + 허용 IP 목록만 있으면 → 정상 저장")
        void enabledTrue_nonEmptyIps_savesSuccessfully() {
            given(accessAllowedCountryService.findAllCountryCodes(TENANT)).willReturn(List.of());
            given(accessAllowedIpService.findAllIpCidrs(TENANT, RoleType.USER)).willReturn(List.of("203.0.113.0/24"));

            adminSystemConfigUseCase.putAccessRestrictionStatus(
                    new AccessRestrictionUpdateRequest(true), 1);

            then(systemConfigService).should()
                    .upsertAccessRestrictionStatus(org.mockito.ArgumentMatchers.eq(TENANT),
                            org.mockito.ArgumentMatchers.eq(1),
                            org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("enabled=false + 국가/IP 목록 모두 비어있어도 → 정상 저장 (목록 검증 없음)")
        void enabledFalse_emptyCountriesAndIps_savesSuccessfully() {
            adminSystemConfigUseCase.putAccessRestrictionStatus(
                    new AccessRestrictionUpdateRequest(false), 1);

            then(accessAllowedCountryService).shouldHaveNoInteractions();
            then(accessAllowedIpService).shouldHaveNoInteractions();
            then(systemConfigService).should()
                    .upsertAccessRestrictionStatus(org.mockito.ArgumentMatchers.eq(TENANT),
                            org.mockito.ArgumentMatchers.eq(1),
                            org.mockito.ArgumentMatchers.any());
        }
    }

    @Nested
    @DisplayName("getStorageQuota() - 테넌트 총 용량·할당 합계 조회")
    class GetStorageQuota {

        @Test
        @DisplayName("현재 테넌트의 총 용량과 사용자 할당량 합계를 그대로 돌려준다")
        void returnsTotalAndAllocated() {
            given(systemConfigFindService.findTenantTotalQuotaByte(TENANT)).willReturn(176_184_791_972L);
            given(userFindService.sumTotalQuotaByteByTenantSeq(TENANT)).willReturn(154_709_955_492L);

            var response = adminSystemConfigUseCase.getStorageQuota();

            assertThat(response.totalQuotaByte()).isEqualTo(176_184_791_972L);
            assertThat(response.allocatedQuotaByte()).isEqualTo(154_709_955_492L);
        }

        @Test
        @DisplayName("총 용량 키가 없으면 → totalQuotaByte null (기본값으로 대신하지 않음), 할당 합계는 그대로")
        void missingKey_returnsNullTotal() {
            given(systemConfigFindService.findTenantTotalQuotaByte(TENANT)).willReturn(null);
            given(userFindService.sumTotalQuotaByteByTenantSeq(TENANT)).willReturn(1_073_741_824L);

            var response = adminSystemConfigUseCase.getStorageQuota();

            assertThat(response.totalQuotaByte()).isNull();
            assertThat(response.allocatedQuotaByte()).isEqualTo(1_073_741_824L);
        }

        @Test
        @DisplayName("테넌트 컨텍스트가 없으면 → 예외 (파라미터로 테넌트를 받지 않음)")
        void noTenantContext_throws() {
            TenantContext.clear();

            assertThatThrownBy(() -> adminSystemConfigUseCase.getStorageQuota())
                    .isInstanceOf(IllegalStateException.class);
            then(systemConfigFindService).shouldHaveNoInteractions();
            then(userFindService).shouldHaveNoInteractions();
        }
    }
    @Nested
    @DisplayName("getStorageInfo() - 설치 단위 접속 정보 마스킹")
    class GetStorageInfo {

        private final StorageDetailResult stored = new StorageDetailResult(StorageType.CEPH, "OC8Q****", "********",
                "t-drive-demo", "us-east-1", "http://172.17.163.141:9000", true);

        private StorageDetailResult converted() {
            ArgumentCaptor<StorageDetailResult> captor = ArgumentCaptor.forClass(StorageDetailResult.class);
            then(systemConfigResponseConverter).should().toStorageResponse(captor.capture());
            return captor.getValue();
        }

        @Test
        @DisplayName("조회 허용(SELF_MANAGED) → 저장소 서비스 값 그대로")
        void allowed_asIs() {
            given(storageService.getStorageInfo()).willReturn(stored);
            given(tenantPolicy.allows(TENANT, TenantFeature.STORAGE_INFO_VIEW)).willReturn(true);

            adminSystemConfigUseCase.getStorageInfo();

            assertThat(converted()).isSameAs(stored);
        }

        @Test
        @DisplayName("조회 차단(SYSTEM_MANAGED) → accessKey·secretKey·버킷·리전·엔드포인트를 __MASKED__ 로, 나머지 그대로")
        void blocked_masked() {
            given(storageService.getStorageInfo()).willReturn(stored);
            given(tenantPolicy.allows(TENANT, TenantFeature.STORAGE_INFO_VIEW)).willReturn(false);

            adminSystemConfigUseCase.getStorageInfo();

            StorageDetailResult result = converted();
            assertThat(result.accessKey()).isEqualTo(MaskingUtils.MASKED_PLACEHOLDER);
            assertThat(result.bucketName()).isEqualTo(MaskingUtils.MASKED_PLACEHOLDER);
            assertThat(result.endPoint()).isEqualTo(MaskingUtils.MASKED_PLACEHOLDER);
            assertThat(result.secretKey()).isEqualTo(MaskingUtils.MASKED_PLACEHOLDER);
            assertThat(result.storageType()).isEqualTo(StorageType.CEPH);
            assertThat(result.region()).isEqualTo(MaskingUtils.MASKED_PLACEHOLDER);
            assertThat(result.status()).isTrue();
        }
    }

    @Nested
    @DisplayName("getGeneralConfig() - 도메인 주소 마스킹")
    class GetGeneralConfig {

        private static final String DOMAIN = "https://drive.example.com/t/acme";

        private void tenantWithDomain(String domain) {
            given(tenantFindService.findByTenantSeq(TENANT)).willReturn(TenantData.builder().tenantSeq(TENANT).domain(domain).build());
        }

        @Test
        @DisplayName("조회 허용(SELF_MANAGED) → 도메인 그대로")
        void allowed_asIs() {
            tenantWithDomain("acme.drive.example.com");
            given(clientDomainService.getDomain(TENANT)).willReturn(DOMAIN);
            given(tenantPolicy.allows(TENANT, TenantFeature.SYSTEM_INFO_VIEW)).willReturn(true);

            assertThat(adminSystemConfigUseCase.getGeneralConfig().clientDomain()).isEqualTo(DOMAIN);
        }

        @Test
        @DisplayName("조회 차단(SYSTEM_MANAGED) → 도메인을 __MASKED__ 로")
        void blocked_masked() {
            tenantWithDomain("acme.drive.example.com");
            given(clientDomainService.getDomain(TENANT)).willReturn(DOMAIN);
            given(tenantPolicy.allows(TENANT, TenantFeature.SYSTEM_INFO_VIEW)).willReturn(false);

            assertThat(adminSystemConfigUseCase.getGeneralConfig().clientDomain()).isEqualTo(MaskingUtils.MASKED_PLACEHOLDER);
        }

        @Test
        @DisplayName("조회 차단 + 도메인 없음 → 빈 문자열 그대로")
        void blocked_noDomain_empty() {
            tenantWithDomain(null);
            given(tenantPolicy.allows(TENANT, TenantFeature.SYSTEM_INFO_VIEW)).willReturn(false);

            assertThat(adminSystemConfigUseCase.getGeneralConfig().clientDomain()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getOfficeConfig() - 접속 정보 마스킹")
    class GetOfficeConfig {

        private final OfficeResponse stored = new OfficeResponse("onPremise", "https://office.test", "ticket",
                "private-token", "drive-adapter", "private-key");

        @Test
        @DisplayName("조회 허용 → 변환 결과 그대로")
        void allowed_asIs() {
            given(systemConfigResponseConverter.toOfficeResponse(null)).willReturn(stored);
            given(tenantPolicy.allows(TENANT, TenantFeature.OFFICE_INFO_VIEW)).willReturn(true);

            assertThat(adminSystemConfigUseCase.getOfficeConfig()).isSameAs(stored);
        }

        @Test
        @DisplayName("조회 차단(SYSTEM_MANAGED + SINGLE) → 주소·토큰·어댑터·서명키 __MASKED__, 연동·인증 방식은 그대로")
        void blocked_masked() {
            given(systemConfigResponseConverter.toOfficeResponse(null)).willReturn(stored);
            given(tenantPolicy.allows(TENANT, TenantFeature.OFFICE_INFO_VIEW)).willReturn(false);

            OfficeResponse result = adminSystemConfigUseCase.getOfficeConfig();

            assertThat(result.officeConnectionType()).isEqualTo("onPremise");
            assertThat(result.officeAuthMode()).isEqualTo("ticket");
            assertThat(result.officeDomain()).isEqualTo(MaskingUtils.MASKED_PLACEHOLDER);
            assertThat(result.officeTicketServiceToken()).isEqualTo(MaskingUtils.MASKED_PLACEHOLDER);
            assertThat(result.officeHttpStorageAdapterName()).isEqualTo(MaskingUtils.MASKED_PLACEHOLDER);
            assertThat(result.officeHttpStorageRequestJwtSecret()).isEqualTo(MaskingUtils.MASKED_PLACEHOLDER);
            assertThat(result.serviceManaged()).isFalse();
        }

        @Test
        @DisplayName("서비스 관리 + 조회 차단 → 비운 토큰·서명키는 빈 값 그대로, 나머지 접속 정보는 __MASKED__")
        void serviceManagedAndBlocked() {
            given(systemConfigResponseConverter.toOfficeResponse(null)).willReturn(stored);
            given(systemConfigFindService.isOfficeServiceManaged(TENANT)).willReturn(true);
            given(tenantPolicy.allows(TENANT, TenantFeature.OFFICE_INFO_VIEW)).willReturn(false);

            OfficeResponse result = adminSystemConfigUseCase.getOfficeConfig();

            assertThat(result.officeTicketServiceToken()).isEmpty();
            assertThat(result.officeHttpStorageRequestJwtSecret()).isEmpty();
            assertThat(result.officeDomain()).isEqualTo(MaskingUtils.MASKED_PLACEHOLDER);
            assertThat(result.serviceManaged()).isTrue();
        }
    }
}
