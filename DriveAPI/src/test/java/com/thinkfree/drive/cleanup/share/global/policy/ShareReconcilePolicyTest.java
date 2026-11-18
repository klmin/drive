package com.thinkfree.drive.cleanup.share.global.policy;

import com.thinkfree.drive.cleanup.share.global.plan.ShareAccessScope;
import com.thinkfree.drive.cleanup.share.global.plan.ShareCleanupPlan;
import com.thinkfree.drive.share.setting.result.EffectiveShareSetting;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공유 설정 변경(old → now) → {@link ShareCleanupPlan} 산출 룰셋 검증.
 * 순수 로직(DB 없음)이라 실제로 실행해 판별을 증명한다.
 *
 * <p>EffectiveShareSetting(shareEnabled, external, specific, orgLink, public)
 */
class ShareReconcilePolicyTest {

    private final ShareReconcilePolicy policy = new ShareReconcilePolicy();

    /** shareEnabled=on, 모든 접근범위/외부 허용. */
    private static EffectiveShareSetting allOn() {
        return new EffectiveShareSetting(true, true, true, true, true);
    }

    @Test
    @DisplayName("공유기능 on→off : 전체 삭제(fullWipe)")
    void shareDisabled_fullWipe() {
        var old = allOn();
        var now = new EffectiveShareSetting(false, true, true, true, true);

        var plan = policy.plan(old, now);

        assertThat(plan.fullWipe()).isTrue();
        assertThat(plan).isEqualTo(ShareCleanupPlan.ofFullWipe());
    }

    @Test
    @DisplayName("공유기능 계속 off(off→off) : 정리 없음(noop)")
    void shareStaysOff_noop() {
        var old = new EffectiveShareSetting(false, true, true, true, true);
        var now = new EffectiveShareSetting(false, false, false, false, false);

        var plan = policy.plan(old, now);

        assertThat(plan.isNoop()).isTrue();
    }

    @Test
    @DisplayName("공유기능 off→on(재활성) + 토글 동일 : noop")
    void shareReEnabled_noop() {
        var old = new EffectiveShareSetting(false, true, true, true, false);
        var now = new EffectiveShareSetting(true, true, true, true, false);

        var plan = policy.plan(old, now);

        assertThat(plan.isNoop()).isTrue();
    }

    @Test
    @DisplayName("제한됨 on→off (전체공개 on) : SPECIFIC 삭제 + 대상 전부 strip, 메타는 보존")
    void specificRevoked_publicOn() {
        var old = allOn();
        var now = new EffectiveShareSetting(true, true, false, true, true);

        var plan = policy.plan(old, now);

        assertThat(plan.fullWipe()).isFalse();
        assertThat(plan.deleteScopes()).containsExactly(ShareAccessScope.SPECIFIC);
        assertThat(plan.stripAllTargets()).isTrue();
        assertThat(plan.stripOtherTenantTargets()).isFalse();
        // 전체공개가 살아있어 모두 접근 유지 → 다른 테넌트 메타도 보존
        assertThat(plan.deleteOtherTenantMeta()).isFalse();
    }

    @Test
    @DisplayName("제한됨 on→off (전체공개 off, 조직내부만 생존) : 대상 전부 strip + 다른 테넌트 메타 정리")
    void specificRevoked_publicOff() {
        // external on, public 이미 off, orgLink on
        var old = new EffectiveShareSetting(true, true, true, true, false);
        var now = new EffectiveShareSetting(true, true, false, true, false);

        var plan = policy.plan(old, now);

        assertThat(plan.deleteScopes()).containsExactly(ShareAccessScope.SPECIFIC);
        assertThat(plan.stripAllTargets()).isTrue();
        assertThat(plan.stripOtherTenantTargets()).isFalse();
        // org 만 생존 → 다른 테넌트는 링크 접근 불가 → 메타 정리
        assertThat(plan.deleteOtherTenantMeta()).isTrue();
    }

