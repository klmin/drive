package com.thinkfree.drive.file.write.orchestrator;

import com.thinkfree.drive.application.file.FileUseCase;
import com.thinkfree.drive.application.file.command.FileUploadCommand;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.notification.service.NotificationRetryService;
import com.thinkfree.drive.postprocess.orchestrator.PostProcessingOrchestratorRetry;
import com.thinkfree.drive.postprocess.orchestrator.exception.PostProcessingErrorCode;
import com.thinkfree.drive.quota.exception.QuotaErrorCode;
import com.thinkfree.drive.resource.entity.Resource;
import com.thinkfree.drive.resource.enums.ResourceSaveType;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.repository.ResourceRepository;
import com.thinkfree.drive.storage.exception.StorageErrorCode;
import com.thinkfree.drive.storage.exception.StorageException;
import com.thinkfree.drive.storage.service.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 파일 업로드 OVERWRITE·DUPLICATE → FileWriteOrchestrator 전 구간 확인(5차 패스, PostgreSQL Testcontainers).
 * 메타 저장(사용량 예약 포함 한 트랜잭션) → 스토리지 → 스토리지 실패만 원복, 후처리(사용량 알림) 실패는 저장 유지, 용량 부족은 재시도 없이 IS_FULL·알림 1회.
 * <p>
 * 메타 저장이 커밋된 뒤 스토리지를 타므로 트랜잭션 없이 커밋하고 끝에 지운다. 스토리지·후처리·알림·감사 기록은 목. 소유자 = ADMIN(2).
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("업로드 OVERWRITE·DUPLICATE — 스토리지 실패만 원복, 후처리 실패는 저장 유지, 용량 부족은 IS_FULL 1회")
class UploadWriteIntegrationTest {

    private static final int ADMIN = 2;

    @Autowired
    private FileUseCase fileUseCase;
    @Autowired
    private com.thinkfree.drive.application.version.VersionUseCase versionUseCase;
    @Autowired
    private ResourceRepository resourceRepository;
    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;
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

    private Long maxSeqBefore;
    private Long quotaBefore;
    private Long totalQuotaBefore;

    /** 세션 행 시각은 DB now() — 앞뒤 경계를 넉넉히(1초 앞) */
    private Instant testStart;

