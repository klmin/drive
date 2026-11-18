package com.thinkfree.drive.api.v1.auth.response;

import com.thinkfree.drive.api.v1.auth.enums.LoginType;

public record LoginConfigResponse
(
    LoginType loginType,
    Integer ssoProviderSeq,
    String errorRedirectUrl,
    /** 비밀번호 찾기(재설정 링크 메일) 가능 여부 — SYSTEM_MANAGED 는 메일 차단이라 false */
    boolean passwordResetEnabled
)
{
}
