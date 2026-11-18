package com.thinkfree.drive.superadmin.loginsetting.dto;

import com.thinkfree.drive.api.v1.auth.enums.LoginType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SuperAdminLoginSettingUpsertDto {
    private LoginType loginType;
    private Boolean isEnforced;
}
