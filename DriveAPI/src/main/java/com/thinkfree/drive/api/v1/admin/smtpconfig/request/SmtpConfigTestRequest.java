package com.thinkfree.drive.api.v1.admin.smtpconfig.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SmtpConfigTestRequest {
    @NotNull
    @Schema(title = "SMTP configuration ID", description = "ID of the saved SMTP configuration to use.")
    private Integer smtpConfigSeq;
}

