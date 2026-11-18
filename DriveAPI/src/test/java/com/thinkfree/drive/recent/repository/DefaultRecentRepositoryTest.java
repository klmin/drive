package com.thinkfree.drive.recent.repository;

import com.thinkfree.drive.global.time.DriveTime;
import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.common.search.enums.FileType;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.partition.PartitionKey;
import com.thinkfree.drive.recent.data.RecentData;
import com.thinkfree.drive.recent.entity.Recent;
import com.thinkfree.drive.recent.query.*;
import com.thinkfree.drive.resource.entity.Resource;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.repository.ResourceRepository;
import com.thinkfree.drive.share.entity.Share;
import com.thinkfree.drive.share.enums.ShareType;
import com.thinkfree.drive.share.repository.ShareRepository;
import com.thinkfree.drive.starred.entity.Starred;
import com.thinkfree.drive.starred.repository.StarredRepository;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * 2단계 Recent 이관 회귀 — 읽기({@link DefaultRecentQueryRepository} findAll)·쓰기({@link DefaultRecentRepository}: upsert + DELETE 12종). upsert·튜플 IN 삭제는 JdbcClient, 나머지 DELETE 11종은 JPQL.
 * 시드 = tenant 1, user 1 superadmin(테넌트 없음)·2 admin(tenant 1)·3 anonymous. 리소스는 {@link ResourceRepository}, 공유는 {@link ShareRepository}, 별표는 {@link StarredRepository}, 수신자는 insertUserWithSeatCheck. 트랜잭션 롤백.
 * 검증 포인트 = upsert(DEFAULT seq·충돌 시 update_date 만·감사 write-back), 목록(열람일 DESC·부모 폴더·별표 조인·필터·가시성)·커서 왕복, DELETE 12종(선택 제외 목록·튜플 IN·항목별 루프·테넌트 3종)·빈 목록 가드.
 */
@DisplayName("DefaultRecentRepository / DefaultRecentQueryRepository — JdbcClient(PostgreSQL Testcontainers)")
class DefaultRecentRepositoryTest extends RepositoryTest {

    private static final int SUPER_ADMIN = 1;
    private static final int ADMIN = 2;
    private static final Instant D1 = DriveTime.toInstant(LocalDateTime.of(2026, 1, 1, 0, 0, 0));
    private static final Instant D2 = DriveTime.toInstant(LocalDateTime.of(2026, 1, 2, 0, 0, 0));
    private static final Instant D3 = DriveTime.toInstant(LocalDateTime.of(2026, 1, 3, 0, 0, 0));

    @Autowired
    private RecentRepository repository;
    @Autowired
    private RecentQueryRepository queryRepository;
    @Autowired
    private ResourceRepository resourceRepository;
    @Autowired
    private ShareRepository shareRepository;
    @Autowired
    private StarredRepository starredRepository;
    @Autowired
    private JdbcTemplate jdbc;

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

    private User user(String userId) {
        User u = User.createUser(userId, 1, null, SignupType.LOCAL, "수신자 " + userId, UserStatus.ACTIVE, 0L, SUPER_ADMIN, null, null, null);
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

    /** 열람 기록: userSeq 가 sharedBy(소유자 ADMIN)의 리소스를 updateDate 에 열람. */
    private Recent open(int userSeq, Resource resource, Instant updateDate) {
        Recent r = Recent.builder().userSeq(userSeq).resourceSeq(resource.getResourceSeq()).sharedByUserSeq(ADMIN).updateDate(updateDate).build();
        repository.save(r);
        return r;
    }

    private int count(int sharedBy) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tb_recent WHERE shared_by_user_seq = ?", Integer.class, sharedBy);
    }

    private Long recentSeq(int userSeq, long resourceSeq) {
        return jdbc.queryForObject("SELECT recent_seq FROM tb_recent WHERE user_seq = ? AND resource_seq = ? AND shared_by_user_seq = ?", Long.class, userSeq, resourceSeq, ADMIN);
    }

    private static RecentListQuery.RecentListQueryBuilder list(int userSeq, int pageSize) {
        return RecentListQuery.builder().userSeq(userSeq).isDelete(false).page(CursorPaginationQuery.of(null, null, null, pageSize));
    }

