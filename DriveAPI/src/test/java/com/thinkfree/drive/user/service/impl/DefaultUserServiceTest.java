package com.thinkfree.drive.user.service.impl;

import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.role.service.UserRoleFindService;
import com.thinkfree.drive.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class DefaultUserServiceTest {

    @Autowired
    public UserService userService;

    @Autowired
    public UserRoleFindService userRoleFindService;

    @Test
    void isAnonymousUser_anonymous() {
        // 익명 유저 seq (tb_user_role = ANONYMOUS)
        assertTrue(userRoleFindService.holds(938, RoleType.ANONYMOUS));
    }

    @Test
    void isAnonymousUser_loginUser() {
        // 일반 로그인 유저 seq
        assertFalse(userRoleFindService.holds(859, RoleType.ANONYMOUS));
    }


}
