package com.thinkfree.drive.resource.repository;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.common.CursorWalk;
import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.common.search.enums.FileType;
import com.thinkfree.drive.common.search.enums.SortOrder;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.global.time.DriveTime;
import com.thinkfree.drive.partition.PartitionKey;
import com.thinkfree.drive.resource.data.ResourceData;
import com.thinkfree.drive.resource.data.ResourceDeleteParentData;
import com.thinkfree.drive.resource.data.ResourceHierarchyData;
import com.thinkfree.drive.resource.data.ResourceTokenAccessData;
import com.thinkfree.drive.resource.data.ResourceTreeData;
import com.thinkfree.drive.resource.data.ResourceUsageSummaryData;
import com.thinkfree.drive.resource.data.ResourceVersionHierarchyData;
import com.thinkfree.drive.resource.entity.Resource;
import com.thinkfree.drive.resource.enums.ResourceSortField;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.query.*;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * {@link DefaultResourceQueryRepository}(jOOQ) 기대값 단언 — 10-30 작업 B P4 에서 전환 전/후 비교 테스트를 대신한다(기대값은 비교 테스트로 구 JdbcClient 구현과 같음을 확인한 값). 트랜잭션 롤백.
 * <p>
 * 픽스처(경계): 다른 테넌트의 다른 사용자(파티션 user_seq 가 다름), 3단 폴더 체인 + 두 단계 공유(가까운 조상 선택), 이름 자연정렬·크기·수정일·열람일 동률,
 * 삭제(is_delete)·휴지통 루트, 잠긴 파일, 공유 토큰(미삭제·삭제), 버전 행.
 * findAll 은 대표 정렬 결과를 이름 순서로 단언하고, 정렬 5종 × 방향 × 페이지 크기 × 폴더 × 필터 전 조합은 커서로 끝까지 읽은 결과가 한 번에 읽은 결과와 같은지({@link CursorWalk}) 본다.
 */
@DisplayName("ResourceQueryRepository — 재귀 CTE·커서 목록·조인 기대값(PostgreSQL Testcontainers)")
class DefaultResourceQueryRepositoryTest extends RepositoryTest {

    private static final int ADMIN = 2;
    private static final int SUPER_ADMIN = 1;
    private static final DateTimeFormatter LOCAL_CURSOR = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private ResourceRepository repository;
    @Autowired
    private ResourceQueryRepository query;
    @Autowired
    private JdbcTemplate jdbc;

    private int other;
    private Resource root, fB, fA, f10, f2, sub, deep, leaf, leaf2, zTxt, cPdf, mDoc, same1, n2a, n2b, gone, top;
    private Resource trashRoot, trashChild, trashFile;
    private Resource oRoot, oFile, oSameName;

