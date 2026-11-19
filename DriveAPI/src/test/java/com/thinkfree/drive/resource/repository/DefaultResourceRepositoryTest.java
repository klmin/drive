package com.thinkfree.drive.resource.repository;

import com.thinkfree.drive.global.time.DriveTime;
import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.common.RepositoryTest;
import jakarta.persistence.EntityManager;
import com.thinkfree.drive.common.search.enums.FileType;
import com.thinkfree.drive.common.search.enums.SortOrder;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.partition.PartitionKey;
import com.thinkfree.drive.resource.data.*;
import com.thinkfree.drive.resource.entity.Resource;
import com.thinkfree.drive.resource.enums.ResourceSortField;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.query.*;
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
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * 2단계 Resource 이관 회귀 — 읽기({@link DefaultResourceQueryRepository} 19 statement)·쓰기({@link DefaultResourceRepository} 25 statement) — 2차 패스(10-07)로 INSERT·고정 컬럼 UPDATE·DELETE·엔티티 조회는 JPA, 동적·목록·CTE 는 JdbcClient.
 * 시드 = tenant 1, user 1 superadmin·2 admin(tenant 1)·3 anonymous. tb_resource 시드 없음. 트랜잭션 롤백.
 * 검증 포인트 = INSERT 키 주입·감사, 엔티티/DTO 매핑(Y/N·enum·LocalDateTime), findAll 의 정렬 4종·커서 게이트·필터·조상 공유 상속, 재귀 CTE 계층 3종,
 * 복사본 번호 FOR UPDATE 집계, 삭제/복원 경로 치환(UPDATE … FROM·SUBSTRING), 잠금 상태(파일/폴더 트리), 버전 갱신/되돌리기(낙관적 조건), IN 삭제·빈 목록 가드.
 */
@DisplayName("DefaultResourceRepository / DefaultResourceQueryRepository — JPA + JdbcClient(PostgreSQL Testcontainers)")
class DefaultResourceRepositoryTest extends RepositoryTest {

    private static final int ADMIN = 2;
    private static final int SUPER_ADMIN = 1;

    @Autowired
    private ResourceRepository repository;
    @Autowired
    private ResourceQueryRepository queryRepository;
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

    /** 폴더: 저장 후 initFolderPath 로 full_path(_seq) 를 "{부모}{seq}/" 형태로 확정(서비스와 동일 순서). */
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

    private <T> T col(long resourceSeq, String column, Class<T> type) {
        return jdbc.queryForObject("SELECT " + column + " FROM tb_resource WHERE resource_seq = ?", type, resourceSeq);
    }

    private static ResourceFindQuery find(long resourceSeq, int userSeq, ResourceType type, Boolean isDelete) {
        return ResourceFindQuery.builder().resourceSeq(resourceSeq).userSeq(userSeq).resourceType(type).isDelete(isDelete).build();
    }

    private static ResourceListQuery.ResourceListQueryBuilder list(int userSeq, Long parentSeq, int pageSize) {
        return ResourceListQuery.builder().userSeq(userSeq).requestUserSeq(userSeq).parentFolderSeq(parentSeq).isDelete(false)
                .page(CursorPaginationQuery.of(null, null, null, pageSize));
    }

    private static CursorPaginationQuery cursor(ResourceData last, Object value, int pageSize) {
        return CursorPaginationQuery.of(value, last.getResourceSeq(), last.getResourceType(), pageSize);
    }

    // ================================================================== INSERT·단건

    @Nested
    @DisplayName("INSERT · 엔티티 단건")
    class InsertAndFind {

        @Test
        @DisplayName("save/saveFolder — identity 키 주입, register_* 감사(비면 컨텍스트), DB 기본값, findDetail(파티션 JPQL 생성자 식 DTO)")
        void saveAndFind() {
            Resource folder = folder(ADMIN, "root", null);
            assertThat(folder.getResourceSeq()).isPositive();
            assertThat(folder.getFullPathSeq()).isEqualTo(folder.getResourceSeq() + "/");
            assertThat(folder.getFullPath()).isEqualTo("root/");

            Resource noAudit = Resource.builder().userSeq(ADMIN).ownerSeq(ADMIN).resourceType(ResourceType.FOLDER).resourceName("no-audit").originalFileName("no-audit").copyNumber(0).build();
            repository.save(noAudit);
            assertThat(col(noAudit.getResourceSeq(), "register_user_seq", Integer.class)).as("JdbcAudit → 컨텍스트 ADMIN").isEqualTo(ADMIN);
            assertThat(noAudit.getRegisterUserSeq()).as("인터셉터처럼 엔티티에도 반영").isEqualTo(ADMIN);
            assertThat(noAudit.getRegisterDate()).isNotNull();
            assertThat(col(noAudit.getResourceSeq(), "is_delete", String.class)).isEqualTo("N");
            assertThat(col(noAudit.getResourceSeq(), "current_version_number", Integer.class)).isEqualTo(1);

            Resource f = file(ADMIN, "doc.txt", folder, 10L, "txt");
            ResourceDetailData found = queryRepository.findDetail(find(f.getResourceSeq(), ADMIN, ResourceType.FILE, false));
            assertThat(found).isNotNull();
            assertThat(found.getResourceSeq()).isEqualTo(f.getResourceSeq());
            assertThat(found.getOwnerSeq()).isEqualTo(ADMIN);
            assertThat(found.getLatestOwnerSeq()).isEqualTo(ADMIN);
            assertThat(found.getResourceType()).isEqualTo(ResourceType.FILE);
            assertThat(found.getParentFolderSeq()).isEqualTo(folder.getResourceSeq());
            assertThat(found.getResourceSaveName()).isEqualTo(ADMIN + "/" + f.getResourceSeq());
            assertThat(found.getCurrentVersionNumber()).isEqualTo(1);
            assertThat(found.getVersionRegisterDate()).isEqualTo(DriveTime.toInstant(LocalDateTime.of(2026, 1, 1, 0, 0, 0)));
            assertThat(found.getCopyNumber()).isZero();
            assertThat(found.getIsDelete()).isFalse();
            assertThat(found.getExt()).isEqualTo("txt");
            assertThat(found.getSizeByte()).isEqualTo(10L);
            assertThat(found.getRegisterUserSeq()).isEqualTo(ADMIN);
            assertThat(found.getRegisterDate()).isNotNull();
            assertThat(found.getUpdateUserSeq()).as("updateResourceSaveName 감사").isEqualTo(ADMIN);

            assertThat(queryRepository.findDetail(find(f.getResourceSeq(), ADMIN, ResourceType.FOLDER, null))).as("타입 불일치").isNull();
            assertThat(queryRepository.findDetail(find(f.getResourceSeq(), ADMIN, null, true))).as("is_delete 불일치").isNull();
            assertThat(queryRepository.findDetail(find(f.getResourceSeq(), SUPER_ADMIN, null, null))).as("다른 소유자").isNull();
        }

