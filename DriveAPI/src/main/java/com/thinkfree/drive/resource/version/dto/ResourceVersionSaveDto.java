package com.thinkfree.drive.resource.version.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Builder
@Getter
public class ResourceVersionSaveDto {
    private Integer userSeq;
    private Long resourceSeq;
    private Integer ownerSeq;
    private String resourceSaveName;
    private Integer versionNumber;
    private Long sizeByte;
    private Instant versionRegisterDate;
    private Integer registerUserSeq;
}
