package com.thinkfree.drive.starred.repository;

import com.thinkfree.drive.global.time.DriveTime;
import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.common.JdbcStatementCounter;
import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.common.search.enums.FileType;
import com.thinkfree.drive.common.search.enums.SortField;
import com.thinkfree.drive.common.search.enums.SortOrder;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.partition.PartitionKey;
import com.thinkfree.drive.resource.entity.Resource;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.repository.ResourceRepository;
import com.thinkfree.drive.share.entity.Share;
import com.thinkfree.drive.share.enums.ShareType;
import com.thinkfree.drive.share.repository.ShareRepository;
import com.thinkfree.drive.starred.data.StarredData;
import com.thinkfree.drive.starred.entity.Starred;
import com.thinkfree.drive.starred.query.*;
import com.thinkfree.drive.starred.dto.StarredSaveDto;
import com.thinkfree.drive.starred.exception.StarredErrorCode;
import com.thinkfree.drive.starred.service.StarredService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.SignupType;
import com.thinkfree.drive.user.enums.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * 2단계 Starred 이관 회귀 — 읽기({@link DefaultStarredQueryRepository} findAll)·쓰기({@link DefaultStarredRepository}: INSERT 는 JPA saveAndFlush, DELETE 13종 중 12종은 JPQL {@code @Modifying}·튜플 IN 1종은 JdbcClient).
 * 시드 = tenant 1, user 1 superadmin(테넌트 없음)·2 admin(tenant 1)·3 anonymous. 리소스는 {@link ResourceRepository}, 공유는 {@link ShareRepository}, 수신자는 insertUserWithSeatCheck. 트랜잭션 롤백.
 * 검증 포인트 = JPA INSERT(identity 키·리스너 감사·uniq_starred 위반 → DataIntegrityViolationException), 목록(기본 NAME ASC·부모 폴더 조인·필터·공유자 가시성·휴지통)·커서 3종(기본 NAME/SIZE DESC/UPDATE), DELETE 13종(선택 조건·튜플 IN·항목별 루프·테넌트 3종)·빈 목록 가드.
 */
@DisplayName("DefaultStarredRepository / DefaultStarredQueryRepository — JPA insert + JdbcClient(PostgreSQL Testcontainers)")
class DefaultStarredRepositoryTest extends RepositoryTest {

    private static final int SUPER_ADMIN = 1;
    private static final int ADMIN = 2;

