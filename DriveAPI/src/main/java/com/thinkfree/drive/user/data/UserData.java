package com.thinkfree.drive.user.data;

import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UserData {
    private Integer userSeq;
    private String userId;
    private String userName;
    private UserStatus status;
    private RoleType roleType;
    private Integer tenantSeq;
    private String tenantName;
}
