package com.thinkfree.drive.cleanup.storage;

import com.thinkfree.drive.cleanup.storage.service.StorageDeletionProcessor;
import com.thinkfree.drive.event.resource.ResourceDeletedEvent;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

/**
 * 실제 스케줄 검증 — 테스트 설정의 "-" 를 운영 기본값(30초)으로 되돌려 SchedulerService#processStorageDeletions 를 진짜 스케줄·진짜 ShedLock 으로 돌린다.
 * 소량(10개)·대량(3,000개) 등록 뒤 다음 회차(최대 30초) 안에 스토리지·대기열이 비는지, 회차가 매분 0·30초에 빠짐없이 도는지(최소 잠금 10초가 회차를 건너뛰지 않는지) 본다.
 * 약 1분 30초 걸린다.
 */
@SpringBootTest(properties = "drive.scheduler.storage-deletion.cron=*/30 * * * * *")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("스토리지 삭제 스케줄 — 30초 회차·ShedLock 실제 동작(MinIO)")
class StorageDeletionSchedulerTimingIntegrationTest {

    private static final String ACCESS_KEY = "sd-timing-access";
    private static final String SECRET_KEY = "sd-timing-secret-key";
    private static final String BUCKET = "sd-timing-bucket";
    private static final String PREFIX = "sd-timing/";

    @SuppressWarnings("resource")
    private static final GenericContainer<?> MINIO = new GenericContainer<>("bitnami/minio:2025")
            .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000).withStartupTimeout(Duration.ofMinutes(2)));

    static {
        MINIO.start();
    }

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("storage.type", () -> "ceph");
        registry.add("storage.connection.endpoint", () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        registry.add("storage.connection.access-key", () -> ACCESS_KEY);
        registry.add("storage.connection.secret-key", () -> SECRET_KEY);
        registry.add("storage.connection.bucket-name", () -> BUCKET);
    }

    @Autowired
    private ApplicationEventPublisher publisher;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private S3Client s3Client;
    @MockitoSpyBean
    private StorageDeletionProcessor processor;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM tb_storage_deletion WHERE storage_key LIKE ?", PREFIX + "%");
    }

    private void upload(List<String> keys) {
        keys.parallelStream().forEach(key -> s3Client.putObject(req -> req.bucket(BUCKET).key(key), RequestBody.fromString("x")));
    }

    private long objectCount(String prefix) {
        return s3Client.listObjectsV2Paginator(req -> req.bucket(BUCKET).prefix(prefix)).contents().stream().count();
    }

    private int rowCount(String prefix) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tb_storage_deletion WHERE storage_key LIKE ?", Integer.class, prefix + "%");
    }

    /** 등록 → 큐가 빌 때까지 걸린 시간 */
    private Duration registerAndAwaitEmpty(String prefix, int count) {
        List<String> keys = IntStream.range(0, count).mapToObj(i -> prefix + "obj-" + i).toList();
        upload(keys);
        assertThat(objectCount(prefix)).isEqualTo(count);

        new TransactionTemplate(transactionManager).executeWithoutResult(s -> publisher.publishEvent(new ResourceDeletedEvent(keys)));
        Instant registered = Instant.now();
        assertThat(rowCount(prefix)).isEqualTo(count);

        Awaitility.await().atMost(Duration.ofSeconds(45)).pollInterval(Duration.ofMillis(200))
                  .until(() -> rowCount(prefix) == 0);
        Duration took = Duration.between(registered, Instant.now());
        assertThat(objectCount(prefix)).as("스토리지 객체도 지워짐").isZero();
        return took;
    }

    @Test
    @DisplayName("소량 10개·대량 3,000개 — 다음 회차(30초 이내)에 스토리지·대기열이 비고, 회차는 0·30초마다 빠짐없이 돈다")
    void scheduledRunsDeleteWithinOneInterval() {
        List<Instant> runs = new CopyOnWriteArrayList<>();
        doAnswer(inv -> {
            runs.add(Instant.now());
            return inv.callRealMethod();
        }).when(processor).processDue();

        String run = PREFIX + UUID.randomUUID() + "/";
        Duration small = registerAndAwaitEmpty(run + "small/", 10);
        Duration large = registerAndAwaitEmpty(run + "large/", 3_000);

        // 회차를 하나 더 확인할 수 있게 다음 회차까지 기다린다
        int seen = runs.size();
        Awaitility.await().atMost(Duration.ofSeconds(35)).until(() -> runs.size() > seen);

        Map<String, Object> lock = jdbc.queryForMap("SELECT locked_at, lock_until, locked_by FROM shedlock WHERE name = 'storage_deletion'");
        Duration lockHeld = Duration.between(((Timestamp) lock.get("locked_at")).toInstant(), ((Timestamp) lock.get("lock_until")).toInstant());

        List<String> runSeconds = new ArrayList<>();
        for (Instant r : runs) {
            runSeconds.add(r.atOffset(ZoneOffset.UTC).toLocalTime().toString());
        }
        List<Long> gapsMs = new ArrayList<>();
        for (int i = 1; i < runs.size(); i++) {
            gapsMs.add(Duration.between(runs.get(i - 1), runs.get(i)).toMillis());
        }
        System.out.printf("[StorageDeletionTiming] small 10: %d ms, large 3,000: %d ms%n", small.toMillis(), large.toMillis());
        System.out.printf("[StorageDeletionTiming] runs (UTC): %s%n", runSeconds);
        System.out.printf("[StorageDeletionTiming] gaps ms: %s%n", gapsMs);
        System.out.printf("[StorageDeletionTiming] last lock held: %d ms by %s%n", lockHeld.toMillis(), lock.get("locked_by"));

        assertThat(small).as("소량 — 다음 회차 안에 삭제").isLessThanOrEqualTo(Duration.ofSeconds(32));
        assertThat(large).as("대량 — 다음 회차 안에 삭제(처리 시간 포함)").isLessThanOrEqualTo(Duration.ofSeconds(35));
        assertThat(runs).as("회차 3번 이상").hasSizeGreaterThanOrEqualTo(3);
        assertThat(runs).as("매분 0·30초에 시작(지연 2초 이내)").allSatisfy(r -> {
            int sec = r.atOffset(ZoneOffset.UTC).getSecond();
            assertThat(sec % 30).isLessThan(2);
        });
        assertThat(gapsMs).as("회차 간격 약 30초 — 최소 잠금 10초가 회차를 건너뛰지 않음").allSatisfy(g -> assertThat(g).isBetween(28_000L, 32_000L));
        assertThat(lockHeld).as("짧은 실행이면 최소 잠금 10초만 유지").isBetween(Duration.ofSeconds(9), Duration.ofSeconds(11));
    }
}
