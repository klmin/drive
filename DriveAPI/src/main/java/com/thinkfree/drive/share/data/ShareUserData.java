package com.thinkfree.drive.share.data;

import com.thinkfree.drive.user.enums.UserStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ShareUserData {
    private Integer userSeq;
    private String userId;
    private String userName;
    private UserStatus userStatus;
    private Integer tenantSeq;
    private String tenantName;
}
