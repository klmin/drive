package com.thinkfree.drive.queryrepository;

import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.global.time.DriveTime;
import com.thinkfree.drive.partition.PartitionKey;
import com.thinkfree.drive.permission.query.ResourcePermissionQuery;
import com.thinkfree.drive.permission.query.ResourcesPermissionQuery;
import com.thinkfree.drive.permission.query.SharePermissionQuery;
import com.thinkfree.drive.permission.repository.ResourcePermissionQueryRepository;
import com.thinkfree.drive.permission.repository.SharePermissionQueryRepository;
import com.thinkfree.drive.resource.entity.Resource;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.repository.ResourceRepository;
import com.thinkfree.drive.share.query.*;
import com.thinkfree.drive.share.repository.ShareQueryRepository;
import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.user.enums.SignupType;
import com.thinkfree.drive.user.enums.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.mockito.BDDMockito.given;

/**
 * 공유·권한 체인 리포 3종(jOOQ) 기대값 단언 — 10-30 작업 B P4 에서 전환 전/후 비교 테스트를 대신한다(기대값은 비교 테스트로 구 JdbcClient 구현과 같음을 확인한 값). 트랜잭션 롤백.
 * <p>
 * 픽스처: 공유 3단(root 조직 링크 → deep 특정 사용자 → leaf 전체 공개, 권한 플래그 섞임·NULL 포함), 삭제된 공유 리소스·휴지통, 다른 테넌트 사용자의 2단 공유와 그 대상인 ADMIN.
 * 결과는 {@link Render} 로 필드를 늘어놓은 문자열(리소스·공유·사용자 seq 는 픽스처 이름, 시각은 t)로 비교한다.
 */
@DisplayName("공유·권한 체인 리포 3종 — 조상/하위 공유·권한 사슬 기대값(PostgreSQL Testcontainers)")
class ShareChainQueryRepositoryTest extends RepositoryTest {

    private static final int ADMIN = 2;
    private static final int SUPER_ADMIN = 1;

    @Autowired
    private ResourceRepository resourceRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ShareQueryRepository share;
    @Autowired
    private ResourcePermissionQueryRepository resourcePermission;
    @Autowired
    private SharePermissionQueryRepository sharePermission;

    private int viewer, other;
    private Resource root, sub, deep, leaf, leaf2, mid, gone, top, trashRoot, trashFile, oRoot, oSub, oFile;
    private long sRoot, sDeep, sLeaf, sTop, sGone, sTrash, sO, sOSub, sOFile;
    private final Map<Long, String> resourceNames = new HashMap<>();
    private final Map<Long, String> shareNames = new HashMap<>();