    @Test
    @DisplayName("조직내부 on→off : ORG_LINK 만 삭제")
    void orgLinkRevoked() {
        var old = allOn();
        var now = new EffectiveShareSetting(true, true, true, false, true);

        var plan = policy.plan(old, now);

        assertThat(plan.deleteScopes()).containsExactly(ShareAccessScope.ORG_LINK);
        assertThat(plan.stripAllTargets()).isFalse();
        assertThat(plan.stripOtherTenantTargets()).isFalse();
        assertThat(plan.deleteOtherTenantMeta()).isFalse();
    }

    @Test
    @DisplayName("전체공개 on→off (제한됨/외부 변화 없음) : PUBLIC 만 삭제, 다른 테넌트 메타 손 안 댐")
    void publicRevoked() {
        var old = allOn();
        var now = new EffectiveShareSetting(true, true, true, true, false);

        var plan = policy.plan(old, now);

        assertThat(plan.deleteScopes()).containsExactly(ShareAccessScope.PUBLIC);
        assertThat(plan.stripAllTargets()).isFalse();
        assertThat(plan.stripOtherTenantTargets()).isFalse();
        // 제한됨/외부 모두 그대로 → 개별권한으로 접근하던 다른 테넌트는 PUBLIC 삭제와 무관하게 유지
        assertThat(plan.deleteOtherTenantMeta()).isFalse();
    }

    @Test
    @DisplayName("외부공유 on→off : 다른 테넌트 대상 strip + 다른 테넌트 메타 정리, scope 삭제 없음")
    void externalRevoked() {
        // 외부 off → (검증규칙상) 전체공개도 off 인 상태로 전이
        var old = new EffectiveShareSetting(true, true, true, true, false);
        var now = new EffectiveShareSetting(true, false, true, true, false);

        var plan = policy.plan(old, now);

        assertThat(plan.deleteScopes()).isEmpty();
        assertThat(plan.stripAllTargets()).isFalse();
        assertThat(plan.stripOtherTenantTargets()).isTrue();
        assertThat(plan.deleteOtherTenantMeta()).isTrue();
    }

    @Test
    @DisplayName("완화(off→on) : 삭제 없음(noop)")
    void widened_noop() {
        var old = new EffectiveShareSetting(true, false, false, false, false);
        var now = allOn();

        var plan = policy.plan(old, now);

        assertThat(plan.isNoop()).isTrue();
    }

    @Test
    @DisplayName("제한됨 + 외부공유 동시 off : SPECIFIC 삭제 + 전체 대상 strip(상위) + 다른 테넌트 메타")
    void specificAndExternalRevoked() {
        // public 은 이미 off 상태(외부 on→off 전이를 격리하기 위함)
        var old = new EffectiveShareSetting(true, true, true, true, false);
        var now = new EffectiveShareSetting(true, false, false, true, false);

        var plan = policy.plan(old, now);

        assertThat(plan.deleteScopes()).containsExactly(ShareAccessScope.SPECIFIC);
        assertThat(plan.stripAllTargets()).isTrue();
        assertThat(plan.stripOtherTenantTargets()).isTrue();
        assertThat(plan.deleteOtherTenantMeta()).isTrue();
    }

    @Test
    @DisplayName("여러 scope 동시 off : 모든 해당 scope 삭제")
    void multipleScopesRevoked() {
        var old = allOn();
        var now = new EffectiveShareSetting(true, true, true, false, false);

        var plan = policy.plan(old, now);

        assertThat(plan.deleteScopes())
                .containsExactlyInAnyOrder(ShareAccessScope.ORG_LINK, ShareAccessScope.PUBLIC);
        assertThat(plan.stripAllTargets()).isFalse();
        assertThat(plan.stripOtherTenantTargets()).isFalse();
        // 제한됨/외부 변화 없음 → 다른 테넌트 메타 손 안 댐
        assertThat(plan.deleteOtherTenantMeta()).isFalse();
    }

    @Test
    @DisplayName("변화 없음 : noop")
    void noChange_noop() {
        var old = allOn();

        var plan = policy.plan(old, allOn());

        assertThat(plan.isNoop()).isTrue();
    }
}
