package com.thinkfree.drive.permission.repository;

import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.partition.PartitionKey;
import com.thinkfree.drive.permission.data.ResourcePermissionData;
import com.thinkfree.drive.permission.data.ResourceShareChainData;
import com.thinkfree.drive.permission.data.ResourceShareChainItem;
import com.thinkfree.drive.permission.data.SharePermissionData;
import com.thinkfree.drive.permission.query.ResourcePermissionQuery;
import com.thinkfree.drive.permission.query.ResourcesPermissionQuery;
import com.thinkfree.drive.permission.query.SharePermissionQuery;
import com.thinkfree.drive.share.enums.ShareType;
import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.SignupType;
import com.thinkfree.drive.user.enums.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * CommonMapper 조각 묶음 이관 회귀 — {@link DefaultResourcePermissionQueryRepository}(구 ResourcePermission.xml 4) + {@link DefaultSharePermissionQueryRepository}(구 SharePermission.xml 1), 전부 JdbcClient.
 * 시드 = tenant 1, user 1 superadmin(테넌트 없음)·2 admin. 소유자/요청자/제3자 계정은 tenant 1 에 새로 만들고, 트리 root(폴더) / mid(폴더) / leaf.txt 는 tb_resource 에 직접 INSERT. 트랜잭션 롤백.
 * 검증 포인트 = 가장 가까운 조상의 대상 공유(t)·링크 공유(l) 선택과 depth, 링크 공유의 테넌트 조건(ORG_LINK 는 같은 테넌트만, PUBLIC_LINK 는 누구나), 소유자 본인은 유형 무관,
 * isDelete 가 재귀 단계까지 걸리는지, 소유 계정 노출 조건, Y/N NULL → false, 튜플 IN·빈 키 가드, 사슬(depth 순·부모 폴더·대상 폴더)·리소스별 묶음.
 */
@DisplayName("DefaultResourcePermissionQueryRepository / DefaultSharePermissionQueryRepository — JdbcClient(PostgreSQL Testcontainers)")
class DefaultResourcePermissionQueryRepositoryTest extends RepositoryTest {

    private static final int SUPER_ADMIN = 1;
    private static final int ADMIN = 2;

    @Autowired
    private ResourcePermissionQueryRepository repository;
    @Autowired
    private SharePermissionQueryRepository shareRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private int owner;
    private int me;
    private int other;
    private long root;
    private long mid;
    private long leaf;

    @BeforeEach
    void setUp() {
        loginAs(ADMIN);
        owner = user("perm-owner", "소유자");
        me = user("perm-me", "요청자");
        other = user("perm-other", "제3자");
        root = resource(owner, "FOLDER", null, "root", null, null, null);
        mid = resource(owner, "FOLDER", root, "mid", root + "/", "root/", null);
        leaf = resource(owner, "FILE", mid, "leaf.txt", root + "/" + mid + "/", "root/mid/", "txt");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------ 픽스처

    private static void loginAs(int userSeq) {
        CustomUserDetails details = Mockito.mock(CustomUserDetails.class);
        given(details.getUserSeq()).willReturn(userSeq);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(details, null, List.of()));
    }

    private int user(String userId, String name) {
        User u = User.createUser(userId, 1, null, SignupType.LOCAL, name, UserStatus.ACTIVE, 0L, SUPER_ADMIN, null, null, null);
        userRepository.insertUserWithSeatCheck(u, false, 0);
        return u.getUserSeq();
    }

    private long resource(int userSeq, String type, Long parent, String name, String fullPathSeq, String fullPath, String ext) {
        return jdbc.queryForObject("""
                INSERT INTO tb_resource (user_seq, owner_seq, resource_type, parent_folder_seq, resource_name, full_path_seq, full_path, ext, register_user_seq)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING resource_seq
                """, Long.class, userSeq, userSeq, type, parent, name, fullPathSeq, fullPath, ext, userSeq);
    }

    private long share(long resourceSeq, ShareType type, String canView, String canEdit, String canDownload) {
        return jdbc.queryForObject("""
                INSERT INTO tb_share (user_seq, resource_seq, share_type, can_view, can_edit, can_download, can_upload, can_delete, can_share, register_user_seq)
                VALUES (?, ?, ?, ?, ?, ?, 'N', 'N', 'N', ?) RETURNING share_seq
                """, Long.class, owner, resourceSeq, type.name(), canView, canEdit, canDownload, owner);
    }

