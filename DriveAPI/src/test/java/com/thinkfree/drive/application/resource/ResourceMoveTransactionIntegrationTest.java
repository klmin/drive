package com.thinkfree.drive.application.resource;

import com.thinkfree.drive.application.resource.command.ResourceMoveCommand;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.notification.service.NotificationRetryService;
import com.thinkfree.drive.postprocess.orchestrator.PostProcessingOrchestrator;
import com.thinkfree.drive.postprocess.orchestrator.exception.PostProcessingErrorCode;
import com.thinkfree.drive.quota.exception.QuotaErrorCode;
import com.thinkfree.drive.quota.exception.QuotaException;
import com.thinkfree.drive.resource.dto.ResourceMoveDto;
import com.thinkfree.drive.resource.dto.ResourceRenameDto;
import com.thinkfree.drive.resource.dto.folder.FolderUploadDto;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.exception.ResourceErrorCode;
import com.thinkfree.drive.resource.service.ResourceFindService;
import com.thinkfree.drive.resource.service.ResourceRetryService;
import com.thinkfree.drive.resource.service.ResourceService;
import com.thinkfree.drive.storage.service.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import javax.sql.DataSource;

import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 이동의 트랜잭션·재시도·잠금(10-14 점검 2-b·6-c) — 커밋까지 보려고 테스트 트랜잭션 없이 실행하고 끝에 지운다(FolderCreateRetryIntegrationTest 와 같은 방식).
 * <ul>
 *   <li>2-b: 이동 후처리 재시도(PostProcessingOrchestratorRetry, REQUIRED)는 UseCase 트랜잭션에 합류한다 — 합류한 재시도는 실패 시도가 공유 트랜잭션을
 *       rollback-only 로 만들어 "성공한" 재시도도 커밋 때 UnexpectedRollbackException(500)이 됐다. 이동 + 사용량 이전 + 공유 정리는 한 트랜잭션(결정)이라
 *       합류 중에는 재시도하지 않고 MOVE_NOTIFY_CREATE_FAILED 로 끝나야 한다. 용량 부족(QuotaException → IS_FULL)은 그대로.</li>
 *   <li>6-c: 이동할 행은 첫 로드에서 잠근다 — 같은 폴더의 동시 이동이 하위 경로를 어긋나게 두지 않는다.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("이동 트랜잭션 — 합류 재시도 없음(오류 코드 유지)·이동 행 첫 로드 잠금")
class ResourceMoveTransactionIntegrationTest {

    private static final int ADMIN = 2;
    private static final int SUPER_ADMIN = 1;
    private static final int TENANT = 1;

    @Autowired
    private ResourceUseCase resourceUseCase;
    @Autowired
    private ResourceService resourceService;
    @Autowired
    private ResourceRetryService resourceRetryService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;
    @MockitoSpyBean
    private PostProcessingOrchestrator postProcessingOrchestrator;
    @MockitoSpyBean
    private ResourceFindService resourceFindService;
    @MockitoBean
    private ResourceAuditService resourceAuditService;
    @MockitoBean
    private NotificationRetryService notificationRetryService;
    @MockitoBean
    private StorageService storageService;

    private Long maxSeqBefore;

    @BeforeEach
    void setUp() {
        maxSeqBefore = jdbc.queryForObject("SELECT COALESCE(MAX(resource_seq), 0) FROM tb_resource", Long.class);
        loginAs(ADMIN);
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        jdbc.update("DELETE FROM tb_resource WHERE resource_seq > ?", maxSeqBefore);
    }