    // ================================================================== upsert · 목록

    @Nested
    @DisplayName("save(upsert) · findAll")
    class SaveAndFind {

        @Test
        @DisplayName("save — DEFAULT seq INSERT·register_* 감사 바인딩, 충돌(user, resource, shared_by)은 update_date 만 갱신(seq·register 유지)")
        void upsert() {
            Resource f = file("doc.txt", null, 10L, "txt");
            open(ADMIN, f, D1);
            Long seq = recentSeq(ADMIN, f.getResourceSeq());
            assertThat(seq).isPositive();
            assertThat(jdbc.queryForObject("SELECT register_user_seq FROM tb_recent WHERE recent_seq = ?", Integer.class, seq)).as("JdbcAudit → 컨텍스트 ADMIN(바인딩만, 엔티티 write-back 없음 — W3)").isEqualTo(ADMIN);
            assertThat(jdbc.queryForObject("SELECT register_date FROM tb_recent WHERE recent_seq = ?", java.sql.Timestamp.class, seq)).isNotNull();
            assertThat(jdbc.queryForObject("SELECT update_date FROM tb_recent WHERE recent_seq = ?", java.sql.Timestamp.class, seq).toInstant()).isEqualTo(D1);

            Recent again = Recent.builder().userSeq(ADMIN).resourceSeq(f.getResourceSeq()).sharedByUserSeq(ADMIN).registerUserSeq(SUPER_ADMIN).updateDate(D2).build();
            assertThat(repository.save(again)).isEqualTo(1);
            assertThat(recentSeq(ADMIN, f.getResourceSeq())).as("충돌 행 seq 유지").isEqualTo(seq);
            assertThat(jdbc.queryForObject("SELECT update_date FROM tb_recent WHERE recent_seq = ?", java.sql.Timestamp.class, seq).toInstant()).isEqualTo(D2);
            assertThat(jdbc.queryForObject("SELECT register_user_seq FROM tb_recent WHERE recent_seq = ?", Integer.class, seq)).as("충돌 시 register_* 는 안 바꿈").isEqualTo(ADMIN);
            assertThat(count(ADMIN)).isEqualTo(1);
        }

