package com.thinkfree.drive.user.query;

import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class UserSearchQuery {
    private String searchKeyword;
    private RoleType roleType;
    private List<UserStatus> userStatuses;
    private Integer requestUserSeq;
    private Integer tenantSeq;
}
