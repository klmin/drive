package com.thinkfree.drive.cleanup.share.global.policy;

import com.thinkfree.drive.cleanup.share.global.plan.ShareAccessScope;
import com.thinkfree.drive.cleanup.share.global.plan.ShareCleanupPlan;
import com.thinkfree.drive.share.setting.result.EffectiveShareSetting;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * 공유 설정 변경(old → new effective)을 비교해 {@link ShareCleanupPlan}을 만드는 <b>순수 정책</b>.
 * DB 접근·삭제 호출 없음(단위테스트 대상). 실제 삭제는 cleanup 실행기가 plan 을 받아 수행한다.
 * <p>
 * 단일 scope 모델(확정) 기준 — 룰셋은 5개 연산의 조합으로 귀결한다. 각 축은 "허용→불허(revoke)" 일 때만 동작:
 * <pre>
 * 트리거(revoke)      | plan
 * 공유기능 on→off      | fullWipe
 * 제한됨 on→off        | deleteScopes += SPECIFIC, stripAllTargets=true (+ 전체공개도 off면 다른테넌트 메타)
 * 조직내부 on→off      | deleteScopes += ORG_LINK
 * 전체공개 on→off      | deleteScopes += PUBLIC
 * 외부공유 on→off      | stripOtherTenantTargets=true, deleteOtherTenantMeta=true
 * 확장(off→on 완화)    | (해당 축 동작 없음)
 * </pre>
 * "제한됨만 on / 조직내부만 선언" 등 조합 룰은 위 단일 축들의 union 으로 자연히 도출된다.
 * <p>
 * 메타(최근/중요/핀) 정리 원칙: 같은 테넌트 메타는 벌크로 지우지 않는다(살아있는 링크로 접근 유지 가능 — live-gating).
 * 다른 테넌트는 org 링크로 접근 불가하므로, 외부공유 OFF 거나 제한됨 OFF + 전체공개도 OFF 면 접근경로가 0 → 메타 정리.
 * (검증규칙상 전체공개 ON 은 외부공유 ON 을 함의하므로, 외부 OFF 면 전체공개도 OFF 가 보장된다.)
 * <p>
 * ⚠️ 물리적 실행(deleteScopes 등)은 share 의 scope 컬럼(3값) 확정 후 가능 — 실행기 Phase 2.
 * 단, 이 plan 산출 자체는 설정값만 보므로 share 모델과 무관하게 지금 완전하다.
 */
@Component
public class ShareReconcilePolicy {

    public ShareCleanupPlan plan(EffectiveShareSetting old, EffectiveShareSetting now) {
        // 공유 기능 자체 ON → OFF = 테넌트 전체 정리
        if (old.shareEnabled() && !now.shareEnabled()) {
            return ShareCleanupPlan.ofFullWipe();
        }

        // 계속 OFF(또는 이미 OFF)면 추가 정리 없음
        if (!now.shareEnabled()) {
            return ShareCleanupPlan.ofNoop();
        }

        // 공유 기능 ON 유지: 허용→불허로 좁아진 축만 정리
        boolean specificRevoked = revoked(old.specificUsersAllowed(), now.specificUsersAllowed());
        boolean orgLinkRevoked = revoked(old.orgLinkAllowed(), now.orgLinkAllowed());
        boolean publicRevoked = revoked(old.publicAllowed(), now.publicAllowed());
        boolean externalRevoked = revoked(old.externalShareAllowed(), now.externalShareAllowed());

        Set<ShareAccessScope> deleteScopes = EnumSet.noneOf(ShareAccessScope.class);
        if (specificRevoked) {
            deleteScopes.add(ShareAccessScope.SPECIFIC);
        }
        if (orgLinkRevoked) {
            deleteScopes.add(ShareAccessScope.ORG_LINK);
        }
        if (publicRevoked) {
            deleteScopes.add(ShareAccessScope.PUBLIC);
        }
       
        // 다른 테넌트 메타: 외부공유 OFF(다른 테넌트 접근경로 0) 또는 제한됨 OFF + 전체공개도 OFF
        // (org 만 살아남으면 다른 테넌트는 링크 접근 불가 → 개별권한 strip 시 접근 완전 상실)
        boolean deleteOtherTenantMeta = externalRevoked || (specificRevoked && !now.publicAllowed());

        return new ShareCleanupPlan(false, deleteScopes, specificRevoked, externalRevoked, deleteOtherTenantMeta);
    }

    /** 허용(was)에서 불허(now)로 좁아졌는지. */
    private static boolean revoked(boolean was, boolean now) {
        return was && !now;
    }
}
