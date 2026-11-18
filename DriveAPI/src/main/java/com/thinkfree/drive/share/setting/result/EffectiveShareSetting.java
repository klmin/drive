package com.thinkfree.drive.share.setting.result;

import com.thinkfree.drive.share.setting.data.ShareSettingData;

/**
 * 특정 테넌트에 "실제로 적용되는" 공유 설정 (글로벌 enforce / 테넌트 개별 해소 결과).
 * 설정은 서로 독립적인 boolean 토글의 묶음으로 표현한다 (ShareType enum 비결합).
 * <p>
 * shareEnabled(공유 기능 사용 여부)는 system_config 의 share_status 에서 온다.
 * - enforce 시: 슈퍼어드민(글로벌) share_status
 * - 개별 적용 시: 테넌트 share_status
 *
 * @param shareEnabled         공유 기능 사용 여부 (system_config.share_status)
 * @param externalShareAllowed 외부(타 테넌트) 공유 허용
 * @param specificUsersAllowed 접근범위: 제한됨(직접 추가한 사용자만)
 * @param orgLinkAllowed       접근범위: 조직 내부 공개
 * @param publicAllowed        접근범위: 전체 공개
 */
public record EffectiveShareSetting(
        boolean shareEnabled,
        boolean externalShareAllowed,
        boolean specificUsersAllowed,
        boolean orgLinkAllowed,
        boolean publicAllowed
) {

    public static EffectiveShareSetting from(ShareSettingData data, boolean shareEnabled) {
        return new EffectiveShareSetting(
                shareEnabled,
                data.externalShareAllowed(),
                data.specificUsersAllowed(),
                data.orgLinkAllowed(),
                data.publicAllowed()
        );
    }

    /** 공유 기능 off — 모든 토글 false(SYSTEM_MANAGED 테넌트) */
    public static EffectiveShareSetting off() {
        return new EffectiveShareSetting(false, false, false, false, false);
    }

    /** 접근범위(scope) 중 하나라도 허용되는지. */
    public boolean anyAccessScopeAllowed() {
        return specificUsersAllowed || orgLinkAllowed || publicAllowed;
    }

    /** 공유 기능이 켜져 있고 허용된 접근범위가 하나라도 있어 공유 등록이 가능한지. */
    public boolean isShareCreatable() {
        return shareEnabled && anyAccessScopeAllowed();
    }
}
