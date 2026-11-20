package com.thinkfree.drive.permission;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.partition.PartitionKey;
import com.thinkfree.drive.permission.enums.SharePermissionType;
import com.thinkfree.drive.permission.model.Permissions;
import com.thinkfree.drive.permission.model.ResourcePermission;
import com.thinkfree.drive.permission.service.ResourcePermissionFindService;
import com.thinkfree.drive.permission.service.ResourcePermissionService;
import com.thinkfree.drive.permission.support.RequestActor;
import com.thinkfree.drive.share.enums.ShareType;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import com.thinkfree.drive.common.DriveIntegrationTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 리소스 접근 권한 룰 통합 검증.
 *
 * ⚠ 실행 환경: H2(test 프로파일)는 운영 쿼리의 WITH RECURSIVE CTE를 파싱하지 못한다
 *   (H2 2.3.232는 재귀 CTE에 컬럼 리스트를 요구). 따라서 이 테스트는 기존
 *   ResourcePermissionFindServiceTest 와 동일하게 실제 MySQL(개발/로컬 DB)을 쓰는
 *   @DriveIntegrationTest(test 프로필) 로 동작한다. 데이터는 직접 INSERT 하고 @Transactional 로 롤백한다.
 *   (seq 는 기존 데이터와 충돌하지 않도록 큰 값 사용)
 *
 * 룰:
 * - SHARE(L)        = 가장 가까운 링크 타입(ORG_LINK/PUBLIC_LINK) TB_SHARE
 * - SHARE_TARGET(T) = 내가 대상인 가장 가까운 TB_SHARE_TARGET
 * - useTarget = hasLink ? (L.SHARE_SEQ == T.SHARE_SEQ) : (T 있음)
 * - 권한       = useTarget ? 타겟권한 : (hasLink ? 링크권한 : invalid)
 * - 익명       = RequestActor.anonymous=true 면 checkPermission 내부에서 resolved SHARE_TYPE == PUBLIC_LINK 만 허용
 */
@DriveIntegrationTest
@Transactional
@Disabled
class ResourcePermissionRuleIntegrationTest {

    private static final int TENANT_A = 880001;   // OWNER/ME 의 테넌트
    private static final int TENANT_B = 880002;   // 외부 테넌트

    private static final int OWNER = 990001;     // 리소스 소유자(TENANT_A)
    private static final int ME = 990002;        // 접근 요청자(TENANT_A, owner 와 동일 테넌트)
    private static final int OTHER = 990003;     // 다른 사용자(타 target)
    private static final int EXTERNAL = 990004;  // 외부 테넌트 요청자(TENANT_B)

    private static final long ROOT = 99000001;   // 폴더
    private static final long CHILD = 99000002;  // ROOT 하위 파일
    private static final long ROOT2 = 99000003;  // 멀티체크용 폴더

    @Autowired
    private ResourcePermissionFindService findService;
    @Autowired
    private ResourcePermissionService permissionService;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUpUsers() {
        user(OWNER, TENANT_A);
        user(ME, TENANT_A);        // owner 와 동일 테넌트 → ORG_LINK 허용
        user(OTHER, TENANT_A);
        user(EXTERNAL, TENANT_B);  // 외부 테넌트 → ORG_LINK 차단 대상
    }

    // ── 케이스 1 : SPECIFIC + 내가 대상 → 타겟 권한 ──────────────────────────
    @Test
    @DisplayName("케이스1) SPECIFIC, 내가 대상이면 타겟 권한")
    void specific_withMyTarget_usesTargetPermission() {
        folder(ROOT, null);
        share(100, ROOT, "SPECIFIC_USERS", false, false, false, false, false, false);
        shareTarget(200, ME, 100, ROOT, true, true, false, false, false, false);

        ResourcePermission rp = findService.findResourcePermission(ME, key(ROOT), false);

        assertEquals(ShareType.SPECIFIC_USERS, rp.getShareType());
        assertGranted(rp.getPermissions(), true, true);   // view, edit (= 타겟 권한)
    }