    private long target(int targetUser, long shareSeq, long resourceSeq, String canView, String canEdit, String canDownload) {
        return jdbc.queryForObject("""
                INSERT INTO tb_share_target (user_seq, share_seq, resource_seq, shared_by_user_seq, share_date, can_view, can_edit, can_download, can_upload, can_delete, can_share, register_user_seq)
                VALUES (?, ?, ?, ?, now(), ?, ?, ?, 'N', 'N', 'N', ?) RETURNING share_target_seq
                """, Long.class, targetUser, shareSeq, resourceSeq, owner, canView, canEdit, canDownload, owner);
    }

    private static ResourcePermissionQuery one(int userSeq, int sharedBy, long resourceSeq, Boolean isDelete) {
        return ResourcePermissionQuery.builder().userSeq(userSeq).key(new PartitionKey(sharedBy, resourceSeq)).isDelete(isDelete).build();
    }

    private static ResourcesPermissionQuery many(int userSeq, Boolean isDelete, PartitionKey... keys) {
        return ResourcesPermissionQuery.builder().userSeq(userSeq).keys(List.of(keys)).isDelete(isDelete).build();
    }

    // ================================================================== 권한(findResourcePermission·findResourcePermissions)

    @Nested
    @DisplayName("findResourcePermission — 가장 가까운 대상 공유(t)·링크 공유(l)")
    class Permission {

        @Test
        @DisplayName("조상(root) SPECIFIC 공유의 내 대상 행 → t 는 root(depth 2), 링크 아님 → l 없음; 대상 아닌 사용자는 행은 있되 전부 비고 Y/N 은 false")
        void specificTargetOnAncestor() {
            long rootShare = share(root, ShareType.SPECIFIC_USERS, "Y", "N", "N");
            long myTarget = target(me, rootShare, root, "Y", "Y", "Y");

            ResourcePermissionData d = repository.findResourcePermission(one(me, owner, leaf, false));
            assertThat(d).isNotNull();
            assertThat(d.getUserSeq()).isEqualTo(owner);
            assertThat(d.getResourceSeq()).isEqualTo(leaf);
            assertThat(d.getShareSeq()).as("SPECIFIC 은 남에게 링크 공유가 아님").isNull();
            assertThat(d.getShareDepth()).isNull();
            assertThat(d.getShareCanView()).as("NULL → false(구 BooleanTypeHandler)").isFalse();
            assertThat(d.getShareTargetShareSeq()).isEqualTo(rootShare);
            assertThat(d.getShareTargetSeq()).isEqualTo(myTarget);
            assertThat(d.getShareTargetShareResourceSeq()).isEqualTo(root);
            assertThat(d.getShareTargetShareType()).isEqualTo(ShareType.SPECIFIC_USERS);
            assertThat(d.getShareTargetCanView()).isTrue();
            assertThat(d.getShareTargetCanEdit()).isTrue();
            assertThat(d.getShareTargetCanDownload()).isTrue();
            assertThat(d.getShareTargetCanDelete()).isFalse();
            assertThat(d.getShareTargetDepth()).isEqualTo(2);

            ResourcePermissionData none = repository.findResourcePermission(one(other, owner, leaf, false));
            assertThat(none).as("리소스가 있으면 공유 없어도 행은 있음(FindService 가 null 판정)").isNotNull();
            assertThat(none.getShareSeq()).isNull();
            assertThat(none.getShareTargetSeq()).isNull();
            assertThat(none.getShareTargetCanView()).isFalse();
        }

