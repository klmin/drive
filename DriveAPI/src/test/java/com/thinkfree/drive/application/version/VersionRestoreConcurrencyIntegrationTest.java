package com.thinkfree.drive.application.version;

import com.thinkfree.drive.api.v1.resource.version.controller.ResourceVersionController;
import com.thinkfree.drive.api.v1.resource.version.request.ResourceVersionPath;
import com.thinkfree.drive.api.v1.resource.version.request.ResourceVersionRestoreRequest;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.exception.ErrorCode;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.mcp.tool.DriveVersionTools;
import com.thinkfree.drive.oauth2.enums.OAuth2Scope;
import com.thinkfree.drive.postprocess.orchestrator.PostProcessingOrchestratorRetry;
import com.thinkfree.drive.resource.aspect.exception.ResourceLockErrorCode;
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
 * 버전 복원의 잠김 확인 시점 — 웹(컨트롤러, 애스펙트 포함)·MCP 툴을 그대로 부르고, 잠김 확인 뒤·복원 메타 저장(ResourceService.updateWithVersioning) 직전에
 * 다른 커넥션이 파일을 잠근다(is_locked=Y, 편집 시작에 해당). 확인이 트랜잭션 밖이면 잠긴(편집 중) 파일 내용이 옛 버전으로 바뀐다.
 * 테스트는 "잠긴 파일은 복원되지 않아야 한다" 를 기대값으로 둔다. 스토리지는 목 — 메타(current_version_number)로 복원 여부를 본다.
 * 실제 커밋이 필요해 Testcontainers 에 커밋하고 끝에 지운다(사용자 2, 테넌트 1 픽스처).
 */
@SpringBootTest
@ActiveProfiles("test")
class VersionRestoreConcurrencyIntegrationTest {

    static final int USER = 2;
    static final String KEY_PREFIX = "version-restore-concurrency-test/";

    @Autowired JdbcTemplate jdbc;
    @Autowired ResourceVersionController versions;
    @Autowired DriveVersionTools mcp;
    @MockitoSpyBean ResourceService resourceService;
    @MockitoBean ResourceAuditService audit;
    @MockitoBean StorageService storage;
    @MockitoBean com.thinkfree.drive.storage.config.S3BucketInitializer bucketInitializer;
    @MockitoBean PostProcessingOrchestratorRetry postprocessing;

    final ExecutorService pool = Executors.newFixedThreadPool(2);
    final List<Long> created = new ArrayList<>();
    CustomUserDetails user;
    long quotaBefore;
    Long totalQuotaBefore;