        @Test
        @DisplayName("소유 계정 가시성 — INACTIVE 는 보이고 DELETED 는 안 보인다(findDetail·findByIdAndIsDelete)")
        void ownerVisibility() {
            User u = User.createUser("res-owner", 1, null, SignupType.LOCAL, "소유자", UserStatus.ACTIVE, 0L, SUPER_ADMIN, null, null, null);
            userRepository.insertUserWithSeatCheck(u, false, 0);
            Resource f = file(u.getUserSeq(), "mine.txt", null, 1L, "txt");

            assertThat(queryRepository.findDetail(find(f.getResourceSeq(), u.getUserSeq(), null, null))).isNotNull();
            ResourceData data = queryRepository.findByIdAndIsDelete(find(f.getResourceSeq(), u.getUserSeq(), null, false));
            assertThat(data.getOwnerId()).isEqualTo("res-owner");
            assertThat(data.getOwnerStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(data.getOwnerTenantName()).isEqualTo("기본 테넌트");
            assertThat(data.getRegisterUserId()).isEqualTo("admin");
            assertThat(data.getUpdateUserId()).as("updateResourceSaveName 감사 = 컨텍스트 사용자").isEqualTo("admin");
            assertThat(data.getIsLocked()).isFalse();
            assertThat(data.getParentFolderName()).isNull();

            jdbc.update("UPDATE tb_user SET status = 'INACTIVE' WHERE user_seq = ?", u.getUserSeq());
            assertThat(queryRepository.findDetail(find(f.getResourceSeq(), u.getUserSeq(), null, null))).isNotNull();
            jdbc.update("UPDATE tb_user SET status = 'DELETED' WHERE user_seq = ?", u.getUserSeq());
            assertThat(queryRepository.findDetail(find(f.getResourceSeq(), u.getUserSeq(), null, null))).isNull();
            assertThat(queryRepository.findByIdAndIsDelete(find(f.getResourceSeq(), u.getUserSeq(), null, false))).isNull();
        }

        @Test
        @DisplayName("findDetail(DTO)은 같은 트랜잭션의 관리 엔티티를 떼어내지 않고 DB 현재 값 — JDBC 로 바뀐 크기를 본다(엔티티 반환 시절엔 캐시의 옛 인스턴스를 돌려주고 detach)")
        void detailReadsDbValueWithoutTouchingManagedEntity() {
            Resource f = file(ADMIN, "fresh.txt", null, 10L, "txt");
            Resource managed = entityManager.createQuery("select r from Resource r where r.userSeq = :u and r.resourceSeq = :s", Resource.class)
                    .setParameter("u", ADMIN).setParameter("s", f.getResourceSeq()).getSingleResult();
            assertThat(managed.getSizeByte()).isEqualTo(10L);

            jdbc.update("UPDATE tb_resource SET size_byte = 77 WHERE user_seq = ? AND resource_seq = ?", ADMIN, f.getResourceSeq());

            ResourceDetailData detail = queryRepository.findDetail(find(f.getResourceSeq(), ADMIN, null, null));
            assertThat(detail.getSizeByte()).as("DB 현재 값").isEqualTo(77L);
            assertThat(entityManager.contains(managed)).as("DTO 조회가 관리 엔티티를 떼어내지 않음").isTrue();
            assertThat(managed.getSizeByte()).as("관리 엔티티는 JDBC 변경을 모름(캐시) — DTO 결과와 무관").isEqualTo(10L);
        }

        @Test
        @DisplayName("existsResourceName/findResourceByExists — 루트(NULL 부모)·하위 폴더, 공유 토큰 3종")
        void existsAndTokens() {
            Resource root = folder(ADMIN, "ex", null);
            Resource f = file(ADMIN, "a.txt", root, 1L, "txt");
            Resource top = file(ADMIN, "top.txt", null, 1L, "txt");

            ResourceExistsQuery inFolder = ResourceExistsQuery.builder().userSeq(ADMIN).parentFolderSeq(root.getResourceSeq()).originalFileName("a.txt").resourceType(ResourceType.FILE).isDelete(false).build();
            assertThat(queryRepository.existsResourceName(inFolder)).isTrue();
            assertThat(queryRepository.findResourceByExists(inFolder).getResourceSeq()).isEqualTo(f.getResourceSeq());
            ResourceExistsQuery atRoot = ResourceExistsQuery.builder().userSeq(ADMIN).originalFileName("top.txt").resourceType(ResourceType.FILE).isDelete(false).build();
            assertThat(queryRepository.findResourceByExists(atRoot).getResourceSeq()).isEqualTo(top.getResourceSeq());
            assertThat(queryRepository.existsResourceName(ResourceExistsQuery.builder().userSeq(ADMIN).originalFileName("a.txt").resourceType(ResourceType.FILE).isDelete(false).build())).as("루트에는 a.txt 없음").isFalse();

            assertThat(queryRepository.existsByShareToken("tok-1")).isFalse();
            f.applyShareToken("tok-1");   // W10 — 관리 엔티티 변경 감지(구 updateShareToken JPQL)
            repository.flush();
            assertThat(queryRepository.existsByShareToken("tok-1")).isTrue();
            ResourceTokenAccessData access = queryRepository.findResourceByShareToken("tok-1", false);
            assertThat(access.getResourceSeq()).isEqualTo(f.getResourceSeq());
            assertThat(access.getTenantSeq()).isEqualTo(1);
            assertThat(access.getResourceType()).isEqualTo(ResourceType.FILE);
            assertThat(queryRepository.findResourceByShareToken("tok-1", true)).isNull();
            ResourceLinkTokenData link = queryRepository.findShareTokenForLink(ADMIN, f.getResourceSeq());
            assertThat(link.getShareToken()).isEqualTo("tok-1");
            assertThat(link.getTenantSeq()).isEqualTo(1);
        }
    }

    // ================================================================== findAll

    @Nested
    @DisplayName("findAll — 정렬·커서·필터·공유 상속")
    class FindAll {

        private Resource root;
        private Resource fB, fA, f10, f2;
        private Resource zTxt, cPdf, mDoc;

        @BeforeEach
        void seed() {
            root = folder(ADMIN, "root", null);
            fB = folder(ADMIN, "b", root);
            fA = folder(ADMIN, "a", root);
            f10 = folder(ADMIN, "10", root);
            f2 = folder(ADMIN, "2", root);
            zTxt = file(ADMIN, "z.txt", root, 300L, "txt");
            cPdf = file(ADMIN, "c.pdf", root, 100L, "pdf");
            mDoc = file(ADMIN, "m.docx", root, 200L, "docx");
        }

        private List<Long> seqs(List<ResourceData> rows) {
            return rows.stream().map(ResourceData::getResourceSeq).toList();
        }

        @Test
        @DisplayName("기본 정렬(폴더 먼저·자연정렬) + 매핑 + NAME ASC 커서 2페이지")
        void defaultSortAndNameCursor() {
            List<ResourceData> all = queryRepository.findAll(list(ADMIN, root.getResourceSeq(), 10).build());
            assertThat(seqs(all)).containsExactly(f2.getResourceSeq(), f10.getResourceSeq(), fA.getResourceSeq(), fB.getResourceSeq(), cPdf.getResourceSeq(), mDoc.getResourceSeq(), zTxt.getResourceSeq());
            ResourceData first = all.get(0);
            assertThat(first.getParentFolderName()).isEqualTo("root");
            assertThat(first.getFullPath()).isEqualTo("root/");
            assertThat(first.getOwnerId()).isEqualTo("admin");
            assertThat(first.getIsStarred()).isFalse();
            assertThat(first.getIsShared()).isFalse();
            assertThat(first.getCanView()).isTrue();
            assertThat(first.getCanShare()).isTrue();
            assertThat(first.getShareTargetSeq()).isNull();
            assertThat(first.getRegisterDate()).isNotNull();
            assertThat(first.getRecentUpdateDate()).as("열람 없으면 updateDate fallback").isEqualTo(first.getUpdateDate());

            List<ResourceData> page1 = queryRepository.findAll(list(ADMIN, root.getResourceSeq(), 3).sortBy(ResourceSortField.NAME).sortOrder(SortOrder.ASC).build());
            assertThat(seqs(page1)).containsExactly(f2.getResourceSeq(), f10.getResourceSeq(), fA.getResourceSeq());
            ResourceData last = page1.get(2);
            List<ResourceData> page2 = queryRepository.findAll(list(ADMIN, root.getResourceSeq(), 3).sortBy(ResourceSortField.NAME).sortOrder(SortOrder.ASC)
                    .page(cursor(last, last.getResourceName(), 3)).build());
            assertThat(seqs(page2)).containsExactly(fB.getResourceSeq(), cPdf.getResourceSeq(), mDoc.getResourceSeq());
            ResourceData last2 = page2.get(2);
            List<ResourceData> page3 = queryRepository.findAll(list(ADMIN, root.getResourceSeq(), 3).sortBy(ResourceSortField.NAME).sortOrder(SortOrder.ASC)
                    .page(cursor(last2, last2.getResourceName(), 3)).build());
            assertThat(seqs(page3)).containsExactly(zTxt.getResourceSeq());

            List<ResourceData> desc = queryRepository.findAll(list(ADMIN, root.getResourceSeq(), 10).sortBy(ResourceSortField.NAME).sortOrder(SortOrder.DESC).build());
            assertThat(seqs(desc)).containsExactly(fB.getResourceSeq(), fA.getResourceSeq(), f10.getResourceSeq(), f2.getResourceSeq(), zTxt.getResourceSeq(), mDoc.getResourceSeq(), cPdf.getResourceSeq());
        }

