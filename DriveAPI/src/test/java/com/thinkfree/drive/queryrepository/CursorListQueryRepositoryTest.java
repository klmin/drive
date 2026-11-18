package com.thinkfree.drive.queryrepository;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.api.page.query.PaginationQuery;
import com.thinkfree.drive.common.CursorWalk;
import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.common.search.enums.FileType;
import com.thinkfree.drive.common.search.enums.SortField;
import com.thinkfree.drive.common.search.enums.SortOrder;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.global.time.DriveTime;
import com.thinkfree.drive.recent.data.RecentData;
import com.thinkfree.drive.recent.query.RecentListQuery;
import com.thinkfree.drive.recent.repository.RecentQueryRepository;
import com.thinkfree.drive.resource.data.ResourceUsageSummaryData;
import com.thinkfree.drive.resource.entity.Resource;
import com.thinkfree.drive.resource.enums.ResourceSortField;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.global.data.ResourceGlobalData;
import com.thinkfree.drive.resource.global.repository.ResourceGlobalQueryRepository;
import com.thinkfree.drive.resource.query.ResourceListQuery;
import com.thinkfree.drive.resource.repository.ResourceRepository;
import com.thinkfree.drive.resource.share.data.ResourceSharedData;
import com.thinkfree.drive.resource.share.repository.ResourceShareQueryRepository;
import com.thinkfree.drive.resource.version.data.ResourceVersionData;
import com.thinkfree.drive.resource.version.enums.ResourceVersionStatus;
import com.thinkfree.drive.resource.version.query.ResourceVersionListQuery;
import com.thinkfree.drive.resource.version.repository.ResourceVersionQueryRepository;
import com.thinkfree.drive.share.query.ShareTargetNearestFindQuery;
import com.thinkfree.drive.share.target.data.ShareTargetData;
import com.thinkfree.drive.share.target.enums.ShareTargetSortField;
import com.thinkfree.drive.share.target.query.ShareTargetListQuery;
import com.thinkfree.drive.share.target.query.ShareTargetResourceUsersQuery;
import com.thinkfree.drive.share.target.query.UnsharedTargetsFindQuery;
import com.thinkfree.drive.share.target.repository.ShareTargetQueryRepository;
import com.thinkfree.drive.starred.data.StarredData;
import com.thinkfree.drive.starred.query.StarredListQuery;
import com.thinkfree.drive.starred.repository.StarredQueryRepository;
import com.thinkfree.drive.trash.data.TrashData;
import com.thinkfree.drive.trash.enums.TrashSortField;
import com.thinkfree.drive.trash.query.TrashListQuery;
import com.thinkfree.drive.trash.repository.TrashQueryRepository;
import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.user.enums.SignupType;
import com.thinkfree.drive.user.enums.UserStatus;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * 커서 목록 리포 7종(jOOQ) 기대값 단언 — 10-30 작업 B P4 에서 전환 전/후 비교 테스트를 대신한다(기대값은 비교 테스트로 구 JdbcClient 구현과 같음을 확인한 값). 트랜잭션 롤백.
 * <p>
 * 픽스처(경계): 같은 테넌트 뷰어·다른 테넌트 사용자(파티션 user_seq 가 다름), 3단 폴더 + 2단 공유(가까운 조상 선택), 여러 공유자에게서 받은 공유,
 * 이름 자연정렬·크기·수정일·열람일·공유일·삭제일 동률, 삭제(is_delete)·휴지통(삭제자 다름), 잠긴 파일, 버전(상태 섞임).
 * 대표 정렬 결과는 이름 순서로 단언하고, 정렬 × 방향 × 페이지 크기 × 필터 전 조합은 커서로 끝까지 읽은 결과가 한 번에 읽은 결과와 같은지({@link CursorWalk}) 본다.
 */
@DisplayName("커서 목록 리포 7종 — 정렬·필터·커서 기대값(PostgreSQL Testcontainers)")
class CursorListQueryRepositoryTest extends RepositoryTest {

    private static final int ADMIN = 2;
    private static final int SUPER_ADMIN = 1;
    private static final List<String> NONE = List.of();

    @Autowired
    private ResourceRepository resourceRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RecentQueryRepository recent;
    @Autowired
    private StarredQueryRepository starred;
    @Autowired
    private TrashQueryRepository trash;
    @Autowired
    private ShareTargetQueryRepository shareTarget;
    @Autowired
    private ResourceShareQueryRepository resourceShare;
    @Autowired
    private ResourceGlobalQueryRepository resourceGlobal;
    @Autowired
    private ResourceVersionQueryRepository resourceVersion;

    private int walked;

    private int viewer, other;
    private Resource root, fB, fA, f10, f2, sub, deep, leaf, leaf2, zTxt, cPdf, mDoc, same1, n2a, n2b, gone, top;
    private Resource trashRoot, trashChild, trashFile;
    private Resource oRoot, oFile, oSameName, oGone;
    private long sRoot, sDeep, sCpdf, sGone, sTop, sO;

