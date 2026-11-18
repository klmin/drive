package com.thinkfree.drive.email.dto;

import com.thinkfree.drive.resource.enums.ResourceType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Locale;

@Builder
@Getter
public class ShareTargetAddedEmailDto {
    private Integer tenantSeq;
    private String requestUserId;
    private String requestUserName;
    private ResourceType resourceType;
    private String resourceName;
    private List<String> recipientEmails;
    private Locale locale;
}