        @Test
        @DisplayName("SIZE DESC 커서(파일 구간)·UPDATE 정렬 커서·OPEN 정렬(열람 행 먼저)")
        void sizeUpdateOpenSort() {
            List<ResourceData> size = queryRepository.findAll(list(ADMIN, root.getResourceSeq(), 10).sortBy(ResourceSortField.SIZE).sortOrder(SortOrder.DESC).build());
            assertThat(seqs(size).subList(4, 7)).containsExactly(zTxt.getResourceSeq(), mDoc.getResourceSeq(), cPdf.getResourceSeq());
            ResourceData cursorRow = size.get(4);
            List<ResourceData> afterZ = queryRepository.findAll(list(ADMIN, root.getResourceSeq(), 10).sortBy(ResourceSortField.SIZE).sortOrder(SortOrder.DESC)
                    .page(cursor(cursorRow, cursorRow.getSizeByte(), 10)).build());
            assertThat(seqs(afterZ)).containsExactly(mDoc.getResourceSeq(), cPdf.getResourceSeq());

            jdbc.update("UPDATE tb_resource SET update_date = ? WHERE resource_seq = ?", java.sql.Timestamp.valueOf("2030-05-01 00:00:00"), cPdf.getResourceSeq());
            List<ResourceData> upd = queryRepository.findAll(list(ADMIN, root.getResourceSeq(), 10).sortBy(ResourceSortField.UPDATE).sortOrder(SortOrder.DESC).build());
            assertThat(seqs(upd).get(4)).as("가장 최근 수정 파일 먼저").isEqualTo(cPdf.getResourceSeq());
            ResourceData firstFile = upd.get(4);
            List<ResourceData> afterFirst = queryRepository.findAll(list(ADMIN, root.getResourceSeq(), 10).sortBy(ResourceSortField.UPDATE).sortOrder(SortOrder.DESC)
                    .page(cursor(firstFile, firstFile.getUpdateDate().toString(), 10)).build());
            assertThat(seqs(afterFirst)).doesNotContain(cPdf.getResourceSeq()).hasSize(2);

            jdbc.update("INSERT INTO tb_recent (user_seq, resource_seq, shared_by_user_seq, register_user_seq, update_date) VALUES (?, ?, ?, ?, ?)",
                    ADMIN, zTxt.getResourceSeq(), ADMIN, ADMIN, java.sql.Timestamp.valueOf("2026-06-01 00:00:00"));
            List<ResourceData> open = queryRepository.findAll(list(ADMIN, root.getResourceSeq(), 10).sortBy(ResourceSortField.OPEN).sortOrder(SortOrder.DESC).build());
            assertThat(seqs(open).get(4)).as("열람 기록 있는 파일이 먼저").isEqualTo(zTxt.getResourceSeq());
            assertThat(open.get(4).getRecentUpdateDate()).isEqualTo(DriveTime.toInstant(LocalDateTime.of(2026, 6, 1, 0, 0, 0)));
            ResourceData opened = open.get(4);
            List<ResourceData> afterOpened = queryRepository.findAll(list(ADMIN, root.getResourceSeq(), 10).sortBy(ResourceSortField.OPEN).sortOrder(SortOrder.DESC)
                    .page(cursor(opened, opened.getRecentUpdateDate().toString(), 10)).build());
            assertThat(seqs(afterOpened)).as("미열람 2개가 뒤 페이지").containsExactlyInAnyOrder(cPdf.getResourceSeq(), mDoc.getResourceSeq());
        }

        @Test
        @DisplayName("필터 — 확장자(FileType)·resourceType·소유자 아이디·수정일 범위, 루트(NULL 부모) 목록")
        void filters() {
            assertThat(seqs(queryRepository.findAll(list(ADMIN, root.getResourceSeq(), 10).fileType(FileType.PDF).build()))).containsExactly(cPdf.getResourceSeq());
            assertThat(seqs(queryRepository.findAll(list(ADMIN, root.getResourceSeq(), 10).resourceType(ResourceType.FILE).build()))).hasSize(3);
            assertThat(seqs(queryRepository.findAll(list(ADMIN, root.getResourceSeq(), 10).userId("admin").build()))).hasSize(7);
            assertThat(seqs(queryRepository.findAll(list(ADMIN, root.getResourceSeq(), 10).userId("nobody").build()))).isEmpty();
            assertThat(seqs(queryRepository.findAll(list(ADMIN, root.getResourceSeq(), 10).from(DriveTime.startOfDay(LocalDate.now(DriveTime.zone()).minusDays(1))).to(DriveTime.endExclusive(LocalDate.now(DriveTime.zone()).plusDays(1))).build()))).hasSize(7);
            assertThat(seqs(queryRepository.findAll(list(ADMIN, root.getResourceSeq(), 10).from(DriveTime.startOfDay(LocalDate.of(2000, 1, 1))).to(DriveTime.endExclusive(LocalDate.of(2000, 1, 2))).build()))).isEmpty();
            assertThat(seqs(queryRepository.findAll(list(ADMIN, null, 10).build()))).as("루트 목록").containsExactly(root.getResourceSeq());
        }

        @Test
        @DisplayName("공유 상속 — 상위 폴더 공유면 하위 is_shared, share_seq 는 공유 리소스 자신에만, 별표 조인")
        void shareInheritanceAndStarred() {
            jdbc.update("INSERT INTO tb_share (user_seq, resource_seq, share_type, register_user_seq) VALUES (?, ?, 'LINK', ?)", ADMIN, root.getResourceSeq(), ADMIN);
            jdbc.update("INSERT INTO tb_starred (user_seq, resource_seq, shared_by_user_seq, register_user_seq) VALUES (?, ?, ?, ?)", ADMIN, fA.getResourceSeq(), ADMIN, ADMIN);
            Long shareSeq = jdbc.queryForObject("SELECT share_seq FROM tb_share WHERE resource_seq = ?", Long.class, root.getResourceSeq());

            List<ResourceData> rootList = queryRepository.findAll(list(ADMIN, null, 10).build());
            assertThat(rootList.get(0).getIsShared()).isTrue();
            assertThat(rootList.get(0).getShareSeq()).isEqualTo(shareSeq);
            assertThat(rootList.get(0).getShareResourceSeq()).isEqualTo(root.getResourceSeq());

            List<ResourceData> children = queryRepository.findAll(list(ADMIN, root.getResourceSeq(), 10).build());
            assertThat(children).allSatisfy(d -> {
                assertThat(d.getIsShared()).as("조상 공유 상속").isTrue();
                assertThat(d.getShareSeq()).as("자기 공유 아님").isNull();
                assertThat(d.getShareResourceSeq()).isEqualTo(root.getResourceSeq());
            });
            ResourceData starred = children.stream().filter(d -> d.getResourceSeq().equals(fA.getResourceSeq())).findFirst().orElseThrow();
            assertThat(starred.getIsStarred()).isTrue();
            assertThat(starred.getStarredSeq()).isNotNull();
        }
    }

    // ================================================================== 계층·기타 조회

    @Nested
    @DisplayName("계층 CTE · 기타 조회")
    class Hierarchy {

        @Test
        @DisplayName("findResourceHierarchy(+PartitionKeys) — 깊이·경로 조립·폴더 '/' 접미, isDelete 필터, 빈 목록 가드")
        void hierarchy() {
            Resource root = folder(ADMIN, "h", null);
            Resource sub = folder(ADMIN, "sub", root);
            Resource f = file(ADMIN, "deep.txt", sub, 1L, "txt");
            Resource deleted = file(ADMIN, "gone.txt", sub, 1L, "txt");
            jdbc.update("UPDATE tb_resource SET is_delete = 'Y' WHERE resource_seq = ?", deleted.getResourceSeq());

            List<ResourceHierarchyData> h = queryRepository.findResourceHierarchy(ResourceHierarchyQuery.builder().resourceSeqList(List.of(root.getResourceSeq())).userSeq(ADMIN).isDelete(false).build());
            assertThat(h).extracting(ResourceHierarchyData::getResourceSeq).containsExactly(root.getResourceSeq(), sub.getResourceSeq(), f.getResourceSeq());
            assertThat(h).extracting(ResourceHierarchyData::getFullPath).containsExactly("h/", "h/sub/", "h/sub/deep.txt");
            assertThat(h).extracting(ResourceHierarchyData::getDepth).containsExactly(0, 1, 2);
            assertThat(h.get(2).getResourceSaveName()).isEqualTo(ADMIN + "/" + f.getResourceSeq());
            assertThat(h.get(0).getIsFolder()).isTrue();

            List<ResourceHierarchyData> all = queryRepository.findResourceHierarchy(ResourceHierarchyQuery.builder().resourceSeqList(List.of(root.getResourceSeq())).userSeq(ADMIN).build());
            assertThat(all).as("isDelete 미지정이면 삭제분 포함").hasSize(4);
            assertThat(queryRepository.findResourceHierarchy(ResourceHierarchyQuery.builder().resourceSeqList(List.of()).userSeq(ADMIN).build())).isEmpty();

            List<ResourceHierarchyData> byKeys = queryRepository.findResourceHierarchyByPartitionKeys(ResourceHierarchyPartitionKeysQuery.builder()
                    .keys(List.of(new PartitionKey(ADMIN, sub.getResourceSeq()), new PartitionKey(SUPER_ADMIN, root.getResourceSeq()))).isDelete(false).build());
            assertThat(byKeys).extracting(ResourceHierarchyData::getResourceSeq).as("다른 user_seq 키는 매치 안 됨").containsExactly(sub.getResourceSeq(), f.getResourceSeq());
            assertThat(byKeys.get(0).getFullPath()).isEqualTo("sub/");
        }

