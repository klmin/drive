package com.thinkfree.drive.user.query;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class UserListQuery {
    private UserStatus status;
    private String userId;
    /** 이 중 하나라도 보유한 계정(tb_user_role) */
    private List<RoleType> roles;
    private Integer tenantSeq;
    private CursorPaginationQuery page;
}