    @BeforeEach
    void setUp() {
        testStart = Instant.now().minusSeconds(1);
        maxSeqBefore = jdbc.queryForObject("SELECT COALESCE(MAX(resource_seq), 0) FROM tb_resource", Long.class);
        quotaBefore = jdbc.queryForObject("SELECT use_quota_byte FROM tb_user WHERE user_seq = ?", Long.class, ADMIN);
        totalQuotaBefore = jdbc.queryForObject("SELECT total_quota_byte FROM tb_user WHERE user_seq = ?", Long.class, ADMIN);
        jdbc.update("UPDATE tb_user SET use_quota_byte = 1000, total_quota_byte = 1073741824 WHERE user_seq = ?", ADMIN);
        loginAs(ADMIN);
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM tb_storage_deletion WHERE storage_key IN (SELECT storage_key FROM tb_upload_session WHERE user_seq = ? AND create_date >= ?)",
                ADMIN, java.sql.Timestamp.from(testStart));
        jdbc.update("DELETE FROM tb_upload_session WHERE user_seq = ? AND create_date >= ?", ADMIN, java.sql.Timestamp.from(testStart));
        jdbc.update("DELETE FROM tb_storage_deletion WHERE storage_key LIKE ?", "2/it-%");
        jdbc.update("DELETE FROM tb_resource_version WHERE user_seq = ? AND resource_seq > ?", ADMIN, maxSeqBefore);
        jdbc.update("DELETE FROM tb_resource WHERE user_seq = ? AND resource_seq > ?", ADMIN, maxSeqBefore);
        jdbc.update("UPDATE tb_user SET use_quota_byte = ?, total_quota_byte = ? WHERE user_seq = ?", quotaBefore, totalQuotaBefore, ADMIN);
    }

    private static void loginAs(int userSeq) {
        CustomUserDetails details = Mockito.mock(CustomUserDetails.class);
        given(details.getUserSeq()).willReturn(userSeq);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(details, null, List.of()));
    }

    /** 덮어쓸 파일 — 크기 100, 루트, 수정자·수정일은 알려진 과거 값 */
    private Resource createFile() {
        Resource r = Resource.builder().userSeq(ADMIN).ownerSeq(ADMIN).latestOwnerSeq(ADMIN).resourceType(ResourceType.FILE)
                .resourceName("ow-" + System.nanoTime() + ".txt").originalFileName("ow.txt").copyNumber(0).ext("txt").contentType("text/plain").sizeByte(100L)
                .versionRegisterDate(Instant.parse("2026-01-01T00:00:00Z")).registerUserSeq(ADMIN).build();
        // save 뒤 저장 이름은 변경 감지 — 트랜잭션 안에서(밖이면 save 뒤 분리 상태라 UPDATE 가 안 나감, W10)
        transactionTemplate.executeWithoutResult(s -> {
            resourceRepository.save(r);
            r.initResourceSaveName();
        });

        jdbc.update("UPDATE tb_resource SET update_user_seq = 1, update_date = TIMESTAMPTZ '2026-02-02 02:02:02+00' WHERE resource_seq = ?", r.getResourceSeq());
        return r;
    }

    private Map<String, Object> snapshot(Long resourceSeq) {
        Map<String, Object> resource = jdbc.queryForMap("SELECT size_byte, update_user_seq, update_date FROM tb_resource WHERE resource_seq = ?", resourceSeq);
        return Map.of("resource", resource, "quota", quota());
    }

    private long quota() {
        return jdbc.queryForObject("SELECT use_quota_byte FROM tb_user WHERE user_seq = ?", Long.class, ADMIN);
    }

    private int newRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tb_resource WHERE user_seq = ? AND resource_seq > ?", Integer.class, ADMIN, maxSeqBefore);
    }

    private static MockMultipartFile file(int size) {
        return new MockMultipartFile("file", "up.txt", "text/plain", new byte[size]);
    }

    private void overwrite(Long resourceSeq, int size) {
        fileUseCase.upload(new FileUploadCommand(null, null, resourceSeq, file(size), ResourceSaveType.OVERWRITE), ADMIN, false, false);
    }

    // ------------------------------------------------------------------ DUPLICATE — 업로드 세션 경로(세션 → 저장 → 커밋)

    private void uploadDuplicate(int size) {
        fileUseCase.upload(new FileUploadCommand(null, null, null, file(size), ResourceSaveType.DUPLICATE), ADMIN, false, false);
    }

    /** 이 테스트가 만든 마지막 세션 */
    private Map<String, Object> lastSession() {
        return jdbc.queryForMap("SELECT upload_session_id, status, fail_reason, storage_key, result_resource_seq FROM tb_upload_session"
                + " WHERE user_seq = ? AND create_date >= ? ORDER BY create_date DESC, upload_session_id DESC LIMIT 1", ADMIN, java.sql.Timestamp.from(testStart));
    }

    private List<String> queuedActions(String storageKey) {
        return jdbc.queryForList("SELECT action FROM tb_storage_deletion WHERE storage_key = ? ORDER BY storage_deletion_seq", String.class, storageKey);
    }

    @Test
    @DisplayName("DUPLICATE 성공 — 세션 key({tenant}/{uuid})에 단일 PUT, 행의 저장 이름 = 세션 key, 사용량 +50, 세션 COMPLETED, 커밋 뒤 사용량 후처리")
    void duplicateSuccess() {
        given(storageService.headContentLength(anyString())).willReturn(50L);

        uploadDuplicate(50);

        Map<String, Object> session = lastSession();
        String key = (String) session.get("storage_key");
        assertThat(session.get("status")).isEqualTo("COMPLETED");
        assertThat(key).matches("\\d+/" + session.get("upload_session_id"));
        verify(storageService).putObject(eq(key), anyString(), any(java.io.InputStream.class), eq(50L));
        assertThat(newRows()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT resource_save_name FROM tb_resource WHERE resource_seq = ?", String.class, session.get("result_resource_seq")))
                .isEqualTo(key);
        assertThat(quota()).isEqualTo(1050L);
        verify(postProcessingOrchestratorRetry, Mockito.timeout(5000)).processQuotaIncreaseAndNotify(any());
    }

    @Test
    @DisplayName("DUPLICATE 스토리지 실패 — 새 행 없음·사용량 그대로, 세션 FAILED(STORAGE_ERROR) + 그 key DELETE 등록, StorageException 전파")
    void duplicateStorageFailure() {
        willThrow(StorageException.type(StorageErrorCode.UPLOAD_FAILED)).given(storageService).putObject(anyString(), anyString(), any(), Mockito.anyLong());

        assertThatThrownBy(() -> uploadDuplicate(50)).isInstanceOf(StorageException.class);

        Map<String, Object> session = lastSession();
        assertThat(session.get("status")).isEqualTo("FAILED");
        assertThat(session.get("fail_reason")).isEqualTo("STORAGE_ERROR");
        assertThat(queuedActions((String) session.get("storage_key"))).containsExactly("DELETE");
        assertThat(newRows()).isZero();
        assertThat(quota()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("DUPLICATE 저장된 크기 불일치 — 커밋 안 함, 세션 FAILED(VALIDATION) + DELETE 등록, UPLOAD_SIZE_MISMATCH")
    void duplicateSizeMismatch() {
        given(storageService.headContentLength(anyString())).willReturn(49L);

        assertThatThrownBy(() -> uploadDuplicate(50))
                .isInstanceOfSatisfying(ApiRuntimeException.class,
                        e -> assertThat(e.getCode()).isEqualTo(com.thinkfree.drive.upload.session.exception.UploadSessionErrorCode.UPLOAD_SIZE_MISMATCH));

        Map<String, Object> session = lastSession();
        assertThat(session.get("status")).isEqualTo("FAILED");
        assertThat(session.get("fail_reason")).isEqualTo("VALIDATION");
        assertThat(queuedActions((String) session.get("storage_key"))).containsExactly("DELETE");
        assertThat(newRows()).isZero();
        assertThat(quota()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("DUPLICATE 용량 부족 — 세션 시작 사전 검사에서 IS_FULL, 용량 초과 알림 1회, 세션·새 행 없음, 스토리지 호출 없음")
    void duplicateQuotaFull() {
        jdbc.update("UPDATE tb_user SET use_quota_byte = total_quota_byte WHERE user_seq = ?", ADMIN);
        long full = quota();

        assertThatThrownBy(() -> uploadDuplicate(50))
                .isInstanceOfSatisfying(ApiRuntimeException.class, e -> assertThat(e.getCode()).isEqualTo(QuotaErrorCode.IS_FULL));

        verify(notificationRetryService, Mockito.times(1)).quotaExceededNotify(any());
        verify(storageService, never()).putObject(anyString(), anyString(), any(), Mockito.anyLong());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_upload_session WHERE user_seq = ? AND create_date >= ?", Integer.class,
                ADMIN, java.sql.Timestamp.from(testStart))).isZero();
        assertThat(newRows()).isZero();
        assertThat(quota()).isEqualTo(full);
    }

    @Test
    @DisplayName("DUPLICATE 동시 업로드(같은 이름·같은 위치) — 커밋 tx 의 사용자·부모 행 잠금으로 이름 넘버링이 직렬화되어 이름이 모두 다르다")
    void concurrentSameNameUploadsGetDistinctNames() throws Exception {
        given(storageService.headContentLength(anyString())).willReturn(50L);
        int threads = 6;
        var pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    loginAs(ADMIN);
                    start.await();
                    uploadDuplicate(50);
                    return null;
                }));
            }
            start.countDown();
            for (var f : futures) {
                f.get(60, java.util.concurrent.TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        List<String> names = jdbc.queryForList("SELECT resource_name FROM tb_resource WHERE user_seq = ? AND resource_seq > ?", String.class, ADMIN, maxSeqBefore);
        assertThat(names).hasSize(threads).doesNotHaveDuplicates();
        assertThat(quota()).isEqualTo(1000L + 50L * threads);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_upload_session WHERE user_seq = ? AND create_date >= ? AND status = 'COMPLETED'",
                Integer.class, ADMIN, java.sql.Timestamp.from(testStart))).isEqualTo(threads);
    }

    @Test
    @DisplayName("DUPLICATE 진행 중 업로드가 용량을 잡고 있으면 사전 검사에서 막힌다(확정 + 진행 중 + 신고 크기)")
    void duplicateBlockedByInProgressSessions() {
        jdbc.update("UPDATE tb_user SET use_quota_byte = 1000, total_quota_byte = 1100 WHERE user_seq = ?", ADMIN);
        jdbc.update("INSERT INTO tb_upload_session (user_seq, request_user_seq, save_type, file_name, content_type, size_byte) VALUES (?, ?, 'DUPLICATE', 'x', 'text/plain', 80)",
                ADMIN, ADMIN);

        assertThatThrownBy(() -> uploadDuplicate(50))
                .isInstanceOfSatisfying(ApiRuntimeException.class, e -> assertThat(e.getCode()).isEqualTo(QuotaErrorCode.IS_FULL));
        verify(storageService, never()).putObject(anyString(), anyString(), any(), Mockito.anyLong());
    }

    // ------------------------------------------------------------------ VERSION·OVERWRITE·RESTORE — 업로드 세션 경로(불변 key, 커밋 tx 에서만 반영)

    /** 목 스토리지가 받은 크기를 기억했다가 커밋 전 크기 확인(headContentLength)에 돌려준다 — 복사는 원본 크기 */
    private final Map<String, Long> stored = new java.util.concurrent.ConcurrentHashMap<>();

    private void rememberStoredSizes() {
        Mockito.doAnswer(inv -> stored.put(inv.getArgument(0), inv.getArgument(3))).when(storageService)
               .putObject(anyString(), anyString(), any(), Mockito.anyLong());
        Mockito.doAnswer(inv -> stored.put(inv.getArgument(1), stored.getOrDefault((String) inv.getArgument(0), -1L))).when(storageService)
               .copy(anyString(), anyString());
        given(storageService.headContentLength(anyString())).willAnswer(inv -> stored.getOrDefault((String) inv.getArgument(0), -1L));
    }

    private void saveVersion(Long resourceSeq, int size) {
        fileUseCase.upload(new FileUploadCommand(null, null, resourceSeq, file(size), ResourceSaveType.VERSION), ADMIN, false, false);
    }

    private String keyOf(Long resourceSeq) {
        return jdbc.queryForObject("SELECT resource_save_name FROM tb_resource WHERE resource_seq = ?", String.class, resourceSeq);
    }

    private List<Map<String, Object>> versions(Long resourceSeq) {
        return jdbc.queryForList("SELECT version_number, resource_save_name, size_byte, status FROM tb_resource_version WHERE resource_seq = ? ORDER BY version_number", resourceSeq);
    }

    /** 삭제 등록 행의 다음 시도 시각이 유예(24h)만큼 뒤인지 */
    private boolean deferredDeleteQueued(String key) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tb_storage_deletion WHERE storage_key = ? AND action = 'DELETE' AND next_attempt_date > now() + interval '23 hours'",
                Integer.class, key) == 1;
    }

    private void insertVersion(Long resourceSeq, int versionNumber, String key, long size) {
        jdbc.update("INSERT INTO tb_resource_version (user_seq, resource_seq, owner_seq, resource_save_name, version_number, size_byte, status, register_user_seq)"
                + " VALUES (?, ?, ?, ?, ?, ?, 'SUCCESS', ?)", ADMIN, resourceSeq, ADMIN, key, versionNumber, size, ADMIN);
    }

    @Test
    @DisplayName("OVERWRITE 성공 — 새 세션 key 로 교체(크기 100 → 300, 사용량 +200), 이전 key 는 24시간 유예 삭제 등록, 세션 COMPLETED")
    void overwriteSuccess() {
        rememberStoredSizes();
        Resource r = createFile();
        String before = keyOf(r.getResourceSeq());

        overwrite(r.getResourceSeq(), 300);

        Map<String, Object> session = lastSession();
        assertThat(session.get("status")).isEqualTo("COMPLETED");
        assertThat(keyOf(r.getResourceSeq())).isEqualTo(session.get("storage_key")).isNotEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT size_byte FROM tb_resource WHERE resource_seq = ?", Long.class, r.getResourceSeq())).isEqualTo(300L);
        assertThat(quota()).isEqualTo(1200L);
        assertThat(deferredDeleteQueued(before)).isTrue();
        assertThat(versions(r.getResourceSeq())).as("덮어쓰기는 버전을 남기지 않는다").isEmpty();
        jdbc.update("DELETE FROM tb_storage_deletion WHERE storage_key = ?", before);
    }

    @Test
    @DisplayName("OVERWRITE 스토리지 실패 — key·크기·수정자·수정일·사용량 모두 그대로, 세션 FAILED + 새 key DELETE 등록")
    void overwriteStorageFailureChangesNothing() {
        Resource r = createFile();
        String before = keyOf(r.getResourceSeq());
        Map<String, Object> snapshot = snapshot(r.getResourceSeq());
        willThrow(StorageException.type(StorageErrorCode.UPLOAD_FAILED)).given(storageService).putObject(anyString(), anyString(), any(), Mockito.anyLong());

        assertThatThrownBy(() -> overwrite(r.getResourceSeq(), 300)).isInstanceOf(StorageException.class);

        assertThat(snapshot(r.getResourceSeq())).isEqualTo(snapshot);
        assertThat(keyOf(r.getResourceSeq())).isEqualTo(before);
        Map<String, Object> session = lastSession();
        assertThat(session.get("status")).isEqualTo("FAILED");
        assertThat(queuedActions((String) session.get("storage_key"))).containsExactly("DELETE");
        assertThat(queuedActions(before)).as("현재 내용 key 는 건드리지 않는다").isEmpty();
    }

    @Test
    @DisplayName("OVERWRITE 후처리 실패 — 저장 유지(크기 300·사용량 +200), 오류 없음")
    void overwritePostProcessingFailureKeepsSave() {
        rememberStoredSizes();
        Resource r = createFile();
        String before = keyOf(r.getResourceSeq());
        willThrow(ApiRuntimeException.type(PostProcessingErrorCode.QUOTA_STATE_EVALUATION_FAILED))
                .given(postProcessingOrchestratorRetry).processQuotaIncreaseAndNotify(any());

        overwrite(r.getResourceSeq(), 300);

        verify(postProcessingOrchestratorRetry, Mockito.timeout(5000)).processQuotaIncreaseAndNotify(any());
        assertThat(jdbc.queryForObject("SELECT size_byte FROM tb_resource WHERE resource_seq = ?", Long.class, r.getResourceSeq())).isEqualTo(300L);
        assertThat(quota()).isEqualTo(1200L);
        jdbc.update("DELETE FROM tb_storage_deletion WHERE storage_key = ?", before);
    }

    @Test
    @DisplayName("OVERWRITE 용량 부족 — 세션 시작에서 IS_FULL, 알림 1회, 스토리지 호출 없음, 메타·사용량 그대로")
    void overwriteQuotaFull() {
        Resource r = createFile();
        jdbc.update("UPDATE tb_user SET use_quota_byte = total_quota_byte WHERE user_seq = ?", ADMIN);
        Map<String, Object> snapshot = snapshot(r.getResourceSeq());

        assertThatThrownBy(() -> overwrite(r.getResourceSeq(), 300))
                .isInstanceOfSatisfying(ApiRuntimeException.class, e -> assertThat(e.getCode()).isEqualTo(QuotaErrorCode.IS_FULL));

        verify(notificationRetryService, Mockito.times(1)).quotaExceededNotify(any());
        verify(storageService, never()).putObject(anyString(), anyString(), any(), Mockito.anyLong());
        assertThat(snapshot(r.getResourceSeq())).isEqualTo(snapshot);
    }

    @Test
    @DisplayName("OVERWRITE 충돌 — 업로드 중 다른 저장이 내용을 바꾸면 덮어쓰지 않고 버전 저장으로(바뀐 내용은 버전으로 남는다)")
    void overwriteConflictSavesAsVersion() {
        rememberStoredSizes();
        Resource r = createFile();
        Mockito.doAnswer(inv -> {
            // 업로드 도중 다른 저장이 현재 내용을 바꿈(불변 key 라 key 가 바뀐다)
            jdbc.update("UPDATE tb_resource SET resource_save_name = ? WHERE resource_seq = ?", "2/it-concurrent", r.getResourceSeq());
            stored.put(inv.getArgument(0), inv.getArgument(3));
            return null;
        }).when(storageService).putObject(anyString(), anyString(), any(), Mockito.anyLong());

        overwrite(r.getResourceSeq(), 300);

        assertThat(keyOf(r.getResourceSeq())).isEqualTo(lastSession().get("storage_key"));
        assertThat(versions(r.getResourceSeq())).singleElement().satisfies(v -> {
            assertThat(v.get("resource_save_name")).as("다른 저장의 내용이 버전으로 남는다").isEqualTo("2/it-concurrent");
            assertThat(v.get("status")).isEqualTo("SUCCESS");
        });
        assertThat(queuedActions("2/it-concurrent")).as("버전으로 남긴 key 는 지우지 않는다").isEmpty();
        assertThat(quota()).as("버전 저장 정책 — 새 크기 전체").isEqualTo(1300L);
    }

    @Test
    @DisplayName("VERSION 저장 — 현재 key 를 그대로 버전 1 로(S3 복사 없음), 리소스는 새 key·크기 300·버전 2, 사용량 +300")
    void versionSave() {
        rememberStoredSizes();
        Resource r = createFile();
        String before = keyOf(r.getResourceSeq());

        saveVersion(r.getResourceSeq(), 300);

        assertThat(keyOf(r.getResourceSeq())).isEqualTo(lastSession().get("storage_key"));
        assertThat(jdbc.queryForObject("SELECT current_version_number FROM tb_resource WHERE resource_seq = ?", Integer.class, r.getResourceSeq())).isEqualTo(2);
        assertThat(versions(r.getResourceSeq())).singleElement().satisfies(v -> {
            assertThat(v.get("version_number")).isEqualTo(1);
            assertThat(v.get("resource_save_name")).isEqualTo(before);
            assertThat(v.get("size_byte")).isEqualTo(100L);
            assertThat(v.get("status")).isEqualTo("SUCCESS");
        });
        assertThat(quota()).isEqualTo(1300L);
        verify(storageService, never()).copy(anyString(), anyString());
    }

    @Test
    @DisplayName("VERSION 저장 한도 — 가장 오래된 버전 행 삭제·번호 당김, 사용량 +새 크기 −밀려난 크기, 밀려난 key 는 24시간 유예 삭제 등록")
    void versionSavePrunesOldest() {
        rememberStoredSizes();
        Resource r = createFile();
        int limit = 99;
        for (int n = 1; n <= limit; n++) {
            insertVersion(r.getResourceSeq(), n, "2/it-old-" + n, 1L);
        }
        jdbc.update("UPDATE tb_resource SET current_version_number = ? WHERE resource_seq = ?", limit + 1, r.getResourceSeq());
        String before = keyOf(r.getResourceSeq());

        saveVersion(r.getResourceSeq(), 300);

        List<Map<String, Object>> rows = versions(r.getResourceSeq());
        assertThat(rows).hasSize(limit);
        assertThat(rows).extracting(v -> v.get("version_number")).containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, limit).boxed().toList());
        assertThat(rows.get(0).get("resource_save_name")).isEqualTo("2/it-old-2");
        assertThat(rows.get(limit - 1).get("resource_save_name")).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT current_version_number FROM tb_resource WHERE resource_seq = ?", Integer.class, r.getResourceSeq())).isEqualTo(limit + 1);
        assertThat(quota()).isEqualTo(1000L + 300L - 1L);
        assertThat(deferredDeleteQueued("2/it-old-1")).isTrue();
    }

    @Test
    @DisplayName("버전 복원 — 버전 객체를 새 key 로 서버측 복사, 현재 내용은 새 버전으로, 복원 크기만큼 사용량 증가")
    void restoreVersion() {
        rememberStoredSizes();
        Resource r = createFile();
        insertVersion(r.getResourceSeq(), 1, "2/it-v1", 40L);
        jdbc.update("UPDATE tb_resource SET current_version_number = 2 WHERE resource_seq = ?", r.getResourceSeq());
        stored.put("2/it-v1", 40L);
        Long versionSeq = jdbc.queryForObject("SELECT resource_version_seq FROM tb_resource_version WHERE resource_seq = ?", Long.class, r.getResourceSeq());
        String before = keyOf(r.getResourceSeq());

        versionUseCase.restoreVersion(r.getResourceSeq(), versionSeq, null, ADMIN);

        String now = keyOf(r.getResourceSeq());
        verify(storageService).copy("2/it-v1", now);
        assertThat(jdbc.queryForObject("SELECT size_byte FROM tb_resource WHERE resource_seq = ?", Long.class, r.getResourceSeq())).isEqualTo(40L);
        assertThat(versions(r.getResourceSeq())).extracting(v -> v.get("resource_save_name")).containsExactly("2/it-v1", before);
        assertThat(lastSession().get("status")).isEqualTo("COMPLETED");
        assertThat(quota()).isEqualTo(1040L);
    }
}