        @Test
        @DisplayName("버전 계층·트리·경로 하위·휴지통 하위·저장 키·사용량 요약")
        void otherReads() {
            Resource root = folder(ADMIN, "o", null);
            Resource sub = folder(ADMIN, "o-sub", root);
            Resource f = file(ADMIN, "v.txt", sub, 7L, "txt");
            jdbc.update("INSERT INTO tb_resource_version (user_seq, resource_seq, owner_seq, resource_save_name, version_number, size_byte, status, register_user_seq) VALUES (?, ?, ?, 'k/v1', 1, 3, 'ACTIVE', ?)", ADMIN, f.getResourceSeq(), ADMIN, ADMIN);

            List<ResourceVersionHierarchyData> versions = queryRepository.findResourceVersionsByFolderHierarchy(ResourceVersionHierarchyQuery.builder().resourceSeq(root.getResourceSeq()).userSeq(ADMIN).isDelete(false).build());
            assertThat(versions).hasSize(4);
            assertThat(versions.stream().filter(ResourceVersionHierarchyData::hasVersion).toList()).singleElement().satisfies(v -> {
                assertThat(v.getResourceSeq()).isEqualTo(f.getResourceSeq());
                assertThat(v.getSizeByte()).isEqualTo(3L);
            });

            List<ResourceTreeData> tree = queryRepository.findResourceTree(ResourceFindQuery.builder().userSeq(ADMIN).isDelete(false).resourceType(ResourceType.FOLDER).build());
            assertThat(tree).extracting(ResourceTreeData::getResourceSeq).containsExactly(root.getResourceSeq(), sub.getResourceSeq());
            assertThat(tree.get(1).getParentFolderSeq()).isEqualTo(root.getResourceSeq());

            List<ResourcePathData> under = queryRepository.findFolderAndDescendants(new ResourcePathQuery(ADMIN, root.getFullPathSeq(), false));
            assertThat(under).extracting(ResourcePathData::getResourceSeq).as("full_path_seq 를 가진 폴더만(파일은 NULL)").containsExactlyInAnyOrder(root.getResourceSeq(), sub.getResourceSeq());

            jdbc.update("UPDATE tb_resource SET is_delete = 'Y' WHERE resource_seq = ?", root.getResourceSeq());
            jdbc.update("UPDATE tb_resource SET is_delete = 'Y', delete_parent_folder_seq = ? WHERE resource_seq IN (?, ?)", root.getResourceSeq(), sub.getResourceSeq(), f.getResourceSeq());
            jdbc.update("INSERT INTO tb_trash (user_seq, resource_seq, register_user_seq) VALUES (?, ?, ?)", ADMIN, root.getResourceSeq(), ADMIN);
            List<ResourceDeleteParentData> trashed = queryRepository.findAllByDeleteParentFolderSeq(ResourceDeleteParentListQuery.builder().resourceSeq(root.getResourceSeq()).userSeq(ADMIN).build());
            assertThat(trashed).extracting(ResourceDeleteParentData::getResourceSeq).containsExactlyInAnyOrder(sub.getResourceSeq(), f.getResourceSeq(), root.getResourceSeq());
            assertThat(trashed.stream().filter(ResourceDeleteParentData::isFile).toList()).singleElement().satisfies(d -> assertThat(d.getResourceSaveName()).isEqualTo(ADMIN + "/" + f.getResourceSeq()));

            List<ResourceStorageKeyData> keys = queryRepository.findStorageKeysByUserSeqList(List.of(ADMIN));
            assertThat(keys).extracting(ResourceStorageKeyData::getResourceSaveName).contains(ADMIN + "/" + f.getResourceSeq());
            assertThat(queryRepository.findStorageKeysByUserSeqList(List.of())).isEmpty();

            ResourceUsageSummaryData usage = queryRepository.findUsageSummaryByUserSeq(ADMIN);
            assertThat(usage.totalBytes()).isEqualTo(7L);
            assertThat(usage.trashBytes()).isEqualTo(7L);
            assertThat(usage.liveBytes()).isZero();
            assertThat(queryRepository.findUsageSummaryByUserSeq(99999).totalBytes()).isZero();
        }
    }

    // ================================================================== 쓰기

    @Nested
    @DisplayName("쓰기 — 복사본 번호 잠금·버전·이름/이동·삭제/복원·잠금·토큰·IN 삭제")
    class Writes {

        @Test
        @DisplayName("lockMaxCopyNumbers(자기 제외·부모 NULL/값) · lockResourceForVersioning(PK — @Lock 생성자 식 DTO, W10)")
        void locks() {
            Resource root = folder(ADMIN, "L", null);
            Resource a = file(ADMIN, "a.txt", root, 1L, "txt");
            Resource a1 = file(ADMIN, "a (1).txt", root, 1L, "txt");
            a1.changeCopyResource("a (1).txt", "a.txt", 1);
            Resource a2 = file(ADMIN, "a (2).txt", root, 1L, "txt");
            a2.changeCopyResource("a (2).txt", "a.txt", 2);
            repository.flush();   // 관리 엔티티 변경 감지 — JdbcClient 집계 전에 내보냄

            ResourceCopyNumbers n = repository.lockMaxCopyNumbers(ResourceLockQuery.builder().userSeq(ADMIN).parentFolderSeq(root.getResourceSeq()).originalFileName("a.txt").resourceType(ResourceType.FILE).isDelete(false).build());
            assertThat(n.resourceNameMax()).as("표시 이름이 a.txt 인 행의 copy_number").isZero();
            assertThat(n.originalFileNameMax()).as("원본 이름이 a.txt 인 행 최대").isEqualTo(2);
            ResourceCopyNumbers excluding = repository.lockMaxCopyNumbers(ResourceLockQuery.builder().userSeq(ADMIN).parentFolderSeq(root.getResourceSeq()).originalFileName("a.txt").resourceType(ResourceType.FILE).isDelete(false).resourceSeq(a2.getResourceSeq()).build());
            assertThat(excluding.originalFileNameMax()).isEqualTo(1);
            ResourceCopyNumbers none = repository.lockMaxCopyNumbers(ResourceLockQuery.builder().userSeq(ADMIN).originalFileName("a.txt").resourceType(ResourceType.FILE).isDelete(false).build());
            assertThat(none.resourceNameMax()).as("루트엔 없음").isNull();

            ResourceDetailData byPk = repository.lockResourceForVersioning(a.getResourceSeq(), ADMIN);
            assertThat(byPk.getResourceSeq()).isEqualTo(a.getResourceSeq());
            assertThat(byPk.getResourceSaveName()).isEqualTo(ADMIN + "/" + a.getResourceSeq());
            assertThat(byPk.getCurrentVersionNumber()).isEqualTo(1);
            assertThat(byPk.getOwnerSeq()).as("W10 — 부분 엔티티가 아니라 전 컬럼 DTO").isEqualTo(ADMIN);
            assertThat(repository.lockResourceForVersioning(99999L, ADMIN)).isNull();
            assertThat(repository.lockResourceForVersioning(a.getResourceSeq(), SUPER_ADMIN)).as("파티션 키 불일치").isNull();
        }

