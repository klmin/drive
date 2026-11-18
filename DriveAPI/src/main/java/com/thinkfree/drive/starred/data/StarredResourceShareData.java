package com.thinkfree.drive.starred.data;

import com.thinkfree.drive.common.search.support.SortFieldHolder;
import com.thinkfree.drive.resource.enums.ResourceLocation;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.user.enums.UserStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Builder
@Getter
public class StarredResourceShareData implements SortFieldHolder {
    private Long starredSeq;
    private Integer userSeq;
    private Long resourceSeq;
    private Integer sharedByUserSeq;
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
    private Boolean isShared;
    private Long shareSeq;
    private Long shareResourceSeq;
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
    private Instant starredRegisterDate;
    private ResourceLocation resourceLocation;

    @Override
    public Instant getUpdateDate() {
        return this.resourceUpdateDate;
    }

    @Override
    public Instant getRecentUpdateDate() {
        return this.resourceUpdateDate;
    }

    @Override
    public Instant getRegisterDate() {
        return this.resourceRegisterDate;
    }

    @Override
    public Instant getShareDate() {
        return null;
    }
}
