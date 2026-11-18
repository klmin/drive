package com.thinkfree.drive.email.dto;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class UserCsvRegisteredEmailDto {
    private String recipientEmail;
    private String baseUrl;
    private String pw;
    private String loginUrl;
    private String serviceName;
}
