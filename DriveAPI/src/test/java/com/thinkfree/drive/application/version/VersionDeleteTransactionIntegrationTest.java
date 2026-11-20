package com.thinkfree.drive.application.version;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.notification.service.NotificationRetryService;
import com.thinkfree.drive.postprocess.orchestrator.PostProcessingOrchestrator;
import com.thinkfree.drive.postprocess.orchestrator.PostProcessingOrchestratorRetry;
import com.thinkfree.drive.postprocess.orchestrator.dto.PostProcessQuotaChangeDto;
import com.thinkfree.drive.postprocess.orchestrator.exception.PostProcessingErrorCode;
import com.thinkfree.drive.resource.service.ResourceService;
import com.thinkfree.drive.resource.version.exception.ResourceVersionErrorCode;
import com.thinkfree.drive.storage.service.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 버전 삭제의 트랜잭션·재시도·잠금 순서(10-14 점검 2-a·6-d) — 커밋까지 보려고 테스트 트랜잭션 없이 실행하고 끝에 지운다.
 * <ul>
 *   <li>2-a: 재시도는 트랜잭션 전체를 감싸야 한다(시도마다 새 트랜잭션). 구 구조는 UseCase 트랜잭션에 합류한 재시도(ResourceRetryService·후처리)라
 *       실패 시도가 공유 트랜잭션을 rollback-only 로 만들어 "성공한" 재시도도 커밋 때 UnexpectedRollbackException(500)이 됐다.</li>
 *   <li>6-d: 잠금 순서 = 사용자 → 리소스 → 버전(업로드·버전 저장·덮어쓰기와 같게). 구 삭제 경로는 리소스·버전 행을 먼저 잡고 사용량 갱신에서 사용자 행을 잡아
 *       같은 파일의 동시 버전 저장과 교착할 수 있었다.</li>
 * </ul>
 * 스토리지(삭제 이벤트 리스너)·알림은 목. 소유자 = ADMIN(2), 파일 크기 10, 버전 1개(크기 5).
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("버전 삭제 트랜잭션 — 재시도는 트랜잭션 전체, 잠금은 사용자 행 먼저")
class VersionDeleteTransactionIntegrationTest {

    private static final int ADMIN = 2;
    private static final int TENANT = 1;
    private static final long VERSION_SIZE = 5L;

    @Autowired
    private VersionUseCase versionUseCase;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private PostProcessingOrchestratorRetry postProcessingOrchestratorRetry;
    @MockitoSpyBean
    private ResourceService resourceService;
    @MockitoSpyBean
    private PostProcessingOrchestrator postProcessingOrchestrator;
    @MockitoBean
    private StorageService storageService;
    @MockitoBean
    private NotificationRetryService notificationRetryService;

    private Long resourceSeq;
    private Long resourceVersionSeq;
    private Long quotaBefore;
    private Long totalQuotaBefore;

    @BeforeEach
    void setUp() {
        quotaBefore = jdbc.queryForObject("SELECT use_quota_byte FROM tb_user WHERE user_seq = ?", Long.class, ADMIN);
        totalQuotaBefore = jdbc.queryForObject("SELECT total_quota_byte FROM tb_user WHERE user_seq = ?", Long.class, ADMIN);
        jdbc.update("UPDATE tb_user SET use_quota_byte = 1000, total_quota_byte = 1073741824 WHERE user_seq = ?", ADMIN);
        given(storageService.deleteObjectsAsync(any())).willReturn(CompletableFuture.completedFuture(null));
        loginAs(ADMIN);

        String key = "version-delete-tx/" + Instant.now().toEpochMilli();
        resourceSeq = jdbc.queryForObject("INSERT INTO tb_resource (user_seq, owner_seq, latest_owner_seq, resource_type, resource_name, original_file_name, "
                + "resource_save_name, current_version_number, ext, content_type, size_byte, register_user_seq) "
                + "VALUES (?, ?, ?, 'FILE', 'vdel.txt', 'vdel.txt', ?, 2, 'txt', 'text/plain', 10, ?) RETURNING resource_seq",
                Long.class, ADMIN, ADMIN, ADMIN, key, ADMIN);
        resourceVersionSeq = jdbc.queryForObject("INSERT INTO tb_resource_version (user_seq, resource_seq, owner_seq, resource_save_name, version_number, size_byte, "
                + "status, version_register_date, register_user_seq, register_date) VALUES (?, ?, ?, ?, 1, ?, 'SUCCESS', now() - interval '1 day', ?, now() - interval '1 day') "
                + "RETURNING resource_version_seq", Long.class, ADMIN, resourceSeq, ADMIN, key + "/1", VERSION_SIZE, ADMIN);
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        jdbc.update("DELETE FROM tb_resource_version WHERE resource_seq = ?", resourceSeq);
        jdbc.update("DELETE FROM tb_resource WHERE resource_seq = ?", resourceSeq);
        // 버전 삭제가 커밋되면 삭제 대기열에 키가 남는다 — 대기열 전체를 처리하는 다른 테스트(StorageDeletionBulk 등)에 섞이지 않게 정리
        jdbc.update("DELETE FROM tb_storage_deletion WHERE storage_key LIKE 'version-delete-tx/%'");
        jdbc.update("UPDATE tb_user SET use_quota_byte = ?, total_quota_byte = ? WHERE user_seq = ?", quotaBefore, totalQuotaBefore, ADMIN);
    }

