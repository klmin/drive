package com.thinkfree.drive.user.repository;

import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.SignupType;
import com.thinkfree.drive.user.enums.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SEAT 좌석 동시성 — 코드 리뷰 P2 재현. 남은 좌석 1개에서 서로 다른 행을 고치는 두 요청(활성화·등록)이 겹치면 뒤 요청은 앞 요청이 커밋한 좌석까지 세야 한다.
 * <p>
 * 구 방식(좌석 COUNT 를 조건으로 붙인 한 문장)은 다른 트랜잭션의 미커밋 변경을 못 보고, 고치는 행도 달라 둘 다 성공했다.
 * A 가 좌석을 소비한 뒤 B 가 테넌트 행 잠금 대기에 들어간 것을 pg_stat_activity 로 확인하고 나서 A 커밋 → B 결과를 본다.
 * 트랜잭션을 실제로 커밋하므로 롤백형 RepositoryTest 가 아니고, 끝에 지운다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("SEAT 좌석 — 동시 등록·활성화 직렬화(테넌트 행 잠금 → 좌석 조건 문장)")
class SeatConcurrencyTest {

    private static final int TENANT = 1;
    private static final int SUPER_ADMIN = 1;
    private static final int ADMIN = 2;
    private static final String ID_PREFIX = "seat-race-";

    @Autowired
    private UserRepository repository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbc;

    private int inactive1;
    private int inactive2;
    /** 남은 좌석 1개가 되는 상한 */
    private int limit;

    @BeforeEach
    void setUp() {
        cleanUp();
        inactive1 = insertCommitted("a", UserStatus.INACTIVE);
        inactive2 = insertCommitted("b", UserStatus.INACTIVE);
        limit = activeCount() + 1;
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM tb_user WHERE user_id LIKE ?", ID_PREFIX + "%");
    }

    private static User newUser(String suffix, UserStatus status) {
        return User.createUser(ID_PREFIX + suffix + "@example.com", TENANT, null, SignupType.LOCAL, "좌석" + suffix, status,
                0L, SUPER_ADMIN, null, null, null);
    }

    private int insertCommitted(String suffix, UserStatus status) {
        User u = newUser(suffix, status);
        transactionTemplate.executeWithoutResult(s -> repository.insertUserWithSeatCheck(u, false, 0));
        return u.getUserSeq();
    }

    private int activeCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tb_user WHERE status = 'ACTIVE' AND tenant_seq = ?", Integer.class, TENANT);
    }

    private boolean activate(int userSeq) {
        return repository.activateUser(ADMIN, userSeq, TENANT, true, limit) > 0;
    }

    private boolean register(String suffix) {
        return repository.insertUserWithSeatCheck(newUser(suffix, UserStatus.ACTIVE), true, limit);
    }

    @Test
    @DisplayName("서로 다른 두 계정 동시 활성화 — 뒤 요청은 0건, 활성 수는 상한")
    void concurrentActivations() throws Exception {
        assertRace(() -> activate(inactive1), () -> activate(inactive2));
    }

    @Test
    @DisplayName("등록과 활성화가 겹침 — 뒤 활성화는 0건, 활성 수는 상한")
    void registerThenActivate() throws Exception {
        assertRace(() -> register("c"), () -> activate(inactive2));
    }

    @Test
    @DisplayName("활성화와 등록이 겹침 — 뒤 등록은 0건, 활성 수는 상한")
    void activateThenRegister() throws Exception {
        assertRace(() -> activate(inactive1), () -> register("d"));
    }

    @Test
    @DisplayName("트랜잭션 밖 SEAT 좌석 소비는 거부(@Repository 예외 변환으로 감싸짐) — 잠금이 바로 풀려 직렬화되지 않으므로")
    void seatConsumptionOutsideTransaction_rejected() {
        assertThatThrownBy(() -> activate(inactive1)).hasRootCauseInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> register("e")).hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(activeCount()).isEqualTo(limit - 1);
    }

    /** A 가 좌석을 소비하고 커밋 전 대기 → B 가 잠금 대기에 들어간 뒤 A 커밋. A 성공·B 실패, 최종 활성 수 = 상한 */
    private void assertRace(Supplier<Boolean> first, Supplier<Boolean> second) throws Exception {
        CountDownLatch aConsumed = new CountDownLatch(1);
        CountDownLatch bWaiting = new CountDownLatch(1);

        CompletableFuture<Boolean> a = CompletableFuture.supplyAsync(() -> transactionTemplate.execute(s -> {
            boolean ok = first.get();
            aConsumed.countDown();
            await(bWaiting);
            return ok;
        }));
        await(aConsumed);

        CompletableFuture<Boolean> b = CompletableFuture.supplyAsync(() -> transactionTemplate.execute(s -> second.get()));
        waitUntilBlockedOnLock();
        bWaiting.countDown();

        assertThat(a.get(30, TimeUnit.SECONDS)).isTrue();
        assertThat(b.get(30, TimeUnit.SECONDS)).as("앞 요청이 커밋한 좌석까지 센다").isFalse();
        assertThat(activeCount()).as("상한 유지").isEqualTo(limit);
    }

    /** B 의 잠금 대기를 확인 — 확인 없이 A 를 커밋시키면 B 가 잠금 없이 지나가 경합이 재현되지 않는다. */
    private void waitUntilBlockedOnLock() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            Integer waiting = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM pg_stat_activity WHERE datname = current_database() AND wait_event_type = 'Lock'", Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("두 번째 요청이 잠금 대기에 들어가지 않았다");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("latch timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
