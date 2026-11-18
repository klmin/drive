package com.thinkfree.drive.cleanup.share.global.service;

import com.thinkfree.drive.cleanup.share.global.plan.ShareCleanupPlan;
import com.thinkfree.drive.cleanup.share.global.policy.ShareReconcilePolicy;
import com.thinkfree.drive.share.service.ShareFindService;
import com.thinkfree.drive.share.setting.result.EffectiveShareSetting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 공유 설정 변경에 따른 데이터 재조정(reconcile) 오케스트레이터.
 * <p>
 * 흐름: old/new effective 설정을 받아 → 순수 {@link ShareReconcilePolicy}로 plan 산출 →
 * {@link GlobalShareCleanUpService}(실행기)에 위임. 삭제 service 호출은 실행기에서만 한다.
 * <p>
 * 두 진입점을 effective 설정 비교 하나로 통일한다:
 * <ul>
 *   <li>테넌트 개별 변경 → {@link #reconcile}</li>
 *   <li>슈퍼어드민 전체적용(enforce) → {@link #reconcileAllTenants} (글로벌이 모든 테넌트의 effective)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class GlobalShareReconcileService {

    private final ShareReconcilePolicy shareReconcilePolicy;
    private final GlobalShareCleanUpService globalShareCleanUpService;
    private final ShareFindService shareFindService;

    /** 단일 테넌트 reconcile. */
    public void reconcile(int tenantSeq, EffectiveShareSetting old, EffectiveShareSetting now) {
        ShareCleanupPlan plan = shareReconcilePolicy.plan(old, now);
        
        if (plan.isNoop()) {
            return;
        }
        // 공유 데이터가 없으면 정리할 것도 없음
        if (!shareFindService.existsByTenant(tenantSeq)) {
            return;
        }

        log.info("[ShareReconcile] apply 실행. tenantSeq={}, plan={}", tenantSeq, plan);
        globalShareCleanUpService.apply(tenantSeq, plan);
    }

    /**
     * 전체적용(enforce) 시 전 조직 테넌트 reconcile.
     * <p>
     * enforce 후 모든 테넌트의 new effective 는 글로벌({@code newGlobal})로 통일되지만,
     * <b>old effective 는 테넌트마다 다르다</b>:
     * <ul>
     *   <li>변경 전 글로벌이 이미 enforce 였으면 모든 테넌트의 old = oldGlobal</li>
     *   <li>변경 전이 개별적용이었으면 각 테넌트의 old = 그 테넌트 자체 설정</li>
     * </ul>
     * 그래서 호출부가 <b>저장 직전</b>에 테넌트별 effective 를 캡처해 {@code oldEffectiveByTenant} 로 넘긴다
     * (저장 후엔 글로벌 enforce 로 덮여 과거 테넌트별 effective 를 복원할 수 없다).
     */
    public void reconcileAllTenants(Map<Integer, EffectiveShareSetting> oldEffectiveByTenant, EffectiveShareSetting newGlobal) {
        if (oldEffectiveByTenant.isEmpty()) {
            return;
        }
        log.info("[ShareReconcile] 전체적용 reconcile 시작. tenantCount : {}", oldEffectiveByTenant.size());

        oldEffectiveByTenant.forEach((tenantSeq, oldEffective) -> reconcile(tenantSeq, oldEffective, newGlobal));

        log.info("[ShareReconcile] 전체적용 reconcile 완료.");
    }
}
