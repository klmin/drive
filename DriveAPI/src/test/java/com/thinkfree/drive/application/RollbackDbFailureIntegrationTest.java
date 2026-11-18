package com.thinkfree.drive.application;

import com.thinkfree.drive.application.admin.user.AdminUserImportUseCase;
import com.thinkfree.drive.application.admin.command.AdminRegisterUserCommand;
import com.thinkfree.drive.application.admin.user.AdminUserUseCase;
import com.thinkfree.drive.application.admin.user.command.AdminUserBulkUpsertCommand;
import com.thinkfree.drive.application.admin.user.command.AdminUserBulkUpsertCommand.UserItemCommand;
import com.thinkfree.drive.application.resource.ResourceUseCase;
import com.thinkfree.drive.application.resource.command.ResourceMoveCommand;
import com.thinkfree.drive.application.resource.command.ResourceRenameCommand;
import com.thinkfree.drive.application.share.ShareUseCase;
import com.thinkfree.drive.application.share.command.ShareSaveCommand;
import com.thinkfree.drive.application.share.command.ShareTargetItemCommand;
import com.thinkfree.drive.application.share.command.ShareUpdateCommand;
import com.thinkfree.drive.application.trash.TrashUseCase;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.license.service.LicenseService;
import com.thinkfree.drive.license.service.SeatLicenseService;
import com.thinkfree.drive.notification.service.NotificationRetryService;
import com.thinkfree.drive.postprocess.orchestrator.exception.PostProcessingErrorCode;
import com.thinkfree.drive.application.quota.TenantUserQuotaGuard;
import com.thinkfree.drive.resource.dto.folder.FolderUploadDto;
import com.thinkfree.drive.resource.service.ResourceRetryService;
import com.thinkfree.drive.share.enums.ShareType;
import com.thinkfree.drive.share.target.exception.ShareTargetErrorCode;
import com.thinkfree.drive.storage.result.StorageCapacityResult;
import com.thinkfree.drive.storage.service.StorageAdminService;
import com.thinkfree.drive.storage.service.StorageService;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.exception.UserErrorCode;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * 트랜잭션 도중 DB 오류가 나면 앞서 쓴 행까지 전부 롤백되는지(MyBatis/MySQL → JPA/JdbcClient·PostgreSQL 전환 뒤 확인).
 * <p>
 * 방법: 실제 진입점(UseCase — 컨트롤러가 부르는 것)을 그대로 불러 트랜잭션 경계를 실물로 탄다. 커밋까지 보려고 테스트 트랜잭션 없이 실행하고 끝에 지운다
 * (FolderCreateRetryIntegrationTest 와 같은 방식). DB 오류는 임시 BEFORE 행 트리거(plpgsql — {@code RAISE EXCEPTION 'injected' USING ERRCODE = 'check_violation'})로
 * 만든다. 트리거는 이 테스트가 만든 행(resource_seq·user_seq 등)에만 걸리는 WHEN 조건이고, 작업 안에서 <b>늦게</b> 쓰는 테이블·행에 건다 — 앞선 쓰기가
 * 이미 DB 에 나간 뒤 실패해야 "롤백"을 증명하기 때문. Testcontainers 의 격리된 PG 18 컨테이너라 공유 dev DB 와 무관하고, 트리거·함수는 finally 에서 지운다.
 * PG 13+ 는 파티션 부모(HASH user_seq)에 BEFORE 행 트리거를 만들면 각 파티션에 복제되므로 부모에 건다.
 * <p>
 * 각 사례: ① 실패(앱 예외·오류 코드 확인) ② 관련 행 스냅샷 = 실패 전 스냅샷 ③ 트리거 제거 후 같은 호출 재실행 → 성공(잠금·반쯤 쓴 행 등 찌꺼기 없음).
 * 외부 연동(스토리지·알림·좌석 라이선스)만 목. 소유자 = ADMIN(2), 테넌트 1.
 */
@SpringBootTest
@ActiveProfiles("test")
// 이 클래스만의 목 조합이라 컨텍스트가 따로 캐시된다 — 끝나면 닫아 커넥션 풀을 돌려준다(캐시된 컨텍스트 풀들이 Testcontainers PG max_connections 를 넘기지 않게)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("DB 오류 시 트랜잭션 전체 롤백 — 임시 트리거로 늦은 쓰기를 실패시켜 앞선 쓰기까지 되돌아가는지")
class RollbackDbFailureIntegrationTest {

    private static final int SUPER_ADMIN = 1;
    private static final int ADMIN = 2;
    private static final int TENANT = 1;
    private static final String TEST_USER_PREFIX = "rbtest-";
    private static final String TRIGGER = "rb_test_fail";
    private static final String PROBE = "rb_test_probe";

    /** 스냅샷 컬럼 — 경로·부모·이름·삭제 상태·소유자·감사 컬럼 */
    private static final String RESOURCE_COLUMNS = "resource_seq, user_seq, parent_folder_seq, delete_parent_folder_seq, resource_name, original_file_name, "
            + "copy_number, full_path, full_path_seq, is_delete, size_byte, current_version_number, update_user_seq, update_date";

