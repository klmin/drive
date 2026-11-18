package com.thinkfree.drive.user.query;

import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 테넌트 안에서 계정을 검색하는 조건.
 * 여러 role 을 함께 받는다 — {@link UserSearchQuery} 는 role 이 단일값이라 관리자·사용자를 한 목록으로 못 받음.
 */
@Builder
@Getter
public class UserTenantSearchQuery {

    /** userId·userName 부분 일치 */
    private String searchKeyword;
    private Integer tenantSeq;
    private List<RoleType> roleTypes;
    private List<UserStatus> userStatuses;
}
