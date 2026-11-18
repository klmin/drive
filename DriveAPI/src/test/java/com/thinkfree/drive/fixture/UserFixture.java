package com.thinkfree.drive.fixture;


import com.thinkfree.drive.user.entity.Password;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.user.enums.SignupType;
import com.thinkfree.drive.user.enums.UserStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static com.thinkfree.drive.global.utils.PasswordEncoderUtils.ENCODER;

@Getter
@RequiredArgsConstructor
public enum UserFixture {
    TEST_SUPER_ADMIN_01("superadmin@thinkfree.com", "SuperAdmin1234!", "슈퍼어드민", UserStatus.ACTIVE, 0L, 0L, RoleType.SUPER_ADMIN, 1, null, null, null, null),
    TEST_ADMIN_01("admin@thinkfree.com", "Admin1234!", "관리자", UserStatus.ACTIVE, 1L, 0L, RoleType.ADMIN, 1, null, null, null, 1),
    TEST_USER_01("user1@thinkfree.com", "User1234!", "유저1", UserStatus.ACTIVE, 1L, 0L, RoleType.USER, 1, null, null, "010-1111-1111", 1),
    TEST_USER_02("user2@thinkfree.com", "User1234!", "유저2", UserStatus.ACTIVE, 1L, 0L, RoleType.USER, 1, null, null, "010-2222-2222", 1);

    private final String userId;
    private final String encPwd;
    private final String userName;
    private final UserStatus status;
    private final Long totalQuotaByte;
    private final Long useQuotaByte;
    private final RoleType roleType;
    private final Integer registerUserSeq;
    private final String profileImageName;
    private final String countryCode;
    private final String phoneNumber;
    private final Integer tenantSeq;

    public User toUser() {
        return User.createUser(userId, tenantSeq, Password.encrypt(encPwd, ENCODER), SignupType.LOCAL, userName, status, totalQuotaByte, registerUserSeq, profileImageName, countryCode, phoneNumber);
    }
}