        @Test
        @DisplayName("findAll — 열람일 DESC·resource_seq DESC, 조인 매핑(부모 폴더·별표), 필터(타입/확장자/소유자 아이디/열람일 범위)·휴지통·공유자 DELETED·수신자 분리, 커서 왕복")
        void findAll() {
            Resource alpha = folder("alpha", null);
            Resource beta = file("beta.txt", alpha, 10L, "txt");
            Resource gamma = file("gamma.pdf", null, 30L, "pdf");
            User t1 = user("rec-t1");
            open(ADMIN, alpha, D1);
            open(ADMIN, beta, D3);
            open(ADMIN, gamma, D2);
            open(t1.getUserSeq(), beta, D1);
            starredRepository.save(Starred.builder().userSeq(ADMIN).resourceSeq(beta.getResourceSeq()).sharedByUserSeq(ADMIN).build());

            List<RecentData> rows = queryRepository.findAll(list(ADMIN, 10).build());
            assertThat(rows).extracting(RecentData::getResourceName).as("열람일 DESC").containsExactly("beta.txt", "gamma.pdf", "alpha");
            RecentData b = rows.get(0);
            assertThat(b.getRecentSeq()).isEqualTo(recentSeq(ADMIN, beta.getResourceSeq()));
            assertThat(b.getUserSeq()).isEqualTo(ADMIN);
            assertThat(b.getSharedByUserSeq()).isEqualTo(ADMIN);
            assertThat(b.getOwnerId()).isEqualTo("admin");
            assertThat(b.getOwnerStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(b.getOwnerTenantName()).isEqualTo("기본 테넌트");
            assertThat(b.getResourceType()).isEqualTo(ResourceType.FILE);
            assertThat(b.getParentFolderSeq()).isEqualTo(alpha.getResourceSeq());
            assertThat(b.getParentFolderName()).isEqualTo("alpha");
            assertThat(b.getFullPath()).isEqualTo("alpha/");
            assertThat(b.getSizeByte()).isEqualTo(10L);
            assertThat(b.getIsLocked()).isFalse();
            assertThat(b.getIsStarred()).as("별표 조인").isTrue();
            assertThat(b.getStarredSeq()).isNotNull();
            assertThat(rows.get(1).getIsStarred()).isFalse();
            assertThat(rows.get(1).getStarredSeq()).isNull();
            assertThat(b.getResourceRegisterUserId()).isEqualTo("admin");
            assertThat(b.getResourceUpdateUserId()).as("updateResourceSaveName 감사").isEqualTo("admin");
            assertThat(b.getRecentRegisterDate()).isNotNull();
            assertThat(b.getRecentUpdateDate()).isEqualTo(D3);

            assertThat(queryRepository.findAll(list(t1.getUserSeq(), 10).build())).extracting(RecentData::getResourceName).containsExactly("beta.txt");
            assertThat(queryRepository.findAll(list(ADMIN, 10).resourceType(ResourceType.FOLDER).build())).extracting(RecentData::getResourceName).containsExactly("alpha");
            assertThat(queryRepository.findAll(list(ADMIN, 10).fileType(FileType.PDF).build())).extracting(RecentData::getResourceName).containsExactly("gamma.pdf");
            assertThat(queryRepository.findAll(list(ADMIN, 10).userId("admin").build())).hasSize(3);
            assertThat(queryRepository.findAll(list(ADMIN, 10).userId("nobody").build())).isEmpty();
            assertThat(queryRepository.findAll(list(ADMIN, 10).from(DriveTime.startOfDay(LocalDate.of(2026, 1, 2))).to(DriveTime.endExclusive(LocalDate.of(2026, 1, 3))).build())).as("열람일 범위").extracting(RecentData::getResourceName).containsExactly("beta.txt", "gamma.pdf");
            assertThat(queryRepository.findAll(RecentListQuery.builder().userSeq(ADMIN).isDelete(true).page(CursorPaginationQuery.of(null, null, null, 10)).build())).as("휴지통 기준").isEmpty();

            List<RecentData> p1 = queryRepository.findAll(list(ADMIN, 2).build());
            assertThat(p1).extracting(RecentData::getResourceName).containsExactly("beta.txt", "gamma.pdf");
            RecentData last = p1.get(1);
            List<RecentData> p2 = queryRepository.findAll(RecentListQuery.builder().userSeq(ADMIN).isDelete(false)
                    .page(CursorPaginationQuery.of(last.getRecentUpdateDate(), last.getResourceSeq(), last.getResourceType(), 2)).build());
            assertThat(p2).extracting(RecentData::getResourceName).containsExactly("alpha");
            open(ADMIN, gamma, D3);
            List<RecentData> tie = queryRepository.findAll(list(ADMIN, 1).build());
            RecentData tieLast = tie.get(0);
            List<RecentData> tie2 = queryRepository.findAll(RecentListQuery.builder().userSeq(ADMIN).isDelete(false)
                    .page(CursorPaginationQuery.of(tieLast.getRecentUpdateDate(), tieLast.getResourceSeq(), tieLast.getResourceType(), 1)).build());
            assertThat(tie2).as("같은 열람일은 resource_seq DESC tie-break").hasSize(1);
            assertThat(tie2.get(0).getResourceSeq()).isLessThan(tieLast.getResourceSeq());

            jdbc.update("UPDATE tb_user SET status = 'DELETED' WHERE user_seq = ?", ADMIN);
            assertThat(queryRepository.findAll(list(t1.getUserSeq(), 10).build())).as("공유자 DELETED → 숨김").isEmpty();
        }

        @Test
        @DisplayName("열람일 범위는 설정 시간대(Asia/Seoul) 하루 [00:00, 다음날 00:00) — 23:59:59.5 는 포함, 다음날 00:00 은 제외(세션 시간대 무관)")
        void findAll_dayBoundary() {
            Resource late = file("late.txt", null, 1L, "txt");
            Resource midnight = file("midnight.txt", null, 1L, "txt");
            Resource early = file("early.txt", null, 1L, "txt");
            open(ADMIN, late, D1);
            open(ADMIN, midnight, D1);
            open(ADMIN, early, D1);
            setRecentDate(late, "2026-01-03 23:59:59.5+09");
            setRecentDate(midnight, "2026-01-04 00:00:00+09");
            setRecentDate(early, "2026-01-03 00:00:00+09");

            assertThat(queryRepository.findAll(list(ADMIN, 10).from(DriveTime.startOfDay(LocalDate.of(2026, 1, 3))).to(DriveTime.endExclusive(LocalDate.of(2026, 1, 3))).build()))
                    .extracting(RecentData::getResourceName).containsExactlyInAnyOrder("late.txt", "early.txt");
            assertThat(queryRepository.findAll(list(ADMIN, 10).from(DriveTime.startOfDay(LocalDate.of(2026, 1, 4))).to(DriveTime.endExclusive(LocalDate.of(2026, 1, 4))).build()))
                    .extracting(RecentData::getResourceName).containsExactly("midnight.txt");
        }

        @Test
        @DisplayName("findAll — 별표 조인은 shared_by_user_seq 까지 맞춘다: 같은 사용자·리소스에 다른 공유자 별표 행이 있어도 한 행, 그 별표는 무시(작업 E5)")
        void findAll_starredJoinMatchesSharedBy() {
            Resource doc = file("doc.txt", null, 1L, "txt");
            open(ADMIN, doc, D1);
            // 소유자 변경 이동 전의 옛 공유자(SUPER_ADMIN) 기준 별표가 남은 상황 — uniq_starred(user, resource, shared_by) 라 공존 가능
            starredRepository.save(Starred.builder().userSeq(ADMIN).resourceSeq(doc.getResourceSeq()).sharedByUserSeq(SUPER_ADMIN).build());

            List<RecentData> rows = queryRepository.findAll(list(ADMIN, 10).build());
            assertThat(rows).as("다른 공유자 별표로 행이 늘지 않음").hasSize(1);
            assertThat(rows.get(0).getIsStarred()).as("열람 기록의 공유자(ADMIN) 기준 별표 없음").isFalse();

            starredRepository.save(Starred.builder().userSeq(ADMIN).resourceSeq(doc.getResourceSeq()).sharedByUserSeq(ADMIN).build());
            rows = queryRepository.findAll(list(ADMIN, 10).build());
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getIsStarred()).isTrue();
        }