        @Test
        @DisplayName("버전 — 버전 갱신·현재 버전(관리 엔티티 변경 감지, W10)·rollbackResourceVersionData(<set> 조건)·overwrite/revertOverwrite(낙관적 조건)")
        void versions() {
            Resource f = file(ADMIN, "ver.txt", null, 10L, "txt");
            Instant vDate = DriveTime.toInstant(LocalDateTime.of(2026, 2, 2, 2, 2, 2));
            f.applyVersionUpdate(false, SUPER_ADMIN, 20L, SUPER_ADMIN, vDate);
            repository.flush();
            assertThat(col(f.getResourceSeq(), "current_version_number", Integer.class)).isEqualTo(2);
            assertThat(col(f.getResourceSeq(), "size_byte", Long.class)).isEqualTo(20L);
            assertThat(col(f.getResourceSeq(), "latest_owner_seq", Integer.class)).isEqualTo(SUPER_ADMIN);
            assertThat(col(f.getResourceSeq(), "update_user_seq", Integer.class)).as("시큐리티 사용자 우선(선언 값 덮어씀)").isEqualTo(ADMIN);
            assertThat(col(f.getResourceSeq(), "update_date", Object.class)).as("감사가 채움").isNotNull();

            f.decreaseVersionNumber();
            repository.flush();
            assertThat(col(f.getResourceSeq(), "current_version_number", Integer.class)).isEqualTo(1);
            assertThat(col(f.getResourceSeq(), "update_user_seq", Integer.class)).as("비면 컨텍스트 ADMIN").isEqualTo(ADMIN);

            jdbc.update("UPDATE tb_resource SET update_user_seq = NULL, update_date = NULL WHERE resource_seq = ?", f.getResourceSeq());
            assertThat(repository.rollbackResourceVersionData(ResourceVersionRollbackQuery.builder().resourceSeq(f.getResourceSeq()).userSeq(ADMIN).latestOwnerSeq(ADMIN)
                    .currentVersionNumber(3).versionRegisterDate(vDate).sizeByte(5L).build())).isEqualTo(1);
            assertThat(col(f.getResourceSeq(), "current_version_number", Integer.class)).isEqualTo(3);
            assertThat(col(f.getResourceSeq(), "update_user_seq", Integer.class)).as("updateUserSeq null 이면 SET 안 함").isNull();
            assertThat(repository.rollbackResourceVersionData(ResourceVersionRollbackQuery.builder().resourceSeq(f.getResourceSeq()).userSeq(ADMIN).latestOwnerSeq(ADMIN)
                    .currentVersionNumber(3).versionRegisterDate(vDate).sizeByte(5L).updateUserSeq(SUPER_ADMIN).updateDate(vDate).build())).isEqualTo(1);
            assertThat(col(f.getResourceSeq(), "update_user_seq", Integer.class)).isEqualTo(SUPER_ADMIN);
            assertThat(repository.rollbackResourceVersionData(ResourceVersionRollbackQuery.builder().resourceSeq(f.getResourceSeq()).userSeq(ADMIN).latestOwnerSeq(ADMIN)
                    .currentVersionNumber(3).versionRegisterDate(vDate).sizeByte(5L).restoreUpdateMetadata(true).build())).isEqualTo(1);
            assertThat(col(f.getResourceSeq(), "update_user_seq", Integer.class)).as("저장 실패 롤백은 저장 전 NULL 도 그대로 복원").isNull();
            assertThat(col(f.getResourceSeq(), "update_date", Object.class)).isNull();
            assertThat(repository.rollbackResourceVersionData(ResourceVersionRollbackQuery.builder().resourceSeq(f.getResourceSeq()).userSeq(SUPER_ADMIN).latestOwnerSeq(ADMIN)
                    .currentVersionNumber(9).versionRegisterDate(vDate).sizeByte(5L).build())).as("5차 패스 D5 — WHERE 에 파티션 키 user_seq, 다른 소유자는 0").isZero();
            assertThat(repository.rollbackResourceVersionData(ResourceVersionRollbackQuery.builder().resourceSeq(f.getResourceSeq()).userSeq(ADMIN).latestOwnerSeq(ADMIN)
                    .currentVersionNumber(3).versionRegisterDate(vDate).sizeByte(5L).updateUserSeq(SUPER_ADMIN).updateDate(vDate).build())).isEqualTo(1);

            Instant before = queryRepository.findDetail(find(f.getResourceSeq(), ADMIN, null, null)).getUpdateDate();
            String baseKey = col(f.getResourceSeq(), "resource_save_name", String.class);
            assertThat(repository.overwrite(ADMIN, f.getResourceSeq(), 50L, "2/ow", "stale-key")).as("base key 불일치(그 사이 내용 바뀜)는 null").isNull();
            var stamp = repository.overwrite(ADMIN, f.getResourceSeq(), 50L, "2/ow", baseKey);
            assertThat(stamp).as("W10 — 쓴 감사 값 반환").isNotNull();
            assertThat(stamp.userSeq()).as("시큐리티 사용자").isEqualTo(ADMIN);
            assertThat(col(f.getResourceSeq(), "size_byte", Long.class)).isEqualTo(50L);
            assertThat(col(f.getResourceSeq(), "update_date", java.sql.Timestamp.class).toInstant()).as("반환 stamp = DB 에 쓴 수정일").isEqualTo(stamp.date());
            assertThat(col(f.getResourceSeq(), "resource_save_name", String.class)).isEqualTo("2/ow");
            assertThat(repository.overwrite(SUPER_ADMIN, f.getResourceSeq(), 60L, "2/ow2", "2/ow")).as("없는 행(파티션 키 불일치)은 null").isNull();

            Instant currentDate = stamp.date();
            Instant restoreDate = before;
            assertThat(repository.revertOverwrite(new ResourceRevertOverWriteQuery(ADMIN, f.getResourceSeq(), 5L, SUPER_ADMIN, restoreDate, 999L, ADMIN, currentDate))).as("현재 값 불일치").isZero();
            assertThat(repository.revertOverwrite(new ResourceRevertOverWriteQuery(ADMIN, f.getResourceSeq(), 5L, SUPER_ADMIN, restoreDate, 50L, ADMIN, currentDate))).isEqualTo(1);
            assertThat(col(f.getResourceSeq(), "size_byte", Long.class)).isEqualTo(5L);
        }

        @Test
        @DisplayName("이름 변경·이동(관리 엔티티 변경 감지 — 소유자(파티션 키) 변경 포함, W10)·updateFullPathForChildFolders(seq 경로 선택·자기 제외)·updateUserSeqForResources")
        void renameMovePath() {
            Resource root = folder(ADMIN, "mv", null);
            Resource sub = folder(ADMIN, "sub", root);
            Resource f = file(ADMIN, "f.txt", sub, 1L, "txt");
            Resource other = folder(ADMIN, "other", null);

            sub.changeCopyResource("sub2", "sub2", 0);
            sub.replaceFullPath(sub.getFullPath());
            repository.flush();
            assertThat(col(sub.getResourceSeq(), "resource_name", String.class)).isEqualTo("sub2");
            assertThat(col(sub.getResourceSeq(), "full_path", String.class)).isEqualTo("mv/sub2/");

            String oldSeqPath = root.getFullPathSeq();
            String newSeqPath = other.getFullPathSeq();
            assertThat(repository.updateFullPathForChildFolders(ResourcePathUpdateQuery.builder().resourceSeq(root.getResourceSeq()).userSeq(ADMIN)
                    .oldFullPath("mv/").newFullPath("other/mv/").oldFullPathSeq(oldSeqPath).newFullPathSeq(newSeqPath + root.getResourceSeq() + "/").isDelete(false).build())).as("자기 제외 → 하위 폴더 1").isEqualTo(1);
            assertThat(col(sub.getResourceSeq(), "full_path", String.class)).isEqualTo("other/mv/sub2/");
            assertThat(col(sub.getResourceSeq(), "full_path_seq", String.class)).isEqualTo(newSeqPath + root.getResourceSeq() + "/" + sub.getResourceSeq() + "/");
            assertThat(col(sub.getResourceSeq(), "update_user_seq", Integer.class)).isEqualTo(ADMIN);

            root.moveToParentFolder(other.getResourceSeq());
            root.changeFolderPath("other/mv/", newSeqPath + root.getResourceSeq() + "/");
            repository.flush();
            assertThat(col(root.getResourceSeq(), "parent_folder_seq", Long.class)).isEqualTo(other.getResourceSeq());
            assertThat(col(root.getResourceSeq(), "full_path", String.class)).isEqualTo("other/mv/");
            assertThat(col(root.getResourceSeq(), "update_user_seq", Integer.class)).isEqualTo(ADMIN);

            // 소유자 변경 = 파티션 키 변경도 변경 감지(구 moveToOtherUser JdbcClient — 계획 부록 D-5)
            f.changeUserSeq(SUPER_ADMIN);
            repository.flush();
            assertThat(col(f.getResourceSeq(), "user_seq", Integer.class)).as("파티션 키 user_seq 변경 → 행이 다른 파티션으로").isEqualTo(SUPER_ADMIN);
            assertThat(repository.findByUserSeqAndResourceSeq(SUPER_ADMIN, f.getResourceSeq())).as("새 소유자 파티션에서 같은 인스턴스").isSameAs(f);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_resource WHERE user_seq = ? AND resource_seq = ?", Integer.class, ADMIN, f.getResourceSeq())).as("이전 파티션엔 없음").isZero();

            entityManager.clear();   // 아래 벌크 JPQL 이 캐시를 안 고치므로 비우고 확인(쥔 엔티티를 다시 쓰지 않음)
            assertThat(repository.updateUserSeqForResources(ResourcesUserUpdateQuery.builder().userSeq(SUPER_ADMIN).changeUserSeq(ADMIN).resourceSeqList(List.of(f.getResourceSeq())).build())).isEqualTo(1);
            assertThat(col(f.getResourceSeq(), "user_seq", Integer.class)).isEqualTo(ADMIN);
            assertThat(repository.updateUserSeqForResources(ResourcesUserUpdateQuery.builder().userSeq(ADMIN).changeUserSeq(SUPER_ADMIN).resourceSeqList(List.of()).build())).isZero();
        }

