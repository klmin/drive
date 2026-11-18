package com.thinkfree.drive.api.v1.auth.mfa.request;

import jakarta.validation.constraints.NotBlank;

public record MfaVerifyRequest
(
    @NotBlank(message = "Challenge Token을 입력해주세요.")
    String t,
    @NotBlank(message = "인증 코드를 입력해주세요.")
    String c
)
{
}
