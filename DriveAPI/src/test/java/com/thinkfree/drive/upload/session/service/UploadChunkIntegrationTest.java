package com.thinkfree.drive.upload.session.service;

import com.thinkfree.drive.application.upload.UploadSessionUseCase;
import com.thinkfree.drive.application.upload.command.UploadSessionStartCommand;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.notification.service.NotificationRetryService;
import com.thinkfree.drive.postprocess.orchestrator.PostProcessingOrchestratorRetry;
import com.thinkfree.drive.resource.enums.ResourceSaveType;
import com.thinkfree.drive.storage.exception.StorageErrorCode;
import com.thinkfree.drive.storage.exception.StorageException;
import com.thinkfree.drive.storage.result.StoragePart;
import com.thinkfree.drive.storage.service.StorageService;
import com.thinkfree.drive.upload.session.enums.UploadSessionFailReason;
import com.thinkfree.drive.upload.session.enums.UploadSessionStatus;
import com.thinkfree.drive.upload.session.exception.UploadSessionErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import com.thinkfree.drive.global.security.CustomUserDetails;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;

/**
 * 청크 업로드 API(세션 생성 → 파트 PUT → 상태 조회 → complete / 취소) 전 구간 — PostgreSQL Testcontainers.
 * 스토리지는 메모리 멀티파트 가짜(파트 크기·번호만 기억, 바이트는 세기만 한다)로, S3 계약 자체는 S3StorageServiceMultipartContractTest 가 본다.
 * 소유자·요청자 = ADMIN(2), 루트 업로드. 33MB = 16MB·16MB·1MB 세 파트.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("청크 업로드 API — 이어 올리기·complete 멱등·동시 complete·취소·만료·검증")
class UploadChunkIntegrationTest {

    private static final int ADMIN = 2;
    private static final int OTHER = 1;
    private static final long MB = 1024L * 1024;
    private static final long SIZE = 33 * MB;
    private static final long PART = 16 * MB;

    @Autowired
    private UploadSessionUseCase useCase;
    @Autowired
    private UploadSessionCleanupService cleanupService;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private StorageService storageService;
    @MockitoBean
    private PostProcessingOrchestratorRetry postProcessingOrchestratorRetry;
    @MockitoBean
    private ResourceAuditService resourceAuditService;
    @MockitoBean
    private NotificationRetryService notificationRetryService;

    private final FakeMultipart fake = new FakeMultipart();
    private Long maxSeqBefore;
    private Long quotaBefore;
    private Long totalQuotaBefore;
    private Instant testStart;

    @BeforeEach
    void setUp() {
        testStart = Instant.now().minusSeconds(1);
        maxSeqBefore = jdbc.queryForObject("SELECT COALESCE(MAX(resource_seq), 0) FROM tb_resource", Long.class);
        quotaBefore = jdbc.queryForObject("SELECT use_quota_byte FROM tb_user WHERE user_seq = ?", Long.class, ADMIN);
        totalQuotaBefore = jdbc.queryForObject("SELECT total_quota_byte FROM tb_user WHERE user_seq = ?", Long.class, ADMIN);
        jdbc.update("UPDATE tb_user SET use_quota_byte = 1000, total_quota_byte = ? WHERE user_seq = ?", 10L * 1024 * MB, ADMIN);
        loginAs(ADMIN);   // 요청 스레드처럼 — 리소스 행 등록자는 JPA auditing 이 시큐리티 사용자에서 채운다

        given(storageService.createMultipartUpload(anyString(), any())).willAnswer(inv -> fake.create(inv.getArgument(0)));
        given(storageService.uploadPart(anyString(), anyString(), anyInt(), any(InputStream.class), anyLong()))
                .willAnswer(inv -> fake.uploadPart(inv.getArgument(1), inv.getArgument(2), inv.getArgument(3), inv.getArgument(4)));
        given(storageService.listParts(anyString(), anyString())).willAnswer(inv -> fake.listParts(inv.getArgument(1)));
        willAnswer(inv -> {
            fake.complete(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2));
            return null;
        }).given(storageService).completeMultipartUpload(anyString(), anyString(), anyList());
        given(storageService.headContentLength(anyString())).willAnswer(inv -> fake.head(inv.getArgument(0)));
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        Timestamp since = Timestamp.from(testStart);
        jdbc.update("DELETE FROM tb_storage_deletion WHERE storage_key IN (SELECT storage_key FROM tb_upload_session WHERE user_seq = ? AND create_date >= ?)", ADMIN, since);
        jdbc.update("DELETE FROM tb_upload_session WHERE user_seq = ? AND create_date >= ?", ADMIN, since);
        jdbc.update("DELETE FROM tb_resource WHERE user_seq = ? AND resource_seq > ?", ADMIN, maxSeqBefore);
        jdbc.update("UPDATE tb_user SET use_quota_byte = ?, total_quota_byte = ? WHERE user_seq = ?", quotaBefore, totalQuotaBefore, ADMIN);
    }

    // ------------------------------------------------------------------ 성공·이어 올리기

    @Test
    @DisplayName("중간에 끊긴 업로드 — GET 으로 빠진 파트만 다시 보내고 complete → 파일 1개·사용량 +크기·세션 COMPLETED")
    void resumeAndComplete() {
        var started = start("big.bin", SIZE);
        assertThat(started.partSize()).isEqualTo(PART);
        assertThat(started.partCount()).isEqualTo(3);

        put(started.sessionId(), 1, PART);
        put(started.sessionId(), 3, SIZE - 2 * PART);
        // 연결이 끊겼다 다시 옴 — 올라간 파트 확인
        assertThat(useCase.status(started.sessionId(), ADMIN).uploadedParts()).containsExactly(1, 3);
        put(started.sessionId(), 2, PART);

        var completed = useCase.complete(started.sessionId(), ADMIN);

        assertThat(completed.replayed()).isFalse();
        Map<String, Object> row = jdbc.queryForMap("SELECT resource_name, size_byte, resource_save_name FROM tb_resource WHERE resource_seq = ?",
                completed.result().resourceSeq());
        assertThat(row.get("resource_name")).isEqualTo("big.bin");
        assertThat(row.get("size_byte")).isEqualTo(SIZE);
        assertThat((String) row.get("resource_save_name")).endsWith("/" + started.sessionId());
        assertThat(quota()).isEqualTo(1000 + SIZE);
        assertThat(sessionStatus(started.sessionId())).isEqualTo("COMPLETED");
        assertThat(useCase.list(UploadSessionStatus.PENDING, ADMIN)).noneMatch(s -> s.uploadSessionId().equals(started.sessionId()));
    }

    @Test
    @DisplayName("complete 재요청 — 이전 결과를 그대로 돌려주고 파일·사용량은 한 번만")
    void completeIsIdempotent() {
        var started = uploadAll("again.bin");
        var first = useCase.complete(started.sessionId(), ADMIN);
        var second = useCase.complete(started.sessionId(), ADMIN);

        assertThat(second.replayed()).isTrue();
        assertThat(second.result().resourceSeq()).isEqualTo(first.result().resourceSeq());
        assertThat(second.result().resourceName()).isEqualTo(first.result().resourceName());
        assertThat(newRows()).isEqualTo(1);
        assertThat(quota()).isEqualTo(1000 + SIZE);
    }

    @Test
    @DisplayName("동시 complete(중복 클릭) — 하나만 반영되고 다른 하나는 진행 중(409) 또는 같은 결과")
    void concurrentComplete() throws Exception {
        var started = uploadAll("dup-click.bin");
        fake.completeDelayMillis = 300;   // 첫 요청이 조립 중일 때 두 번째가 들어오게
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    go.await();
                    loginAs(ADMIN);
                    try {
                        return useCase.complete(started.sessionId(), ADMIN);
                    } catch (ApiRuntimeException e) {
                        return e.getCode();
                    }
                }));
            }
            go.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> f : results) {
                outcomes.add(f.get(30, TimeUnit.SECONDS));
            }
            long succeeded = outcomes.stream().filter(o -> o instanceof UploadChunkService.Completed c && !c.replayed()).count();
            assertThat(succeeded).isEqualTo(1);
            assertThat(outcomes).allMatch(o -> o instanceof UploadChunkService.Completed || o == UploadSessionErrorCode.UPLOAD_SESSION_IN_PROGRESS);
        } finally {
            pool.shutdownNow();
        }
        assertThat(newRows()).isEqualTo(1);
        assertThat(quota()).isEqualTo(1000 + SIZE);
        assertThat(fake.completeCalls.get()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ 취소·만료

    @Test
    @DisplayName("취소 — FAILED(CANCELED) + ABORT 등록, 이후 파트 PUT 은 409, 다시 취소해도 성공")
    void cancel() {
        var started = start("cancel.bin", SIZE);
        put(started.sessionId(), 1, PART);

        assertThat(useCase.cancel(started.sessionId(), ADMIN)).isEqualTo(UploadSessionStatus.FAILED);
        assertThat(failReason(started.sessionId())).isEqualTo("CANCELED");
        assertThat(abortRows(started.sessionId())).isEqualTo(1);

        assertThatThrownBy(() -> put(started.sessionId(), 2, PART))
                .isInstanceOfSatisfying(ApiRuntimeException.class, e -> assertThat(e.getCode()).isEqualTo(UploadSessionErrorCode.UPLOAD_SESSION_FAILED));
        assertThat(useCase.cancel(started.sessionId(), ADMIN)).isEqualTo(UploadSessionStatus.FAILED);
        assertThat(abortRows(started.sessionId())).isEqualTo(1);
        assertThatThrownBy(() -> useCase.complete(started.sessionId(), ADMIN))
                .isInstanceOfSatisfying(ApiRuntimeException.class, e -> assertThat(e.getCode()).isEqualTo(UploadSessionErrorCode.UPLOAD_SESSION_GONE));
    }

    @Test
    @DisplayName("스케줄러 만료 뒤 — 파트 PUT 409, complete 410")
    void expiredByScheduler() {
        var started = start("expire.bin", SIZE);
        put(started.sessionId(), 1, PART);
        jdbc.update("UPDATE tb_upload_session SET last_activity = now() - interval '2 hours' WHERE upload_session_id = ?", started.sessionId());
        cleanupService.expirePendingBatch();

        assertThat(failReason(started.sessionId())).isEqualTo("EXPIRED");
        assertThatThrownBy(() -> put(started.sessionId(), 2, PART))
                .isInstanceOfSatisfying(ApiRuntimeException.class, e -> assertThat(e.getCode()).isEqualTo(UploadSessionErrorCode.UPLOAD_SESSION_FAILED));
        assertThatThrownBy(() -> useCase.complete(started.sessionId(), ADMIN))
                .isInstanceOfSatisfying(ApiRuntimeException.class, e -> assertThat(e.getCode()).isEqualTo(UploadSessionErrorCode.UPLOAD_SESSION_GONE));
    }

    // ------------------------------------------------------------------ 검증

    @Test
    @DisplayName("빠진 파트로 complete — 400 UPLOAD_PARTS_INCOMPLETE, 세션 FAILED(VALIDATION) + ABORT, 파일·사용량 변화 없음")
    void completeWithMissingPart() {
        var started = start("missing.bin", SIZE);
        put(started.sessionId(), 1, PART);
        put(started.sessionId(), 3, SIZE - 2 * PART);

        assertThatThrownBy(() -> useCase.complete(started.sessionId(), ADMIN))
                .isInstanceOfSatisfying(ApiRuntimeException.class, e -> assertThat(e.getCode()).isEqualTo(UploadSessionErrorCode.UPLOAD_PARTS_INCOMPLETE));
        assertThat(sessionStatus(started.sessionId())).isEqualTo("FAILED");
        assertThat(failReason(started.sessionId())).isEqualTo("VALIDATION");
        assertThat(abortRows(started.sessionId())).isEqualTo(1);
        assertThat(newRows()).isZero();
        assertThat(quota()).isEqualTo(1000);
        assertThat(fake.completeCalls.get()).isZero();
    }

    @Test
    @DisplayName("파트 PUT 검증 — 길이 없음 411, 크기 다름·번호 범위 밖 400, 세션은 PENDING 그대로")
    void partValidation() {
        var started = start("valid.bin", SIZE);

        assertThatThrownBy(() -> useCase.uploadPart(started.sessionId(), 1, -1, zeros(PART), ADMIN))
                .isInstanceOfSatisfying(ApiRuntimeException.class, e -> assertThat(e.getCode()).isEqualTo(UploadSessionErrorCode.UPLOAD_PART_LENGTH_REQUIRED));
        assertThatThrownBy(() -> put(started.sessionId(), 1, PART - 1))
                .isInstanceOfSatisfying(ApiRuntimeException.class, e -> assertThat(e.getCode()).isEqualTo(UploadSessionErrorCode.UPLOAD_PART_INVALID));
        assertThatThrownBy(() -> put(started.sessionId(), 3, PART))
                .isInstanceOfSatisfying(ApiRuntimeException.class, e -> assertThat(e.getCode()).isEqualTo(UploadSessionErrorCode.UPLOAD_PART_INVALID));
        assertThatThrownBy(() -> put(started.sessionId(), 4, 1))
                .isInstanceOfSatisfying(ApiRuntimeException.class, e -> assertThat(e.getCode()).isEqualTo(UploadSessionErrorCode.UPLOAD_PART_INVALID));
        assertThatThrownBy(() -> put(started.sessionId(), 0, 1))
                .isInstanceOfSatisfying(ApiRuntimeException.class, e -> assertThat(e.getCode()).isEqualTo(UploadSessionErrorCode.UPLOAD_PART_INVALID));
        assertThat(sessionStatus(started.sessionId())).isEqualTo("PENDING");
        assertThat(fake.listParts(fake.uploadIdOf(started.sessionId()))).isEmpty();
    }

    @Test
    @DisplayName("스토리지에서 업로드가 사라짐(NoSuchUpload) — 세션 FAILED(NO_SUCH_UPLOAD), 409")
    void noSuchUploadOnPart() {
        var started = start("gone.bin", SIZE);
        fake.uploads.remove(fake.uploadIdOf(started.sessionId()));

        assertThatThrownBy(() -> put(started.sessionId(), 1, PART))
                .isInstanceOfSatisfying(ApiRuntimeException.class, e -> assertThat(e.getCode()).isEqualTo(UploadSessionErrorCode.UPLOAD_SESSION_FAILED));
        assertThat(failReason(started.sessionId())).isEqualTo(UploadSessionFailReason.NO_SUCH_UPLOAD.name());
    }

    @Test
    @DisplayName("다른 사용자 — 조회·파트·complete·취소 모두 404(존재 숨김)")
    void otherUserCannotSee() {
        var started = start("mine.bin", SIZE);
        UUID id = started.sessionId();

        for (Runnable call : List.<Runnable>of(
                () -> useCase.status(id, OTHER),
                () -> useCase.uploadPart(id, 1, PART, zeros(PART), OTHER),
                () -> useCase.complete(id, OTHER),
                () -> useCase.cancel(id, OTHER))) {
            assertThatThrownBy(call::run)
                    .isInstanceOfSatisfying(ApiRuntimeException.class, e -> assertThat(e.getCode()).isEqualTo(UploadSessionErrorCode.UPLOAD_SESSION_NOT_FOUND));
        }
        assertThat(sessionStatus(id)).isEqualTo("PENDING");
        assertThat(useCase.list(UploadSessionStatus.PENDING, OTHER)).noneMatch(s -> s.uploadSessionId().equals(id));
    }

    @Test
    @DisplayName("단일 요청 기준(32MB) 미만 — 400, 세션을 만들지 않는다")
    void smallFileRejected() {
        assertThatThrownBy(() -> start("small.bin", 32 * MB - 1))
                .isInstanceOfSatisfying(ApiRuntimeException.class, e -> assertThat(e.getCode()).isEqualTo(UploadSessionErrorCode.UPLOAD_PART_INVALID));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_upload_session WHERE user_seq = ? AND create_date >= ?", Integer.class,
                ADMIN, Timestamp.from(testStart))).isZero();
    }

    // ------------------------------------------------------------------ helpers

    private static void loginAs(int userSeq) {
        CustomUserDetails details = Mockito.mock(CustomUserDetails.class);
        given(details.getUserSeq()).willReturn(userSeq);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(details, null, List.of()));
    }

    private UploadChunkService.Started start(String fileName, long size) {
        return useCase.start(new UploadSessionStartCommand(null, null, null, fileName, size, ResourceSaveType.DUPLICATE), ADMIN);
    }

    private UploadChunkService.Started uploadAll(String fileName) {
        var started = start(fileName, SIZE);
        put(started.sessionId(), 1, PART);
        put(started.sessionId(), 2, PART);
        put(started.sessionId(), 3, SIZE - 2 * PART);
        return started;
    }

    private void put(UUID sessionId, int partNumber, long length) {
        useCase.uploadPart(sessionId, partNumber, length, zeros(length), ADMIN);
    }

    private long quota() {
        return jdbc.queryForObject("SELECT use_quota_byte FROM tb_user WHERE user_seq = ?", Long.class, ADMIN);
    }

    private int newRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tb_resource WHERE user_seq = ? AND resource_seq > ?", Integer.class, ADMIN, maxSeqBefore);
    }

    private String sessionStatus(UUID id) {
        return jdbc.queryForObject("SELECT status FROM tb_upload_session WHERE upload_session_id = ?", String.class, id);
    }

    private String failReason(UUID id) {
        return jdbc.queryForObject("SELECT fail_reason FROM tb_upload_session WHERE upload_session_id = ?", String.class, id);
    }

    private int abortRows(UUID id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tb_storage_deletion d JOIN tb_upload_session s ON d.storage_key = s.storage_key AND d.upload_id = s.upload_id "
                + "WHERE s.upload_session_id = ? AND d.action = 'ABORT_MULTIPART'", Integer.class, id);
    }

    /** length 바이트의 0 — 메모리에 담지 않고 센다 */
    private static InputStream zeros(long length) {
        return new InputStream() {
            long left = length;

            @Override
            public int read() {
                if (left <= 0) {
                    return -1;
                }
                left--;
                return 0;
            }

            @Override
            public int read(byte[] b, int off, int len) {
                if (left <= 0) {
                    return -1;
                }
                int n = (int) Math.min(len, left);
                left -= n;
                return n;
            }
        };
    }

    /** 메모리 멀티파트 — key·uploadId·파트 크기만 기억한다. */
    static final class FakeMultipart {
        final Map<String, Map<Integer, Long>> uploads = new ConcurrentHashMap<>();
        final Map<String, String> uploadKeys = new ConcurrentHashMap<>();
        final Map<String, Long> objects = new ConcurrentHashMap<>();
        final AtomicInteger completeCalls = new AtomicInteger();
        volatile long completeDelayMillis;

        String create(String key) {
            String uploadId = "up-" + UUID.randomUUID();
            uploads.put(uploadId, new ConcurrentHashMap<>());
            uploadKeys.put(uploadId, key);
            return uploadId;
        }

        String uploadIdOf(UUID sessionId) {
            return uploadKeys.entrySet().stream().filter(e -> e.getValue().endsWith("/" + sessionId)).map(Map.Entry::getKey).findFirst().orElseThrow();
        }

        String uploadPart(String uploadId, int partNumber, InputStream in, long length) throws IOException {
            Map<Integer, Long> parts = uploads.get(uploadId);
            if (parts == null) {
                throw StorageException.type(StorageErrorCode.NO_SUCH_UPLOAD);
            }
            long read = in.transferTo(java.io.OutputStream.nullOutputStream());
            if (read != length) {
                throw new IOException("short body: " + read + " / " + length);
            }
            parts.put(partNumber, length);
            return "\"etag-" + partNumber + "\"";
        }

        List<StoragePart> listParts(String uploadId) {
            Map<Integer, Long> parts = uploads.get(uploadId);
            if (parts == null) {
                throw StorageException.type(StorageErrorCode.NO_SUCH_UPLOAD);
            }
            return parts.entrySet().stream().sorted(Map.Entry.comparingByKey())
                        .map(e -> new StoragePart(e.getKey(), "\"etag-" + e.getKey() + "\"", e.getValue())).toList();
        }

        void complete(String key, String uploadId, List<StoragePart> parts) throws InterruptedException {
            completeCalls.incrementAndGet();
            if (completeDelayMillis > 0) {
                Thread.sleep(completeDelayMillis);
            }
            Map<Integer, Long> uploaded = uploads.remove(uploadId);
            if (uploaded == null) {
                throw StorageException.type(StorageErrorCode.NO_SUCH_UPLOAD);
            }
            long size = parts.stream().sorted(Comparator.comparingInt(StoragePart::partNumber)).mapToLong(p -> uploaded.get(p.partNumber())).sum();
            objects.put(key, size);
        }

        long head(String key) {
            Long size = objects.get(key);
            if (size == null) {
                throw StorageException.type(StorageErrorCode.NO_SUCH_KEY);
            }
            return size;
        }
    }
}