        private void setRecentDate(Resource resource, String timestamptz) {
            jdbc.update("UPDATE tb_recent SET update_date = CAST(? AS timestamptz) WHERE user_seq = ? AND resource_seq = ?", timestamptz, ADMIN, resource.getResourceSeq());
        }
    }

    // ================================================================== DELETE

    @Nested
    @DisplayName("DELETE 12종")
    class Deletes {

        private Resource a, b, c;
        private User t1, t2;

        @BeforeEach
        void fixture() {
            a = file("a.txt", null, 1L, "txt");
            b = file("b.txt", null, 1L, "txt");
            c = file("c.txt", null, 1L, "txt");
            t1 = user("rec-t1");
            t2 = user("rec-t2");
        }

        @Test
        @DisplayName("deleteByResource(s) · ExcludingOwner(선택 제외 목록) · deleteByShareTargets(튜플 IN) · deleteByUserResources(배치) · 빈 목록 가드")
        void byResourceAndKeys() {
            open(ADMIN, a, D1);
            open(t1.getUserSeq(), a, D1);
            open(t2.getUserSeq(), a, D1);
            open(t1.getUserSeq(), b, D1);
            open(t2.getUserSeq(), b, D1);
            open(t1.getUserSeq(), c, D1);
            assertThat(count(ADMIN)).isEqualTo(6);

            assertThat(repository.deleteByResourceExcludingOwner(RecentDeleteResourceQuery.builder().userSeq(t1.getUserSeq()).sharedByUserSeq(ADMIN).resourceSeq(a.getResourceSeq()).excludeUserSeqList(List.of(t2.getUserSeq(), ADMIN)).build())).as("전부 제외").isZero();
            assertThat(repository.deleteByResourceExcludingOwner(RecentDeleteResourceQuery.builder().userSeq(t1.getUserSeq()).sharedByUserSeq(ADMIN).resourceSeq(a.getResourceSeq()).build())).as("t2·ADMIN").isEqualTo(2);
            assertThat(repository.deleteByResourcesExcludingOwner(RecentDeleteResourcesQuery.builder().userSeq(t2.getUserSeq()).sharedByUserSeq(ADMIN).resourceSeqList(List.of()).build())).isZero();
            assertThat(repository.deleteByResourcesExcludingOwner(RecentDeleteResourcesQuery.builder().userSeq(t2.getUserSeq()).sharedByUserSeq(ADMIN).resourceSeqList(List.of(b.getResourceSeq(), c.getResourceSeq())).excludeUserSeqList(List.of(t1.getUserSeq())).build())).isZero();
            assertThat(repository.deleteByResourcesExcludingOwner(RecentDeleteResourcesQuery.builder().userSeq(t2.getUserSeq()).sharedByUserSeq(ADMIN).resourceSeqList(List.of(b.getResourceSeq(), c.getResourceSeq())).build())).as("b·c 의 t1").isEqualTo(2);
            assertThat(count(ADMIN)).as("a:t1, b:t2").isEqualTo(2);

            assertThat(repository.deleteByShareTargets(RecentDeleteShareTargetsQuery.builder().sharedByUserSeq(ADMIN).keys(List.of()).build())).isZero();
            assertThat(repository.deleteByShareTargets(RecentDeleteShareTargetsQuery.builder().sharedByUserSeq(ADMIN).keys(List.of(new PartitionKey(t1.getUserSeq(), a.getResourceSeq()), new PartitionKey(t2.getUserSeq(), c.getResourceSeq()))).build())).as("(t1,a) 만 존재").isEqualTo(1);

            open(t1.getUserSeq(), a, D1);
            open(t1.getUserSeq(), c, D1);
            repository.deleteByUserResources(List.of(
                    RecentDeleteUserResourcesQuery.builder().userSeq(t1.getUserSeq()).sharedByUserSeq(ADMIN).resourceSeqList(List.of(a.getResourceSeq(), c.getResourceSeq())).build(),
                    RecentDeleteUserResourcesQuery.builder().userSeq(t2.getUserSeq()).sharedByUserSeq(ADMIN).resourceSeqList(List.of()).build()));
            repository.deleteByUserResources(List.of());
            assertThat(count(ADMIN)).as("b:t2 만").isEqualTo(1);

            assertThat(repository.deleteByResource(RecentDeleteResourceQuery.builder().sharedByUserSeq(ADMIN).resourceSeq(b.getResourceSeq()).build())).isEqualTo(1);
            open(t1.getUserSeq(), a, D1);
            assertThat(repository.deleteByResources(RecentDeleteResourcesQuery.builder().sharedByUserSeq(ADMIN).resourceSeqList(List.of()).build())).isZero();
            assertThat(repository.deleteByResources(RecentDeleteResourcesQuery.builder().sharedByUserSeq(ADMIN).resourceSeqList(List.of(a.getResourceSeq(), 99999L)).build())).isEqualTo(1);
            assertThat(count(ADMIN)).isZero();
        }