    @BeforeEach
    void seed() {
        loginAs(ADMIN);

        Integer adminTenant = jdbc.queryForObject("SELECT tenant_seq FROM tb_user WHERE user_seq = ?", Integer.class, ADMIN);
        Integer otherTenant = jdbc.queryForObject("INSERT INTO tb_tenant (tenant_name, register_user_seq) VALUES ('비교 테넌트', ?) RETURNING tenant_seq", Integer.class, SUPER_ADMIN);
        viewer = user("cmp-viewer", adminTenant, "뷰어");
        other = user("cmp-other", otherTenant, "다른 사용자");

        root = folder(ADMIN, "root", null);
        sub = folder(ADMIN, "sub", root);
        deep = folder(ADMIN, "deep", sub);
        leaf = file(ADMIN, "leaf.txt", deep);
        leaf2 = file(ADMIN, "leaf2.txt", deep);
        mid = file(ADMIN, "mid.txt", sub);
        gone = file(ADMIN, "gone.txt", root);
        top = file(ADMIN, "top.txt", null);
        trashRoot = folder(ADMIN, "trash-root", null);
        trashFile = file(ADMIN, "trash-file.txt", trashRoot);
        oRoot = folder(other, "o-root", null);
        oSub = folder(other, "o-sub", oRoot);
        oFile = file(other, "o.txt", oSub);

        jdbc.update("UPDATE tb_resource SET is_delete = 'Y' WHERE resource_seq IN (?, ?, ?)", seq(gone), seq(trashRoot), seq(trashFile));

        // 공유 3단(root 조직 링크 → deep 특정 사용자 → leaf 전체 공개), 권한 플래그 섞임(NULL 포함)
        sRoot = share("sRoot", ADMIN, root, "ORG_LINK", "'Y', 'N', 'Y', NULL, 'N', 'N'");
        sDeep = share("sDeep", ADMIN, deep, "SPECIFIC_USERS", "NULL, NULL, NULL, NULL, NULL, NULL");
        sLeaf = share("sLeaf", ADMIN, leaf, "PUBLIC_LINK", "'Y', 'N', 'N', 'N', 'N', 'N'");
        sTop = share("sTop", ADMIN, top, "SPECIFIC_USERS", "NULL, NULL, NULL, NULL, NULL, NULL");
        sGone = share("sGone", ADMIN, gone, "PUBLIC_LINK", "'Y', 'Y', 'Y', 'Y', 'Y', 'Y'");
        sTrash = share("sTrash", ADMIN, trashRoot, "SPECIFIC_USERS", "NULL, NULL, NULL, NULL, NULL, NULL");
        sO = share("sO", other, oRoot, "SPECIFIC_USERS", "NULL, NULL, NULL, NULL, NULL, NULL");
        sOSub = share("sOSub", other, oSub, "ORG_LINK", "'Y', 'N', 'Y', 'N', 'N', 'N'");
        sOFile = share("sOFile", other, oFile, "PUBLIC_LINK", "'Y', 'N', 'N', 'N', 'N', 'N'");
        target(viewer, sDeep, deep, ADMIN, "'Y', 'Y', 'Y', 'N', 'N', 'N'");
        target(other, sDeep, deep, ADMIN, "'Y', 'N', 'N', 'N', 'N', 'N'");
        target(viewer, sTop, top, ADMIN, "'Y', 'N', 'Y', 'N', 'N', 'Y'");
        target(viewer, sTrash, trashRoot, ADMIN, "'Y', 'N', 'N', 'N', 'N', 'N'");
        target(viewer, sO, oRoot, other, "'Y', 'Y', 'Y', 'Y', 'Y', 'Y'");
        target(ADMIN, sO, oRoot, other, "'Y', 'N', 'N', 'N', 'N', 'N'");
        jdbc.update("UPDATE tb_share SET password = 'pw', is_password_enabled = 'Y', expire_date = ? WHERE share_seq = ?",
                java.sql.Timestamp.valueOf("2031-01-01 00:00:00"), sLeaf);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------ 공유 단건·대상·해제 범위·조상 공유

    @Test
    @DisplayName("Share — findShareWithTargets·findUsersByShare·findUnsharedDescendantsByShare")
    void shareTargetsAndUnshared() {
        Snapshot s = snapshot();
        for (long[] sh : new long[][]{{ADMIN, sRoot}, {ADMIN, sDeep}, {ADMIN, sLeaf}, {ADMIN, sTop}, {ADMIN, sGone}, {ADMIN, sTrash}, {other, sO}, {other, sOSub}, {ADMIN, sO}}) {
            for (Boolean isDelete : new Boolean[]{false, true}) {
                String label = shareNames.get(sh[1]) + "/" + who((int) sh[0]) + "/" + isDelete;
                s.put(label + " withTargets", () -> share.findShareWithTargets(ShareWithTargetsFindQuery.builder().shareSeq(sh[1]).userSeq((int) sh[0]).isDelete(isDelete).build()));
                s.put(label + " users", () -> share.findUsersByShare(ShareUsersFindQuery.builder().shareSeq(sh[1]).userSeq((int) sh[0]).isDelete(isDelete).build()));
                s.put(label + " unshared", () -> share.findUnsharedDescendantsByShare(UnsharedResourcesFindQuery.builder().shareSeq(sh[1]).userSeq((int) sh[0]).isDelete(isDelete).build()));
            }
        }
        s.put("withTargets 삭제 상태 null", () -> share.findShareWithTargets(ShareWithTargetsFindQuery.builder().shareSeq(sRoot).userSeq(ADMIN).build()));
        s.put("withTargets 빈 조건", () -> share.findShareWithTargets(ShareWithTargetsFindQuery.builder().build()));
        s.put("withTargets 없는 공유", () -> share.findShareWithTargets(ShareWithTargetsFindQuery.builder().shareSeq(999_999_999L).userSeq(ADMIN).isDelete(false).build()));
        s.assertEquals("shareTargetsAndUnshared", Map.ofEntries(
                Map.entry("sRoot/admin/false withTargets", "(shareSeq=sRoot userSeq=admin resourceSeq=root shareType=ORG_LINK canView=true canEdit=false canDownload=true canDelete=false canShare=false isPasswordEnabled=false targets=[])"),
                Map.entry("sRoot/admin/false users", "[]"),
                Map.entry("sRoot/admin/false unshared", "(sharedByUserSeq=admin unsharedResources=[(userSeq=admin resourceSeq=mid.txt), (userSeq=admin resourceSeq=root), (userSeq=admin resourceSeq=sub)])"),
                Map.entry("sRoot/admin/true withTargets", "null"),
                Map.entry("sRoot/admin/true users", "[]"),
                Map.entry("sRoot/admin/true unshared", "null"),
                Map.entry("sDeep/admin/false withTargets", "(shareSeq=sDeep userSeq=admin resourceSeq=deep shareType=SPECIFIC_USERS isPasswordEnabled=false targets=[(shareTargetSeq=# userSeq=other userId=cmp-other userName=다른 사용자 userStatus=ACTIVE tenantSeq=# tenantName=비교 테넌트 canView=true canEdit=false canDownload=false canUpload=false canDelete=false canShare=false), (shareTargetSeq=# userSeq=viewer userId=cmp-viewer userName=뷰어 userStatus=ACTIVE tenantSeq=# tenantName=기본 테넌트 canView=true canEdit=true canDownload=true canUpload=false canDelete=false canShare=false)])"),
                Map.entry("sDeep/admin/false users", "[(userSeq=other userId=cmp-other userName=다른 사용자 userStatus=ACTIVE tenantSeq=# tenantName=비교 테넌트), (userSeq=viewer userId=cmp-viewer userName=뷰어 userStatus=ACTIVE tenantSeq=# tenantName=기본 테넌트)]"),
                Map.entry("sDeep/admin/false unshared", "(sharedByUserSeq=admin unsharedResources=[(userSeq=admin resourceSeq=deep), (userSeq=admin resourceSeq=leaf2.txt)])"),
                Map.entry("sDeep/admin/true withTargets", "null"),
                Map.entry("sDeep/admin/true users", "[]"),
                Map.entry("sDeep/admin/true unshared", "null"),
                Map.entry("sLeaf/admin/false withTargets", "(shareSeq=sLeaf userSeq=admin resourceSeq=leaf.txt shareType=PUBLIC_LINK canView=true canEdit=false canDownload=false canUpload=false canDelete=false canShare=false password=pw isPasswordEnabled=true expireDate=t targets=[])"),
                Map.entry("sLeaf/admin/false users", "[]"),
                Map.entry("sLeaf/admin/false unshared", "(sharedByUserSeq=admin unsharedResources=[(userSeq=admin resourceSeq=leaf.txt)])"),
                Map.entry("sLeaf/admin/true withTargets", "null"),
                Map.entry("sLeaf/admin/true users", "[]"),
                Map.entry("sLeaf/admin/true unshared", "null"),
                Map.entry("sTop/admin/false withTargets", "(shareSeq=sTop userSeq=admin resourceSeq=top.txt shareType=SPECIFIC_USERS isPasswordEnabled=false targets=[(shareTargetSeq=# userSeq=viewer userId=cmp-viewer userName=뷰어 userStatus=ACTIVE tenantSeq=# tenantName=기본 테넌트 canView=true canEdit=false canDownload=true canUpload=false canDelete=false canShare=true)])"),
                Map.entry("sTop/admin/false users", "[(userSeq=viewer userId=cmp-viewer userName=뷰어 userStatus=ACTIVE tenantSeq=# tenantName=기본 테넌트)]"),
                Map.entry("sTop/admin/false unshared", "(sharedByUserSeq=admin unsharedResources=[(userSeq=admin resourceSeq=top.txt)])"),
                Map.entry("sTop/admin/true withTargets", "null"),
                Map.entry("sTop/admin/true users", "[]"),
                Map.entry("sTop/admin/true unshared", "null"),
                Map.entry("sGone/admin/false withTargets", "null"),
                Map.entry("sGone/admin/false users", "[]"),
                Map.entry("sGone/admin/false unshared", "null"),
                Map.entry("sGone/admin/true withTargets", "(shareSeq=sGone userSeq=admin resourceSeq=gone.txt shareType=PUBLIC_LINK canView=true canEdit=true canDownload=true canUpload=true canDelete=true canShare=true isPasswordEnabled=false targets=[])"),
                Map.entry("sGone/admin/true users", "[]"),
                Map.entry("sGone/admin/true unshared", "(sharedByUserSeq=admin unsharedResources=[(userSeq=admin resourceSeq=gone.txt)])"),
                Map.entry("sTrash/admin/false withTargets", "null"),
                Map.entry("sTrash/admin/false users", "[]"),
                Map.entry("sTrash/admin/false unshared", "null"),
                Map.entry("sTrash/admin/true withTargets", "(shareSeq=sTrash userSeq=admin resourceSeq=trash-root shareType=SPECIFIC_USERS isPasswordEnabled=false targets=[(shareTargetSeq=# userSeq=viewer userId=cmp-viewer userName=뷰어 userStatus=ACTIVE tenantSeq=# tenantName=기본 테넌트 canView=true canEdit=false canDownload=false canUpload=false canDelete=false canShare=false)])"),
                Map.entry("sTrash/admin/true users", "[(userSeq=viewer userId=cmp-viewer userName=뷰어 userStatus=ACTIVE tenantSeq=# tenantName=기본 테넌트)]"),
                Map.entry("sTrash/admin/true unshared", "(sharedByUserSeq=admin unsharedResources=[(userSeq=admin resourceSeq=trash-file.txt), (userSeq=admin resourceSeq=trash-root)])"),
                Map.entry("sO/other/false withTargets", "(shareSeq=sO userSeq=other resourceSeq=o-root shareType=SPECIFIC_USERS isPasswordEnabled=false targets=[(shareTargetSeq=# userSeq=admin userId=admin userName=관리자 userStatus=ACTIVE tenantSeq=# tenantName=기본 테넌트 canView=true canEdit=false canDownload=false canUpload=false canDelete=false canShare=false), (shareTargetSeq=# userSeq=viewer userId=cmp-viewer userName=뷰어 userStatus=ACTIVE tenantSeq=# tenantName=기본 테넌트 canView=true canEdit=true canDownload=true canUpload=true canDelete=true canShare=true)])"),
                Map.entry("sO/other/false users", "[(userSeq=admin userId=admin userName=관리자 userStatus=ACTIVE tenantSeq=# tenantName=기본 테넌트), (userSeq=viewer userId=cmp-viewer userName=뷰어 userStatus=ACTIVE tenantSeq=# tenantName=기본 테넌트)]"),
                Map.entry("sO/other/false unshared", "(sharedByUserSeq=other unsharedResources=[(userSeq=other resourceSeq=o-root)])"),
                Map.entry("sO/other/true withTargets", "null"),
                Map.entry("sO/other/true users", "[]"),
                Map.entry("sO/other/true unshared", "null"),
                Map.entry("sOSub/other/false withTargets", "(shareSeq=sOSub userSeq=other resourceSeq=o-sub shareType=ORG_LINK canView=true canEdit=false canDownload=true canUpload=false canDelete=false canShare=false isPasswordEnabled=false targets=[])"),
                Map.entry("sOSub/other/false users", "[]"),
                Map.entry("sOSub/other/false unshared", "(sharedByUserSeq=other unsharedResources=[(userSeq=other resourceSeq=o-sub)])"),
                Map.entry("sOSub/other/true withTargets", "null"),
                Map.entry("sOSub/other/true users", "[]"),
                Map.entry("sOSub/other/true unshared", "null"),
                Map.entry("sO/admin/false withTargets", "null"),
                Map.entry("sO/admin/false users", "[]"),
                Map.entry("sO/admin/false unshared", "null"),
                Map.entry("sO/admin/true withTargets", "null"),
                Map.entry("sO/admin/true users", "[]"),
                Map.entry("sO/admin/true unshared", "null"),
                Map.entry("withTargets 삭제 상태 null", "null"),
                Map.entry("withTargets 빈 조건", "null"),
                Map.entry("withTargets 없는 공유", "null")));
    }

    @Test
    @DisplayName("Share — nearest 6종·findAncestorSharesByResource — 리소스·공유·루트 기준")
    void shareNearest() {
        Snapshot s = snapshot();
        for (Resource r : List.of(root, sub, deep, leaf, leaf2, mid, top, gone, oSub, oFile)) {
            String label = r.getResourceName() + "/" + who(r.getUserSeq());
            SharedResourceNearestFindQuery q = SharedResourceNearestFindQuery.builder().resourceSeq(seq(r)).rootResourceSeq(seq(r)).userSeq(r.getUserSeq()).isDelete(r == gone).build();
            s.put("ancestorByResource " + label, () -> share.findNearestAncestorShareByResource(q));
            s.put("selfOrAncestor " + label, () -> share.findNearestSelfOrAncestorShareByResource(q));
            s.put("descendantByResource " + label, () -> share.findNearestDescendantShareByResource(q));
            s.put("ancestorShares " + label, () -> sorted(share.findAncestorSharesByResource(SharedResourceFindQuery.builder().resourceSeq(seq(r)).userSeq(r.getUserSeq()).isDelete(r == gone).build())));
        }
        s.put("ancestorByResource 남의 키", () -> share.findNearestAncestorShareByResource(SharedResourceNearestFindQuery.builder().resourceSeq(seq(leaf)).userSeq(other).isDelete(false).build()));
        for (long[] sh : new long[][]{{ADMIN, sRoot}, {ADMIN, sDeep}, {ADMIN, sLeaf}, {other, sO}, {other, sOSub}, {other, sOFile}}) {
            for (Resource rootResource : new Resource[]{null, root, oRoot}) {
                String label = shareNames.get(sh[1]) + "/" + (rootResource == null ? null : rootResource.getResourceName());
                SharedResourceNearestFindQuery q = SharedResourceNearestFindQuery.builder().shareSeq(sh[1]).rootResourceSeq(seq(rootResource)).userSeq((int) sh[0]).isDelete(false).build();
                s.put("ancestorByShare " + label, () -> share.findNearestAncestorShareByShare(q));
                s.put("ancestorAndRoot " + label, () -> share.findNearestAncestorShareAndRootByShare(q));
                s.put("descendantByShare " + label, () -> share.findNearestDescendantShareByShare(q));
            }
        }
        s.assertEquals("shareNearest", Map.ofEntries(
                Map.entry("ancestorByResource root/admin", "(rootResourceSeq=root resourceType=FOLDER)"),
                Map.entry("selfOrAncestor root/admin", "(shareSeq=sRoot shareType=ORG_LINK resourceSeq=root rootResourceSeq=root resourceType=FOLDER)"),
                Map.entry("descendantByResource root/admin", "(shareSeq=sDeep shareType=SPECIFIC_USERS resourceSeq=deep)"),
                Map.entry("ancestorShares root/admin", "[(shareSeq=sRoot resourceSeq=root shareType=ORG_LINK resourceType=FOLDER)]"),
                Map.entry("ancestorByResource sub/admin", "(shareSeq=sRoot shareType=ORG_LINK resourceSeq=root rootResourceSeq=root resourceType=FOLDER)"),
                Map.entry("selfOrAncestor sub/admin", "(shareSeq=sRoot shareType=ORG_LINK resourceSeq=root rootResourceSeq=root resourceType=FOLDER)"),
                Map.entry("descendantByResource sub/admin", "(shareSeq=sDeep shareType=SPECIFIC_USERS resourceSeq=deep)"),
                Map.entry("ancestorShares sub/admin", "[(shareSeq=sRoot resourceSeq=root shareType=ORG_LINK resourceType=FOLDER)]"),
                Map.entry("ancestorByResource deep/admin", "(shareSeq=sRoot shareType=ORG_LINK resourceSeq=root rootResourceSeq=root resourceType=FOLDER)"),
                Map.entry("selfOrAncestor deep/admin", "(shareSeq=sDeep shareType=SPECIFIC_USERS resourceSeq=deep rootResourceSeq=root resourceType=FOLDER)"),
                Map.entry("descendantByResource deep/admin", "(shareSeq=sLeaf shareType=PUBLIC_LINK resourceSeq=leaf.txt)"),
                Map.entry("ancestorShares deep/admin", "[(shareSeq=sDeep resourceSeq=deep shareType=SPECIFIC_USERS resourceType=FOLDER), (shareSeq=sRoot resourceSeq=root shareType=ORG_LINK resourceType=FOLDER)]"),
                Map.entry("ancestorByResource leaf.txt/admin", "(shareSeq=sDeep shareType=SPECIFIC_USERS resourceSeq=deep rootResourceSeq=root resourceType=FOLDER)"),
                Map.entry("selfOrAncestor leaf.txt/admin", "(shareSeq=sLeaf shareType=PUBLIC_LINK resourceSeq=leaf.txt rootResourceSeq=root resourceType=FOLDER)"),
                Map.entry("descendantByResource leaf.txt/admin", "null"),
                Map.entry("ancestorShares leaf.txt/admin", "[(shareSeq=sDeep resourceSeq=deep shareType=SPECIFIC_USERS resourceType=FOLDER), (shareSeq=sLeaf resourceSeq=leaf.txt shareType=PUBLIC_LINK resourceType=FILE), (shareSeq=sRoot resourceSeq=root shareType=ORG_LINK resourceType=FOLDER)]"),
                Map.entry("ancestorByResource leaf2.txt/admin", "(shareSeq=sDeep shareType=SPECIFIC_USERS resourceSeq=deep rootResourceSeq=root resourceType=FOLDER)"),
                Map.entry("selfOrAncestor leaf2.txt/admin", "(shareSeq=sDeep shareType=SPECIFIC_USERS resourceSeq=deep rootResourceSeq=root resourceType=FOLDER)"),
                Map.entry("descendantByResource leaf2.txt/admin", "null"),
                Map.entry("ancestorShares leaf2.txt/admin", "[(shareSeq=sDeep resourceSeq=deep shareType=SPECIFIC_USERS resourceType=FOLDER), (shareSeq=sRoot resourceSeq=root shareType=ORG_LINK resourceType=FOLDER)]"),
                Map.entry("ancestorByResource mid.txt/admin", "(shareSeq=sRoot shareType=ORG_LINK resourceSeq=root rootResourceSeq=root resourceType=FOLDER)"),
                Map.entry("selfOrAncestor mid.txt/admin", "(shareSeq=sRoot shareType=ORG_LINK resourceSeq=root rootResourceSeq=root resourceType=FOLDER)"),
                Map.entry("descendantByResource mid.txt/admin", "null"),
                Map.entry("ancestorShares mid.txt/admin", "[(shareSeq=sRoot resourceSeq=root shareType=ORG_LINK resourceType=FOLDER)]"),
                Map.entry("ancestorByResource top.txt/admin", "(rootResourceSeq=top.txt resourceType=FILE)"),
                Map.entry("selfOrAncestor top.txt/admin", "(shareSeq=sTop shareType=SPECIFIC_USERS resourceSeq=top.txt rootResourceSeq=top.txt resourceType=FILE)"),
                Map.entry("descendantByResource top.txt/admin", "null"),
                Map.entry("ancestorShares top.txt/admin", "[(shareSeq=sTop resourceSeq=top.txt shareType=SPECIFIC_USERS resourceType=FILE)]"),
                Map.entry("ancestorByResource gone.txt/admin", "(rootResourceSeq=gone.txt resourceType=FILE)"),
                Map.entry("selfOrAncestor gone.txt/admin", "(shareSeq=sGone shareType=PUBLIC_LINK resourceSeq=gone.txt rootResourceSeq=gone.txt resourceType=FILE)"),
                Map.entry("descendantByResource gone.txt/admin", "null"),
                Map.entry("ancestorShares gone.txt/admin", "[(shareSeq=sGone resourceSeq=gone.txt shareType=PUBLIC_LINK resourceType=FILE)]"),
                Map.entry("ancestorByResource o-sub/other", "(shareSeq=sO shareType=SPECIFIC_USERS resourceSeq=o-root rootResourceSeq=o-root resourceType=FOLDER)"),
                Map.entry("selfOrAncestor o-sub/other", "(shareSeq=sOSub shareType=ORG_LINK resourceSeq=o-sub rootResourceSeq=o-root resourceType=FOLDER)"),
                Map.entry("descendantByResource o-sub/other", "(shareSeq=sOFile shareType=PUBLIC_LINK resourceSeq=o.txt)"),
                Map.entry("ancestorShares o-sub/other", "[(shareSeq=sO resourceSeq=o-root shareType=SPECIFIC_USERS resourceType=FOLDER), (shareSeq=sOSub resourceSeq=o-sub shareType=ORG_LINK resourceType=FOLDER)]"),
                Map.entry("ancestorByResource o.txt/other", "(shareSeq=sOSub shareType=ORG_LINK resourceSeq=o-sub rootResourceSeq=o-root resourceType=FOLDER)"),
                Map.entry("selfOrAncestor o.txt/other", "(shareSeq=sOFile shareType=PUBLIC_LINK resourceSeq=o.txt rootResourceSeq=o-root resourceType=FOLDER)"),
                Map.entry("descendantByResource o.txt/other", "null"),
                Map.entry("ancestorShares o.txt/other", "[(shareSeq=sO resourceSeq=o-root shareType=SPECIFIC_USERS resourceType=FOLDER), (shareSeq=sOFile resourceSeq=o.txt shareType=PUBLIC_LINK resourceType=FILE), (shareSeq=sOSub resourceSeq=o-sub shareType=ORG_LINK resourceType=FOLDER)]"),
                Map.entry("ancestorByResource 남의 키", "null"),
                Map.entry("ancestorByShare sRoot/null", "null"),
                Map.entry("ancestorAndRoot sRoot/null", "(rootResourceSeq=root resourceType=FOLDER)"),
                Map.entry("descendantByShare sRoot/null", "null"),
                Map.entry("ancestorByShare sRoot/root", "null"),
                Map.entry("ancestorAndRoot sRoot/root", "(rootResourceSeq=root resourceType=FOLDER)"),
                Map.entry("descendantByShare sRoot/root", "(shareSeq=sDeep shareType=SPECIFIC_USERS resourceSeq=deep)"),
                Map.entry("ancestorByShare sRoot/o-root", "null"),
                Map.entry("ancestorAndRoot sRoot/o-root", "(rootResourceSeq=root resourceType=FOLDER)"),
                Map.entry("descendantByShare sRoot/o-root", "null"),
                Map.entry("ancestorByShare sDeep/null", "(shareSeq=sRoot)"),
                Map.entry("ancestorAndRoot sDeep/null", "(shareSeq=sRoot shareType=ORG_LINK resourceSeq=root rootResourceSeq=root resourceType=FOLDER)"),
                Map.entry("descendantByShare sDeep/null", "null"),
                Map.entry("ancestorByShare sDeep/root", "(shareSeq=sRoot)"),
                Map.entry("ancestorAndRoot sDeep/root", "(shareSeq=sRoot shareType=ORG_LINK resourceSeq=root rootResourceSeq=root resourceType=FOLDER)"),
                Map.entry("descendantByShare sDeep/root", "(shareSeq=sRoot shareType=ORG_LINK resourceSeq=root)"),
                Map.entry("ancestorByShare sDeep/o-root", "(shareSeq=sRoot)"),
                Map.entry("ancestorAndRoot sDeep/o-root", "(shareSeq=sRoot shareType=ORG_LINK resourceSeq=root rootResourceSeq=root resourceType=FOLDER)"),
                Map.entry("descendantByShare sDeep/o-root", "null"),
                Map.entry("ancestorByShare sLeaf/null", "(shareSeq=sDeep)"),
                Map.entry("ancestorAndRoot sLeaf/null", "(shareSeq=sDeep shareType=SPECIFIC_USERS resourceSeq=deep rootResourceSeq=root resourceType=FOLDER)"),
                Map.entry("descendantByShare sLeaf/null", "null"),
                Map.entry("ancestorByShare sLeaf/root", "(shareSeq=sDeep)"),
                Map.entry("ancestorAndRoot sLeaf/root", "(shareSeq=sDeep shareType=SPECIFIC_USERS resourceSeq=deep rootResourceSeq=root resourceType=FOLDER)"),
                Map.entry("descendantByShare sLeaf/root", "(shareSeq=sRoot shareType=ORG_LINK resourceSeq=root)"),
                Map.entry("ancestorByShare sLeaf/o-root", "(shareSeq=sDeep)"),
                Map.entry("ancestorAndRoot sLeaf/o-root", "(shareSeq=sDeep shareType=SPECIFIC_USERS resourceSeq=deep rootResourceSeq=root resourceType=FOLDER)"),
                Map.entry("descendantByShare sLeaf/o-root", "null"),
                Map.entry("ancestorByShare sO/null", "null"),
                Map.entry("ancestorAndRoot sO/null", "(rootResourceSeq=o-root resourceType=FOLDER)"),
                Map.entry("descendantByShare sO/null", "null"),
                Map.entry("ancestorByShare sO/root", "null"),
                Map.entry("ancestorAndRoot sO/root", "(rootResourceSeq=o-root resourceType=FOLDER)"),
                Map.entry("descendantByShare sO/root", "null"),
                Map.entry("ancestorByShare sO/o-root", "null"),
                Map.entry("ancestorAndRoot sO/o-root", "(rootResourceSeq=o-root resourceType=FOLDER)"),
                Map.entry("descendantByShare sO/o-root", "(shareSeq=sOSub shareType=ORG_LINK resourceSeq=o-sub)"),
                Map.entry("ancestorByShare sOSub/null", "(shareSeq=sO)"),
                Map.entry("ancestorAndRoot sOSub/null", "(shareSeq=sO shareType=SPECIFIC_USERS resourceSeq=o-root rootResourceSeq=o-root resourceType=FOLDER)"),
                Map.entry("descendantByShare sOSub/null", "null"),
                Map.entry("ancestorByShare sOSub/root", "(shareSeq=sO)"),
                Map.entry("ancestorAndRoot sOSub/root", "(shareSeq=sO shareType=SPECIFIC_USERS resourceSeq=o-root rootResourceSeq=o-root resourceType=FOLDER)"),
                Map.entry("descendantByShare sOSub/root", "null"),
                Map.entry("ancestorByShare sOSub/o-root", "(shareSeq=sO)"),
                Map.entry("ancestorAndRoot sOSub/o-root", "(shareSeq=sO shareType=SPECIFIC_USERS resourceSeq=o-root rootResourceSeq=o-root resourceType=FOLDER)"),
                Map.entry("descendantByShare sOSub/o-root", "(shareSeq=sO shareType=SPECIFIC_USERS resourceSeq=o-root)"),
                Map.entry("ancestorByShare sOFile/null", "(shareSeq=sOSub)"),
                Map.entry("ancestorAndRoot sOFile/null", "(shareSeq=sOSub shareType=ORG_LINK resourceSeq=o-sub rootResourceSeq=o-root resourceType=FOLDER)"),
                Map.entry("descendantByShare sOFile/null", "null"),
                Map.entry("ancestorByShare sOFile/root", "(shareSeq=sOSub)"),
                Map.entry("ancestorAndRoot sOFile/root", "(shareSeq=sOSub shareType=ORG_LINK resourceSeq=o-sub rootResourceSeq=o-root resourceType=FOLDER)"),
                Map.entry("descendantByShare sOFile/root", "null"),
                Map.entry("ancestorByShare sOFile/o-root", "(shareSeq=sOSub)"),
                Map.entry("ancestorAndRoot sOFile/o-root", "(shareSeq=sOSub shareType=ORG_LINK resourceSeq=o-sub rootResourceSeq=o-root resourceType=FOLDER)"),
                Map.entry("descendantByShare sOFile/o-root", "(shareSeq=sO shareType=SPECIFIC_USERS resourceSeq=o-root)")));
    }

    // ------------------------------------------------------------------ 권한

    @Test
    @DisplayName("ResourcePermission — 단건·N건 권한과 사슬 — 리소스 × 요청자, 키 목록 섞임, 소유 계정 상태")
    void resourcePermission() {
        Snapshot s = snapshot();
        for (Resource r : List.of(root, sub, deep, leaf, leaf2, mid, top, gone, trashFile, oRoot, oFile)) {
            for (Integer requester : new Integer[]{ADMIN, viewer, other}) {
                String label = r.getResourceName() + "/" + who(requester);
                ResourcePermissionQuery q = ResourcePermissionQuery.builder().key(new PartitionKey(r.getUserSeq(), seq(r))).userSeq(requester).isDelete(r == gone || r == trashFile).build();
                s.put("permission " + label, () -> resourcePermission.findResourcePermission(q));
                s.put("chain " + label, () -> resourcePermission.findResourceShareChain(q));
            }
        }
        s.put("permission 소유자 키 틀림", () -> resourcePermission.findResourcePermission(
                ResourcePermissionQuery.builder().key(new PartitionKey(other, seq(leaf))).userSeq(viewer).isDelete(false).build()));
        List<PartitionKey> keys = List.of(new PartitionKey(ADMIN, seq(leaf)), new PartitionKey(ADMIN, seq(mid)), new PartitionKey(other, seq(oFile)),
                new PartitionKey(ADMIN, seq(top)), new PartitionKey(other, seq(leaf)), new PartitionKey(ADMIN, 999_999_999L));
        for (Integer requester : new Integer[]{viewer, other}) {
            ResourcesPermissionQuery q = ResourcesPermissionQuery.builder().keys(keys).userSeq(requester).isDelete(false).build();
            s.put("permissions " + who(requester), () -> resourcePermission.findResourcePermissions(q));
            s.put("chains " + who(requester), () -> resourcePermission.findResourcesShareChain(q));
        }
        s.put("permissions 빈 키", () -> resourcePermission.findResourcePermissions(ResourcesPermissionQuery.builder().keys(List.of()).userSeq(viewer).isDelete(false).build()));
        // 소유 계정 노출 상태 — INACTIVE 는 보이고 DELETED 는 안 보임
        for (String status : List.of("INACTIVE", "DELETED")) {
            jdbc.update("UPDATE tb_user SET status = ? WHERE user_seq = ?", status, other);
            ResourcePermissionQuery q = ResourcePermissionQuery.builder().key(new PartitionKey(other, seq(oFile))).userSeq(viewer).isDelete(false).build();
            s.put("permission 소유자 " + status, () -> resourcePermission.findResourcePermission(q));
            s.put("chain 소유자 " + status, () -> resourcePermission.findResourceShareChain(q));
        }
        jdbc.update("UPDATE tb_user SET status = 'ACTIVE' WHERE user_seq = ?", other);
        s.assertEquals("resourcePermission", Map.ofEntries(
                Map.entry("permission root/admin", "(userSeq=admin resourceSeq=root shareSeq=sRoot shareResourceSeq=root shareType=ORG_LINK shareCanView=true shareCanEdit=false shareCanDownload=true shareCanUpload=false shareCanDelete=false shareCanShare=false shareDepth=0 shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false)"),
                Map.entry("chain root/admin", "(resourceSeq=root userSeq=admin chains=[(userSeq=admin resourceSeq=root ancestorResourceSeq=root resourceName=root shareSeq=sRoot shareType=ORG_LINK depth=0)])"),
                Map.entry("permission root/viewer", "(userSeq=admin resourceSeq=root shareSeq=sRoot shareResourceSeq=root shareType=ORG_LINK shareCanView=true shareCanEdit=false shareCanDownload=true shareCanUpload=false shareCanDelete=false shareCanShare=false shareDepth=0 shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false)"),
                Map.entry("chain root/viewer", "(resourceSeq=root userSeq=admin chains=[(userSeq=admin resourceSeq=root ancestorResourceSeq=root resourceName=root shareSeq=sRoot shareType=ORG_LINK depth=0)])"),
                Map.entry("permission root/other", "(userSeq=admin resourceSeq=root shareCanView=false shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false)"),
                Map.entry("chain root/other", "(resourceSeq=root userSeq=admin chains=[(userSeq=admin resourceSeq=root ancestorResourceSeq=root resourceName=root shareSeq=sRoot shareType=ORG_LINK depth=0)])"),
                Map.entry("permission sub/admin", "(userSeq=admin resourceSeq=sub shareSeq=sRoot shareResourceSeq=root shareType=ORG_LINK shareCanView=true shareCanEdit=false shareCanDownload=true shareCanUpload=false shareCanDelete=false shareCanShare=false shareDepth=1 shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false)"),
                Map.entry("chain sub/admin", "(resourceSeq=sub userSeq=admin chains=[(userSeq=admin resourceSeq=sub ancestorResourceSeq=sub resourceName=sub parentFolderSeq=root parentFolderName=root fullPathSeq=root/ fullPath=root/ depth=0), (userSeq=admin resourceSeq=sub ancestorResourceSeq=root resourceName=sub parentFolderSeq=root parentFolderName=root fullPathSeq=root/ fullPath=root/ shareSeq=sRoot shareType=ORG_LINK depth=1)])"),
                Map.entry("permission sub/viewer", "(userSeq=admin resourceSeq=sub shareSeq=sRoot shareResourceSeq=root shareType=ORG_LINK shareCanView=true shareCanEdit=false shareCanDownload=true shareCanUpload=false shareCanDelete=false shareCanShare=false shareDepth=1 shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false)"),
                Map.entry("chain sub/viewer", "(resourceSeq=sub userSeq=admin chains=[(userSeq=admin resourceSeq=sub ancestorResourceSeq=sub resourceName=sub parentFolderSeq=root parentFolderName=root fullPathSeq=root/ fullPath=root/ depth=0), (userSeq=admin resourceSeq=sub ancestorResourceSeq=root resourceName=sub parentFolderSeq=root parentFolderName=root fullPathSeq=root/ fullPath=root/ shareSeq=sRoot shareType=ORG_LINK depth=1)])"),
                Map.entry("permission sub/other", "(userSeq=admin resourceSeq=sub shareCanView=false shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false)"),
                Map.entry("chain sub/other", "(resourceSeq=sub userSeq=admin chains=[(userSeq=admin resourceSeq=sub ancestorResourceSeq=sub resourceName=sub parentFolderSeq=root parentFolderName=root fullPathSeq=root/ fullPath=root/ depth=0), (userSeq=admin resourceSeq=sub ancestorResourceSeq=root resourceName=sub parentFolderSeq=root parentFolderName=root fullPathSeq=root/ fullPath=root/ shareSeq=sRoot shareType=ORG_LINK depth=1)])"),
                Map.entry("permission deep/admin", "(userSeq=admin resourceSeq=deep shareSeq=sDeep shareResourceSeq=deep shareType=SPECIFIC_USERS shareCanView=false shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareDepth=0 shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false)"),
                Map.entry("chain deep/admin", "(resourceSeq=deep userSeq=admin chains=[(userSeq=admin resourceSeq=deep ancestorResourceSeq=deep resourceName=deep parentFolderSeq=sub parentFolderName=sub fullPathSeq=root/sub/ fullPath=root/sub/ shareSeq=sDeep shareType=SPECIFIC_USERS depth=0), (userSeq=admin resourceSeq=deep ancestorResourceSeq=sub resourceName=deep parentFolderSeq=sub parentFolderName=sub fullPathSeq=root/sub/ fullPath=root/sub/ depth=1), (userSeq=admin resourceSeq=deep ancestorResourceSeq=root resourceName=deep parentFolderSeq=sub parentFolderName=sub fullPathSeq=root/sub/ fullPath=root/sub/ shareSeq=sRoot shareType=ORG_LINK depth=2)])"),
                Map.entry("permission deep/viewer", "(userSeq=admin resourceSeq=deep shareSeq=sRoot shareResourceSeq=root shareType=ORG_LINK shareCanView=true shareCanEdit=false shareCanDownload=true shareCanUpload=false shareCanDelete=false shareCanShare=false shareDepth=2 shareTargetShareSeq=sDeep shareTargetSeq=# shareTargetShareResourceSeq=deep shareTargetShareType=SPECIFIC_USERS shareTargetCanView=true shareTargetCanEdit=true shareTargetCanDownload=true shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false shareTargetDepth=0)"),
                Map.entry("chain deep/viewer", "(resourceSeq=deep userSeq=admin chains=[(userSeq=admin resourceSeq=deep ancestorResourceSeq=deep resourceName=deep parentFolderSeq=sub parentFolderName=sub fullPathSeq=root/sub/ fullPath=root/sub/ farthestShareResourceSeq=deep farthestResourceName=deep shareSeq=sDeep shareType=SPECIFIC_USERS shareTargetSeq=# depth=0), (userSeq=admin resourceSeq=deep ancestorResourceSeq=sub resourceName=deep parentFolderSeq=sub parentFolderName=sub fullPathSeq=root/sub/ fullPath=root/sub/ depth=1), (userSeq=admin resourceSeq=deep ancestorResourceSeq=root resourceName=deep parentFolderSeq=sub parentFolderName=sub fullPathSeq=root/sub/ fullPath=root/sub/ shareSeq=sRoot shareType=ORG_LINK depth=2)])"),
                Map.entry("permission deep/other", "(userSeq=admin resourceSeq=deep shareCanView=false shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareTargetShareSeq=sDeep shareTargetSeq=# shareTargetShareResourceSeq=deep shareTargetShareType=SPECIFIC_USERS shareTargetCanView=true shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false shareTargetDepth=0)"),
                Map.entry("chain deep/other", "(resourceSeq=deep userSeq=admin chains=[(userSeq=admin resourceSeq=deep ancestorResourceSeq=deep resourceName=deep parentFolderSeq=sub parentFolderName=sub fullPathSeq=root/sub/ fullPath=root/sub/ farthestShareResourceSeq=deep farthestResourceName=deep shareSeq=sDeep shareType=SPECIFIC_USERS shareTargetSeq=# depth=0), (userSeq=admin resourceSeq=deep ancestorResourceSeq=sub resourceName=deep parentFolderSeq=sub parentFolderName=sub fullPathSeq=root/sub/ fullPath=root/sub/ depth=1), (userSeq=admin resourceSeq=deep ancestorResourceSeq=root resourceName=deep parentFolderSeq=sub parentFolderName=sub fullPathSeq=root/sub/ fullPath=root/sub/ shareSeq=sRoot shareType=ORG_LINK depth=2)])"),
                Map.entry("permission leaf.txt/admin", "(userSeq=admin resourceSeq=leaf.txt shareSeq=sLeaf shareResourceSeq=leaf.txt shareType=PUBLIC_LINK shareCanView=true shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareDepth=0 shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false)"),
                Map.entry("chain leaf.txt/admin", "(resourceSeq=leaf.txt userSeq=admin chains=[(userSeq=admin resourceSeq=leaf.txt ancestorResourceSeq=leaf.txt resourceName=leaf.txt parentFolderSeq=deep parentFolderName=deep fullPathSeq=root/sub/deep/ fullPath=root/sub/deep/ shareSeq=sLeaf shareType=PUBLIC_LINK depth=0), (userSeq=admin resourceSeq=leaf.txt ancestorResourceSeq=deep resourceName=leaf.txt parentFolderSeq=deep parentFolderName=deep fullPathSeq=root/sub/deep/ fullPath=root/sub/deep/ shareSeq=sDeep shareType=SPECIFIC_USERS depth=1), (userSeq=admin resourceSeq=leaf.txt ancestorResourceSeq=sub resourceName=leaf.txt parentFolderSeq=deep parentFolderName=deep fullPathSeq=root/sub/deep/ fullPath=root/sub/deep/ depth=2), (userSeq=admin resourceSeq=leaf.txt ancestorResourceSeq=root resourceName=leaf.txt parentFolderSeq=deep parentFolderName=deep fullPathSeq=root/sub/deep/ fullPath=root/sub/deep/ shareSeq=sRoot shareType=ORG_LINK depth=3)])"),
                Map.entry("permission leaf.txt/viewer", "(userSeq=admin resourceSeq=leaf.txt shareSeq=sLeaf shareResourceSeq=leaf.txt shareType=PUBLIC_LINK shareCanView=true shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareDepth=0 shareTargetShareSeq=sDeep shareTargetSeq=# shareTargetShareResourceSeq=deep shareTargetShareType=SPECIFIC_USERS shareTargetCanView=true shareTargetCanEdit=true shareTargetCanDownload=true shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false shareTargetDepth=1)"),
                Map.entry("chain leaf.txt/viewer", "(resourceSeq=leaf.txt userSeq=admin chains=[(userSeq=admin resourceSeq=leaf.txt ancestorResourceSeq=leaf.txt resourceName=leaf.txt parentFolderSeq=deep parentFolderName=deep fullPathSeq=root/sub/deep/ fullPath=root/sub/deep/ shareSeq=sLeaf shareType=PUBLIC_LINK depth=0), (userSeq=admin resourceSeq=leaf.txt ancestorResourceSeq=deep resourceName=leaf.txt parentFolderSeq=deep parentFolderName=deep fullPathSeq=root/sub/deep/ fullPath=root/sub/deep/ farthestShareResourceSeq=deep farthestResourceName=deep shareSeq=sDeep shareType=SPECIFIC_USERS shareTargetSeq=# depth=1), (userSeq=admin resourceSeq=leaf.txt ancestorResourceSeq=sub resourceName=leaf.txt parentFolderSeq=deep parentFolderName=deep fullPathSeq=root/sub/deep/ fullPath=root/sub/deep/ depth=2), (userSeq=admin resourceSeq=leaf.txt ancestorResourceSeq=root resourceName=leaf.txt parentFolderSeq=deep parentFolderName=deep fullPathSeq=root/sub/deep/ fullPath=root/sub/deep/ shareSeq=sRoot shareType=ORG_LINK depth=3)])"),
                Map.entry("permission leaf.txt/other", "(userSeq=admin resourceSeq=leaf.txt shareSeq=sLeaf shareResourceSeq=leaf.txt shareType=PUBLIC_LINK shareCanView=true shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareDepth=0 shareTargetShareSeq=sDeep shareTargetSeq=# shareTargetShareResourceSeq=deep shareTargetShareType=SPECIFIC_USERS shareTargetCanView=true shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false shareTargetDepth=1)"),
                Map.entry("chain leaf.txt/other", "(resourceSeq=leaf.txt userSeq=admin chains=[(userSeq=admin resourceSeq=leaf.txt ancestorResourceSeq=leaf.txt resourceName=leaf.txt parentFolderSeq=deep parentFolderName=deep fullPathSeq=root/sub/deep/ fullPath=root/sub/deep/ shareSeq=sLeaf shareType=PUBLIC_LINK depth=0), (userSeq=admin resourceSeq=leaf.txt ancestorResourceSeq=deep resourceName=leaf.txt parentFolderSeq=deep parentFolderName=deep fullPathSeq=root/sub/deep/ fullPath=root/sub/deep/ farthestShareResourceSeq=deep farthestResourceName=deep shareSeq=sDeep shareType=SPECIFIC_USERS shareTargetSeq=# depth=1), (userSeq=admin resourceSeq=leaf.txt ancestorResourceSeq=sub resourceName=leaf.txt parentFolderSeq=deep parentFolderName=deep fullPathSeq=root/sub/deep/ fullPath=root/sub/deep/ depth=2), (userSeq=admin resourceSeq=leaf.txt ancestorResourceSeq=root resourceName=leaf.txt parentFolderSeq=deep parentFolderName=deep fullPathSeq=root/sub/deep/ fullPath=root/sub/deep/ shareSeq=sRoot shareType=ORG_LINK depth=3)])"),
                Map.entry("permission leaf2.txt/admin", "(userSeq=admin resourceSeq=leaf2.txt shareSeq=sDeep shareResourceSeq=deep shareType=SPECIFIC_USERS shareCanView=false shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareDepth=1 shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false)"),
                Map.entry("chain leaf2.txt/admin", "(resourceSeq=leaf2.txt userSeq=admin chains=[(userSeq=admin resourceSeq=leaf2.txt ancestorResourceSeq=leaf2.txt resourceName=leaf2.txt parentFolderSeq=deep parentFolderName=deep fullPathSeq=root/sub/deep/ fullPath=root/sub/deep/ depth=0), (userSeq=admin resourceSeq=leaf2.txt ancestorResourceSeq=deep resourceName=leaf2.txt parentFolderSeq=deep parentFolderName=deep fullPathSeq=root/sub/deep/ fullPath=root/sub/deep/ shareSeq=sDeep shareType=SPECIFIC_USERS depth=1), (userSeq=admin resourceSeq=leaf2.txt ancestorResourceSeq=sub resourceName=leaf2.txt parentFolderSeq=deep parentFolderName=deep fullPathSeq=root/sub/deep/ fullPath=root/sub/deep/ depth=2), (userSeq=admin resourceSeq=leaf2.txt ancestorResourceSeq=root resourceName=leaf2.txt parentFolderSeq=deep parentFolderName=deep fullPathSeq=root/sub/deep/ fullPath=root/sub/deep/ shareSeq=sRoot shareType=ORG_LINK depth=3)])"),
                Map.entry("permission leaf2.txt/viewer", "(userSeq=admin resourceSeq=leaf2.txt shareSeq=sRoot shareResourceSeq=root shareType=ORG_LINK shareCanView=true shareCanEdit=false shareCanDownload=true shareCanUpload=false shareCanDelete=false shareCanShare=false shareDepth=3 shareTargetShareSeq=sDeep shareTargetSeq=# shareTargetShareResourceSeq=deep shareTargetShareType=SPECIFIC_USERS shareTargetCanView=true shareTargetCanEdit=true shareTargetCanDownload=true shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false shareTargetDepth=1)"),
                Map.entry("chain leaf2.txt/viewer", "(resourceSeq=leaf2.txt userSeq=admin chains=[(userSeq=admin resourceSeq=leaf2.txt ancestorResourceSeq=leaf2.txt resourceName=leaf2.txt parentFolderSeq=deep parentFolderName=deep fullPathSeq=root/sub/deep/ fullPath=root/sub/deep/ depth=0), (userSeq=admin resourceSeq=leaf2.txt ancestorResourceSeq=deep resourceName=leaf2.txt parentFolderSeq=deep parentFolderName=deep fullPathSeq=root/sub/deep/ fullPath=root/sub/deep/ farthestShareResourceSeq=deep farthestResourceName=deep shareSeq=sDeep shareType=SPECIFIC_USERS shareTargetSeq=# depth=1), (userSeq=admin resourceSeq=leaf2.txt ancestorResourceSeq=sub resourceName=leaf2.txt parentFolderSeq=deep parentFolderName=deep fullPathSeq=root/sub/deep/ fullPath=root/sub/deep/ depth=2), (userSeq=admin resourceSeq=leaf2.txt ancestorResourceSeq=root resourceName=leaf2.txt parentFolderSeq=deep parentFolderName=deep fullPathSeq=root/sub/deep/ fullPath=root/sub/deep/ shareSeq=sRoot shareType=ORG_LINK depth=3)])"),
                Map.entry("permission leaf2.txt/other", "(userSeq=admin resourceSeq=leaf2.txt shareCanView=false shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareTargetShareSeq=sDeep shareTargetSeq=# shareTargetShareResourceSeq=deep shareTargetShareType=SPECIFIC_USERS shareTargetCanView=true shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false shareTargetDepth=1)"),
                Map.entry("chain leaf2.txt/other", "(resourceSeq=leaf2.txt userSeq=admin chains=[(userSeq=admin resourceSeq=leaf2.txt ancestorResourceSeq=leaf2.txt resourceName=leaf2.txt parentFolderSeq=deep parentFolderName=deep fullPathSeq=root/sub/deep/ fullPath=root/sub/deep/ depth=0), (userSeq=admin resourceSeq=leaf2.txt ancestorResourceSeq=deep resourceName=leaf2.txt parentFolderSeq=deep parentFolderName=deep fullPathSeq=root/sub/deep/ fullPath=root/sub/deep/ farthestShareResourceSeq=deep farthestResourceName=deep shareSeq=sDeep shareType=SPECIFIC_USERS shareTargetSeq=# depth=1), (userSeq=admin resourceSeq=leaf2.txt ancestorResourceSeq=sub resourceName=leaf2.txt parentFolderSeq=deep parentFolderName=deep fullPathSeq=root/sub/deep/ fullPath=root/sub/deep/ depth=2), (userSeq=admin resourceSeq=leaf2.txt ancestorResourceSeq=root resourceName=leaf2.txt parentFolderSeq=deep parentFolderName=deep fullPathSeq=root/sub/deep/ fullPath=root/sub/deep/ shareSeq=sRoot shareType=ORG_LINK depth=3)])"),
                Map.entry("permission mid.txt/admin", "(userSeq=admin resourceSeq=mid.txt shareSeq=sRoot shareResourceSeq=root shareType=ORG_LINK shareCanView=true shareCanEdit=false shareCanDownload=true shareCanUpload=false shareCanDelete=false shareCanShare=false shareDepth=2 shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false)"),
                Map.entry("chain mid.txt/admin", "(resourceSeq=mid.txt userSeq=admin chains=[(userSeq=admin resourceSeq=mid.txt ancestorResourceSeq=mid.txt resourceName=mid.txt parentFolderSeq=sub parentFolderName=sub fullPathSeq=root/sub/ fullPath=root/sub/ depth=0), (userSeq=admin resourceSeq=mid.txt ancestorResourceSeq=sub resourceName=mid.txt parentFolderSeq=sub parentFolderName=sub fullPathSeq=root/sub/ fullPath=root/sub/ depth=1), (userSeq=admin resourceSeq=mid.txt ancestorResourceSeq=root resourceName=mid.txt parentFolderSeq=sub parentFolderName=sub fullPathSeq=root/sub/ fullPath=root/sub/ shareSeq=sRoot shareType=ORG_LINK depth=2)])"),
                Map.entry("permission mid.txt/viewer", "(userSeq=admin resourceSeq=mid.txt shareSeq=sRoot shareResourceSeq=root shareType=ORG_LINK shareCanView=true shareCanEdit=false shareCanDownload=true shareCanUpload=false shareCanDelete=false shareCanShare=false shareDepth=2 shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false)"),
                Map.entry("chain mid.txt/viewer", "(resourceSeq=mid.txt userSeq=admin chains=[(userSeq=admin resourceSeq=mid.txt ancestorResourceSeq=mid.txt resourceName=mid.txt parentFolderSeq=sub parentFolderName=sub fullPathSeq=root/sub/ fullPath=root/sub/ depth=0), (userSeq=admin resourceSeq=mid.txt ancestorResourceSeq=sub resourceName=mid.txt parentFolderSeq=sub parentFolderName=sub fullPathSeq=root/sub/ fullPath=root/sub/ depth=1), (userSeq=admin resourceSeq=mid.txt ancestorResourceSeq=root resourceName=mid.txt parentFolderSeq=sub parentFolderName=sub fullPathSeq=root/sub/ fullPath=root/sub/ shareSeq=sRoot shareType=ORG_LINK depth=2)])"),
                Map.entry("permission mid.txt/other", "(userSeq=admin resourceSeq=mid.txt shareCanView=false shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false)"),
                Map.entry("chain mid.txt/other", "(resourceSeq=mid.txt userSeq=admin chains=[(userSeq=admin resourceSeq=mid.txt ancestorResourceSeq=mid.txt resourceName=mid.txt parentFolderSeq=sub parentFolderName=sub fullPathSeq=root/sub/ fullPath=root/sub/ depth=0), (userSeq=admin resourceSeq=mid.txt ancestorResourceSeq=sub resourceName=mid.txt parentFolderSeq=sub parentFolderName=sub fullPathSeq=root/sub/ fullPath=root/sub/ depth=1), (userSeq=admin resourceSeq=mid.txt ancestorResourceSeq=root resourceName=mid.txt parentFolderSeq=sub parentFolderName=sub fullPathSeq=root/sub/ fullPath=root/sub/ shareSeq=sRoot shareType=ORG_LINK depth=2)])"),
                Map.entry("permission top.txt/admin", "(userSeq=admin resourceSeq=top.txt shareSeq=sTop shareResourceSeq=top.txt shareType=SPECIFIC_USERS shareCanView=false shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareDepth=0 shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false)"),
                Map.entry("chain top.txt/admin", "(resourceSeq=top.txt userSeq=admin chains=[(userSeq=admin resourceSeq=top.txt ancestorResourceSeq=top.txt resourceName=top.txt shareSeq=sTop shareType=SPECIFIC_USERS depth=0)])"),
                Map.entry("permission top.txt/viewer", "(userSeq=admin resourceSeq=top.txt shareCanView=false shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareTargetShareSeq=sTop shareTargetSeq=# shareTargetShareResourceSeq=top.txt shareTargetShareType=SPECIFIC_USERS shareTargetCanView=true shareTargetCanEdit=false shareTargetCanDownload=true shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=true shareTargetDepth=0)"),
                Map.entry("chain top.txt/viewer", "(resourceSeq=top.txt userSeq=admin chains=[(userSeq=admin resourceSeq=top.txt ancestorResourceSeq=top.txt resourceName=top.txt shareSeq=sTop shareType=SPECIFIC_USERS shareTargetSeq=# depth=0)])"),
                Map.entry("permission top.txt/other", "(userSeq=admin resourceSeq=top.txt shareCanView=false shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false)"),
                Map.entry("chain top.txt/other", "(resourceSeq=top.txt userSeq=admin chains=[(userSeq=admin resourceSeq=top.txt ancestorResourceSeq=top.txt resourceName=top.txt shareSeq=sTop shareType=SPECIFIC_USERS depth=0)])"),
                Map.entry("permission gone.txt/admin", "(userSeq=admin resourceSeq=gone.txt shareSeq=sGone shareResourceSeq=gone.txt shareType=PUBLIC_LINK shareCanView=true shareCanEdit=true shareCanDownload=true shareCanUpload=true shareCanDelete=true shareCanShare=true shareDepth=0 shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false)"),
                Map.entry("chain gone.txt/admin", "(resourceSeq=gone.txt userSeq=admin chains=[(userSeq=admin resourceSeq=gone.txt ancestorResourceSeq=gone.txt resourceName=gone.txt parentFolderSeq=root parentFolderName=root fullPathSeq=root/ fullPath=root/ shareSeq=sGone shareType=PUBLIC_LINK depth=0)])"),
                Map.entry("permission gone.txt/viewer", "(userSeq=admin resourceSeq=gone.txt shareSeq=sGone shareResourceSeq=gone.txt shareType=PUBLIC_LINK shareCanView=true shareCanEdit=true shareCanDownload=true shareCanUpload=true shareCanDelete=true shareCanShare=true shareDepth=0 shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false)"),
                Map.entry("chain gone.txt/viewer", "(resourceSeq=gone.txt userSeq=admin chains=[(userSeq=admin resourceSeq=gone.txt ancestorResourceSeq=gone.txt resourceName=gone.txt parentFolderSeq=root parentFolderName=root fullPathSeq=root/ fullPath=root/ shareSeq=sGone shareType=PUBLIC_LINK depth=0)])"),
                Map.entry("permission gone.txt/other", "(userSeq=admin resourceSeq=gone.txt shareSeq=sGone shareResourceSeq=gone.txt shareType=PUBLIC_LINK shareCanView=true shareCanEdit=true shareCanDownload=true shareCanUpload=true shareCanDelete=true shareCanShare=true shareDepth=0 shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false)"),
                Map.entry("chain gone.txt/other", "(resourceSeq=gone.txt userSeq=admin chains=[(userSeq=admin resourceSeq=gone.txt ancestorResourceSeq=gone.txt resourceName=gone.txt parentFolderSeq=root parentFolderName=root fullPathSeq=root/ fullPath=root/ shareSeq=sGone shareType=PUBLIC_LINK depth=0)])"),
                Map.entry("permission trash-file.txt/admin", "(userSeq=admin resourceSeq=trash-file.txt shareSeq=sTrash shareResourceSeq=trash-root shareType=SPECIFIC_USERS shareCanView=false shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareDepth=1 shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false)"),
                Map.entry("chain trash-file.txt/admin", "(resourceSeq=trash-file.txt userSeq=admin chains=[(userSeq=admin resourceSeq=trash-file.txt ancestorResourceSeq=trash-file.txt resourceName=trash-file.txt parentFolderSeq=trash-root parentFolderName=trash-root fullPathSeq=trash-root/ fullPath=trash-root/ depth=0), (userSeq=admin resourceSeq=trash-file.txt ancestorResourceSeq=trash-root resourceName=trash-file.txt parentFolderSeq=trash-root parentFolderName=trash-root fullPathSeq=trash-root/ fullPath=trash-root/ shareSeq=sTrash shareType=SPECIFIC_USERS depth=1)])"),
                Map.entry("permission trash-file.txt/viewer", "(userSeq=admin resourceSeq=trash-file.txt shareCanView=false shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareTargetShareSeq=sTrash shareTargetSeq=# shareTargetShareResourceSeq=trash-root shareTargetShareType=SPECIFIC_USERS shareTargetCanView=true shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false shareTargetDepth=1)"),
                Map.entry("chain trash-file.txt/viewer", "(resourceSeq=trash-file.txt userSeq=admin chains=[(userSeq=admin resourceSeq=trash-file.txt ancestorResourceSeq=trash-file.txt resourceName=trash-file.txt parentFolderSeq=trash-root parentFolderName=trash-root fullPathSeq=trash-root/ fullPath=trash-root/ depth=0), (userSeq=admin resourceSeq=trash-file.txt ancestorResourceSeq=trash-root resourceName=trash-file.txt parentFolderSeq=trash-root parentFolderName=trash-root fullPathSeq=trash-root/ fullPath=trash-root/ farthestShareResourceSeq=trash-root farthestResourceName=trash-root shareSeq=sTrash shareType=SPECIFIC_USERS shareTargetSeq=# depth=1)])"),
                Map.entry("permission trash-file.txt/other", "(userSeq=admin resourceSeq=trash-file.txt shareCanView=false shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false)"),
                Map.entry("chain trash-file.txt/other", "(resourceSeq=trash-file.txt userSeq=admin chains=[(userSeq=admin resourceSeq=trash-file.txt ancestorResourceSeq=trash-file.txt resourceName=trash-file.txt parentFolderSeq=trash-root parentFolderName=trash-root fullPathSeq=trash-root/ fullPath=trash-root/ depth=0), (userSeq=admin resourceSeq=trash-file.txt ancestorResourceSeq=trash-root resourceName=trash-file.txt parentFolderSeq=trash-root parentFolderName=trash-root fullPathSeq=trash-root/ fullPath=trash-root/ shareSeq=sTrash shareType=SPECIFIC_USERS depth=1)])"),
                Map.entry("permission o-root/admin", "(userSeq=other resourceSeq=o-root shareCanView=false shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareTargetShareSeq=sO shareTargetSeq=# shareTargetShareResourceSeq=o-root shareTargetShareType=SPECIFIC_USERS shareTargetCanView=true shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false shareTargetDepth=0)"),
                Map.entry("chain o-root/admin", "(resourceSeq=o-root userSeq=other chains=[(userSeq=other resourceSeq=o-root ancestorResourceSeq=o-root resourceName=o-root farthestShareResourceSeq=o-root farthestResourceName=o-root shareSeq=sO shareType=SPECIFIC_USERS shareTargetSeq=# depth=0)])"),
                Map.entry("permission o-root/viewer", "(userSeq=other resourceSeq=o-root shareCanView=false shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareTargetShareSeq=sO shareTargetSeq=# shareTargetShareResourceSeq=o-root shareTargetShareType=SPECIFIC_USERS shareTargetCanView=true shareTargetCanEdit=true shareTargetCanDownload=true shareTargetCanUpload=true shareTargetCanDelete=true shareTargetCanShare=true shareTargetDepth=0)"),
                Map.entry("chain o-root/viewer", "(resourceSeq=o-root userSeq=other chains=[(userSeq=other resourceSeq=o-root ancestorResourceSeq=o-root resourceName=o-root farthestShareResourceSeq=o-root farthestResourceName=o-root shareSeq=sO shareType=SPECIFIC_USERS shareTargetSeq=# depth=0)])"),
                Map.entry("permission o-root/other", "(userSeq=other resourceSeq=o-root shareSeq=sO shareResourceSeq=o-root shareType=SPECIFIC_USERS shareCanView=false shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareDepth=0 shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false)"),
                Map.entry("chain o-root/other", "(resourceSeq=o-root userSeq=other chains=[(userSeq=other resourceSeq=o-root ancestorResourceSeq=o-root resourceName=o-root shareSeq=sO shareType=SPECIFIC_USERS depth=0)])"),
                Map.entry("permission o.txt/admin", "(userSeq=other resourceSeq=o.txt shareSeq=sOFile shareResourceSeq=o.txt shareType=PUBLIC_LINK shareCanView=true shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareDepth=0 shareTargetShareSeq=sO shareTargetSeq=# shareTargetShareResourceSeq=o-root shareTargetShareType=SPECIFIC_USERS shareTargetCanView=true shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false shareTargetDepth=2)"),
                Map.entry("chain o.txt/admin", "(resourceSeq=o.txt userSeq=other chains=[(userSeq=other resourceSeq=o.txt ancestorResourceSeq=o.txt resourceName=o.txt parentFolderSeq=o-sub parentFolderName=o-sub fullPathSeq=o-root/o-sub/ fullPath=o-root/o-sub/ shareSeq=sOFile shareType=PUBLIC_LINK depth=0), (userSeq=other resourceSeq=o.txt ancestorResourceSeq=o-sub resourceName=o.txt parentFolderSeq=o-sub parentFolderName=o-sub fullPathSeq=o-root/o-sub/ fullPath=o-root/o-sub/ shareSeq=sOSub shareType=ORG_LINK depth=1), (userSeq=other resourceSeq=o.txt ancestorResourceSeq=o-root resourceName=o.txt parentFolderSeq=o-sub parentFolderName=o-sub fullPathSeq=o-root/o-sub/ fullPath=o-root/o-sub/ farthestShareResourceSeq=o-root farthestResourceName=o-root shareSeq=sO shareType=SPECIFIC_USERS shareTargetSeq=# depth=2)])"),
                Map.entry("permission o.txt/viewer", "(userSeq=other resourceSeq=o.txt shareSeq=sOFile shareResourceSeq=o.txt shareType=PUBLIC_LINK shareCanView=true shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareDepth=0 shareTargetShareSeq=sO shareTargetSeq=# shareTargetShareResourceSeq=o-root shareTargetShareType=SPECIFIC_USERS shareTargetCanView=true shareTargetCanEdit=true shareTargetCanDownload=true shareTargetCanUpload=true shareTargetCanDelete=true shareTargetCanShare=true shareTargetDepth=2)"),
                Map.entry("chain o.txt/viewer", "(resourceSeq=o.txt userSeq=other chains=[(userSeq=other resourceSeq=o.txt ancestorResourceSeq=o.txt resourceName=o.txt parentFolderSeq=o-sub parentFolderName=o-sub fullPathSeq=o-root/o-sub/ fullPath=o-root/o-sub/ shareSeq=sOFile shareType=PUBLIC_LINK depth=0), (userSeq=other resourceSeq=o.txt ancestorResourceSeq=o-sub resourceName=o.txt parentFolderSeq=o-sub parentFolderName=o-sub fullPathSeq=o-root/o-sub/ fullPath=o-root/o-sub/ shareSeq=sOSub shareType=ORG_LINK depth=1), (userSeq=other resourceSeq=o.txt ancestorResourceSeq=o-root resourceName=o.txt parentFolderSeq=o-sub parentFolderName=o-sub fullPathSeq=o-root/o-sub/ fullPath=o-root/o-sub/ farthestShareResourceSeq=o-root farthestResourceName=o-root shareSeq=sO shareType=SPECIFIC_USERS shareTargetSeq=# depth=2)])"),
                Map.entry("permission o.txt/other", "(userSeq=other resourceSeq=o.txt shareSeq=sOFile shareResourceSeq=o.txt shareType=PUBLIC_LINK shareCanView=true shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareDepth=0 shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false)"),
                Map.entry("chain o.txt/other", "(resourceSeq=o.txt userSeq=other chains=[(userSeq=other resourceSeq=o.txt ancestorResourceSeq=o.txt resourceName=o.txt parentFolderSeq=o-sub parentFolderName=o-sub fullPathSeq=o-root/o-sub/ fullPath=o-root/o-sub/ shareSeq=sOFile shareType=PUBLIC_LINK depth=0), (userSeq=other resourceSeq=o.txt ancestorResourceSeq=o-sub resourceName=o.txt parentFolderSeq=o-sub parentFolderName=o-sub fullPathSeq=o-root/o-sub/ fullPath=o-root/o-sub/ shareSeq=sOSub shareType=ORG_LINK depth=1), (userSeq=other resourceSeq=o.txt ancestorResourceSeq=o-root resourceName=o.txt parentFolderSeq=o-sub parentFolderName=o-sub fullPathSeq=o-root/o-sub/ fullPath=o-root/o-sub/ shareSeq=sO shareType=SPECIFIC_USERS depth=2)])"),
                Map.entry("permission 소유자 키 틀림", "null"),
                Map.entry("permissions viewer", "[(userSeq=admin resourceSeq=leaf.txt shareSeq=sLeaf shareResourceSeq=leaf.txt shareType=PUBLIC_LINK shareCanView=true shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareDepth=0 shareTargetShareSeq=sDeep shareTargetSeq=# shareTargetShareResourceSeq=deep shareTargetShareType=SPECIFIC_USERS shareTargetCanView=true shareTargetCanEdit=true shareTargetCanDownload=true shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false shareTargetDepth=1), (userSeq=admin resourceSeq=mid.txt shareSeq=sRoot shareResourceSeq=root shareType=ORG_LINK shareCanView=true shareCanEdit=false shareCanDownload=true shareCanUpload=false shareCanDelete=false shareCanShare=false shareDepth=2 shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false), (userSeq=admin resourceSeq=top.txt shareCanView=false shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareTargetShareSeq=sTop shareTargetSeq=# shareTargetShareResourceSeq=top.txt shareTargetShareType=SPECIFIC_USERS shareTargetCanView=true shareTargetCanEdit=false shareTargetCanDownload=true shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=true shareTargetDepth=0), (userSeq=other resourceSeq=o.txt shareSeq=sOFile shareResourceSeq=o.txt shareType=PUBLIC_LINK shareCanView=true shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareDepth=0 shareTargetShareSeq=sO shareTargetSeq=# shareTargetShareResourceSeq=o-root shareTargetShareType=SPECIFIC_USERS shareTargetCanView=true shareTargetCanEdit=true shareTargetCanDownload=true shareTargetCanUpload=true shareTargetCanDelete=true shareTargetCanShare=true shareTargetDepth=2)]"),
                Map.entry("chains viewer", "[(resourceSeq=leaf.txt userSeq=admin chains=[(userSeq=admin resourceSeq=leaf.txt ancestorResourceSeq=leaf.txt resourceName=leaf.txt parentFolderSeq=deep parentFolderName=deep fullPathSeq=root/sub/deep/ fullPath=root/sub/deep/ shareSeq=sLeaf shareType=PUBLIC_LINK depth=0), (userSeq=admin resourceSeq=leaf.txt ancestorResourceSeq=deep resourceName=leaf.txt parentFolderSeq=deep parentFolderName=deep fullPathSeq=root/sub/deep/ fullPath=root/sub/deep/ farthestShareResourceSeq=deep farthestResourceName=deep shareSeq=sDeep shareType=SPECIFIC_USERS shareTargetSeq=# depth=1), (userSeq=admin resourceSeq=leaf.txt ancestorResourceSeq=sub resourceName=leaf.txt parentFolderSeq=deep parentFolderName=deep fullPathSeq=root/sub/deep/ fullPath=root/sub/deep/ depth=2), (userSeq=admin resourceSeq=leaf.txt ancestorResourceSeq=root resourceName=leaf.txt parentFolderSeq=deep parentFolderName=deep fullPathSeq=root/sub/deep/ fullPath=root/sub/deep/ shareSeq=sRoot shareType=ORG_LINK depth=3)]), (resourceSeq=mid.txt userSeq=admin chains=[(userSeq=admin resourceSeq=mid.txt ancestorResourceSeq=mid.txt resourceName=mid.txt parentFolderSeq=sub parentFolderName=sub fullPathSeq=root/sub/ fullPath=root/sub/ depth=0), (userSeq=admin resourceSeq=mid.txt ancestorResourceSeq=sub resourceName=mid.txt parentFolderSeq=sub parentFolderName=sub fullPathSeq=root/sub/ fullPath=root/sub/ depth=1), (userSeq=admin resourceSeq=mid.txt ancestorResourceSeq=root resourceName=mid.txt parentFolderSeq=sub parentFolderName=sub fullPathSeq=root/sub/ fullPath=root/sub/ shareSeq=sRoot shareType=ORG_LINK depth=2)]), (resourceSeq=top.txt userSeq=admin chains=[(userSeq=admin resourceSeq=top.txt ancestorResourceSeq=top.txt resourceName=top.txt shareSeq=sTop shareType=SPECIFIC_USERS shareTargetSeq=# depth=0)]), (resourceSeq=o.txt userSeq=other chains=[(userSeq=other resourceSeq=o.txt ancestorResourceSeq=o.txt resourceName=o.txt parentFolderSeq=o-sub parentFolderName=o-sub fullPathSeq=o-root/o-sub/ fullPath=o-root/o-sub/ shareSeq=sOFile shareType=PUBLIC_LINK depth=0), (userSeq=other resourceSeq=o.txt ancestorResourceSeq=o-sub resourceName=o.txt parentFolderSeq=o-sub parentFolderName=o-sub fullPathSeq=o-root/o-sub/ fullPath=o-root/o-sub/ shareSeq=sOSub shareType=ORG_LINK depth=1), (userSeq=other resourceSeq=o.txt ancestorResourceSeq=o-root resourceName=o.txt parentFolderSeq=o-sub parentFolderName=o-sub fullPathSeq=o-root/o-sub/ fullPath=o-root/o-sub/ farthestShareResourceSeq=o-root farthestResourceName=o-root shareSeq=sO shareType=SPECIFIC_USERS shareTargetSeq=# depth=2)])]"),
                Map.entry("permissions other", "[(userSeq=admin resourceSeq=leaf.txt shareSeq=sLeaf shareResourceSeq=leaf.txt shareType=PUBLIC_LINK shareCanView=true shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareDepth=0 shareTargetShareSeq=sDeep shareTargetSeq=# shareTargetShareResourceSeq=deep shareTargetShareType=SPECIFIC_USERS shareTargetCanView=true shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false shareTargetDepth=1), (userSeq=admin resourceSeq=mid.txt shareCanView=false shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false), (userSeq=admin resourceSeq=top.txt shareCanView=false shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false), (userSeq=other resourceSeq=o.txt shareSeq=sOFile shareResourceSeq=o.txt shareType=PUBLIC_LINK shareCanView=true shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareDepth=0 shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false)]"),
                Map.entry("chains other", "[(resourceSeq=leaf.txt userSeq=admin chains=[(userSeq=admin resourceSeq=leaf.txt ancestorResourceSeq=leaf.txt resourceName=leaf.txt parentFolderSeq=deep parentFolderName=deep fullPathSeq=root/sub/deep/ fullPath=root/sub/deep/ shareSeq=sLeaf shareType=PUBLIC_LINK depth=0), (userSeq=admin resourceSeq=leaf.txt ancestorResourceSeq=deep resourceName=leaf.txt parentFolderSeq=deep parentFolderName=deep fullPathSeq=root/sub/deep/ fullPath=root/sub/deep/ farthestShareResourceSeq=deep farthestResourceName=deep shareSeq=sDeep shareType=SPECIFIC_USERS shareTargetSeq=# depth=1), (userSeq=admin resourceSeq=leaf.txt ancestorResourceSeq=sub resourceName=leaf.txt parentFolderSeq=deep parentFolderName=deep fullPathSeq=root/sub/deep/ fullPath=root/sub/deep/ depth=2), (userSeq=admin resourceSeq=leaf.txt ancestorResourceSeq=root resourceName=leaf.txt parentFolderSeq=deep parentFolderName=deep fullPathSeq=root/sub/deep/ fullPath=root/sub/deep/ shareSeq=sRoot shareType=ORG_LINK depth=3)]), (resourceSeq=mid.txt userSeq=admin chains=[(userSeq=admin resourceSeq=mid.txt ancestorResourceSeq=mid.txt resourceName=mid.txt parentFolderSeq=sub parentFolderName=sub fullPathSeq=root/sub/ fullPath=root/sub/ depth=0), (userSeq=admin resourceSeq=mid.txt ancestorResourceSeq=sub resourceName=mid.txt parentFolderSeq=sub parentFolderName=sub fullPathSeq=root/sub/ fullPath=root/sub/ depth=1), (userSeq=admin resourceSeq=mid.txt ancestorResourceSeq=root resourceName=mid.txt parentFolderSeq=sub parentFolderName=sub fullPathSeq=root/sub/ fullPath=root/sub/ shareSeq=sRoot shareType=ORG_LINK depth=2)]), (resourceSeq=top.txt userSeq=admin chains=[(userSeq=admin resourceSeq=top.txt ancestorResourceSeq=top.txt resourceName=top.txt shareSeq=sTop shareType=SPECIFIC_USERS depth=0)]), (resourceSeq=o.txt userSeq=other chains=[(userSeq=other resourceSeq=o.txt ancestorResourceSeq=o.txt resourceName=o.txt parentFolderSeq=o-sub parentFolderName=o-sub fullPathSeq=o-root/o-sub/ fullPath=o-root/o-sub/ shareSeq=sOFile shareType=PUBLIC_LINK depth=0), (userSeq=other resourceSeq=o.txt ancestorResourceSeq=o-sub resourceName=o.txt parentFolderSeq=o-sub parentFolderName=o-sub fullPathSeq=o-root/o-sub/ fullPath=o-root/o-sub/ shareSeq=sOSub shareType=ORG_LINK depth=1), (userSeq=other resourceSeq=o.txt ancestorResourceSeq=o-root resourceName=o.txt parentFolderSeq=o-sub parentFolderName=o-sub fullPathSeq=o-root/o-sub/ fullPath=o-root/o-sub/ shareSeq=sO shareType=SPECIFIC_USERS depth=2)])]"),
                Map.entry("permissions 빈 키", "[]"),
                Map.entry("permission 소유자 INACTIVE", "(userSeq=other resourceSeq=o.txt shareSeq=sOFile shareResourceSeq=o.txt shareType=PUBLIC_LINK shareCanView=true shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareDepth=0 shareTargetShareSeq=sO shareTargetSeq=# shareTargetShareResourceSeq=o-root shareTargetShareType=SPECIFIC_USERS shareTargetCanView=true shareTargetCanEdit=true shareTargetCanDownload=true shareTargetCanUpload=true shareTargetCanDelete=true shareTargetCanShare=true shareTargetDepth=2)"),
                Map.entry("chain 소유자 INACTIVE", "(resourceSeq=o.txt userSeq=other chains=[(userSeq=other resourceSeq=o.txt ancestorResourceSeq=o.txt resourceName=o.txt parentFolderSeq=o-sub parentFolderName=o-sub fullPathSeq=o-root/o-sub/ fullPath=o-root/o-sub/ shareSeq=sOFile shareType=PUBLIC_LINK depth=0), (userSeq=other resourceSeq=o.txt ancestorResourceSeq=o-sub resourceName=o.txt parentFolderSeq=o-sub parentFolderName=o-sub fullPathSeq=o-root/o-sub/ fullPath=o-root/o-sub/ shareSeq=sOSub shareType=ORG_LINK depth=1), (userSeq=other resourceSeq=o.txt ancestorResourceSeq=o-root resourceName=o.txt parentFolderSeq=o-sub parentFolderName=o-sub fullPathSeq=o-root/o-sub/ fullPath=o-root/o-sub/ farthestShareResourceSeq=o-root farthestResourceName=o-root shareSeq=sO shareType=SPECIFIC_USERS shareTargetSeq=# depth=2)])"),
                Map.entry("permission 소유자 DELETED", "null"),
                Map.entry("chain 소유자 DELETED", "null")));
    }

    @Test
    @DisplayName("SharePermission — 공유 × 요청자(소유자 키 맞음·틀림)")
    void sharePermission() {
        Snapshot s = snapshot();
        for (long[] sh : new long[][]{{ADMIN, sRoot}, {ADMIN, sDeep}, {ADMIN, sLeaf}, {ADMIN, sTop}, {ADMIN, sGone}, {other, sO}, {other, sOSub}, {other, sOFile}}) {
            for (Integer requester : new Integer[]{ADMIN, viewer, other}) {
                SharePermissionQuery q = SharePermissionQuery.builder().shareSeq(sh[1]).userSeq((int) sh[0]).requestUserSeq(requester).isDelete(sh[1] == sGone).build();
                s.put(shareNames.get(sh[1]) + "/" + who(requester), () -> sharePermission.findSharePermission(q));
            }
        }
        s.put("소유자 키 틀림", () -> sharePermission.findSharePermission(SharePermissionQuery.builder().shareSeq(sDeep).userSeq(other).requestUserSeq(viewer).isDelete(false).build()));
        s.assertEquals("sharePermission", Map.ofEntries(
                Map.entry("sRoot/admin", "(userSeq=admin resourceSeq=root shareSeq=sRoot shareResourceSeq=root shareType=ORG_LINK shareCanView=true shareCanEdit=false shareCanDownload=true shareCanUpload=false shareCanDelete=false shareCanShare=false shareDepth=0 shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false)"),
                Map.entry("sRoot/viewer", "(userSeq=admin resourceSeq=root shareSeq=sRoot shareResourceSeq=root shareType=ORG_LINK shareCanView=true shareCanEdit=false shareCanDownload=true shareCanUpload=false shareCanDelete=false shareCanShare=false shareDepth=0 shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false)"),
                Map.entry("sRoot/other", "(userSeq=admin resourceSeq=root shareCanView=false shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false)"),
                Map.entry("sDeep/admin", "(userSeq=admin resourceSeq=deep shareSeq=sRoot shareResourceSeq=root shareType=ORG_LINK shareCanView=true shareCanEdit=false shareCanDownload=true shareCanUpload=false shareCanDelete=false shareCanShare=false shareDepth=2 shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false)"),
                Map.entry("sDeep/viewer", "(userSeq=admin resourceSeq=deep shareSeq=sRoot shareResourceSeq=root shareType=ORG_LINK shareCanView=true shareCanEdit=false shareCanDownload=true shareCanUpload=false shareCanDelete=false shareCanShare=false shareDepth=2 shareTargetShareSeq=sDeep shareTargetSeq=# shareTargetShareResourceSeq=deep shareTargetShareType=SPECIFIC_USERS shareTargetCanView=true shareTargetCanEdit=true shareTargetCanDownload=true shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false shareTargetDepth=0)"),
                Map.entry("sDeep/other", "(userSeq=admin resourceSeq=deep shareCanView=false shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareTargetShareSeq=sDeep shareTargetSeq=# shareTargetShareResourceSeq=deep shareTargetShareType=SPECIFIC_USERS shareTargetCanView=true shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false shareTargetDepth=0)"),
                Map.entry("sLeaf/admin", "(userSeq=admin resourceSeq=leaf.txt shareSeq=sLeaf shareResourceSeq=leaf.txt shareType=PUBLIC_LINK shareCanView=true shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareDepth=0 shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false)"),
                Map.entry("sLeaf/viewer", "(userSeq=admin resourceSeq=leaf.txt shareSeq=sLeaf shareResourceSeq=leaf.txt shareType=PUBLIC_LINK shareCanView=true shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareDepth=0 shareTargetShareSeq=sDeep shareTargetSeq=# shareTargetShareResourceSeq=deep shareTargetShareType=SPECIFIC_USERS shareTargetCanView=true shareTargetCanEdit=true shareTargetCanDownload=true shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false shareTargetDepth=1)"),
                Map.entry("sLeaf/other", "(userSeq=admin resourceSeq=leaf.txt shareSeq=sLeaf shareResourceSeq=leaf.txt shareType=PUBLIC_LINK shareCanView=true shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareDepth=0 shareTargetShareSeq=sDeep shareTargetSeq=# shareTargetShareResourceSeq=deep shareTargetShareType=SPECIFIC_USERS shareTargetCanView=true shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false shareTargetDepth=1)"),
                Map.entry("sTop/admin", "(userSeq=admin resourceSeq=top.txt shareCanView=false shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false)"),
                Map.entry("sTop/viewer", "(userSeq=admin resourceSeq=top.txt shareCanView=false shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareTargetShareSeq=sTop shareTargetSeq=# shareTargetShareResourceSeq=top.txt shareTargetShareType=SPECIFIC_USERS shareTargetCanView=true shareTargetCanEdit=false shareTargetCanDownload=true shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=true shareTargetDepth=0)"),
                Map.entry("sTop/other", "(userSeq=admin resourceSeq=top.txt shareCanView=false shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false)"),
                Map.entry("sGone/admin", "(userSeq=admin resourceSeq=gone.txt shareSeq=sGone shareResourceSeq=gone.txt shareType=PUBLIC_LINK shareCanView=true shareCanEdit=true shareCanDownload=true shareCanUpload=true shareCanDelete=true shareCanShare=true shareDepth=0 shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false)"),
                Map.entry("sGone/viewer", "(userSeq=admin resourceSeq=gone.txt shareSeq=sGone shareResourceSeq=gone.txt shareType=PUBLIC_LINK shareCanView=true shareCanEdit=true shareCanDownload=true shareCanUpload=true shareCanDelete=true shareCanShare=true shareDepth=0 shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false)"),
                Map.entry("sGone/other", "(userSeq=admin resourceSeq=gone.txt shareSeq=sGone shareResourceSeq=gone.txt shareType=PUBLIC_LINK shareCanView=true shareCanEdit=true shareCanDownload=true shareCanUpload=true shareCanDelete=true shareCanShare=true shareDepth=0 shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false)"),
                Map.entry("sO/admin", "(userSeq=other resourceSeq=o-root shareCanView=false shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareTargetShareSeq=sO shareTargetSeq=# shareTargetShareResourceSeq=o-root shareTargetShareType=SPECIFIC_USERS shareTargetCanView=true shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false shareTargetDepth=0)"),
                Map.entry("sO/viewer", "(userSeq=other resourceSeq=o-root shareCanView=false shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareTargetShareSeq=sO shareTargetSeq=# shareTargetShareResourceSeq=o-root shareTargetShareType=SPECIFIC_USERS shareTargetCanView=true shareTargetCanEdit=true shareTargetCanDownload=true shareTargetCanUpload=true shareTargetCanDelete=true shareTargetCanShare=true shareTargetDepth=0)"),
                Map.entry("sO/other", "(userSeq=other resourceSeq=o-root shareCanView=false shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false)"),
                Map.entry("sOSub/admin", "(userSeq=other resourceSeq=o-sub shareCanView=false shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareTargetShareSeq=sO shareTargetSeq=# shareTargetShareResourceSeq=o-root shareTargetShareType=SPECIFIC_USERS shareTargetCanView=true shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false shareTargetDepth=1)"),
                Map.entry("sOSub/viewer", "(userSeq=other resourceSeq=o-sub shareCanView=false shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareTargetShareSeq=sO shareTargetSeq=# shareTargetShareResourceSeq=o-root shareTargetShareType=SPECIFIC_USERS shareTargetCanView=true shareTargetCanEdit=true shareTargetCanDownload=true shareTargetCanUpload=true shareTargetCanDelete=true shareTargetCanShare=true shareTargetDepth=1)"),
                Map.entry("sOSub/other", "(userSeq=other resourceSeq=o-sub shareSeq=sOSub shareResourceSeq=o-sub shareType=ORG_LINK shareCanView=true shareCanEdit=false shareCanDownload=true shareCanUpload=false shareCanDelete=false shareCanShare=false shareDepth=0 shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false)"),
                Map.entry("sOFile/admin", "(userSeq=other resourceSeq=o.txt shareSeq=sOFile shareResourceSeq=o.txt shareType=PUBLIC_LINK shareCanView=true shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareDepth=0 shareTargetShareSeq=sO shareTargetSeq=# shareTargetShareResourceSeq=o-root shareTargetShareType=SPECIFIC_USERS shareTargetCanView=true shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false shareTargetDepth=2)"),
                Map.entry("sOFile/viewer", "(userSeq=other resourceSeq=o.txt shareSeq=sOFile shareResourceSeq=o.txt shareType=PUBLIC_LINK shareCanView=true shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareDepth=0 shareTargetShareSeq=sO shareTargetSeq=# shareTargetShareResourceSeq=o-root shareTargetShareType=SPECIFIC_USERS shareTargetCanView=true shareTargetCanEdit=true shareTargetCanDownload=true shareTargetCanUpload=true shareTargetCanDelete=true shareTargetCanShare=true shareTargetDepth=2)"),
                Map.entry("sOFile/other", "(userSeq=other resourceSeq=o.txt shareSeq=sOFile shareResourceSeq=o.txt shareType=PUBLIC_LINK shareCanView=true shareCanEdit=false shareCanDownload=false shareCanUpload=false shareCanDelete=false shareCanShare=false shareDepth=0 shareTargetCanView=false shareTargetCanEdit=false shareTargetCanDownload=false shareTargetCanUpload=false shareTargetCanDelete=false shareTargetCanShare=false)"),
                Map.entry("소유자 키 틀림", "null")));
    }

    // ------------------------------------------------------------------ 헬퍼

    private Render render;

    /** 조상 공유 목록·해제 범위는 SQL 에 ORDER BY 가 없어(구 구현도 같음) 정렬한 문자열로 비교. */
    private Snapshot snapshot() {
        render = new Render(field -> {
            String f = field.toLowerCase();
            if (f.equals("unsharedresources")) {
                return v -> sorted((Collection<?>) v);
            }
            if (f.equals("fullpathseq")) {
                return v -> Arrays.stream(((String) v).split("/")).map(x -> resourceNames.getOrDefault(Long.valueOf(x), x)).collect(Collectors.joining("/", "", "/"));
            }
            if (f.endsWith("sharetargetseq") || f.contains("tenant")) {
                return null;
            }
            if (f.contains("resourceseq") || f.endsWith("folderseq")) {
                return v -> v instanceof Collection<?> c ? c.stream().map(x -> resourceNames.getOrDefault(((Number) x).longValue(), "?")).sorted().toList().toString()
                        : resourceNames.getOrDefault(((Number) v).longValue(), String.valueOf(v));
            }
            if (f.endsWith("shareseq")) {
                return v -> shareNames.getOrDefault(((Number) v).longValue(), String.valueOf(v));
            }
            if (f.endsWith("userseq") || f.equals("ownerseq")) {
                return v -> v instanceof Collection<?> c ? c.stream().map(x -> who(((Number) x).intValue())).toList().toString() : who(((Number) v).intValue());
            }
            return null;
        });
        return new Snapshot(render);
    }

    private String sorted(Collection<?> rows) {
        return rows.stream().map(render::of).sorted().toList().toString();
    }

    private String who(int userSeq) {
        return userSeq == ADMIN ? "admin" : userSeq == viewer ? "viewer" : userSeq == other ? "other" : userSeq == SUPER_ADMIN ? "superadmin" : String.valueOf(userSeq);
    }

    private static Long seq(Resource r) {
        return r == null ? null : r.getResourceSeq();
    }

    // ------------------------------------------------------------------ 픽스처

    private static void loginAs(int userSeq) {
        CustomUserDetails details = Mockito.mock(CustomUserDetails.class);
        given(details.getUserSeq()).willReturn(userSeq);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(details, null, List.of()));
    }

