package com.thinkfree.drive.weboffice.dto;

import com.thinkfree.drive.resource.enums.ResourceType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Builder
@Getter
public class WebOfficeInfoResult {
    private ResourceType resourceType;
    private String resourceName;
    private Instant updateDate;
    private Boolean read;
    private Boolean write;
    private Boolean locked;
    private String loginUserName;
    private long sizeByte;
    private String profileImage;
    private String cursorColor;
    private int currentVersionNumber;
}
