package com.thinkfree.drive.permission.model;

import com.thinkfree.drive.permission.data.ResourcePermissionData;
import com.thinkfree.drive.resource.enums.ResourceLocation;
import com.thinkfree.drive.share.enums.ShareType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ResourcePermission.from() 권한 해소 룰 단위 검증 (DB/Spring 불필요한 순수 함수).
 *
 * 입력은 SQL 이 뽑는 raw 두 묶음:
 *   L(share*)        = 가장 가까운 link 공유
 *   T(shareTarget*)  = 내가 대상인 가장 가까운 공유 대상
 * 룰:
 *   hasLink   = shareResourceSeq != null
 *   useTarget = hasLink ? (shareSeq == shareTargetShareSeq) : (shareTargetShareResourceSeq != null)
 *   권한      = owner ? 전권 : useTarget ? T.can* : (hasLink ? L.can* : invalid)
 */
class ResourcePermissionFromTest {

    private static final int OWNER = 2000;
    private static final int ME = 1000;
    private static final long RESOURCE = 50L;

    private void assertPerms(Permissions p, boolean view, boolean edit, boolean download, boolean upload, boolean delete, boolean share) {
        assertEquals(view, Boolean.TRUE.equals(p.getCanView()), "canView");
        assertEquals(edit, Boolean.TRUE.equals(p.getCanEdit()), "canEdit");
        assertEquals(download, Boolean.TRUE.equals(p.getCanDownload()), "canDownload");
        assertEquals(upload, Boolean.TRUE.equals(p.getCanUpload()), "canUpload");
        assertEquals(delete, Boolean.TRUE.equals(p.getCanDelete()), "canDelete");
        assertEquals(share, Boolean.TRUE.equals(p.getCanShare()), "canShare");
    }

    @Test
    @DisplayName("소유자 본인 → 전권 + MY")
    void owner_fullPermission() {
        var data = ResourcePermissionData.builder().userSeq(OWNER).resourceSeq(RESOURCE).build();

        var rp = ResourcePermission.from(data, OWNER);

        assertTrue(rp.getIsOwner());
        assertEquals(ResourceLocation.MY, rp.getResourceLocation());
        assertPerms(rp.getPermissions(), true, true, true, true, true, true);
    }

    @Test
    @DisplayName("SPECIFIC + 내 대상 → 타겟 권한")
    void specific_withTarget_usesTargetPermission() {
        var data = ResourcePermissionData.builder()
                .userSeq(OWNER).resourceSeq(RESOURCE)
                // 링크 없음(L 전부 null)
                .shareTargetShareSeq(100L).shareTargetSeq(200L).shareTargetShareResourceSeq(RESOURCE)
                .shareTargetShareType(ShareType.SPECIFIC_USERS)
                .shareTargetCanView(true).shareTargetCanEdit(true)
                .shareTargetCanDownload(false).shareTargetCanUpload(false).shareTargetCanDelete(false).shareTargetCanShare(false)
                .build();

        var rp = ResourcePermission.from(data, ME);

        assertFalse(rp.getIsOwner());
        assertEquals(ResourceLocation.SHARE, rp.getResourceLocation());
        assertEquals(ShareType.SPECIFIC_USERS, rp.getShareType());
        assertTrue(rp.getIsShared());
        assertPerms(rp.getPermissions(), true, true, false, false, false, false);
        // 이 리소스에 직접 달린 대상이라 식별자 노출
        assertEquals(200L, (long) rp.getShareTargetSeq());
        assertEquals(100L, (long) rp.getShareSeq());
    }

    @Test
    @DisplayName("SPECIFIC + 내 대상 없음 → invalid")
    void specific_withoutTarget_invalid() {
        var data = ResourcePermissionData.builder().userSeq(OWNER).resourceSeq(RESOURCE).build();

        var rp = ResourcePermission.from(data, ME);

        assertFalse(rp.getIsShared());
        assertPerms(rp.getPermissions(), false, false, false, false, false, false);
    }

    @Test
    @DisplayName("ORG_LINK + 내 대상 없음 → 링크 권한")
    void orgLink_noTarget_usesLinkPermission() {
        var data = ResourcePermissionData.builder()
                .userSeq(OWNER).resourceSeq(RESOURCE)
                .shareSeq(100L).shareResourceSeq(RESOURCE).shareType(ShareType.ORG_LINK)
                .shareCanView(true).shareCanEdit(false).shareCanDownload(false)
                .shareCanUpload(false).shareCanDelete(false).shareCanShare(false)
                .build();

        var rp = ResourcePermission.from(data, ME);

        assertEquals(ShareType.ORG_LINK, rp.getShareType());
        assertTrue(rp.getIsShared());
        assertPerms(rp.getPermissions(), true, false, false, false, false, false);
    }