        @Test
        @DisplayName("deleteByUserResources 배치(B2) — 같은 길이(t1·t2: a,b)·다른 길이(t3: c)·빈 목록 섞기, 목록 밖 리소스·다른 공유자 행은 남음")
        void deleteByUserResources_batch() {
            User t3 = user("rec-t3");
            for (User t : List.of(t1, t2, t3)) {
                open(t.getUserSeq(), a, D1);
                open(t.getUserSeq(), b, D1);
                open(t.getUserSeq(), c, D1);
            }
            repository.save(Recent.builder().userSeq(t1.getUserSeq()).resourceSeq(a.getResourceSeq()).sharedByUserSeq(SUPER_ADMIN).updateDate(D1).build());

            repository.deleteByUserResources(List.of(
                    RecentDeleteUserResourcesQuery.builder().userSeq(t1.getUserSeq()).sharedByUserSeq(ADMIN).resourceSeqList(List.of(a.getResourceSeq(), b.getResourceSeq())).build(),
                    RecentDeleteUserResourcesQuery.builder().userSeq(t3.getUserSeq()).sharedByUserSeq(ADMIN).resourceSeqList(List.of(c.getResourceSeq())).build(),
                    RecentDeleteUserResourcesQuery.builder().userSeq(t2.getUserSeq()).sharedByUserSeq(ADMIN).resourceSeqList(List.of(a.getResourceSeq(), b.getResourceSeq())).build(),
                    RecentDeleteUserResourcesQuery.builder().userSeq(t2.getUserSeq()).sharedByUserSeq(ADMIN).resourceSeqList(List.of()).build()));

            assertThat(jdbc.queryForList("SELECT user_seq || ':' || resource_seq FROM tb_recent WHERE shared_by_user_seq = ?", String.class, ADMIN))
                    .as("t1:c, t2:c, t3:a, t3:b 만 남음")
                    .containsExactlyInAnyOrder(t1.getUserSeq() + ":" + c.getResourceSeq(), t2.getUserSeq() + ":" + c.getResourceSeq(),
                            t3.getUserSeq() + ":" + a.getResourceSeq(), t3.getUserSeq() + ":" + b.getResourceSeq());
            assertThat(count(SUPER_ADMIN)).as("다른 공유자 행은 그대로").isEqualTo(1);
        }

