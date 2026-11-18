package com.thinkfree.drive.api.v1.auth.mfa.request;

import com.thinkfree.drive.user.enums.MfaMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MfaSetupRequest
(
    @NotBlank(message = "Setup Token을 입력해주세요.")
    String t,
    @NotNull(message = "MFA 방식을 입력해주세요.")
    MfaMethod m
)
{
}
