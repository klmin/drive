package com.thinkfree.drive.user.repository;

import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.SignupType;
import com.thinkfree.drive.user.enums.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.thinkfree.drive.fixture.UserFixture.TEST_USER_01;
import static com.thinkfree.drive.fixture.UserFixture.TEST_USER_02;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SEAT 라이선스 - DB 레벨 시트 체크 통합 테스트")
class SeatInsertCheckRepositoryTest extends RepositoryTest {

    private static final int TENANT_SEQ = 1;
    // schema-test.sql이 admin 유저(ACTIVE) 1명을 기본 삽입하므로 시작 카운트는 1
    private static final int SCHEMA_DEFAULT_ACTIVE_COUNT = 1;

    private User buildNewUser(String email) {
        return User.createUser(email, TENANT_SEQ, null, SignupType.LOCAL,
                "신규유저", UserStatus.ACTIVE, 0L, 1, null, null, null);
    }

    @Nested
    @DisplayName("SEAT 라이선스인 경우 (isSeat=true)")
    class WhenSeat {

        @Test
        @DisplayName("현재 활성 사용자 수 < constrainValue → INSERT 성공 (1 rows affected)")
        void activeUsersLessThanConstraint_insertSucceeds() {
            // given: 기본 admin(1) + TEST_USER_01(1) = 2명, constrainValue=3 → 여유 있음
            userRepository.insertUserWithSeatCheck(TEST_USER_01.toUser(), false, 0);

            User newUser = buildNewUser("new@example.com");

            // when
            boolean result = userRepository.insertUserWithSeatCheck(newUser, true, SCHEMA_DEFAULT_ACTIVE_COUNT + 2);

            // then
            assertThat(result).isTrue();
            assertThat(newUser.getUserSeq()).isPositive();
        }

        @Test
        @DisplayName("현재 활성 사용자 수 == constrainValue → INSERT 차단 (0 rows affected)")
        void activeUsersEqualToConstraint_insertBlocked() {
            // given: 기본 admin(1) + TEST_USER_01(1) + TEST_USER_02(1) = 3명, constrainValue=3 → 시트 꽉 참
            userRepository.insertUserWithSeatCheck(TEST_USER_01.toUser(), false, 0);
            userRepository.insertUserWithSeatCheck(TEST_USER_02.toUser(), false, 0);

            User newUser = buildNewUser("new@example.com");

            // when
            boolean result = userRepository.insertUserWithSeatCheck(newUser, true, SCHEMA_DEFAULT_ACTIVE_COUNT + 2);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("현재 활성 사용자 수 > constrainValue → INSERT 차단 (0 rows affected)")
        void activeUsersExceedConstraint_insertBlocked() {
            // given: ACTIVE 유저 2명, constrainValue=1 → 이미 초과
            userRepository.insertUserWithSeatCheck(TEST_USER_01.toUser(), false, 0);
            userRepository.insertUserWithSeatCheck(TEST_USER_02.toUser(), false, 0);

            User newUser = buildNewUser("new@example.com");

            // when
            boolean result = userRepository.insertUserWithSeatCheck(newUser, true, 1);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("constrainValue=0인 SEAT → INSERT 차단 (잘못 발급된 라이선스)")
        void constrainValueZero_seat_insertBlocked() {
            // given: constrainValue=0 SEAT → 0명도 허용 안 됨
            User newUser = buildNewUser("new@example.com");

            // when
            boolean result = userRepository.insertUserWithSeatCheck(newUser, true, 0);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("비-SEAT 라이선스인 경우 (isSeat=false)")
    class WhenNonSeat {

        @Test
        @DisplayName("constrainValue=0 이어도 isSeat=false → 활성 사용자 수 무관하게 INSERT 성공")
        void nonSeat_constrainValueZero_insertAlwaysSucceeds() {
            // given: ACTIVE 유저 다수 존재, constrainValue=0 (비-SEAT 기본값)
            userRepository.insertUserWithSeatCheck(TEST_USER_01.toUser(), false, 0);
            userRepository.insertUserWithSeatCheck(TEST_USER_02.toUser(), false, 0);

            User newUser = buildNewUser("new@example.com");

            // when
            boolean result = userRepository.insertUserWithSeatCheck(newUser, false, 0);

            // then: isSeat=false 조건이 WHERE 절을 단락시켜 COUNT 체크를 건너뜀
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("활성 사용자가 없어도 isSeat=false → INSERT 성공")
        void nonSeat_noActiveUsers_insertSucceeds() {
            User newUser = buildNewUser("new@example.com");

            boolean result = userRepository.insertUserWithSeatCheck(newUser, false, 0);

            assertThat(result).isTrue();
        }
    }
}
