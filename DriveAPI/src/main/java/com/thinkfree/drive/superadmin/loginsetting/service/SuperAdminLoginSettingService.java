package com.thinkfree.drive.superadmin.loginsetting.service;

import com.thinkfree.drive.superadmin.loginsetting.dto.SuperAdminLoginSettingUpsertDto;

public interface SuperAdminLoginSettingService {
    void upsert(SuperAdminLoginSettingUpsertDto dto);
    void upsertEnforce(boolean isEnforced);
}
