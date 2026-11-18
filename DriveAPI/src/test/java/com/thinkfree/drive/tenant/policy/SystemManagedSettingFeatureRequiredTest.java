package com.thinkfree.drive.tenant.policy;

import com.thinkfree.drive.api.v1.admin.accessrestriction.request.AllowedCountriesUpdateRequest;
import com.thinkfree.drive.api.v1.admin.accessrestriction.request.AllowedIpsUpdateRequest;
import com.thinkfree.drive.api.v1.admin.accessrestriction.usecase.AccessAllowedCountryUseCase;
import com.thinkfree.drive.api.v1.admin.accessrestriction.usecase.AccessAllowedIpUseCase;
import com.thinkfree.drive.api.v1.admin.systemconfig.request.AccessRestrictionUpdateRequest;
import com.thinkfree.drive.api.v1.admin.systemconfig.request.IntellectUpdateRequest;
import com.thinkfree.drive.api.v1.admin.systemconfig.request.MfaStatusUpdateRequest;
import com.thinkfree.drive.api.v1.admin.systemconfig.usecase.AdminSystemConfigUseCase;
import com.thinkfree.drive.application.admin.command.AdminRegisterUserCommand;
import com.thinkfree.drive.application.admin.user.AdminUserUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관리자 설정 저장 중 SYSTEM_MANAGED 에서 막는 것 — 접근 제한 3곳(허용 IP·허용 국가·사용 여부)은 {@link TenantFeature#ACCESS_RESTRICTION},
 * MFA 사용 여부는 {@link TenantFeature#MFA_SETTING}, 노트 설정은 {@link TenantFeature#SYSTEM_CONFIG}, 관리자 사용자 등록은 {@link TenantFeature#USER_REGISTER} 판정을 탄다(SYSTEM_MANAGED 면 TENANT_011)
 */
@DisplayName("SYSTEM_MANAGED 차단 설정 저장 — @TenantFeatureRequired")
class SystemManagedSettingFeatureRequiredTest {

    static Stream<Arguments> saveMethods() throws NoSuchMethodException {
        return Stream.of(
                Arguments.of(AccessAllowedIpUseCase.class.getMethod("putAllowedIps", AllowedIpsUpdateRequest.class, Integer.class),
                        TenantFeature.ACCESS_RESTRICTION),
                Arguments.of(AccessAllowedCountryUseCase.class.getMethod("putAllowedCountries", AllowedCountriesUpdateRequest.class, int.class),
                        TenantFeature.ACCESS_RESTRICTION),
                Arguments.of(AdminSystemConfigUseCase.class.getMethod("putAccessRestrictionStatus", AccessRestrictionUpdateRequest.class, int.class),
                        TenantFeature.ACCESS_RESTRICTION),
                Arguments.of(AdminSystemConfigUseCase.class.getMethod("putMfaStatus", MfaStatusUpdateRequest.class, int.class),
                        TenantFeature.MFA_SETTING),
                Arguments.of(AdminSystemConfigUseCase.class.getMethod("putIntellect", IntellectUpdateRequest.class, int.class),
                        TenantFeature.SYSTEM_CONFIG),
                Arguments.of(AdminUserUseCase.class.getMethod("registerUser", AdminRegisterUserCommand.class, int.class),
                        TenantFeature.USER_REGISTER));
    }

    @ParameterizedTest
    @MethodSource("saveMethods")
    @DisplayName("저장 메서드에 해당 기능 판정")
    void annotated(Method method, TenantFeature feature) {
        TenantFeatureRequired required = method.getAnnotation(TenantFeatureRequired.class);

        assertThat(required).as(method.getName()).isNotNull();
        assertThat(required.value()).isEqualTo(feature);
    }
}
