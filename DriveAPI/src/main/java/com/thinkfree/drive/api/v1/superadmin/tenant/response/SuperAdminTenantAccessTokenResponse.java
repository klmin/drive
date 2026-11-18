package com.thinkfree.drive.api.v1.superadmin.tenant.response;

/**
 * 슈퍼어드민 테넌트 진입 키 응답.
 *
 * @param entryKey 일회용 진입 키
 * @param entryUrl 진입 완성 URL — 테넌트 주소 체계(서브도메인/경로)를 서버가 반영해 조립.
 *                 프론트는 이 값을 그대로 열면 되고 {@code tenant.domain} 으로 직접 조립하지 않는다.
 */
public record SuperAdminTenantAccessTokenResponse(String entryKey, String entryUrl) {
    public static SuperAdminTenantAccessTokenResponse of(String entryKey, String entryUrl) {
        return new SuperAdminTenantAccessTokenResponse(entryKey, entryUrl);
    }
}