    @Test
    @DisplayName("ORG_LINK + 같은 공유에 내 대상 → 타겟 권한 우선")
    void orgLink_withTargetOnSameShare_targetWins() {
        var data = ResourcePermissionData.builder()
                .userSeq(OWNER).resourceSeq(RESOURCE)
                .shareSeq(100L).shareResourceSeq(RESOURCE).shareType(ShareType.ORG_LINK)
                .shareCanView(true).shareCanEdit(false).shareCanDownload(false)
                .shareCanUpload(false).shareCanDelete(false).shareCanShare(false)
                // 같은 공유(100)에 내 대상 (view+edit)
                .shareTargetShareSeq(100L).shareTargetSeq(200L).shareTargetShareResourceSeq(RESOURCE)
                .shareTargetShareType(ShareType.ORG_LINK)
                .shareTargetCanView(true).shareTargetCanEdit(true)
                .shareTargetCanDownload(false).shareTargetCanUpload(false).shareTargetCanDelete(false).shareTargetCanShare(false)
                .build();

        var rp = ResourcePermission.from(data, ME);

        assertPerms(rp.getPermissions(), true, true, false, false, false, false); // 링크(view only)가 아니라 타겟(view+edit)
    }

    @Test
    @DisplayName("중첩: 가까운 링크(다른 공유)엔 내 대상 없고 먼 공유에 대상 → 가까운 링크 권한")
    void nested_nearestLinkWinsOverFarTarget() {
        long child = 60L;
        var data = ResourcePermissionData.builder()
                .userSeq(OWNER).resourceSeq(child)
                // 가까운 링크: child(60) 의 공유 300, view only, 내 대상 없음
                .shareSeq(300L).shareResourceSeq(child).shareType(ShareType.ORG_LINK)
                .shareCanView(true).shareCanEdit(false).shareCanDownload(false)
                .shareCanUpload(false).shareCanDelete(false).shareCanShare(false)
                // 먼 대상: 조상(50) 의 다른 공유 100, view+edit
                .shareTargetShareSeq(100L).shareTargetSeq(200L).shareTargetShareResourceSeq(RESOURCE)
                .shareTargetShareType(ShareType.ORG_LINK)
                .shareTargetCanView(true).shareTargetCanEdit(true)
                .shareTargetCanDownload(false).shareTargetCanUpload(false).shareTargetCanDelete(false).shareTargetCanShare(false)
                .build();

        var rp = ResourcePermission.from(data, ME);

        // useTarget = (nearestShareSeq 300 == shareTargetShareSeq 100) → false → 가까운 링크 권한
        assertPerms(rp.getPermissions(), true, false, false, false, false, false);
        // 가까운 링크는 child 에 직접 → shareSeq 노출, 먼 대상은 상속이라 shareTargetSeq 는 null
        assertEquals(300L, (long) rp.getShareSeq());
        assertNull(rp.getShareTargetSeq());
    }

    @Test
    @DisplayName("PUBLIC_LINK + 내 대상 없음 → 링크 권한")
    void publicLink_noTarget_usesLinkPermission() {
        var data = ResourcePermissionData.builder()
                .userSeq(OWNER).resourceSeq(RESOURCE)
                .shareSeq(100L).shareResourceSeq(RESOURCE).shareType(ShareType.PUBLIC_LINK)
                .shareCanView(true).shareCanEdit(false).shareCanDownload(true)
                .shareCanUpload(false).shareCanDelete(false).shareCanShare(false)
                .build();

        var rp = ResourcePermission.from(data, ME);

        assertEquals(ShareType.PUBLIC_LINK, rp.getShareType());
        assertPerms(rp.getPermissions(), true, false, true, false, false, false);
    }

    @Test
    @DisplayName("공유 전혀 없음(L·T 모두 null) → invalid + 미공유")
    void noShare_invalid() {
        var data = ResourcePermissionData.builder().userSeq(OWNER).resourceSeq(RESOURCE).build();

        var rp = ResourcePermission.from(data, ME);

        assertFalse(rp.getIsShared());
        assertNull(rp.getShareType());
        assertPerms(rp.getPermissions(), false, false, false, false, false, false);
    }
}
