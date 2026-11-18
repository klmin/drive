package com.thinkfree.drive.api.v1.superadmin.apikey.request;

import jakarta.validation.constraints.NotNull;

/**
 * 슈퍼관리자 보호 키 설정·해제 요청.
 * 보호 키는 관리자·사용자 경로에서 만료일 수정·활성화·비활성화·재발급이 거부된다(API_KEY_017) — 슈퍼관리자 경로만 바꿀 수 있다.
 */
public record SuperAdminApiKeyProtectionUpdateRequest(
        /* true = 보호 키로 설정, false = 해제. 필수 */
        @NotNull
        Boolean pt
) {
}
