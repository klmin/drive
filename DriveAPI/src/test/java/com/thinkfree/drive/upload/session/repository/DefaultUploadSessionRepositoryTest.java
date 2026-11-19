package com.thinkfree.drive.upload.session.repository;

import com.thinkfree.drive.cleanup.storage.repository.StorageDeletionRepository;
import com.thinkfree.drive.cleanup.storage.service.StorageDeletionService;
import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.upload.session.data.UploadSessionData;
import com.thinkfree.drive.upload.session.enums.UploadSaveType;
import com.thinkfree.drive.upload.session.enums.UploadSessionFailReason;
import com.thinkfree.drive.upload.session.enums.UploadSessionStatus;
import com.thinkfree.drive.upload.session.query.UploadSessionCreateQuery;
import com.thinkfree.drive.upload.session.service.UploadSessionCleanupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 업로드 세션 테이블·정리 스케줄러·정리 대기열 action 분리(PostgreSQL Testcontainers, 테스트마다 롤백).
 * 한 트랜잭션 안의 now() 는 고정이라 "오래된" 행은 시각 컬럼을 직접 과거로 돌려 만든다.
 */
@DisplayName("업로드 세션 저장소·정리(PostgreSQL)")
class DefaultUploadSessionRepositoryTest extends RepositoryTest {

    private static final int OWNER = 900_001;
    private static final int REQUESTER = 900_002;
    private static final int OTHER = 900_003;