        @Test
        @DisplayName("삭제/복원 — 파일 휴지통(변경 감지)·updateIsDeleteByFullPathSeq(UPDATE … FROM)·복원(잠금 로드 + 변경 감지, W10)·restoreChildResources(파일 경로 NULL)·delete 3종")
        void deleteAndRestore() {
            Resource root = folder(ADMIN, "del", null);
            Resource sub = folder(ADMIN, "sub", root);
            Resource f = file(ADMIN, "f.txt", sub, 1L, "txt");
            Resource lone = file(ADMIN, "lone.txt", null, 1L, "txt");

            lone.applyDeleteState(Boolean.TRUE);
            repository.flush();
            assertThat(col(lone.getResourceSeq(), "is_delete", String.class)).isEqualTo("Y");

            assertThat(repository.updateIsDeleteByFullPathSeq(ResourceDeleteStateQuery.builder().resourceSeq(root.getResourceSeq()).userSeq(ADMIN).isDelete(true).fullPathSeq(root.getFullPathSeq()).build())).isEqualTo(3);
            assertThat(col(root.getResourceSeq(), "delete_parent_folder_seq", Long.class)).as("자기 자신은 NULL").isNull();
            assertThat(col(sub.getResourceSeq(), "delete_parent_folder_seq", Long.class)).isEqualTo(root.getResourceSeq());
            assertThat(col(f.getResourceSeq(), "delete_parent_folder_seq", Long.class)).isEqualTo(root.getResourceSeq());
            assertThat(col(f.getResourceSeq(), "is_delete", String.class)).isEqualTo("Y");

            // 실제 복원은 다른 요청(새 트랜잭션) — 위 JdbcClient 변경을 모르는 캐시를 비우고 잠금 로드(첫 로드)
            entityManager.clear();
            Resource toRestore = repository.lockVisibleByUserSeqAndResourceSeq(ADMIN, root.getResourceSeq());
            assertThat(toRestore.getIsDelete()).as("잠금 로드 = DB 현재 값").isTrue();
            toRestore.changeCopyResource("del (1)", "del", 1);
            toRestore.changeFolderPath("del (1)/", root.getFullPathSeq());
            toRestore.restore();
            repository.flush();
            assertThat(col(root.getResourceSeq(), "is_delete", String.class)).isEqualTo("N");
            assertThat(col(root.getResourceSeq(), "resource_name", String.class)).isEqualTo("del (1)");
            assertThat(col(root.getResourceSeq(), "update_user_seq", Integer.class)).as("Auditing — 시큐리티 사용자").isEqualTo(ADMIN);

            assertThat(repository.restoreChildResources(ResourceRestoreChildQuery.builder().resourceSeq(root.getResourceSeq()).userSeq(ADMIN)
                    .oldFullPath("del/").newFullPath("del (1)/").oldFullPathSeq(root.getFullPathSeq()).newFullPathSeq(root.getFullPathSeq()).build())).isEqualTo(2);
            assertThat(col(sub.getResourceSeq(), "full_path", String.class)).isEqualTo("del (1)/sub/");
            assertThat(col(sub.getResourceSeq(), "is_delete", String.class)).isEqualTo("N");
            assertThat(col(f.getResourceSeq(), "full_path", String.class)).as("파일은 경로 NULL").isNull();
            assertThat(col(f.getResourceSeq(), "delete_parent_folder_seq", Long.class)).isNull();

            assertThat(repository.delete(ResourceDeleteQuery.builder().resourceSeq(lone.getResourceSeq()).userSeq(ADMIN).build())).isEqualTo(1);
            assertThat(repository.deleteByResourceSeqList(ResourceDeleteSeqListQuery.builder().userSeq(ADMIN).resourceSeqList(List.of(f.getResourceSeq(), sub.getResourceSeq())).build())).isEqualTo(2);
            assertThat(repository.deleteByResourceSeqList(ResourceDeleteSeqListQuery.builder().userSeq(ADMIN).resourceSeqList(List.of()).build())).isZero();
            assertThat(repository.deleteByUserSeqList(List.of())).isZero();
            assertThat(repository.deleteByUserSeqList(List.of(ADMIN))).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_resource WHERE user_seq = ?", Integer.class, ADMIN)).isZero();
        }

        @Test
        @DisplayName("하위 일괄 UPDATE 경계(W10d — JdbcClient → JPQL 전후 같은 결과): 하위 경로 치환(소유자 변경·seq 경로 없음·삭제 필터·다른 트리)·하위 복원(seq 경로 없음)·폴더 휴지통(손자·이미 삭제·다른 트리)·롤백(값 하나만)")
        void childBulkUpdateEdges() {
            // 하위 경로 치환 — 소유자 변경(파티션 이동)·자기 제외·삭제 행 제외·다른 트리 무관
            Resource root = folder(ADMIN, "a", null);
            Resource sub = folder(ADMIN, "b", root);
            Resource subsub = folder(ADMIN, "c", sub);
            Resource deletedChild = folder(ADMIN, "x", root);
            Resource otherTree = folder(ADMIN, "z", null);
            jdbc.update("UPDATE tb_resource SET is_delete = 'Y' WHERE resource_seq = ?", deletedChild.getResourceSeq());
            String rootSeq = root.getFullPathSeq();
            entityManager.clear();

            assertThat(repository.updateFullPathForChildFolders(ResourcePathUpdateQuery.builder().resourceSeq(root.getResourceSeq()).userSeq(ADMIN)
                    .oldFullPath("a/").newFullPath("m/a/").oldFullPathSeq(rootSeq).newFullPathSeq("9/" + rootSeq).changeUserSeq(SUPER_ADMIN).isDelete(false).build()))
                    .as("자기·삭제 행·다른 트리 제외 → b·c").isEqualTo(2);
            assertThat(col(sub.getResourceSeq(), "user_seq", Integer.class)).as("하위 소유자 변경 = 다른 파티션으로").isEqualTo(SUPER_ADMIN);
            assertThat(col(subsub.getResourceSeq(), "user_seq", Integer.class)).isEqualTo(SUPER_ADMIN);
            assertThat(col(subsub.getResourceSeq(), "full_path", String.class)).isEqualTo("m/a/b/c/");
            assertThat(col(subsub.getResourceSeq(), "full_path_seq", String.class)).isEqualTo("9/" + subsub.getFullPathSeq());
            assertThat(col(sub.getResourceSeq(), "update_user_seq", Integer.class)).as("감사 = 시큐리티 사용자").isEqualTo(ADMIN);
            assertThat(col(root.getResourceSeq(), "user_seq", Integer.class)).as("자기 행 제외").isEqualTo(ADMIN);
            assertThat(col(root.getResourceSeq(), "full_path", String.class)).isEqualTo("a/");
            assertThat(col(deletedChild.getResourceSeq(), "full_path", String.class)).as("삭제 행 제외").isEqualTo("a/x/");
            assertThat(col(deletedChild.getResourceSeq(), "user_seq", Integer.class)).isEqualTo(ADMIN);
            assertThat(col(otherTree.getResourceSeq(), "full_path", String.class)).isEqualTo("z/");

            // 이름 변경처럼 seq 경로가 안 바뀌면(new 없음) full_path_seq·user_seq 그대로, full_path 만 치환
            Resource r2 = folder(ADMIN, "r", null);
            Resource r2c = folder(ADMIN, "s", r2);
            entityManager.clear();
            assertThat(repository.updateFullPathForChildFolders(ResourcePathUpdateQuery.builder().resourceSeq(r2.getResourceSeq()).userSeq(ADMIN)
                    .oldFullPath("r/").newFullPath("rr/").oldFullPathSeq(r2.getFullPathSeq()).isDelete(false).build())).isEqualTo(1);
            assertThat(col(r2c.getResourceSeq(), "full_path", String.class)).isEqualTo("rr/s/");
            assertThat(col(r2c.getResourceSeq(), "full_path_seq", String.class)).as("seq 경로 유지").isEqualTo(r2c.getFullPathSeq());
            assertThat(col(r2c.getResourceSeq(), "user_seq", Integer.class)).isEqualTo(ADMIN);

            // 폴더 휴지통 — 손자까지(부모 경로 접두), 이미 삭제된 행은 그대로(delete_parent 유지), 다른 트리 무관
            Resource d = folder(ADMIN, "d", null);
            Resource d1 = folder(ADMIN, "d1", d);
            Resource d2 = folder(ADMIN, "d2", d1);
            Resource d2f = file(ADMIN, "d2f.txt", d2, 1L, "txt");
            Resource dGone = file(ADMIN, "gone.txt", d1, 1L, "txt");
            Resource e = folder(ADMIN, "e", null);
            Resource ef = file(ADMIN, "ef.txt", e, 1L, "txt");
            jdbc.update("UPDATE tb_resource SET is_delete = 'Y', delete_parent_folder_seq = NULL WHERE resource_seq = ?", dGone.getResourceSeq());
            entityManager.clear();
            assertThat(repository.updateIsDeleteByFullPathSeq(ResourceDeleteStateQuery.builder().resourceSeq(d.getResourceSeq()).userSeq(ADMIN).isDelete(true)
                    .fullPathSeq(d.getFullPathSeq()).build())).as("d·d1·d2·d2f").isEqualTo(4);
            assertThat(col(d2f.getResourceSeq(), "is_delete", String.class)).isEqualTo("Y");
            assertThat(col(d2f.getResourceSeq(), "delete_parent_folder_seq", Long.class)).isEqualTo(d.getResourceSeq());
            assertThat(col(d2.getResourceSeq(), "delete_parent_folder_seq", Long.class)).isEqualTo(d.getResourceSeq());
            assertThat(col(d.getResourceSeq(), "delete_parent_folder_seq", Long.class)).isNull();
            assertThat(col(dGone.getResourceSeq(), "delete_parent_folder_seq", Long.class)).as("이미 삭제 — 건드리지 않음").isNull();
            assertThat(col(ef.getResourceSeq(), "is_delete", String.class)).isEqualTo("N");
            assertThat(col(e.getResourceSeq(), "is_delete", String.class)).isEqualTo("N");

            // 하위 복원 — seq 경로가 없으면 full_path_seq 그대로(파일도), full_path 는 파일 NULL·폴더 치환
            assertThat(repository.restoreChildResources(ResourceRestoreChildQuery.builder().resourceSeq(d.getResourceSeq()).userSeq(ADMIN)
                    .oldFullPath("d/").newFullPath("d (1)/").build())).as("d1·d2·d2f").isEqualTo(3);
            assertThat(col(d2.getResourceSeq(), "full_path", String.class)).isEqualTo("d (1)/d1/d2/");
            assertThat(col(d2.getResourceSeq(), "full_path_seq", String.class)).isEqualTo(d2.getFullPathSeq());
            assertThat(col(d2f.getResourceSeq(), "full_path", String.class)).isNull();
            assertThat(col(d2f.getResourceSeq(), "is_delete", String.class)).isEqualTo("N");
            assertThat(col(d2f.getResourceSeq(), "update_user_seq", Integer.class)).isEqualTo(ADMIN);
            assertThat(col(dGone.getResourceSeq(), "is_delete", String.class)).as("delete_parent 가 다르면 복원 안 됨").isEqualTo("Y");
            assertThat(col(d.getResourceSeq(), "is_delete", String.class)).as("자기 행은 대상 아님(변경 감지)").isEqualTo("Y");

            // 롤백(미완료 버전 정리) — 수정일만 있으면 수정자는 그대로
            Resource v = file(ADMIN, "v.txt", null, 1L, "txt");
            Instant vDate = DriveTime.toInstant(LocalDateTime.of(2026, 3, 3, 3, 3, 3));
            jdbc.update("UPDATE tb_resource SET update_user_seq = 7 WHERE resource_seq = ?", v.getResourceSeq());
            entityManager.clear();
            assertThat(repository.rollbackResourceVersionData(ResourceVersionRollbackQuery.builder().resourceSeq(v.getResourceSeq()).userSeq(ADMIN).latestOwnerSeq(ADMIN)
                    .currentVersionNumber(4).versionRegisterDate(vDate).sizeByte(8L).updateDate(vDate).build())).isEqualTo(1);
            assertThat(col(v.getResourceSeq(), "update_user_seq", Integer.class)).isEqualTo(7);
            assertThat(col(v.getResourceSeq(), "update_date", java.sql.Timestamp.class).toInstant()).isEqualTo(vDate);
            assertThat(col(v.getResourceSeq(), "size_byte", Long.class)).isEqualTo(8L);
        }