    private int user(String userId, Integer tenantSeq, String name) {
        User u = User.createUser(userId, tenantSeq, null, SignupType.LOCAL, name, UserStatus.ACTIVE, 0L, SUPER_ADMIN, null, null, null);
        userRepository.insertUserWithSeatCheck(u, false, 0);
        return u.getUserSeq();
    }

    private Resource folder(int userSeq, String name, Resource parent) {
        Resource r = Resource.builder().userSeq(userSeq).ownerSeq(userSeq).resourceType(ResourceType.FOLDER)
                .parentFolderSeq(parent == null ? null : parent.getResourceSeq()).resourceName(name).originalFileName(name)
                .fullPathSeq(parent == null ? "" : parent.getFullPathSeq()).fullPath(parent == null ? "" : parent.getFullPath())
                .copyNumber(0).registerUserSeq(userSeq).build();
        resourceRepository.save(r);
        r.initFolderPath();
        resourceRepository.flush();
        resourceNames.put(r.getResourceSeq(), name);
        return r;
    }

    private Resource file(int userSeq, String name, Resource parent) {
        Resource r = Resource.builder().userSeq(userSeq).ownerSeq(userSeq).latestOwnerSeq(userSeq).resourceType(ResourceType.FILE)
                .parentFolderSeq(parent == null ? null : parent.getResourceSeq()).resourceName(name).originalFileName(name)
                .copyNumber(0).ext("txt").contentType("text/plain").sizeByte(10L)
                .versionRegisterDate(DriveTime.toInstant(LocalDateTime.of(2026, 1, 1, 0, 0, 0))).registerUserSeq(userSeq).build();
        resourceRepository.save(r);
        r.initResourceSaveName();
        resourceRepository.flush();
        resourceNames.put(r.getResourceSeq(), name);
        return r;
    }

    /** @param flags can_view·can_edit·can_download·can_upload·can_delete·can_share SQL 리터럴 */
    private long share(String label, int userSeq, Resource r, String type, String flags) {
        long seq = jdbc.queryForObject("INSERT INTO tb_share (user_seq, resource_seq, share_type, can_view, can_edit, can_download, can_upload, can_delete, can_share, register_user_seq) "
                + "VALUES (?, ?, ?, " + flags + ", ?) RETURNING share_seq", Long.class, userSeq, seq(r), type, userSeq);
        shareNames.put(seq, label);
        return seq;
    }

    private void target(int userSeq, long shareSeq, Resource r, int sharedBy, String flags) {
        jdbc.update("INSERT INTO tb_share_target (user_seq, share_seq, resource_seq, shared_by_user_seq, share_date, can_view, can_edit, can_download, can_upload, can_delete, can_share, register_user_seq) "
                + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, " + flags + ", ?)", userSeq, shareSeq, seq(r), sharedBy, sharedBy);
    }
}
