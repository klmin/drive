package com.thinkfree.drive.api.v1.superadmin.tenant.response;

import com.thinkfree.drive.global.annotation.DriveDateTimeFormat;
import com.thinkfree.drive.tenant.enums.TenantType;
import com.thinkfree.drive.tenant.enums.TenantUserMode;

import java.time.Instant;

/**
 * 슈퍼어드민 테넌트 목록 응답.
 * license 는 null 일 수 있음 (라이선스 미등록/검증 실패) — FE 는 null 을 "라이선스 없음" 으로 표시.
 * 라이선스는 테넌트 단위 — row 별로 해당 테넌트의 라이선스 정보가 채워진다.
 */
public record SuperAdminTenantListResponse(
        Integer tenantSeq,
        String tenantName,
        TenantType tenantType,
        TenantUserMode userMode,
        String domain,
        String clientUrl,
        @DriveDateTimeFormat
        Instant registerDate,
        LicenseInfo license,
        /* Office 연결 요약 — 행 메뉴가 등록/수정을 고르고 목록에 연결 상태를 보이는 데 쓴다(값 전체는 GET /{tenantSeq}/office) */
        OfficeInfo office
) {
    /**
     * @param connectionType {@code onPremise} | {@code none} — 키가 없으면 도메인 유무로 판정(관리자 office 조회와 같은 규칙)
     * @param serviceManaged 슈퍼관리자가 저장해 서비스 관리로 표시됨(그 테넌트 관리자는 office 를 못 고친다)
     */
    public record OfficeInfo(
            String connectionType,
            boolean serviceManaged
    ) {}

    public record LicenseInfo(
            boolean state,
            String expiresOn,
            SeatInfo seat
    ) {}

    public record SeatInfo(
            int current,
            int max
    ) {}
}
