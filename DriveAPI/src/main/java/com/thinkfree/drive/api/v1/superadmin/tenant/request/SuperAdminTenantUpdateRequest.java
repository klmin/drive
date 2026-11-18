package com.thinkfree.drive.api.v1.superadmin.tenant.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SuperAdminTenantUpdateRequest(

        @NotBlank
        String tn,

        @NotBlank
        @Size(max = 253)
        String sd,

        @NotNull
        Integer bs,

        /** 테넌트 총 용량(byte) — 양수만(무제한 없음) */
        @NotNull
        @Positive
        Long tq,

        /** ADMIN 허용 IP — SYSTEM_MANAGED 는 무시 */
        List<String> ips
) {
}
