package com.thinkfree.drive.starred.data;

import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.partition.PartitionKeyProvider;
import com.thinkfree.drive.user.enums.UserStatus;
import lombok.*;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class StarredData implements PartitionKeyProvider {
    
    private Long starredSeq;
    private Integer userSeq;
    private Long resourceSeq;
    private Integer sharedByUserSeq;
    private String ownerId;
    private String ownerName;
    private Integer ownerSeq;
    private UserStatus ownerStatus;
    private Integer ownerTenantSeq;
    private String ownerTenantName;
    private ResourceType resourceType;
    private Long parentFolderSeq;
    private String parentFolderName;
    private String resourceName;
    private String ext;
    private String fullPathSeq;
    private String fullPath;
    private Long sizeByte;
    private Boolean isLocked;
    private String resourceRegisterUserId;
    private String resourceRegisterUserName;
    private UserStatus resourceRegisterUserStatus;
    private Instant resourceRegisterDate;
    private String resourceUpdateUserId;
    private String resourceUpdateUserName;
    private UserStatus resourceUpdateUserStatus;
    private Instant resourceUpdateDate;
    private Instant starredRegisterDate;
        
}