        @Test
        @DisplayName("255자 초과 seq 경로(full_path_seq text, 10-14) — 깊은 폴더 저장·하위 경로 치환(이동)·폴더 휴지통·하위 복원")
        void deepSeqPathOver255() {
            Resource root = folder(ADMIN, "deep", null);
            String longPrefix = "1/".repeat(150);
            root.changeFolderPath("deep/", longPrefix + root.getResourceSeq() + "/");
            repository.flush();
            Resource sub = folder(ADMIN, "s", root);
            Resource subsub = folder(ADMIN, "t", sub);
            Resource f = file(ADMIN, "f.txt", subsub, 1L, "txt");
            assertThat(col(subsub.getResourceSeq(), "full_path_seq", String.class)).as("저장").isEqualTo(subsub.getFullPathSeq()).hasSizeGreaterThan(255);
            entityManager.clear();

            String movedPrefix = "2/".repeat(200);
            assertThat(repository.updateFullPathForChildFolders(ResourcePathUpdateQuery.builder().resourceSeq(root.getResourceSeq()).userSeq(ADMIN)
                    .oldFullPath("deep/").newFullPath("x/deep/").oldFullPathSeq(root.getFullPathSeq()).newFullPathSeq(movedPrefix + root.getResourceSeq() + "/").isDelete(false).build()))
                    .as("이동 — 하위 s·t").isEqualTo(2);
            String movedSubsub = movedPrefix + root.getResourceSeq() + "/" + sub.getResourceSeq() + "/" + subsub.getResourceSeq() + "/";
            assertThat(col(subsub.getResourceSeq(), "full_path_seq", String.class)).isEqualTo(movedSubsub).hasSizeGreaterThan(400);
            assertThat(col(subsub.getResourceSeq(), "full_path", String.class)).isEqualTo("x/deep/s/t/");

            String rootMoved = movedPrefix + root.getResourceSeq() + "/";
            jdbc.update("UPDATE tb_resource SET full_path_seq = ?, full_path = 'x/deep/' WHERE resource_seq = ?", rootMoved, root.getResourceSeq());
            assertThat(repository.updateIsDeleteByFullPathSeq(ResourceDeleteStateQuery.builder().resourceSeq(root.getResourceSeq()).userSeq(ADMIN).isDelete(true)
                    .fullPathSeq(rootMoved).build())).as("휴지통 — 자기·s·t·f").isEqualTo(4);
            assertThat(col(f.getResourceSeq(), "delete_parent_folder_seq", Long.class)).isEqualTo(root.getResourceSeq());

            assertThat(repository.restoreChildResources(ResourceRestoreChildQuery.builder().resourceSeq(root.getResourceSeq()).userSeq(ADMIN)
                    .oldFullPath("x/deep/").newFullPath("deep/").oldFullPathSeq(rootMoved).newFullPathSeq(longPrefix + root.getResourceSeq() + "/").build()))
                    .as("복원 — s·t·f").isEqualTo(3);
            assertThat(col(subsub.getResourceSeq(), "full_path_seq", String.class)).isEqualTo(longPrefix + root.getResourceSeq() + "/" + sub.getResourceSeq() + "/" + subsub.getResourceSeq() + "/");
            assertThat(col(subsub.getResourceSeq(), "full_path", String.class)).isEqualTo("deep/s/t/");
            assertThat(col(subsub.getResourceSeq(), "is_delete", String.class)).isEqualTo("N");
        }

        @Test
        @DisplayName("고칠 행 로드 3종 — 파티션 단건(관리 엔티티)·소유 계정 노출 조건(DELETED 소유자는 null)·잠금판")
        void loadsForChange() {
            User u = User.createUser("res-load", 1, null, SignupType.LOCAL, "로드", UserStatus.ACTIVE, 0L, SUPER_ADMIN, null, null, null);
            userRepository.insertUserWithSeatCheck(u, false, 0);
            Resource f = file(u.getUserSeq(), "load.txt", null, 1L, "txt");
            entityManager.clear();

            Resource plain = repository.findByUserSeqAndResourceSeq(u.getUserSeq(), f.getResourceSeq());
            assertThat(entityManager.contains(plain)).as("관리 엔티티").isTrue();
            assertThat(repository.findVisibleByUserSeqAndResourceSeq(u.getUserSeq(), f.getResourceSeq())).isSameAs(plain);
            assertThat(repository.findByUserSeqAndResourceSeq(SUPER_ADMIN, f.getResourceSeq())).as("파티션 키 불일치").isNull();

            jdbc.update("UPDATE tb_user SET status = 'DELETED' WHERE user_seq = ?", u.getUserSeq());
            assertThat(repository.findVisibleByUserSeqAndResourceSeq(u.getUserSeq(), f.getResourceSeq())).as("DELETED 소유자").isNull();
            assertThat(repository.lockVisibleByUserSeqAndResourceSeq(u.getUserSeq(), f.getResourceSeq())).isNull();
            assertThat(repository.findByUserSeqAndResourceSeq(u.getUserSeq(), f.getResourceSeq())).as("노출 조건 없는 로드").isNotNull();
        }

