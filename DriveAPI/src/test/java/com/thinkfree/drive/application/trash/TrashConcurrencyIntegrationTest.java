package com.thinkfree.drive.application.trash;

import com.thinkfree.drive.api.v1.file.controller.FileController;
import com.thinkfree.drive.api.v1.folder.controller.FolderController;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.exception.ErrorCode;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.mcp.tool.DriveTrashTools;
import com.thinkfree.drive.oauth2.enums.OAuth2Scope;
import com.thinkfree.drive.postprocess.orchestrator.PostProcessingOrchestratorRetry;
import com.thinkfree.drive.resource.aspect.exception.ResourceLockErrorCode;
import com.thinkfree.drive.resource.data.ResourceIdentity;
import com.thinkfree.drive.resource.dto.folder.FolderUploadDto;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.exception.ResourceErrorCode;
import com.thinkfree.drive.resource.service.ResourceRetryService;
import com.thinkfree.drive.storage.result.StorageDeleteObjectsResult;
import com.thinkfree.drive.storage.service.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 휴지통 보내기(웹 파일·폴더, MCP)와 영구 삭제 API 의 동시성 — 진입점을 실제 빈(컨트롤러·MCP 툴, 애스펙트 포함)으로 호출하고,
 * 잠김 확인과 변경 "사이"(감사 기록 시점)에 다른 커넥션이 같은 행을 잠그려(is_locked=Y) 하거나 같은 대상을 동시에 지운다.
 * 잠금 확인이 트랜잭션·행 잠금 밖(애스펙트)이면 다른 커넥션이 끼어들어 "잠긴 파일이 휴지통으로" 가고, 안이면 끼어들지 못하고 기다린다.
 * 실제 커밋이 필요해 Testcontainers 에 커밋하고 끝에 지운다(사용자 2, 테넌트 1 픽스처).
 */
@SpringBootTest
@ActiveProfiles("test")
class TrashConcurrencyIntegrationTest {

    static final int USER = 2;
    static final String KEY_PREFIX = "trash-concurrency-test/";

    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txManager;
    @Autowired ResourceRetryService resources;
    @Autowired FileController files;
    @Autowired FolderController folders;
    @Autowired DriveTrashTools mcp;
    @Autowired TrashUseCase trash;
    @Autowired com.thinkfree.drive.cleanup.trash.service.TrashCleanUpService cleanUp;
    @MockitoBean ResourceAuditService audit;
    @MockitoBean StorageService storage;
    @MockitoBean com.thinkfree.drive.storage.config.S3BucketInitializer bucketInitializer;
    @MockitoBean PostProcessingOrchestratorRetry postprocessing;

