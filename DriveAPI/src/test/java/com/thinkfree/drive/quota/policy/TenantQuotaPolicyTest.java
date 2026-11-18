package com.thinkfree.drive.quota.policy;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.quota.exception.QuotaErrorCode;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.exception.UserErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * 테넌트 총 용량 × 사용자 할당량 순수 정책 — 목 없이 값만으로 판정.
 */
@DisplayName("TenantQuotaPolicy — 테넌트 총 용량·사용자 할당량 판정")
class TenantQuotaPolicyTest {

    private static final Set<RoleType> USER_ONLY = Set.of(RoleType.USER);
    private static final Set<RoleType> ADMIN_ONLY = Set.of(RoleType.ADMIN);

    // ───────── 테넌트 총 용량 변경

    @Test
    @DisplayName("같은 값이면 변경 불필요, 키가 없으면(null) 항상 설정 필요")
    void isUnchanged() {
        assertThat(TenantQuotaPolicy.isUnchanged(100L, 100L)).isTrue();
        assertThat(TenantQuotaPolicy.isUnchanged(100L, 101L)).isFalse();
        assertThat(TenantQuotaPolicy.isUnchanged(null, 100L)).isFalse();
    }

    @Test
    @DisplayName("사용자 할당량 합보다 작으면 TENANT_QUOTA_BELOW_ALLOCATED, 같은 값은 허용")
    void requireNotBelowAllocated() {
        var e = catchThrowableOfType(ApiRuntimeException.class, () -> TenantQuotaPolicy.requireNotBelowAllocated(59L, 60L));

        assertThat(e.getCode()).isEqualTo(QuotaErrorCode.TENANT_QUOTA_BELOW_ALLOCATED);
        assertThatCode(() -> TenantQuotaPolicy.requireNotBelowAllocated(60L, 60L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("증가분 = 새 값 − 현재 값(음수면 하향), 키가 없으면 할당 합을 기준으로")
    void increaseOf() {
        assertThat(TenantQuotaPolicy.increaseOf(100L, 150L, 30L)).isEqualTo(50L);
        assertThat(TenantQuotaPolicy.increaseOf(100L, 60L, 30L)).isEqualTo(-40L);
        assertThat(TenantQuotaPolicy.increaseOf(null, 100L, 30L)).isEqualTo(70L);
    }

    // ───────── 사용자 할당량

    @Test
    @DisplayName("사용자(USER) 할당량은 0 보다 커야 한다 — 0·음수(-1 무제한 포함)는 INVALID_USER_QUOTA")
    void validateUserQuota() {
        var negative = catchThrowableOfType(ApiRuntimeException.class, () -> TenantQuotaPolicy.validateUserQuota(-1L));
        assertThat(negative.getCode()).isEqualTo(QuotaErrorCode.INVALID_USER_QUOTA);

        var zero = catchThrowableOfType(ApiRuntimeException.class, () -> TenantQuotaPolicy.validateUserQuota(0L));
        assertThat(zero.getCode()).isEqualTo(QuotaErrorCode.INVALID_USER_QUOTA);

        assertThatCode(() -> TenantQuotaPolicy.validateUserQuota(1L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("관리자만 보유 할당량 — 생략(null)·0 은 0 으로 확정, 그 외(양수·음수)는 ADMIN_QUOTA_NOT_ALLOWED")
    void resolveUserQuota_admin() {
        assertThat(TenantQuotaPolicy.resolveUserQuota(ADMIN_ONLY, null)).isZero();
        assertThat(TenantQuotaPolicy.resolveUserQuota(ADMIN_ONLY, 0L)).isZero();

        var positive = catchThrowableOfType(ApiRuntimeException.class, () -> TenantQuotaPolicy.resolveUserQuota(ADMIN_ONLY, 1L));
        assertThat(positive.getCode()).isEqualTo(QuotaErrorCode.ADMIN_QUOTA_NOT_ALLOWED);

        var negative = catchThrowableOfType(ApiRuntimeException.class, () -> TenantQuotaPolicy.resolveUserQuota(ADMIN_ONLY, -1L));
        assertThat(negative.getCode()).isEqualTo(QuotaErrorCode.ADMIN_QUOTA_NOT_ALLOWED);
    }

    @Test
    @DisplayName("사용자 할당량 — 필수(null 이면 REQUIRED_FIELD), 양수 그대로, 0·음수는 INVALID_USER_QUOTA")
    void resolveUserQuota_user() {
        assertThat(TenantQuotaPolicy.resolveUserQuota(USER_ONLY, 1024L)).isEqualTo(1024L);

        var zero = catchThrowableOfType(ApiRuntimeException.class, () -> TenantQuotaPolicy.resolveUserQuota(USER_ONLY, 0L));
        assertThat(zero.getCode()).isEqualTo(QuotaErrorCode.INVALID_USER_QUOTA);

        var missing = catchThrowableOfType(ApiRuntimeException.class, () -> TenantQuotaPolicy.resolveUserQuota(USER_ONLY, null));
        assertThat(missing.getCode()).isEqualTo(UserErrorCode.REQUIRED_FIELD);

        var negative = catchThrowableOfType(ApiRuntimeException.class, () -> TenantQuotaPolicy.resolveUserQuota(USER_ONLY, -1L));
        assertThat(negative.getCode()).isEqualTo(QuotaErrorCode.INVALID_USER_QUOTA);
    }

    @Test
    @DisplayName("USER·ADMIN 동시 보유 — USER 규칙(필수·0 보다 큼), 관리자 0 규칙은 적용 안 함")
    void resolveUserQuota_userAndAdmin() {
        assertThat(TenantQuotaPolicy.resolveUserQuota(Set.of(RoleType.USER, RoleType.ADMIN), 1024L)).isEqualTo(1024L);

        var zero = catchThrowableOfType(ApiRuntimeException.class, () -> TenantQuotaPolicy.resolveUserQuota(Set.of(RoleType.USER, RoleType.ADMIN), 0L));
        assertThat(zero.getCode()).isEqualTo(QuotaErrorCode.INVALID_USER_QUOTA);

        var missing = catchThrowableOfType(ApiRuntimeException.class, () -> TenantQuotaPolicy.resolveUserQuota(Set.of(RoleType.USER, RoleType.ADMIN), null));
        assertThat(missing.getCode()).isEqualTo(UserErrorCode.REQUIRED_FIELD);
    }

    @Test
    @DisplayName("배정 가능 — 할당 합 + 증감분 ≤ 총 용량(같으면 가능), 증감분 0 이하는 항상 가능")
    void isAssignable() {
        assertThat(TenantQuotaPolicy.isAssignable(100L, 60L, 40L)).isTrue();
        assertThat(TenantQuotaPolicy.isAssignable(100L, 60L, 41L)).isFalse();
        assertThat(TenantQuotaPolicy.isAssignable(100L, 200L, 0L)).isTrue();
        assertThat(TenantQuotaPolicy.isAssignable(100L, 200L, -10L)).isTrue();
    }

    @Test
    @DisplayName("배정 가능 — 큰 증감분(Long.MAX_VALUE)은 합 오버플로로 통과하지 않고 초과")
    void isAssignable_overflow() {
        assertThat(TenantQuotaPolicy.isAssignable(100L, 60L, Long.MAX_VALUE)).isFalse();
        assertThat(TenantQuotaPolicy.isAssignable(100L, 200L, Long.MAX_VALUE)).isFalse();
    }

    @Test
    @DisplayName("할당량 합산 — 범위 안이면 합(음수 증감 포함)")
    void addQuota() {
        assertThat(TenantQuotaPolicy.addQuota(60L, 40L)).isEqualTo(100L);
        assertThat(TenantQuotaPolicy.addQuota(60L, -100L)).isEqualTo(-40L);
        assertThat(TenantQuotaPolicy.addQuota(0L, Long.MAX_VALUE)).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("할당량 합산 — long 범위를 넘으면 TENANT_QUOTA_EXCEEDED")
    void addQuota_overflow() {
        var up = catchThrowableOfType(ApiRuntimeException.class, () -> TenantQuotaPolicy.addQuota(1L, Long.MAX_VALUE));
        var down = catchThrowableOfType(ApiRuntimeException.class, () -> TenantQuotaPolicy.addQuota(-1L, Long.MIN_VALUE));

        assertThat(up.getCode()).isEqualTo(QuotaErrorCode.TENANT_QUOTA_EXCEEDED);
        assertThat(down.getCode()).isEqualTo(QuotaErrorCode.TENANT_QUOTA_EXCEEDED);
    }
}
