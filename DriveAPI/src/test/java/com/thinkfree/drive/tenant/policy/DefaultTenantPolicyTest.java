package com.thinkfree.drive.tenant.policy;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.tenant.data.TenantData;
import com.thinkfree.drive.tenant.enums.TenantType;
import com.thinkfree.drive.tenant.enums.TenantUserMode;
import com.thinkfree.drive.tenant.exception.TenantErrorCode;
import com.thinkfree.drive.tenant.service.TenantFindService;
import com.thinkfree.drive.user.exception.UserErrorCode;
import com.thinkfree.drive.user.service.UserFindService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 규칙 표 전 칸(유형·모드 3열 × 기능) + 테넌트 없음 + 보호 계정 — 위반은 SYSTEM_MANAGED_RESTRICTED */
@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultTenantPolicy — 유형·모드별 기능 제한·보호 계정 판정")
class DefaultTenantPolicyTest {

    private static final int SELF_MULTI = 1;
    private static final int SYSTEM_MULTI = 2;
    private static final int SYSTEM_SINGLE = 3;

    /** SYSTEM_MANAGED + MULTI 에서도 허용되는 기능 — 나머지는 SYSTEM_MANAGED 면 모드 무관 차단 */
    private static final Set<TenantFeature> ALLOWED_IN_SYSTEM_MULTI =
            EnumSet.of(TenantFeature.USER_REGISTER, TenantFeature.API_KEY_MANAGE, TenantFeature.SUB_ACCOUNT_MANAGE, TenantFeature.API_KEY_INFO_VIEW,
                       TenantFeature.OFFICE_INFO_VIEW);

    @Mock
    private TenantFindService tenantFindService;
    @Mock
    private UserFindService userFindService;

    @InjectMocks
    private DefaultTenantPolicy policy;

    private void tenant(int tenantSeq, TenantType type, TenantUserMode mode) {
        when(tenantFindService.findByTenantSeq(tenantSeq))
                .thenReturn(TenantData.builder().tenantSeq(tenantSeq).tenantType(type).userMode(mode).build());
    }

