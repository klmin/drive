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
public class UserPendingDeleteData {
    private Integer userSeq;
    private String userId;
    private String userName;
    private Integer tenantSeq;
    private UserStatus status;
    /** 물리 삭제 대상(findPendingDeleteTargets) 전용 — ADMIN 보유면 ADMIN, 아니면 USER(영구 삭제 감사 이벤트 분기) */
    private RoleType roleType;
    /** 삭제 대기 목록(findPendingDeleteUsers) 전용 — 보유 역할(enum 순서) */
    private List<RoleType> roles;
    private Instant pendingDeleteDate;
    private String profileImageKey;
    private Instant deleteScheduleTime;
    private Integer deleteUserSeq;
    private String deleteUserId;
    private UserStatus deleteUserStatus;
}
