package com.thinkfree.drive.resource.data;

import com.thinkfree.drive.common.search.support.SortFieldHolder;
import com.thinkfree.drive.partition.PartitionKeyProvider;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.user.enums.UserStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ResourceData implements SortFieldHolder, PartitionKeyProvider {
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
    /** 확장자(생성 시 결정, 점 제외) */
    private String ext;
    private String fullPathSeq;
    private String fullPath;
    private Long sizeByte;
    private Boolean isLocked;
    private Boolean isStarred;
    private Long starredSeq;
    private Boolean isShared;
    private Long shareSeq;
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
