package com.thinkfree.drive.share.setting.facade;

import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantPolicy;
import com.thinkfree.drive.share.setting.data.ShareSettingData;
import com.thinkfree.drive.share.setting.result.EffectiveShareSetting;
import com.thinkfree.drive.share.setting.result.ResolvedShareSetting;
import com.thinkfree.drive.share.setting.service.ShareSettingFindService;
import com.thinkfree.drive.systemconfig.service.SystemConfigFindService;
import com.thinkfree.drive.user.service.UserFindService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공유 설정 퍼사드.
 * - 슈퍼어드민(글로벌) 행과 테넌트별 행을 해소(resolve)해 "실제 적용 설정"을 제공한다.
 *   1) 글로벌 행이 is_enforced=Y 면 글로벌 설정(+글로벌 share_status)을 강제 적용
 *   2) 아니면 테넌트 개별 행(+테넌트 share_status)
 *   3) 테넌트 행이 없으면 글로벌 행, 그것도 없으면 테이블 기본값
 * - 공유 검색 / 링크 접근 / 공유 등록 가능 여부 등 여러 비즈니스의 단일 진입점.
 *
 * 참고: 공유 유형(ShareType) 결합 판단은 enum 제거/로직 전환 이후 단계에서 추가한다.
 *      현재는 boolean 토글 기반 조건만 노출한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShareSettingFacade {

    private final ShareSettingFindService shareSettingFindService;
    private final SystemConfigFindService systemConfigFindService;
    private final UserFindService userFindService;
    private final TenantPolicy tenantPolicy;

    /** 해당 테넌트에 실제로 적용되는 공유 설정. */
    public EffectiveShareSetting resolve(int tenantSeq) {
        return resolveWithEnforced(tenantSeq).effective();
    }

    /**
     * 유저(소유주) 기준으로 테넌트를 해소하고 실효 공유 설정을 함께 반환한다.
     * (findTenantSeqByUserSeq → resolve 2-step 을 캡슐화 — 호출부가 UserFindService 에 직접 의존하지 않도록)
     *
     * @return (소유주 테넌트 seq, 실효 공유 설정). external/scope 판정에 tenantSeq 가 필요한 호출부를 위해 둘 다 반환.
     */
    public ResolvedByUser resolveByUserSeq(int userSeq) {
        int tenantSeq = userFindService.findTenantSeqByUserSeq(userSeq);
        return new ResolvedByUser(tenantSeq, resolve(tenantSeq));
    }

    /** {@link #resolveByUserSeq(int)} 결과. */
    public record ResolvedByUser(int tenantSeq, EffectiveShareSetting effective) {}

    /**
     * 해당 테넌트에 실제로 적용되는 공유 설정 + 글로벌 강제(enforce) 여부를 한 번에 산출한다.
     * (findGlobal 1회로 effective 와 enforced 를 함께 반환 — 어드민 조회처럼 둘 다 필요할 때 사용)
     */
    public ResolvedShareSetting resolveWithEnforced(int tenantSeq) {

        // SYSTEM_MANAGED 는 공유 off 고정 — 슈퍼어드민 전체 적용(enforce)도 무시한다
        if (!tenantPolicy.allows(tenantSeq, TenantFeature.SHARE)) {
            return new ResolvedShareSetting(false, EffectiveShareSetting.off());
        }

        ShareSettingData global = shareSettingFindService.findGlobal();
        boolean enforced = global != null && global.enforced();

        boolean shareEnabled = enforced
                ? systemConfigFindService.findGlobalShareStatusConfig().isEnabled()  // 전체적용 → 슈퍼어드민 shareStatus
                : systemConfigFindService.findShareStatusConfig(tenantSeq).isEnabled(); // 개별적용 → 테넌트 shareStatus

        ShareSettingData tenantData = enforced ? global : shareSettingFindService.findByTenant(tenantSeq);
        EffectiveShareSetting effective = EffectiveShareSetting.from(tenantData, shareEnabled);

        return new ResolvedShareSetting(enforced, effective);
    }

    /** 글로벌 강제 적용 여부. */
    public boolean isEnforced() {
        ShareSettingData global = shareSettingFindService.findGlobal();
        return global != null && global.enforced();
    }

    /** 외부(타 테넌트) 공유 허용 여부. */
    public boolean isExternalShareAllowed(int tenantSeq) {
        return resolve(tenantSeq).externalShareAllowed();
    }
}
