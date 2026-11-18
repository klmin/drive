package com.thinkfree.drive.application.resource;

import com.thinkfree.drive.api.v1.file.controller.FileController;
import com.thinkfree.drive.api.v1.file.request.FileMoveRequest;
import com.thinkfree.drive.api.v1.file.request.FileRenameRequest;
import com.thinkfree.drive.api.v1.folder.controller.FolderController;
import com.thinkfree.drive.api.v1.folder.request.FolderMoveRequest;
import com.thinkfree.drive.api.v1.folder.request.FolderRenameRequest;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.exception.ErrorCode;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.mcp.tool.DriveResourceTools;
import com.thinkfree.drive.oauth2.enums.OAuth2Scope;
import com.thinkfree.drive.postprocess.orchestrator.PostProcessingOrchestratorRetry;
import com.thinkfree.drive.resource.aspect.exception.ResourceLockErrorCode;
import com.thinkfree.drive.resource.dto.folder.FolderUploadDto;
import com.thinkfree.drive.resource.enums.NameConflictPolicy;
import com.thinkfree.drive.resource.service.ResourceRetryService;
import com.thinkfree.drive.resource.service.ResourceService;
import com.thinkfree.drive.storage.service.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 이동·이름 변경의 잠김 확인 시점 — 웹 진입점(컨트롤러, 애스펙트 포함)을 그대로 부르고,
 * 잠김 확인(애스펙트) 뒤·도메인 변경(ResourceService) 직전에 다른 커넥션이 대상(또는 폴더 하위)을 잠근다(is_locked=Y, 편집 시작에 해당).
 * 확인이 트랜잭션 밖이면 잠긴 대상이 그대로 이동·이름 변경된다. 테스트는 "잠긴 대상은 바뀌지 않아야 한다" 를 기대값으로 둔다.
 * 실제 커밋이 필요해 Testcontainers 에 커밋하고 끝에 지운다(사용자 2, 테넌트 1 픽스처).
 */
@SpringBootTest
@ActiveProfiles("test")
class ResourceMoveRenameConcurrencyIntegrationTest {

    static final int USER = 2;
    static final String KEY_PREFIX = "move-rename-concurrency-test/";

    @Autowired JdbcTemplate jdbc;
    @Autowired ResourceRetryService resources;
    @Autowired FileController files;
    @Autowired FolderController folders;
    @Autowired DriveResourceTools mcp;
    @MockitoSpyBean ResourceService resourceService;
    @MockitoBean ResourceAuditService audit;
    @MockitoBean StorageService storage;
    @MockitoBean com.thinkfree.drive.storage.config.S3BucketInitializer bucketInitializer;
    @MockitoBean PostProcessingOrchestratorRetry postprocessing;

    final ExecutorService pool = Executors.newFixedThreadPool(2);
    final List<Long> created = new ArrayList<>();
    CustomUserDetails user;