    private static void loginAs(int userSeq) {
        CustomUserDetails details = Mockito.mock(CustomUserDetails.class);
        given(details.getUserSeq()).willReturn(userSeq);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(details, null, List.of()));
        TenantContext.set(TENANT);
    }

    private int versionCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tb_resource_version WHERE resource_seq = ?", Integer.class, resourceSeq);
    }

    private long quota() {
        return jdbc.queryForObject("SELECT use_quota_byte FROM tb_user WHERE user_seq = ?", Long.class, ADMIN);
    }

    private int currentVersionNumber() {
        return jdbc.queryForObject("SELECT current_version_number FROM tb_resource WHERE resource_seq = ?", Integer.class, resourceSeq);
    }

    // ------------------------------------------------------------------ 2-a

    @Test
    @DisplayName("2-a 버전 삭제 첫 시도 일시 실패(UPDATE_FAIL) — 새 트랜잭션으로 재시도해 성공(구: 합류 재시도 성공 → 커밋 UnexpectedRollbackException)")
    void deleteVersion_transientFailure_retriesWholeTransaction() {
        willThrow(ApiRuntimeException.type(ResourceVersionErrorCode.UPDATE_FAIL)).willCallRealMethod()
                .given(resourceService).deleteVersion(anyLong(), anyLong(), anyInt());

        versionUseCase.deleteVersion(resourceSeq, resourceVersionSeq, ADMIN);

        verify(resourceService, times(2)).deleteVersion(anyLong(), anyLong(), anyInt());
        assertThat(versionCount()).isZero();
        assertThat(quota()).as("사용량은 한 번만 감소").isEqualTo(1000 - VERSION_SIZE);
        assertThat(currentVersionNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("2-a 버전 삭제 후처리 첫 시도 일시 실패 — 트랜잭션 전체 재시도로 성공, 사용량 감소 1회(구: UnexpectedRollbackException)")
    void deleteVersion_postProcessingTransientFailure_retriesWholeTransaction() {
        willThrow(ApiRuntimeException.type(PostProcessingErrorCode.QUOTA_STATE_UPSERT_FAILED)).willCallRealMethod()
                .given(postProcessingOrchestrator).processQuotaDecreaseAndNotify(any());

        versionUseCase.deleteVersion(resourceSeq, resourceVersionSeq, ADMIN);

        verify(postProcessingOrchestrator, times(2)).processQuotaDecreaseAndNotify(any());
        assertThat(versionCount()).isZero();
        assertThat(quota()).as("첫 시도는 롤백 — 사용량은 한 번만 감소").isEqualTo(1000 - VERSION_SIZE);
    }

    @Test
    @DisplayName("2-a 전체 버전 삭제 후처리 첫 시도 일시 실패 — 트랜잭션 전체 재시도로 성공(구: UnexpectedRollbackException)")
    void deleteAllVersions_postProcessingTransientFailure_retriesWholeTransaction() {
        willThrow(ApiRuntimeException.type(PostProcessingErrorCode.QUOTA_STATE_UPSERT_FAILED)).willCallRealMethod()
                .given(postProcessingOrchestrator).processQuotaDecreaseAndNotify(any());

        versionUseCase.deleteAllVersions(resourceSeq, ADMIN);

        verify(postProcessingOrchestrator, times(2)).processQuotaDecreaseAndNotify(any());
        assertThat(versionCount()).isZero();
        assertThat(quota()).isEqualTo(1000 - VERSION_SIZE);
    }

    @Test
    @DisplayName("2-a 없는 버전 삭제 — 오류 코드는 그대로 DELETE_FAIL, 데이터 변화 없음")
    void deleteVersion_notFound_deleteFail() {
        assertThatThrownBy(() -> versionUseCase.deleteVersion(resourceSeq, resourceVersionSeq + 100_000, ADMIN))
                .isInstanceOfSatisfying(ApiRuntimeException.class, e -> assertThat(e.getCode()).isEqualTo(ResourceVersionErrorCode.DELETE_FAIL));

        assertThat(versionCount()).isEqualTo(1);
        assertThat(quota()).isEqualTo(1000);
    }

    @Test
    @DisplayName("2-a·2-b 재시도 판정(RetryUtil.ONLY_OUTSIDE_TRANSACTION) — 트랜잭션 밖 호출은 재시도해 성공, 합류 호출은 재시도 없이 오류 코드")
    void postProcessingRetry_onlyOutsideTransaction() {
        var dto = new PostProcessQuotaChangeDto(ADMIN, ADMIN, Instant.now());
        willThrow(ApiRuntimeException.type(PostProcessingErrorCode.QUOTA_STATE_UPSERT_FAILED)).willCallRealMethod()
                .given(postProcessingOrchestrator).processQuotaDecreaseAndNotify(any());

        postProcessingOrchestratorRetry.processQuotaDecreaseAndNotify(dto);
        verify(postProcessingOrchestrator, times(2)).processQuotaDecreaseAndNotify(any());

        Mockito.clearInvocations(postProcessingOrchestrator);
        willThrow(ApiRuntimeException.type(PostProcessingErrorCode.QUOTA_STATE_UPSERT_FAILED)).willCallRealMethod()
                .given(postProcessingOrchestrator).processQuotaDecreaseAndNotify(any());
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(s -> postProcessingOrchestratorRetry.processQuotaDecreaseAndNotify(dto)))
                .isInstanceOfSatisfying(ApiRuntimeException.class, e -> assertThat(e.getCode()).isEqualTo(PostProcessingErrorCode.QUOTA_STATE_UPSERT_FAILED));
        verify(postProcessingOrchestrator, times(1)).processQuotaDecreaseAndNotify(any());
    }

    // ------------------------------------------------------------------ 6-d

    /** 다른 커넥션이 사용자 행을 잡은 동안 삭제를 돌리고, 삭제가 잠금 대기에 들어간 시점에 리소스·버전 행이 비어 있는지(= 사용자 행을 먼저 잡으려다 멈췄는지) 본다 */
    private void assertUserRowLockedFirst(Runnable delete) throws Exception {
        CompletableFuture<Void> running;
        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (Statement st = holder.createStatement()) {
                st.execute("SELECT user_seq FROM tb_user WHERE user_seq = " + ADMIN + " FOR UPDATE");
            }
            running = CompletableFuture.runAsync(() -> {
                loginAs(ADMIN);
                delete.run();
            });
            try {
                awaitLockWait();
                assertThat(jdbc.queryForList("SELECT resource_seq FROM tb_resource WHERE user_seq = ? AND resource_seq = ? FOR UPDATE NOWAIT", ADMIN, resourceSeq))
                        .as("사용자 행 대기 중 — 리소스 행은 아직 안 잡음").hasSize(1);
                assertThat(jdbc.queryForList("SELECT resource_version_seq FROM tb_resource_version WHERE user_seq = ? AND resource_seq = ? FOR UPDATE NOWAIT", ADMIN, resourceSeq))
                        .as("사용자 행 대기 중 — 버전 행은 아직 안 잡음").hasSize(1);
            } finally {
                holder.rollback();
            }
        }
        running.get(20, TimeUnit.SECONDS);
    }

    private void awaitLockWait() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            Integer waiting = jdbc.queryForObject("SELECT COUNT(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock' AND datname = current_database()", Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(50);
        }
        fail("삭제가 행 잠금 대기에 들어가지 않았다");
    }

    @Test
    @DisplayName("6-d 버전 삭제 — 사용자 행을 먼저 잠근다(사용자 행 대기 중 리소스·버전 행은 비어 있음)")
    void deleteVersion_locksUserRowFirst() throws Exception {
        assertUserRowLockedFirst(() -> versionUseCase.deleteVersion(resourceSeq, resourceVersionSeq, ADMIN));

        assertThat(versionCount()).isZero();
        assertThat(quota()).isEqualTo(1000 - VERSION_SIZE);
    }

    @Test
    @DisplayName("6-d 전체 버전 삭제 — 사용자 행을 먼저 잠근다")
    void deleteAllVersions_locksUserRowFirst() throws Exception {
        assertUserRowLockedFirst(() -> versionUseCase.deleteAllVersions(resourceSeq, ADMIN));

        assertThat(versionCount()).isZero();
        assertThat(quota()).isEqualTo(1000 - VERSION_SIZE);
    }
}