        @Test
        @DisplayName("잠금 — 잠금 상태 변경(관리 엔티티, 시큐리티 없으면 지정 수정자)·isLockedResource(파일/폴더 트리)·updateExpiredLocks(threshold)")
        void locking() {
            Resource root = folder(ADMIN, "lk", null);
            Resource sub = folder(ADMIN, "sub", root);
            Resource f = file(ADMIN, "f.txt", sub, 1L, "txt");
            Instant lockedAt = Instant.parse("2026-04-01T00:00:00Z");

            assertThat(queryRepository.isLockedResource(new ResourceLockUnlockQuery(f.getResourceSeq(), ADMIN, true, null, ADMIN, null))).isFalse();
            f.applyLockState(Boolean.TRUE, lockedAt, null);
            repository.flush();
            assertThat(col(f.getResourceSeq(), "is_locked", String.class)).isEqualTo("Y");
            assertThat(col(f.getResourceSeq(), "update_user_seq", Integer.class)).as("시큐리티 사용자(Auditing)").isEqualTo(ADMIN);

            // 시큐리티 없음(웹오피스) — 지정한 로그인 사용자가 수정자로 남는다
            SecurityContextHolder.clearContext();
            f.applyLockState(Boolean.FALSE, null, SUPER_ADMIN);
            repository.flush();
            assertThat(col(f.getResourceSeq(), "is_locked", String.class)).isEqualTo("N");
            assertThat(col(f.getResourceSeq(), "update_user_seq", Integer.class)).as("시큐리티 없음 → 지정 로그인 사용자").isEqualTo(SUPER_ADMIN);
            Object unlockedDate = col(f.getResourceSeq(), "update_date", Object.class);
            f.applyLockState(Boolean.FALSE, null, SUPER_ADMIN);
            repository.flush();
            assertThat(col(f.getResourceSeq(), "update_date", Object.class)).as("이미 해제 + 같은 수정자 — UPDATE 없음(예외 없음)").isEqualTo(unlockedDate);
            loginAs(ADMIN);
            f.applyLockState(Boolean.TRUE, lockedAt, null);
            repository.flush();
            assertThat(queryRepository.isLockedResource(new ResourceLockUnlockQuery(f.getResourceSeq(), ADMIN, true, null, ADMIN, null))).as("파일 자기 잠금").isTrue();
            assertThat(queryRepository.isLockedResource(new ResourceLockUnlockQuery(root.getResourceSeq(), ADMIN, true, null, ADMIN, null))).as("폴더 트리에 잠긴 파일").isTrue();
            assertThat(queryRepository.isLockedResource(new ResourceLockUnlockQuery(root.getResourceSeq(), ADMIN, false, null, ADMIN, null))).as("트리에 안 잠긴 것도 있음").isTrue();
            Resource empty = folder(ADMIN, "empty", null);
            assertThat(queryRepository.isLockedResource(new ResourceLockUnlockQuery(empty.getResourceSeq(), ADMIN, true, null, ADMIN, null))).isFalse();

            assertThat(repository.updateExpiredLocks(new ResourceLockExpireQuery(lockedAt.minusSeconds(1), SUPER_ADMIN, Instant.now().truncatedTo(ChronoUnit.SECONDS)))).as("threshold 이전").isZero();
            assertThat(repository.updateExpiredLocks(new ResourceLockExpireQuery(lockedAt, SUPER_ADMIN, Instant.now().truncatedTo(ChronoUnit.SECONDS)))).isEqualTo(1);
            assertThat(col(f.getResourceSeq(), "is_locked", String.class)).isEqualTo("N");
            assertThat(col(f.getResourceSeq(), "locked_date", Object.class)).isNull();
        }
    }

    // ================================================================== JPA 영속성·감사(2차 패스 → W10)

    @Nested
    @DisplayName("JPA — 저장 뒤 관리 상태(변경 감지)·INSERT 컬럼·시큐리티 없음 감사")
    class JpaLifecycle {

        @Test
        @DisplayName("save 뒤 엔티티는 관리 상태 — 이어서 고친 값은 flush·커밋 때 UPDATE(W10, 구 detach) / INSERT 에서 빠진 컬럼은 DB DEFAULT")
        void managedAfterSave() {
            Resource f = file(ADMIN, "jpa-managed.txt", null, 10L, "txt");
            assertThat(entityManager.contains(f)).isTrue();
            assertThat(col(f.getResourceSeq(), "resource_save_name", String.class)).as("저장 뒤 initResourceSaveName 이 변경 감지로 나감").isEqualTo(ADMIN + "/" + f.getResourceSeq());
            assertThat(col(f.getResourceSeq(), "current_version_number", Integer.class)).as("DB DEFAULT").isEqualTo(1);
            assertThat(col(f.getResourceSeq(), "is_delete", String.class)).isEqualTo("N");
            assertThat(col(f.getResourceSeq(), "is_locked", String.class)).isEqualTo("N");

            Resource d = folder(ADMIN, "jpa-managed-folder", null);
            assertThat(entityManager.contains(d)).isTrue();
            assertThat(col(d.getResourceSeq(), "version_register_date", Object.class)).as("폴더는 null 컬럼을 빼서(@DynamicInsert) DB DEFAULT").isNotNull();
            assertThat(col(d.getResourceSeq(), "size_byte", Long.class)).isNull();
            assertThat(col(d.getResourceSeq(), "full_path_seq", String.class)).isEqualTo(d.getResourceSeq() + "/");
        }

        @Test
        @DisplayName("변경 감지(@PartitionKey + @DynamicUpdate) — 같은 값 재수정은 UPDATE·감사 갱신 없음(예외 없음), 같은 초 연속 수정 OK, 바꾸지 않은 컬럼(잠금)은 보존")
        void dirtyCheckingNoOpAndOtherColumns() {
            Resource f = file(ADMIN, "dc.txt", null, 1L, "txt");
            f.changeCopyResource("dc2", "dc2.txt", 0);
            repository.flush();
            Object firstUpdateDate = col(f.getResourceSeq(), "update_date", Object.class);
            assertThat(firstUpdateDate).isNotNull();

            // 같은 값으로 즉시 다시(같은 초) — 업무 컬럼이 그대로라 UPDATE 가 나가지 않고 예외도 없다
            f.changeCopyResource("dc2", "dc2.txt", 0);
            repository.flush();
            assertThat(col(f.getResourceSeq(), "update_date", Object.class)).isEqualTo(firstUpdateDate);

            // 다른 경로(JdbcClient)가 잠금 컬럼을 바꾼 뒤 이름 변경 → 바뀐 컬럼만 UPDATE 라 잠금 유지
            jdbc.update("UPDATE tb_resource SET is_locked = 'Y' WHERE user_seq = ? AND resource_seq = ?", ADMIN, f.getResourceSeq());
            f.changeCopyResource("dc3", "dc3.txt", 0);
            repository.flush();
            assertThat(col(f.getResourceSeq(), "resource_name", String.class)).isEqualTo("dc3");
            assertThat(col(f.getResourceSeq(), "is_locked", String.class)).as("@DynamicUpdate — 바꾸지 않은 컬럼은 쓰지 않음").isEqualTo("Y");
        }

        @Test
        @DisplayName("시큐리티 없음(웹오피스·스케줄러) — save 는 선언한 등록자, 버전 갱신은 지정한 수정자(버전 등록자) 그대로, 지정도 없으면 null")
        void auditWithoutSecurityContext() {
            SecurityContextHolder.clearContext();
            Resource f = file(SUPER_ADMIN, "jpa-nosec.txt", null, 10L, "txt");
            assertThat(col(f.getResourceSeq(), "register_user_seq", Integer.class)).as("선언 값 그대로").isEqualTo(SUPER_ADMIN);
            assertThat(col(f.getResourceSeq(), "update_user_seq", Integer.class)).as("저장 이름 변경 감지 — 지정도 시큐리티도 없으면 null").isNull();

            f.applyVersionUpdate(false, ADMIN, 20L, ADMIN, Instant.parse("2026-02-02T00:00:00Z"));
            repository.flush();
            assertThat(col(f.getResourceSeq(), "update_user_seq", Integer.class)).as("버전 등록자 그대로").isEqualTo(ADMIN);
            assertThat(col(f.getResourceSeq(), "update_date", Object.class)).isNotNull();
            assertThat(col(f.getResourceSeq(), "current_version_number", Integer.class)).isEqualTo(2);
        }
    }
}
