package com.thinkfree.drive.cleanup.storage;

import com.thinkfree.drive.cleanup.storage.constants.StorageDeletionConstants;
import com.thinkfree.drive.cleanup.storage.service.StorageDeletionProcessor;
import com.thinkfree.drive.event.resource.ResourceDeletedEvent;
import com.thinkfree.drive.storage.properties.S3ConnectionProperty;
import com.thinkfree.drive.storage.properties.StorageProperty;
import com.thinkfree.drive.storage.result.StorageDeleteObjectsResult;
import com.thinkfree.drive.storage.service.S3StorageService;
import com.thinkfree.drive.storage.service.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import com.thinkfree.drive.common.TestS3Container;
import org.testcontainers.containers.GenericContainer;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

/**
 * 스토리지 삭제 대기열 대량 검증 — 실제 S3 호환 스토리지(MinIO 컨테이너)와 Testcontainers PG 로
 * 등록(리스너, 같은 트랜잭션) → 처리(1000개 묶음 반복) → 실패 백오프·데드레터·복구까지 확인한다.
 * <p>
 * 스케줄은 테스트 설정에서 꺼져 있어(cron "-") 처리기를 직접 부른다. 스토리지 빈은 스파이로 감싸 호출 횟수·묶음 크기를 보고,
 * 실패 시나리오에서만 특정 키를 실패로 돌려준다(나머지는 실제 삭제).
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("스토리지 삭제 대기열 — 대량 등록·처리·실패 재시도(MinIO)")
class StorageDeletionBulkIntegrationTest {

    private static final String ACCESS_KEY = "sd-test-access";
    private static final String SECRET_KEY = "sd-test-secret-key";
    private static final String BUCKET = "sd-test-bucket";
    private static final String PREFIX = "sd-test/";

    @SuppressWarnings("resource")
    private static final GenericContainer<?> MINIO = TestS3Container.create(ACCESS_KEY, SECRET_KEY);

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
    private StorageDeletionProcessor processor;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private S3Client s3Client;
    @Autowired
    private S3AsyncClient s3AsyncClient;
    @Autowired
    private S3ConnectionProperty connection;
    @Autowired
    private StorageProperty storageProperty;
    @MockitoSpyBean
    private StorageService storageService;

    private StorageService realStorage;
    private TransactionTemplate tx;
    private String run;
    /** 실패 시나리오에서 켜면 이름이 "-fail" 로 끝나는 키를 스토리지에 보내지 않고 실패로 돌려준다 */
    private final AtomicBoolean injectFailures = new AtomicBoolean(false);

    @BeforeEach
    void setUp() {
        realStorage = new S3StorageService(s3Client, s3AsyncClient, connection, storageProperty);
        tx = new TransactionTemplate(transactionManager);
        run = PREFIX + UUID.randomUUID() + "/";
        injectFailures.set(false);
        // processDue 는 대기열 전체를 처리하고 이 테스트는 처리 건수·묶음 크기를 단언하므로, 같은 DB 를 쓴 앞 테스트가 남긴 행을 먼저 비운다
        jdbc.update("DELETE FROM tb_storage_deletion");
        doAnswer(invocation -> {
            List<String> keys = invocation.getArgument(0);
            if (!injectFailures.get()) {
                return invocation.callRealMethod();
            }
            // 실패 대상이 아닌 키는 실제 구현(스파이가 아닌 별도 인스턴스)으로 지우고, 실패 대상 키만 실패로 합친다
            List<String> passThrough = keys.stream().filter(k -> !k.endsWith("-fail")).toList();
            Map<String, String> failed = new HashMap<>(realStorage.deleteObjectsReportingFailures(passThrough).failedKeys());
            keys.stream().filter(k -> k.endsWith("-fail")).forEach(k -> failed.put(k, "SimulatedError: injected"));
            return new StorageDeleteObjectsResult(failed);
        }).when(storageService).deleteObjectsReportingFailures(anyList());
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM tb_storage_deletion WHERE storage_key LIKE ?", PREFIX + "%");
    }

    // ------------------------------------------------------------------ 도우미

    private List<String> keys(String name, int count) {
        return IntStream.range(0, count).mapToObj(i -> run + name + "-" + i).toList();
    }

    private void upload(List<String> keys) {
        keys.parallelStream().forEach(key ->
                s3Client.putObject(req -> req.bucket(BUCKET).key(key), RequestBody.fromString("x")));
    }

    private int objectCount() {
        return (int) s3Client.listObjectsV2Paginator(req -> req.bucket(BUCKET).prefix(run))
                             .contents().stream().map(S3Object::key).count();
    }

    private void publishInTransaction(List<String> keys) {
        tx.executeWithoutResult(status -> publisher.publishEvent(new ResourceDeletedEvent(keys)));
    }

    private int rowCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tb_storage_deletion WHERE storage_key LIKE ?", Integer.class, run + "%");
    }

    private void makeDue(String keySuffixLike, int attemptCount) {
        jdbc.update("UPDATE tb_storage_deletion SET next_attempt_date = ?, attempt_count = ? WHERE storage_key LIKE ?",
                Timestamp.from(Instant.now().minusSeconds(1)), attemptCount, run + "%" + keySuffixLike);
    }

    /** 스파이에 기록된 처리 호출의 키 수 목록 */
    private List<Integer> deleteCallSizes() {
        List<Integer> sizes = new ArrayList<>();
        mockingDetails(storageService).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("deleteObjectsReportingFailures"))
                .forEach(i -> sizes.add(((List<?>) i.getArgument(0)).size()));
        return sizes;
    }

    // ------------------------------------------------------------------ 시나리오

    @Test
    @DisplayName("대량 — 실제 객체 2,500 + 없는 키 300 + 중복 등록 200 = 3,000행: 1000키 묶음 3번으로 전부 지우고 대기열도 비운다, 롤백된 등록은 없다")
    void bulkRegisterAndProcess() {
        List<String> existing = keys("obj", 2_500);
        List<String> missing = keys("missing", 300);
        long uploadStart = System.nanoTime();
        upload(existing);
        long uploadMs = (System.nanoTime() - uploadStart) / 1_000_000;
        assertThat(objectCount()).isEqualTo(2_500);

        // 같은 이벤트 안의 중복·빈 키는 하나로/버림
        List<String> first = new ArrayList<>(existing);
        first.addAll(missing);
        first.addAll(existing.subList(0, 10));
        first.add("");
        publishInTransaction(first);
        assertThat(rowCount()).as("2,500 + 300 (같은 이벤트 중복 10·빈 키 제외)").isEqualTo(2_800);

        // 다른 트랜잭션에서 같은 키 200개 재등록 → 행은 따로 생김
        publishInTransaction(existing.subList(0, 200));
        assertThat(rowCount()).isEqualTo(3_000);

        // 롤백된 트랜잭션의 등록은 남지 않는다
        List<String> rolledBack = keys("rolledback", 100);
        tx.executeWithoutResult(status -> {
            publisher.publishEvent(new ResourceDeletedEvent(rolledBack));
            status.setRollbackOnly();
        });
        assertThat(rowCount()).as("롤백 등록 없음").isEqualTo(3_000);

        // 처리
        clearInvocations(storageService);
        long processStart = System.nanoTime();
        int deleted = processor.processDue();
        long processMs = (System.nanoTime() - processStart) / 1_000_000;

        assertThat(deleted).isEqualTo(3_000);
        assertThat(rowCount()).as("대기열 비움").isZero();
        assertThat(objectCount()).as("스토리지 객체 전부 삭제").isZero();
        // 행 3,000 = 1000행 묶음 3개. 세 번째 묶음은 남은 800행 + 재등록 200행(이미 지운 키 → 없는 키도 성공)
        assertThat(deleteCallSizes()).as("스토리지 호출당 키 수").containsExactly(1_000, 1_000, 1_000);
        System.out.printf("[StorageDeletionBulk] upload 2,500: %d ms, process 3,000 rows: %d ms%n", uploadMs, processMs);
    }

    @Test
    @DisplayName("등록 실패 — 커밋 직전 등록 INSERT 가 실패하면(키 1024자 초과) 예외가 올라오고 같은 트랜잭션의 비즈니스 쓰기·다른 등록도 롤백된다")
    void registerFailureRollsBackBusinessTransaction() {
        String businessMarker = run + "business-write";
        String validKey = run + "valid";
        String tooLongKey = run + "z".repeat(1100);

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> tx.executeWithoutResult(status -> {
            // 비즈니스 쓰기 흉내 — 같은 트랜잭션의 일반 DB 쓰기
            jdbc.update("INSERT INTO tb_storage_deletion (storage_key, attempt_count, next_attempt_date, register_date) VALUES (?, 0, now(), now())", businessMarker);
            publisher.publishEvent(new ResourceDeletedEvent(List.of(validKey)));
            publisher.publishEvent(new ResourceDeletedEvent(List.of(tooLongKey)));
        }));

        assertThat(thrown).as("등록 실패가 호출부까지 올라옴").isNotNull();
        System.out.println("[StorageDeletionBulk] register failure: " + thrown.getClass().getName());
        assertThat(rowCount()).as("비즈니스 쓰기·정상 등록까지 전부 롤백").isZero();
    }

    @Test
    @DisplayName("실패 — 일부 키 실패: 성공 행만 지우고, 실패 행은 횟수+1·1분 뒤·사유. 같은 실행·바로 다음 실행에서 다시 시도하지 않는다")
    void partialFailureBacksOff() {
        List<String> ok = keys("ok", 1_350);
        List<String> fail = keys("x", 150).stream().map(k -> k + "-fail").toList();
        upload(ok);
        List<String> all = new ArrayList<>(ok);
        all.addAll(fail);
        publishInTransaction(all);
        assertThat(rowCount()).isEqualTo(1_500);

        injectFailures.set(true);
        clearInvocations(storageService);
        Instant before = Instant.now();
        int deleted = processor.processDue();

        assertThat(deleted).isEqualTo(1_350);
        assertThat(objectCount()).as("성공 키 객체는 실제로 지워짐").isZero();
        assertThat(deleteCallSizes()).as("1,500행 = 1000 + 500, 실패 행은 같은 실행에서 다시 조회되지 않음").containsExactly(1_000, 500);

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT storage_key, attempt_count, next_attempt_date, last_error FROM tb_storage_deletion WHERE storage_key LIKE ?", run + "%");
        assertThat(rows).hasSize(150).allSatisfy(r -> {
            assertThat((String) r.get("storage_key")).endsWith("-fail");
            assertThat(r.get("attempt_count")).isEqualTo(1);
            assertThat(r.get("last_error")).isEqualTo("SimulatedError: injected");
            Instant next = ((Timestamp) r.get("next_attempt_date")).toInstant();
            assertThat(next).as("백오프 2^0 = 1분").isBetween(before.plusSeconds(59), Instant.now().plusSeconds(61));
        });

        clearInvocations(storageService);
        assertThat(processor.processDue()).as("바로 다시 돌려도 아직 때가 아님").isZero();
        assertThat(deleteCallSizes()).isEmpty();
    }

    @Test
    @DisplayName("백오프·데드레터·복구 — 실패 전 3회면 8분 뒤, 9회에서 실패하면 10회로 데드레터(조회 제외), 0으로 되돌리면 다시 처리")
    void backoffExhaustionAndRecovery() {
        List<String> fail = keys("y", 20).stream().map(k -> k + "-fail").toList();
        publishInTransaction(fail);
        injectFailures.set(true);

        // 실패 전 3회 → 2^3 = 8분
        makeDue("-fail", 3);
        Instant before = Instant.now();
        processor.processDue();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT attempt_count, next_attempt_date FROM tb_storage_deletion WHERE storage_key LIKE ?", run + "%");
        assertThat(rows).hasSize(20).allSatisfy(r -> {
            assertThat(r.get("attempt_count")).isEqualTo(4);
            assertThat(((Timestamp) r.get("next_attempt_date")).toInstant()).isBetween(before.plusSeconds(8 * 60 - 1), Instant.now().plusSeconds(8 * 60 + 1));
        });

        // 실패 전 9회 → 이번 실패로 10회 = 데드레터
        makeDue("-fail", StorageDeletionConstants.MAX_ATTEMPT_COUNT - 1);
        processor.processDue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_storage_deletion WHERE storage_key LIKE ? AND attempt_count = ?",
                Integer.class, run + "%", StorageDeletionConstants.MAX_ATTEMPT_COUNT)).isEqualTo(20);

        // 시각이 지나도 데드레터는 조회되지 않는다
        jdbc.update("UPDATE tb_storage_deletion SET next_attempt_date = ? WHERE storage_key LIKE ?", Timestamp.from(Instant.now().minusSeconds(3600)), run + "%");
        clearInvocations(storageService);
        assertThat(processor.processDue()).isZero();
        assertThat(deleteCallSizes()).as("데드레터는 스토리지로 보내지 않음").isEmpty();
        assertThat(rowCount()).as("행은 남아 있음(수동 확인)").isEqualTo(20);

        // 원인 해결 + attempt_count = 0 → 다시 처리돼 지워진다
        injectFailures.set(false);
        makeDue("-fail", 0);
        assertThat(processor.processDue()).isEqualTo(20);
        assertThat(rowCount()).isZero();
        assertThat(mockingDetails(storageService).getInvocations()).isNotEmpty();
    }
}
