package com.thinkfree.drive.trash.repository;

import com.thinkfree.drive.global.time.DriveTime;
import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.common.search.enums.FileType;
import com.thinkfree.drive.common.search.enums.SortOrder;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.resource.entity.Resource;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.repository.ResourceRepository;
import com.thinkfree.drive.trash.data.TrashData;
import com.thinkfree.drive.trash.entity.Trash;
import com.thinkfree.drive.trash.enums.TrashSortField;
import com.thinkfree.drive.trash.query.TrashDeleteQuery;
import com.thinkfree.drive.trash.query.TrashDeleteResourcesQuery;
import com.thinkfree.drive.trash.query.TrashListQuery;
import com.thinkfree.drive.user.enums.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.BDDMockito.given;

/**
 * 2단계 Trash 이관 회귀(§1 v2 "JPA 우선") — 쓰기({@link DefaultTrashRepository}: saveAndFlush·JPQL 삭제 3종(파티션 user_seq 조건))·읽기({@link DefaultTrashQueryRepository}: 7테이블 조인 DTO 목록, 정렬 3종 × 커서, 필터 4종).
 * 시드 = tenant 1("기본 테넌트"), user 2 admin(tenant 1, ACTIVE). 소유자·삭제자 테넌트가 INNER JOIN 이라 리소스 소유자·삭제자는 admin 으로 둔다. 리소스는 {@link ResourceRepository} 로 폴더 1 + 파일 3(이름 1-report/2-report/10-report(앞 숫자 자연정렬) · 크기 5/50/500). 트랜잭션 롤백.
 * 검증 포인트 = identity 키·리스너 register_*(LocalDateTime 컨버터), 조인 32컬럼 매핑, NAME 자연정렬(숫자 접두 1·2·10, 숫자 없는 폴더는 ASC 에서 마지막)·SIZE·기본(삭제일 DESC, seq ASC tie-break) + 각 커서, 타입/날짜/확장자/삭제자 필터, 삭제 3종(타 사용자 0·빈 목록 0), 유니크(user_seq, resource_seq) 위반 맨 끝.
 */
@DisplayName("DefaultTrashRepository / DefaultTrashQueryRepository — JPA + JdbcClient 목록(PostgreSQL Testcontainers)")
class DefaultTrashRepositoryTest extends RepositoryTest {

    private static final int ADMIN = 2;
    private static final int OTHER = 910001;

    @Autowired
    private TrashRepository repository;
    @Autowired
    private TrashQueryRepository queryRepository;
    @Autowired
    private ResourceRepository resourceRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
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

    private Resource folder(String name) {
        Resource r = Resource.builder().userSeq(ADMIN).ownerSeq(ADMIN).resourceType(ResourceType.FOLDER).parentFolderSeq(null)
                .resourceName(name).originalFileName(name).fullPathSeq("/").fullPath("/" + name).copyNumber(0).registerUserSeq(ADMIN).build();
        resourceRepository.save(r);
        return r;
    }

    private Resource file(String name, String ext, long size, Long parentSeq) {
        Resource r = Resource.builder().userSeq(ADMIN).ownerSeq(ADMIN).latestOwnerSeq(ADMIN).resourceType(ResourceType.FILE).parentFolderSeq(parentSeq)
                .resourceName(name).originalFileName(name).fullPathSeq("/").fullPath("/" + name).copyNumber(0)
                .ext(ext).contentType("application/octet-stream").sizeByte(size).versionRegisterDate(Instant.now()).registerUserSeq(ADMIN).build();
        resourceRepository.save(r);
        return r;
    }

    private Trash trash(int userSeq, long resourceSeq) {
        Trash t = Trash.builder().userSeq(userSeq).resourceSeq(resourceSeq).build();
        assertThat(repository.save(t)).isEqualTo(1);
        return t;
    }

    private List<TrashData> findAll(TrashListQuery.TrashListQueryBuilder b, Object cursorValue, Long cursorId, int pageSize) {
        return queryRepository.findAll(b.userSeq(ADMIN).page(CursorPaginationQuery.of(cursorValue, cursorId, null, pageSize)).build());
    }

