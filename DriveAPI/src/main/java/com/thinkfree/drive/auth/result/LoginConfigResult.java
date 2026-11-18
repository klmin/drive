package com.thinkfree.drive.auth.result;

import com.thinkfree.drive.api.v1.auth.enums.LoginType;

public record LoginConfigResult
(
    LoginType loginType,
    Integer ssoProviderSeq,
    String errorRedirectUrl
)
{
}
