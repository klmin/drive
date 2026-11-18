package com.thinkfree.drive.resource.version.data;

import com.thinkfree.drive.resource.version.enums.ResourceVersionType;
import com.thinkfree.drive.user.enums.UserStatus;
import lombok.*;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ResourceVersionData {
    private Long resourceVersionSeq;
    private Integer userSeq;
    private String userName;
    private Integer tenantSeq;
    private String tenantName;
    private Long resourceSeq;
    private Integer versionNumber;
    private Long sizeByte;
    private ResourceVersionType versionType;
    private Integer ownerSeq;
    private String ownerName;
    private UserStatus ownerStatus;
    private Integer ownerTenantSeq;
    private String ownerTenantName;
    private Instant versionRegisterDate;
}