    @ParameterizedTest
    @EnumSource(TenantFeature.class)
    @DisplayName("SELF_MANAGED — 모든 기능 허용")
    void selfManaged(TenantFeature feature) {
        tenant(SELF_MULTI, TenantType.SELF_MANAGED, TenantUserMode.MULTI);

        assertThat(policy.allows(SELF_MULTI, feature)).isTrue();
        assertThatCode(() -> policy.require(SELF_MULTI, feature)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(TenantFeature.class)
    @DisplayName("SYSTEM_MANAGED + MULTI — 사용자 등록·API Key 관리·보조 계정 관리·키 값·Office 접속 정보 보기만 허용")
    void systemManagedMulti(TenantFeature feature) {
        tenant(SYSTEM_MULTI, TenantType.SYSTEM_MANAGED, TenantUserMode.MULTI);
        boolean expected = ALLOWED_IN_SYSTEM_MULTI.contains(feature);

        assertThat(policy.allows(SYSTEM_MULTI, feature)).isEqualTo(expected);
        if (expected) {
            assertThatCode(() -> policy.require(SYSTEM_MULTI, feature)).doesNotThrowAnyException();
        } else {
            assertRestricted(() -> policy.require(SYSTEM_MULTI, feature));
        }
    }

    @ParameterizedTest
    @EnumSource(TenantFeature.class)
    @DisplayName("SYSTEM_MANAGED + SINGLE — 모든 기능 차단")
    void systemManagedSingle(TenantFeature feature) {
        tenant(SYSTEM_SINGLE, TenantType.SYSTEM_MANAGED, TenantUserMode.SINGLE);

        assertThat(policy.allows(SYSTEM_SINGLE, feature)).isFalse();
        assertRestricted(() -> policy.require(SYSTEM_SINGLE, feature));
    }

    @ParameterizedTest
    @EnumSource(TenantFeature.class)
    @DisplayName("테넌트 없음(null) — 테넌트 조회 없이 허용")
    void nullTenant(TenantFeature feature) {
        assertThat(policy.allows(null, feature)).isTrue();
        assertThatCode(() -> policy.require(null, feature)).doesNotThrowAnyException();
        verify(tenantFindService, never()).findByTenantSeq(anyInt());
    }

    @Test
    @DisplayName("requireManageableUser — 보호 계정은 테넌트 유형과 관계없이 USER_PROTECTED(테넌트 조회 없음)")
    void requireManageableUser_protectedRejectedAnyType() {
        when(userFindService.findTenantSeqIfProtected(10)).thenReturn(SYSTEM_MULTI);
        when(userFindService.findTenantSeqIfProtected(11)).thenReturn(SELF_MULTI);

        assertThat(catchThrowableOfType(ApiRuntimeException.class, () -> policy.requireManageableUser(10)).getCode())
                .isEqualTo(UserErrorCode.PROTECTED_USER);
        assertThat(catchThrowableOfType(ApiRuntimeException.class, () -> policy.requireManageableUser(11)).getCode())
                .isEqualTo(UserErrorCode.PROTECTED_USER);
        verify(tenantFindService, never()).findByTenantSeq(anyInt());
    }

    @Test
    @DisplayName("requireManageableUser — 보호 아님(superadmin 포함)은 통과")
    void requireManageableUser_notProtectedPasses() {
        when(userFindService.findTenantSeqIfProtected(12)).thenReturn(null);

        assertThatCode(() -> policy.requireManageableUser(12)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("requireSelfPasswordChangeable — SYSTEM_MANAGED 보호 계정만 거부")
    void requireSelfPasswordChangeable_systemManagedProtected() {
        tenant(SYSTEM_MULTI, TenantType.SYSTEM_MANAGED, TenantUserMode.MULTI);
        when(userFindService.findTenantSeqIfProtected(10)).thenReturn(SYSTEM_MULTI);

        assertRestricted(() -> policy.requireSelfPasswordChangeable(10));
        assertThat(policy.isSelfPasswordChangeable(10)).isFalse();
    }

    @Test
    @DisplayName("requireSelfPasswordChangeable — SELF_MANAGED 보호 계정(시드 관리자 등)·보호 아님은 통과")
    void requireSelfPasswordChangeable_passes() {
        tenant(SELF_MULTI, TenantType.SELF_MANAGED, TenantUserMode.MULTI);
        when(userFindService.findTenantSeqIfProtected(11)).thenReturn(SELF_MULTI);
        when(userFindService.findTenantSeqIfProtected(12)).thenReturn(null); // 보호 아님·superadmin

        assertThatCode(() -> policy.requireSelfPasswordChangeable(11)).doesNotThrowAnyException();
        assertThatCode(() -> policy.requireSelfPasswordChangeable(12)).doesNotThrowAnyException();
        assertThat(policy.isSelfPasswordChangeable(11)).isTrue();
        assertThat(policy.isSelfPasswordChangeable(12)).isTrue();
    }

    @Test
    @DisplayName("requireUpdatableUser — SYSTEM_MANAGED 보호 계정만 거부(MULTI·SINGLE 공통)")
    void requireUpdatableUser_systemManagedProtected() {
        tenant(SYSTEM_MULTI, TenantType.SYSTEM_MANAGED, TenantUserMode.MULTI);
        tenant(SYSTEM_SINGLE, TenantType.SYSTEM_MANAGED, TenantUserMode.SINGLE);
        when(userFindService.findTenantSeqIfProtected(10)).thenReturn(SYSTEM_MULTI);
        when(userFindService.findTenantSeqIfProtected(13)).thenReturn(SYSTEM_SINGLE);

        assertRestricted(() -> policy.requireUpdatableUser(10));
        assertRestricted(() -> policy.requireUpdatableUser(13));
    }

    @Test
    @DisplayName("requireUpdatableUser — SELF_MANAGED 보호 계정·보호 아닌 계정(MULTI 보조 계정 포함)은 통과")
    void requireUpdatableUser_passes() {
        tenant(SELF_MULTI, TenantType.SELF_MANAGED, TenantUserMode.MULTI);
        when(userFindService.findTenantSeqIfProtected(11)).thenReturn(SELF_MULTI);
        when(userFindService.findTenantSeqIfProtected(12)).thenReturn(null);

        assertThatCode(() -> policy.requireUpdatableUser(11)).doesNotThrowAnyException();
        assertThatCode(() -> policy.requireUpdatableUser(12)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(TenantFeature.class)
    @DisplayName("restrictedWhen(기능) — 문서용 조건이 allows 판정과 같다(유형·모드 3열)")
    void restrictedWhen_feature_matchesAllows(TenantFeature feature) {
        tenant(SELF_MULTI, TenantType.SELF_MANAGED, TenantUserMode.MULTI);
        tenant(SYSTEM_MULTI, TenantType.SYSTEM_MANAGED, TenantUserMode.MULTI);
        tenant(SYSTEM_SINGLE, TenantType.SYSTEM_MANAGED, TenantUserMode.SINGLE);
        var conditions = DefaultTenantPolicy.restrictedWhen(feature);

        assertThat(conditions).isNotEmpty(); // 모든 기능은 어딘가에서 막힌다 — 빈 목록이면 @TenantRestriction 문서가 비게 된다
        assertThat(conditions.stream().noneMatch(c -> c.matches(TenantType.SELF_MANAGED, TenantUserMode.MULTI, false)))
                .isEqualTo(policy.allows(SELF_MULTI, feature));
        assertThat(conditions.stream().noneMatch(c -> c.matches(TenantType.SYSTEM_MANAGED, TenantUserMode.MULTI, false)))
                .isEqualTo(policy.allows(SYSTEM_MULTI, feature));
        assertThat(conditions.stream().noneMatch(c -> c.matches(TenantType.SYSTEM_MANAGED, TenantUserMode.SINGLE, false)))
                .isEqualTo(policy.allows(SYSTEM_SINGLE, feature));
    }

    @Test
    @DisplayName("isMasked — 조회 응답 값을 가리는 기능(*_INFO_VIEW)만 true, 문서는 maskedWhen 으로 나간다")
    void isMasked() {
        assertThat(EnumSet.allOf(TenantFeature.class).stream().filter(DefaultTenantPolicy::isMasked))
                .containsExactlyInAnyOrder(TenantFeature.STORAGE_INFO_VIEW, TenantFeature.SYSTEM_INFO_VIEW, TenantFeature.SMTP_INFO_VIEW,
                                           TenantFeature.API_KEY_INFO_VIEW, TenantFeature.OFFICE_INFO_VIEW);
    }

    @ParameterizedTest
    @EnumSource(TenantType.class)
    @DisplayName("restrictedWhen(보호 계정) — 문서용 조건이 require* 판정과 같다(보호 계정, 유형별)")
    void restrictedWhen_protectedAccount_matchesRequire(TenantType type) {
        lenient().when(tenantFindService.findByTenantSeq(SELF_MULTI))
                .thenReturn(TenantData.builder().tenantSeq(SELF_MULTI).tenantType(type).userMode(TenantUserMode.MULTI).build());
        when(userFindService.findTenantSeqIfProtected(10)).thenReturn(SELF_MULTI);

        assertRuleMatches(ProtectedAccountRule.MANAGE, type, () -> policy.requireManageableUser(10));
        assertRuleMatches(ProtectedAccountRule.UPDATE, type, () -> policy.requireUpdatableUser(10));
        assertRuleMatches(ProtectedAccountRule.SELF_PASSWORD, type, () -> policy.requireSelfPasswordChangeable(10));
    }

    private static void assertRuleMatches(ProtectedAccountRule rule, TenantType type, Runnable call) {
        boolean documented = DefaultTenantPolicy.restrictedWhen(rule).stream().anyMatch(c -> c.matches(type, TenantUserMode.MULTI, true));
        boolean rejected = catchThrowableOfType(ApiRuntimeException.class, call::run) != null;
        assertThat(documented).as("%s / %s", rule, type).isEqualTo(rejected);
    }

    private static void assertRestricted(Runnable call) {
        var e = catchThrowableOfType(ApiRuntimeException.class, call::run);
        assertThat(e.getCode()).isEqualTo(TenantErrorCode.SYSTEM_MANAGED_RESTRICTED);
    }
}
