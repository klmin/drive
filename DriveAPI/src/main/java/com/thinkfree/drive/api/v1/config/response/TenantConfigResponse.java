package com.thinkfree.drive.api.v1.config.response;

import com.thinkfree.drive.tenant.enums.TenantType;
import com.thinkfree.drive.tenant.enums.TenantUserMode;

/**
 * 요청 테넌트의 유형·사용자 모드 — 화면이 막힌 기능의 버튼을 미리 끄는 데 쓴다(거부 판정은 서버 TenantPolicy).
 * 둘 다 테넌트 생성 후 바뀌지 않으므로 화면은 한 번 읽어 두면 된다({@code GET /config} 처럼 응답마다 다시 읽지 않는다).
 */
public record TenantConfigResponse(
        TenantType tenantType,
        TenantUserMode userMode
) {
}
