package com.thinkfree.drive.api.v1.superadmin.loginsetting.response;

import com.thinkfree.drive.api.v1.auth.enums.LoginType;
import com.thinkfree.drive.api.v1.common.loginsetting.response.SsoProtocolConfigResponse;

import java.util.List;

public record SuperAdminLoginSettingResponse
(
    Integer superAdminLoginSettingSeq,
    LoginType loginType,
    Boolean isEnforced,
    List<SsoProtocolConfigResponse> configs
)
{
}