    @Autowired
    private UploadSessionRepository repository;
    @Autowired
    private UploadSessionCleanupService cleanupService;
    @Autowired
    private StorageDeletionRepository storageDeletionRepository;
    @Autowired
    private StorageDeletionService storageDeletionService;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        // 같은 DB 를 쓴 앞 테스트(커밋형)가 남긴 행이 결과 수 단언을 흐리지 않게 — 이 테스트는 롤백되므로 실제로 지워지지 않는다
        jdbc.update("DELETE FROM tb_upload_session");
        jdbc.update("DELETE FROM tb_storage_deletion");
    }

    private UUID insert(int owner, long size) {
        return repository.insert(new UploadSessionCreateQuery(1, owner, REQUESTER, UploadSaveType.DUPLICATE, 10L, null, null, null,
                                                              "a.pdf", "application/pdf", size, 16L * 1024 * 1024, 1));
    }

    private void age(UUID id, String column, Duration age) {
        jdbc.update("UPDATE tb_upload_session SET " + column + " = now() - make_interval(secs => ?) WHERE upload_session_id = ?",
                    (double) age.toSeconds(), id);
    }

    private String status(UUID id) {
        return jdbc.queryForObject("SELECT status FROM tb_upload_session WHERE upload_session_id = ?", String.class, id);
    }

    @Test
    @DisplayName("INSERT — uuidv7 ID, PENDING, 스토리지 준비 전(storage_key·upload_id NULL)")
    void insertDefaults() {
        UUID id = insert(OWNER, 100);

        assertThat(id.version()).isEqualTo(7);
        UploadSessionData data = repository.findByIdAndRequester(id, REQUESTER).orElseThrow();
        assertThat(data.status()).isEqualTo(UploadSessionStatus.PENDING);
        assertThat(data.storageKey()).isNull();
        assertThat(data.uploadId()).isNull();
        assertThat(data.userSeq()).isEqualTo(OWNER);
        assertThat(data.saveType()).isEqualTo(UploadSaveType.DUPLICATE);
    }

    @Test
    @DisplayName("요청자가 아니면 조회되지 않는다(존재 숨김)")
    void requesterScoped() {
        UUID id = insert(OWNER, 100);
        assertThat(repository.findByIdAndRequester(id, OTHER)).isEmpty();
        assertThat(repository.markCompleting(id, OTHER)).isZero();
    }

    @Test
    @DisplayName("attachStorage 는 PENDING 일 때만")
    void attachStorageOnlyPending() {
        UUID id = insert(OWNER, 100);
        assertThat(repository.attachStorage(id, "1/" + id, "up-1")).isEqualTo(1);
        assertThat(repository.findByIdAndRequester(id, REQUESTER).orElseThrow().isMultipart()).isTrue();

        repository.markFailed(id, UploadSessionStatus.PENDING, UploadSessionFailReason.CANCELED, null);
        assertThat(repository.attachStorage(id, "1/" + id, "up-2")).isZero();
    }

    @Test
    @DisplayName("진행 중 합계 = 소유자의 PENDING·COMPLETING 만")
    void sumActive() {
        UUID pending = insert(OWNER, 100);
        UUID completing = insert(OWNER, 200);
        UUID failed = insert(OWNER, 400);
        insert(OTHER, 800);
        repository.markCompleting(completing, REQUESTER);
        repository.markFailed(failed, UploadSessionStatus.PENDING, UploadSessionFailReason.CANCELED, null);

        var usage = repository.sumActive(OWNER);
        assertThat(usage.sizeByte()).isEqualTo(300);
        assertThat(usage.count()).isEqualTo(2);
        assertThat(repository.sumActive(123_456).sizeByte()).isZero();
        assertThat(pending).isNotNull();
    }

    @Test
    @DisplayName("상태 전이는 조건부 — 선점은 한 번만, COMPLETED 는 COMPLETING 에서만, FAILED 는 기대 상태일 때 한 번만 정리 대상을 준다")
    void conditionalTransitions() {
        UUID id = insert(OWNER, 100);
        repository.attachStorage(id, "1/" + id, "up-1");

        assertThat(repository.markCompleted(id, 1L, "a.pdf")).as("PENDING 에서 바로 COMPLETED 불가").isZero();
        assertThat(repository.markCompleting(id, REQUESTER)).isEqualTo(1);
        assertThat(repository.markCompleting(id, REQUESTER)).as("중복 complete").isZero();
        assertThat(repository.touch(id)).as("COMPLETING 이면 활동 갱신 안 됨(파트 PUT 409)").isZero();

        var target = repository.markFailed(id, UploadSessionStatus.COMPLETING, UploadSessionFailReason.COMMIT_ERROR, "x".repeat(2000));
        assertThat(target).hasValueSatisfying(t -> {
            assertThat(t.storageKey()).isEqualTo("1/" + id);
            assertThat(t.uploadId()).isEqualTo("up-1");
        });
        assertThat(repository.markFailed(id, UploadSessionStatus.COMPLETING, UploadSessionFailReason.COMMIT_ERROR, null)).isEmpty();
        assertThat(repository.markCompleted(id, 1L, "a.pdf")).isZero();

        UploadSessionData data = repository.findByIdAndRequester(id, REQUESTER).orElseThrow();
        assertThat(data.status()).isEqualTo(UploadSessionStatus.FAILED);
        assertThat(data.failReason()).isEqualTo(UploadSessionFailReason.COMMIT_ERROR);
    }

    @Test
    @DisplayName("COMPLETED — 결과(리소스 seq·확정 이름) 저장")
    void completed() {
        UUID id = insert(OWNER, 100);
        repository.markCompleting(id, REQUESTER);
        assertThat(repository.markCompleted(id, 77L, "a (1).pdf")).isEqualTo(1);

        UploadSessionData data = repository.findByIdAndRequester(id, REQUESTER).orElseThrow();
        assertThat(data.status()).isEqualTo(UploadSessionStatus.COMPLETED);
        assertThat(data.resultResourceSeq()).isEqualTo(77L);
        assertThat(data.resultName()).isEqualTo("a (1).pdf");
        assertThat(repository.findByRequesterAndStatus(REQUESTER, UploadSessionStatus.COMPLETED, 10)).hasSize(1);
    }

    @Test
    @DisplayName("정리 — 만료 PENDING 은 FAILED(EXPIRED) + 멀티파트면 ABORT, 단일 요청이면 DELETE, 준비 전이면 등록 없음")
    void expirePending() {
        UUID multipart = insert(OWNER, 100);
        repository.attachStorage(multipart, "1/" + multipart, "up-mp");
        UUID single = insert(OWNER, 100);
        repository.attachStorage(single, "1/" + single, null);
        UUID notReady = insert(OWNER, 100);
        UUID fresh = insert(OWNER, 100);
        for (UUID id : List.of(multipart, single, notReady)) {
            age(id, "last_activity", Duration.ofMinutes(61));
        }
        age(fresh, "last_activity", Duration.ofMinutes(59));

        assertThat(cleanupService.expirePendingBatch()).isEqualTo(3);

        assertThat(status(multipart)).isEqualTo("FAILED");
        assertThat(status(fresh)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT fail_reason FROM tb_upload_session WHERE upload_session_id = ?", String.class, single)).isEqualTo("EXPIRED");

        var queue = jdbc.queryForList("SELECT storage_key, action, upload_id FROM tb_storage_deletion ORDER BY storage_deletion_seq");
        assertThat(queue).containsExactlyInAnyOrder(
                Map.of("storage_key", "1/" + multipart, "action", "ABORT_MULTIPART", "upload_id", "up-mp"),
                row("1/" + single, "DELETE", null));
    }

    @Test
    @DisplayName("정리 — 30분 넘은 COMPLETING 은 FAILED(COMMIT_ERROR) + ABORT + DELETE")
    void staleCompleting() {
        UUID stale = insert(OWNER, 100);
        repository.attachStorage(stale, "1/" + stale, "up-stale");
        repository.markCompleting(stale, REQUESTER);
        age(stale, "completing_at", Duration.ofMinutes(31));
        UUID running = insert(OWNER, 100);
        repository.markCompleting(running, REQUESTER);

        assertThat(cleanupService.failStaleCompletingBatch()).isEqualTo(1);

        assertThat(status(stale)).isEqualTo("FAILED");
        assertThat(status(running)).isEqualTo("COMPLETING");
        var queue = jdbc.queryForList("SELECT storage_key, action, upload_id FROM tb_storage_deletion");
        assertThat(queue).containsExactlyInAnyOrder(
                Map.of("storage_key", "1/" + stale, "action", "ABORT_MULTIPART", "upload_id", "up-stale"),
                row("1/" + stale, "DELETE", null));
    }

    @Test
    @DisplayName("정리 — 7일 지난 COMPLETED·FAILED 행만 삭제(진행 중은 오래돼도 유지)")
    void purgeFinished() {
        UUID oldCompleted = insert(OWNER, 1);
        repository.markCompleting(oldCompleted, REQUESTER);
        repository.markCompleted(oldCompleted, 1L, "a.pdf");
        UUID oldFailed = insert(OWNER, 1);
        repository.markFailed(oldFailed, UploadSessionStatus.PENDING, UploadSessionFailReason.CANCELED, null);
        UUID oldPending = insert(OWNER, 1);
        UUID recentFailed = insert(OWNER, 1);
        repository.markFailed(recentFailed, UploadSessionStatus.PENDING, UploadSessionFailReason.CANCELED, null);
        for (UUID id : List.of(oldCompleted, oldFailed, oldPending)) {
            age(id, "create_date", Duration.ofDays(8));
        }

        assertThat(cleanupService.purgeFinishedBatch()).isEqualTo(2);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM tb_upload_session", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("정리 대기열 — DELETE·유예 DELETE·ABORT 가 action 별로만 조회되고, 참조 중인 key 를 찾는다")
    void storageDeletionActions() {
        storageDeletionService.register(List.of("1/now"));
        storageDeletionService.registerDeferred(List.of("1/later"), Duration.ofHours(24));
        storageDeletionService.registerAbort("1/now", "up-x");

        Instant now = Instant.now().plusSeconds(1);
        assertThat(storageDeletionRepository.findDue(now, 10, 100)).singleElement()
                .satisfies(d -> assertThat(d.getStorageKey()).isEqualTo("1/now"));
        assertThat(storageDeletionRepository.findDueAborts(now, 10, 100)).singleElement().satisfies(d -> {
            assertThat(d.getStorageKey()).isEqualTo("1/now");
            assertThat(d.getUploadId()).isEqualTo("up-x");
        });
        assertThat(storageDeletionRepository.findDue(now.plus(Duration.ofHours(25)), 10, 100)).hasSize(2);

        jdbc.update("INSERT INTO tb_resource (user_seq, resource_type, resource_name, resource_save_name, register_user_seq) VALUES (?, 'FILE', 'x', ?, ?)",
                    OWNER, "1/live-resource", OWNER);
        jdbc.update("INSERT INTO tb_resource_version (user_seq, resource_seq, resource_save_name, size_byte, register_user_seq) VALUES (?, 1, ?, 1, ?)",
                    OWNER, "1/live-version", OWNER);
        assertThat(storageDeletionRepository.findReferencedKeys(List.of("1/live-resource", "1/live-version", "1/orphan")))
                .containsExactlyInAnyOrder("1/live-resource", "1/live-version");
    }

    private static Map<String, Object> row(String key, String action, String uploadId) {
        var map = new java.util.HashMap<String, Object>();
        map.put("storage_key", key);
        map.put("action", action);
        map.put("upload_id", uploadId);
        return map;
    }
}
