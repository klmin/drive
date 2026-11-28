package com.thinkfree.drive.user.service;

import com.thinkfree.drive.common.DriveIntegrationTest;
import com.thinkfree.drive.user.data.LoginAttempt;
import com.thinkfree.drive.user.util.LoginLockPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로그인 시도 카운트의 동시성 — 같은 계정에 동시에 많은 시도가 와도 비밀번호 비교까지 가는 시도(COUNTED)는 정확히 5번.
 * 예전에는 요청 시작 때 읽은 횟수로 판단해 동시에 보낸 요청이 모두 통과했다.
 * 서비스 메서드가 새 트랜잭션(REQUIRES_NEW)이고 행이 커밋돼 있어야 해서 테스트 트랜잭션 없이 Flyway 시드 사용자(2)를 쓰고 끝에 값을 되돌린다.
 */
@DriveIntegrationTest
class LoginAttemptConcurrencyIntegrationTest {

    static final int USER = 2;
    static final int THREADS = 20;

    @Autowired UserService userService;
    @Autowired JdbcTemplate jdbc;

    Map<String, Object> before;

    @BeforeEach
    void setUp() {
        before = jdbc.queryForMap("SELECT login_failure_count, login_failed_at FROM tb_user WHERE user_seq = ?", USER);
        jdbc.update("UPDATE tb_user SET login_failure_count = 0, login_failed_at = NULL WHERE user_seq = ?", USER);
    }

    @AfterEach
    void restore() {
        jdbc.update("UPDATE tb_user SET login_failure_count = ?, login_failed_at = ? WHERE user_seq = ?",
                before.get("login_failure_count"), (Timestamp) before.get("login_failed_at"), USER);
    }

    @Test
    @DisplayName("동시 20회 시도 — COUNTED 는 1~5 한 번씩, 나머지는 LOCKED, 최종 횟수 5")
    void concurrentAttemptsCountedExactlyUpToLimit() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<LoginAttempt>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return userService.countLoginAttempt(USER);
                }));
            }
            start.countDown();

            List<Integer> counted = new ArrayList<>();
            int locked = 0;
            for (Future<LoginAttempt> f : futures) {
                LoginAttempt attempt = f.get(30, TimeUnit.SECONDS);
                switch (attempt.outcome()) {
                    case COUNTED -> counted.add(attempt.failureCount());
                    case LOCKED -> locked++;
                    case NOT_FOUND -> throw new AssertionError("커밋된 시드 사용자가 안 보임");
                }
            }

            assertThat(counted).containsExactlyInAnyOrder(1, 2, 3, 4, 5);
            assertThat(locked).isEqualTo(THREADS - LoginLockPolicy.MAX_FAILURES);
            assertThat(jdbc.queryForObject("SELECT login_failure_count FROM tb_user WHERE user_seq = ?", Integer.class, USER))
                    .isEqualTo(LoginLockPolicy.MAX_FAILURES);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("비밀번호 일치 초기화는 새 트랜잭션으로 커밋된다")
    void resetOnPasswordMatchCommits() {
        userService.countLoginAttempt(USER);
        userService.countLoginAttempt(USER);
        userService.resetLoginFailureCountOnPasswordMatch(USER);
        assertThat(jdbc.queryForObject("SELECT login_failure_count FROM tb_user WHERE user_seq = ?", Integer.class, USER)).isZero();
    }
}
