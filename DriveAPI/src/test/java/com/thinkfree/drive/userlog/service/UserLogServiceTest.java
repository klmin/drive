package com.thinkfree.drive.userlog.service;

import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.common.ServiceTest;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.userlog.data.UserLogData;
import com.thinkfree.drive.userlog.enums.ActivityType;
import com.thinkfree.drive.userlog.enums.ActorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static com.thinkfree.drive.fixture.UserFixture.TEST_USER_01;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserLog [Service Layer] -> UserLogService 테스트")
public class UserLogServiceTest extends ServiceTest {

    @Autowired
    private UserLogService userLogService;

    @Autowired
    private UserLogFindService userLogFindService;

    private UserDetailData testUser;
    private MockHttpServletRequest mockRequest;

    @BeforeEach
    void setup() {
        insertUserWithRole(TEST_USER_01.toUser(), TEST_USER_01.getRoleType());
        testUser = userQueryRepository.findActiveDetailByUserId(TEST_USER_01.getTenantSeq(), TEST_USER_01.getUserId());
        mockRequest = new MockHttpServletRequest();
        mockRequest.setRemoteAddr("127.0.0.1");
    }

    @Nested
    @DisplayName("초기 비밀번호 변경 여부 확인 (hasChangedPassword)")
    class HasChangedPassword {

        @Test
        @DisplayName("비밀번호 변경 이력이 없으면 false를 반환한다")
        void returnsFalseWhenNoChangePasswordLog() {
            assertThat(userLogService.hasChangedPassword(testUser.getUserSeq())).isFalse();
        }

        @Test
        @DisplayName("비밀번호 변경 이력이 있으면 true를 반환한다")
        void returnsTrueWhenChangePasswordLogExists() {
            userLogService.registerChangePasswordLog(testUser.getUserSeq(), mockRequest, ActorType.USER);

            assertThat(userLogService.hasChangedPassword(testUser.getUserSeq())).isTrue();
        }

        @Test
        @DisplayName("로그인 이력만 있고 비밀번호 변경 이력이 없으면 false를 반환한다")
        void returnsFalseWhenOnlyLoginLogExists() {
            userLogService.registerLoginLog(testUser.getUserSeq(), mockRequest);

            assertThat(userLogService.hasChangedPassword(testUser.getUserSeq())).isFalse();
        }

        @Test
        @DisplayName("관리자가 변경한 이력도 true를 반환한다")
        void returnsTrueWhenAdminChangedPassword() {
            userLogService.registerChangePasswordLog(testUser.getUserSeq(), mockRequest, ActorType.ADMIN);

            assertThat(userLogService.hasChangedPassword(testUser.getUserSeq())).isTrue();
        }
    }

    @Nested
    @DisplayName("로그인 로그 등록 (registerLoginLog)")
    class RegisterLoginLog {

        @Test
        @DisplayName("로그인 시 LOGIN 타입 로그가 기록된다")
        void recordsLoginActivityLog() {
            userLogService.registerLoginLog(testUser.getUserSeq(), mockRequest);

            List<UserLogData> logs = userLogFindService.findMyLogs(testUser.getUserSeq(), firstPage()).getList();
            assertThat(logs).hasSize(1);
            assertThat(logs.get(0).getActivityType()).isEqualTo(ActivityType.LOGIN.name());
        }
    }

    @Nested
    @DisplayName("로그아웃 로그 등록 (registerLogoutLog)")
    class RegisterLogoutLog {

        @Test
        @DisplayName("로그아웃 시 LOGOUT 타입 로그가 기록된다")
        void recordsLogoutActivityLog() {
            userLogService.registerLogoutLog(testUser.getUserSeq(), mockRequest);

            List<UserLogData> logs = userLogFindService.findMyLogs(testUser.getUserSeq(), firstPage()).getList();
            assertThat(logs).hasSize(1);
            assertThat(logs.get(0).getActivityType()).isEqualTo(ActivityType.LOGOUT.name());
        }
    }

    @Nested
    @DisplayName("사용자 로그 조회 (findMyLogs)")
    class FindMyLogs {

        @Test
        @DisplayName("주어진 userSeq의 로그만 조회된다")
        void returnsOnlyMatchingUserLogs() {
            userLogService.registerLoginLog(testUser.getUserSeq(), mockRequest);
            userLogService.registerChangePasswordLog(testUser.getUserSeq(), mockRequest, ActorType.USER);

            List<UserLogData> logs = userLogFindService.findMyLogs(testUser.getUserSeq(), firstPage()).getList();
            assertThat(logs).hasSize(2);
            assertThat(logs).allMatch(log -> log.getUserSeq().equals(testUser.getUserSeq()));
        }

        @Test
        @DisplayName("로그가 없으면 빈 목록이 반환된다")
        void returnsEmptyWhenNoLogs() {
            List<UserLogData> logs = userLogFindService.findMyLogs(testUser.getUserSeq(), firstPage()).getList();
            assertThat(logs).isEmpty();
        }
    }

    private CursorPagingRequest firstPage() {
        return new CursorPagingRequest(null, null, null, 10);
    }
}