        @Test
        @DisplayName("deleteByUserSeqList · deleteBySharedByUserSeqList · 테넌트 3종(자기 제외·공유 유형·외부 접근자) · deleteOtherTenantByResources")
        void byUsersAndTenant() {
            shareRepository.save(Share.builder().userSeq(ADMIN).resourceSeq(a.getResourceSeq()).shareType(ShareType.ORG_LINK).isPasswordEnabled(false).build());
            shareRepository.save(Share.builder().userSeq(ADMIN).resourceSeq(b.getResourceSeq()).shareType(ShareType.SPECIFIC_USERS).isPasswordEnabled(false).build());
            open(ADMIN, a, D1);
            open(t1.getUserSeq(), a, D1);
            open(t2.getUserSeq(), a, D1);
            open(SUPER_ADMIN, a, D1);
            open(t1.getUserSeq(), b, D1);
            open(SUPER_ADMIN, c, D1);
            assertThat(count(ADMIN)).isEqualTo(6);

            assertThat(repository.deleteOtherTenantByResources(RecentDeleteResourcesQuery.builder().sharedByUserSeq(ADMIN).resourceSeqList(List.of()).tenantSeq(1).build())).isZero();
            assertThat(repository.deleteOtherTenantByResources(RecentDeleteResourcesQuery.builder().sharedByUserSeq(ADMIN).resourceSeqList(List.of(c.getResourceSeq())).tenantSeq(1).excludeUserSeqList(List.of(SUPER_ADMIN)).build())).as("제외 목록").isZero();
            assertThat(repository.deleteOtherTenantByResources(RecentDeleteResourcesQuery.builder().sharedByUserSeq(ADMIN).resourceSeqList(List.of(c.getResourceSeq())).tenantSeq(1).build())).as("c 의 superadmin(테넌트 밖)").isEqualTo(1);
            assertThat(repository.deleteOtherTenantSharedByTenant(1)).as("a 의 superadmin").isEqualTo(1);
            assertThat(repository.deleteSharedByTenantAndShareTypes(1, List.of())).isZero();
            assertThat(repository.deleteSharedByTenantAndShareTypes(1, List.of("ORG_LINK"))).as("a(ORG_LINK) 의 t1·t2, 자기 자신 제외").isEqualTo(2);
            assertThat(count(ADMIN)).as("a:ADMIN, b:t1").isEqualTo(2);
            assertThat(repository.deleteAllSharedByTenant(1)).as("자기 자신 제외 → b 의 t1").isEqualTo(1);

            open(t2.getUserSeq(), b, D1);
            assertThat(repository.deleteByUserSeqList(List.of())).isZero();
            assertThat(repository.deleteByUserSeqList(List.of(t2.getUserSeq()))).isEqualTo(1);
            assertThat(repository.deleteBySharedByUserSeqList(List.of())).isZero();
            assertThat(repository.deleteBySharedByUserSeqList(List.of(ADMIN))).as("남은 a:ADMIN").isEqualTo(1);
            assertThat(count(ADMIN)).isZero();
        }
    }
}