    private static void loginAs(int userSeq) {
        CustomUserDetails details = Mockito.mock(CustomUserDetails.class);
        given(details.getUserSeq()).willReturn(userSeq);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(details, null, List.of()));
        TenantContext.set(TENANT);
    }

    private long folder(String name, Long parentFolderSeq) {
        return resourceRetryService.saveFolderWithCopyCheck(FolderUploadDto.builder().userSeq(ADMIN).ownerSeq(ADMIN)
                .parentFolderSeq(parentFolderSeq).baseFolderName(name).originalFolderName(name).build()).resourceSeq();
    }

    private long file(String name) {
        return jdbc.queryForObject("INSERT INTO tb_resource (user_seq, owner_seq, latest_owner_seq, resource_type, resource_name, original_file_name, "
                + "resource_save_name, ext, content_type, size_byte, register_user_seq) VALUES (?, ?, ?, 'FILE', ?, ?, ?, 'txt', 'text/plain', 10, ?) RETURNING resource_seq",
                Long.class, ADMIN, ADMIN, ADMIN, name, name, "move-tx/" + name + "/" + Instant.now().toEpochMilli(), ADMIN);
    }

    private Long parentOf(long resourceSeq) {
        return jdbc.queryForObject("SELECT parent_folder_seq FROM tb_resource WHERE resource_seq = ?", Long.class, resourceSeq);
    }

    private String fullPathSeqOf(long resourceSeq) {
        return jdbc.queryForObject("SELECT full_path_seq FROM tb_resource WHERE resource_seq = ?", String.class, resourceSeq);
    }

    private static ResourceMoveDto folderMoveDto(long resourceSeq, long parentFolderSeq) {
        return ResourceMoveDto.builder().resourceSeq(resourceSeq).parentFolderSeq(parentFolderSeq).resourceType(ResourceType.FOLDER)
                .isDelete(false).userSeq(ADMIN).targetUserSeq(ADMIN).build();
    }

    // ------------------------------------------------------------------ 2-b

    @Test
    @DisplayName("2-b 이동 후처리 1회 실패 뒤 성공 가능 — 합류 재시도 없이 MOVE_NOTIFY_CREATE_FAILED(구: 합류 재시도 성공 → 커밋 UnexpectedRollbackException), 이동 롤백")
    void moveFolder_postProcessingFailure_noJoinedRetry() {
        long target = folder("move-a", null);
        long destination = folder("move-d", null);
        willThrow(ApiRuntimeException.type(PostProcessingErrorCode.POST_PROCESSING_FAILED)).willCallRealMethod()
                .given(postProcessingOrchestrator).processMoveAndNotify(any());

        assertThatThrownBy(() -> resourceUseCase.moveFolder(target, new ResourceMoveCommand(null, null, destination), ADMIN, "admin"))
                .isInstanceOfSatisfying(ApiRuntimeException.class, e -> assertThat(e.getCode()).isEqualTo(PostProcessingErrorCode.MOVE_NOTIFY_CREATE_FAILED));

        verify(postProcessingOrchestrator, times(1)).processMoveAndNotify(any());
        assertThat(parentOf(target)).as("이동 + 후처리 한 트랜잭션 — 이동도 롤백").isNull();
    }

    @Test
    @DisplayName("2-b 대상 용량 부족(QuotaException) — 재시도 없이 IS_FULL(의도된 설계 유지)")
    void moveFolder_quotaFull_isFull() {
        long target = folder("move-q", null);
        long destination = folder("move-qd", null);
        willThrow(new QuotaException(QuotaErrorCode.IS_FULL)).given(postProcessingOrchestrator).processMoveAndNotify(any());

        assertThatThrownBy(() -> resourceUseCase.moveFolder(target, new ResourceMoveCommand(null, null, destination), ADMIN, "admin"))
                .isInstanceOfSatisfying(ApiRuntimeException.class, e -> assertThat(e.getCode()).isEqualTo(QuotaErrorCode.IS_FULL));

        verify(postProcessingOrchestrator, times(1)).processMoveAndNotify(any());
        assertThat(parentOf(target)).isNull();
    }

    // ------------------------------------------------------------------ 6-c

    @Test
    @DisplayName("6-c 같은 폴더 동시 이동 — 늦은 쪽은 앞선 이동의 커밋까지 기다리고, 하위 경로가 폴더 경로를 따른다")
    void concurrentFolderMoves_keepSubtreeConsistent() throws Exception {
        long a = folder("race-a", null);
        long c = folder("race-c", a);
        long d1 = folder("race-d1", null);
        long d2 = folder("race-d2", null);

        // 첫 이동(T2)을 행 로드 직후(하위 계층 조회)에서 멈춘다
        AtomicBoolean pauseNext = new AtomicBoolean(true);
        CountDownLatch reached = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        willAnswer(inv -> {
            if (pauseNext.compareAndSet(true, false)) {
                reached.countDown();
                release.await(20, TimeUnit.SECONDS);
            }
            return inv.callRealMethod();
        }).given(resourceFindService).findResourceVersionsByFolderHierarchy(anyLong(), anyInt());

        CompletableFuture<Void> t2 = CompletableFuture.runAsync(() -> {
            loginAs(ADMIN);
            resourceService.folderMove(folderMoveDto(a, d2));
        });
        assertThat(reached.await(10, TimeUnit.SECONDS)).as("T2 가 행을 읽고 멈춤").isTrue();

        CompletableFuture<Void> t1 = CompletableFuture.runAsync(() -> {
            loginAs(ADMIN);
            resourceService.folderMove(folderMoveDto(a, d1));
        });
        boolean t1FinishedWhileT2Paused;
        try {
            t1.get(1500, TimeUnit.MILLISECONDS);
            t1FinishedWhileT2Paused = true;
        } catch (java.util.concurrent.TimeoutException expected) {
            t1FinishedWhileT2Paused = false;
        } catch (ExecutionException e) {
            t1FinishedWhileT2Paused = true;
        } finally {
            release.countDown();
        }
        t2.get(20, TimeUnit.SECONDS);
        t1.get(20, TimeUnit.SECONDS);

        String aPath = fullPathSeqOf(a);
        assertThat(fullPathSeqOf(c)).as("하위 폴더 경로가 이동한 폴더 경로(%s) 아래", aPath).startsWith(aPath);
        assertThat(t1FinishedWhileT2Paused).as("T1 은 T2 가 첫 로드에서 잡은 행 잠금을 기다려야 한다").isFalse();
        assertThat(parentOf(a)).as("나중에 커밋한 T1 의 목적지").isEqualTo(d1);
    }

    @Test
    @DisplayName("교차 이동(A→B 먼저·B→A 나중) — 늦은 쪽은 앞선 커밋을 기다린 뒤 하위 폴더 이동으로 UPDATE_FAIL, 순환 없음")
    void crossFolderMoves_lowerSourceFirst_noCycle() throws Exception {
        long a = folder("cross-a", null);
        long b = folder("cross-b", null);

        assertCrossMoveRejected(a, b);
    }

    @Test
    @DisplayName("교차 이동(B→A 먼저·A→B 나중) — 늦은 쪽은 앞선 커밋을 기다린 뒤 하위 폴더 이동으로 UPDATE_FAIL, 순환 없음")
    void crossFolderMoves_higherSourceFirst_noCycle() throws Exception {
        long a = folder("cross-a", null);
        long b = folder("cross-b", null);

        assertCrossMoveRejected(b, a);
    }

    /** first → second 아래 이동을 하위 계층 조회에서 멈춘 채 second → first 아래 이동을 시작한다 */
    private void assertCrossMoveRejected(long first, long second) throws Exception {
        AtomicBoolean pauseNext = new AtomicBoolean(true);
        CountDownLatch reached = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        willAnswer(inv -> {
            if (pauseNext.compareAndSet(true, false)) {
                reached.countDown();
                release.await(20, TimeUnit.SECONDS);
            }
            return inv.callRealMethod();
        }).given(resourceFindService).findResourceVersionsByFolderHierarchy(anyLong(), anyInt());

        CompletableFuture<Void> t1 = CompletableFuture.runAsync(() -> {
            loginAs(ADMIN);
            resourceService.folderMove(folderMoveDto(first, second));
        });
        assertThat(reached.await(10, TimeUnit.SECONDS)).as("T1 이 잠금을 잡고 멈춤").isTrue();

        CompletableFuture<Void> t2 = CompletableFuture.runAsync(() -> {
            loginAs(ADMIN);
            resourceService.folderMove(folderMoveDto(second, first));
        });
        try {
            Thread.sleep(1500);
            assertThat(t2).as("T2 는 T1 의 잠금을 기다려야 한다").isNotDone();
        } finally {
            release.countDown();
        }
        t1.get(20, TimeUnit.SECONDS);

        assertThatThrownBy(() -> t2.get(20, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                .cause().isInstanceOfSatisfying(ApiRuntimeException.class, e -> assertThat(e.getCode()).isEqualTo(ResourceErrorCode.UPDATE_FAIL));
        assertThat(parentOf(first)).isEqualTo(second);
        assertThat(parentOf(second)).as("나중 이동은 롤백 — 루트 그대로").isNull();
    }

    @Test
    @DisplayName("6-c 파일 이동 — 이동할 행을 첫 로드에서 잠근다(로드 직후 다른 커넥션의 FOR UPDATE NOWAIT 실패)")
    void fileMove_locksRowOnFirstLoad() throws Exception {
        long f = file("lock-move.txt");
        long destination = folder("lock-move-d", null);

        // fileMove 는 행 로드 직후 감사용 이동 전 경로를 조회한다 — 거기서 멈춘다
        CountDownLatch reached = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        willAnswer(inv -> {
            reached.countDown();
            release.await(20, TimeUnit.SECONDS);
            return "";
        }).given(resourceAuditService).findResourcePath(any(), any(), any());

        CompletableFuture<Void> move = CompletableFuture.runAsync(() -> {
            loginAs(ADMIN);
            resourceService.fileMove(ResourceMoveDto.builder().resourceSeq(f).parentFolderSeq(destination).resourceType(ResourceType.FILE)
                    .isDelete(false).userSeq(ADMIN).targetUserSeq(ADMIN).build());
        });
        try {
            assertThat(reached.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> jdbc.queryForList("SELECT resource_seq FROM tb_resource WHERE user_seq = ? AND resource_seq = ? FOR UPDATE NOWAIT", ADMIN, f))
                    .as("이동 중인 행은 잠겨 있어야 한다").isInstanceOf(DataAccessException.class);
        } finally {
            release.countDown();
            move.get(20, TimeUnit.SECONDS);
        }
        assertThat(parentOf(f)).isEqualTo(destination);
    }

    // ------------------------------------------------------------------ 10-14 후속: 이름 변경 첫 로드 잠금·소유자 바뀌는 이동의 사용자 우선 잠금

    @Test
    @DisplayName("폴더 이동 중 같은 폴더 이름 변경 — 이름 변경은 이동 커밋까지 기다린 뒤 새 경로로 바꾼다(폴더·하위 경로 일치)")
    void concurrentMoveAndRename_keepPathsConsistent() throws Exception {
        long a = folder("rr-a", null);
        long c = folder("rr-c", a);
        long d2 = folder("rr-d2", null);

        // 이동(T2)을 행 로드 직후(하위 계층 조회)에서 멈춘다
        AtomicBoolean pauseNext = new AtomicBoolean(true);
        CountDownLatch reached = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        willAnswer(inv -> {
            if (pauseNext.compareAndSet(true, false)) {
                reached.countDown();
                release.await(20, TimeUnit.SECONDS);
            }
            return inv.callRealMethod();
        }).given(resourceFindService).findResourceVersionsByFolderHierarchy(anyLong(), anyInt());

        CompletableFuture<Void> move = CompletableFuture.runAsync(() -> {
            loginAs(ADMIN);
            resourceService.folderMove(folderMoveDto(a, d2));
        });
        assertThat(reached.await(10, TimeUnit.SECONDS)).as("이동이 행을 읽고 멈춤").isTrue();

        CompletableFuture<Void> rename = CompletableFuture.runAsync(() -> {
            loginAs(ADMIN);
            resourceService.folderRenameWithChildFolders(ResourceRenameDto.builder().resourceSeq(a).userSeq(ADMIN).resourceType(ResourceType.FOLDER)
                    .isDelete(false).resourceName("rr-a2").originalFileName("rr-a2").copyNumber(0).build());
        });
        try {
            Thread.sleep(1000);
        } finally {
            release.countDown();
        }
        move.get(20, TimeUnit.SECONDS);
        rename.get(20, TimeUnit.SECONDS);

        assertThat(fullPathOf(a)).as("이동한 위치 + 새 이름").isEqualTo("rr-d2/rr-a2/");
        assertThat(fullPathOf(c)).as("하위 경로가 폴더 경로를 따른다").isEqualTo("rr-d2/rr-a2/rr-c/");
        assertThat(fullPathSeqOf(c)).startsWith(fullPathSeqOf(a));
    }

    @Test
    @DisplayName("소유자가 바뀌는 파일 이동 — 두 사용자 행을 리소스 행보다 먼저 잠근다(사용자 행을 다른 커넥션이 쥐면 리소스 행은 아직 안 잠김)")
    void crossOwnerMove_locksUsersBeforeResource() throws Exception {
        long f = file("xo-move.txt");
        long targetFolder = resourceRetryService.saveFolderWithCopyCheck(FolderUploadDto.builder().userSeq(SUPER_ADMIN).ownerSeq(SUPER_ADMIN)
                .baseFolderName("xo-target").originalFolderName("xo-target").build()).resourceSeq();
        var quotas = jdbc.queryForList("SELECT user_seq, use_quota_byte, total_quota_byte FROM tb_user WHERE user_seq IN (?, ?)", SUPER_ADMIN, ADMIN);
        jdbc.update("UPDATE tb_user SET total_quota_byte = 1073741824 WHERE user_seq = ?", SUPER_ADMIN);

        CompletableFuture<Void> move;
        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (var st = holder.createStatement()) {
                st.execute("SELECT user_seq FROM tb_user WHERE user_seq = " + SUPER_ADMIN + " FOR UPDATE");
            }
            move = CompletableFuture.runAsync(() -> {
                loginAs(ADMIN);
                resourceUseCase.moveFile(f, new ResourceMoveCommand(null, SUPER_ADMIN, targetFolder), ADMIN, "admin");
            });
            Thread.sleep(1500);
            assertThat(move).as("대상 사용자 행 잠금을 기다리는 중").isNotDone();
            try {
                assertThat(jdbc.queryForList("SELECT resource_seq FROM tb_resource WHERE resource_seq = ? FOR UPDATE NOWAIT", f))
                        .as("사용자 행을 기다리는 동안 리소스 행은 아직 안 잠김(사용자 → 리소스 순서)").hasSize(1);
            } finally {
                holder.rollback();
            }
        }
        try {
            move.get(20, TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT user_seq FROM tb_resource WHERE resource_seq = ?", Integer.class, f)).isEqualTo(SUPER_ADMIN);
        } finally {
            for (var q : quotas) {
                jdbc.update("UPDATE tb_user SET use_quota_byte = ?, total_quota_byte = ? WHERE user_seq = ?", q.get("use_quota_byte"), q.get("total_quota_byte"), q.get("user_seq"));
            }
        }
    }

    private String fullPathOf(long resourceSeq) {
        return jdbc.queryForObject("SELECT full_path FROM tb_resource WHERE resource_seq = ?", String.class, resourceSeq);
    }
}
