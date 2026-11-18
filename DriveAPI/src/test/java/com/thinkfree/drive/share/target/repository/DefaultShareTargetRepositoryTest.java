package com.thinkfree.drive.share.target.repository;

import com.thinkfree.drive.global.time.DriveTime;
import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.common.search.enums.SortOrder;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.resource.entity.Resource;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.repository.ResourceRepository;
import com.thinkfree.drive.share.entity.Share;
import com.thinkfree.drive.share.enums.ShareType;
import com.thinkfree.drive.share.query.ShareTargetNearestFindQuery;
import com.thinkfree.drive.share.repository.ShareRepository;
import com.thinkfree.drive.share.target.data.*;
import com.thinkfree.drive.share.target.entity.ShareTarget;
import com.thinkfree.drive.share.target.enums.ShareTargetSortField;
import com.thinkfree.drive.share.target.key.ShareTargetDeleteKey;
import com.thinkfree.drive.share.target.query.*;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * 2단계 ShareTarget 이관 회귀 — 읽기({@link DefaultShareTargetQueryRepository} 10 statement)·쓰기({@link DefaultShareTargetRepository}: 배치 INSERT/UPSERT + DELETE 8). 배치·튜플 IN 삭제는 JdbcClient, 나머지 DELETE 7종은 JPQL.
 * 시드 = tenant 1, user 1 superadmin(테넌트 없음)·2 admin(tenant 1)·3 anonymous. 리소스는 {@link ResourceRepository}, 공유는 {@link ShareRepository}(JPA), 대상 사용자는 insertUserWithSeatCheck. 트랜잭션 롤백.
 * 검증 포인트 = 배치 INSERT(DEFAULT seq·감사 write-back·처리 건수)·UPSERT(충돌 SET), 공유받은 목록(기본 SHARE DESC·NAME 정렬·커서·필터·공유자 가시성),
 * 파티션 키, nearest 조상 공유 대상(사용자 한정·없으면 null), 대상별 해제 범위(다른 공유 서브트리 제외), 대상 사용자 목록 3종, 리소스별 대상 그룹핑 2종, DELETE 8종·빈 목록 가드.
 */
@DisplayName("DefaultShareTargetRepository / DefaultShareTargetQueryRepository — JdbcClient(PostgreSQL Testcontainers)")
class DefaultShareTargetRepositoryTest extends RepositoryTest {

    private static final int SUPER_ADMIN = 1;
    private static final int ADMIN = 2;
    private static final Instant D1 = DriveTime.toInstant(LocalDateTime.of(2026, 1, 1, 0, 0, 0));
    private static final Instant D2 = DriveTime.toInstant(LocalDateTime.of(2026, 1, 2, 0, 0, 0));

    @Autowired
    private ShareTargetRepository repository;
    @Autowired
    private ShareTargetQueryRepository queryRepository;
    @Autowired
    private ResourceRepository resourceRepository;
    @Autowired
    private ShareRepository shareRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private User t1, t2;