    // ── 케이스 2 : SPECIFIC + 내가 대상 아님 → 거부 ────────────────────────
    @Test
    @DisplayName("케이스2) SPECIFIC, 내가 대상 아니면 거부(invalid)")
    void specific_withoutMyTarget_denied() {
        folder(ROOT, null);
        share(100, ROOT, "SPECIFIC_USERS", false, false, false, false, false, false);
        shareTarget(200, OTHER, 100, ROOT, true, true, false, false, false, false); // 타인 대상

        ResourcePermission rp = findService.findResourcePermission(ME, key(ROOT), false);
        assertGranted(rp.getPermissions(), false, false); // 전부 invalid

        assertThrows(ApiRuntimeException.class,
                () -> permissionService.checkPermission(SharePermissionType.VIEW, ROOT, OWNER, RequestActor.user(ME), false));
    }

    // ── 케이스 3 : ORG_LINK + 내 타겟 없음 → 링크 권한 ────────────────────
    @Test
    @DisplayName("케이스3) ORG_LINK, 내 타겟 없으면 링크 권한")
    void orgLink_noTarget_usesLinkPermission() {
        folder(ROOT, null);
        share(100, ROOT, "ORG_LINK", true, false, false, false, false, false); // view만

        ResourcePermission rp = findService.findResourcePermission(ME, key(ROOT), false);

        assertEquals(ShareType.ORG_LINK, rp.getShareType());
        assertGranted(rp.getPermissions(), true, false); // view만(=링크 권한)
    }

    // ── 케이스 4 : ORG_LINK + 그 공유에 내 타겟 → 타겟 권한 우선 ──────────
    @Test
    @DisplayName("케이스4) ORG_LINK, 같은 공유에 내 타겟 있으면 타겟 권한")
    void orgLink_withMyTargetOnSameShare_usesTargetPermission() {
        folder(ROOT, null);
        share(100, ROOT, "ORG_LINK", true, false, false, false, false, false); // 링크: view만
        shareTarget(200, ME, 100, ROOT, true, true, false, false, false, false); // 타겟: view+edit

        ResourcePermission rp = findService.findResourcePermission(ME, key(ROOT), false);

        assertGranted(rp.getPermissions(), true, true); // view+edit(=타겟 권한 우선)
    }

    // ── 케이스 5 : 중첩 - 가까운 링크엔 내 타겟 없고, 먼 공유에 내 타겟 → 가까운 링크 권한 ──
    @Test
    @DisplayName("케이스5) 중첩 시 먼 타겟 무시, 가까운 링크 권한 사용")
    void nested_nearestLinkWinsOverFarTarget() {
        folder(ROOT, null);
        share(100, ROOT, "ORG_LINK", true, false, false, false, false, false);
        shareTarget(200, ME, 100, ROOT, true, true, true, false, false, false); // 먼 상위 타겟: view+edit+download
        file(CHILD, ROOT);
        share(300, CHILD, "ORG_LINK", true, false, false, false, false, false); // 가까운 링크: view만, 내 타겟 없음

        ResourcePermission rp = findService.findResourcePermission(ME, key(CHILD), false);

        assertEquals(ShareType.ORG_LINK, rp.getShareType());
        assertGranted(rp.getPermissions(), true, false); // 가까운 링크(view만) — edit는 false여야 함(먼 타겟 무시)
    }

    // ── 케이스 6 : 공유 없음 → 거부 ───────────────────────────────────────
    @Test
    @DisplayName("케이스6) 공유 전혀 없으면 거부(invalid)")
    void noShare_denied() {
        folder(ROOT, null);

        ResourcePermission rp = findService.findResourcePermission(ME, key(ROOT), false);
        assertGranted(rp.getPermissions(), false, false);

        assertThrows(ApiRuntimeException.class,
                () -> permissionService.checkPermission(SharePermissionType.VIEW, ROOT, OWNER, RequestActor.user(ME), false));
    }

