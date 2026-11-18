package com.thinkfree.drive.share.repository;

import com.thinkfree.drive.global.time.DriveTime;
import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.permission.model.Permissions;
import com.thinkfree.drive.resource.entity.Resource;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.repository.ResourceRepository;
import com.thinkfree.drive.share.data.*;
import com.thinkfree.drive.share.entity.Share;
import com.thinkfree.drive.share.enums.ShareType;
import com.thinkfree.drive.share.query.*;
import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.SignupType;
import com.thinkfree.drive.user.enums.UserStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * 2단계 Share 이관 회귀 — 읽기({@link DefaultShareQueryRepository} 14 statement, existsByTenant 만 JPQL·나머지 JdbcClient)·쓰기({@link DefaultShareRepository}: INSERT 는 JPA saveAndFlush, UPDATE 2갈래·DELETE 6종은 JPQL @Modifying).
 * 시드 = tenant 1, user 1 superadmin(테넌트 없음)·2 admin(tenant 1)·3 anonymous. 리소스는 {@link ResourceRepository}, 공유 대상은 tb_share_target 직접 INSERT. 트랜잭션 롤백.
 * 검증 포인트 = JPA INSERT(identity 키·리스너 감사·Y/N·LocalDateTime 왕복·유니크 전파), DTO 매핑(nullable 권한 Boolean 유지), 대상 포함 조회의 수동 그룹핑(DESC·빈 목록),
 * nearest 6종(조상/자기 포함/하위, 리소스/공유 기준, 제외 조건, 최상위 노드, 파일 루트), 해제 범위(다른 공유 서브트리 제외), 조상 공유 목록, 조건부 UPDATE(<set>)·감사 write-back, DELETE 6종·빈 목록 가드.
 */
@DisplayName("DefaultShareRepository / DefaultShareQueryRepository — JPA insert + JdbcClient(PostgreSQL Testcontainers)")
class DefaultShareRepositoryTest extends RepositoryTest {

    private static final int SUPER_ADMIN = 1;
    private static final int ADMIN = 2;

    @Autowired
    private ShareRepository repository;
    @Autowired
    private ShareQueryRepository queryRepository;
    @Autowired
    private ResourceRepository resourceRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void loginAsAdmin() {
        loginAs(ADMIN);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void loginAs(int userSeq) {
        CustomUserDetails details = Mockito.mock(CustomUserDetails.class);
        given(details.getUserSeq()).willReturn(userSeq);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(details, null, List.of()));
    }

    private Resource folder(int userSeq, String name, Resource parent) {
        Resource r = Resource.builder().userSeq(userSeq).ownerSeq(userSeq).resourceType(ResourceType.FOLDER)
                .parentFolderSeq(parent == null ? null : parent.getResourceSeq()).resourceName(name).originalFileName(name)
                .fullPathSeq(parent == null ? "" : parent.getFullPathSeq()).fullPath(parent == null ? "" : parent.getFullPath())
                .copyNumber(0).registerUserSeq(userSeq).build();
        resourceRepository.save(r);
        r.initFolderPath();
        resourceRepository.flush();
        return r;
    }

    private Resource file(int userSeq, String name, Resource parent) {
        Resource r = Resource.builder().userSeq(userSeq).ownerSeq(userSeq).latestOwnerSeq(userSeq).resourceType(ResourceType.FILE)
                .parentFolderSeq(parent == null ? null : parent.getResourceSeq()).resourceName(name).originalFileName(name)
                .copyNumber(0).ext("txt").contentType("text/plain").sizeByte(1L)
                .versionRegisterDate(DriveTime.toInstant(LocalDateTime.of(2026, 1, 1, 0, 0, 0))).registerUserSeq(userSeq).build();
        resourceRepository.save(r);
        r.initResourceSaveName();
        resourceRepository.flush();
        return r;
    }