        @Test
        @DisplayName("링크 공유 — ORG_LINK 는 같은 테넌트만(슈퍼어드민은 테넌트 없음 → 제외), PUBLIC_LINK 는 누구나, 가장 가까운 조상이 이김")
        void linkShareByTenant() {
            long rootShare = share(root, ShareType.SPECIFIC_USERS, "Y", "N", "N");
            long myTarget = target(me, rootShare, root, "Y", "N", "N");
            long midShare = share(mid, ShareType.ORG_LINK, "Y", "N", "Y");

            ResourcePermissionData mine = repository.findResourcePermission(one(me, owner, leaf, false));
            assertThat(mine.getShareSeq()).isEqualTo(midShare);
            assertThat(mine.getShareResourceSeq()).isEqualTo(mid);
            assertThat(mine.getShareType()).isEqualTo(ShareType.ORG_LINK);
            assertThat(mine.getShareDepth()).isEqualTo(1);
            assertThat(mine.getShareCanDownload()).isTrue();
            assertThat(mine.getShareCanEdit()).isFalse();
            assertThat(mine.getShareTargetSeq()).as("대상 공유는 그대로 root").isEqualTo(myTarget);
            assertThat(mine.getShareTargetDepth()).isEqualTo(2);

            ResourcePermissionData superAdmin = repository.findResourcePermission(one(SUPER_ADMIN, owner, leaf, false));
            assertThat(superAdmin).isNotNull();
            assertThat(superAdmin.getShareSeq()).as("테넌트 다름 → ORG_LINK 제외").isNull();
            assertThat(superAdmin.getShareTargetSeq()).isNull();

            long leafShare = share(leaf, ShareType.PUBLIC_LINK, "Y", "N", "N");
            ResourcePermissionData viaPublic = repository.findResourcePermission(one(SUPER_ADMIN, owner, leaf, false));
            assertThat(viaPublic.getShareSeq()).as("리소스 자신의 PUBLIC_LINK 가 가장 가까움").isEqualTo(leafShare);
            assertThat(viaPublic.getShareDepth()).isZero();
            assertThat(repository.findResourcePermission(one(me, owner, leaf, false)).getShareSeq()).isEqualTo(leafShare);
        }

        @Test
        @DisplayName("소유자 본인 — 유형 무관하게 가장 가까운 공유가 l, 대상 공유는 없음")
        void ownerSeesNearestShareOfAnyType() {
            long rootShare = share(root, ShareType.SPECIFIC_USERS, "Y", "Y", "Y");
            target(me, rootShare, root, "Y", "N", "N");

            ResourcePermissionData d = repository.findResourcePermission(one(owner, owner, leaf, false));
            assertThat(d.getShareSeq()).isEqualTo(rootShare);
            assertThat(d.getShareType()).isEqualTo(ShareType.SPECIFIC_USERS);
            assertThat(d.getShareDepth()).isEqualTo(2);
            assertThat(d.getShareTargetSeq()).isNull();

            long midShare = share(mid, ShareType.ORG_LINK, "Y", "N", "N");
            assertThat(repository.findResourcePermission(one(owner, owner, leaf, false)).getShareSeq()).isEqualTo(midShare);
        }

        @Test
        @DisplayName("isDelete — 리소스 자신과 재귀 단계 모두에 적용(null 이면 조건 없음), 소유 계정이 노출 불가(DELETED)면 null")
        void isDeleteAndVisibility() {
            long rootShare = share(root, ShareType.SPECIFIC_USERS, "Y", "N", "N");
            long myTarget = target(me, rootShare, root, "Y", "N", "N");

            jdbc.update("UPDATE tb_resource SET is_delete = 'Y' WHERE resource_seq = ?", leaf);
            assertThat(repository.findResourcePermission(one(me, owner, leaf, false))).isNull();
            assertThat(repository.findResourcePermission(one(me, owner, leaf, true)).getShareTargetSeq()).as("isDelete=true 는 재귀 단계에도 걸려 삭제 안 된 조상에 못 닿음(구 XML 동형)").isNull();
            assertThat(repository.findResourcePermission(one(me, owner, leaf, null)).getShareTargetSeq()).isEqualTo(myTarget);
            jdbc.update("UPDATE tb_resource SET is_delete = 'N' WHERE resource_seq = ?", leaf);

            jdbc.update("UPDATE tb_resource SET is_delete = 'Y' WHERE resource_seq = ?", mid);
            ResourcePermissionData cut = repository.findResourcePermission(one(me, owner, leaf, false));
            assertThat(cut).isNotNull();
            assertThat(cut.getShareTargetSeq()).as("삭제된 mid 에서 재귀가 끊겨 root 의 대상 공유에 못 닿음").isNull();
            assertThat(repository.findResourcePermission(one(me, owner, leaf, null)).getShareTargetSeq()).as("isDelete 없으면 재귀 조건도 없음").isEqualTo(myTarget);
            jdbc.update("UPDATE tb_resource SET is_delete = 'N' WHERE resource_seq = ?", mid);

            jdbc.update("UPDATE tb_user SET status = 'DELETED' WHERE user_seq = ?", owner);
            assertThat(repository.findResourcePermission(one(me, owner, leaf, false))).isNull();
            assertThat(repository.findResourcePermission(one(me, owner, 999_999_999L, false))).as("없는 리소스").isNull();
        }

