package com.thinkfree.drive.api.v1.superadmin.tenant.request;

import com.thinkfree.drive.tenant.enums.TenantType;
import com.thinkfree.drive.tenant.enums.TenantUserMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SuperAdminTenantCreateRequest(

        @NotBlank
        String tn,

        @NotBlank
        @Size(max = 253)
        String sd,

        @NotNull
        Integer bs,

        @NotBlank
        String e,

        @NotBlank
        String n,

        @NotBlank
        String p,

        /** 테넌트 총 용량(byte) — 양수만(무제한 없음) */
        @NotNull
        @Positive
        Long tq,

        /** ADMIN 허용 IP — SYSTEM_MANAGED 는 무시 */
        List<String> ips,

        /** 테넌트 유형 — 생략 시 SELF_MANAGED, 생성 후 변경 불가 */
        TenantType tt,

        /** 사용자 모드 — 생략 시 MULTI, SELF_MANAGED 는 MULTI 만, 생성 후 변경 불가 */
        TenantUserMode um
) {
    public TenantType tenantTypeOrDefault() {
        return tt == null ? TenantType.SELF_MANAGED : tt;
    }

    public TenantUserMode userModeOrDefault() {
        return um == null ? TenantUserMode.MULTI : um;
    }
}