    @Autowired
    private ResourceUseCase resourceUseCase;
    @Autowired
    private TrashUseCase trashUseCase;
    @Autowired
    private ShareUseCase shareUseCase;
    @Autowired
    private AdminUserUseCase adminUserUseCase;
    @Autowired
    private AdminUserImportUseCase adminUserImportUseCase;
    @Autowired
    private ResourceRetryService resourceRetryService;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private StorageService storageService;
    @MockitoBean
    private NotificationRetryService notificationRetryService;
    @MockitoBean
    private SeatLicenseService seatLicenseService;
    @MockitoBean
    private LicenseService licenseService;
    @MockitoBean // 테넌트 총 용량 판정은 이 테스트 대상(DB 실패 롤백) 밖 — 통과시킨다
    private TenantUserQuotaGuard tenantUserQuotaGuard;
    @MockitoBean // 스토리지(Ceph·RustFS) 가용량 판정도 대상 밖 — 실제 관리 API 를 부르면 그 서버 상태에 따라 트리거 전에 실패한다
    private StorageAdminService storageAdminService;

    private Long maxSeqBefore;
    private List<Map<String, Object>> quotasBefore;

    @BeforeEach
    void setUp() {
        dropFailTrigger();
        maxSeqBefore = jdbc.queryForObject("SELECT COALESCE(MAX(resource_seq), 0) FROM tb_resource", Long.class);
        quotasBefore = jdbc.queryForList("SELECT user_seq, use_quota_byte, total_quota_byte FROM tb_user WHERE user_seq IN (?, ?)", SUPER_ADMIN, ADMIN);
        jdbc.update("UPDATE tb_user SET use_quota_byte = 1000, total_quota_byte = 1073741824 WHERE user_seq = ?", ADMIN);
        given(seatLicenseService.isSeatAvailable(anyInt(), anyInt())).willReturn(true);
        given(licenseService.getSeatPolicy(anyInt())).willReturn(new LicenseService.SeatPolicy(false, 0));
        given(storageAdminService.getStorageCapacity()).willReturn(StorageCapacityResult.unlimited());
        loginAs(ADMIN);
    }

    @AfterEach
    void cleanUp() {
        dropFailTrigger();
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        jdbc.update("DELETE FROM tb_share_target WHERE resource_seq > ?", maxSeqBefore);
        jdbc.update("DELETE FROM tb_share WHERE resource_seq > ?", maxSeqBefore);
        jdbc.update("DELETE FROM tb_trash WHERE resource_seq > ?", maxSeqBefore);
        jdbc.update("DELETE FROM tb_recent WHERE resource_seq > ?", maxSeqBefore);
        jdbc.update("DELETE FROM tb_starred WHERE resource_seq > ?", maxSeqBefore);
        jdbc.update("DELETE FROM tb_resource_version WHERE resource_seq > ?", maxSeqBefore);
        jdbc.update("DELETE FROM tb_resource WHERE resource_seq > ?", maxSeqBefore);
        jdbc.update("DELETE FROM tb_storage_deletion WHERE storage_key LIKE 'rb-test/%'");
        // 공유 알림(후처리)은 DB 에 남는다 — 이 테스트 리소스·사용자 것만 지운다
        jdbc.update("DELETE FROM tb_notification WHERE notification_event_seq IN (SELECT notification_event_seq FROM tb_notification_event WHERE resource_seq > ?)", maxSeqBefore);
        jdbc.update("DELETE FROM tb_notification WHERE user_seq IN (SELECT user_seq FROM tb_user WHERE user_id LIKE ?)", TEST_USER_PREFIX + "%");
        jdbc.update("DELETE FROM tb_notification_event WHERE resource_seq > ?", maxSeqBefore);
        jdbc.update("DELETE FROM tb_user_quota_alert_state WHERE user_seq IN (SELECT user_seq FROM tb_user WHERE user_id LIKE ?)", TEST_USER_PREFIX + "%");
        jdbc.update("DELETE FROM tb_user_log WHERE user_seq IN (SELECT user_seq FROM tb_user WHERE user_id LIKE ?)", TEST_USER_PREFIX + "%");
        jdbc.update("DELETE FROM tb_user WHERE user_id LIKE ?", TEST_USER_PREFIX + "%");
        for (var q : quotasBefore) {
            jdbc.update("UPDATE tb_user SET use_quota_byte = ?, total_quota_byte = ? WHERE user_seq = ?", q.get("use_quota_byte"), q.get("total_quota_byte"), q.get("user_seq"));
        }
    }

    // ------------------------------------------------------------------ 픽스처·도우미