    final ExecutorService pool = Executors.newFixedThreadPool(4);
    final List<Long> created = new ArrayList<>();
    CustomUserDetails user;
    long quotaBefore;
    /** 감사 기록(잠김 확인 뒤·변경 전) 시점에 끼어들 동작 — 이벤트별 */
    final Map<EventName, Runnable> hooks = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        user = mock(CustomUserDetails.class);
        when(user.getUserSeq()).thenReturn(USER);
        when(user.getUsername()).thenReturn("fixture");
        signIn();
        quotaBefore = quota();
        when(storage.deleteObjectsReportingFailures(anyList())).thenReturn(new StorageDeleteObjectsResult(Map.of()));
        doAnswer(call -> {
            Runnable hook = hooks.get(call.<EventName>getArgument(0));
            if (hook != null) hook.run();
            return null;
        }).when(audit).record(any(EventName.class), any(ResourceIdentity.class), any());
    }

    @AfterEach
    void cleanUp() {
        pool.shutdownNow();
        for (long id : created) {
            jdbc.update("DELETE FROM tb_resource_version WHERE user_seq=? AND resource_seq=?", USER, id);
            jdbc.update("DELETE FROM tb_trash WHERE user_seq=? AND resource_seq=?", USER, id);
            jdbc.update("DELETE FROM tb_resource WHERE user_seq=? AND resource_seq=?", USER, id);
        }
        jdbc.update("DELETE FROM tb_storage_deletion WHERE storage_key LIKE ?", KEY_PREFIX + "%");
        jdbc.update("UPDATE tb_user SET use_quota_byte=? WHERE user_seq=?", quotaBefore, USER);
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------ 픽스처

    void signIn() {
        TenantContext.set(1);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority(OAuth2Scope.MCP_WRITE.authority()))));
    }

    long quota() {
        return jdbc.queryForObject("SELECT use_quota_byte FROM tb_user WHERE user_seq=?", Long.class, USER);
    }

    long file(Long parent) {
        String key = KEY_PREFIX + UUID.randomUUID();
        String path = parent == null ? "/" : jdbc.queryForObject("SELECT full_path_seq FROM tb_resource WHERE resource_seq=? AND user_seq=?", String.class, parent, USER);
        long id = jdbc.queryForObject("""
                INSERT INTO tb_resource(user_seq,owner_seq,latest_owner_seq,resource_type,resource_name,original_file_name,resource_save_name,ext,content_type,
                                        size_byte,register_user_seq,parent_folder_seq,full_path_seq)
                VALUES(?,?,?,'FILE',?,'test.txt',?,'txt','text/plain',10,?,?,?) RETURNING resource_seq""",
                Long.class, USER, USER, USER, key.substring(KEY_PREFIX.length()) + ".txt", key, USER, parent, path);
        created.add(id);
        jdbc.update("INSERT INTO tb_resource_version(user_seq,resource_seq,owner_seq,resource_save_name,size_byte,status,register_user_seq) VALUES(?,?,?,?,5,'SUCCESS',?)",
                USER, id, USER, key + "-version", USER);
        jdbc.update("UPDATE tb_user SET use_quota_byte=use_quota_byte+15 WHERE user_seq=?", USER);
        return id;
    }

    long folder() {
        long id = resources.saveFolderWithCopyCheck(FolderUploadDto.builder().userSeq(USER).ownerSeq(USER)
                .baseFolderName("trash-concurrency-" + UUID.randomUUID()).originalFolderName("trash-concurrency").build()).resourceSeq();
        created.add(id);
        return id;
    }

    String flags(long id) {
        return jdbc.queryForObject("SELECT is_delete || '/' || is_locked FROM tb_resource WHERE user_seq=? AND resource_seq=?", String.class, USER, id);
    }

    int count(String table, long id) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE user_seq=? AND resource_seq=?", Integer.class, USER, id);
    }

    /**
     * 다른 커넥션(편집 시작 같은 잠금 API 의 UPDATE)이 같은 행을 is_locked=Y 로 바꾸려 한다 — 1.5초 안에 못 잡으면(행 잠금에 막힘) false.
     * 진행 중인 휴지통 트랜잭션과 별개 스레드·커넥션이라, 그 트랜잭션이 행을 잠그고 있지 않으면 바로 커밋된다.
     */
    boolean lockFromOtherConnection(long id) {
        try {
            return pool.submit(() -> {
                try {
                    return Boolean.TRUE.equals(new TransactionTemplate(txManager).execute(s -> {
                        jdbc.execute("SET LOCAL lock_timeout = '1500ms'");
                        return jdbc.update("UPDATE tb_resource SET is_locked='Y' WHERE user_seq=? AND resource_seq=?", USER, id) == 1;
                    }));
                } catch (DataAccessException blocked) {
                    return false;
                }
            }).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 다른 스레드에서 같은 사용자로 실행 — 성공이면 null, 실패면 예외 */
    Future<Throwable> async(Runnable entry) {
        return pool.submit(() -> {
            signIn();
            try {
                entry.run();
                return null;
            } catch (Throwable t) {
                return t;
            } finally {
                TenantContext.clear();
                SecurityContextHolder.clearContext();
            }
        });
    }

    static ErrorCode code(Throwable t) {
        return t instanceof ApiRuntimeException api ? api.getCode() : null;
    }

    // ------------------------------------------------------------------ 동시성 — 잠김 확인과 변경 사이에 끼어들기

    @Test
    @DisplayName("웹 파일 휴지통 — 확인 뒤 다른 요청이 파일을 잠그려 하면 휴지통 이동이 끝날 때까지 기다린다(잠긴 파일이 휴지통으로 가지 않음)")
    void webFileTrash_otherConnectionCannotLockMidway() {
        long id = file(null);
        boolean[] lockedMidway = new boolean[1];
        hooks.put(EventName.FILE_DELETE, () -> lockedMidway[0] = lockFromOtherConnection(id));

        files.delete(id, user);

        assertThat(lockedMidway[0]).as("이동 중 다른 커넥션이 잠금(is_locked=Y)에 성공했는가").isFalse();
        assertThat(flags(id)).as("is_delete/is_locked").isEqualTo("Y/N");
        assertThat(count("tb_trash", id)).isOne();
    }

    @Test
    @DisplayName("웹 폴더 휴지통 — 확인 뒤 다른 요청이 하위 파일을 잠그려 하면 기다린다(잠긴 하위가 휴지통으로 가지 않음)")
    void webFolderTrash_otherConnectionCannotLockChildMidway() {
        long folder = folder();
        long child = file(folder);
        boolean[] lockedMidway = new boolean[1];
        hooks.put(EventName.FOLDER_DELETE, () -> lockedMidway[0] = lockFromOtherConnection(child));

        folders.delete(folder, user);

        assertThat(lockedMidway[0]).as("이동 중 다른 커넥션이 하위 잠금에 성공했는가").isFalse();
        assertThat(flags(child)).as("하위 is_delete/is_locked").isEqualTo("Y/N");
    }

    @Test
    @DisplayName("MCP 휴지통(drive_delete_resource) — 웹과 같은 흐름: 확인 뒤 다른 요청이 파일을 잠그지 못한다")
    void mcpTrash_otherConnectionCannotLockMidway() {
        long id = file(null);
        boolean[] lockedMidway = new boolean[1];
        hooks.put(EventName.FILE_DELETE, () -> lockedMidway[0] = lockFromOtherConnection(id));

        var result = mcp.deleteResource(id);

        assertThat(result.error()).isNull();
        assertThat(lockedMidway[0]).as("이동 중 다른 커넥션이 잠금에 성공했는가").isFalse();
        assertThat(flags(id)).isEqualTo("Y/N");
    }

    @Test
    @DisplayName("영구 삭제 API(폴더) — 확인 뒤 다른 요청이 하위 파일을 잠그지 못하고, 하위까지 지워진다")
    void permanentFolderDelete_otherConnectionCannotLockChildMidway() {
        long folder = folder();
        long child = file(folder);
        boolean[] lockedMidway = new boolean[1];
        hooks.put(EventName.FOLDER_DELETE, () -> lockedMidway[0] = lockFromOtherConnection(child));

        folders.deletePermanently(folder, user);

        assertThat(lockedMidway[0]).as("삭제 중 다른 커넥션이 하위 잠금에 성공했는가").isFalse();
        assertThat(count("tb_resource", folder)).isZero();
        assertThat(count("tb_resource", child)).isZero();
        assertThat(quota()).isEqualTo(quotaBefore);
    }

    // ------------------------------------------------------------------ 동시성 — 같은 대상 동시 휴지통

    @Test
    @DisplayName("같은 파일 동시 휴지통 2건 — 하나만 성공하고, 늦은 쪽은 앞 요청 커밋을 기다린 뒤 RESOURCE_005(이미 휴지통)로 끝난다")
    void sameFileTrashedTwice_secondGetsNotFound() throws Exception {
        long id = file(null);
        CountDownLatch bothInside = new CountDownLatch(2);
        hooks.put(EventName.FILE_DELETE, () -> {
            bothInside.countDown();
            try {
                bothInside.await(3, TimeUnit.SECONDS); // 잠금이 없으면 둘 다 여기까지 온다
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        var first = async(() -> files.delete(id, user));
        var second = async(() -> files.delete(id, user));
        List<Throwable> outcomes = new ArrayList<>(List.of(Optional.ofNullable(first.get(30, TimeUnit.SECONDS)).orElse(new Success()),
                Optional.ofNullable(second.get(30, TimeUnit.SECONDS)).orElse(new Success())));

        assertThat(outcomes).as("결과(성공=Success)").filteredOn(t -> t instanceof Success).hasSize(1);
        Throwable failure = outcomes.stream().filter(t -> !(t instanceof Success)).findFirst().orElseThrow();
        assertThat(code(failure)).as("늦은 쪽 실패 원인: " + failure).isEqualTo(ResourceErrorCode.NOT_FOUND);
        assertThat(count("tb_trash", id)).isOne();
        assertThat(flags(id)).isEqualTo("Y/N");
    }

    static final class Success extends Throwable {
        Success() { super("success", null, false, false); }
    }

    // ------------------------------------------------------------------ 기능 — 잠김·종류·없는 대상

    @Test
    @DisplayName("잠긴 파일 휴지통 — 웹·MCP 모두 RESOURCE_LOCKED, 아무것도 바뀌지 않음")
    void lockedFile_rejectedOnWebAndMcp() {
        long id = file(null);
        jdbc.update("UPDATE tb_resource SET is_locked='Y' WHERE user_seq=? AND resource_seq=?", USER, id);

        Throwable web = catchAll(() -> files.delete(id, user));
        var tool = mcp.deleteResource(id);

        assertThat(code(web)).isEqualTo(ResourceLockErrorCode.RESOURCE_LOCKED);
        assertThat(tool.errorCode()).isEqualTo(ResourceLockErrorCode.RESOURCE_LOCKED.getErrorCode());
        assertThat(flags(id)).isEqualTo("N/Y");
        assertThat(count("tb_trash", id)).isZero();
    }

    @Test
    @DisplayName("하위에 잠긴 파일이 있는 폴더 휴지통 — RESOURCE_LOCKED, 폴더·하위 그대로")
    void folderWithLockedChild_rejected() {
        long folder = folder();
        long child = file(folder);
        jdbc.update("UPDATE tb_resource SET is_locked='Y' WHERE user_seq=? AND resource_seq=?", USER, child);

        Throwable web = catchAll(() -> folders.delete(folder, user));

        assertThat(code(web)).isEqualTo(ResourceLockErrorCode.RESOURCE_LOCKED);
        assertThat(flags(folder)).startsWith("N/");
        assertThat(flags(child)).isEqualTo("N/Y");
    }

    @Test
    @DisplayName("종류 불일치·없는 대상·이미 휴지통 — RESOURCE_005")
    void wrongTypeMissingOrTrashed_notFound() {
        long id = file(null);
        long folder = folder();

        assertThat(code(catchAll(() -> folders.delete(id, user)))).as("파일을 폴더 API 로").isEqualTo(ResourceErrorCode.NOT_FOUND);
        assertThat(code(catchAll(() -> files.delete(folder, user)))).as("폴더를 파일 API 로").isEqualTo(ResourceErrorCode.NOT_FOUND);
        assertThat(code(catchAll(() -> files.delete(-1L, user)))).as("없는 대상").isEqualTo(ResourceErrorCode.NOT_FOUND);
        files.delete(id, user);
        assertThat(code(catchAll(() -> files.delete(id, user)))).as("이미 휴지통").isEqualTo(ResourceErrorCode.NOT_FOUND);
        assertThat(mcp.deleteResource(id).errorCode()).as("MCP 이미 휴지통").isEqualTo(ResourceErrorCode.NOT_FOUND.getErrorCode());
    }

    @Test
    @DisplayName("정상 휴지통 → 복원 → 휴지통 영구 삭제 — 기존 흐름 회귀")
    void trashRestoreAndPermanentDelete_regression() {
        long folder = folder();
        long child = file(folder);

        folders.delete(folder, user);
        assertThat(flags(folder)).startsWith("Y/");
        assertThat(flags(child)).startsWith("Y/");
        assertThat(count("tb_trash", folder)).isOne();

        trash.restoreFolder(folder, false, USER);
        assertThat(flags(folder)).startsWith("N/");
        assertThat(flags(child)).startsWith("N/");

        mcp.deleteResource(folder);
        trash.deleteFolder(folder, USER, "fixture");
        assertThat(count("tb_resource", folder)).isZero();
        assertThat(count("tb_resource", child)).isZero();
        assertThat(quota()).isEqualTo(quotaBefore);
    }

    @Test
    @DisplayName("휴지통 비우기 묶음과 복원 동시 — 복원이 사용자 행을 쥔 동안 시작한 비우기는 복원 커밋을 기다린 뒤 대상을 읽어, 복원된 파일을 지우지 않는다")
    void emptyTrashBatch_waitsForRestore_andKeepsRestoredFile() throws Exception {
        long id = file(null);
        files.delete(id, user);
        long trashSeq = jdbc.queryForObject("SELECT trash_seq FROM tb_trash WHERE user_seq=? AND resource_seq=?", Long.class, USER, id);
        long quotaInTrash = quota();

        // 복원 트랜잭션이 사용자 행을 잡고 아직 커밋하지 않은 시점(복원 감사 기록)에 그 trash_seq 묶음 비우기를 시작한다.
        // 잠금 없이 대상을 먼저 읽으면 커밋 전 상태(아직 휴지통)를 보고, 사용량 UPDATE 에서 기다렸다가 복원 커밋 뒤 그 파일을 지운다.
        Future<?>[] emptying = new Future<?>[1];
        hooks.put(EventName.FILE_RESTORE, () -> {
            emptying[0] = pool.submit(() -> cleanUp.cleanUpResourcesByUser(USER, List.of(trashSeq)));
            try {
                Thread.sleep(1500);   // 비우기 쪽이 사용자 행 잠금에서 기다리게 될 때까지
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            assertThat(emptying[0]).as("복원이 커밋하기 전에는 비우기가 끝나지 않는다(사용자 행에서 대기)").isNotDone();
        });

        trash.restoreFile(id, false, USER);
        Object deleted = emptying[0].get(10, TimeUnit.SECONDS);

        assertThat(deleted).as("비우기가 지운 리소스 수").isEqualTo(0);
        assertThat(count("tb_resource", id)).as("복원된 파일이 남아 있는가").isOne();
        assertThat(flags(id)).startsWith("N/");
        assertThat(count("tb_trash", id)).isZero();
        assertThat(count("tb_resource_version", id)).isOne();
        assertThat(quota()).as("사용량은 복원 전과 같음(빠지지 않음)").isEqualTo(quotaInTrash);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tb_storage_deletion WHERE storage_key LIKE ?", Integer.class, KEY_PREFIX + "%"))
                .as("복원된 파일의 스토리지 삭제 등록 없음").isZero();
    }

    static Throwable catchAll(Runnable r) {
        try {
            r.run();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }
}
