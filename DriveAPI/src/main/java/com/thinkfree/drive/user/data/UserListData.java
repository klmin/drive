package com.thinkfree.drive.user.data;

import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UserListData {
    private Integer userSeq;
    private String userId;
    private String userName;
    private UserStatus status;
    private Long totalQuotaByte;
    private Long useQuotaByte;
    private Integer registerUserSeq;
    private Instant registerDate;
    private Integer updateUserSeq;
    private Instant updateDate;
    private Instant lastLoginDate;
    /** 보유 역할(enum 순서) — 역할 행이 없으면 빈 목록 */
    private List<RoleType> roles;
    /** 보호 계정(tb_user.is_protected) — 막는 판정은 TenantPolicy(SYSTEM_MANAGED 만), 여기는 표시용 */
    private Boolean isProtected;
}
