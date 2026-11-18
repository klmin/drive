package com.thinkfree.drive.share.target.data;

import com.thinkfree.drive.common.search.support.SortFieldHolder;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.user.enums.UserStatus;
import lombok.*;

import java.time.Instant;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ShareTargetResourceData implements SortFieldHolder {
    
    private Long shareTargetSeq;
    private Integer userSeq;
    private Long shareSeq;
    private Long resourceSeq;
    private Integer sharedByUserSeq;
    private ResourceType resourceType;
    private Long parentFolderSeq;
    private String parentFolderName;
    private String resourceName;
    private String ext;
    private Integer ownerSeq;
    private String ownerId;
    private String ownerName;
    private UserStatus ownerStatus;
    private Integer ownerTenantSeq;
    private String ownerTenantName;
    private String fullPathSeq;
    private String fullPath;
    private Long sizeByte;
    private Boolean isLocked;
    private Boolean isStarred;
    private Long starredSeq;
    private Instant shareDate;
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
    private String resourceRegisterUserId;
    private String resourceRegisterUserName;
    private UserStatus resourceRegisterUserStatus;
    private Instant resourceRegisterDate;
    private String resourceUpdateUserId;
    private String resourceUpdateUserName;
    private UserStatus resourceUpdateUserStatus;
    private Instant resourceUpdateDate;
    private Instant recentUpdateDate;

    @Override
    public Instant getUpdateDate() {
        return this.resourceUpdateDate;
    }

    /**
     * 최근 열람(OPEN) 정렬 커서값. 열람 기록이 없으면 리소스 수정일로 fallback —
     * ResourceData/ResourceGlobalListData 와 동일 규약이며, ShareTargetMapper OPEN 분기의
     * COALESCE(J.UPDATE_DATE, B.UPDATE_DATE) 와 같은 값이어야 커서 페이징이 맞는다.
     * (이전엔 Lombok 기본 getter 라 미열람 행에서 null → nextCursorValue null → 커서 게이트 미적용으로 첫 페이지 반복)
     */
    @Override
    public Instant getRecentUpdateDate() {
        return recentUpdateDate != null ? recentUpdateDate : resourceUpdateDate;
    }
    
}
