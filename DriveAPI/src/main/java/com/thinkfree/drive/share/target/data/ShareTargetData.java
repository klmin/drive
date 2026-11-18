package com.thinkfree.drive.share.target.data;

import com.thinkfree.drive.partition.PartitionKeyProvider;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.user.enums.UserStatus;
import lombok.*;

import java.time.Instant;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ShareTargetData implements PartitionKeyProvider {
    
    // 권한(can_*)/근접경로(parentFolder, fullPath)는 이 raw row 가 담지 않는다.
    // PermissionAggregationService 가 ResourcePermission.from()(권한) + farthest(공유 루트 기준 경로)로 해소해
    // ShareTargetResourceData 로 합칠 때 채운다. (ShareTargetMapper.findAll 도 해당 컬럼 미조회)
    private Long shareTargetSeq;
    private Integer userSeq;
    private Long shareSeq;
    private Long resourceSeq;
    private Integer sharedByUserSeq;
    private ResourceType resourceType;
    private String resourceName;
    private String ext;
    private Integer ownerSeq;
    private String ownerId;
    private String ownerName;
    private UserStatus ownerStatus;
    private Integer ownerTenantSeq;
    private String ownerTenantName;
    private Long sizeByte;
    private Boolean isLocked;
    private Boolean isStarred;
    private Long starredSeq;
    private Instant shareDate;
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
    
}
