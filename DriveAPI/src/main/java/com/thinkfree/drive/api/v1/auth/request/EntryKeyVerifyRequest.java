package com.thinkfree.drive.api.v1.auth.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class EntryKeyVerifyRequest {
    @NotBlank(message = "진입 키는 필수입니다.")
    private String token;
}
