package com.thinkfree.drive.user.service.impl;

import com.thinkfree.drive.common.DriveFixtures;
import com.thinkfree.drive.common.DriveIntegrationTest;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.role.service.UserRoleFindService;
import com.thinkfree.drive.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DriveIntegrationTest
class DefaultUserServiceTest {

    @Autowired
    public UserService userService;

    @Autowired
    public UserRoleFindService userRoleFindService;

    @Test
    void isAnonymousUser_anonymous() {
        // V1 시드 익명 사용자(tb_user_role = ANONYMOUS)
        assertTrue(userRoleFindService.holds(DriveFixtures.ANONYMOUS, RoleType.ANONYMOUS));
    }

    @Test
    void isAnonymousUser_loginUser() {
        // V1 시드 로그인 사용자(admin, ADMIN)
        assertFalse(userRoleFindService.holds(DriveFixtures.ADMIN, RoleType.ANONYMOUS));
        assertTrue(userRoleFindService.holds(DriveFixtures.ADMIN, RoleType.ADMIN));
    }


}
