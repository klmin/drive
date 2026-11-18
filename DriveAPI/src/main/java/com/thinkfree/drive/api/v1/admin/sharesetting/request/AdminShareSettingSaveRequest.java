package com.thinkfree.drive.api.v1.admin.sharesetting.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * 테넌트 어드민 공유 설정 등록/수정 요청.
 * <p>
 * 슈퍼어드민 요청과 동일 구조에서 적용범위(e)만 제외한다(enforce 는 슈퍼어드민 전용).
 * 화면 중첩 구조에 맞춰 단계별로만 필수다:
 * <ul>
 *   <li>{@code se} : 항상 필수 (공유 기능 on/off)</li>
 *   <li>권한 4개({@code esa/sua/ola/pa}) : 공유 on(se=true)일 때만 필수</li>
 * </ul>
 * 공유 off(se=false)면 나머지는 화면에 없으므로 보내지 않아도 된다.
 *
 * @param se  공유 기능 사용 여부 (테넌트 share_status, system_config 에 저장)
 * @param esa 외부 공유 허용
 * @param sua 접근범위 - 제한됨(직접 추가한 사용자만)
 * @param ola 접근범위 - 조직 내부 공개
 * @param pa  접근범위 - 전체 공개
 */
public record AdminShareSettingSaveRequest(
        @Schema(title = "Enable sharing", description = "Enable or disable sharing for the tenant. When true, set all four permission options and allow at least one access scope.")
        @NotNull
        Boolean se,
        @Schema(title = "Allow external sharing", description = "Allow sharing with people outside the tenant. Public sharing also requires this setting.")
        Boolean esa,
        @Schema(title = "Allow selected-user sharing", description = "Allow shares addressed to specific users.")
        Boolean sua,
        @Schema(title = "Allow organization links", description = "Allow links restricted to members of the tenant.")
        Boolean ola,
        @Schema(title = "Allow public links", description = "Allow publicly accessible sharing links. External sharing must also be enabled.")
        Boolean pa
) {

    /** 공유 on 이면 접근범위 4개는 모두 필수. */
    @AssertTrue(message = "공유 사용 시 접근범위 4개는 필수입니다")
    private boolean isPermissionsPresentWhenEnabled() {
        if (!Boolean.TRUE.equals(se)) {
            return true;
        }
        return esa != null && sua != null && ola != null && pa != null;
    }

    /**
     * 공유 on 이면 접근범위(제한됨/조직내부/전체공개) 중 최소 하나는 허용이어야 한다.
     * (외부공유 esa 는 접근범위가 아닌 별개 축이므로 제외)
     */
    @AssertTrue(message = "공유 사용 시 접근범위(제한됨/조직내부/전체공개)를 하나 이상 허용해야 합니다")
    private boolean isAtLeastOneScopeAllowedWhenEnabled() {
        if (!Boolean.TRUE.equals(se)) {
            return true;
        }
        return Boolean.TRUE.equals(sua) || Boolean.TRUE.equals(ola) || Boolean.TRUE.equals(pa);
    }

    /** 전체 공개는 외부 공유 허용이 켜져 있어야 한다. (권한이 실제 적용되는 공유 on 일 때만 검증) */
    @AssertTrue(message = "전체 공개는 외부 공유 허용이 켜져 있어야 합니다")
    private boolean isPublicRequiresExternal() {
        if (!Boolean.TRUE.equals(se)) {
            return true;
        }
        if (!Boolean.TRUE.equals(pa)) {
            return true;
        }
        return Boolean.TRUE.equals(esa);
    }
}