    /** 삭제일을 고정 — JPA Auditing 은 등록일을 현재 시각(초 절삭)으로 채워 같은 테스트 안의 행들은 삭제일이 같아지므로, 정렬 간격은 JDBC 로 준다. */
    private Trash trashAt(int userSeq, long resourceSeq, Instant registerDate) {
        Trash t = Trash.builder().userSeq(userSeq).resourceSeq(resourceSeq).build();
        assertThat(repository.save(t)).isEqualTo(1);
        jdbc.update("UPDATE tb_trash SET register_date = ? WHERE trash_seq = ?", java.time.OffsetDateTime.ofInstant(registerDate, java.time.ZoneOffset.UTC), t.getTrashSeq());
        return t;
    }

    private static List<String> names(List<TrashData> rows) {
        return rows.stream().map(TrashData::getResourceName).toList();
    }

    @Test
    @DisplayName("save(identity·리스너 register_* LocalDateTime) → findAll 매핑(소유자/삭제자 테넌트·부모 폴더·등록자) · 정렬 NAME 자연정렬/SIZE/기본(삭제일 DESC, seq ASC) × 커서 · 필터(타입·삭제일 범위·확장자·삭제자 아이디) · 유니크 위반 맨 끝")
    void saveAndList() {
        Instant before = Instant.now().minusSeconds(1);
        Resource dir = folder("docs");
        Resource f1 = file("1-report.txt", "txt", 5L, dir.getResourceSeq());
        Resource f2 = file("2-report.xlsx", "xlsx", 50L, null);
        Resource f10 = file("10-report.txt", "txt", 500L, null);

        Trash t1 = trashAt(ADMIN, f1.getResourceSeq(), before.minus(3, ChronoUnit.MINUTES));
        assertThat(t1.getTrashSeq()).as("identity 키").isNotNull().isPositive();
        assertThat(t1.getRegisterUserSeq()).as("JPA Auditing — 시큐리티 사용자").isEqualTo(ADMIN);
        assertThat(t1.getRegisterDate()).as("JPA Auditing — 현재 시각(초 절삭)").isAfterOrEqualTo(before.truncatedTo(ChronoUnit.SECONDS));
        Trash t2 = trashAt(ADMIN, f2.getResourceSeq(), before.minus(2, ChronoUnit.MINUTES));
        Trash t10 = trashAt(ADMIN, f10.getResourceSeq(), before.minus(1, ChronoUnit.MINUTES));
        Trash tDir = trash(ADMIN, dir.getResourceSeq());
        assertThat(tDir.getRegisterDate()).as("JPA Auditing — 현재 시각(초 절삭)").isNotNull().isAfter(before.minusSeconds(2));
        assertThat(t2.getTrashSeq()).isGreaterThan(t1.getTrashSeq());
        assertThat(jdbc.queryForObject("SELECT CAST(register_date AS VARCHAR) FROM tb_trash WHERE trash_seq = ?", String.class, t1.getTrashSeq())).isNotBlank();

        // 기본 정렬 = 삭제일 DESC, tie-break a.resource_seq(방향 생략 = ASC) → 나중 삭제 순: dir, file10, file2, file1
        List<TrashData> all = findAll(TrashListQuery.builder(), null, null, 10);
        assertThat(names(all)).containsExactly("docs", "10-report.txt", "2-report.xlsx", "1-report.txt");
        TrashData d1 = all.get(3);
        assertThat(d1.getTrashSeq()).isEqualTo(t1.getTrashSeq());
        assertThat(d1.getResourceSeq()).isEqualTo(f1.getResourceSeq());
        assertThat(d1.getUserSeq()).isEqualTo(ADMIN);
        assertThat(d1.getOwnerSeq()).isEqualTo(ADMIN);
        assertThat(d1.getOwnerId()).isEqualTo("admin");
        assertThat(d1.getOwnerName()).isNotBlank();
        assertThat(d1.getOwnerStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(d1.getOwnerTenantSeq()).isEqualTo(1);
        assertThat(d1.getOwnerTenantName()).isEqualTo("기본 테넌트");
        assertThat(d1.getResourceType()).isEqualTo(ResourceType.FILE);
        assertThat(d1.getParentFolderSeq()).isEqualTo(dir.getResourceSeq());
        assertThat(d1.getParentFolderName()).as("부모 폴더 LEFT JOIN").isEqualTo("docs");
        assertThat(d1.getFullPath()).as("부모 폴더의 full_path").isEqualTo("/docs");
        assertThat(d1.getSizeByte()).isEqualTo(5L);
        assertThat(d1.getIsLocked()).isNotNull();
        assertThat(d1.getRegisterUserId()).isEqualTo("admin");
        assertThat(d1.getRegisterUserStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(d1.getRegisterDate()).as("리소스 등록일").isNotNull();
        assertThat(d1.getUpdateUserId()).as("수정자 LEFT JOIN").isNull();
        assertThat(d1.getDeleteUserSeq()).isEqualTo(ADMIN);
        assertThat(d1.getDeleteUserId()).isEqualTo("admin");
        assertThat(d1.getDeleteUserTenantSeq()).isEqualTo(1);
        assertThat(d1.getDeleteUserTenantName()).isEqualTo("기본 테넌트");
        assertThat(d1.getDeleteUserStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(d1.getDeleteDate()).as("삭제일 = tb_trash.register_date(µs 반올림)").isCloseTo(before.minus(3, ChronoUnit.MINUTES), within(1, ChronoUnit.MICROS));
        assertThat(all.get(0).getParentFolderName()).as("루트 폴더").isNull();
        assertThat(all.get(0).getSizeByte()).as("폴더 size NULL").isNull();

        // 기본 정렬 커서(삭제일 DESC) — cursorValue = 삭제일 문자열, cursorId = resource_seq
        List<TrashData> page1 = findAll(TrashListQuery.builder(), null, null, 2);
        assertThat(names(page1)).containsExactly("docs", "10-report.txt");
        TrashData last = page1.get(1);
        List<TrashData> page2 = findAll(TrashListQuery.builder(), last.getDeleteDate().toString(), last.getResourceSeq(), 2);
        assertThat(names(page2)).containsExactly("2-report.xlsx", "1-report.txt");
        assertThat(findAll(TrashListQuery.builder(), page2.get(1).getDeleteDate().toString(), page2.get(1).getResourceSeq(), 2)).isEmpty();

        // NAME ASC 자연정렬: docs, file1, file2, file10 — 커서는 이름 튜플
        List<TrashData> nameAsc = findAll(TrashListQuery.builder().sortBy(TrashSortField.NAME).sortOrder(SortOrder.ASC), null, null, 10);
        assertThat(names(nameAsc)).containsExactly("1-report.txt", "2-report.xlsx", "10-report.txt", "docs");
        List<TrashData> nameAscP1 = findAll(TrashListQuery.builder().sortBy(TrashSortField.NAME).sortOrder(SortOrder.ASC), null, null, 2);
        assertThat(names(nameAscP1)).containsExactly("1-report.txt", "2-report.xlsx");
        List<TrashData> nameAscP2 = findAll(TrashListQuery.builder().sortBy(TrashSortField.NAME).sortOrder(SortOrder.ASC), "2-report.xlsx", f2.getResourceSeq(), 2);
        assertThat(names(nameAscP2)).containsExactly("10-report.txt", "docs");
        List<TrashData> nameDesc = findAll(TrashListQuery.builder().sortBy(TrashSortField.NAME).sortOrder(SortOrder.DESC), "2-report.xlsx", f2.getResourceSeq(), 10);
        assertThat(names(nameDesc)).as("DESC 는 숫자 없는 폴더가 먼저(NULLS FIRST) → 2-report 뒤에는 1-report 만").containsExactly("1-report.txt");

        // SIZE DESC: 500, 50, 5, 폴더(NULL = 0) — 정렬·커서 게이트 모두 COALESCE(size,0)(구 MySQL 도 DESC 에서 NULL 이 맨 뒤)
        List<TrashData> sizeDesc = findAll(TrashListQuery.builder().sortBy(TrashSortField.SIZE).sortOrder(SortOrder.DESC), null, null, 10);
        assertThat(names(sizeDesc)).as("COALESCE(size,0) DESC — 폴더는 0 으로 맨 뒤").containsExactly("10-report.txt", "2-report.xlsx", "1-report.txt", "docs");
        List<TrashData> sizeDescP2 = findAll(TrashListQuery.builder().sortBy(TrashSortField.SIZE).sortOrder(SortOrder.DESC), "50", f2.getResourceSeq(), 10);
        assertThat(names(sizeDescP2)).containsExactly("1-report.txt", "docs");
        List<TrashData> sizeAsc = findAll(TrashListQuery.builder().sortBy(TrashSortField.SIZE).sortOrder(SortOrder.ASC), "5", f1.getResourceSeq(), 10);
        assertThat(names(sizeAsc)).containsExactly("2-report.xlsx", "10-report.txt");

        // REGISTER(삭제일) ASC 명시 정렬 + 커서
        List<TrashData> regAsc = findAll(TrashListQuery.builder().sortBy(TrashSortField.REGISTER).sortOrder(SortOrder.ASC), null, null, 10);
        assertThat(names(regAsc)).containsExactly("1-report.txt", "2-report.xlsx", "10-report.txt", "docs");
        List<TrashData> regAscP2 = findAll(TrashListQuery.builder().sortBy(TrashSortField.REGISTER).sortOrder(SortOrder.ASC), regAsc.get(1).getDeleteDate().toString(), regAsc.get(1).getResourceSeq(), 10);
        assertThat(names(regAscP2)).containsExactly("10-report.txt", "docs");

        // 필터
        assertThat(names(findAll(TrashListQuery.builder().resourceType(ResourceType.FOLDER), null, null, 10))).containsExactly("docs");
        assertThat(names(findAll(TrashListQuery.builder().fileType(FileType.DOCUMENT), null, null, 10))).containsExactly("10-report.txt", "1-report.txt");
        assertThat(names(findAll(TrashListQuery.builder().fileType(FileType.SPREADSHEET), null, null, 10))).containsExactly("2-report.xlsx");
        // 범위 시작 = 가장 이른 삭제일(t1 = 3분 전)의 날짜 — 자정 직후(00:00~00:03)엔 t1 이 어제라 "오늘 하루" 범위면 3건이 된다
        LocalDate firstDeleteDay = LocalDate.ofInstant(before.minus(3, ChronoUnit.MINUTES), DriveTime.zone());
        assertThat(findAll(TrashListQuery.builder().from(DriveTime.startOfDay(firstDeleteDay)).to(DriveTime.endExclusive(LocalDate.now(DriveTime.zone()))), null, null, 10)).hasSize(4);
        assertThat(findAll(TrashListQuery.builder().from(DriveTime.startOfDay(LocalDate.now(DriveTime.zone()).minusDays(10))).to(DriveTime.endExclusive(LocalDate.now(DriveTime.zone()).minusDays(5))), null, null, 10)).isEmpty();
        assertThat(findAll(TrashListQuery.builder().from(DriveTime.startOfDay(LocalDate.now(DriveTime.zone()))), null, null, 10)).as("둘 중 하나만 있으면 범위 조건 없음(XML 동일)").hasSize(4);
        assertThat(findAll(TrashListQuery.builder().deleteUserId("admin"), null, null, 10)).hasSize(4);
        assertThat(findAll(TrashListQuery.builder().deleteUserId("no-such-user"), null, null, 10)).isEmpty();
        jdbc.update("UPDATE tb_user SET status = 'DELETED' WHERE user_seq = ?", ADMIN);
        assertThat(findAll(TrashListQuery.builder().deleteUserId("admin"), null, null, 10)).as("삭제자 가시성(user-where-visible-alias)").isEmpty();
        assertThat(findAll(TrashListQuery.builder(), null, null, 10)).as("아이디 필터 없으면 가시성 조건 없음").hasSize(4);
        jdbc.update("UPDATE tb_user SET status = 'ACTIVE' WHERE user_seq = ?", ADMIN);
        assertThat(queryRepository.findAll(TrashListQuery.builder().userSeq(OTHER).page(CursorPaginationQuery.of(null, null, null, 10)).build())).isEmpty();

        // uniq_trash (user_seq, resource_seq) 위반 → PG tx abort, 맨 끝
        assertThatThrownBy(() -> repository.save(Trash.builder().userSeq(ADMIN).resourceSeq(f1.getResourceSeq()).build())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("delete(user_seq + resource_seq, 타 사용자 0) · deleteByResources(IN·빈/null 0 — 휴지통 정리 배치 자리) · deleteByUserSeqList(IN·빈/null 0)")
    void deletes() {
        Resource a = file("a.txt", "txt", 1L, null);
        Resource b = file("b.txt", "txt", 2L, null);
        Resource c = file("c.txt", "txt", 3L, null);
        trash(ADMIN, a.getResourceSeq());
        trash(ADMIN, b.getResourceSeq());
        trash(ADMIN, c.getResourceSeq());
        trash(OTHER, a.getResourceSeq());

        assertThat(repository.delete(TrashDeleteQuery.builder().userSeq(OTHER).resourceSeq(b.getResourceSeq()).build())).as("타 사용자 — 파티션 조건").isZero();
        assertThat(repository.delete(TrashDeleteQuery.builder().userSeq(ADMIN).resourceSeq(a.getResourceSeq()).build())).isEqualTo(1);
        assertThat(repository.delete(TrashDeleteQuery.builder().userSeq(ADMIN).resourceSeq(a.getResourceSeq()).build())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_trash WHERE user_seq = ?", Integer.class, OTHER)).as("다른 사용자의 같은 리소스 유지").isEqualTo(1);

        assertThat(repository.deleteByResources(TrashDeleteResourcesQuery.builder().userSeq(ADMIN).resourceSeqList(List.of()).build())).isZero();
        assertThat(repository.deleteByResources(TrashDeleteResourcesQuery.builder().userSeq(ADMIN).build())).isZero();
        assertThat(repository.deleteByResources(TrashDeleteResourcesQuery.builder().userSeq(ADMIN).resourceSeqList(List.of(b.getResourceSeq(), c.getResourceSeq(), 999999L)).build())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_trash WHERE user_seq = ?", Integer.class, ADMIN)).isZero();

        trash(ADMIN, b.getResourceSeq());
        assertThat(repository.deleteByUserSeqList(List.of())).isZero();
        assertThat(repository.deleteByUserSeqList(null)).isZero();
        assertThat(repository.deleteByUserSeqList(List.of(ADMIN, OTHER, 999999))).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_trash WHERE user_seq IN (?, ?)", Integer.class, ADMIN, OTHER)).isZero();
    }

    /** 웹과 같이 다음 커서 = 마지막 항목의 (크기, resource_seq) — 폴더(크기 NULL)는 cv=0 으로 보낸다 */
    private List<TrashData> pageThroughBySize(SortOrder order, int pageSize) {
        List<TrashData> all = new java.util.ArrayList<>();
        Object cursorValue = null;
        Long cursorId = null;
        for (int i = 0; i < 20; i++) {
            List<TrashData> page = findAll(TrashListQuery.builder().sortBy(TrashSortField.SIZE).sortOrder(order), cursorValue, cursorId, pageSize);
            all.addAll(page);
            if (page.size() < pageSize) {
                return all;
            }
            TrashData last = page.get(page.size() - 1);
            cursorValue = last.getSizeByte() == null ? 0L : last.getSizeByte();
            cursorId = last.getResourceSeq();
        }
        throw new AssertionError("페이지 반복 상한 초과");
    }

    @Test
    @DisplayName("SIZE 정렬 커서 — 폴더(크기 NULL)와 파일이 섞여도 페이지를 넘긴 결과 = 한 번에 받은 목록(누락·중복 없음), 폴더는 0 으로 정렬(ASC 맨 앞·DESC 맨 뒤 — 구 MySQL 순서)")
    void sizeSort_withFolders_pagingMatchesSinglePage() {
        Resource dir1 = folder("dir1");
        Resource dir2 = folder("dir2");
        Resource f0 = file("zero.txt", "txt", 0L, null);
        Resource f5 = file("five.txt", "txt", 5L, null);
        Resource f50 = file("fifty.txt", "txt", 50L, null);
        for (Resource r : List.of(dir1, dir2, f0, f5, f50)) {
            trash(ADMIN, r.getResourceSeq());
        }

        for (SortOrder order : SortOrder.values()) {
            List<String> single = names(findAll(TrashListQuery.builder().sortBy(TrashSortField.SIZE).sortOrder(order), null, null, 100));
            assertThat(single).as(order + " 한 번에").hasSize(5);
            List<Long> sizes = findAll(TrashListQuery.builder().sortBy(TrashSortField.SIZE).sortOrder(order), null, null, 100).stream()
                    .map(d -> d.getSizeByte() == null ? 0L : d.getSizeByte()).toList();
            assertThat(sizes).as(order + " 폴더 = 0 으로 단조").isSortedAccordingTo(order == SortOrder.ASC ? java.util.Comparator.naturalOrder() : java.util.Comparator.reverseOrder());
            for (int ps : List.of(1, 2, 3)) {
                assertThat(names(pageThroughBySize(order, ps))).as(order + " ps=" + ps).containsExactlyElementsOf(single);
            }
        }
    }
}
