package com.thinkfree.drive.api.v1.superadmin.basedomain.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SuperAdminBaseDomainUpsertRequest(

        /** {@code [scheme://]host[:port]} — 예) {@code drive.example.com}, {@code https://drive.example.com:8443}, {@code 192.168.0.10:8080}(PATH 모드) */
        @NotBlank
        @Size(max = 300)
        String bd
) {
}
