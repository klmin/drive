package com.thinkfree.drive.pinnedfolder.repository;

import com.thinkfree.drive.pinnedfolder.entity.PinnedFolder;
import com.thinkfree.drive.pinnedfolder.query.PinnedFolderCountQuery;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 고정 폴더 상한(3개) 동시성 — 코드 리뷰 P2 재현. 고정 폴더 2개 상태에서 두 요청이 겹칠 때 뒤 요청은 앞 요청이 커밋한 3번째까지 세야 한다.
 * <p>
 * 구 방식(고정 폴더 행 FOR UPDATE 와 COUNT 를 한 문장)은 잠금 대기 뒤에도 문장 시작 스냅샷이라 2로 읽어 4개가 저장됐다(MySQL 8.4 는 3개).
 * A 가 센 뒤 B 가 잠금 대기에 들어간 것을 pg_stat_activity 로 확인하고 나서 A 가 넣고 커밋 → B 의 개수를 본다.
 * 트랜잭션을 실제로 커밋하므로 롤백형 RepositoryTest 가 아니고, 끝에 지운다. 리소스 seq 는 FK 가 없어 임의 값.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("고정 폴더 상한 — 동시 요청 직렬화(사용자 행 잠금 → 별도 문장 COUNT)")
class PinnedFolderLimitConcurrencyTest {

    private static final int ADMIN = 2;
    private static final long RESOURCE_BASE = 990_000_000L;

    @Autowired
    private PinnedFolderRepository repository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        cleanUp();
        pinCommitted(RESOURCE_BASE + 1);
        pinCommitted(RESOURCE_BASE + 2);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM tb_pinned_folder WHERE user_seq = ? AND resource_seq > ?", ADMIN, RESOURCE_BASE);
    }

    private void pinCommitted(long resourceSeq) {
        transactionTemplate.executeWithoutResult(s -> repository.insertPinnedFolder(pinned(resourceSeq)));
    }

    private static PinnedFolder pinned(long resourceSeq) {
        return PinnedFolder.builder().userSeq(ADMIN).resourceSeq(resourceSeq).sharedByUserSeq(ADMIN).build();
    }

    private int count() {
        return repository.countByUserForUpdate(PinnedFolderCountQuery.builder().userSeq(ADMIN).build());
    }

    @Test
    @DisplayName("2개 상태에서 겹친 두 요청 — 뒤 요청은 3을 읽고, 최종 3개")
    void overlappingRequests_secondSeesCommittedThird() throws Exception {
        CountDownLatch aCounted = new CountDownLatch(1);
        CountDownLatch bWaiting = new CountDownLatch(1);

        CompletableFuture<Integer> a = CompletableFuture.supplyAsync(() -> transactionTemplate.execute(s -> {
            int n = count();
            aCounted.countDown();
            await(bWaiting);
            if (n < 3) {
                repository.insertPinnedFolder(pinned(RESOURCE_BASE + 3));
            }
            return n;
        }));
        await(aCounted);

        CompletableFuture<Integer> b = CompletableFuture.supplyAsync(() -> transactionTemplate.execute(s -> {
            int n = count();
            if (n < 3) {
                repository.insertPinnedFolder(pinned(RESOURCE_BASE + 4));
            }
            return n;
        }));
        waitUntilBlockedOnLock();
        bWaiting.countDown();

        assertThat(a.get(30, TimeUnit.SECONDS)).isEqualTo(2);
        assertThat(b.get(30, TimeUnit.SECONDS)).as("앞 요청이 커밋한 3번째까지 센다").isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_pinned_folder WHERE user_seq = ? AND resource_seq > ?", Integer.class, ADMIN, RESOURCE_BASE))
                .as("상한 3 유지").isEqualTo(3);
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