    // ── 케이스 7 : 소유자 본인 → 전권 ────────────────────────────────────
    @Test
    @DisplayName("케이스7) 소유자 본인은 전권")
    void owner_hasFullPermission() {
        folder(ROOT, null);

        ResourcePermission rp = findService.findResourcePermission(OWNER, key(ROOT), false);

        assertTrue(rp.getIsOwner());
        Permissions p = rp.getPermissions();
        assertTrue(Boolean.TRUE.equals(p.getCanView()));
        assertTrue(Boolean.TRUE.equals(p.getCanEdit()));
        assertTrue(Boolean.TRUE.equals(p.getCanDelete()));
    }

    // ── 케이스 8 : 익명 ──────────────────────────────────────────────────
    @Nested
    @DisplayName("케이스8) 익명(RequestActor.of(seq, true)) — checkPermission 내부 게이트")
    class Anonymous {

        private final RequestActor anonymous = RequestActor.of(ME, true);

        @Test
        @DisplayName("PUBLIC_LINK 이면 허용")
        void anonymous_publicLink_allowed() {
            folder(ROOT, null);
            share(100, ROOT, "PUBLIC_LINK", true, false, false, false, false, false);

            // 예외가 없어야 통과
            permissionService.checkPermission(SharePermissionType.VIEW, ROOT, OWNER, anonymous, false);
        }

        @Test
        @DisplayName("ORG_LINK 이면 거부")
        void anonymous_orgLink_denied() {
            folder(ROOT, null);
            share(100, ROOT, "ORG_LINK", true, false, false, false, false, false);

            assertThrows(ApiRuntimeException.class,
                    () -> permissionService.checkPermission(SharePermissionType.VIEW, ROOT, OWNER, anonymous, false));
        }

        @Test
        @DisplayName("SPECIFIC_USERS 이면 거부")
        void anonymous_specific_denied() {
            folder(ROOT, null);
            share(100, ROOT, "SPECIFIC_USERS", false, false, false, false, false, false);
            shareTarget(200, OTHER, 100, ROOT, true, false, false, false, false, false);

            assertThrows(ApiRuntimeException.class,
                    () -> permissionService.checkPermission(SharePermissionType.VIEW, ROOT, OWNER, anonymous, false));
        }
    }

    // ── 멀티 체크(checkPermissions) ──────────────────────────────────────
    @Test
    @DisplayName("멀티체크) 모두 공유되어 권한 있으면 통과")
    void multiCheck_allShared_pass() {
        folder(ROOT, null);
        share(100, ROOT, "ORG_LINK", true, false, false, false, false, false);
        folder(ROOT2, null);
        share(500, ROOT2, "ORG_LINK", true, false, false, false, false, false);

        permissionService.checkPermissions(
                SharePermissionType.VIEW, List.of(key(ROOT), key(ROOT2)), RequestActor.user(ME), false);
    }

    @Test
    @DisplayName("멀티체크) 하나라도 미공유면 거부")
    void multiCheck_oneUnshared_denied() {
        folder(ROOT, null);
        share(100, ROOT, "ORG_LINK", true, false, false, false, false, false);
        folder(ROOT2, null); // 공유 없음

        assertThrows(ApiRuntimeException.class,
                () -> permissionService.checkPermissions(
                        SharePermissionType.VIEW, List.of(key(ROOT), key(ROOT2)), RequestActor.user(ME), false));
    }

    // ── 테넌트 정책 ───────────────────────────────────────────────────────
    @Nested
    @DisplayName("테넌트 정책) ORG_LINK 는 요청자·소유주 테넌트 일치 시만, PUBLIC_LINK 는 무관, target 은 무관")
    class TenantPolicy {

        @Test
        @DisplayName("ORG_LINK, 요청자가 소유주와 다른 테넌트면 거부")
        void orgLink_differentTenant_denied() {
            folder(ROOT, null);
            share(100, ROOT, "ORG_LINK", true, false, false, false, false, false);

            ResourcePermission rp = findService.findResourcePermission(EXTERNAL, key(ROOT), false);
            assertGranted(rp.getPermissions(), false, false); // 링크 권한 거부(invalid)

            assertThrows(ApiRuntimeException.class,
                    () -> permissionService.checkPermission(SharePermissionType.VIEW, ROOT, OWNER, RequestActor.user(EXTERNAL), false));
        }