    private static void loginAs(int userSeq) {
        CustomUserDetails details = Mockito.mock(CustomUserDetails.class);
        given(details.getUserSeq()).willReturn(userSeq);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(details, null, List.of()));
        TenantContext.set(TENANT);
    }

    private long folder(int userSeq, String name, Long parentFolderSeq) {
        return resourceRetryService.saveFolderWithCopyCheck(FolderUploadDto.builder().userSeq(userSeq).ownerSeq(userSeq)
                .parentFolderSeq(parentFolderSeq).baseFolderName(name).originalFolderName(name).build()).resourceSeq();
    }

    private long folder(String name, Long parentFolderSeq) {
        return folder(ADMIN, name, parentFolderSeq);
    }

    /** 파일(경로 NULL — 업로드와 같음), 크기 10 */
    private long file(String name, Long parentFolderSeq) {
        return jdbc.queryForObject("INSERT INTO tb_resource (user_seq, owner_seq, latest_owner_seq, resource_type, parent_folder_seq, resource_name, original_file_name, "
                + "resource_save_name, ext, content_type, size_byte, register_user_seq) VALUES (?, ?, ?, 'FILE', ?, ?, ?, ?, 'txt', 'text/plain', 10, ?) RETURNING resource_seq",
                Long.class, ADMIN, ADMIN, ADMIN, parentFolderSeq, name, name, "rb-test/" + name + "/" + System.nanoTime(), ADMIN);
    }

    /** 이전 버전 1개(크기 5) — 파일의 현재 버전 번호는 2 로 */
    private void addVersion(long resourceSeq) {
        jdbc.update("UPDATE tb_resource SET current_version_number = 2 WHERE resource_seq = ?", resourceSeq);
        jdbc.update("INSERT INTO tb_resource_version (user_seq, resource_seq, owner_seq, resource_save_name, version_number, size_byte, status, version_register_date, "
                + "register_user_seq, register_date) VALUES (?, ?, ?, ?, 1, 5, 'SUCCESS', now() - interval '1 day', ?, now() - interval '1 day')",
                ADMIN, resourceSeq, ADMIN, "rb-test/version/" + resourceSeq + "/1", ADMIN);
    }

    /** 공유 대상용 일반 사용자(테넌트 1, USER, 사용량 0) */
    private int user(String suffix) {
        return jdbc.queryForObject("WITH u AS (INSERT INTO tb_user (tenant_seq, user_id, user_name, status, total_quota_byte, use_quota_byte, register_user_seq) "
                + "VALUES (?, ?, ?, 'ACTIVE', 0, 0, 1) RETURNING user_seq), "
                + "r AS (INSERT INTO tb_user_role (user_seq, role_type, register_user_seq) SELECT user_seq, 'USER', 1 FROM u) SELECT user_seq FROM u", Integer.class, TENANT, TEST_USER_PREFIX + suffix + "@test.com", "rb-" + suffix);
    }

    private List<Map<String, Object>> resources(Long... resourceSeqs) {
        String in = String.join(",", java.util.Collections.nCopies(resourceSeqs.length, "?"));
        return jdbc.queryForList("SELECT " + RESOURCE_COLUMNS + " FROM tb_resource WHERE resource_seq IN (" + in + ") ORDER BY resource_seq", (Object[]) resourceSeqs);
    }

    /** 리소스·버전 행의 저장 키(resource_save_name) — 삭제 전에 읽어 둔다 */
    private List<String> saveNames(long resourceSeq) {
        return jdbc.queryForList("SELECT resource_save_name FROM tb_resource WHERE resource_seq = ? UNION ALL SELECT resource_save_name FROM tb_resource_version WHERE resource_seq = ?",
                String.class, resourceSeq, resourceSeq);
    }

    /** 주어진 키 중 삭제 대기열에 들어간 키 */
    private List<String> storageDeletionKeys(List<String> storageKeys) {
        String in = String.join(",", java.util.Collections.nCopies(storageKeys.size(), "?"));
        return jdbc.queryForList("SELECT storage_key FROM tb_storage_deletion WHERE storage_key IN (" + in + ")", String.class, storageKeys.toArray());
    }

    private List<Map<String, Object>> trashRows(long resourceSeq) {
        return jdbc.queryForList("SELECT user_seq, resource_seq, trash_seq, register_date FROM tb_trash WHERE resource_seq = ?", resourceSeq);
    }

    private List<Map<String, Object>> versionRows(long resourceSeq) {
        return jdbc.queryForList("SELECT resource_version_seq, user_seq, version_number, size_byte, resource_save_name, status FROM tb_resource_version "
                + "WHERE resource_seq = ? ORDER BY resource_version_seq", resourceSeq);
    }

    private List<Map<String, Object>> shareRows(long resourceSeq) {
        return jdbc.queryForList("SELECT share_seq, user_seq, share_type, can_view, can_edit, can_download, can_upload, can_share, update_date FROM tb_share "
                + "WHERE resource_seq = ? ORDER BY share_seq", resourceSeq);
    }

    private List<Map<String, Object>> shareTargetRows(long resourceSeq) {
        return jdbc.queryForList("SELECT share_target_seq, user_seq, share_seq, shared_by_user_seq, can_view, can_edit, can_download, can_upload, can_share, update_date "
                + "FROM tb_share_target WHERE resource_seq = ? ORDER BY user_seq", resourceSeq);
    }

    private List<Map<String, Object>> userRows(Integer... userSeqs) {
        String in = String.join(",", java.util.Collections.nCopies(userSeqs.length, "?"));
        return jdbc.queryForList("SELECT user_seq, user_id, user_name, total_quota_byte, use_quota_byte, country_code, phone_number, profile_image_key, status, "
                + "update_user_seq, update_date FROM tb_user WHERE user_seq IN (" + in + ") ORDER BY user_seq", (Object[]) userSeqs);
    }

    private long quota(int userSeq) {
        return jdbc.queryForObject("SELECT use_quota_byte FROM tb_user WHERE user_seq = ?", Long.class, userSeq);
    }

    /**
     * {@code table} 에 BEFORE {@code event} 행 트리거를 걸고(WHEN {@code condition}) {@code call} 을 실행해 그 예외를 돌려준다. 트리거·함수·시퀀스는 finally 에서 지운다.
     * <p>
     * 앞선 쓰기 증명(probe): 트리거 함수는 오류를 내기 직전 같은 트랜잭션 안에서 {@code probeSql}(정수 스칼라 — 앞선 쓰기가 반영된 행 수 등)을 계산해
     * 시퀀스에 {@code setval} 로 남긴다. setval 은 트랜잭션과 무관하게 남으므로 롤백 뒤에도 읽을 수 있다 → "실패 시점에 앞선 쓰기가 이미 DB 에 나가 있었다"
     * (= 롤백이 그 쓰기를 되돌렸다)를 확인한다. 값이 -1 이면 트리거가 아예 안 걸린 것(다른 이유로 실패).
     */
    private Throwable failWithTrigger(String table, String event, String condition, String probeSql, long expectedProbe, ThrowingCallable call) {
        jdbc.execute("CREATE SEQUENCE " + PROBE + " MINVALUE -1 START -1");
        jdbc.execute("CREATE OR REPLACE FUNCTION " + TRIGGER + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
                + "PERFORM setval('" + PROBE + "', (" + probeSql + ")::bigint, true); "
                + "RAISE EXCEPTION 'injected' USING ERRCODE = 'check_violation'; END $$");
        jdbc.execute("CREATE TRIGGER " + TRIGGER + " BEFORE " + event + " ON " + table + " FOR EACH ROW WHEN (" + condition + ") EXECUTE FUNCTION " + TRIGGER + "()");
        Throwable thrown;
        long probe;
        try {
            thrown = catchThrowable(call);
        } finally {
            probe = jdbc.queryForObject("SELECT last_value FROM " + PROBE, Long.class);
            dropFailTrigger();
        }
        System.out.println("[ROLLBACK-TEST] " + table + " " + event + " -> " + describe(thrown) + " / probe=" + probe);
        assertThat(thrown).as("주입한 DB 오류로 실패해야 한다").isNotNull();
        assertThat(probe).as("트리거가 걸렸고(≠ -1), 그 시점에 같은 트랜잭션의 앞선 쓰기가 이미 반영돼 있었다(probe: %s)", probeSql).isEqualTo(expectedProbe);
        assertThat(causeChainMentionsInjected(thrown)).as("원인 사슬에 주입 오류(injected)가 있거나, 앱이 원인을 감싼 오류 코드로 바꿨어야 한다 — %s", describe(thrown))
                .isTrue();
        return thrown;
    }

    private void dropFailTrigger() {
        for (String table : List.of("tb_resource", "tb_trash", "tb_share", "tb_share_target", "tb_resource_version", "tb_user")) {
            jdbc.execute("DROP TRIGGER IF EXISTS " + TRIGGER + " ON " + table);
        }
        jdbc.execute("DROP FUNCTION IF EXISTS " + TRIGGER + "()");
        jdbc.execute("DROP SEQUENCE IF EXISTS " + PROBE);
    }

    /** 원인 사슬 어딘가에 'injected' — 앱이 원인 없이 오류 코드로 바꾼 경우(ApiRuntimeException.type)는 로그로만 남으므로 그때는 ApiRuntimeException 이면 통과 */
    private static boolean causeChainMentionsInjected(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c.getMessage() != null && c.getMessage().contains("injected")) {
                return true;
            }
            if (c.getCause() == c) {
                break;
            }
        }
        return t instanceof ApiRuntimeException;
    }

    private static String describe(Throwable t) {
        if (t == null) {
            return "null";
        }
        String code = t instanceof ApiRuntimeException e ? " code=" + e.getCode() : "";
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return t.getClass().getName() + code + " / root=" + root.getClass().getName() + ": " + root.getMessage();
    }

    private static void assertCode(Throwable thrown, Object expectedCode) {
        assertThat(thrown).isInstanceOfSatisfying(ApiRuntimeException.class, e -> assertThat(e.getCode()).isEqualTo(expectedCode));
    }

    // ------------------------------------------------------------------ 1·2 폴더 이동·이름 변경(하위 경로 치환)

    @Test
    @DisplayName("1 하위 있는 폴더 이동 — 하위 폴더 경로 UPDATE 실패 → 폴더·하위 경로·부모 모두 그대로, 트리거 제거 후 재시도 성공")
    void folderMove_childPathUpdateFails_allRolledBack() {
        long a = folder("rb-mv-a", null);
        long c = folder("rb-mv-c", a);
        long f = file("rb-mv-f.txt", a);
        long d = folder("rb-mv-d", null);
        var before = resources(a, c, f, d);

        // 자기 행(a) 경로는 JPQL 벌크 전 자동 flush 로 먼저 나가고, 하위 치환(c)에서 실패
        Throwable thrown = failWithTrigger("tb_resource", "UPDATE", "OLD.resource_seq = " + c,
                "SELECT count(*) FROM tb_resource WHERE resource_seq = " + a + " AND parent_folder_seq = " + d + " AND full_path = 'rb-mv-d/rb-mv-a/'", 1,
                () -> resourceUseCase.moveFolder(a, new ResourceMoveCommand(null, null, d), ADMIN, "admin"));

        assertThat(thrown).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(resources(a, c, f, d)).as("폴더·하위·목적지 행 전부 실패 전과 같음").isEqualTo(before);

        resourceUseCase.moveFolder(a, new ResourceMoveCommand(null, null, d), ADMIN, "admin");
        var after = jdbc.queryForMap("SELECT parent_folder_seq, full_path, full_path_seq FROM tb_resource WHERE resource_seq = ?", a);
        assertThat(after.get("parent_folder_seq")).isEqualTo(d);
        assertThat(after.get("full_path")).isEqualTo("rb-mv-d/rb-mv-a/");
        assertThat(jdbc.queryForObject("SELECT full_path FROM tb_resource WHERE resource_seq = ?", String.class, c)).isEqualTo("rb-mv-d/rb-mv-a/rb-mv-c/");
        assertThat(jdbc.queryForObject("SELECT full_path_seq FROM tb_resource WHERE resource_seq = ?", String.class, c))
                .isEqualTo(after.get("full_path_seq") + String.valueOf(c) + "/");
        assertThat(jdbc.queryForObject("SELECT parent_folder_seq FROM tb_resource WHERE resource_seq = ?", Long.class, f)).isEqualTo(a);
    }

    @Test
    @DisplayName("2 하위 있는 폴더 이름 변경 — 하위 폴더 경로 UPDATE 실패 → 이름·하위 경로 그대로, 재시도 성공")
    void folderRename_childPathUpdateFails_allRolledBack() {
        long a = folder("rb-rn-a", null);
        long c = folder("rb-rn-c", a);
        long f = file("rb-rn-f.txt", a);
        var before = resources(a, c, f);

        Throwable thrown = failWithTrigger("tb_resource", "UPDATE", "OLD.resource_seq = " + c,
                "SELECT count(*) FROM tb_resource WHERE resource_seq = " + a + " AND resource_name = 'rb-rn-a2' AND full_path = 'rb-rn-a2/'", 1,
                () -> resourceUseCase.renameFolder(a, new ResourceRenameCommand(null, "rb-rn-a2", null), ADMIN));

        assertThat(thrown).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(resources(a, c, f)).as("이름·경로 전부 실패 전과 같음").isEqualTo(before);

        resourceUseCase.renameFolder(a, new ResourceRenameCommand(null, "rb-rn-a2", null), ADMIN);
        assertThat(jdbc.queryForObject("SELECT resource_name FROM tb_resource WHERE resource_seq = ?", String.class, a)).isEqualTo("rb-rn-a2");
        assertThat(jdbc.queryForObject("SELECT full_path FROM tb_resource WHERE resource_seq = ?", String.class, a)).isEqualTo("rb-rn-a2/");
        assertThat(jdbc.queryForObject("SELECT full_path FROM tb_resource WHERE resource_seq = ?", String.class, c)).isEqualTo("rb-rn-a2/rb-rn-c/");
    }

    // ------------------------------------------------------------------ 3 파일 이동(소유자 변경 — 이동 행 flush 뒤 사용량 이전)

    @Test
    @DisplayName("3 다른 소유자 폴더로 파일 이동 — 이동 행·대상 사용량 증가 뒤 원 소유자 사용량 감소 실패 → 파일·두 사용자 사용량 그대로, 재시도 성공")
    void fileMove_crossOwner_quotaDecreaseFails_allRolledBack() {
        jdbc.update("UPDATE tb_user SET total_quota_byte = 1073741824 WHERE user_seq = ?", SUPER_ADMIN);
        long f = file("rb-fm.txt", null);
        long target = folder(SUPER_ADMIN, "rb-fm-target", null);
        var beforeResources = resources(f, target);
        var beforeUsers = userRows(SUPER_ADMIN, ADMIN);

        // 순서: 파일 행 UPDATE(소유자·부모, flush) → 대상(1) 사용량 증가 → 원 소유자(2) 사용량 감소 ← 여기서 실패
        Throwable thrown = failWithTrigger("tb_user", "UPDATE", "OLD.user_seq = " + ADMIN + " AND NEW.use_quota_byte < OLD.use_quota_byte",
                "SELECT (SELECT count(*) FROM tb_resource WHERE resource_seq = " + f + " AND user_seq = " + SUPER_ADMIN + " AND parent_folder_seq = " + target + ") "
                        + "+ (SELECT count(*) FROM tb_user WHERE user_seq = " + SUPER_ADMIN + " AND use_quota_byte = " + (quota(SUPER_ADMIN) + 10) + ")", 2,
                () -> resourceUseCase.moveFile(f, new ResourceMoveCommand(null, SUPER_ADMIN, target), ADMIN, "admin"));

        assertCode(thrown, PostProcessingErrorCode.MOVE_NOTIFY_CREATE_FAILED);
        assertThat(resources(f, target)).as("파일 행(소유자·부모) 그대로").isEqualTo(beforeResources);
        assertThat(userRows(SUPER_ADMIN, ADMIN)).as("대상 사용량 증가도 롤백").isEqualTo(beforeUsers);

        long q1 = quota(SUPER_ADMIN);
        long q2 = quota(ADMIN);
        resourceUseCase.moveFile(f, new ResourceMoveCommand(null, SUPER_ADMIN, target), ADMIN, "admin");
        var moved = jdbc.queryForMap("SELECT user_seq, parent_folder_seq FROM tb_resource WHERE resource_seq = ?", f);
        assertThat(moved.get("user_seq")).isEqualTo(SUPER_ADMIN);
        assertThat(moved.get("parent_folder_seq")).isEqualTo(target);
        assertThat(quota(SUPER_ADMIN)).isEqualTo(q1 + 10);
        assertThat(quota(ADMIN)).isEqualTo(q2 - 10);
    }

    // ------------------------------------------------------------------ 4·5·6 휴지통

    @Test
    @DisplayName("4 하위 있는 폴더 휴지통 이동 — 삭제 상태 UPDATE 뒤 tb_trash INSERT 실패 → 폴더·하위 미삭제, 휴지통 행 없음, 재시도 성공")
    void folderToTrash_trashInsertFails_allRolledBack() {
        long a = folder("rb-tr-a", null);
        long c = folder("rb-tr-c", a);
        long f = file("rb-tr-f.txt", a);
        var before = resources(a, c, f);

        Throwable thrown = failWithTrigger("tb_trash", "INSERT", "NEW.resource_seq = " + a,
                "SELECT count(*) FROM tb_resource WHERE resource_seq IN (" + a + "," + c + "," + f + ") AND is_delete = 'Y'", 3,
                () -> trashUseCase.moveFolderToTrash(a, ADMIN));

        assertThat(thrown).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(resources(a, c, f)).as("is_delete·삭제 부모 그대로").isEqualTo(before);
        assertThat(trashRows(a)).isEmpty();

        trashUseCase.moveFolderToTrash(a, ADMIN);
        assertThat(jdbc.queryForList("SELECT is_delete FROM tb_resource WHERE resource_seq IN (?, ?, ?)", String.class, a, c, f)).containsOnly("Y");
        assertThat(trashRows(a)).hasSize(1);
    }

    @Test
    @DisplayName("5 휴지통 폴더 복원 — 자기·하위 복원 UPDATE 뒤 tb_trash DELETE 실패 → 여전히 휴지통, 행 그대로, 재시도 성공")
    void folderRestore_trashDeleteFails_allRolledBack() {
        long a = folder("rb-rs-a", null);
        long c = folder("rb-rs-c", a);
        long f = file("rb-rs-f.txt", a);
        trashUseCase.moveFolderToTrash(a, ADMIN);
        var before = resources(a, c, f);
        var beforeTrash = trashRows(a);
        assertThat(beforeTrash).hasSize(1);

        Throwable thrown = failWithTrigger("tb_trash", "DELETE", "OLD.resource_seq = " + a,
                "SELECT count(*) FROM tb_resource WHERE resource_seq IN (" + a + "," + c + "," + f + ") AND is_delete = 'N' AND delete_parent_folder_seq IS NULL", 3,
                () -> trashUseCase.restoreFolder(a, false, ADMIN));

        assertThat(thrown).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(resources(a, c, f)).as("is_delete=Y·삭제 부모·경로 그대로").isEqualTo(before);
        assertThat(trashRows(a)).isEqualTo(beforeTrash);

        trashUseCase.restoreFolder(a, false, ADMIN);
        assertThat(jdbc.queryForList("SELECT is_delete FROM tb_resource WHERE resource_seq IN (?, ?, ?)", String.class, a, c, f)).containsOnly("N");
        assertThat(trashRows(a)).isEmpty();
    }

    @Test
    @DisplayName("6 버전 있는 파일 완전 삭제 — 버전·리소스 DELETE, 사용량 감소 뒤 tb_trash DELETE 실패 → 휴지통·버전·사용량 그대로, 스토리지 삭제 없음, 재시도 성공")
    void permanentDelete_trashDeleteFails_allRolledBack() {
        long f = file("rb-pd.txt", null);
        addVersion(f);
        trashUseCase.moveFileToTrash(f, ADMIN);
        var before = resources(f);
        var beforeVersions = versionRows(f);
        var beforeTrash = trashRows(f);
        long quotaBefore = quota(ADMIN);
        List<String> storageKeys = saveNames(f);
        assertThat(beforeVersions).hasSize(1);
        assertThat(beforeTrash).hasSize(1);

        Throwable thrown = failWithTrigger("tb_trash", "DELETE", "OLD.resource_seq = " + f,
                "SELECT (SELECT count(*) = 0 FROM tb_resource WHERE resource_seq = " + f + ")::int + (SELECT count(*) = 0 FROM tb_resource_version WHERE resource_seq = " + f + ")::int "
                        + "+ (SELECT count(*) FROM tb_user WHERE user_seq = " + ADMIN + " AND use_quota_byte = " + (quotaBefore - 15) + ")", 3,
                () -> trashUseCase.deleteFile(f, ADMIN, "admin"));

        assertThat(thrown).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(resources(f)).as("리소스 행 그대로(휴지통 상태)").isEqualTo(before);
        assertThat(versionRows(f)).as("버전 행 그대로").isEqualTo(beforeVersions);
        assertThat(trashRows(f)).isEqualTo(beforeTrash);
        assertThat(quota(ADMIN)).as("사용량 감소 롤백").isEqualTo(quotaBefore);
        assertThat(storageDeletionKeys(storageKeys)).as("스토리지 삭제 대기열 등록도 롤백").isEmpty();

        trashUseCase.deleteFile(f, ADMIN, "admin");
        assertThat(resources(f)).isEmpty();
        assertThat(versionRows(f)).isEmpty();
        assertThat(trashRows(f)).isEmpty();
        assertThat(quota(ADMIN)).as("파일 10 + 버전 5").isEqualTo(quotaBefore - 15);
        assertThat(storageDeletionKeys(storageKeys)).as("리소스 키 + 버전 키가 삭제 대기열에 등록").hasSize(2).containsExactlyInAnyOrderElementsOf(storageKeys);
    }

    // ------------------------------------------------------------------ 7·8 공유

    private static ShareTargetItemCommand viewTarget(int userSeq) {
        return new ShareTargetItemCommand(userSeq, true, false, false, false, false, false);
    }

    private ShareSaveCommand specificShare(long resourceSeq, int... targetUserSeqs) {
        var targets = java.util.Arrays.stream(targetUserSeqs).mapToObj(RollbackDbFailureIntegrationTest::viewTarget).toList();
        return new ShareSaveCommand(resourceSeq, ShareType.SPECIFIC_USERS, true, false, false, false, false, targets);
    }

    @Test
    @DisplayName("7 공유 등록(특정 사용자) — tb_share INSERT 뒤 tb_share_target INSERT 실패 → 공유 행 없음, 재시도 성공")
    void shareCreate_targetInsertFails_noShareRow() {
        long f = file("rb-sh.txt", null);
        int u1 = user("sh-u1");

        Throwable thrown = failWithTrigger("tb_share_target", "INSERT", "NEW.resource_seq = " + f,
                "SELECT count(*) FROM tb_share WHERE resource_seq = " + f, 1,
                () -> shareUseCase.save(specificShare(f, u1), ADMIN, "admin", "admin"));

        assertCode(thrown, ShareTargetErrorCode.INSERT_FAIL);
        assertThat(shareRows(f)).as("tb_share INSERT 도 롤백").isEmpty();
        assertThat(shareTargetRows(f)).isEmpty();

        shareUseCase.save(specificShare(f, u1), ADMIN, "admin", "admin");
        assertThat(shareRows(f)).hasSize(1);
        assertThat(shareTargetRows(f)).extracting(r -> r.get("user_seq")).containsExactly(u1);
    }

    @Test
    @DisplayName("8 공유 수정(대상 교체·권한 변경) — 빠진 대상 DELETE 뒤 남은 대상 권한 UPSERT 실패 → 이전 대상·권한 그대로, 재시도 성공")
    void shareUpdate_targetUpsertFails_previousTargetsKept() {
        long f = file("rb-su.txt", null);
        int u1 = user("su-u1");
        int u2 = user("su-u2");
        shareUseCase.save(specificShare(f, u1, u2), ADMIN, "admin", "admin");
        long shareSeq = jdbc.queryForObject("SELECT share_seq FROM tb_share WHERE resource_seq = ?", Long.class, f);
        var beforeShare = shareRows(f);
        var beforeTargets = shareTargetRows(f);
        assertThat(beforeTargets).hasSize(2);

        // u2 제거 + u1 권한 VIEW → VIEW+DOWNLOAD. 순서: u2 대상 DELETE → u1 upsert(ON CONFLICT DO UPDATE) ← 여기서 실패
        var update = new ShareUpdateCommand(ShareType.SPECIFIC_USERS, true, false, false, false, false,
                List.of(new ShareTargetItemCommand(u1, true, false, true, false, false, false)));
        Throwable thrown = failWithTrigger("tb_share_target", "UPDATE", "OLD.user_seq = " + u1 + " AND OLD.resource_seq = " + f,
                "SELECT (count(*) = 0)::int FROM tb_share_target WHERE resource_seq = " + f + " AND user_seq = " + u2, 1,
                () -> shareUseCase.update(shareSeq, update, ADMIN, "admin", "admin"));

        assertCode(thrown, ShareTargetErrorCode.INSERT_FAIL);
        assertThat(shareRows(f)).isEqualTo(beforeShare);
        assertThat(shareTargetRows(f)).as("u2 DELETE 롤백·u1 권한 그대로").isEqualTo(beforeTargets);

        shareUseCase.update(shareSeq, update, ADMIN, "admin", "admin");
        var targets = shareTargetRows(f);
        assertThat(targets).extracting(r -> r.get("user_seq")).containsExactly(u1);
        assertThat(targets.get(0).get("can_download")).isEqualTo("Y");
    }

    // ------------------------------------------------------------------ 9·10 관리자 사용자

    @Test
    @DisplayName("9 관리자 일괄 수정(2명) — 두 번째 사용자 UPDATE 실패 → 첫 번째 사용자도 그대로(한 트랜잭션), 재시도 성공")
    void adminBulkUpdate_secondUserFails_firstAlsoRolledBack() {
        int u1 = user("bu-u1");
        int u2 = user("bu-u2");
        var before = userRows(u1, u2);
        var command = new AdminUserBulkUpsertCommand(List.of(
                new UserItemCommand(u1, null, null, "rb-bu-u1-new", 1L, null, null, null),
                new UserItemCommand(u2, null, null, "rb-bu-u2-new", 1L, null, null, null)));

        Throwable thrown = failWithTrigger("tb_user", "UPDATE", "OLD.user_seq = " + u2 + " AND NEW.user_name IS DISTINCT FROM OLD.user_name",
                "SELECT count(*) FROM tb_user WHERE user_seq = " + u1 + " AND user_name = 'rb-bu-u1-new'", 1,
                () -> adminUserImportUseCase.bulkUpsert(command, ADMIN));

        assertCode(thrown, UserErrorCode.UPDATE_FAIL);
        assertThat(userRows(u1, u2)).as("첫 번째 사용자 UPDATE 도 롤백").isEqualTo(before);

        adminUserImportUseCase.bulkUpsert(command, ADMIN);
        assertThat(jdbc.queryForList("SELECT user_name FROM tb_user WHERE user_seq IN (?, ?) ORDER BY user_seq", String.class, u1, u2))
                .containsExactly("rb-bu-u1-new", "rb-bu-u2-new");
    }

    @Test
    @DisplayName("10 관리자 사용자 등록(프로필 이미지) — INSERT 뒤 프로필 키 UPDATE(커밋 flush) 실패 → 사용자 행 없음, 업로드 이미지 보상 삭제, 재시도 성공")
    void adminRegister_profileKeyUpdateFails_noUserRow() {
        String userId = TEST_USER_PREFIX + "reg@test.com";
        // PNG 시그니처 — 업로드 형식 검사(ProfileImageValidator)를 통과하는 최소 내용
        var image = new MockMultipartFile("profileImageFile", "p.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        var command = AdminRegisterUserCommand.builder().userId(userId).encPwd("Passw0rd!x").userName("rb-reg")
                .totalQuotaByte(1L).roles(java.util.Set.of(RoleType.USER)).profileImageFile(image).build();

        Throwable thrown = failWithTrigger("tb_user", "UPDATE", "NEW.user_id = '" + userId + "' AND NEW.profile_image_key IS NOT NULL",
                "SELECT count(*) FROM tb_user WHERE user_id = '" + userId + "' AND profile_image_key IS NULL", 1,
                () -> adminUserUseCase.registerUser(command, ADMIN));

        assertThat(thrown).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_user WHERE user_id = ?", Integer.class, userId)).as("INSERT 도 롤백").isZero();
        verify(storageService).delete(anyString());

        int userSeq = adminUserUseCase.registerUser(command, ADMIN);
        var saved = jdbc.queryForMap("SELECT user_id, profile_image_key FROM tb_user WHERE user_seq = ?", userSeq);
        assertThat(saved.get("user_id")).isEqualTo(userId);
        assertThat(saved.get("profile_image_key")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_storage_deletion WHERE storage_key = ?", Integer.class, saved.get("profile_image_key"))).as("저장된 프로필 이미지는 삭제 대기열에 없음").isZero();
    }
}
