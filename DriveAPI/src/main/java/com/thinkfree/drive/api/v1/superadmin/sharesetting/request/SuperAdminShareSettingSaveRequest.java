package com.thinkfree.drive.api.v1.superadmin.sharesetting.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * 슈퍼어드민 공유 설정 등록/수정 요청.
 * <p>
 * 화면 중첩 구조에 맞춰 단계별로만 필수다:
 * <ul>
 *   <li>{@code e} : 항상 필수 (적용범위)</li>
 *   <li>{@code se} : 전체적용(e=true)일 때만 필수 (공유 on/off)</li>
 *   <li>권한 4개({@code esa/sua/ola/pa}) : 전체적용+공유 on(e=true && se=true)일 때만 필수</li>
 * </ul>
 * 개별적용(e=false)이면 나머지는 화면에 없으므로 보내지 않아도 된다.
 *
 * @param e   적용범위: true=전체적용(글로벌 강제) / false=테넌트 개별적용
 * @param se  공유 기능 사용 여부 (글로벌 share_status, system_config 에 저장)
 * @param esa 외부 공유 허용
 * @param sua 접근범위 - 제한됨(직접 추가한 사용자만)
 * @param ola 접근범위 - 조직 내부 공개
 * @param pa  접근범위 - 전체 공개
 */
public record SuperAdminShareSettingSaveRequest(
        @NotNull
        Boolean e,
        Boolean se,
        Boolean esa,
        Boolean sua,
        Boolean ola,
        Boolean pa
) {

    /** 전체적용이면 공유 사용 여부(se)는 필수. */
    @AssertTrue(message = "전체적용 시 공유 사용 여부는 필수입니다")
    private boolean isShareEnabledPresentWhenEnforced() {
        if (!Boolean.TRUE.equals(e)) {
            return true;
        }
        return se != null;
    }

    /** 전체적용 + 공유 on 이면 접근범위 4개는 모두 필수. */
    @AssertTrue(message = "전체적용 + 공유 사용 시 접근범위 4개는 필수입니다")
    private boolean isPermissionsPresentWhenEnabled() {
        if (!(Boolean.TRUE.equals(e) && Boolean.TRUE.equals(se))) {
            return true;
        }
        return esa != null && sua != null && ola != null && pa != null;
    }

    /**
     * 전체적용 + 공유 on 이면 접근범위(제한됨/조직내부/전체공개) 중 최소 하나는 허용이어야 한다.
     * 셋 다 false 면 공유 기능은 켜졌지만 실제로 공유할 수 있는 범위가 없는 모순 상태가 된다.
     * (외부공유 esa 는 접근범위가 아닌 별개 축이므로 여기서 제외)
     */
    @AssertTrue(message = "공유 사용 시 접근범위(제한됨/조직내부/전체공개)를 하나 이상 허용해야 합니다")
    private boolean isAtLeastOneScopeAllowedWhenEnabled() {
        if (!(Boolean.TRUE.equals(e) && Boolean.TRUE.equals(se))) {
            return true;
        }
        return Boolean.TRUE.equals(sua) || Boolean.TRUE.equals(ola) || Boolean.TRUE.equals(pa);
    }

    /**
     * 전체 공개는 외부 공유 허용이 켜져 있어야 한다.
     * 권한이 실제로 적용되는 전체적용+공유 on 일 때만 검증한다(다른 권한 규칙과 동일 가드).
     */
    @AssertTrue(message = "전체 공개는 외부 공유 허용이 켜져 있어야 합니다")
    private boolean isPublicRequiresExternal() {
        if (!(Boolean.TRUE.equals(e) && Boolean.TRUE.equals(se))) {
            return true;
        }
        if (!Boolean.TRUE.equals(pa)) {
            return true;
        }
        return Boolean.TRUE.equals(esa);
    }
}
