package com.thinkfree.drive.resource.global.data;

import com.thinkfree.drive.common.search.support.SortFieldHolder;
import com.thinkfree.drive.partition.PartitionKeyProvider;
import com.thinkfree.drive.resource.enums.ResourceLocation;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.share.enums.ShareType;
import com.thinkfree.drive.user.enums.UserStatus;
import lombok.*;

import java.time.Instant;

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ResourceGlobalListData implements SortFieldHolder, PartitionKeyProvider {
    
    private Long resourceSeq;
    private Integer userSeq;
    private Integer ownerSeq;
    private String ownerId;
    private String ownerName;
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
    private Boolean isStarred;
    private Long starredSeq;
    private Boolean isShared;
    private Long shareSeq;
    private ShareType shareType;
    private Long shareTargetSeq;
    private Long shareResourceSeq;
    private Boolean canView;
    private Boolean canEdit;
    private Boolean canDownload;
    private Boolean canUpload;
    private Boolean canDelete;
    private Boolean canShare;
    private String registerUserId;
    private String registerUserName;
    private UserStatus registerUserStatus;
    private Instant registerDate;
    private String updateUserId;
    private String updateUserName;
    private UserStatus updateUserStatus;
    private Instant updateDate;
    private Instant recentUpdateDate;
    private ResourceLocation resourceLocation;

    @Override
    public Instant getRecentUpdateDate() {
        return recentUpdateDate != null ? recentUpdateDate : updateDate;
    }

    @Override
    public Instant getShareDate() {
        return null;
    }
    
    @Override
    public Integer getSharedByUserSeq() {
        return userSeq;
    }
}
