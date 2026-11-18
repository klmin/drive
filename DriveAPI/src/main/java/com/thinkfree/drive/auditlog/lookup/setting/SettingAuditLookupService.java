package com.thinkfree.drive.auditlog.lookup.setting;

import com.thinkfree.drive.api.v1.auth.enums.LoginType;

/**
 * 감사 로그(SETTING_UPDATE) 기록에 필요하지만 비즈니스 로직상으로는 조회할 필요가 없는
 * "변경 전 값"을 조회하는 공용 창구. 새 쿼리를 추가하지 않고 기존 FindService 호출만 감싼다.
 */
public interface SettingAuditLookupService {

    LoginType findTenantLoginType(int tenantSeq);

    boolean isGlobalLoginEnforced();

    boolean isGlobalShareEnforced();
}
