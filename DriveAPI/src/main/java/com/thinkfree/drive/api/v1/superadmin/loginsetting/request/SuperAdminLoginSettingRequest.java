package com.thinkfree.drive.api.v1.superadmin.loginsetting.request;

import com.thinkfree.drive.api.v1.auth.enums.LoginType;
import com.thinkfree.drive.sso.provider.enums.SsoProviderScope;

public record SuperAdminLoginSettingRequest
(
    SsoProviderScope s,
    LoginType l
)
{
}
