package com.thinkfree.drive.trash.data;

import com.thinkfree.drive.common.search.support.SortFieldHolder;
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
public class TrashData implements SortFieldHolder {

    private Long trashSeq;
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
    private String registerUserId;
    private String registerUserName;
    private UserStatus registerUserStatus;
    private Instant registerDate;
    private String updateUserId;
    private String updateUserName;
    private UserStatus updateUserStatus;
    private Instant updateDate;
    private Integer deleteUserSeq;
    private String deleteUserId;
    private String deleteUserName;
    private Integer deleteUserTenantSeq;
    private String deleteUserTenantName;
    private UserStatus deleteUserStatus;
    private Instant deleteDate;

    @Override
    public Instant getRecentUpdateDate() {
        return this.registerDate;
    }

    @Override
    public Instant getRegisterDate() {
        return this.deleteDate;
    }

    @Override
    public Instant getShareDate() {
        return null;
    }
}