    @BeforeEach
    void setUp() {
        user = mock(CustomUserDetails.class);
        when(user.getUserSeq()).thenReturn(USER);
        when(user.getUsername()).thenReturn("fixture");
        TenantContext.set(1);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority(OAuth2Scope.MCP_WRITE.authority()))));
        quotaBefore = jdbc.queryForObject("SELECT use_quota_byte FROM tb_user WHERE user_seq=?", Long.class, USER);
        // 픽스처 사용자의 총 용량이 작아(0 일 수 있음) 복원(사용량 예약)이 IS_FULL 로 막히지 않게 — 끝에 원래 값으로 되돌린다
        totalQuotaBefore = jdbc.queryForObject("SELECT total_quota_byte FROM tb_user WHERE user_seq=?", Long.class, USER);
        jdbc.update("UPDATE tb_user SET total_quota_byte=? WHERE user_seq=?", Long.MAX_VALUE / 4, USER);
    }

    @AfterEach
    void cleanUp() {
        pool.shutdownNow();
        for (long id : created) {
            jdbc.update("DELETE FROM tb_resource_version WHERE user_seq=? AND resource_seq=?", USER, id);
            jdbc.update("DELETE FROM tb_resource WHERE user_seq=? AND resource_seq=?", USER, id);
        }
        jdbc.update("UPDATE tb_user SET use_quota_byte=? WHERE user_seq=?", quotaBefore, USER);
        jdbc.update("UPDATE tb_user SET total_quota_byte=? WHERE user_seq=?", totalQuotaBefore, USER);
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    ResourceService target() {
        return AopTestUtils.getUltimateTargetObject(resourceService);
    }

    /** 현재 버전 2 인 파일 + 되돌릴 버전 1(SUCCESS) — 반환 [resourceSeq, resourceVersionSeq] */
    long[] fileWithHistory() {
        String key = KEY_PREFIX + UUID.randomUUID();
        long id = jdbc.queryForObject("""
                INSERT INTO tb_resource(user_seq,owner_seq,latest_owner_seq,resource_type,resource_name,original_file_name,resource_save_name,ext,content_type,
                                        size_byte,register_user_seq,parent_folder_seq,full_path_seq,current_version_number)
                VALUES(?,?,?,'FILE',?,'test.txt',?,'txt','text/plain',10,?,NULL,'/',2) RETURNING resource_seq""",
                Long.class, USER, USER, USER, key.substring(KEY_PREFIX.length()) + ".txt", key, USER);
        created.add(id);
        long versionSeq = jdbc.queryForObject("""
                INSERT INTO tb_resource_version(user_seq,resource_seq,owner_seq,resource_save_name,version_number,size_byte,status,register_user_seq)
                VALUES(?,?,?,?,1,5,'SUCCESS',?) RETURNING resource_version_seq""", Long.class, USER, id, USER, key + "-v1", USER);
        return new long[]{id, versionSeq};
    }

    void lockFromOtherConnection(long id) {
        try {
            pool.submit(() -> jdbc.update("UPDATE tb_resource SET is_locked='Y' WHERE user_seq=? AND resource_seq=?", USER, id)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    int currentVersion(long id) {
        return jdbc.queryForObject("SELECT current_version_number FROM tb_resource WHERE user_seq=? AND resource_seq=?", Integer.class, USER, id);
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

    // ------------------------------------------------------------------ 확인 뒤·복원 전에 잠기는 경우

    @Test
    @DisplayName("웹 버전 복원 — 확인 뒤 다른 요청이 파일을 잠그면, 잠긴 파일은 복원되지 않아야 한다(RESOURCE_LOCKED)")
    void webRestore_lockedInGap_mustNotRestore() {
        long[] f = fileWithHistory();
        doAnswer(call -> { lockFromOtherConnection(f[0]); return call.callRealMethod(); }).when(target()).updateWithVersioning(any());

        Throwable failure = catchAll(() -> versions.restoreVersion(new ResourceVersionPath(f[0], f[1]), new ResourceVersionRestoreRequest(null), user));

        assertThat(currentVersion(f[0])).as("잠긴 파일이 복원됐는가(현재 버전 번호)").isEqualTo(2);
        assertThat(code(failure)).isEqualTo(ResourceLockErrorCode.RESOURCE_LOCKED);
    }

    @Test
    @DisplayName("MCP 버전 복원 — 웹과 같은 경로: 확인 뒤 잠기면 복원되지 않는다(RESOURCE_LOCKED)")
    void mcpRestore_lockedInGap_mustNotRestore() {
        long[] f = fileWithHistory();
        doAnswer(call -> { lockFromOtherConnection(f[0]); return call.callRealMethod(); }).when(target()).updateWithVersioning(any());

        var result = mcp.restoreResourceVersion(f[0], f[1], null);

        assertThat(currentVersion(f[0])).as("잠긴 파일이 복원됐는가(현재 버전 번호)").isEqualTo(2);
        assertThat(result.errorCode()).isEqualTo(ResourceLockErrorCode.RESOURCE_LOCKED.getErrorCode());
    }

    // ------------------------------------------------------------------ 기본 기능

    @Test
    @DisplayName("잠기지 않은 파일 복원 — 웹·MCP 모두 성공, 현재 버전 번호가 올라간다")
    void restoreUnlocked_succeeds() {
        long[] web = fileWithHistory();
        long[] tool = fileWithHistory();

        versions.restoreVersion(new ResourceVersionPath(web[0], web[1]), new ResourceVersionRestoreRequest(null), user);
        var result = mcp.restoreResourceVersion(tool[0], tool[1], null);

        assertThat(currentVersion(web[0])).isEqualTo(3);
        assertThat(result.error()).isNull();
        assertThat(currentVersion(tool[0])).isEqualTo(3);
    }

    @Test
    @DisplayName("미리 잠긴 파일 복원 — 웹·MCP 모두 RESOURCE_LOCKED, 복원 안 됨")
    void restoreLockedBeforehand_rejected() {
        long[] f = fileWithHistory();
        jdbc.update("UPDATE tb_resource SET is_locked='Y' WHERE user_seq=? AND resource_seq=?", USER, f[0]);

        Throwable web = catchAll(() -> versions.restoreVersion(new ResourceVersionPath(f[0], f[1]), new ResourceVersionRestoreRequest(null), user));
        var tool = mcp.restoreResourceVersion(f[0], f[1], null);

        assertThat(code(web)).isEqualTo(ResourceLockErrorCode.RESOURCE_LOCKED);
        assertThat(tool.errorCode()).isEqualTo(ResourceLockErrorCode.RESOURCE_LOCKED.getErrorCode());
        assertThat(currentVersion(f[0])).isEqualTo(2);
    }
}
