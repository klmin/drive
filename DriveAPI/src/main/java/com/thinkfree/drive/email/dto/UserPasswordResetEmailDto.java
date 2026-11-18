package com.thinkfree.drive.email.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Locale;

@Builder
@Getter
public class UserPasswordResetEmailDto {
    private Integer tenantSeq;
    private String toEmail;
    private String userName;
    private String serviceName;
    private String resetLink;
    private Locale locale;
    private String logoUrl;
}
