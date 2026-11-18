package com.thinkfree.drive.user.data;

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
public class UserBulkStatusData {
    private Integer userSeq;
    private String userId;
    private UserStatus status;
}