    @BeforeEach
    void setUp() {
        loginAs(ADMIN);
        t1 = user("st-t1");
        t2 = user("st-t2");
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

    private User user(String userId) {
        User u = User.createUser(userId, 1, null, SignupType.LOCAL, "대상 " + userId, UserStatus.ACTIVE, 0L, SUPER_ADMIN, null, null, null);
        userRepository.insertUserWithSeatCheck(u, false, 0);
        return u;
    }

    private Resource folder(String name, Resource parent) {
        Resource r = Resource.builder().userSeq(ADMIN).ownerSeq(ADMIN).resourceType(ResourceType.FOLDER)
                .parentFolderSeq(parent == null ? null : parent.getResourceSeq()).resourceName(name).originalFileName(name)
                .fullPathSeq(parent == null ? "" : parent.getFullPathSeq()).fullPath(parent == null ? "" : parent.getFullPath())
                .copyNumber(0).registerUserSeq(ADMIN).build();
        resourceRepository.save(r);
        r.initFolderPath();
        resourceRepository.flush();
        return r;
    }

    private Resource file(String name, Resource parent, long size, String ext) {
        Resource r = Resource.builder().userSeq(ADMIN).ownerSeq(ADMIN).latestOwnerSeq(ADMIN).resourceType(ResourceType.FILE)
                .parentFolderSeq(parent == null ? null : parent.getResourceSeq()).resourceName(name).originalFileName(name)
                .copyNumber(0).ext(ext).contentType("text/plain").sizeByte(size).versionRegisterDate(D1).registerUserSeq(ADMIN).build();
        resourceRepository.save(r);
        r.initResourceSaveName();
        resourceRepository.flush();
        return r;
    }

    private Share share(Resource resource, ShareType type) {
        return shareRepository.save(Share.builder().userSeq(ADMIN).resourceSeq(resource.getResourceSeq()).shareType(type)
                .canView(true).canEdit(false).canDownload(true).canUpload(false).canDelete(false).canShare(false).isPasswordEnabled(false).build());
    }

    private static ShareTarget target(Share share, int targetUserSeq, Instant shareDate, boolean canEdit) {
        return ShareTarget.builder().userSeq(targetUserSeq).shareSeq(share.getShareSeq()).resourceSeq(share.getResourceSeq()).sharedByUserSeq(ADMIN)
                .shareDate(shareDate).canView(true).canEdit(canEdit).canDownload(true).canUpload(false).canDelete(false).canShare(false)
                .updateUserSeq(ADMIN).updateDate(shareDate).build();
    }

    private Long targetSeq(int userSeq, long resourceSeq) {
        return jdbc.queryForObject("SELECT share_target_seq FROM tb_share_target WHERE user_seq = ? AND resource_seq = ? AND shared_by_user_seq = ?", Long.class, userSeq, resourceSeq, ADMIN);
    }

    private <T> T col(long shareTargetSeq, String column, Class<T> type) {
        return jdbc.queryForObject("SELECT " + column + " FROM tb_share_target WHERE share_target_seq = ?", type, shareTargetSeq);
    }

    private static ShareTargetListQuery.ShareTargetListQueryBuilder list(int userSeq, int pageSize) {
        return ShareTargetListQuery.builder().userSeq(userSeq).isDelete(false).page(CursorPaginationQuery.of(null, null, null, pageSize));
    }

    private static ShareTargetNearestFindQuery nearest(long shareSeq, List<Integer> users) {
        return ShareTargetNearestFindQuery.builder().userSeq(ADMIN).shareSeq(shareSeq).targetUserSeqList(users).isDelete(false).build();
    }

    // ================================================================== 배치 INSERT / UPSERT

    @Nested
    @DisplayName("insertAll / upsertAll")
    class Batch {

        @Test
        @DisplayName("insertAll — DEFAULT seq, 처리 건수 = 항목 수, register_* 감사 write-back, Y/N·share_date 왕복, 빈 목록 0")
        void insertAll() {
            Resource f = file("ins.txt", null, 1L, "txt");
            Share s = share(f, ShareType.SPECIFIC_USERS);
            ShareTarget a = target(s, t1.getUserSeq(), D1, false);
            ShareTarget b = ShareTarget.builder().userSeq(t2.getUserSeq()).shareSeq(s.getShareSeq()).resourceSeq(f.getResourceSeq()).sharedByUserSeq(ADMIN)
                    .shareDate(D2).canView(true).canEdit(true).canDownload(false).canUpload(false).canDelete(false).canShare(false).registerUserSeq(SUPER_ADMIN).build();
            assertThat(repository.insertAll(List.of(a, b))).isEqualTo(2);
            assertThat(repository.insertAll(List.of())).isZero();

            Long aSeq = targetSeq(t1.getUserSeq(), f.getResourceSeq());
            assertThat(aSeq).isPositive();
            assertThat(col(aSeq, "register_user_seq", Integer.class)).as("비면 컨텍스트 ADMIN").isEqualTo(ADMIN);
            assertThat(col(aSeq, "register_date", Object.class)).as("JdbcAudit 현재 시각").isNotNull();
            assertThat(a.getRegisterUserSeq()).as("W9 — 엔티티 write-back 없음(바인딩만)").isNull();
            assertThat(col(aSeq, "can_edit", String.class)).isEqualTo("N");
            assertThat(col(aSeq, "update_user_seq", Integer.class)).as("INSERT 는 update_* 안 씀").isNull();

            Long bSeq = targetSeq(t2.getUserSeq(), f.getResourceSeq());
            assertThat(bSeq).isPositive().isNotEqualTo(aSeq);
            assertThat(col(bSeq, "register_user_seq", Integer.class)).as("시큐리티 사용자 우선(선언 값 덮어씀)").isEqualTo(ADMIN);
            assertThat(col(bSeq, "can_edit", String.class)).isEqualTo("Y");

            List<ShareTargetPartitionKeyData> keys = queryRepository.findShareTargetPartitionKeys(ShareTargetFindQuery.builder().shareSeq(s.getShareSeq()).sharedByUserSeq(ADMIN).build());
            assertThat(keys).extracting(ShareTargetPartitionKeyData::getUserSeq).containsExactlyInAnyOrder(t1.getUserSeq(), t2.getUserSeq());
            assertThat(keys.get(0).getResourceSeq()).isEqualTo(f.getResourceSeq());
            assertThat(keys.get(0).getSharedByUserSeq()).isEqualTo(ADMIN);
        }

        @Test
        @DisplayName("upsertAll — 새 행은 INSERT, 유니크(user, resource, shared_by) 충돌은 권한 6컬럼 + update_* 갱신(seq 유지)")
        void upsertAll() {
            Resource f = file("ups.txt", null, 1L, "txt");
            Share s = share(f, ShareType.SPECIFIC_USERS);
            assertThat(repository.insertAll(List.of(target(s, t1.getUserSeq(), D1, false)))).isEqualTo(1);
            Long before = targetSeq(t1.getUserSeq(), f.getResourceSeq());

            ShareTarget changed = target(s, t1.getUserSeq(), D2, true);
            ShareTarget added = target(s, t2.getUserSeq(), D2, false);
            assertThat(repository.upsertAll(List.of(changed, added))).isEqualTo(2);
            assertThat(targetSeq(t1.getUserSeq(), f.getResourceSeq())).as("충돌 행 seq 유지").isEqualTo(before);
            assertThat(col(before, "can_edit", String.class)).isEqualTo("Y");
            assertThat(col(before, "update_user_seq", Integer.class)).as("converter 가 넣은 update_* 바인딩").isEqualTo(ADMIN);
            assertThat(col(before, "update_date", Object.class)).isNotNull();
            assertThat(col(before, "share_date", java.sql.Timestamp.class).toInstant()).as("충돌 시 share_date 는 안 바꿈").isEqualTo(D1);
            assertThat(targetSeq(t2.getUserSeq(), f.getResourceSeq())).isPositive();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_share_target WHERE resource_seq = ?", Integer.class, f.getResourceSeq())).isEqualTo(2);
        }
    }

    // ================================================================== 공유받은 목록

    @Nested
    @DisplayName("findAll — 공유받은 목록")
    class FindAll {

        private Resource folderA, fileB, fileC;

        @BeforeEach
        void shared() {
            folderA = folder("alpha", null);
            fileB = file("beta.txt", null, 10L, "txt");
            fileC = file("gamma.pdf", null, 30L, "pdf");
            Share sa = share(folderA, ShareType.SPECIFIC_USERS);
            Share sb = share(fileB, ShareType.SPECIFIC_USERS);
            Share sc = share(fileC, ShareType.SPECIFIC_USERS);
            repository.insertAll(List.of(target(sa, t1.getUserSeq(), D1, false), target(sb, t1.getUserSeq(), D2, false), target(sc, t1.getUserSeq(), DriveTime.toInstant(LocalDateTime.of(2026, 1, 3, 0, 0, 0)), false),
                    target(sb, t2.getUserSeq(), D1, false)));
        }

        @Test
        @DisplayName("기본 정렬(폴더 먼저·share_date DESC)·조인 컬럼 매핑·수신자 분리·필터(타입/확장자/소유자 아이디/날짜)·공유자 가시성")
        void defaultSortAndFilters() {
            List<ShareTargetData> rows = queryRepository.findAll(list(t1.getUserSeq(), 10).build());
            assertThat(rows).extracting(ShareTargetData::getResourceName).containsExactly("alpha", "gamma.pdf", "beta.txt");
            ShareTargetData b = rows.get(2);
            assertThat(b.getUserSeq()).isEqualTo(t1.getUserSeq());
            assertThat(b.getSharedByUserSeq()).isEqualTo(ADMIN);
            assertThat(b.getResourceType()).isEqualTo(ResourceType.FILE);
            assertThat(b.getOwnerId()).isEqualTo("admin");
            assertThat(b.getOwnerStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(b.getOwnerTenantName()).isEqualTo("기본 테넌트");
            assertThat(b.getSizeByte()).isEqualTo(10L);
            assertThat(b.getIsLocked()).isFalse();
            assertThat(b.getIsStarred()).isFalse();
            assertThat(b.getStarredSeq()).isNull();
            assertThat(b.getShareDate()).isEqualTo(D2);
            assertThat(b.getRegisterUserId()).as("대상 등록자 = 컨텍스트 admin").isEqualTo("admin");
            assertThat(b.getRegisterDate()).isNotNull();
            assertThat(b.getUpdateUserId()).as("INSERT 는 update_* 없음 → LEFT JOIN null").isNull();
            assertThat(b.getResourceRegisterUserId()).isEqualTo("admin");
            assertThat(b.getResourceRegisterDate()).isNotNull();
            assertThat(b.getResourceUpdateUserId()).as("updateResourceSaveName 감사").isEqualTo("admin");
            assertThat(b.getRecentUpdateDate()).isNull();

            assertThat(queryRepository.findAll(list(t2.getUserSeq(), 10).build())).extracting(ShareTargetData::getResourceName).containsExactly("beta.txt");
            assertThat(queryRepository.findAll(list(t1.getUserSeq(), 10).resourceType(ResourceType.FOLDER).build())).extracting(ShareTargetData::getResourceName).containsExactly("alpha");
            assertThat(queryRepository.findAll(list(t1.getUserSeq(), 10).fileType(com.thinkfree.drive.common.search.enums.FileType.PDF).build())).extracting(ShareTargetData::getResourceName).containsExactly("gamma.pdf");
            assertThat(queryRepository.findAll(list(t1.getUserSeq(), 10).userId("admin").build())).hasSize(3);
            assertThat(queryRepository.findAll(list(t1.getUserSeq(), 10).userId("nobody").build())).isEmpty();
            assertThat(queryRepository.findAll(list(t1.getUserSeq(), 10).from(DriveTime.startOfDay(java.time.LocalDate.now(DriveTime.zone()))).to(DriveTime.endExclusive(java.time.LocalDate.now(DriveTime.zone()))).build())).as("리소스 수정일 = 오늘").hasSize(3);
            assertThat(queryRepository.findAll(list(t1.getUserSeq(), 10).from(DriveTime.startOfDay(java.time.LocalDate.of(2000, 1, 1))).to(DriveTime.endExclusive(java.time.LocalDate.of(2000, 1, 2))).build())).isEmpty();
            assertThat(queryRepository.findAll(ShareTargetListQuery.builder().userSeq(t1.getUserSeq()).isDelete(true).page(CursorPaginationQuery.of(null, null, null, 10)).build())).as("휴지통 기준").isEmpty();

            jdbc.update("UPDATE tb_user SET status = 'DELETED' WHERE user_seq = ?", ADMIN);
            assertThat(queryRepository.findAll(list(t1.getUserSeq(), 10).build())).as("공유자 DELETED → 숨김").isEmpty();
        }

        @Test
        @DisplayName("커서 — 기본(share_date DESC) ps=1 두 페이지 왕복, NAME ASC 자연정렬 커서, SIZE DESC 커서")
        void cursors() {
            List<ShareTargetData> p1 = queryRepository.findAll(list(t1.getUserSeq(), 1).build());
            assertThat(p1).extracting(ShareTargetData::getResourceName).containsExactly("alpha");
            ShareTargetData last = p1.get(0);
            List<ShareTargetData> p2 = queryRepository.findAll(ShareTargetListQuery.builder().userSeq(t1.getUserSeq()).isDelete(false)
                    .page(CursorPaginationQuery.of(last.getShareDate(), last.getResourceSeq(), last.getResourceType(), 1)).build());
            assertThat(p2).extracting(ShareTargetData::getResourceName).as("폴더 커서 뒤 파일 중 share_date 최신").containsExactly("gamma.pdf");
            ShareTargetData last2 = p2.get(0);
            List<ShareTargetData> p3 = queryRepository.findAll(ShareTargetListQuery.builder().userSeq(t1.getUserSeq()).isDelete(false)
                    .page(CursorPaginationQuery.of(last2.getShareDate(), last2.getResourceSeq(), last2.getResourceType(), 1)).build());
            assertThat(p3).extracting(ShareTargetData::getResourceName).containsExactly("beta.txt");

            List<ShareTargetData> name1 = queryRepository.findAll(list(t1.getUserSeq(), 2).sortBy(ShareTargetSortField.NAME).sortOrder(SortOrder.ASC).build());
            assertThat(name1).extracting(ShareTargetData::getResourceName).containsExactly("alpha", "beta.txt");
            ShareTargetData nl = name1.get(1);
            List<ShareTargetData> name2 = queryRepository.findAll(ShareTargetListQuery.builder().userSeq(t1.getUserSeq()).isDelete(false).sortBy(ShareTargetSortField.NAME).sortOrder(SortOrder.ASC)
                    .page(CursorPaginationQuery.of(nl.getResourceName(), nl.getResourceSeq(), nl.getResourceType(), 2)).build());
            assertThat(name2).extracting(ShareTargetData::getResourceName).containsExactly("gamma.pdf");

            List<ShareTargetData> size1 = queryRepository.findAll(list(t1.getUserSeq(), 2).sortBy(ShareTargetSortField.SIZE).sortOrder(SortOrder.DESC).build());
            assertThat(size1).extracting(ShareTargetData::getResourceName).containsExactly("alpha", "gamma.pdf");
            ShareTargetData sl = size1.get(1);
            List<ShareTargetData> size2 = queryRepository.findAll(ShareTargetListQuery.builder().userSeq(t1.getUserSeq()).isDelete(false).sortBy(ShareTargetSortField.SIZE).sortOrder(SortOrder.DESC)
                    .page(CursorPaginationQuery.of(sl.getSizeByte(), sl.getResourceSeq(), sl.getResourceType(), 2)).build());
            assertThat(size2).extracting(ShareTargetData::getResourceName).containsExactly("beta.txt");
        }
    }

    // ================================================================== nearest · 해제 범위 · 사용자 목록

    @Nested
    @DisplayName("nearest 조상 공유 대상 · 대상별 해제 범위 · 대상 사용자 목록 · 리소스별 대상 그룹핑")
    class NearestAndUsers {

        private Resource root, sub, leaf, deep;
        private Share rootShare, subShare;
        private User t3;

        @BeforeEach
        void tree() {
            // root(S1: t1, t2) / leaf.txt , root / sub(S2: t1, t3) / deep.txt
            t3 = user("st-t3");
            root = folder("root", null);
            sub = folder("sub", root);
            leaf = file("leaf.txt", root, 1L, "txt");
            deep = file("deep.txt", sub, 1L, "txt");
            rootShare = share(root, ShareType.SPECIFIC_USERS);
            subShare = share(sub, ShareType.SPECIFIC_USERS);
            repository.insertAll(List.of(target(rootShare, t1.getUserSeq(), D1, false), target(rootShare, t2.getUserSeq(), D1, false),
                    target(subShare, t1.getUserSeq(), D2, true), target(subShare, t3.getUserSeq(), D2, false)));
        }

        @Test
        @DisplayName("findNearestAncestorShareTargets(ByUsers) — 같은 사용자를 둔 조상 공유 대상, 없으면 ancestorShareTargetSeq null, 테넌트 컬럼, 빈 목록 가드")
        void nearestTargets() {
            List<ShareTargetNearestData> all = queryRepository.findNearestAncestorShareTargets(nearest(subShare.getShareSeq(), List.of()));
            assertThat(all).extracting(ShareTargetNearestData::getUserSeq).containsExactlyInAnyOrder(t1.getUserSeq(), t3.getUserSeq());
            ShareTargetNearestData n1 = all.stream().filter(n -> n.getUserSeq().equals(t1.getUserSeq())).findFirst().orElseThrow();
            assertThat(n1.getShareTargetSeq()).isEqualTo(targetSeq(t1.getUserSeq(), sub.getResourceSeq()));
            assertThat(n1.getAncestorShareTargetSeq()).as("root 공유의 t1 대상").isEqualTo(targetSeq(t1.getUserSeq(), root.getResourceSeq()));
            assertThat(n1.getSharedByUserSeq()).isEqualTo(ADMIN);
            assertThat(n1.getTenantSeq()).isEqualTo(1);
            assertThat(n1.getOwnerTenantSeq()).isEqualTo(1);
            ShareTargetNearestData n3 = all.stream().filter(n -> n.getUserSeq().equals(t3.getUserSeq())).findFirst().orElseThrow();
            assertThat(n3.getAncestorShareTargetSeq()).as("t3 는 조상 공유 대상 아님").isNull();

            assertThat(queryRepository.findNearestAncestorShareTargets(nearest(rootShare.getShareSeq(), List.of()))).extracting(ShareTargetNearestData::getAncestorShareTargetSeq).containsOnlyNulls();
            assertThat(queryRepository.findNearestAncestorShareTargetsByUsers(nearest(subShare.getShareSeq(), List.of(t1.getUserSeq())))).singleElement()
                    .satisfies(n -> assertThat(n.getAncestorShareTargetSeq()).isNotNull());
            assertThat(queryRepository.findNearestAncestorShareTargetsByUsers(nearest(subShare.getShareSeq(), List.of(t2.getUserSeq())))).as("sub 공유에 t2 없음").isEmpty();
            assertThat(queryRepository.findNearestAncestorShareTargetsByUsers(nearest(subShare.getShareSeq(), List.of()))).as("빈 목록 가드").isEmpty();
            // TARGETS cte 는 root_data 와 무관(XML 동일) → 휴지통 기준이면 조상 탐색만 비고 대상 행은 ancestor null 로 남는다
            assertThat(queryRepository.findNearestAncestorShareTargets(ShareTargetNearestFindQuery.builder().userSeq(ADMIN).shareSeq(subShare.getShareSeq()).isDelete(true).build()))
                    .as("휴지통 기준 root_data 없음 → 대상 2건, 조상 없음").hasSize(2).extracting(ShareTargetNearestData::getAncestorShareTargetSeq).containsOnlyNulls();
        }

        @Test
        @DisplayName("findUnsharedDescendantsByTargets — t1 은 sub 서브트리(다른 공유 대상) 제외, t2 는 전체, 빈 목록 가드")
        void unshared() {
            List<UnsharedTargetsData> u = queryRepository.findUnsharedDescendantsByTargets(UnsharedTargetsFindQuery.builder().targetUserSeqList(List.of(t1.getUserSeq(), t2.getUserSeq())).userSeq(ADMIN).shareSeq(rootShare.getShareSeq()).isDelete(false).build());
            assertThat(u).extracting(UnsharedTargetsData::getUserSeq).containsExactlyInAnyOrder(t1.getUserSeq(), t2.getUserSeq());
            UnsharedTargetsData u1 = u.stream().filter(x -> x.getUserSeq().equals(t1.getUserSeq())).findFirst().orElseThrow();
            assertThat(u1.getResourceSeqList()).containsExactlyInAnyOrder(root.getResourceSeq(), leaf.getResourceSeq());
            UnsharedTargetsData u2 = u.stream().filter(x -> x.getUserSeq().equals(t2.getUserSeq())).findFirst().orElseThrow();
            assertThat(u2.getResourceSeqList()).containsExactlyInAnyOrder(root.getResourceSeq(), sub.getResourceSeq(), leaf.getResourceSeq(), deep.getResourceSeq());
            assertThat(queryRepository.findUnsharedDescendantsByTargets(UnsharedTargetsFindQuery.builder().targetUserSeqList(List.of()).userSeq(ADMIN).shareSeq(rootShare.getShareSeq()).isDelete(false).build())).isEmpty();
            assertThat(queryRepository.findUnsharedDescendantsByTargets(UnsharedTargetsFindQuery.builder().targetUserSeqList(List.of(t3.getUserSeq())).userSeq(ADMIN).shareSeq(rootShare.getShareSeq()).isDelete(false).build())).as("root 공유 대상 아님").isEmpty();
        }

        @Test
        @DisplayName("대상 사용자 목록 3종(IN 가드·리소스 is_delete 조인·삭제 리소스는 조인 없음) · 리소스별 대상 그룹핑 2종(resource_name 유무)")
        void usersAndGroups() {
            ShareTargetUsersQuery byShares = ShareTargetUsersQuery.builder().sharedByUserSeq(ADMIN).shareSeqList(List.of(rootShare.getShareSeq(), subShare.getShareSeq())).isDelete(false).build();
            assertThat(queryRepository.findShareTargetUsersByShares(byShares)).extracting(ShareTargetUserData::getUserSeq)
                    .containsExactlyInAnyOrder(t1.getUserSeq(), t2.getUserSeq(), t1.getUserSeq(), t3.getUserSeq());
            assertThat(queryRepository.findShareTargetUsersByShares(ShareTargetUsersQuery.builder().sharedByUserSeq(ADMIN).shareSeqList(List.of()).isDelete(false).build())).isEmpty();

            ShareTargetUsersQuery byResource = ShareTargetUsersQuery.builder().sharedByUserSeq(ADMIN).targetUserSeq(ADMIN).resourceSeq(root.getResourceSeq()).isDelete(false).build();
            assertThat(queryRepository.findShareTargetUsersByResource(byResource)).extracting(ShareTargetUserData::getUserSeq).containsExactlyInAnyOrder(t1.getUserSeq(), t2.getUserSeq());
            assertThat(queryRepository.findShareTargetUsersByResource(ShareTargetUsersQuery.builder().sharedByUserSeq(ADMIN).targetUserSeq(ADMIN).resourceSeq(root.getResourceSeq()).isDelete(true).build())).isEmpty();
            jdbc.update("UPDATE tb_resource SET is_delete = 'Y' WHERE resource_seq = ?", sub.getResourceSeq());
            assertThat(queryRepository.findShareTargetUsersByResource(ShareTargetUsersQuery.builder().sharedByUserSeq(ADMIN).targetUserSeq(ADMIN).resourceSeq(sub.getResourceSeq()).isDelete(false).build())).isEmpty();
            assertThat(queryRepository.findShareTargetUsersByDeletedResource(ShareTargetUsersQuery.builder().sharedByUserSeq(ADMIN).resourceSeq(sub.getResourceSeq()).build()))
                    .extracting(ShareTargetUserData::getUserSeq).containsExactlyInAnyOrder(t1.getUserSeq(), t3.getUserSeq());

            List<ShareTargetResourceUsersData> grouped = queryRepository.findShareTargetResourceUsersByResources(ShareTargetResourceUsersQuery.builder().sharedByUserSeq(ADMIN).targetUserSeq(ADMIN)
                    .resourceSeqList(List.of(root.getResourceSeq(), sub.getResourceSeq(), 99999L)).isDelete(false).build());
            assertThat(grouped).as("sub 는 is_delete=Y 라 제외").singleElement().satisfies(g -> {
                assertThat(g.getResourceSeq()).isEqualTo(root.getResourceSeq());
                assertThat(g.getResourceName()).isEqualTo("root");
                assertThat(g.getTargetUsers()).containsExactlyInAnyOrder(t1.getUserSeq(), t2.getUserSeq());
            });
            List<ShareTargetResourceUsersData> deleted = queryRepository.findShareTargetResourceUsersByDeletedResources(ShareTargetResourceUsersQuery.builder().sharedByUserSeq(ADMIN)
                    .resourceSeqList(List.of(root.getResourceSeq(), sub.getResourceSeq())).build());
            assertThat(deleted).extracting(ShareTargetResourceUsersData::getResourceSeq).containsExactlyInAnyOrder(root.getResourceSeq(), sub.getResourceSeq());
            assertThat(deleted).allSatisfy(g -> assertThat(g.getResourceName()).isNull());
            assertThat(deleted.stream().filter(g -> g.getResourceSeq().equals(sub.getResourceSeq())).findFirst().orElseThrow().getTargetUsers()).containsExactlyInAnyOrder(t1.getUserSeq(), t3.getUserSeq());
            assertThat(queryRepository.findShareTargetResourceUsersByResources(ShareTargetResourceUsersQuery.builder().sharedByUserSeq(ADMIN).targetUserSeq(ADMIN).resourceSeqList(List.of()).isDelete(false).build())).isEmpty();
        }
    }

    // ================================================================== DELETE

    @Nested
    @DisplayName("DELETE 8종")
    class Deletes {

        @Test
        @DisplayName("키 튜플·리소스·리소스 IN·수신자 IN·공유자 IN·테넌트(자기 제외)·테넌트+공유 유형·테넌트 외부 접근자, 빈 목록 가드")
        void deletes() {
            Resource a = file("a.txt", null, 1L, "txt");
            Resource b = file("b.txt", null, 1L, "txt");
            Resource c = file("c.txt", null, 1L, "txt");
            Share sa = share(a, ShareType.SPECIFIC_USERS);
            Share sb = share(b, ShareType.ORG_LINK);
            Share sc = share(c, ShareType.SPECIFIC_USERS);
            repository.insertAll(List.of(target(sa, t1.getUserSeq(), D1, false), target(sa, t2.getUserSeq(), D1, false), target(sa, SUPER_ADMIN, D1, false), target(sa, ADMIN, D1, false),
                    target(sb, t1.getUserSeq(), D1, false), target(sc, t1.getUserSeq(), D1, false), target(sc, t2.getUserSeq(), D1, false)));
            int count = jdbc.queryForObject("SELECT COUNT(*) FROM tb_share_target WHERE shared_by_user_seq = ?", Integer.class, ADMIN);
            assertThat(count).isEqualTo(7);

            Long k1 = targetSeq(t1.getUserSeq(), a.getResourceSeq());
            assertThat(repository.deleteByShareTargetDeleteKeys(ShareTargetDeleteKeysQuery.builder().deleteKeys(List.of()).sharedByUserSeq(ADMIN).build())).isZero();
            assertThat(repository.deleteByShareTargetDeleteKeys(ShareTargetDeleteKeysQuery.builder().deleteKeys(List.of(new ShareTargetDeleteKey(t1.getUserSeq(), k1))).sharedByUserSeq(SUPER_ADMIN).build())).as("다른 공유자").isZero();
            assertThat(repository.deleteByShareTargetDeleteKeys(ShareTargetDeleteKeysQuery.builder().deleteKeys(List.of(new ShareTargetDeleteKey(t1.getUserSeq(), k1))).sharedByUserSeq(ADMIN).build())).isEqualTo(1);

            assertThat(repository.deleteOtherTenantByTenant(1)).as("테넌트 밖 수신자 = superadmin").isEqualTo(1);
            assertThat(repository.deleteByTenantAndShareTypes(1, List.of())).isZero();
            assertThat(repository.deleteByTenantAndShareTypes(1, List.of("ORG_LINK"))).as("b 의 대상").isEqualTo(1);
            assertThat(repository.deleteByResource(ShareTargetDeleteResourceQuery.builder().sharedByUserSeq(ADMIN).resourceSeq(c.getResourceSeq()).build())).isEqualTo(2);
            assertThat(repository.deleteByResources(ShareTargetDeleteResourcesQuery.builder().sharedByUserSeq(ADMIN).resourceSeqList(List.of()).build())).isZero();

            repository.insertAll(List.of(target(sc, t1.getUserSeq(), D1, false)));
            assertThat(repository.deleteByResources(ShareTargetDeleteResourcesQuery.builder().sharedByUserSeq(ADMIN).resourceSeqList(List.of(c.getResourceSeq(), 99999L)).build())).isEqualTo(1);
            assertThat(repository.deleteByUserSeqList(List.of())).isZero();
            assertThat(repository.deleteByUserSeqList(List.of(t2.getUserSeq()))).as("a 의 t2").isEqualTo(1);
            assertThat(repository.deleteByTenant(1)).as("남은 a 의 ADMIN 자기 대상은 제외 → 0").isZero();
            assertThat(repository.deleteBySharedByUserSeqList(List.of())).isZero();
            assertThat(repository.deleteBySharedByUserSeqList(List.of(ADMIN))).as("자기 대상 1건").isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_share_target WHERE shared_by_user_seq = ?", Integer.class, ADMIN)).isZero();
        }
    }
}