        @Test
        @DisplayName("findResourcePermissions — 키 튜플 IN(없는 키는 빠짐), 빈 키는 SQL 없이 빈 목록")
        void findResourcePermissions() {
            long rootShare = share(root, ShareType.SPECIFIC_USERS, "Y", "N", "N");
            long myTarget = target(me, rootShare, root, "Y", "N", "N");

            List<ResourcePermissionData> list = repository.findResourcePermissions(
                    many(me, false, new PartitionKey(owner, leaf), new PartitionKey(owner, mid), new PartitionKey(owner, 999_999_999L)));
            assertThat(list).extracting(ResourcePermissionData::getResourceSeq).containsExactlyInAnyOrder(leaf, mid);
            assertThat(list).extracting(ResourcePermissionData::getShareTargetSeq).containsOnly(myTarget);
            assertThat(list).filteredOn(d -> d.getResourceSeq().equals(mid)).extracting(ResourcePermissionData::getShareTargetDepth).containsExactly(1);

            assertThat(repository.findResourcePermissions(many(me, false))).isEmpty();
            assertThat(repository.findResourcePermissions(ResourcesPermissionQuery.builder().userSeq(me).keys(null).isDelete(false).build())).isEmpty();
        }
    }

    // ================================================================== 사슬(findResourceShareChain·findResourcesShareChain)

    @Nested
    @DisplayName("findResourceShareChain — depth 0(자신)부터 조상까지, 각 조상의 공유·내 대상·부모/대상 폴더")
    class Chain {

        @Test
        @DisplayName("단건 — 3단 사슬(leaf → mid ORG_LINK → root SPECIFIC+내 대상), 부모 폴더 경로, 대상 폴더(farthest) 는 대상 행이 있는 조상에만")
        void chainOfThree() {
            long rootShare = share(root, ShareType.SPECIFIC_USERS, "Y", "N", "N");
            long myTarget = target(me, rootShare, root, "Y", "N", "N");
            long midShare = share(mid, ShareType.ORG_LINK, "Y", "N", "N");

            ResourceShareChainData data = repository.findResourceShareChain(one(me, owner, leaf, false));
            assertThat(data).isNotNull();
            assertThat(data.getUserSeq()).isEqualTo(owner);
            assertThat(data.getResourceSeq()).isEqualTo(leaf);
            assertThat(data.getChains()).hasSize(3);
            assertThat(data.getChains()).extracting(ResourceShareChainItem::getDepth).containsExactly(0, 1, 2);
            assertThat(data.getChains()).extracting(ResourceShareChainItem::getAncestorResourceSeq).containsExactly(leaf, mid, root);
            assertThat(data.getChains()).extracting(ResourceShareChainItem::getUserSeq).containsOnly(owner);
            assertThat(data.getChains()).extracting(ResourceShareChainItem::getResourceSeq).containsOnly(leaf);

            ResourceShareChainItem self = data.getChains().get(0);
            assertThat(self.getResourceName()).isEqualTo("leaf.txt");
            assertThat(self.getParentFolderSeq()).isEqualTo(mid);
            assertThat(self.getParentFolderName()).isEqualTo("mid");
            assertThat(self.getFullPathSeq()).isEqualTo(root + "/");
            assertThat(self.getFullPath()).isEqualTo("root/");
            assertThat(self.getShareSeq()).isNull();
            assertThat(self.getShareType()).isNull();
            assertThat(self.getShareTargetSeq()).isNull();
            assertThat(self.getFarthestShareResourceSeq()).isNull();

            ResourceShareChainItem viaMid = data.getChains().get(1);
            assertThat(viaMid.getShareSeq()).isEqualTo(midShare);
            assertThat(viaMid.getShareType()).isEqualTo(ShareType.ORG_LINK);
            assertThat(viaMid.getShareTargetSeq()).isNull();
            assertThat(viaMid.getFarthestShareResourceSeq()).isNull();

            ResourceShareChainItem viaRoot = data.getChains().get(2);
            assertThat(viaRoot.getShareSeq()).isEqualTo(rootShare);
            assertThat(viaRoot.getShareType()).isEqualTo(ShareType.SPECIFIC_USERS);
            assertThat(viaRoot.getShareTargetSeq()).isEqualTo(myTarget);
            assertThat(viaRoot.getFarthestShareResourceSeq()).isEqualTo(root);
            assertThat(viaRoot.getFarthestResourceName()).isEqualTo("root");

            assertThat(repository.findResourceShareChain(one(other, owner, leaf, false)).getChains())
                    .extracting(ResourceShareChainItem::getShareTargetSeq).as("대상 아닌 사용자").containsOnlyNulls();
            assertThat(repository.findResourceShareChain(one(me, owner, 999_999_999L, false))).isNull();
        }

