package com.thinkfree.drive.resource.share.repository;

import com.thinkfree.drive.global.time.DriveTime;
import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.common.search.enums.FileType;
import com.thinkfree.drive.common.search.enums.SortOrder;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.resource.enums.ResourceSortField;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.query.ResourceListQuery;
import com.thinkfree.drive.resource.share.data.ResourceSharedData;
import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.SignupType;
import com.thinkfree.drive.user.enums.UserStatus;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * CommonMapper 조각 묶음 이관 회귀 — {@link DefaultResourceShareQueryRepository}(구 ResourceShareMapper.xml findAllByShared), JdbcClient.
 * 소유자(share-owner)의 root 폴더 아래 폴더 2·파일 2·삭제 파일 1 을 tb_resource 에 직접 INSERT, 뷰어(share-viewer)의 별표·최근열람과 소유자 본인의 별표를 섞는다. 트랜잭션 롤백.
 * 검증 포인트 = 뷰어 기준 별표/최근열람 조인(소유자 별표는 안 보임), 23컬럼 매핑, 정렬 6종의 커서 왕복 = 단일 페이지(누락·중복 0), 필터(확장자·타입·소유자 아이디·수정일 범위), 부모·소유 계정 조건.
 */
@DisplayName("DefaultResourceShareQueryRepository — JdbcClient(PostgreSQL Testcontainers)")
class DefaultResourceShareQueryRepositoryTest extends RepositoryTest {

    private static final int SUPER_ADMIN = 1;
    private static final int ADMIN = 2;

    @Autowired
    private ResourceShareQueryRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    private int owner;
    private int viewer;
    private long root;
    private long alpha;
    private long beta;
    private long cTxt;
    private long dPdf;
    private long starredSeq;

