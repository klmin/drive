package com.thinkfree.drive.recent.data;

import com.thinkfree.drive.resource.enums.ResourceLocation;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.user.enums.UserStatus;
import lombok.*;

import java.time.Instant;

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RecentResourceShareData {
    private Long recentSeq;
    private Integer userSeq;
    private Long resourceSeq;
    private Integer sharedByUserSeq;
    private String ownerId;
    private String ownerName;
    private Integer ownerSeq;
    private Integer ownerTenantSeq;
    private String ownerTenantName;
    private UserStatus ownerStatus;
    private ResourceType resourceType;
    private Long parentFolderSeq;
    private String parentFolderName;
    private String resourceName;
    private String ext;
    private String fullPathSeq;
    private String fullPath;
    private Long sizeByte;
    private Boolean isLocked;
    private Boolean isStarred;
    private Long starredSeq;
    private Boolean isShared;
    private Long shareResourceSeq;
    private Long shareSeq;
    private Long shareTargetSeq;
    private Boolean canView;
    private Boolean canEdit;
    private Boolean canDownload;
    private Boolean canUpload;
    private Boolean canDelete;
    private Boolean canShare;
    private String resourceRegisterUserId;
    private String resourceRegisterUserName;
    private UserStatus resourceRegisterUserStatus;
    private Instant resourceRegisterDate;
    private String resourceUpdateUserId;
    private String resourceUpdateUserName;
    private UserStatus resourceUpdateUserStatus;
    private Instant resourceUpdateDate;
    private Instant recentRegisterDate;
    private Instant recentUpdateDate;
    private ResourceLocation resourceLocation;
}
