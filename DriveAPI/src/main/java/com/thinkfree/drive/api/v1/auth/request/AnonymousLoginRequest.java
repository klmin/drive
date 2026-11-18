package com.thinkfree.drive.api.v1.auth.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AnonymousLoginRequest {
    @NotBlank(message = "링크 토큰은 필수입니다.")
    private String linkToken;
}