    @Autowired
    private StarredRepository repository;
    @Autowired
    private StarredService starredService;
    @Autowired
    private StarredQueryRepository queryRepository;
    @Autowired
    private ResourceRepository resourceRepository;
    @Autowired
    private ShareRepository shareRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private StarredJpaRepository starredJpaRepository;

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
                .copyNumber(0).ext(ext).contentType("text/plain").sizeByte(size).versionRegisterDate(DriveTime.toInstant(LocalDateTime.of(2026, 1, 1, 0, 0, 0))).registerUserSeq(ADMIN).build();
        resourceRepository.save(r);
        r.initResourceSaveName();
        resourceRepository.flush();
        return r;
    }

    /** 별표: userSeq 가 sharedBy(소유자 ADMIN)의 리소스에 등록. */
    private Starred star(int userSeq, Resource resource) {
        Starred s = Starred.builder().userSeq(userSeq).resourceSeq(resource.getResourceSeq()).sharedByUserSeq(ADMIN).build();
        repository.save(s);
        return s;
    }

    private int count(int sharedBy) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tb_starred WHERE shared_by_user_seq = ?", Integer.class, sharedBy);
    }

    private static StarredListQuery.StarredListQueryBuilder list(int userSeq, int pageSize) {
        return StarredListQuery.builder().userSeq(userSeq).isDelete(false).page(CursorPaginationQuery.of(null, null, null, pageSize));
    }

    private static CursorPaginationQuery cursor(StarredData last, Object value, int pageSize) {
        return CursorPaginationQuery.of(value, last.getResourceSeq(), last.getResourceType(), pageSize);
    }

    // ================================================================== JPA INSERT · 목록

    @Nested
    @DisplayName("JPA INSERT · findAll")
    class InsertAndFind {

        @Test
        @DisplayName("save — identity 키, 리스너 감사(비면 컨텍스트), uniq_starred 위반은 DataIntegrityViolationException; findAll 기본 NAME ASC·부모 폴더 조인·매핑")
        void saveAndFindAll() {
            Resource alpha = folder("alpha", null);
            Resource beta = file("beta.txt", alpha, 10L, "txt");
            Resource gamma = file("gamma.pdf", null, 30L, "pdf");
            Starred s = star(ADMIN, beta);
            assertThat(s.getStarredSeq()).isPositive();
            assertThat(s.getRegisterUserSeq()).as("JpaAuditListener → 컨텍스트 ADMIN").isEqualTo(ADMIN);
            assertThat(s.getRegisterDate()).isNotNull();
            star(ADMIN, alpha);
            star(ADMIN, gamma);

            List<StarredData> rows = queryRepository.findAll(list(ADMIN, 10).build());
            assertThat(rows).extracting(StarredData::getResourceName).as("폴더 먼저, 이름 자연정렬").containsExactly("alpha", "beta.txt", "gamma.pdf");
            StarredData b = rows.get(1);
            assertThat(b.getStarredSeq()).isEqualTo(s.getStarredSeq());
            assertThat(b.getUserSeq()).isEqualTo(ADMIN);
            assertThat(b.getSharedByUserSeq()).isEqualTo(ADMIN);
            assertThat(b.getOwnerId()).isEqualTo("admin");
            assertThat(b.getOwnerStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(b.getOwnerTenantName()).isEqualTo("기본 테넌트");
            assertThat(b.getResourceType()).isEqualTo(ResourceType.FILE);
            assertThat(b.getParentFolderSeq()).isEqualTo(alpha.getResourceSeq());
            assertThat(b.getParentFolderName()).isEqualTo("alpha");
            assertThat(b.getFullPathSeq()).as("부모 폴더의 경로").isEqualTo(alpha.getFullPathSeq());
            assertThat(b.getFullPath()).isEqualTo("alpha/");
            assertThat(b.getSizeByte()).isEqualTo(10L);
            assertThat(b.getIsLocked()).isFalse();
            assertThat(b.getResourceRegisterUserId()).isEqualTo("admin");
            assertThat(b.getResourceRegisterDate()).isNotNull();
            assertThat(b.getResourceUpdateUserId()).as("updateResourceSaveName 감사").isEqualTo("admin");
            assertThat(b.getStarredRegisterDate()).isNotNull();
            assertThat(rows.get(2).getParentFolderName()).as("루트 파일은 부모 없음").isNull();

            // 유니크 위반은 PG 트랜잭션을 abort 시키므로 맨 끝에서 확인(이후 문장이 없어야 한다)
            assertThatThrownBy(() -> star(ADMIN, beta)).as("uniq_starred(user, resource, shared_by) → Spring 번역 DataIntegrityViolationException(하위 DuplicateKeyException 포함)").isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("필터(타입/확장자/소유자 아이디/날짜)·휴지통 기준·공유자 DELETED 숨김·수신자별 분리")
        void filters() {
            Resource alpha = folder("alpha", null);
            Resource beta = file("beta.txt", null, 10L, "txt");
            Resource gamma = file("gamma.pdf", null, 30L, "pdf");
            User t1 = user("star-t1");
            star(ADMIN, alpha);
            star(ADMIN, beta);
            star(ADMIN, gamma);
            star(t1.getUserSeq(), beta);

            assertThat(queryRepository.findAll(list(t1.getUserSeq(), 10).build())).extracting(StarredData::getResourceName).containsExactly("beta.txt");
            assertThat(queryRepository.findAll(list(ADMIN, 10).resourceType(ResourceType.FOLDER).build())).extracting(StarredData::getResourceName).containsExactly("alpha");
            assertThat(queryRepository.findAll(list(ADMIN, 10).fileType(FileType.PDF).build())).extracting(StarredData::getResourceName).containsExactly("gamma.pdf");
            assertThat(queryRepository.findAll(list(ADMIN, 10).userId("admin").build())).hasSize(3);
            assertThat(queryRepository.findAll(list(ADMIN, 10).userId("nobody").build())).isEmpty();
            assertThat(queryRepository.findAll(list(ADMIN, 10).from(DriveTime.startOfDay(LocalDate.now(DriveTime.zone()))).to(DriveTime.endExclusive(LocalDate.now(DriveTime.zone()))).build())).as("리소스 수정일 = 오늘").hasSize(3);
            assertThat(queryRepository.findAll(list(ADMIN, 10).from(DriveTime.startOfDay(LocalDate.of(2000, 1, 1))).to(DriveTime.endExclusive(LocalDate.of(2000, 1, 2))).build())).isEmpty();
            assertThat(queryRepository.findAll(StarredListQuery.builder().userSeq(ADMIN).isDelete(true).page(CursorPaginationQuery.of(null, null, null, 10)).build())).as("휴지통 기준").isEmpty();

            jdbc.update("UPDATE tb_user SET status = 'DELETED' WHERE user_seq = ?", ADMIN);
            assertThat(queryRepository.findAll(list(t1.getUserSeq(), 10).build())).as("공유자 DELETED → 숨김").isEmpty();
        }

        @Test
        @DisplayName("커서 — 기본(NAME ASC) ps=2 왕복, SIZE DESC(폴더 먼저), UPDATE ASC")
        void cursors() {
            Resource alpha = folder("alpha", null);
            Resource beta = file("beta.txt", null, 10L, "txt");
            Resource gamma = file("gamma.pdf", null, 30L, "pdf");
            star(ADMIN, alpha);
            star(ADMIN, beta);
            star(ADMIN, gamma);
            jdbc.update("UPDATE tb_resource SET update_date = ? WHERE resource_seq = ?", java.sql.Timestamp.valueOf(LocalDateTime.of(2026, 3, 1, 0, 0, 0)), gamma.getResourceSeq());
            jdbc.update("UPDATE tb_resource SET update_date = ? WHERE resource_seq = ?", java.sql.Timestamp.valueOf(LocalDateTime.of(2026, 2, 1, 0, 0, 0)), beta.getResourceSeq());

            List<StarredData> p1 = queryRepository.findAll(list(ADMIN, 2).build());
            assertThat(p1).extracting(StarredData::getResourceName).containsExactly("alpha", "beta.txt");
            StarredData last = p1.get(1);
            List<StarredData> p2 = queryRepository.findAll(StarredListQuery.builder().userSeq(ADMIN).isDelete(false).page(cursor(last, last.getResourceName(), 2)).build());
            assertThat(p2).extracting(StarredData::getResourceName).containsExactly("gamma.pdf");

            List<StarredData> size1 = queryRepository.findAll(list(ADMIN, 2).sortBy(SortField.SIZE).sortOrder(SortOrder.DESC).build());
            assertThat(size1).extracting(StarredData::getResourceName).containsExactly("alpha", "gamma.pdf");
            StarredData sl = size1.get(1);
            List<StarredData> size2 = queryRepository.findAll(StarredListQuery.builder().userSeq(ADMIN).isDelete(false).sortBy(SortField.SIZE).sortOrder(SortOrder.DESC).page(cursor(sl, sl.getSizeByte(), 2)).build());
            assertThat(size2).extracting(StarredData::getResourceName).containsExactly("beta.txt");

            List<StarredData> up1 = queryRepository.findAll(list(ADMIN, 2).sortBy(SortField.UPDATE).sortOrder(SortOrder.ASC).build());
            assertThat(up1).extracting(StarredData::getResourceName).containsExactly("alpha", "beta.txt");
            StarredData ul = up1.get(1);
            List<StarredData> up2 = queryRepository.findAll(StarredListQuery.builder().userSeq(ADMIN).isDelete(false).sortBy(SortField.UPDATE).sortOrder(SortOrder.ASC).page(cursor(ul, ul.getResourceUpdateDate(), 2)).build());
            assertThat(up2).extracting(StarredData::getResourceName).containsExactly("gamma.pdf");
        }
    }

    // ================================================================== DELETE

    @Nested
    @DisplayName("DELETE 13종")
    class Deletes {

        private Resource a, b, c;
        private User t1, t2;

        @BeforeEach
        void fixture() {
            a = file("a.txt", null, 1L, "txt");
            b = file("b.txt", null, 1L, "txt");
            c = file("c.txt", null, 1L, "txt");
            t1 = user("star-t1");
            t2 = user("star-t2");
        }

        @Test
        @DisplayName("delete · deleteByResource(s) · ExcludingOwner(선택 제외 목록) · deleteByShareTargets(튜플 IN) · deleteByUserResources(배치) · 빈 목록 가드")
        void byResourceAndKeys() {
            Starred own = star(ADMIN, a);
            star(t1.getUserSeq(), a);
            star(t2.getUserSeq(), a);
            star(t1.getUserSeq(), b);
            star(t2.getUserSeq(), b);
            star(t1.getUserSeq(), c);
            assertThat(count(ADMIN)).isEqualTo(6);

            assertThat(repository.delete(StarredDeleteQuery.builder().starredSeq(own.getStarredSeq()).userSeq(SUPER_ADMIN).build())).as("다른 사용자").isZero();
            assertThat(repository.delete(StarredDeleteQuery.builder().starredSeq(own.getStarredSeq()).userSeq(ADMIN).build())).isEqualTo(1);

            assertThat(repository.deleteByResourceExcludingOwner(StarredDeleteResourceQuery.builder().userSeq(t1.getUserSeq()).sharedByUserSeq(ADMIN).resourceSeq(a.getResourceSeq()).excludeUserSeqList(List.of(t2.getUserSeq())).build()))
                    .as("t1(소유자 취급)·t2(제외) 둘 다 남음").isZero();
            assertThat(repository.deleteByResourceExcludingOwner(StarredDeleteResourceQuery.builder().userSeq(t1.getUserSeq()).sharedByUserSeq(ADMIN).resourceSeq(a.getResourceSeq()).build())).as("t2 만").isEqualTo(1);
            assertThat(repository.deleteByResourcesExcludingOwner(StarredDeleteResourcesQuery.builder().userSeq(t2.getUserSeq()).sharedByUserSeq(ADMIN).resourceSeqList(List.of()).build())).isZero();
            assertThat(repository.deleteByResourcesExcludingOwner(StarredDeleteResourcesQuery.builder().userSeq(t2.getUserSeq()).sharedByUserSeq(ADMIN).resourceSeqList(List.of(b.getResourceSeq(), c.getResourceSeq())).excludeUserSeqList(List.of(t1.getUserSeq())).build())).isZero();
            assertThat(repository.deleteByResourcesExcludingOwner(StarredDeleteResourcesQuery.builder().userSeq(t2.getUserSeq()).sharedByUserSeq(ADMIN).resourceSeqList(List.of(b.getResourceSeq(), c.getResourceSeq())).build())).as("b·c 의 t1").isEqualTo(2);
            assertThat(count(ADMIN)).as("a:t1, b:t2").isEqualTo(2);

            assertThat(repository.deleteByShareTargets(StarredDeleteShareTargetsQuery.builder().sharedByUserSeq(ADMIN).keys(List.of()).build())).isZero();
            assertThat(repository.deleteByShareTargets(StarredDeleteShareTargetsQuery.builder().sharedByUserSeq(ADMIN).keys(List.of(new PartitionKey(t1.getUserSeq(), a.getResourceSeq()), new PartitionKey(t2.getUserSeq(), c.getResourceSeq()))).build())).as("(t1,a) 만 존재").isEqualTo(1);

            star(t1.getUserSeq(), a);
            star(t1.getUserSeq(), c);
            repository.deleteByUserResources(List.of(
                    StarredDeleteUserResourcesQuery.builder().userSeq(t1.getUserSeq()).sharedByUserSeq(ADMIN).resourceSeqList(List.of(a.getResourceSeq(), c.getResourceSeq())).build(),
                    StarredDeleteUserResourcesQuery.builder().userSeq(t2.getUserSeq()).sharedByUserSeq(ADMIN).resourceSeqList(List.of()).build()));
            assertThat(count(ADMIN)).as("b:t2 만").isEqualTo(1);
            repository.deleteByUserResources(List.of());

            assertThat(repository.deleteByResource(StarredDeleteResourceQuery.builder().sharedByUserSeq(ADMIN).resourceSeq(b.getResourceSeq()).build())).isEqualTo(1);
            star(t1.getUserSeq(), a);
            assertThat(repository.deleteByResources(StarredDeleteResourcesQuery.builder().sharedByUserSeq(ADMIN).resourceSeqList(List.of()).build())).isZero();
            assertThat(repository.deleteByResources(StarredDeleteResourcesQuery.builder().sharedByUserSeq(ADMIN).resourceSeqList(List.of(a.getResourceSeq(), 99999L)).build())).isEqualTo(1);
            assertThat(count(ADMIN)).isZero();
        }

        @Test
        @DisplayName("deleteByUserResources 행값 IN — 같은 길이(t1·t2: a,b)·다른 길이(t3: c)·빈 목록 섞기, 목록 밖 리소스·다른 공유자 행은 남음, 실측 한 문장 1회")
        void deleteByUserResources_batch() {
            User t3 = user("star-t3");
            for (User t : List.of(t1, t2, t3)) {
                star(t.getUserSeq(), a);
                star(t.getUserSeq(), b);
                star(t.getUserSeq(), c);
            }
            repository.save(Starred.builder().userSeq(t1.getUserSeq()).resourceSeq(a.getResourceSeq()).sharedByUserSeq(SUPER_ADMIN).build());

            JdbcStatementCounter counter = new JdbcStatementCounter(dataSource);
            StarredRepository counted = new DefaultStarredRepository(starredJpaRepository, DSL.using(counter, SQLDialect.POSTGRES));
            counted.deleteByUserResources(List.of(
                    StarredDeleteUserResourcesQuery.builder().userSeq(t1.getUserSeq()).sharedByUserSeq(ADMIN).resourceSeqList(List.of(a.getResourceSeq(), b.getResourceSeq())).build(),
                    StarredDeleteUserResourcesQuery.builder().userSeq(t3.getUserSeq()).sharedByUserSeq(ADMIN).resourceSeqList(List.of(c.getResourceSeq())).build(),
                    StarredDeleteUserResourcesQuery.builder().userSeq(t2.getUserSeq()).sharedByUserSeq(ADMIN).resourceSeqList(List.of(a.getResourceSeq(), b.getResourceSeq())).build(),
                    StarredDeleteUserResourcesQuery.builder().userSeq(t2.getUserSeq()).sharedByUserSeq(ADMIN).resourceSeqList(List.of()).build()));

            assertThat(counter.prepareStatementCount()).as("IN 길이가 달라도 (user, sharedBy, resource) 5행을 한 문장으로").isEqualTo(1);
            assertThat(counter.executeBatchCount()).isZero();
            assertThat(jdbc.queryForList("SELECT user_seq || ':' || resource_seq FROM tb_starred WHERE shared_by_user_seq = ? ORDER BY 1", String.class, ADMIN))
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
            star(ADMIN, a);
            star(t1.getUserSeq(), a);
            star(t2.getUserSeq(), a);
            star(SUPER_ADMIN, a);
            star(t1.getUserSeq(), b);
            star(SUPER_ADMIN, c);
            assertThat(count(ADMIN)).isEqualTo(6);

            assertThat(repository.deleteOtherTenantByResources(StarredDeleteResourcesQuery.builder().sharedByUserSeq(ADMIN).resourceSeqList(List.of()).tenantSeq(1).build())).isZero();
            assertThat(repository.deleteOtherTenantByResources(StarredDeleteResourcesQuery.builder().sharedByUserSeq(ADMIN).resourceSeqList(List.of(c.getResourceSeq())).tenantSeq(1).excludeUserSeqList(List.of(SUPER_ADMIN)).build())).as("제외 목록").isZero();
            assertThat(repository.deleteOtherTenantByResources(StarredDeleteResourcesQuery.builder().sharedByUserSeq(ADMIN).resourceSeqList(List.of(c.getResourceSeq())).tenantSeq(1).build())).as("c 의 superadmin(테넌트 밖)").isEqualTo(1);
            assertThat(repository.deleteOtherTenantSharedByTenant(1)).as("a 의 superadmin").isEqualTo(1);
            assertThat(repository.deleteSharedByTenantAndShareTypes(1, List.of())).isZero();
            assertThat(repository.deleteSharedByTenantAndShareTypes(1, List.of("ORG_LINK"))).as("a(ORG_LINK) 의 t1·t2, 자기 자신 제외").isEqualTo(2);
            assertThat(count(ADMIN)).as("a:ADMIN, b:t1").isEqualTo(2);
            assertThat(repository.deleteAllSharedByTenant(1)).as("자기 자신 제외 → b 의 t1").isEqualTo(1);

            star(t2.getUserSeq(), b);
            assertThat(repository.deleteByUserSeqList(List.of())).isZero();
            assertThat(repository.deleteByUserSeqList(List.of(t2.getUserSeq()))).isEqualTo(1);
            assertThat(repository.deleteBySharedByUserSeqList(List.of())).isZero();
            assertThat(repository.deleteBySharedByUserSeqList(List.of(ADMIN))).as("남은 a:ADMIN").isEqualTo(1);
            assertThat(count(ADMIN)).isZero();
        }
    }

    @Test
    @DisplayName("서비스 save — IDENTITY 라 save 순간 INSERT, 같은 (user, resource, shared_by) 재등록은 save 호출 안의 위반을 서비스 try 가 잡아 CONFLICT(saveAndFlush 불필요 — W3 10-13)")
    void serviceSave_duplicate_conflict() {
        Resource f = file("dup.txt", null, 1L, "txt");
        StarredSaveDto dto = StarredSaveDto.builder().userSeq(ADMIN).resourceSeq(f.getResourceSeq()).sharedByUserSeq(ADMIN).build();
        long seq = starredService.save(dto);
        assertThat(seq).as("save 순간 INSERT 로 키 채움").isPositive();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_starred WHERE starred_seq = ?", Integer.class, seq)).isEqualTo(1);

        // 유니크 위반 → PG 트랜잭션 abort 라 맨 끝
        assertThatThrownBy(() -> starredService.save(dto))
                .isInstanceOf(ApiRuntimeException.class)
                .satisfies(ex -> assertThat(((ApiRuntimeException) ex).getCode()).isEqualTo(StarredErrorCode.CONFLICT));
    }
}