    private Share share(Resource resource, ShareType type) {
        Share s = Share.builder().userSeq(resource.getUserSeq()).resourceSeq(resource.getResourceSeq()).shareType(type)
                .canView(true).canEdit(false).canDownload(true).canUpload(false).canDelete(false).canShare(false)
                .isPasswordEnabled(false).registerUserSeq(resource.getUserSeq()).build();
        return repository.save(s);
    }

    private void target(Share share, int targetUserSeq) {
        jdbc.update("INSERT INTO tb_share_target (user_seq, share_seq, resource_seq, shared_by_user_seq, share_date, can_view, can_edit, register_user_seq) VALUES (?, ?, ?, ?, NOW(), 'Y', 'N', ?)",
                targetUserSeq, share.getShareSeq(), share.getResourceSeq(), share.getUserSeq(), share.getUserSeq());
    }

    private User user(String userId) {
        User u = User.createUser(userId, 1, null, SignupType.LOCAL, "대상 " + userId, UserStatus.ACTIVE, 0L, SUPER_ADMIN, null, null, null);
        userRepository.insertUserWithSeatCheck(u, false, 0);
        return u;
    }

    /** 변경 감지 결과는 DB 값으로 확인 — 미반영 변경을 flush(커밋 대신) 후 JdbcTemplate */
    private <T> T col(long shareSeq, String column, Class<T> type) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT " + column + " FROM tb_share WHERE share_seq = ?", type, shareSeq);
    }

    private static SharedResourceNearestFindQuery nearest(Long resourceSeq, Long shareSeq, Long rootResourceSeq, int userSeq, boolean isDelete) {
        return SharedResourceNearestFindQuery.builder().resourceSeq(resourceSeq).shareSeq(shareSeq).rootResourceSeq(rootResourceSeq).userSeq(userSeq).isDelete(isDelete).build();
    }

    // ================================================================== INSERT·단건

    @Nested
    @DisplayName("JPA INSERT · 단건 DTO · 대상")
    class InsertAndFind {

        @Test
        @DisplayName("save — identity 키(save 순간 INSERT), 리스너 감사(비면 컨텍스트·초 절삭), Y/N·password·expire_date 왕복, findSharedResourceBy* 매핑, 유니크(user, resource) 전파")
        void saveAndFind() {
            Resource f = file(ADMIN, "doc.txt", null);
            Instant expire = DriveTime.toInstant(LocalDateTime.of(2026, 12, 31, 23, 59, 59));
            Share s = Share.builder().userSeq(ADMIN).resourceSeq(f.getResourceSeq()).shareType(ShareType.PUBLIC_LINK)
                    .canView(true).canEdit(false).canDownload(true).password("pw").isPasswordEnabled(true).expireDate(expire).build();
            Share saved = repository.save(s);
            assertThat(saved).isSameAs(s);
            assertThat(s.getShareSeq()).isPositive();
            assertThat(s.getRegisterUserSeq()).as("JpaAuditListener → 컨텍스트 ADMIN").isEqualTo(ADMIN);
            assertThat(s.getRegisterDate()).isNotNull();
            assertThat(s.getRegisterDate().getNano()).isZero();
            assertThat(col(s.getShareSeq(), "can_view", String.class)).isEqualTo("Y");
            assertThat(col(s.getShareSeq(), "can_edit", String.class)).isEqualTo("N");
            assertThat(col(s.getShareSeq(), "can_upload", String.class)).as("builder 미지정 → NULL").isNull();
            assertThat(col(s.getShareSeq(), "is_password_enabled", String.class)).isEqualTo("Y");
            assertThat(col(s.getShareSeq(), "share_type", String.class)).isEqualTo("PUBLIC_LINK");
            assertThat(col(s.getShareSeq(), "update_user_seq", Integer.class)).isNull();

            assertThat(col(s.getShareSeq(), "password", String.class)).isEqualTo("pw");
            assertThat(col(s.getShareSeq(), "expire_date", java.time.OffsetDateTime.class).toInstant()).as("Instant 왕복").isEqualTo(expire);
            assertThat(queryRepository.findSharedResourceByShare(SharedResourceFindQuery.builder().shareSeq(s.getShareSeq()).userSeq(ADMIN).isDelete(true).build())).as("휴지통 기준").isNull();
            assertThat(queryRepository.findSharedResourceByShare(SharedResourceFindQuery.builder().shareSeq(s.getShareSeq()).userSeq(SUPER_ADMIN).isDelete(false).build())).as("다른 소유자").isNull();

            SharedResourceData byShare = queryRepository.findSharedResourceByShare(SharedResourceFindQuery.builder().shareSeq(s.getShareSeq()).userSeq(ADMIN).isDelete(false).build());
            assertThat(byShare.getResourceName()).isEqualTo("doc.txt");
            assertThat(byShare.getResourceType()).isEqualTo(ResourceType.FILE);
            assertThat(byShare.getTenantSeq()).isEqualTo(1);
            assertThat(byShare.getShareType()).isEqualTo(ShareType.PUBLIC_LINK);
            SharedResourceData byResource = queryRepository.findSharedResourceByResource(SharedResourceFindQuery.builder().resourceSeq(f.getResourceSeq()).userSeq(ADMIN).isDelete(false).build());
            assertThat(byResource.getShareSeq()).isEqualTo(s.getShareSeq());
            assertThat(queryRepository.findSharedResourceByResource(SharedResourceFindQuery.builder().resourceSeq(99999L).userSeq(ADMIN).isDelete(false).build())).isNull();

            assertThat(queryRepository.existsByTenant(1)).isTrue();
            assertThat(queryRepository.existsByTenant(999)).isFalse();

            Share dup = Share.builder().userSeq(ADMIN).resourceSeq(f.getResourceSeq()).shareType(ShareType.ORG_LINK).isPasswordEnabled(false).build();
            assertThatThrownBy(() -> repository.save(dup)).as("uniq_share(user_seq, resource_seq)").isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("findShareWithTargets(대상 수동 그룹핑·share_target_seq DESC·대상 없으면 빈 목록) · findUsersByShare(테넌트 INNER JOIN)")
        void withTargetsAndUsers() {
            User t1 = user("share-t1");
            User t2 = user("share-t2");
            Resource f = file(ADMIN, "shared.txt", null);
            Share s = share(f, ShareType.SPECIFIC_USERS);
            target(s, t1.getUserSeq());
            target(s, t2.getUserSeq());
            target(s, SUPER_ADMIN);

            ShareWithTargetsData data = queryRepository.findShareWithTargets(ShareWithTargetsFindQuery.builder().shareSeq(s.getShareSeq()).userSeq(ADMIN).isDelete(false).build());
            assertThat(data).isNotNull();
            assertThat(data.getShareSeq()).isEqualTo(s.getShareSeq());
            assertThat(data.getShareType()).as("문자열 필드").isEqualTo("SPECIFIC_USERS");
            assertThat(data.getCanView()).isTrue();
            assertThat(data.getTargets()).hasSize(3);
            assertThat(data.getTargets()).extracting(ShareWithTargetsData.ShareTargetItem::getUserSeq).as("DESC = 나중 INSERT 가 먼저").containsExactly(SUPER_ADMIN, t2.getUserSeq(), t1.getUserSeq());
            ShareWithTargetsData.ShareTargetItem first = data.getTargets().get(2);
            assertThat(first.getShareTargetSeq()).isNotNull();
            assertThat(first.getUserId()).isEqualTo("share-t1");
            assertThat(first.getUserStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(first.getTenantName()).isEqualTo("기본 테넌트");
            assertThat(first.getCanView()).isTrue();
            assertThat(first.getCanEdit()).isFalse();
            assertThat(first.getCanShare()).as("DB 기본 'N'").isFalse();
            assertThat(data.getTargets().get(0).getTenantName()).as("슈퍼어드민은 테넌트 없음 — LEFT JOIN").isNull();

            Resource lone = file(ADMIN, "lone.txt", null);
            Share noTargets = share(lone, ShareType.ORG_LINK);
            ShareWithTargetsData empty = queryRepository.findShareWithTargets(ShareWithTargetsFindQuery.builder().shareSeq(noTargets.getShareSeq()).userSeq(ADMIN).isDelete(false).build());
            assertThat(empty).isNotNull();
            assertThat(empty.getTargets()).isEmpty();
            assertThat(queryRepository.findShareWithTargets(ShareWithTargetsFindQuery.builder().shareSeq(99999L).userSeq(ADMIN).isDelete(false).build())).isNull();

            List<ShareUserData> users = queryRepository.findUsersByShare(ShareUsersFindQuery.builder().shareSeq(s.getShareSeq()).userSeq(ADMIN).isDelete(false).build());
            assertThat(users).extracting(ShareUserData::getUserId).as("테넌트 없는 슈퍼어드민은 INNER JOIN 탈락").containsExactly("share-t2", "share-t1");
            assertThat(users.get(0).getTenantName()).isEqualTo("기본 테넌트");
            assertThat(users.get(0).getUserStatus()).isEqualTo(UserStatus.ACTIVE);
        }
    }

    // ================================================================== nearest · 해제 범위

    @Nested
    @DisplayName("nearest 6종 · findUnsharedDescendantsByShare · findAncestorSharesByResource")
    class Nearest {

        private Resource root, mid, leaf, deep, other, top;
        private Share rootShare, otherShare;

        @BeforeEach
        void tree() {
            // root(S1) / mid / leaf.txt , root / mid / deep.txt , root / other(S2) , top.txt(루트 파일)
            root = folder(ADMIN, "root", null);
            mid = folder(ADMIN, "mid", root);
            leaf = file(ADMIN, "leaf.txt", mid);
            deep = file(ADMIN, "deep.txt", mid);
            other = folder(ADMIN, "other", root);
            top = file(ADMIN, "top.txt", null);
            rootShare = share(root, ShareType.ORG_LINK);
            otherShare = share(other, ShareType.SPECIFIC_USERS);
        }

        @Test
        @DisplayName("리소스 기준 — 조상(자기 제외)·자기 포함 조상·하위, 최상위 노드·타입, 파일 루트, 없는 리소스/휴지통은 null")
        void byResource() {
            SharedResourceNearestData a = queryRepository.findNearestAncestorShareByResource(nearest(leaf.getResourceSeq(), null, null, ADMIN, false));
            assertThat(a.getShareSeq()).as("leaf 의 조상 중 가장 가까운 공유 = root").isEqualTo(rootShare.getShareSeq());
            assertThat(a.getResourceSeq()).isEqualTo(root.getResourceSeq());
            assertThat(a.getShareType()).isEqualTo(ShareType.ORG_LINK);
            assertThat(a.getRootResourceSeq()).as("최상위 조상").isEqualTo(root.getResourceSeq());
            assertThat(a.getResourceType()).isEqualTo(ResourceType.FOLDER);

            SharedResourceNearestData self = queryRepository.findNearestAncestorShareByResource(nearest(root.getResourceSeq(), null, null, ADMIN, false));
            assertThat(self.getShareSeq()).as("자기 공유는 제외").isNull();
            assertThat(self.getRootResourceSeq()).isEqualTo(root.getResourceSeq());
            SharedResourceNearestData selfIncluded = queryRepository.findNearestSelfOrAncestorShareByResource(nearest(root.getResourceSeq(), null, null, ADMIN, false));
            assertThat(selfIncluded.getShareSeq()).isEqualTo(rootShare.getShareSeq());
            SharedResourceNearestData otherSelf = queryRepository.findNearestSelfOrAncestorShareByResource(nearest(other.getResourceSeq(), null, null, ADMIN, false));
            assertThat(otherSelf.getShareSeq()).as("자기 공유가 root 공유보다 가까움").isEqualTo(otherShare.getShareSeq());

            SharedResourceNearestData topFile = queryRepository.findNearestAncestorShareByResource(nearest(top.getResourceSeq(), null, null, ADMIN, false));
            assertThat(topFile.getRootResourceSeq()).isEqualTo(top.getResourceSeq());
            assertThat(topFile.getResourceType()).as("루트 파일 — 서비스가 isFile 로 분기").isEqualTo(ResourceType.FILE);
            assertThat(topFile.getShareSeq()).isNull();

            SharedResourceNearestData d = queryRepository.findNearestDescendantShareByResource(nearest(root.getResourceSeq(), null, root.getResourceSeq(), ADMIN, false));
            assertThat(d.getShareSeq()).as("root 하위(자기 제외) 가장 가까운 공유 = other").isEqualTo(otherShare.getShareSeq());
            assertThat(d.getResourceSeq()).isEqualTo(other.getResourceSeq());
            assertThat(d.getShareType()).isEqualTo(ShareType.SPECIFIC_USERS);
            assertThat(queryRepository.findNearestDescendantShareByResource(nearest(mid.getResourceSeq(), null, mid.getResourceSeq(), ADMIN, false))).as("mid 하위엔 공유 없음").isNull();

            assertThat(queryRepository.findNearestAncestorShareByResource(nearest(99999L, null, null, ADMIN, false))).isNull();
            assertThat(queryRepository.findNearestAncestorShareByResource(nearest(leaf.getResourceSeq(), null, null, ADMIN, true))).as("휴지통 기준 root_data 없음").isNull();
        }

        @Test
        @DisplayName("공유 기준 — 조상 공유(shareSeq 만)·조상+최상위·하위, 제외 조건, 없으면 null")
        void byShare() {
            SharedResourceNearestData a = queryRepository.findNearestAncestorShareByShare(nearest(null, otherShare.getShareSeq(), null, ADMIN, false));
            assertThat(a.getShareSeq()).as("other 의 조상 공유 = root").isEqualTo(rootShare.getShareSeq());
            assertThat(a.getRootResourceSeq()).as("shareSeq 만 채움").isNull();
            assertThat(queryRepository.findNearestAncestorShareByShare(nearest(null, rootShare.getShareSeq(), null, ADMIN, false))).as("root 는 조상 공유 없음 → 행 없음").isNull();

            SharedResourceNearestData ar = queryRepository.findNearestAncestorShareAndRootByShare(nearest(null, otherShare.getShareSeq(), null, ADMIN, false));
            assertThat(ar.getShareSeq()).isEqualTo(rootShare.getShareSeq());
            assertThat(ar.getRootResourceSeq()).isEqualTo(root.getResourceSeq());
            assertThat(ar.getResourceType()).isEqualTo(ResourceType.FOLDER);
            SharedResourceNearestData arRoot = queryRepository.findNearestAncestorShareAndRootByShare(nearest(null, rootShare.getShareSeq(), null, ADMIN, false));
            assertThat(arRoot.getShareSeq()).as("자기 공유 제외 → null, 최상위 노드는 있음").isNull();
            assertThat(arRoot.getRootResourceSeq()).isEqualTo(root.getResourceSeq());
            assertThat(queryRepository.findNearestAncestorShareAndRootByShare(nearest(null, rootShare.getShareSeq(), null, ADMIN, true))).isNull();

            SharedResourceNearestData d = queryRepository.findNearestDescendantShareByShare(nearest(null, rootShare.getShareSeq(), root.getResourceSeq(), ADMIN, false));
            assertThat(d.getShareSeq()).as("root 하위에서 root 공유 제외 = other").isEqualTo(otherShare.getShareSeq());
            assertThat(queryRepository.findNearestDescendantShareByShare(nearest(null, otherShare.getShareSeq(), other.getResourceSeq(), ADMIN, false))).isNull();
        }

        @Test
        @DisplayName("findUnsharedDescendantsByShare — 다른 공유 서브트리 제외 · findAncestorSharesByResource — 자기 포함 조상 공유 전부")
        void unsharedAndAncestors() {
            Resource inOther = file(ADMIN, "in-other.txt", other);
            UnsharedResourceData u = queryRepository.findUnsharedDescendantsByShare(UnsharedResourcesFindQuery.builder().shareSeq(rootShare.getShareSeq()).userSeq(ADMIN).isDelete(false).build());
            assertThat(u.getSharedByUserSeq()).isEqualTo(ADMIN);
            assertThat(u.getUnsharedResources()).extracting(UnsharedResourceItem::getResourceSeq)
                    .as("root·mid·leaf·deep — other 와 그 하위는 제외").containsExactlyInAnyOrder(root.getResourceSeq(), mid.getResourceSeq(), leaf.getResourceSeq(), deep.getResourceSeq());
            assertThat(u.getUnsharedResources()).allSatisfy(i -> assertThat(i.getUserSeq()).isEqualTo(ADMIN));
            UnsharedResourceData uo = queryRepository.findUnsharedDescendantsByShare(UnsharedResourcesFindQuery.builder().shareSeq(otherShare.getShareSeq()).userSeq(ADMIN).isDelete(false).build());
            assertThat(uo.getUnsharedResources()).extracting(UnsharedResourceItem::getResourceSeq).containsExactlyInAnyOrder(other.getResourceSeq(), inOther.getResourceSeq());
            assertThat(queryRepository.findUnsharedDescendantsByShare(UnsharedResourcesFindQuery.builder().shareSeq(99999L).userSeq(ADMIN).isDelete(false).build())).isNull();

            List<SharedResourceData> leafAncestors = queryRepository.findAncestorSharesByResource(SharedResourceFindQuery.builder().resourceSeq(leaf.getResourceSeq()).userSeq(ADMIN).isDelete(false).build());
            assertThat(leafAncestors).extracting(SharedResourceData::getShareSeq).containsExactly(rootShare.getShareSeq());
            assertThat(leafAncestors.get(0).getResourceSeq()).isEqualTo(root.getResourceSeq());
            assertThat(leafAncestors.get(0).getResourceType()).isEqualTo(ResourceType.FOLDER);
            assertThat(leafAncestors.get(0).getShareType()).isEqualTo(ShareType.ORG_LINK);
            List<SharedResourceData> otherAncestors = queryRepository.findAncestorSharesByResource(SharedResourceFindQuery.builder().resourceSeq(inOther.getResourceSeq()).userSeq(ADMIN).isDelete(false).build());
            assertThat(otherAncestors).extracting(SharedResourceData::getShareSeq).containsExactlyInAnyOrder(otherShare.getShareSeq(), rootShare.getShareSeq());
            assertThat(queryRepository.findAncestorSharesByResource(SharedResourceFindQuery.builder().resourceSeq(top.getResourceSeq()).userSeq(ADMIN).isDelete(false).build())).isEmpty();
        }
    }

    // ================================================================== 쓰기

    @Nested
    @DisplayName("쓰기 — 조건부 UPDATE · DELETE 6종")
    class Writes {

        @Test
        @DisplayName("변경 감지(W9) — findByUserSeqAndShareSeq 관리 엔티티 + changeShare: 링크 공유는 권한 6컬럼, SPECIFIC_USERS 는 공유 유형만, 감사는 Auditing, 같은 값이면 UPDATE 없음, 다른 소유자 null")
        void changeShareByDirtyChecking() {
            Resource f = file(ADMIN, "u.txt", null);
            Share s = share(f, ShareType.ORG_LINK);
            entityManager.clear(); // 저장한 엔티티가 관리 중 — 첫 로드처럼 DB 에서 읽게
            Instant before = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

            Share managed = repository.findByUserSeqAndShareSeq(ADMIN, s.getShareSeq());
            managed.changeShare(ShareType.PUBLIC_LINK, Permissions.builder().canView(true).canEdit(true).canDownload(false).canUpload(false).canDelete(false).canShare(false).build());
            assertThat(col(s.getShareSeq(), "share_type", String.class)).isEqualTo("PUBLIC_LINK");
            assertThat(col(s.getShareSeq(), "can_edit", String.class)).isEqualTo("Y");
            assertThat(col(s.getShareSeq(), "can_download", String.class)).isEqualTo("N");
            assertThat(col(s.getShareSeq(), "update_user_seq", Integer.class)).as("Auditing — 시큐리티 사용자").isEqualTo(ADMIN);
            assertThat(col(s.getShareSeq(), "update_date", java.time.OffsetDateTime.class).toInstant()).isAfterOrEqualTo(before);

            managed.changeShare(ShareType.SPECIFIC_USERS, Permissions.builder().canView(false).canEdit(false).canDownload(false).build());
            assertThat(col(s.getShareSeq(), "share_type", String.class)).isEqualTo("SPECIFIC_USERS");
            assertThat(col(s.getShareSeq(), "can_edit", String.class)).as("SPECIFIC_USERS 는 권한 컬럼 불변(구 XML <set>)").isEqualTo("Y");

            jdbc.update("UPDATE tb_share SET update_date = NULL WHERE share_seq = ?", s.getShareSeq());
            entityManager.refresh(managed); // JdbcTemplate 로 바꾼 행 — 스냅샷을 DB 값으로
            managed.changeShare(ShareType.SPECIFIC_USERS, Permissions.forOwner());
            assertThat(col(s.getShareSeq(), "update_date", Object.class)).as("같은 값(대상 교체만) — UPDATE 없음").isNull();

            assertThat(repository.findByUserSeqAndShareSeq(SUPER_ADMIN, s.getShareSeq())).as("다른 소유자(파티션 키 조건)").isNull();
        }

        @Test
        @DisplayName("delete · deleteByResource · deleteByResources(IN·빈 목록) · deleteByUserSeqList(빈 목록) · deleteByTenant · deleteByTenantAndShareTypes(빈 목록)")
        void deletes() {
            Resource a = file(ADMIN, "a.txt", null);
            Resource b = file(ADMIN, "b.txt", null);
            Resource c = file(ADMIN, "c.txt", null);
            Resource d = file(ADMIN, "d.txt", null);
            Share sa = share(a, ShareType.ORG_LINK);
            Share sb = share(b, ShareType.SPECIFIC_USERS);
            share(c, ShareType.PUBLIC_LINK);
            share(d, ShareType.ORG_LINK);

            assertThat(repository.delete(ShareDeleteQuery.builder().shareSeq(sa.getShareSeq()).userSeq(SUPER_ADMIN).build())).as("다른 소유자").isZero();
            assertThat(repository.delete(ShareDeleteQuery.builder().shareSeq(sa.getShareSeq()).userSeq(ADMIN).build())).isEqualTo(1);
            assertThat(repository.deleteByResource(ShareDeleteResourceQuery.builder().resourceSeq(b.getResourceSeq()).userSeq(ADMIN).build())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_share WHERE share_seq = ?", Integer.class, sb.getShareSeq())).isZero();
            assertThat(repository.deleteByResources(ShareDeleteResourcesQuery.builder().resourceSeqList(List.of()).userSeq(ADMIN).build())).as("빈 목록").isZero();
            assertThat(repository.deleteByResources(ShareDeleteResourcesQuery.builder().resourceSeqList(List.of(c.getResourceSeq(), 99999L)).userSeq(ADMIN).build())).isEqualTo(1);
            assertThat(repository.deleteByUserSeqList(List.of())).as("빈 목록").isZero();
            assertThat(repository.deleteByUserSeqList(List.of(ADMIN))).as("d 만 남아 있음").isEqualTo(1);

            share(a, ShareType.ORG_LINK);
            share(b, ShareType.PUBLIC_LINK);
            share(c, ShareType.SPECIFIC_USERS);
            assertThat(repository.deleteByTenantAndShareTypes(1, List.of())).as("빈 목록").isZero();
            assertThat(repository.deleteByTenantAndShareTypes(1, List.of("ORG_LINK", "PUBLIC_LINK"))).isEqualTo(2);
            assertThat(queryRepository.existsByTenant(1)).as("SPECIFIC_USERS 1건 남음").isTrue();
            assertThat(repository.deleteByTenant(999)).isZero();
            assertThat(repository.deleteByTenant(1)).isEqualTo(1);
            assertThat(queryRepository.existsByTenant(1)).isFalse();
        }
    }
}
