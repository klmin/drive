package com.thinkfree.drive.api.v1.auth.mfa.request;

import com.thinkfree.drive.user.enums.MfaMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MfaConfirmRequest
(
    @NotBlank(message = "Confirm Token을 입력해주세요.")
    String t,
    @NotBlank(message = "인증 코드를 입력해주세요.")
    String c,
    @NotNull(message = "MFA 방식을 입력해주세요.")
    MfaMethod m
)
{
}