    @BeforeEach
    void setUp() {
        loginAs(ADMIN);
        owner = user("share-owner", "소유자");
        viewer = user("share-viewer", "뷰어");
        root = resource("FOLDER", null, "root", null, null, "2026-01-01 00:00:00");
        beta = resource("FOLDER", root, "beta", null, null, "2026-01-20 00:00:00");
        alpha = resource("FOLDER", root, "alpha", null, null, "2026-01-10 00:00:00");
        cTxt = resource("FILE", root, "c.txt", 30L, "txt", "2026-03-01 00:00:00");
        dPdf = resource("FILE", root, "d.pdf", 10L, "pdf", "2026-02-01 00:00:00");
        long deleted = resource("FILE", root, "deleted.txt", 1L, "txt", "2026-02-15 00:00:00");
        jdbc.update("UPDATE tb_resource SET is_delete = 'Y' WHERE resource_seq = ?", deleted);

        starredSeq = jdbc.queryForObject("INSERT INTO tb_starred (user_seq, resource_seq, shared_by_user_seq, register_user_seq) VALUES (?, ?, ?, ?) RETURNING starred_seq",
                Long.class, viewer, alpha, owner, viewer);
        jdbc.update("INSERT INTO tb_starred (user_seq, resource_seq, shared_by_user_seq, register_user_seq) VALUES (?, ?, ?, ?)", owner, beta, owner, owner);
        jdbc.update("INSERT INTO tb_recent (user_seq, resource_seq, shared_by_user_seq, register_user_seq, update_date) VALUES (?, ?, ?, ?, ?)",
                viewer, cTxt, owner, viewer, Timestamp.valueOf("2026-06-01 00:00:00"));
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

    private long resource(String type, Long parent, String name, Long size, String ext, String updateDate) {
        return jdbc.queryForObject("""
                INSERT INTO tb_resource (user_seq, owner_seq, resource_type, parent_folder_seq, resource_name, size_byte, ext, register_user_seq, update_date)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING resource_seq
                """, Long.class, owner, owner, type, parent, name, size, ext, owner, Timestamp.valueOf(updateDate));
    }

    private ResourceListQuery.ResourceListQueryBuilder list(int pageSize) {
        return ResourceListQuery.builder().userSeq(owner).requestUserSeq(viewer).parentFolderSeq(root).isDelete(false)
                .page(CursorPaginationQuery.of(null, null, null, pageSize));
    }

    private static List<Long> seqs(List<ResourceSharedData> rows) {
        return rows.stream().map(ResourceSharedData::getResourceSeq).toList();
    }

    private static Object cursorValue(ResourceSharedData last, ResourceSortField sortBy) {
        return switch (sortBy) {
            case NAME -> last.getResourceName();
            case SIZE -> last.getSizeByte();
            case UPDATE -> last.getUpdateDate().toString();
            case OPEN -> (last.getRecentUpdateDate() != null ? last.getRecentUpdateDate() : last.getUpdateDate()).toString();
        };
    }

    /** ps 크기로 끝까지 커서 왕복한 seq 목록(CursorPageInfo 와 같은 규칙: 마지막 행의 정렬값·seq·타입이 다음 커서). */
    private List<Long> walk(ResourceSortField sortBy, SortOrder order, int ps) {
        List<Long> out = new ArrayList<>();
        CursorPaginationQuery page = CursorPaginationQuery.of(null, null, null, ps);
        for (int guard = 0; guard < 20; guard++) {
            List<ResourceSharedData> rows = repository.findAllByShared(list(ps).sortBy(sortBy).sortOrder(order).page(page).build());
            if (rows.isEmpty()) {
                break;
            }
            out.addAll(seqs(rows));
            if (rows.size() < ps) {
                break;
            }
            ResourceSharedData last = rows.get(rows.size() - 1);
            page = CursorPaginationQuery.of(cursorValue(last, sortBy), last.getResourceSeq(), last.getResourceType(), ps);
        }
        return out;
    }

    // ================================================================== 테스트

    @Test
    @DisplayName("매핑 — NAME ASC(폴더 먼저·자연정렬), 뷰어 별표/최근열람만, 소유자·등록자 컬럼, 삭제 행 제외, 정렬 미지정은 이름 순")
    void mappingAndDefaultOrder() {
        List<ResourceSharedData> rows = repository.findAllByShared(list(10).sortBy(ResourceSortField.NAME).sortOrder(SortOrder.ASC).build());
        assertThat(seqs(rows)).containsExactly(alpha, beta, cTxt, dPdf);

        ResourceSharedData a = rows.get(0);
        assertThat(a.getUserSeq()).isEqualTo(owner);
        assertThat(a.getSharedByUserSeq()).isEqualTo(owner);
        assertThat(a.getOwnerSeq()).isEqualTo(owner);
        assertThat(a.getOwnerId()).isEqualTo("share-owner");
        assertThat(a.getOwnerName()).isEqualTo("소유자");
        assertThat(a.getOwnerStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(a.getOwnerTenantSeq()).isEqualTo(1);
        assertThat(a.getOwnerTenantName()).isEqualTo("기본 테넌트");
        assertThat(a.getResourceType()).isEqualTo(ResourceType.FOLDER);
        assertThat(a.getResourceName()).isEqualTo("alpha");
        assertThat(a.getSizeByte()).isNull();
        assertThat(a.getIsLocked()).isFalse();
        assertThat(a.getIsStarred()).as("뷰어의 별표").isTrue();
        assertThat(a.getStarredSeq()).isEqualTo(starredSeq);
        assertThat(a.getRegisterUserId()).isEqualTo("share-owner");
        assertThat(a.getRegisterUserStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(a.getRegisterDate()).isNotNull();
        assertThat(a.getUpdateUserId()).isNull();
        assertThat(a.getUpdateUserStatus()).isNull();
        assertThat(a.getUpdateDate()).isEqualTo(DriveTime.toInstant(LocalDateTime.of(2026, 1, 10, 0, 0)));
        assertThat(a.getRecentUpdateDate()).isNull();

        ResourceSharedData b = rows.get(1);
        assertThat(b.getIsStarred()).as("소유자 본인의 별표는 뷰어에게 안 보임").isFalse();
        assertThat(b.getStarredSeq()).isNull();

        ResourceSharedData c = rows.get(2);
        assertThat(c.getResourceType()).isEqualTo(ResourceType.FILE);
        assertThat(c.getSizeByte()).isEqualTo(30L);
        assertThat(c.getRecentUpdateDate()).as("뷰어의 최근열람").isEqualTo(DriveTime.toInstant(LocalDateTime.of(2026, 6, 1, 0, 0)));

        assertThat(seqs(repository.findAllByShared(list(10).build()))).as("정렬 미지정 = 이름 순").containsExactly(alpha, beta, cTxt, dPdf);
        assertThat(seqs(repository.findAllByShared(list(2).build()))).as("LIMIT").containsExactly(alpha, beta);
    }

    @Test
    @DisplayName("정렬 6종 — 단일 페이지와 커서 왕복이 같다(누락·중복 0), SIZE/UPDATE/OPEN 의 파일 구간 순서")
    void sortAndCursor() {
        assertThat(walk(ResourceSortField.NAME, SortOrder.ASC, 2)).containsExactly(alpha, beta, cTxt, dPdf);
        assertThat(walk(ResourceSortField.NAME, SortOrder.DESC, 2)).containsExactly(beta, alpha, dPdf, cTxt);

        List<Long> size = seqs(repository.findAllByShared(list(10).sortBy(ResourceSortField.SIZE).sortOrder(SortOrder.DESC).build()));
        assertThat(size.subList(2, 4)).containsExactly(cTxt, dPdf);
        assertThat(walk(ResourceSortField.SIZE, SortOrder.DESC, 3)).isEqualTo(size);

        List<Long> upd = seqs(repository.findAllByShared(list(10).sortBy(ResourceSortField.UPDATE).sortOrder(SortOrder.DESC).build()));
        assertThat(upd).containsExactly(beta, alpha, cTxt, dPdf);
        assertThat(walk(ResourceSortField.UPDATE, SortOrder.DESC, 2)).isEqualTo(upd);
        assertThat(walk(ResourceSortField.UPDATE, SortOrder.ASC, 3)).containsExactly(alpha, beta, dPdf, cTxt);

        List<Long> openDesc = seqs(repository.findAllByShared(list(10).sortBy(ResourceSortField.OPEN).sortOrder(SortOrder.DESC).build()));
        assertThat(openDesc.subList(2, 4)).as("열람 행 먼저").containsExactly(cTxt, dPdf);
        assertThat(walk(ResourceSortField.OPEN, SortOrder.DESC, 2)).isEqualTo(openDesc);
        List<Long> openAsc = seqs(repository.findAllByShared(list(10).sortBy(ResourceSortField.OPEN).sortOrder(SortOrder.ASC).build()));
        assertThat(openAsc.subList(2, 4)).containsExactly(cTxt, dPdf);
        assertThat(walk(ResourceSortField.OPEN, SortOrder.ASC, 3)).isEqualTo(openAsc);
    }

    @Test
    @DisplayName("필터 — 확장자(FileType)·resourceType·소유자 아이디(노출 가능)·수정일 범위, 부모 null 은 0건, 소유 계정 DELETED 는 0건")
    void filtersAndGuards() {
        assertThat(seqs(repository.findAllByShared(list(10).fileType(FileType.PDF).build()))).containsExactly(dPdf);
        assertThat(seqs(repository.findAllByShared(list(10).resourceType(ResourceType.FILE).build()))).containsExactly(cTxt, dPdf);
        assertThat(seqs(repository.findAllByShared(list(10).resourceType(ResourceType.FOLDER).build()))).containsExactly(alpha, beta);
        assertThat(seqs(repository.findAllByShared(list(10).userId("share-owner").build()))).hasSize(4);
        assertThat(seqs(repository.findAllByShared(list(10).userId("nobody").build()))).isEmpty();
        assertThat(seqs(repository.findAllByShared(list(10).from(DriveTime.startOfDay(LocalDate.of(2026, 2, 1))).to(DriveTime.endExclusive(LocalDate.of(2026, 3, 1))).build()))).containsExactly(cTxt, dPdf);
        assertThat(seqs(repository.findAllByShared(list(10).from(DriveTime.startOfDay(LocalDate.of(2026, 2, 1))).to(DriveTime.endExclusive(null)).build()))).as("둘 다 있어야 적용").hasSize(4);

        assertThat(repository.findAllByShared(list(10).parentFolderSeq(null).build())).as("parent_folder_seq = NULL → 0건(구 XML 동형)").isEmpty();
        assertThat(repository.findAllByShared(list(10).userSeq(viewer).build())).as("다른 소유자").isEmpty();
        assertThat(repository.findAllByShared(list(10).isDelete(true).build())).extracting(ResourceSharedData::getResourceName).containsExactly("deleted.txt");

        jdbc.update("UPDATE tb_user SET status = 'DELETED' WHERE user_seq = ?", owner);
        assertThat(repository.findAllByShared(list(10).build())).isEmpty();
    }
}