    @BeforeEach
    void seed() {
        loginAs(ADMIN);
        walked = 0;

        Integer adminTenant = jdbc.queryForObject("SELECT tenant_seq FROM tb_user WHERE user_seq = ?", Integer.class, ADMIN);
        Integer otherTenant = jdbc.queryForObject("INSERT INTO tb_tenant (tenant_name, register_user_seq) VALUES ('비교 테넌트', ?) RETURNING tenant_seq", Integer.class, SUPER_ADMIN);
        viewer = user("cmp-viewer", adminTenant, "뷰어");
        other = user("cmp-other", otherTenant, "다른 사용자");

        root = folder(ADMIN, "root", null);
        fB = folder(ADMIN, "b", root);
        fA = folder(ADMIN, "a", root);
        f10 = folder(ADMIN, "10", root);
        f2 = folder(ADMIN, "2", root);
        sub = folder(ADMIN, "sub", root);
        deep = folder(ADMIN, "deep", sub);
        leaf = file(ADMIN, "leaf.txt", deep, 5L, "txt");
        leaf2 = file(ADMIN, "leaf2.pdf", deep, 6L, "pdf");
        zTxt = file(ADMIN, "z.txt", root, 300L, "txt");
        cPdf = file(ADMIN, "c.pdf", root, 100L, "pdf");
        mDoc = file(ADMIN, "m.docx", root, 200L, "docx");
        same1 = file(ADMIN, "same1.txt", root, 200L, "txt");      // 크기 동률(m.docx)
        n2a = file(ADMIN, "2a.txt", root, 50L, "txt");             // 이름 숫자부 동률
        n2b = file(ADMIN, "2b.txt", root, 50L, "txt");             // 크기 동률 + 숫자부 동률
        gone = file(ADMIN, "gone.txt", root, 9L, "txt");
        top = file(ADMIN, "top.txt", null, 1L, "txt");

        trashRoot = folder(ADMIN, "trash-root", null);
        trashChild = folder(ADMIN, "trash-child", trashRoot);
        trashFile = file(ADMIN, "trash-file.txt", trashChild, 4L, "txt");

        oRoot = folder(other, "o-root", null);
        oFile = file(other, "o.txt", oRoot, 70L, "txt");
        oSameName = file(other, "z.txt", oRoot, 300L, "txt");
        oGone = file(other, "o-gone.pdf", oRoot, 30L, "pdf");

        // 삭제·휴지통 — 삭제일 동률(gone·trashRoot), 삭제자 다름(trashFile 은 뷰어), 다른 사용자 휴지통
        jdbc.update("UPDATE tb_resource SET is_delete = 'Y' WHERE resource_seq IN (?, ?, ?, ?)", seq(gone), seq(trashRoot), seq(trashChild), seq(trashFile));
        jdbc.update("UPDATE tb_resource SET is_delete = 'Y' WHERE resource_seq = ?", seq(oGone));
        trashRow(ADMIN, gone, ADMIN, "2030-01-01 00:00:00");
        trashRow(ADMIN, trashRoot, ADMIN, "2030-01-01 00:00:00");
        trashRow(ADMIN, trashFile, viewer, "2029-01-01 00:00:00");
        trashRow(other, oGone, other, "2030-02-01 00:00:00");

        // 수정일 동률(파일 3개)·수정일 차이
        Timestamp tie = Timestamp.valueOf("2030-01-01 00:00:00");
        for (Resource r : List.of(zTxt, mDoc, same1)) {
            jdbc.update("UPDATE tb_resource SET update_date = ? WHERE resource_seq = ?", tie, seq(r));
        }
        jdbc.update("UPDATE tb_resource SET update_date = ? WHERE resource_seq IN (?, ?)", Timestamp.valueOf("2029-06-01 00:00:00"), seq(fA), seq(fB));

        // 공유 — ADMIN: root(뷰어·다른 사용자)·deep(뷰어, 2단)·c.pdf·gone(삭제)·top(다른 사용자), 다른 사용자: o-root(뷰어·ADMIN)
        sRoot = share(ADMIN, root);
        sDeep = share(ADMIN, deep);
        sCpdf = share(ADMIN, cPdf);
        sGone = share(ADMIN, gone);
        sTop = share(ADMIN, top);
        sO = share(other, oRoot);
        target(viewer, sRoot, root, ADMIN, "2030-01-01 00:00:00");
        target(other, sRoot, root, ADMIN, "2030-01-02 00:00:00");
        target(viewer, sDeep, deep, ADMIN, "2030-01-01 00:00:00");     // 공유일 동률
        target(viewer, sCpdf, cPdf, ADMIN, "2029-05-01 00:00:00");
        target(viewer, sGone, gone, ADMIN, "2029-04-01 00:00:00");
        target(other, sTop, top, ADMIN, "2029-03-01 00:00:00");
        target(viewer, sO, oRoot, other, "2030-03-01 00:00:00");
        target(ADMIN, sO, oRoot, other, "2030-03-01 00:00:00");

        // 별표 — 내 것·남이 공유한 것(shared_by 다름)·삭제된 것
        star(ADMIN, fA, ADMIN);
        star(ADMIN, cPdf, ADMIN);
        star(ADMIN, oFile, other);
        star(ADMIN, oRoot, other);
        star(viewer, root, ADMIN);
        star(viewer, cPdf, ADMIN);
        star(viewer, gone, ADMIN);
        star(viewer, deep, ADMIN);
        star(viewer, oRoot, other);
        star(other, oFile, other);

        // 열람 — 열람일 동률 포함
        seen(ADMIN, zTxt, ADMIN, "2026-06-01 00:00:00");
        seen(ADMIN, cPdf, ADMIN, "2026-05-01 00:00:00");
        seen(ADMIN, mDoc, ADMIN, "2026-05-01 00:00:00");
        seen(ADMIN, f10, ADMIN, "2026-04-01 00:00:00");
        seen(ADMIN, oFile, other, "2026-07-01 00:00:00");
        seen(viewer, root, ADMIN, "2026-06-01 00:00:00");
        seen(viewer, deep, ADMIN, "2026-05-01 00:00:00");
        seen(viewer, cPdf, ADMIN, "2026-05-01 00:00:00");
        seen(viewer, leaf, ADMIN, "2026-03-01 00:00:00");
        seen(viewer, gone, ADMIN, "2026-02-01 00:00:00");
        seen(viewer, oFile, other, "2026-07-01 00:00:00");
        seen(viewer, oRoot, other, "2026-05-01 00:00:00");

        // 잠금·버전(상태 섞임)
        jdbc.update("UPDATE tb_resource SET is_locked = 'Y' WHERE resource_seq = ?", seq(leaf));
        version(ADMIN, leaf, 1, 3L, "SUCCESS");
        version(ADMIN, leaf, 2, 4L, "SUCCESS");
        version(ADMIN, leaf, 3, 7L, "PENDING");
        version(ADMIN, gone, 1, 2L, "SUCCESS");
        version(other, oFile, 1, 8L, "SUCCESS");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------ 최근 열람

    @Test
    @DisplayName("Recent.findAll — 사용자별 열람일 DESC, 필터 × 페이지 크기 커서 왕복")
    void recentFindAll() {
        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(recentNames(ADMIN, null)).as("ADMIN").isEqualTo(List.of("o.txt", "z.txt", "m.docx", "c.pdf", "10"));
        soft.assertThat(recentNames(viewer, null)).as("뷰어 — 받은 공유·삭제된 것").isEqualTo(List.of("o.txt", "root", "o-root", "c.pdf", "deep", "leaf.txt"));
        soft.assertThat(recentNames(other, null)).as("다른 사용자").isEqualTo(NONE);
        soft.assertThat(recentNames(null, null)).as("사용자 null").isEqualTo(NONE);
        soft.assertThat(recentNames(viewer, true)).as("뷰어 삭제 목록").isEqualTo(List.of("gone.txt"));
        soft.assertAll();

        for (Integer user : Arrays.asList(ADMIN, viewer, other)) {
            for (Filter f : filters()) {
                for (int size : new int[]{1, 2}) {
                    walk("recent " + user + "/" + f.name() + "/" + size,
                            p -> recent.findAll(RecentListQuery.builder().userSeq(user).resourceType(f.type()).fileType(f.fileType()).userId(f.userId())
                                    .from(f.from()).to(f.to()).isDelete(f.isDelete()).page(p).build()),
                            RecentData::getRecentUpdateDate, RecentData::getResourceSeq, RecentData::getResourceType, RecentData::getResourceName, size);
                }
            }
        }
        assertThat(walked).as("빈 목록만 본 것이 아님").isGreaterThan(50);
    }

    private List<String> recentNames(Integer user, Boolean isDelete) {
        return names(recent.findAll(RecentListQuery.builder().userSeq(user).isDelete(isDelete == null ? false : isDelete).page(CursorWalk.firstPage()).build()), RecentData::getResourceName);
    }

    // ------------------------------------------------------------------ 별표

    @Test
    @DisplayName("Starred.findAll — 사용자별·정렬별 결과, 정렬 × 방향 × 페이지 크기 × 필터 커서 왕복")
    void starredFindAll() {
        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(starredNames(ADMIN, null, null)).as("ADMIN 기본").isEqualTo(List.of("a", "o-root", "c.pdf", "o.txt"));
        soft.assertThat(starredNames(viewer, null, null)).as("뷰어 기본").isEqualTo(List.of("deep", "o-root", "root", "c.pdf"));
        soft.assertThat(starredNames(other, null, null)).as("다른 사용자 기본").isEqualTo(List.of("o.txt"));
        soft.assertThat(starredNames(viewer, SortField.NAME, SortOrder.DESC)).as("뷰어 NAME DESC").isEqualTo(List.of("root", "o-root", "deep", "c.pdf"));
        soft.assertThat(starredNames(viewer, SortField.SIZE, SortOrder.ASC)).as("뷰어 SIZE ASC").isEqualTo(List.of("root", "deep", "o-root", "c.pdf"));
        soft.assertThat(starredNames(viewer, SortField.UPDATE, SortOrder.DESC)).as("뷰어 UPDATE DESC").isEqualTo(List.of("o-root", "deep", "root", "c.pdf"));
        soft.assertAll();

        for (Integer user : Arrays.asList(ADMIN, viewer, other)) {
            for (SortField sort : SortField.values()) {
                for (SortOrder order : SortOrder.values()) {
                    for (int size : new int[]{1, 2}) {
                        walk("starred " + user + "/" + sort + "/" + order + "/" + size,
                                p -> starred.findAll(StarredListQuery.builder().userSeq(user).isDelete(false).sortBy(sort).sortOrder(order).page(p).build()),
                                d -> starredCursor(sort, d), StarredData::getResourceSeq, StarredData::getResourceType, StarredData::getResourceName, size);
                    }
                }
            }
        }
        for (Filter f : filters()) {
            for (SortField sort : SortField.values()) {
                walk("starred filter " + f.name() + "/" + sort,
                        p -> starred.findAll(StarredListQuery.builder().userSeq(viewer).resourceType(f.type()).fileType(f.fileType()).userId(f.userId())
                                .from(f.from()).to(f.to()).isDelete(f.isDelete()).sortBy(sort).sortOrder(SortOrder.DESC).page(p).build()),
                        d -> starredCursor(sort, d), StarredData::getResourceSeq, StarredData::getResourceType, StarredData::getResourceName, 2);
            }
        }
        assertThat(walked).isGreaterThan(50);
    }

    private List<String> starredNames(int user, SortField sort, SortOrder order) {
        return names(starred.findAll(StarredListQuery.builder().userSeq(user).isDelete(false).sortBy(sort).sortOrder(order).page(CursorWalk.firstPage()).build()),
                StarredData::getResourceName);
    }

    private static Object starredCursor(SortField sort, StarredData d) {
        if (sort == null || sort == SortField.NAME) {
            return d.getResourceName();
        }
        return sort == SortField.SIZE ? d.getSizeByte() : d.getResourceUpdateDate();
    }

    // ------------------------------------------------------------------ 휴지통

    @Test
    @DisplayName("Trash.findAll — 사용자별·정렬별·삭제자 필터 결과, 정렬 × 방향 × 페이지 크기 × 필터 커서 왕복")
    void trashFindAll() {
        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(trashNames(ADMIN, null, null, null)).as("ADMIN 기본").isEqualTo(List.of("gone.txt", "trash-root", "trash-file.txt"));
        soft.assertThat(trashNames(other, null, null, null)).as("다른 사용자 기본").isEqualTo(List.of("o-gone.pdf"));
        soft.assertThat(trashNames(viewer, null, null, null)).as("뷰어(삭제자로만 등장)").isEqualTo(NONE);
        soft.assertThat(trashNames(ADMIN, TrashSortField.NAME, SortOrder.ASC, null)).as("ADMIN NAME ASC").isEqualTo(List.of("gone.txt", "trash-file.txt", "trash-root"));
        soft.assertThat(trashNames(ADMIN, TrashSortField.SIZE, SortOrder.DESC, null)).as("ADMIN SIZE DESC").isEqualTo(List.of("gone.txt", "trash-file.txt", "trash-root"));
        soft.assertThat(trashNames(ADMIN, TrashSortField.REGISTER, SortOrder.ASC, null)).as("ADMIN REGISTER ASC").isEqualTo(List.of("trash-file.txt", "gone.txt", "trash-root"));
        soft.assertThat(trashNames(ADMIN, null, null, "cmp-viewer")).as("삭제자 뷰어").isEqualTo(List.of("trash-file.txt"));
        soft.assertThat(trashNames(ADMIN, null, null, "admin")).as("삭제자 admin").isEqualTo(List.of("gone.txt", "trash-root"));
        soft.assertAll();

        for (Integer user : Arrays.asList(ADMIN, other)) {
            for (TrashSortField sort : TrashSortField.values()) {
                for (SortOrder order : SortOrder.values()) {
                    for (int size : new int[]{1, 2}) {
                        walk("trash " + user + "/" + sort + "/" + order + "/" + size,
                                p -> trash.findAll(TrashListQuery.builder().userSeq(user).sortBy(sort).sortOrder(order).page(p).build()),
                                d -> trashCursor(sort, d), TrashData::getResourceSeq, TrashData::getResourceType, TrashData::getResourceName, size);
                    }
                }
            }
        }
        for (Filter f : filters()) {
            for (String deleteUserId : Arrays.asList(null, "admin", "cmp-viewer", "")) {
                for (TrashSortField sort : TrashSortField.values()) {
                    walk("trash filter " + f.name() + "/" + deleteUserId + "/" + sort,
                            p -> trash.findAll(TrashListQuery.builder().userSeq(ADMIN).resourceType(f.type()).fileType(f.fileType()).deleteUserId(deleteUserId)
                                    .from(f.from()).to(f.to()).sortBy(sort).sortOrder(SortOrder.ASC).page(p).build()),
                            d -> trashCursor(sort, d), TrashData::getResourceSeq, TrashData::getResourceType, TrashData::getResourceName, 1);
                }
            }
        }
        assertThat(walked).isGreaterThan(50);
    }

    private List<String> trashNames(int user, TrashSortField sort, SortOrder order, String deleteUserId) {
        return names(trash.findAll(TrashListQuery.builder().userSeq(user).deleteUserId(deleteUserId).sortBy(sort).sortOrder(order).page(CursorWalk.firstPage()).build()),
                TrashData::getResourceName);
    }

    private static Object trashCursor(TrashSortField sort, TrashData d) {
        if (sort == null || sort == TrashSortField.NAME) {
            return d.getResourceName();
        }
        return sort == TrashSortField.SIZE ? d.getSizeByte() : d.getDeleteDate();
    }

    // ------------------------------------------------------------------ 공유받은 목록

    @Test
    @DisplayName("ShareTarget.findAll — 사용자별·정렬별 결과, 정렬 × 방향 × 페이지 크기 × 필터 커서 왕복")
    void shareTargetFindAll() {
        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(shareTargetNames(viewer, null, null)).as("뷰어 기본(공유일)").isEqualTo(List.of("o-root", "root", "deep", "c.pdf"));
        soft.assertThat(shareTargetNames(other, null, null)).as("다른 사용자 기본").isEqualTo(List.of("root", "top.txt"));
        soft.assertThat(shareTargetNames(ADMIN, null, null)).as("ADMIN 기본").isEqualTo(List.of("o-root"));
        // 폴더(root·deep·o-root) 먼저, 파일(c.pdf) 뒤 — 공유일 root·deep 동률, 열람은 root 06-01·deep·o-root 05-01 동률
        Map<String, List<String>> bySort = Map.of(
                "NAME ASC", List.of("deep", "o-root", "root", "c.pdf"),
                "NAME DESC", List.of("root", "o-root", "deep", "c.pdf"),
                "SIZE ASC", List.of("root", "deep", "o-root", "c.pdf"),
                "SIZE DESC", List.of("o-root", "deep", "root", "c.pdf"),
                "UPDATE ASC", List.of("root", "deep", "o-root", "c.pdf"),
                "UPDATE DESC", List.of("o-root", "deep", "root", "c.pdf"),
                "OPEN ASC", List.of("deep", "o-root", "root", "c.pdf"),
                "OPEN DESC", List.of("root", "o-root", "deep", "c.pdf"),
                "SHARE ASC", List.of("root", "deep", "o-root", "c.pdf"),
                "SHARE DESC", List.of("o-root", "deep", "root", "c.pdf"));
        for (ShareTargetSortField sort : ShareTargetSortField.values()) {
            for (SortOrder order : SortOrder.values()) {
                soft.assertThat(shareTargetNames(viewer, sort, order)).as("뷰어 " + sort + " " + order).isEqualTo(bySort.get(sort + " " + order));
            }
        }
        soft.assertAll();

        for (Integer user : Arrays.asList(viewer, other, ADMIN)) {
            for (ShareTargetSortField sort : ShareTargetSortField.values()) {
                for (SortOrder order : SortOrder.values()) {
                    for (int size : new int[]{1, 2}) {
                        walk("shareTarget " + user + "/" + sort + "/" + order + "/" + size,
                                p -> shareTarget.findAll(ShareTargetListQuery.builder().userSeq(user).isDelete(false).sortBy(sort).sortOrder(order).page(p).build()),
                                d -> shareTargetCursor(sort, d), ShareTargetData::getResourceSeq, ShareTargetData::getResourceType, ShareTargetData::getResourceName, size);
                    }
                }
            }
        }
        for (Filter f : filters()) {
            for (ShareTargetSortField sort : ShareTargetSortField.values()) {
                walk("shareTarget filter " + f.name() + "/" + sort,
                        p -> shareTarget.findAll(ShareTargetListQuery.builder().userSeq(viewer).resourceType(f.type()).fileType(f.fileType()).userId(f.userId())
                                .from(f.from()).to(f.to()).isDelete(f.isDelete()).sortBy(sort).sortOrder(SortOrder.DESC).page(p).build()),
                        d -> shareTargetCursor(sort, d), ShareTargetData::getResourceSeq, ShareTargetData::getResourceType, ShareTargetData::getResourceName, 1);
            }
        }
        assertThat(walked).isGreaterThan(50);
    }

    private List<String> shareTargetNames(int user, ShareTargetSortField sort, SortOrder order) {
        return names(shareTarget.findAll(ShareTargetListQuery.builder().userSeq(user).isDelete(false).sortBy(sort).sortOrder(order).page(CursorWalk.firstPage()).build()),
                ShareTargetData::getResourceName);
    }

    private static Object shareTargetCursor(ShareTargetSortField sort, ShareTargetData d) {
        if (sort == null || sort == ShareTargetSortField.SHARE) {
            return d.getShareDate();
        }
        return switch (sort) {
            case NAME -> d.getResourceName();
            case SIZE -> d.getSizeByte();
            case UPDATE -> d.getResourceUpdateDate();
            case OPEN -> fallback(d.getRecentUpdateDate(), d.getResourceUpdateDate());
            default -> throw new IllegalStateException();
        };
    }

    // ------------------------------------------------------------------ 공유받은 폴더 안·전체 목록

    @Test
    @DisplayName("ResourceShare.findAllByShared — 폴더 × 뷰어 결과, 정렬 × 방향 × 페이지 크기 × 필터 커서 왕복")
    void resourceShareFindAll() {
        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(resourceShareNames(ADMIN, viewer, seq(root), null, null)).as("뷰어가 root 안").isEqualTo(List.of("2", "10", "a", "b", "sub", "2a.txt", "2b.txt", "c.pdf", "m.docx", "same1.txt", "z.txt"));
        soft.assertThat(resourceShareNames(ADMIN, viewer, seq(sub), null, null)).as("뷰어가 sub 안(root 공유 상속)").isEqualTo(List.of("deep"));
        soft.assertThat(resourceShareNames(ADMIN, viewer, seq(deep), null, null)).as("뷰어가 deep 안").isEqualTo(List.of("leaf.txt", "leaf2.pdf"));
        soft.assertThat(resourceShareNames(other, ADMIN, seq(oRoot), null, null)).as("ADMIN 이 o-root 안").isEqualTo(List.of("o.txt", "z.txt"));
        soft.assertThat(resourceShareNames(ADMIN, viewer, seq(root), ResourceSortField.NAME, SortOrder.DESC)).as("NAME DESC").isEqualTo(List.of("sub", "b", "a", "10", "2", "z.txt", "same1.txt", "m.docx", "c.pdf", "2b.txt", "2a.txt"));
        soft.assertThat(resourceShareNames(ADMIN, viewer, seq(root), ResourceSortField.OPEN, SortOrder.ASC)).as("OPEN ASC(뷰어 열람 기준)").isEqualTo(List.of("10", "2", "sub", "b", "a", "c.pdf", "2a.txt", "2b.txt", "z.txt", "m.docx", "same1.txt"));
        soft.assertAll();

        for (Long parent : Arrays.asList(seq(root), seq(sub), seq(deep), null, seq(oRoot))) {
            for (Integer viewerSeq : Arrays.asList(viewer, ADMIN)) {
                for (ResourceSortField sort : ResourceSortField.values()) {
                    for (SortOrder order : SortOrder.values()) {
                        for (int size : new int[]{1, 3}) {
                            int owner = parent != null && parent.equals(seq(oRoot)) ? other : ADMIN;
                            walk("resourceShare " + parent + "/" + viewerSeq + "/" + sort + "/" + order + "/" + size,
                                    p -> resourceShare.findAllByShared(ResourceListQuery.builder().userSeq(owner).requestUserSeq(viewerSeq).parentFolderSeq(parent).isDelete(false)
                                            .sortBy(sort).sortOrder(order).page(p).build()),
                                    d -> resourceCursor(sort, d.getResourceName(), d.getSizeByte(), d.getUpdateDate(), d.getRecentUpdateDate()),
                                    ResourceSharedData::getResourceSeq, ResourceSharedData::getResourceType, ResourceSharedData::getResourceName, size);
                        }
                    }
                }
            }
        }
        for (Filter f : filters()) {
            for (ResourceSortField sort : ResourceSortField.values()) {
                walk("resourceShare filter " + f.name() + "/" + sort,
                        p -> resourceShare.findAllByShared(ResourceListQuery.builder().userSeq(ADMIN).requestUserSeq(viewer).parentFolderSeq(seq(root)).resourceType(f.type())
                                .fileType(f.fileType()).userId(f.userId()).from(f.from()).to(f.to()).isDelete(f.isDelete()).sortBy(sort).sortOrder(SortOrder.DESC).page(p).build()),
                        d -> resourceCursor(sort, d.getResourceName(), d.getSizeByte(), d.getUpdateDate(), d.getRecentUpdateDate()),
                        ResourceSharedData::getResourceSeq, ResourceSharedData::getResourceType, ResourceSharedData::getResourceName, 2);
            }
        }
        assertThat(walked).isGreaterThan(50);
    }

    private List<String> resourceShareNames(int owner, int viewerSeq, Long parent, ResourceSortField sort, SortOrder order) {
        return names(resourceShare.findAllByShared(ResourceListQuery.builder().userSeq(owner).requestUserSeq(viewerSeq).parentFolderSeq(parent).isDelete(false)
                .sortBy(sort).sortOrder(order).page(CursorWalk.firstPage()).build()), ResourceSharedData::getResourceName);
    }

    @Test
    @DisplayName("ResourceGlobal.findAll — 사용자 × 검색어 결과, 정렬 × 방향 × 페이지 크기 × 필터 커서 왕복")
    void resourceGlobalFindAll() {
        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(globalNames(viewer, null, null, null)).as("뷰어 전체(내 것 + 받은 공유)").isEqualTo(List.of("2", "10", "a", "b", "deep", "o-root", "root", "sub", "2a.txt", "2b.txt", "c.pdf", "leaf.txt", "leaf2.pdf", "m.docx", "o.txt", "same1.txt", "z.txt", "z.txt"));
        soft.assertThat(globalNames(viewer, "leaf", null, null)).as("뷰어 'leaf'").isEqualTo(List.of("leaf.txt", "leaf2.pdf"));
        soft.assertThat(globalNames(ADMIN, "t", null, null)).as("ADMIN 't'").isEqualTo(List.of("o-root", "root", "2a.txt", "2b.txt", "leaf.txt", "o.txt", "same1.txt", "top.txt", "z.txt", "z.txt"));
        soft.assertThat(globalNames(other, null, null, null)).as("다른 사용자 전체").isEqualTo(List.of("2", "10", "a", "b", "deep", "o-root", "root", "sub", "2a.txt", "2b.txt", "c.pdf", "leaf.txt", "leaf2.pdf", "m.docx", "o.txt", "same1.txt", "top.txt", "z.txt", "z.txt"));
        soft.assertThat(globalNames(viewer, "없음", null, null)).as("없는 검색어").isEmpty();
        soft.assertThat(globalNames(viewer, null, ResourceSortField.NAME, SortOrder.DESC)).as("뷰어 NAME DESC").isEqualTo(List.of("sub", "root", "o-root", "deep", "b", "a", "10", "2", "z.txt", "z.txt", "same1.txt", "o.txt", "m.docx", "leaf2.pdf", "leaf.txt", "c.pdf", "2b.txt", "2a.txt"));
        soft.assertThat(globalNames(viewer, null, ResourceSortField.OPEN, SortOrder.DESC)).as("뷰어 OPEN DESC").isEqualTo(List.of("root", "o-root", "deep", "a", "b", "sub", "2", "10", "o.txt", "c.pdf", "leaf.txt", "same1.txt", "m.docx", "z.txt", "z.txt", "2b.txt", "2a.txt", "leaf2.pdf"));
        soft.assertAll();

        for (Integer user : Arrays.asList(viewer, ADMIN, other)) {
            for (String keyword : Arrays.asList(null, "t", "leaf")) {
                for (ResourceSortField sort : ResourceSortField.values()) {
                    for (SortOrder order : SortOrder.values()) {
                        walk("global " + user + "/" + keyword + "/" + sort + "/" + order,
                                p -> resourceGlobal.findAll(ResourceListQuery.builder().userSeq(user).searchKeyword(keyword).isDelete(false).sortBy(sort).sortOrder(order).page(p).build()),
                                d -> resourceCursor(sort, d.getResourceName(), d.getSizeByte(), d.getUpdateDate(), d.getRecentUpdateDate()),
                                ResourceGlobalData::getResourceSeq, ResourceGlobalData::getResourceType, ResourceGlobalData::getResourceName, 2);
                    }
                }
            }
        }
        for (Filter f : filters()) {
            for (ResourceSortField sort : ResourceSortField.values()) {
                walk("global filter " + f.name() + "/" + sort,
                        p -> resourceGlobal.findAll(ResourceListQuery.builder().userSeq(viewer).resourceType(f.type()).fileType(f.fileType()).userId(f.userId())
                                .from(f.from()).to(f.to()).isDelete(f.isDelete()).sortBy(sort).sortOrder(SortOrder.DESC).page(p).build()),
                        d -> resourceCursor(sort, d.getResourceName(), d.getSizeByte(), d.getUpdateDate(), d.getRecentUpdateDate()),
                        ResourceGlobalData::getResourceSeq, ResourceGlobalData::getResourceType, ResourceGlobalData::getResourceName, 2);
            }
        }
        assertThat(walked).isGreaterThan(50);
    }

    private List<String> globalNames(int user, String keyword, ResourceSortField sort, SortOrder order) {
        return names(resourceGlobal.findAll(ResourceListQuery.builder().userSeq(user).searchKeyword(keyword).isDelete(false).sortBy(sort).sortOrder(order)
                .page(CursorWalk.firstPage()).build()), ResourceGlobalData::getResourceName);
    }

    private static Object resourceCursor(ResourceSortField sort, String name, Long size, Instant update, Instant recent) {
        if (sort == null || sort == ResourceSortField.NAME) {
            return name;
        }
        return switch (sort) {
            case SIZE -> size;
            case UPDATE -> update;
            case OPEN -> fallback(recent, update);
            default -> throw new IllegalStateException();
        };
    }

    @Test
    @DisplayName("기본 정렬(sortBy 없음) × 방향(없음·ASC·DESC) × 페이지 크기 — 커서로 끝까지 읽은 결과 = 한 번에 읽은 결과(6개 목록)")
    void defaultSortCursorWalk() {
        for (SortOrder order : Arrays.asList(null, SortOrder.ASC, SortOrder.DESC)) {
            for (int size : new int[]{1, 2, 3}) {
                String at = order + "/" + size;
                for (Integer user : Arrays.asList(ADMIN, viewer, other)) {
                    walk("starred default " + user + "/" + at,
                            p -> starred.findAll(StarredListQuery.builder().userSeq(user).isDelete(false).sortOrder(order).page(p).build()),
                            d -> d.getResourceName(), StarredData::getResourceSeq, StarredData::getResourceType, StarredData::getResourceName, size);
                    walk("shareTarget default " + user + "/" + at,
                            p -> shareTarget.findAll(ShareTargetListQuery.builder().userSeq(user).isDelete(false).sortOrder(order).page(p).build()),
                            ShareTargetData::getShareDate, ShareTargetData::getResourceSeq, ShareTargetData::getResourceType, ShareTargetData::getResourceName, size);
                    walk("global default " + user + "/" + at,
                            p -> resourceGlobal.findAll(ResourceListQuery.builder().userSeq(user).isDelete(false).sortOrder(order).page(p).build()),
                            ResourceGlobalData::getResourceName, ResourceGlobalData::getResourceSeq, ResourceGlobalData::getResourceType, ResourceGlobalData::getResourceName, size);
                }
                for (Integer user : Arrays.asList(ADMIN, other)) {
                    walk("trash default " + user + "/" + at,
                            p -> trash.findAll(TrashListQuery.builder().userSeq(user).sortOrder(order).page(p).build()),
                            TrashData::getDeleteDate, TrashData::getResourceSeq, TrashData::getResourceType, TrashData::getResourceName, size);
                }
                walk("resourceShare default root/" + at,
                        p -> resourceShare.findAllByShared(ResourceListQuery.builder().userSeq(ADMIN).requestUserSeq(viewer).parentFolderSeq(seq(root)).isDelete(false)
                                .sortOrder(order).page(p).build()),
                        ResourceSharedData::getResourceName, ResourceSharedData::getResourceSeq, ResourceSharedData::getResourceType, ResourceSharedData::getResourceName, size);
            }
        }
        assertThat(walked).isGreaterThan(100);
    }

    // ------------------------------------------------------------------ 버전

    @Test
    @DisplayName("ResourceVersion.findAll·countAll·findUsageSummaryByUserSeq — 상태·삭제 상태·페이지")
    void resourceVersion() {
        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(versions(ADMIN, seq(leaf), ResourceVersionStatus.SUCCESS, false, 1, 50)).as("leaf SUCCESS").isEqualTo(List.of("leaf.txt#2:4", "leaf.txt#1:3", "leaf.txt#1:5"));
        soft.assertThat(versions(ADMIN, seq(leaf), ResourceVersionStatus.PENDING, false, 1, 50)).as("leaf PENDING").isEqualTo(List.of("leaf.txt#3:7", "leaf.txt#1:5"));
        soft.assertThat(versions(ADMIN, seq(leaf), null, false, 1, 50)).as("leaf 상태 null").isEqualTo(List.of("leaf.txt#1:5"));
        soft.assertThat(versions(ADMIN, seq(leaf), ResourceVersionStatus.SUCCESS, false, 2, 1)).as("leaf SUCCESS 2쪽(1개씩)").isEqualTo(List.of("leaf.txt#1:3"));
        soft.assertThat(versions(ADMIN, seq(gone), ResourceVersionStatus.SUCCESS, true, 1, 50)).as("삭제된 gone").isEqualTo(List.of("gone.txt#1:2", "gone.txt#1:9"));
        soft.assertThat(versions(ADMIN, seq(gone), ResourceVersionStatus.SUCCESS, false, 1, 50)).as("삭제된 gone 을 미삭제로").isEqualTo(NONE);
        soft.assertThat(versions(ADMIN, seq(oFile), ResourceVersionStatus.SUCCESS, false, 1, 50)).as("남의 파일을 내 키로").isEqualTo(NONE);
        soft.assertThat(versions(other, seq(oFile), ResourceVersionStatus.SUCCESS, false, 1, 50)).as("다른 사용자 본인").isEqualTo(List.of("o.txt#1:8", "o.txt#1:70"));
        soft.assertThat(versions(ADMIN, null, ResourceVersionStatus.SUCCESS, false, 1, 50)).as("리소스 null").isEqualTo(NONE);

        soft.assertThat(versionCount(ADMIN, seq(leaf), ResourceVersionStatus.SUCCESS, false)).as("count leaf SUCCESS").isEqualTo(3L);
        soft.assertThat(versionCount(ADMIN, seq(leaf), null, false)).as("count leaf 상태 null").isEqualTo(1L);
        soft.assertThat(versionCount(ADMIN, seq(gone), ResourceVersionStatus.SUCCESS, true)).as("count 삭제된 gone").isEqualTo(2L);
        soft.assertThat(versionCount(ADMIN, seq(leaf), ResourceVersionStatus.SUCCESS, null)).as("count 삭제 상태 null").isEqualTo(0L);

        soft.assertThat(resourceVersion.findUsageSummaryByUserSeq(ADMIN)).as("ADMIN 버전 사용량").isEqualTo(new ResourceUsageSummaryData(16, 14, 2));
        soft.assertThat(resourceVersion.findUsageSummaryByUserSeq(other)).as("다른 사용자 버전 사용량").isEqualTo(new ResourceUsageSummaryData(8, 8, 0));
        soft.assertThat(resourceVersion.findUsageSummaryByUserSeq(viewer)).as("버전 없는 사용자").isEqualTo(new ResourceUsageSummaryData(0, 0, 0));
        soft.assertAll();
    }

    private List<String> versions(Integer user, Long resource, ResourceVersionStatus status, Boolean isDelete, int page, int size) {
        return names(resourceVersion.findAll(ResourceVersionListQuery.builder().userSeq(user).resourceSeq(resource).resourceVersionStatus(status)
                .isDelete(isDelete).page(PaginationQuery.of(page, size)).build()), d -> name(d.getResourceSeq()) + "#" + d.getVersionNumber() + ":" + d.getSizeByte());
    }

    private Long versionCount(Integer user, Long resource, ResourceVersionStatus status, Boolean isDelete) {
        return resourceVersion.countAll(ResourceVersionListQuery.builder().userSeq(user).resourceSeq(resource).resourceVersionStatus(status)
                .isDelete(isDelete).page(PaginationQuery.of(1, 10)).build());
    }

    // ------------------------------------------------------------------ 공유 대상 사용자·nearest·해제 범위

    // 아래 세 조회는 SQL 에 ORDER BY 가 없어(구 구현도 같음) 정렬한 문자열로 비교한다

    @Test
    @DisplayName("ShareTarget 리소스별 대상 사용자 · nearest 조상 대상 · 해제 범위")
    void shareTargetGraphs() {
        SoftAssertions soft = new SoftAssertions();
        // targetUserSeq 는 리소스 파티션(소유자) 키 — 공유자 본인
        soft.assertThat(resourceUsers(ADMIN, ADMIN, List.of(seq(root), seq(deep), seq(cPdf)), false, false)).as("ADMIN 이 공유한 root·deep·c.pdf 대상").isEqualTo(List.of("c.pdf=[viewer]", "deep=[viewer]", "root=[other, viewer]"));
        soft.assertThat(resourceUsers(ADMIN, viewer, List.of(seq(root), seq(deep), seq(cPdf)), false, false)).as("소유자 키가 다르면 없음").isEmpty();
        soft.assertThat(resourceUsers(ADMIN, ADMIN, List.of(seq(gone), seq(top)), false, false)).as("gone(삭제)·top 을 미삭제로").isEqualTo(List.of("top.txt=[other]"));
        soft.assertThat(resourceUsers(ADMIN, ADMIN, List.of(seq(gone), seq(top)), true, false)).as("gone(삭제)·top 을 삭제로").isEqualTo(List.of("gone.txt=[viewer]"));
        soft.assertThat(resourceUsers(ADMIN, null, List.of(seq(root)), false, false)).as("소유자 키 null").isEmpty();
        soft.assertThat(resourceUsers(ADMIN, null, List.of(seq(gone), seq(top)), true, true)).as("삭제된 리소스용 조회").isEqualTo(List.of("null=[other]", "null=[viewer]"));
        soft.assertThat(resourceUsers(ADMIN, null, List.of(), false, false)).as("빈 목록").isEmpty();
        soft.assertThat(resourceUsers(ADMIN, null, null, false, false)).as("null 목록").isEmpty();

        soft.assertThat(nearest(ADMIN, sDeep, false, null)).as("deep 공유의 가까운 조상(root) 대상").isEqualTo(List.of("viewer<-admin"));
        soft.assertThat(nearest(ADMIN, sDeep, false, List.of(viewer))).as("뷰어로 좁힘").isEqualTo(List.of("viewer<-admin"));
        soft.assertThat(nearest(ADMIN, sRoot, false, null)).as("root 공유(조상 없음)").isEqualTo(List.of("other<-admin", "viewer<-admin"));
        soft.assertThat(nearest(other, sO, false, null)).as("다른 사용자 공유").isEqualTo(List.of("admin<-other", "viewer<-other"));

        soft.assertThat(unshared(ADMIN, sRoot, false, List.of(viewer, other))).as("root 공유 해제 — 뷰어·다른 사용자").isEqualTo(List.of("other=[10, 2, 2a.txt, 2b.txt, a, b, c.pdf, deep, leaf.txt, leaf2.pdf, m.docx, root, same1.txt, sub, z.txt]", "viewer=[10, 2, 2a.txt, 2b.txt, a, b, m.docx, root, same1.txt, sub, z.txt]"));
        soft.assertThat(unshared(ADMIN, sDeep, false, List.of(viewer))).as("deep 공유 해제 — 뷰어").isEqualTo(List.of("viewer=[deep, leaf.txt, leaf2.pdf]"));
        soft.assertThat(unshared(ADMIN, sRoot, false, List.of())).as("빈 대상").isEmpty();
        soft.assertAll();
    }

    private List<String> resourceUsers(Integer sharedBy, Integer targetUser, List<Long> list, Boolean isDelete, boolean deletedQuery) {
        ShareTargetResourceUsersQuery q = ShareTargetResourceUsersQuery.builder().sharedByUserSeq(sharedBy).targetUserSeq(targetUser).resourceSeqList(list).isDelete(isDelete).build();
        return names(deletedQuery ? shareTarget.findShareTargetResourceUsersByDeletedResources(q) : shareTarget.findShareTargetResourceUsersByResources(q),
                d -> d.getResourceName() + "=" + d.getTargetUsers().stream().map(this::who).sorted().toList()).stream().sorted().toList();
    }

    private List<String> nearest(int owner, long shareSeq, Boolean isDelete, List<Integer> users) {
        ShareTargetNearestFindQuery q = ShareTargetNearestFindQuery.builder().userSeq(owner).shareSeq(shareSeq).isDelete(isDelete).targetUserSeqList(users).build();
        return names(users == null ? shareTarget.findNearestAncestorShareTargets(q) : shareTarget.findNearestAncestorShareTargetsByUsers(q),
                d -> who(d.getUserSeq()) + "<-" + who(d.getSharedByUserSeq())).stream().sorted().toList();
    }

    private List<String> unshared(int owner, long shareSeq, Boolean isDelete, List<Integer> users) {
        UnsharedTargetsFindQuery q = UnsharedTargetsFindQuery.builder().userSeq(owner).shareSeq(shareSeq).isDelete(isDelete).targetUserSeqList(users).build();
        return names(shareTarget.findUnsharedDescendantsByTargets(q), d -> who(d.getUserSeq()) + "=" + d.getResourceSeqList().stream().map(this::name).sorted().toList()).stream().sorted().toList();
    }

    // ------------------------------------------------------------------ 헬퍼

    /** 커서로 끝까지 읽은 결과가 한 번에 읽은 결과와 같은지. */
    private <R> void walk(String label, Function<CursorPaginationQuery, List<R>> call, Function<R, Object> cursorValue, Function<R, Long> cursorId,
                          Function<R, ResourceType> cursorType, Function<R, String> name, int pageSize) {
        List<R> expected = call.apply(CursorWalk.firstPage());
        List<R> rows = CursorWalk.all(label, call, cursorValue, cursorId, cursorType, pageSize);
        assertThat(names(rows, name)).as(label).isEqualTo(names(expected, name));
        assertThat(rows).as(label).usingRecursiveComparison().isEqualTo(expected);
        walked += expected.size();
    }

    private static <T> List<String> names(List<T> rows, Function<T, String> name) {
        return rows.stream().map(name).toList();
    }

    private String who(Integer userSeq) {
        if (userSeq == null) {
            return "null";
        }
        return userSeq == ADMIN ? "admin" : userSeq == viewer ? "viewer" : userSeq == other ? "other" : String.valueOf(userSeq);
    }

    private String name(Long resourceSeq) {
        Map<Long, String> m = new HashMap<>();
        for (Resource r : List.of(root, fB, fA, f10, f2, sub, deep, leaf, leaf2, zTxt, cPdf, mDoc, same1, n2a, n2b, gone, top, trashRoot, trashChild, trashFile,
                oRoot, oFile, oSameName, oGone)) {
            m.put(r.getResourceSeq(), r.getResourceName());
        }
        return m.getOrDefault(resourceSeq, String.valueOf(resourceSeq));
    }

    private static Long seq(Resource r) {
        return r == null ? null : r.getResourceSeq();
    }

    private static Instant fallback(Instant recent, Instant update) {
        return recent != null ? recent : update;
    }

    /** 공통 필터 — 각 목록 빌더에 끼우는 값 묶음. */
    private record Filter(String name, ResourceType type, FileType fileType, String userId, Instant from, Instant to, Boolean isDelete) {
    }

    private static List<Filter> filters() {
        LocalDate today = LocalDate.now(DriveTime.zone());
        Instant dayFrom = DriveTime.startOfDay(today.minusDays(1));
        Instant dayTo = DriveTime.endExclusive(today.plusDays(1));
        return List.of(
                new Filter("기본", null, null, null, null, null, false),
                new Filter("파일", ResourceType.FILE, null, null, null, null, false),
                new Filter("폴더", ResourceType.FOLDER, null, null, null, null, false),
                new Filter("PDF", null, FileType.PDF, null, null, null, false),
                new Filter("admin", null, null, "admin", null, null, false),
                new Filter("cmp-other", null, null, "cmp-other", null, null, false),
                new Filter("오늘", null, null, null, dayFrom, dayTo, false),
                new Filter("2026~2030", null, null, null, DriveTime.startOfDay(LocalDate.of(2026, 1, 1)), DriveTime.endExclusive(LocalDate.of(2030, 12, 31)), false),
                new Filter("삭제", null, null, null, null, null, true));
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
        return r;
    }

    private Resource file(int userSeq, String name, Resource parent, long size, String ext) {
        Resource r = Resource.builder().userSeq(userSeq).ownerSeq(userSeq).latestOwnerSeq(userSeq).resourceType(ResourceType.FILE)
                .parentFolderSeq(parent == null ? null : parent.getResourceSeq()).resourceName(name).originalFileName(name)
                .copyNumber(0).ext(ext).contentType("application/octet-stream").sizeByte(size)
                .versionRegisterDate(DriveTime.toInstant(java.time.LocalDateTime.of(2026, 1, 1, 0, 0, 0))).registerUserSeq(userSeq).build();
        resourceRepository.save(r);
        r.initResourceSaveName();
        resourceRepository.flush();
        return r;
    }

    private long share(int userSeq, Resource r) {
        return jdbc.queryForObject("INSERT INTO tb_share (user_seq, resource_seq, share_type, register_user_seq) VALUES (?, ?, 'SPECIFIC_USERS', ?) RETURNING share_seq",
                Long.class, userSeq, seq(r), userSeq);
    }

    private void target(int userSeq, long shareSeq, Resource r, int sharedBy, String at) {
        jdbc.update("INSERT INTO tb_share_target (user_seq, share_seq, resource_seq, shared_by_user_seq, share_date, can_view, can_download, register_user_seq) "
                + "VALUES (?, ?, ?, ?, ?, 'Y', 'Y', ?)", userSeq, shareSeq, seq(r), sharedBy, Timestamp.valueOf(at), sharedBy);
    }

    private void star(int userSeq, Resource r, int sharedBy) {
        jdbc.update("INSERT INTO tb_starred (user_seq, resource_seq, shared_by_user_seq, register_user_seq) VALUES (?, ?, ?, ?)", userSeq, seq(r), sharedBy, userSeq);
    }

    private void seen(int userSeq, Resource r, int sharedBy, String at) {
        jdbc.update("INSERT INTO tb_recent (user_seq, resource_seq, shared_by_user_seq, register_user_seq, update_date) VALUES (?, ?, ?, ?, ?)",
                userSeq, seq(r), sharedBy, userSeq, Timestamp.valueOf(at));
    }

    private void trashRow(int userSeq, Resource r, int deletedBy, String at) {
        jdbc.update("INSERT INTO tb_trash (user_seq, resource_seq, register_user_seq, register_date) VALUES (?, ?, ?, ?)", userSeq, seq(r), deletedBy, Timestamp.valueOf(at));
    }

    private void version(int userSeq, Resource r, int number, long size, String status) {
        jdbc.update("INSERT INTO tb_resource_version (user_seq, resource_seq, owner_seq, resource_save_name, version_number, size_byte, status, register_user_seq) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                userSeq, seq(r), userSeq, "k/" + seq(r) + "/v" + number, number, size, status, userSeq);
    }
}
