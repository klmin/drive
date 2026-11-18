package com.thinkfree.drive.sso.protocol.oidc.dto;

import com.thinkfree.drive.signup.dto.SignupDto;

public record OidcSignupDto
(
    String email,
    String name,
    int tenantSeq,
    int ssoProviderSeq,
    String subject,
    String requestEmail,
    String requestName
)
    implements SignupDto
{

}