        @Test
        @DisplayName("ORG_LINK, 외부 테넌트라도 내 target 이 있으면 타겟 권한(target 은 테넌트 무관)")
        void orgLink_differentTenantWithMyTarget_usesTargetPermission() {
            folder(ROOT, null);
            share(100, ROOT, "ORG_LINK", true, false, false, false, false, false);   // 링크: view만 (외부 테넌트엔 차단)
            shareTarget(200, EXTERNAL, 100, ROOT, true, true, false, false, false, false); // 타겟: view+edit

            ResourcePermission rp = findService.findResourcePermission(EXTERNAL, key(ROOT), false);
            assertGranted(rp.getPermissions(), true, true); // 타겟 권한(외부 테넌트 허용)
        }

        @Test
        @DisplayName("PUBLIC_LINK 는 외부 테넌트라도 링크 권한 허용")
        void publicLink_differentTenant_allowed() {
            folder(ROOT, null);
            share(100, ROOT, "PUBLIC_LINK", true, false, false, false, false, false);

            ResourcePermission rp = findService.findResourcePermission(EXTERNAL, key(ROOT), false);
            assertEquals(ShareType.PUBLIC_LINK, rp.getShareType());
            assertGranted(rp.getPermissions(), true, false); // view만(=링크 권한)
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────
    private PartitionKey key(long resourceSeq) {
        return new PartitionKey(OWNER, resourceSeq);
    }

    private void assertGranted(Permissions p, boolean view, boolean edit) {
        assertEquals(view, Boolean.TRUE.equals(p.getCanView()), "canView");
        assertEquals(edit, Boolean.TRUE.equals(p.getCanEdit()), "canEdit");
    }

    private void user(int userSeq, Integer tenantSeq) {
        jdbc.update(
                "INSERT INTO tb_user (user_seq, tenant_seq, user_id, user_name, register_user_seq, status) " +
                "VALUES (?, ?, ?, ?, ?, 'ACTIVE')",
                userSeq, tenantSeq, "permtest_" + userSeq, "permtest_" + userSeq, userSeq);
    }

    private void folder(long resourceSeq, Long parentFolderSeq) {
        resource(resourceSeq, parentFolderSeq, "FOLDER");
    }

    private void file(long resourceSeq, Long parentFolderSeq) {
        resource(resourceSeq, parentFolderSeq, "FILE");
    }

    private void resource(long resourceSeq, Long parentFolderSeq, String type) {
        jdbc.update(
                "INSERT INTO tb_resource (resource_seq, user_seq, resource_type, resource_name, parent_folder_seq, register_user_seq, is_delete) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'N')",
                resourceSeq, OWNER, type, "res" + resourceSeq, parentFolderSeq, OWNER);
    }

    private void share(long shareSeq, long resourceSeq, String shareType,
                       boolean v, boolean e, boolean d, boolean u, boolean del, boolean sh) {
        jdbc.update(
                "INSERT INTO tb_share (share_seq, user_seq, resource_seq, share_type, " +
                "can_view, can_edit, can_download, can_upload, can_delete, can_share, register_user_seq) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                shareSeq, OWNER, resourceSeq, shareType,
                yn(v), yn(e), yn(d), yn(u), yn(del), yn(sh), OWNER);
    }

    private void shareTarget(long shareTargetSeq, int receiver, long shareSeq, long resourceSeq,
                             boolean v, boolean e, boolean d, boolean u, boolean del, boolean sh) {
        jdbc.update(
                "INSERT INTO tb_share_target (share_target_seq, user_seq, share_seq, resource_seq, shared_by_user_seq, share_date, " +
                "can_view, can_edit, can_download, can_upload, can_delete, can_share, register_user_seq) " +
                "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, ?, ?)",
                shareTargetSeq, receiver, shareSeq, resourceSeq, OWNER,
                yn(v), yn(e), yn(d), yn(u), yn(del), yn(sh), OWNER);
    }

    private String yn(boolean b) {
        return b ? "Y" : "N";
    }
}
