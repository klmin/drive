package com.thinkfree.drive.tenant.policy;

import com.thinkfree.drive.tenant.enums.TenantType;
import com.thinkfree.drive.tenant.enums.TenantUserMode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 기능이 막히는 테넌트·계정 조건 하나 — 지정한 항목끼리는 AND, null(또는 protectedAccount=false)은 "상관없음".
 * 여러 조건은 OR 로 읽는다({@link DefaultTenantPolicy#restrictedWhen}).
 *
 * @param tenantType       이 유형일 때, null 이면 모든 유형
 * @param userMode         이 사용자 모드일 때, null 이면 모든 모드
 * @param protectedAccount true 면 대상(또는 본인)이 보호 계정일 때만
 */
public record TenantRestrictionCondition(TenantType tenantType, TenantUserMode userMode, boolean protectedAccount) {

    public boolean matches(TenantType type, TenantUserMode mode, boolean isProtected) {
        return (tenantType == null || tenantType == type)
                && (userMode == null || userMode == mode)
                && (!protectedAccount || isProtected);
    }

    /** OpenAPI {@code x-drive} 용 — 상관없는 항목은 뺀다 */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (tenantType != null) {
            map.put("tenantType", tenantType.name());
        }
        if (userMode != null) {
            map.put("userMode", userMode.name());
        }
        if (protectedAccount) {
            map.put("protectedAccount", true);
        }
        return map;
    }
}
