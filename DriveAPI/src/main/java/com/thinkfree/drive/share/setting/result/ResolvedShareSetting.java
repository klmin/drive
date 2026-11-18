package com.thinkfree.drive.share.setting.result;

/**
 * 공유 설정 해소(resolve) 결과 + 출처 메타데이터 묶음.
 * <p>
 * {@link EffectiveShareSetting} 은 "실제 적용 값"만 담는다. 그 값이 글로벌 강제(enforce)에서 왔는지 여부는
 * 값이 아니라 출처 정보이므로 여기서 함께 반환한다(어드민 폼 잠금 판단 등에 사용).
 *
 * @param enforced  슈퍼어드민(글로벌)이 전체 적용 중인지
 * @param effective 해당 테넌트에 실제 적용되는 공유 설정
 */
public record ResolvedShareSetting(
        boolean enforced,
        EffectiveShareSetting effective
) {
}