        @Test
        @DisplayName("N건 — 리소스별 1개씩 묶이고 사슬은 depth 순, 없는 키는 빠짐, 빈 키는 SQL 없이 빈 목록")
        void chainsGrouped() {
            long rootShare = share(root, ShareType.SPECIFIC_USERS, "Y", "N", "N");
            long myTarget = target(me, rootShare, root, "Y", "N", "N");

            List<ResourceShareChainData> list = repository.findResourcesShareChain(
                    many(me, false, new PartitionKey(owner, leaf), new PartitionKey(owner, mid), new PartitionKey(owner, 999_999_999L)));
            assertThat(list).extracting(ResourceShareChainData::getResourceSeq).containsExactlyInAnyOrder(leaf, mid);
            ResourceShareChainData leafChain = list.stream().filter(d -> d.getResourceSeq().equals(leaf)).findFirst().orElseThrow();
            ResourceShareChainData midChain = list.stream().filter(d -> d.getResourceSeq().equals(mid)).findFirst().orElseThrow();
            assertThat(leafChain.getChains()).extracting(ResourceShareChainItem::getDepth).containsExactly(0, 1, 2);
            assertThat(midChain.getChains()).extracting(ResourceShareChainItem::getDepth).containsExactly(0, 1);
            assertThat(midChain.getChains().get(1).getShareTargetSeq()).isEqualTo(myTarget);
            assertThat(midChain.getChains().get(0).getParentFolderName()).isEqualTo("root");

            assertThat(repository.findResourcesShareChain(many(me, false))).isEmpty();
        }
    }

    // ================================================================== 공유 기준(findSharePermission)

    @Nested
    @DisplayName("findSharePermission — 공유(share_seq)의 리소스에 대한 요청자의 l·t")
    class SharePermission {

        @Test
        @DisplayName("mid ORG_LINK 공유 기준: 같은 테넌트 요청자는 l = 자신(depth 0)·t = root 대상(depth 1); 테넌트 다르면 둘 다 없음; is_delete 불일치·없는 공유·노출 불가 소유자는 null")
        void bySharePermission() {
            long rootShare = share(root, ShareType.SPECIFIC_USERS, "Y", "N", "N");
            long myTarget = target(me, rootShare, root, "Y", "Y", "N");
            long midShare = share(mid, ShareType.ORG_LINK, "Y", "N", "Y");

            SharePermissionData d = shareRepository.findSharePermission(query(midShare, me, false));
            assertThat(d).isNotNull();
            assertThat(d.getUserSeq()).isEqualTo(owner);
            assertThat(d.getResourceSeq()).isEqualTo(mid);
            assertThat(d.getShareSeq()).isEqualTo(midShare);
            assertThat(d.getShareResourceSeq()).isEqualTo(mid);
            assertThat(d.getShareType()).isEqualTo(ShareType.ORG_LINK);
            assertThat(d.getShareDepth()).isZero();
            assertThat(d.getShareCanDownload()).isTrue();
            assertThat(d.getShareCanEdit()).isFalse();
            assertThat(d.getShareTargetShareSeq()).isEqualTo(rootShare);
            assertThat(d.getShareTargetSeq()).isEqualTo(myTarget);
            assertThat(d.getShareTargetShareResourceSeq()).isEqualTo(root);
            assertThat(d.getShareTargetCanEdit()).isTrue();
            assertThat(d.getShareTargetDepth()).isEqualTo(1);

            SharePermissionData superAdmin = shareRepository.findSharePermission(query(midShare, SUPER_ADMIN, false));
            assertThat(superAdmin).isNotNull();
            assertThat(superAdmin.getShareSeq()).isNull();
            assertThat(superAdmin.getShareTargetSeq()).isNull();
            assertThat(superAdmin.getShareCanView()).isFalse();

            assertThat(shareRepository.findSharePermission(query(midShare, me, true))).as("is_delete 불일치").isNull();
            assertThat(shareRepository.findSharePermission(query(999_999_999L, me, false))).isNull();
            jdbc.update("UPDATE tb_user SET status = 'DELETED' WHERE user_seq = ?", owner);
            assertThat(shareRepository.findSharePermission(query(midShare, me, false))).isNull();
        }

        private SharePermissionQuery query(long shareSeq, int requestUserSeq, Boolean isDelete) {
            return SharePermissionQuery.builder().shareSeq(shareSeq).userSeq(owner).requestUserSeq(requestUserSeq).isDelete(isDelete).build();
        }
    }
}
