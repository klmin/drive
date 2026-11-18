package com.thinkfree.drive.user.data;

import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/** 테넌트 계정 검색 결과 — 보유 역할(roles)은 tb_user_role 기준(enum 순서) */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UserSearchData {
    private Integer userSeq;
    private String userId;
    private String userName;
    private UserStatus status;
    private Integer tenantSeq;
    /** 보호 계정 여부(tb_user.is_protected) — SYSTEM_MANAGED 테넌트 생성 계정 */
    private Boolean isProtected;
    private List<RoleType> roles;
}
