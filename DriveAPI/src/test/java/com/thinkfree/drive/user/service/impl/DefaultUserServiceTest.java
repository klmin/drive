package com.thinkfree.drive.user.service.impl;

import org.junit.jupiter.api.Disabled;
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
    @Disabled("dev DB 데이터 의존 — 익명 판정 대상 사용자 필요(test 프로필 Testcontainers 에 없음). 테스트 픽스처로 옮길 때 다시 켠다")
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
