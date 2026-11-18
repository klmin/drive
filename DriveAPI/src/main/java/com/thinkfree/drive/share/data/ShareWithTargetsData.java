package com.thinkfree.drive.share.data;

import com.thinkfree.drive.user.enums.UserStatus;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ShareWithTargetsData {
    
    private Long shareSeq;
    private Integer userSeq;
    private Long resourceSeq;
    private String shareType;
    private Boolean canView;
    private Boolean canEdit;
    private Boolean canDownload;
    private Boolean canUpload;
    private Boolean canDelete;
    private Boolean canShare;
    private String password;
    private Boolean isPasswordEnabled;
    private Instant expireDate;
    private List<ShareTargetItem> targets;
    
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ShareTargetItem{
        private Long shareTargetSeq;
        private Integer userSeq;
        private String userId;
        private String userName;
        private UserStatus userStatus;
        private Integer tenantSeq;
        private String tenantName;
        private Boolean canView;
        private Boolean canEdit;
        private Boolean canDownload;
        private Boolean canUpload;
        private Boolean canDelete;
        private Boolean canShare;
    }
}