    @BeforeEach
    void setUp() {
        user = mock(CustomUserDetails.class);
        when(user.getUserSeq()).thenReturn(USER);
        when(user.getUsername()).thenReturn("fixture");
        TenantContext.set(1);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority(OAuth2Scope.MCP_WRITE.authority()))));
    }

    @AfterEach
    void cleanUp() {
        pool.shutdownNow();
        for (long id : created) {
            jdbc.update("DELETE FROM tb_resource_version WHERE user_seq=? AND resource_seq=?", USER, id);
            jdbc.update("DELETE FROM tb_resource WHERE user_seq=? AND resource_seq=?", USER, id);
        }
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    ResourceService target() {
        return AopTestUtils.getUltimateTargetObject(resourceService);
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
        return id;
    }

    long folder(Long parent) {
        long id = resources.saveFolderWithCopyCheck(FolderUploadDto.builder().userSeq(USER).ownerSeq(USER).parentFolderSeq(parent)
                .baseFolderName("mr-" + UUID.randomUUID()).originalFolderName("mr").build()).resourceSeq();
        created.add(0, id);
        return id;
    }

    /** 다른 커넥션·자동 커밋 — 편집 시작(잠금 API)에 해당. 지금 그 행을 쥔 트랜잭션이 없으면 바로 커밋된다 */
    void lockFromOtherConnection(long id) {
        try {
            pool.submit(() -> jdbc.update("UPDATE tb_resource SET is_locked='Y' WHERE user_seq=? AND resource_seq=?", USER, id)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    Long parentOf(long id) {
        return jdbc.queryForObject("SELECT parent_folder_seq FROM tb_resource WHERE user_seq=? AND resource_seq=?", Long.class, USER, id);
    }

    String nameOf(long id) {
        return jdbc.queryForObject("SELECT resource_name FROM tb_resource WHERE user_seq=? AND resource_seq=?", String.class, USER, id);
    }

    static ErrorCode code(Throwable t) {
        return t instanceof ApiRuntimeException api ? api.getCode() : null;
    }

    static Throwable catchAll(Runnable r) {
        try {
            r.run();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    // ------------------------------------------------------------------ 확인(애스펙트) 뒤·변경 전에 잠기는 경우

    @Test
    @DisplayName("파일 이동 — 확인 뒤 다른 요청이 파일을 잠그면, 잠긴 파일은 옮겨지지 않아야 한다")
    void fileMove_lockedInGap_mustNotMove() {
        long dest = folder(null);
        long id = file(null);
        doAnswer(call -> { lockFromOtherConnection(id); return call.callRealMethod(); }).when(target()).fileMove(any());

        Throwable failure = catchAll(() -> files.move(id, new FileMoveRequest(null, null, dest), user));

        assertThat(parentOf(id)).as("잠긴 파일이 옮겨졌는가(부모)").isNull();
        assertThat(code(failure)).isEqualTo(ResourceLockErrorCode.RESOURCE_LOCKED);
    }

    @Test
    @DisplayName("폴더 이동 — 확인 뒤 다른 요청이 하위 파일을 잠그면, 폴더는 옮겨지지 않아야 한다")
    void folderMove_childLockedInGap_mustNotMove() {
        long dest = folder(null);
        long moving = folder(null);
        long child = file(moving);
        doAnswer(call -> { lockFromOtherConnection(child); return call.callRealMethod(); }).when(target()).folderMove(any());

        Throwable failure = catchAll(() -> folders.move(moving, new FolderMoveRequest(null, null, dest), user));

        assertThat(parentOf(moving)).as("잠긴 하위가 있는 폴더가 옮겨졌는가(부모)").isNull();
        assertThat(code(failure)).isEqualTo(ResourceLockErrorCode.RESOURCE_LOCKED);
    }

    @Test
    @DisplayName("파일 이름 변경(ncp=NONE) — 확인 뒤 다른 요청이 파일을 잠그면, 이름이 바뀌지 않아야 한다")
    void fileRename_lockedInGap_mustNotRename() {
        long id = file(null);
        String before = nameOf(id);
        doAnswer(call -> { lockFromOtherConnection(id); return call.callRealMethod(); }).when(target()).resolveName(any());

        Throwable failure = catchAll(() -> files.rename(id, new FileRenameRequest(null, "renamed-" + UUID.randomUUID(), NameConflictPolicy.NONE), user));

        assertThat(nameOf(id)).as("잠긴 파일 이름이 바뀌었는가").isEqualTo(before);
        assertThat(code(failure)).isEqualTo(ResourceLockErrorCode.RESOURCE_LOCKED);
    }

    @Test
    @DisplayName("MCP 이동(drive_move_resource) — 웹과 같은 도메인 경로: 확인 뒤 잠기면 옮겨지지 않는다")
    void mcpMove_lockedInGap_mustNotMove() {
        long dest = folder(null);
        long id = file(null);
        doAnswer(call -> { lockFromOtherConnection(id); return call.callRealMethod(); }).when(target()).fileMove(any());

        var result = mcp.moveResource(id, dest, null, null);

        assertThat(parentOf(id)).as("잠긴 파일이 옮겨졌는가(부모)").isNull();
        assertThat(result.errorCode()).isEqualTo(ResourceLockErrorCode.RESOURCE_LOCKED.getErrorCode());
    }

    // ------------------------------------------------------------------ 지금 계약 확인(경합 아님)

    @Test
    @DisplayName("[현재 계약] 폴더 이름 변경 — 웹 API 에는 잠김 확인이 없어, 하위에 잠긴 파일이 있어도 이름이 바뀐다")
    void folderRename_withLockedChild_currentContract() {
        long dir = folder(null);
        long child = file(dir);
        jdbc.update("UPDATE tb_resource SET is_locked='Y' WHERE user_seq=? AND resource_seq=?", USER, child);

        Throwable failure = catchAll(() -> folders.rename(dir, new FolderRenameRequest(null, "renamed-" + UUID.randomUUID()), user));

        assertThat(failure).as("실패 원인").isNull();
    }

    @Test
    @DisplayName("MCP 폴더 이름 변경 — 웹 폴더 이름 변경과 같이 잠김 확인이 없다(하위에 잠긴 파일이 있어도 바뀜)")
    void mcpFolderRename_withLockedChild_sameAsWeb() {
        long dir = folder(null);
        long child = file(dir);
        jdbc.update("UPDATE tb_resource SET is_locked='Y' WHERE user_seq=? AND resource_seq=?", USER, child);

        var result = mcp.renameResource(dir, "renamed-" + UUID.randomUUID(), null, null);

        assertThat(result.error()).as("에러").isNull();
    }

    @Test
    @DisplayName("[현재 계약] 파일 이름 변경 ncp=AUTO — 잠김 확인을 건너뛰어 잠긴 파일도 이름이 바뀐다(편집기 자신의 이름 변경용)")
    void fileRenameAuto_lockedFile_currentContract() {
        long id = file(null);
        jdbc.update("UPDATE tb_resource SET is_locked='Y' WHERE user_seq=? AND resource_seq=?", USER, id);
        String before = nameOf(id);

        Throwable failure = catchAll(() -> files.rename(id, new FileRenameRequest(null, "auto-" + UUID.randomUUID(), NameConflictPolicy.AUTO), user));

        assertThat(failure).as("실패 원인").isNull();
        assertThat(nameOf(id)).isNotEqualTo(before);
    }

    @Test
    @DisplayName("[현재 계약] 잠긴 파일 이동·이름 변경(ncp=NONE) — 미리 잠겨 있으면 RESOURCE_LOCKED")
    void lockedBeforehand_rejected() {
        long dest = folder(null);
        long id = file(null);
        jdbc.update("UPDATE tb_resource SET is_locked='Y' WHERE user_seq=? AND resource_seq=?", USER, id);

        assertThat(code(catchAll(() -> files.move(id, new FileMoveRequest(null, null, dest), user)))).isEqualTo(ResourceLockErrorCode.RESOURCE_LOCKED);
        assertThat(code(catchAll(() -> files.rename(id, new FileRenameRequest(null, "x-" + UUID.randomUUID(), NameConflictPolicy.NONE), user))))
                .isEqualTo(ResourceLockErrorCode.RESOURCE_LOCKED);
        assertThat(parentOf(id)).isNull();
    }
}
