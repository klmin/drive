package com.thinkfree.drive.api.v1.admin.loginsetting.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.thinkfree.drive.api.v1.auth.enums.LoginType;

public record LoginSettingRequest
(
    @Schema(title = "Sign-in mode", description = "Select local account sign-in or the configured external sign-in mode.")
    LoginType loginType
)
{
}
