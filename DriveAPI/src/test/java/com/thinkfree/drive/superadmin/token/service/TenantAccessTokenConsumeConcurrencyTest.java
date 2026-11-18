package com.thinkfree.drive.superadmin.token.service;

import com.thinkfree.drive.auth.utils.TokenHasher;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.superadmin.exception.SuperAdminErrorCode;
import com.thinkfree.drive.superadmin.token.entity.TenantAccessToken;
import com.thinkfree.drive.superadmin.token.repository.TenantAccessTokenRepository;
import com.thinkfree.drive.superadmin.token.result.TenantAccessTokenConsumeResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 테넌트 접근 토큰 1회용 보장(5차 패스 A11) — 같은 토큰을 동시에 소비하면 하나만 성공해야 한다.
 * <p>
 * A 가 소비(조회 → 삭제)하고 커밋 전에 멈춘 사이 B 가 조회(커밋 전이라 행이 보임) → 삭제가 A 의 행 잠금을 기다리는 것을 pg_stat_activity 로 확인하고 A 를 커밋 →
 * B 의 삭제는 0행. 구 코드는 삭제 행 수를 보지 않아 B 도 토큰을 돌려받았다(1회용 미보장).
 * 트랜잭션을 실제로 커밋하므로 롤백형 ServiceTest 가 아니고, 끝에 지운다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("테넌트 접근 토큰 — 동시 소비는 하나만 성공")
class TenantAccessTokenConsumeConcurrencyTest {

    private static final int SUPER_ADMIN = 1;
    private static final int TENANT_SEQ = 1;
    private static final String RAW = "concurrency-consume-token-0000001";

    @Autowired
    private TenantAccessTokenService tenantAccessTokenService;
    @Autowired
    private TenantAccessTokenRepository tenantAccessTokenRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        cleanUp();
        Instant now = Instant.now();
        transactionTemplate.executeWithoutResult(s -> tenantAccessTokenRepository.save(
                TenantAccessToken.of(SUPER_ADMIN, TENANT_SEQ, TokenHasher.hash(RAW), now, now.plusSeconds(300))));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM tb_tenant_access_token WHERE token_hash = ?", TokenHasher.hash(RAW));
    }

    @Test
    @DisplayName("A 소비 커밋 전 B 가 같은 토큰 소비 — A 성공, B 는 INVALID_TENANT_ACCESS_TOKEN")
    void concurrentConsume_onlyOneSucceeds() throws Exception {
        CountDownLatch aConsumed = new CountDownLatch(1);
        CountDownLatch bWaiting = new CountDownLatch(1);

        CompletableFuture<TenantAccessTokenConsumeResult> a = CompletableFuture.supplyAsync(() -> transactionTemplate.execute(s -> {
            TenantAccessTokenConsumeResult token = tenantAccessTokenService.consumeAndGet(RAW);
            aConsumed.countDown();
            await(bWaiting);
            return token;
        }));
        await(aConsumed);

        CompletableFuture<TenantAccessTokenConsumeResult> b = CompletableFuture.supplyAsync(() -> transactionTemplate.execute(s -> tenantAccessTokenService.consumeAndGet(RAW)));
        waitUntilBlockedOnLock();
        bWaiting.countDown();

        assertThat(a.get(30, TimeUnit.SECONDS).tenantSeq()).isEqualTo(TENANT_SEQ);
        assertThatThrownBy(() -> b.get(30, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOfSatisfying(ApiRuntimeException.class,
                        e -> assertThat(e.getCode()).isEqualTo(SuperAdminErrorCode.INVALID_TENANT_ACCESS_TOKEN));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_tenant_access_token WHERE token_hash = ?", Integer.class, TokenHasher.hash(RAW))).isZero();
    }

    /** B 의 삭제가 A 의 행 잠금을 기다리는지 확인 — 확인 없이 A 를 커밋시키면 B 가 조회 단계에서 null 로 끝나 경합이 재현되지 않는다. */
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
        throw new AssertionError("두 번째 소비가 잠금 대기에 들어가지 않았다");
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