    @BeforeEach
    void seed() {
        loginAs(ADMIN);

        Integer otherTenant = jdbc.queryForObject("INSERT INTO tb_tenant (tenant_name, register_user_seq) VALUES ('비교 테넌트', ?) RETURNING tenant_seq", Integer.class, SUPER_ADMIN);
        User u = User.createUser("cmp-other", otherTenant, null, SignupType.LOCAL, "다른 사용자", UserStatus.ACTIVE, 0L, SUPER_ADMIN, null, null, null);
        userRepository.insertUserWithSeatCheck(u, false, 0);
        other = u.getUserSeq();

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

        // 삭제·휴지통
        jdbc.update("UPDATE tb_resource SET is_delete = 'Y' WHERE resource_seq = ?", gone.getResourceSeq());
        jdbc.update("UPDATE tb_resource SET is_delete = 'Y' WHERE resource_seq = ?", trashRoot.getResourceSeq());
        jdbc.update("UPDATE tb_resource SET is_delete = 'Y', delete_parent_folder_seq = ? WHERE resource_seq IN (?, ?)", trashRoot.getResourceSeq(), trashChild.getResourceSeq(), trashFile.getResourceSeq());
        jdbc.update("INSERT INTO tb_trash (user_seq, resource_seq, register_user_seq) VALUES (?, ?, ?)", ADMIN, trashRoot.getResourceSeq(), ADMIN);

        // 수정일 동률(파일 3개)·수정일 차이
        Timestamp tie = Timestamp.valueOf("2030-01-01 00:00:00");
        for (Resource r : List.of(zTxt, mDoc, same1)) {
            jdbc.update("UPDATE tb_resource SET update_date = ? WHERE resource_seq = ?", tie, r.getResourceSeq());
        }
        jdbc.update("UPDATE tb_resource SET update_date = ? WHERE resource_seq = ?", Timestamp.valueOf("2029-06-01 00:00:00"), fA.getResourceSeq());
        jdbc.update("UPDATE tb_resource SET update_date = ? WHERE resource_seq = ?", Timestamp.valueOf("2029-06-01 00:00:00"), fB.getResourceSeq());

        // 공유 2단(root·deep) — deep 아래는 가까운 deep 공유, sub 는 root 공유 상속. 다른 사용자 공유
        share(ADMIN, root);
        share(ADMIN, deep);
        share(other, oRoot);

        // 별표·열람(열람일 동률 포함)
        starred(ADMIN, fA);
        starred(ADMIN, cPdf);
        starred(other, oFile);
        recent(ADMIN, zTxt, "2026-06-01 00:00:00");
        recent(ADMIN, cPdf, "2026-05-01 00:00:00");
        recent(ADMIN, mDoc, "2026-05-01 00:00:00");
        recent(ADMIN, f10, "2026-04-01 00:00:00");

        // 잠금·토큰·버전
        jdbc.update("UPDATE tb_resource SET is_locked = 'Y' WHERE resource_seq = ?", leaf.getResourceSeq());
        jdbc.update("UPDATE tb_resource SET share_token = ? WHERE resource_seq = ?", "cmp-tok", cPdf.getResourceSeq());
        jdbc.update("UPDATE tb_resource SET share_token = ? WHERE resource_seq = ?", "cmp-tok-del", gone.getResourceSeq());
        jdbc.update("UPDATE tb_resource SET share_token = ? WHERE resource_seq = ?", "cmp-tok-other", oFile.getResourceSeq());
        version(ADMIN, leaf, 1, 3L);
        version(ADMIN, leaf, 2, 4L);
        version(other, oFile, 1, 8L);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------ 재귀 CTE

    @Test
    @DisplayName("findResourceHierarchy / ByPartitionKeys — 하위 전체·타입·삭제 상태, 빈·null 목록, 다른 사용자 키")
    void hierarchy() {
        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(hierarchy(List.of(seq(root)), ADMIN, null, false)).as("root 하위 전체")
                .isEqualTo(List.of("root:0", "10:1", "2:1", "a:1", "b:1", "sub:1", "2a.txt:1", "2b.txt:1", "c.pdf:1", "m.docx:1", "same1.txt:1", "z.txt:1", "deep:2", "leaf2.pdf:3", "leaf.txt:3"));
        soft.assertThat(hierarchy(List.of(seq(root), seq(sub)), ADMIN, ResourceType.FOLDER, false)).as("타입은 시작점에만 — 겹치는 시작점은 각각 펼침")
                .isEqualTo(List.of("root:0", "sub:0", "10:1", "2:1", "a:1", "b:1", "deep:1", "sub:1", "2a.txt:1", "2b.txt:1", "c.pdf:1", "m.docx:1", "same1.txt:1", "z.txt:1",
                        "deep:2", "leaf2.pdf:2", "leaf.txt:2", "leaf2.pdf:3", "leaf.txt:3"));
        soft.assertThat(hierarchy(List.of(seq(trashRoot)), ADMIN, null, true)).as("휴지통 루트(삭제)").isEqualTo(List.of("trash-root:0", "trash-child:1", "trash-file.txt:2"));
        soft.assertThat(hierarchy(List.of(seq(trashRoot)), ADMIN, null, false)).as("휴지통 루트를 미삭제로").isEmpty();
        soft.assertThat(hierarchy(List.of(seq(oRoot)), ADMIN, null, false)).as("다른 사용자 리소스를 내 키로").isEmpty();
        soft.assertThat(hierarchy(List.of(seq(root)), ADMIN, null, null)).as("삭제 상태 무관 — 삭제된 gone.txt 포함")
                .isEqualTo(List.of("root:0", "10:1", "2:1", "a:1", "b:1", "sub:1", "2a.txt:1", "2b.txt:1", "c.pdf:1", "gone.txt:1", "m.docx:1", "same1.txt:1", "z.txt:1", "deep:2", "leaf2.pdf:3", "leaf.txt:3"));
        soft.assertThat(hierarchy(null, ADMIN, null, false)).as("null 목록").isEmpty();
        soft.assertThat(hierarchy(List.of(), ADMIN, null, false)).as("빈 목록").isEmpty();

        soft.assertThat(names(query.findResourceHierarchyByPartitionKeys(ResourceHierarchyPartitionKeysQuery.builder()
                        .keys(List.of(new PartitionKey(ADMIN, seq(deep)), new PartitionKey(other, seq(oRoot)), new PartitionKey(other, seq(root)))).isDelete(false).build()),
                ResourceHierarchyData::getResourceName)).as("키 여러 사용자 — 맞지 않는 키(other, root)는 빠짐")
                .isEqualTo(List.of("deep", "o-root", "leaf2.pdf", "leaf.txt", "o.txt", "z.txt"));
        soft.assertThat(query.findResourceHierarchyByPartitionKeys(ResourceHierarchyPartitionKeysQuery.builder().keys(List.of()).isDelete(false).build())).as("빈 키").isEmpty();
        soft.assertAll();
    }

    @Test
    @DisplayName("findResourceVersionsByFolderHierarchy · findResourceTree")
    void versionsAndTree() {
        SoftAssertions soft = new SoftAssertions();
        // ORDER BY 없음 — 호출부(DefaultResourceService 이동 검증)는 Set·합계로만 쓴다
        soft.assertThat(versions(seq(root), ADMIN, false)).as("root 아래 리소스·버전")
                .containsExactlyInAnyOrder("root:null", "b:null", "a:null", "10:null", "2:null", "sub:null", "deep:null", "c.pdf:100", "same1.txt:200", "m.docx:200", "z.txt:300",
                        "2a.txt:50", "2b.txt:50", "leaf.txt:5", "leaf2.pdf:6", "leaf.txt+v:3", "leaf.txt+v:4");
        soft.assertThat(versions(seq(trashRoot), ADMIN, true)).as("휴지통 루트 아래").containsExactlyInAnyOrder("trash-root:null", "trash-child:null", "trash-file.txt:4");
        soft.assertThat(versions(seq(oRoot), other, false)).as("다른 사용자").containsExactlyInAnyOrder("o-root:null", "o.txt:70", "z.txt:300", "o.txt+v:8");
        soft.assertThat(versions(seq(oRoot), ADMIN, false)).as("다른 사용자 리소스를 내 키로").isEmpty();
        soft.assertThat(versions(999_999_999L, ADMIN, false)).as("없는 리소스").isEmpty();

        soft.assertThat(tree(ADMIN, false, ResourceType.FOLDER)).as("내 폴더 트리").isEqualTo(List.of("root<null", "2<root", "10<root", "a<root", "b<root", "sub<root", "deep<sub"));
        soft.assertThat(tree(ADMIN, true, null)).as("삭제 상태 true").isEmpty();
        soft.assertThat(tree(other, null, null)).as("삭제 상태 null").isEmpty();
        soft.assertAll();
    }

    @Test
    @DisplayName("isLockedResource — 폴더 트리(잠긴 하위 있음/없음)·파일·없는 리소스·다른 사용자, isLocked true/false/null")
    void isLocked() {
        SoftAssertions soft = new SoftAssertions();
        Map<String, Resource> targets = Map.of("root", root, "sub", sub, "deep", deep, "a", fA, "leaf", leaf, "leaf2", leaf2, "oRoot", oRoot);
        Map<String, String> got = new java.util.TreeMap<>();
        targets.forEach((name, r) -> {
            for (Boolean locked : Arrays.asList(true, false, null)) {
                got.put(name + "/" + locked, String.valueOf(query.isLockedResource(new ResourceLockUnlockQuery(seq(r), ADMIN, locked, null, ADMIN, null))));
            }
        });
        soft.assertThat(got).as("ADMIN 키로 — 리소스 × isLocked(잠금·해제·null) 별 결과(잠긴 파일 leaf 는 deep·sub·root 아래)")
                .isEqualTo(Map.ofEntries(
                        Map.entry("root/true", "true"), Map.entry("root/false", "true"), Map.entry("root/null", "false"),
                        Map.entry("sub/true", "true"), Map.entry("sub/false", "true"), Map.entry("sub/null", "false"),
                        Map.entry("deep/true", "true"), Map.entry("deep/false", "true"), Map.entry("deep/null", "false"),
                        Map.entry("a/true", "false"), Map.entry("a/false", "true"), Map.entry("a/null", "false"),
                        Map.entry("leaf/true", "true"), Map.entry("leaf/false", "true"), Map.entry("leaf/null", "true"),
                        Map.entry("leaf2/true", "false"), Map.entry("leaf2/false", "false"), Map.entry("leaf2/null", "false"),
                        Map.entry("oRoot/true", "false"), Map.entry("oRoot/false", "false"), Map.entry("oRoot/null", "false")));
        soft.assertThat(query.isLockedResource(new ResourceLockUnlockQuery(seq(oRoot), other, true, null, ADMIN, null))).as("다른 사용자 본인 키").isFalse();
        soft.assertThat(query.isLockedResource(new ResourceLockUnlockQuery(999_999_999L, ADMIN, true, null, ADMIN, null))).as("없는 리소스").isFalse();
        soft.assertThat(query.isLockedResource(new ResourceLockUnlockQuery(null, ADMIN, true, null, ADMIN, null))).as("null seq").isFalse();
        soft.assertAll();
    }

    // ------------------------------------------------------------------ 커서 목록

    @Test
    @DisplayName("findAll — 대표 정렬 결과(폴더 먼저·이름 자연정렬·동률 tie-break)")
    void findAllOrder() {
        SoftAssertions soft = new SoftAssertions();
        List<String> byName = List.of("2", "10", "a", "b", "sub", "2a.txt", "2b.txt", "c.pdf", "m.docx", "same1.txt", "z.txt");
        soft.assertThat(names(list(ADMIN, seq(root)), null, null)).as("기본 = 이름 자연정렬").isEqualTo(byName);
        soft.assertThat(names(list(ADMIN, seq(root)), ResourceSortField.NAME, SortOrder.ASC)).as("NAME ASC").isEqualTo(byName);
        soft.assertThat(names(list(ADMIN, seq(root)), ResourceSortField.NAME, SortOrder.DESC)).as("NAME DESC")
                .isEqualTo(List.of("sub", "b", "a", "10", "2", "z.txt", "same1.txt", "m.docx", "c.pdf", "2b.txt", "2a.txt"));
        // 폴더(크기 없음)는 동률 — resource_seq 로 가른다(b·a·10·2·sub 생성 순)
        soft.assertThat(names(list(ADMIN, seq(root)), ResourceSortField.SIZE, SortOrder.ASC)).as("SIZE ASC")
                .isEqualTo(List.of("b", "a", "10", "2", "sub", "2a.txt", "2b.txt", "c.pdf", "m.docx", "same1.txt", "z.txt"));
        soft.assertThat(names(list(ADMIN, seq(root)), ResourceSortField.SIZE, SortOrder.DESC)).as("SIZE DESC")
                .isEqualTo(List.of("sub", "2", "10", "a", "b", "z.txt", "same1.txt", "m.docx", "c.pdf", "2b.txt", "2a.txt"));
        // a·b 는 2029-06-01, z·m·same1 은 2030-01-01 동률, 나머지는 등록 시각
        soft.assertThat(names(list(ADMIN, seq(root)), ResourceSortField.UPDATE, SortOrder.ASC)).as("UPDATE ASC")
                .isEqualTo(List.of("10", "2", "sub", "b", "a", "c.pdf", "2a.txt", "2b.txt", "z.txt", "m.docx", "same1.txt"));
        soft.assertThat(names(list(ADMIN, seq(root)), ResourceSortField.UPDATE, SortOrder.DESC)).as("UPDATE DESC")
                .isEqualTo(List.of("a", "b", "sub", "2", "10", "same1.txt", "m.docx", "z.txt", "2b.txt", "2a.txt", "c.pdf"));
        // 열람일(없으면 수정일) — 열람한 것이 먼저 그룹
        soft.assertThat(names(list(ADMIN, seq(root)), ResourceSortField.OPEN, SortOrder.ASC)).as("OPEN ASC")
                .isEqualTo(List.of("10", "2", "sub", "b", "a", "c.pdf", "m.docx", "z.txt", "2a.txt", "2b.txt", "same1.txt"));
        soft.assertThat(names(list(ADMIN, seq(root)), ResourceSortField.OPEN, SortOrder.DESC)).as("OPEN DESC")
                .isEqualTo(List.of("10", "a", "b", "sub", "2", "z.txt", "m.docx", "c.pdf", "same1.txt", "2b.txt", "2a.txt"));
        soft.assertThat(names(list(ADMIN, null), null, null)).as("내 드라이브 최상위(삭제·휴지통 제외)").isEqualTo(List.of("root", "top.txt"));
        soft.assertThat(names(list(ADMIN, seq(deep)), null, null)).as("deep").isEqualTo(List.of("leaf.txt", "leaf2.pdf"));
        soft.assertThat(names(list(other, seq(oRoot)), null, null)).as("다른 사용자").isEqualTo(List.of("o.txt", "z.txt"));
        soft.assertThat(names(list(ADMIN, seq(oRoot)), null, null)).as("남의 폴더를 내 키로").isEmpty();
        soft.assertAll();
    }

    @Test
    @DisplayName("findAll — 행 필드(소유자·상위 폴더·별표·공유 상속·잠금·열람일)")
    void findAllRow() {
        Map<String, ResourceData> rows = query.findAll(list(ADMIN, seq(deep)).page(CursorWalk.firstPage()).build()).stream()
                .collect(Collectors.toMap(ResourceData::getResourceName, Function.identity()));
        assertThat(rows.get("leaf.txt")).satisfies(d -> {
            assertThat(d.getOwnerId()).isEqualTo("admin");
            assertThat(d.getParentFolderName()).isEqualTo("deep");
            assertThat(d.getIsLocked()).isTrue();
            assertThat(d.getIsShared()).as("가까운 조상(deep) 공유 상속").isTrue();
            assertThat(d.getShareResourceSeq()).isEqualTo(seq(deep));
            assertThat(d.getSizeByte()).isEqualTo(5L);
        });
        Map<String, ResourceData> inRoot = query.findAll(list(ADMIN, seq(root)).page(CursorWalk.firstPage()).build()).stream()
                .collect(Collectors.toMap(ResourceData::getResourceName, Function.identity()));
        assertThat(inRoot.get("a").getIsStarred()).isTrue();
        assertThat(inRoot.get("b").getIsStarred()).isFalse();
        assertThat(inRoot.get("c.pdf").getRecentUpdateDate()).isEqualTo(Timestamp.valueOf("2026-05-01 00:00:00").toInstant());
        assertThat(inRoot.get("sub").getShareResourceSeq()).as("root 공유 상속").isEqualTo(seq(root));
    }

    @Test
    @DisplayName("findAll — 정렬 5종(기본 포함) × 방향 × 페이지 크기 × 폴더 × 사용자, 커서로 끝까지 읽은 결과 = 한 번에 읽은 결과")
    void findAllCursorWalk() {
        int walked = 0;
        for (Long parent : Arrays.asList(null, seq(root), seq(sub), seq(deep), seq(oRoot))) {
            for (Integer user : List.of(ADMIN, other)) {
                for (ResourceSortField sort : Arrays.asList(null, ResourceSortField.NAME, ResourceSortField.SIZE, ResourceSortField.UPDATE, ResourceSortField.OPEN)) {
                    for (SortOrder order : sort == null ? Arrays.asList(null, SortOrder.ASC, SortOrder.DESC) : List.of(SortOrder.ASC, SortOrder.DESC)) {
                        for (int size : new int[]{1, 2, 3}) {
                            walked += sameAsOnePage(list(user, parent).sortBy(sort).sortOrder(order), size, false);
                        }
                    }
                }
            }
        }
        // 날짜 커서를 로컬 형식("yyyy-MM-dd HH:mm:ss" — JooqCursors.timestamp 변환 경로)으로
        for (ResourceSortField sort : List.of(ResourceSortField.UPDATE, ResourceSortField.OPEN)) {
            for (SortOrder order : SortOrder.values()) {
                walked += sameAsOnePage(list(ADMIN, seq(root)).sortBy(sort).sortOrder(order), 2, true);
            }
        }
        assertThat(walked).as("빈 목록만 본 것이 아님").isGreaterThan(200);
    }

    @Test
    @DisplayName("findAll — 필터(확장자·타입·소유자 아이디·수정일 범위)·삭제 목록")
    void findAllFilters() {
        LocalDate today = LocalDate.now(DriveTime.zone());
        List<String> all = List.of("2", "10", "a", "b", "sub", "2a.txt", "2b.txt", "c.pdf", "m.docx", "same1.txt", "z.txt");
        List<String> top2 = List.of("root", "top.txt");
        List<String> none = List.of();
        // 필터 → root 아래 · 최상위 · 휴지통 루트 아래(삭제된 폴더 — isDelete=true 일 때만 보임). txt 는 문서 확장자, 삭제 상태 null 은 FALSE(구 "= NULL")
        List<FilterCase> cases = List.of(
                new FilterCase("PDF", b -> b.fileType(FileType.PDF), List.of("c.pdf"), none, none),
                new FilterCase("문서", b -> b.fileType(FileType.DOCUMENT), List.of("2a.txt", "2b.txt", "m.docx", "same1.txt", "z.txt"), List.of("top.txt"), none),
                new FilterCase("이미지", b -> b.fileType(FileType.IMAGE), none, none, none),
                new FilterCase("파일", b -> b.resourceType(ResourceType.FILE), List.of("2a.txt", "2b.txt", "c.pdf", "m.docx", "same1.txt", "z.txt"), List.of("top.txt"), none),
                new FilterCase("폴더", b -> b.resourceType(ResourceType.FOLDER), List.of("2", "10", "a", "b", "sub"), List.of("root"), none),
                new FilterCase("admin", b -> b.userId("admin"), all, top2, none),
                new FilterCase("nobody", b -> b.userId("nobody"), none, none, none),
                new FilterCase("빈 아이디", b -> b.userId(""), all, top2, none),
                new FilterCase("오늘", b -> b.from(DriveTime.startOfDay(today.minusDays(1))).to(DriveTime.endExclusive(today.plusDays(1))),
                        List.of("2", "10", "sub", "2a.txt", "2b.txt", "c.pdf"), top2, none),
                new FilterCase("2029~2030", b -> b.from(DriveTime.startOfDay(LocalDate.of(2029, 1, 1))).to(DriveTime.endExclusive(LocalDate.of(2030, 12, 31))),
                        List.of("a", "b", "m.docx", "same1.txt", "z.txt"), none, none),
                new FilterCase("from 만(to 없음 → 범위 조건 없음)", b -> b.from(DriveTime.startOfDay(today)), all, top2, none),
                new FilterCase("삭제", b -> b.isDelete(true), List.of("gone.txt"), List.of("trash-root"), List.of("trash-child")),
                new FilterCase("삭제 null", b -> b.isDelete(null), none, none, none));

        SoftAssertions soft = new SoftAssertions();
        for (FilterCase c : cases) {
            soft.assertThat(names(c.filter().apply(list(ADMIN, seq(root))), null, null)).as(c.name() + " root").isEqualTo(c.inRoot());
            soft.assertThat(names(c.filter().apply(list(ADMIN, null)), null, null)).as(c.name() + " 최상위").isEqualTo(c.atTop());
            soft.assertThat(names(c.filter().apply(list(ADMIN, seq(trashRoot))), null, null)).as(c.name() + " 휴지통 루트").isEqualTo(c.inTrashRoot());
            for (ResourceSortField sort : ResourceSortField.values()) {
                sameAsOnePage(c.filter().apply(list(ADMIN, seq(root)).sortBy(sort).sortOrder(SortOrder.DESC)), 2, false);
            }
        }
        soft.assertAll();
    }

    private record FilterCase(String name, Function<ResourceListQuery.ResourceListQueryBuilder, ResourceListQuery.ResourceListQueryBuilder> filter,
                              List<String> inRoot, List<String> atTop, List<String> inTrashRoot) {
    }

    // ------------------------------------------------------------------ 조인·집계·단건

    @Test
    @DisplayName("findByIdAndIsDelete · findResourceByShareToken · findAllByDeleteParentFolderSeq · findUsageSummaryByUserSeq")
    void joinsAndAggregates() {
        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(byId(cPdf, ADMIN, false)).as("내 파일").isEqualTo("c.pdf");
        soft.assertThat(byId(gone, ADMIN, false)).as("삭제된 것을 미삭제로").isNull();
        soft.assertThat(byId(gone, ADMIN, true)).as("삭제된 것").isEqualTo("gone.txt");
        soft.assertThat(byId(gone, ADMIN, null)).as("삭제 상태 null").isNull();
        soft.assertThat(byId(oFile, ADMIN, false)).as("남의 리소스를 내 키로").isNull();
        soft.assertThat(byId(oFile, other, false)).as("다른 사용자 본인").isEqualTo("o.txt");
        soft.assertThat(query.findByIdAndIsDelete(ResourceFindQuery.builder().resourceSeq(999_999_999L).userSeq(ADMIN).isDelete(false).build())).as("없는 리소스").isNull();
        // 소유 계정 노출 상태 — INACTIVE 는 보이고 DELETED 는 안 보임
        jdbc.update("UPDATE tb_user SET status = 'INACTIVE' WHERE user_seq = ?", other);
        soft.assertThat(byId(oFile, other, false)).as("INACTIVE 소유자").isEqualTo("o.txt");
        jdbc.update("UPDATE tb_user SET status = 'DELETED' WHERE user_seq = ?", other);
        soft.assertThat(byId(oFile, other, false)).as("DELETED 소유자").isNull();
        jdbc.update("UPDATE tb_user SET status = 'ACTIVE' WHERE user_seq = ?", other);

        Map<String, String> tokens = new java.util.TreeMap<>();
        for (String token : Arrays.asList("cmp-tok", "cmp-tok-del", "cmp-tok-other", "none", "")) {
            for (boolean isDelete : new boolean[]{false, true}) {
                ResourceTokenAccessData d = query.findResourceByShareToken(token, isDelete);
                tokens.put(token + "/" + isDelete, d == null ? "null" : d.getResourceSeq().equals(seq(cPdf)) ? "c.pdf" : d.getResourceSeq().equals(seq(gone)) ? "gone" : d.getResourceSeq().equals(seq(oFile)) ? "o.txt" : "?");
            }
        }
        soft.assertThat(tokens).as("공유 토큰 — 삭제 여부가 맞을 때만, 다른 사용자 리소스도 토큰으로 찾음")
                .isEqualTo(Map.of("cmp-tok/false", "c.pdf", "cmp-tok/true", "null", "cmp-tok-del/false", "null", "cmp-tok-del/true", "gone",
                        "cmp-tok-other/false", "o.txt", "cmp-tok-other/true", "null", "none/false", "null", "none/true", "null", "/false", "null", "/true", "null"));
        soft.assertThat(query.findResourceByShareToken(null, false)).as("null 토큰").isNull();

        soft.assertThat(names(query.findAllByDeleteParentFolderSeq(ResourceDeleteParentListQuery.builder().resourceSeq(seq(trashRoot)).userSeq(ADMIN).build()),
                ResourceDeleteParentData::getResourceName)).as("휴지통 루트 아래(루트 포함)").containsExactlyInAnyOrder("trash-child", "trash-file.txt", "trash-root");
        soft.assertThat(names(query.findAllByDeleteParentFolderSeq(ResourceDeleteParentListQuery.builder().resourceSeq(seq(trashRoot)).userSeq(other).build()),
                ResourceDeleteParentData::getResourceName)).as("휴지통 루트를 다른 사용자 키로 — 루트 행 자체는 사용자 조건 없이 나옴").isEqualTo(List.of("trash-root"));
        soft.assertThat(query.findAllByDeleteParentFolderSeq(ResourceDeleteParentListQuery.builder().resourceSeq(seq(root)).userSeq(ADMIN).build())).as("삭제 부모 아님").isEmpty();
        soft.assertThat(query.findAllByDeleteParentFolderSeq(ResourceDeleteParentListQuery.builder().build())).as("null").isEmpty();

        soft.assertThat(query.findUsageSummaryByUserSeq(ADMIN)).as("ADMIN 사용량 — 미삭제 파일 합·삭제 파일(gone 9 + trash-file 4) 합, 버전 행은 제외").isEqualTo(new ResourceUsageSummaryData(925, 912, 13));
        soft.assertThat(query.findUsageSummaryByUserSeq(other)).as("다른 사용자 사용량").isEqualTo(new ResourceUsageSummaryData(370, 370, 0));
        soft.assertThat(query.findUsageSummaryByUserSeq(999_999)).as("없는 사용자").isEqualTo(new ResourceUsageSummaryData(0, 0, 0));
        soft.assertAll();
    }

    // ------------------------------------------------------------------ 헬퍼

    private List<String> hierarchy(List<Long> seqs, Integer user, ResourceType type, Boolean isDelete) {
        return query.findResourceHierarchy(ResourceHierarchyQuery.builder().resourceSeqList(seqs).userSeq(user).resourceType(type).isDelete(isDelete).build()).stream()
                .map(d -> d.getResourceName() + ":" + d.getDepth()).toList();
    }

    private List<String> versions(Long start, Integer user, Boolean isDelete) {
        Map<Long, String> byName = nameBySeq();
        return query.findResourceVersionsByFolderHierarchy(ResourceVersionHierarchyQuery.builder().resourceSeq(start).userSeq(user).isDelete(isDelete).build()).stream()
                .map(d -> byName.get(d.getResourceSeq()) + (d.getResourceVersionSeq() == null ? "" : "+v") + ":" + d.getSizeByte()).toList();
    }

    private List<String> tree(Integer user, Boolean isDelete, ResourceType type) {
        Map<Long, String> byName = nameBySeq();
        return query.findResourceTree(ResourceFindQuery.builder().userSeq(user).isDelete(isDelete).resourceType(type).build()).stream()
                .map(d -> d.getResourceName() + "<" + byName.get(d.getParentFolderSeq())).toList();
    }

    private Map<Long, String> nameBySeq() {
        Map<Long, String> m = new java.util.HashMap<>();
        for (Resource r : List.of(root, fB, fA, f10, f2, sub, deep, leaf, leaf2, zTxt, cPdf, mDoc, same1, n2a, n2b, gone, top, trashRoot, trashChild, trashFile, oRoot, oFile, oSameName)) {
            m.put(r.getResourceSeq(), r.getResourceName());
        }
        return m;
    }

    private String byId(Resource r, Integer user, Boolean isDelete) {
        ResourceData d = query.findByIdAndIsDelete(ResourceFindQuery.builder().resourceSeq(seq(r)).userSeq(user).isDelete(isDelete).build());
        return d == null ? null : d.getResourceName();
    }

    private List<String> names(ResourceListQuery.ResourceListQueryBuilder b, ResourceSortField sort, SortOrder order) {
        return names(query.findAll(b.sortBy(sort).sortOrder(order).page(CursorWalk.firstPage()).build()), ResourceData::getResourceName);
    }

    private static <T> List<String> names(List<T> rows, Function<T, String> name) {
        return rows.stream().map(name).toList();
    }

    /** 커서로 끝까지 읽은 결과가 한 번에 읽은 결과와 같은지 — 읽은 행 수를 돌려준다. */
    private int sameAsOnePage(ResourceListQuery.ResourceListQueryBuilder b, int pageSize, boolean localCursor) {
        ResourceListQuery one = b.page(CursorWalk.firstPage()).build();
        String label = describe(one) + " size=" + pageSize;
        List<ResourceData> expected = query.findAll(one);
        List<ResourceData> walked = CursorWalk.all(label, p -> query.findAll(b.page(p).build()),
                d -> cursorValue(one.getSortBy(), d, localCursor), ResourceData::getResourceSeq, ResourceData::getResourceType, pageSize);
        assertThat(names(walked, ResourceData::getResourceName)).as(label).isEqualTo(names(expected, ResourceData::getResourceName));
        assertThat(walked).as(label).usingRecursiveComparison().isEqualTo(expected);
        return expected.size();
    }

    private static Object cursorValue(ResourceSortField sort, ResourceData last, boolean local) {
        if (sort == null || sort == ResourceSortField.NAME) {
            return last.getResourceName();
        }
        return switch (sort) {
            case SIZE -> last.getSizeCursorValue();
            case UPDATE -> local ? LocalDateTime.ofInstant(last.getUpdateDate(), DriveTime.zone()).format(LOCAL_CURSOR) : last.getUpdateDate();
            case OPEN -> {
                java.time.Instant at = last.getRecentUpdateDate() != null ? last.getRecentUpdateDate() : last.getUpdateDate();
                yield local ? LocalDateTime.ofInstant(at, DriveTime.zone()).format(LOCAL_CURSOR) : at;
            }
            default -> throw new IllegalStateException();
        };
    }

    private static String describe(ResourceListQuery q) {
        return "user=" + q.getUserSeq() + " parent=" + q.getParentFolderSeq() + " del=" + q.getIsDelete() + " sort=" + q.getSortBy() + "/" + q.getSortOrder()
                + " type=" + q.getResourceType() + " ext=" + q.getFileType() + " uid=" + q.getUserId() + " from=" + q.getFrom() + " to=" + q.getTo();
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

    private ResourceListQuery.ResourceListQueryBuilder list(int userSeq, Long parentSeq) {
        return ResourceListQuery.builder().userSeq(userSeq).requestUserSeq(userSeq).parentFolderSeq(parentSeq).isDelete(false);
    }

    private Resource folder(int userSeq, String name, Resource parent) {
        Resource r = Resource.builder().userSeq(userSeq).ownerSeq(userSeq).resourceType(ResourceType.FOLDER)
                .parentFolderSeq(parent == null ? null : parent.getResourceSeq()).resourceName(name).originalFileName(name)
                .fullPathSeq(parent == null ? "" : parent.getFullPathSeq()).fullPath(parent == null ? "" : parent.getFullPath())
                .copyNumber(0).registerUserSeq(userSeq).build();
        repository.save(r);
        r.initFolderPath();
        repository.flush();
        return r;
    }

    private Resource file(int userSeq, String name, Resource parent, long size, String ext) {
        Resource r = Resource.builder().userSeq(userSeq).ownerSeq(userSeq).latestOwnerSeq(userSeq).resourceType(ResourceType.FILE)
                .parentFolderSeq(parent == null ? null : parent.getResourceSeq()).resourceName(name).originalFileName(name)
                .copyNumber(0).ext(ext).contentType("application/octet-stream").sizeByte(size)
                .versionRegisterDate(DriveTime.toInstant(LocalDateTime.of(2026, 1, 1, 0, 0, 0))).registerUserSeq(userSeq).build();
        repository.save(r);
        r.initResourceSaveName();
        repository.flush();
        return r;
    }

    private void share(int userSeq, Resource r) {
        jdbc.update("INSERT INTO tb_share (user_seq, resource_seq, share_type, register_user_seq) VALUES (?, ?, 'LINK', ?)", userSeq, r.getResourceSeq(), userSeq);
    }

    private void starred(int userSeq, Resource r) {
        jdbc.update("INSERT INTO tb_starred (user_seq, resource_seq, shared_by_user_seq, register_user_seq) VALUES (?, ?, ?, ?)", userSeq, r.getResourceSeq(), userSeq, userSeq);
    }

    private void recent(int userSeq, Resource r, String at) {
        jdbc.update("INSERT INTO tb_recent (user_seq, resource_seq, shared_by_user_seq, register_user_seq, update_date) VALUES (?, ?, ?, ?, ?)",
                userSeq, r.getResourceSeq(), userSeq, userSeq, Timestamp.valueOf(at));
    }

    private void version(int userSeq, Resource r, int number, long size) {
        jdbc.update("INSERT INTO tb_resource_version (user_seq, resource_seq, owner_seq, resource_save_name, version_number, size_byte, status, register_user_seq) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?)",
                userSeq, r.getResourceSeq(), userSeq, "k/" + r.getResourceSeq() + "/v" + number, number, size, userSeq);
    }
}
