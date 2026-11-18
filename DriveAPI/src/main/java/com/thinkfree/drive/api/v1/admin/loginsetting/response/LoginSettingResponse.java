package com.thinkfree.drive.api.v1.admin.loginsetting.response;

import com.thinkfree.drive.api.v1.auth.enums.LoginType;
import com.thinkfree.drive.api.v1.common.loginsetting.response.SsoProtocolConfigResponse;

import java.util.List;

public record LoginSettingResponse
(
    LoginType loginType,
    String redirectUri,
    boolean superAdminEnforced,
    List<SsoProtocolConfigResponse> configs
)
{
}
