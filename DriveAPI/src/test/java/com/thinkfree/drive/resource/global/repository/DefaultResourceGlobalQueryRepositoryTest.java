package com.thinkfree.drive.resource.global.repository;

import com.thinkfree.drive.global.time.DriveTime;
import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.common.search.enums.FileType;
import com.thinkfree.drive.common.search.enums.SortOrder;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.resource.enums.ResourceSortField;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.global.data.ResourceGlobalData;
import com.thinkfree.drive.resource.query.ResourceListQuery;
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
 * CommonMapper 조각 묶음 이관 회귀 — {@link DefaultResourceGlobalQueryRepository}(구 ResourceGlobalMapper.xml findAll), JdbcClient.
 * 뷰어(global-viewer)의 리소스(폴더 1·파일 1·삭제 1) + 공유자(global-owner)의 공유 폴더 o-shared(하위 o-sub/o-deep.txt·o-child.pdf·삭제 1)·미공유 o-private 를 tb_resource 에 직접 INSERT,
 * o-shared 와 그 안의 o-sub 를 둘 다 뷰어에게 공유(중첩 공유 → 중복 제거 확인). 트랜잭션 롤백.
 * 검증 포인트 = 내 것 + 공유받은 것(하위 포함)의 합집합·중복 제거·미공유/삭제 제외, 뷰어 기준 별표/최근열람, 정렬 6종의 커서 왕복 = 단일 페이지(OPEN 커서는 소유자를 찾아 뷰어 tb_recent 판정),
 * 검색어·확장자·타입·소유자 아이디·수정일 범위 필터, 공유자 계정 노출 조건, LIMIT.
 */
@DisplayName("DefaultResourceGlobalQueryRepository — JdbcClient(PostgreSQL Testcontainers)")
class DefaultResourceGlobalQueryRepositoryTest extends RepositoryTest {

    private static final int SUPER_ADMIN = 1;
    private static final int ADMIN = 2;

    @Autowired
    private ResourceGlobalQueryRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    private int viewer;
    private int owner;
    private long mFolder;
    private long mTxt;
    private long oShared;
    private long oSub;
    private long oDeep;
    private long oChild;
    private long oPrivate;
    private long starredSeq;

    @BeforeEach
    void setUp() {
        loginAs(ADMIN);
        viewer = user("global-viewer", "뷰어");
        owner = user("global-owner", "공유자");

        mFolder = resource(viewer, "FOLDER", null, "m-folder", null, null, "2026-01-10 00:00:00");
        mTxt = resource(viewer, "FILE", mFolder, "m.txt", 5L, "txt", "2026-01-15 00:00:00");
        long mDeleted = resource(viewer, "FILE", mFolder, "m-del.txt", 1L, "txt", "2026-01-16 00:00:00");
        jdbc.update("UPDATE tb_resource SET is_delete = 'Y' WHERE resource_seq = ?", mDeleted);

        oShared = resource(owner, "FOLDER", null, "o-shared", null, null, "2026-02-10 00:00:00");
        oSub = resource(owner, "FOLDER", oShared, "o-sub", null, null, "2026-02-11 00:00:00");
        oDeep = resource(owner, "FILE", oSub, "o-deep.txt", 7L, "txt", "2026-02-12 00:00:00");
        oChild = resource(owner, "FILE", oShared, "o-child.pdf", 20L, "pdf", "2026-02-13 00:00:00");
        long oGone = resource(owner, "FILE", oShared, "o-gone.txt", 2L, "txt", "2026-02-14 00:00:00");
        jdbc.update("UPDATE tb_resource SET is_delete = 'Y' WHERE resource_seq = ?", oGone);
        oPrivate = resource(owner, "FOLDER", null, "o-private", null, null, "2026-02-15 00:00:00");

        shareTo(oShared);
        shareTo(oSub);

        starredSeq = jdbc.queryForObject("INSERT INTO tb_starred (user_seq, resource_seq, shared_by_user_seq, register_user_seq) VALUES (?, ?, ?, ?) RETURNING starred_seq",
                Long.class, viewer, oChild, owner, viewer);
        jdbc.update("INSERT INTO tb_recent (user_seq, resource_seq, shared_by_user_seq, register_user_seq, update_date) VALUES (?, ?, ?, ?, ?)",
                viewer, mTxt, viewer, viewer, Timestamp.valueOf("2026-06-01 00:00:00"));
        jdbc.update("INSERT INTO tb_recent (user_seq, resource_seq, shared_by_user_seq, register_user_seq, update_date) VALUES (?, ?, ?, ?, ?)",
                viewer, oDeep, owner, viewer, Timestamp.valueOf("2026-05-01 00:00:00"));
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

    private long resource(int userSeq, String type, Long parent, String name, Long size, String ext, String updateDate) {
        return jdbc.queryForObject("""
                INSERT INTO tb_resource (user_seq, owner_seq, resource_type, parent_folder_seq, resource_name, size_byte, ext, register_user_seq, update_date)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING resource_seq
                """, Long.class, userSeq, userSeq, type, parent, name, size, ext, userSeq, Timestamp.valueOf(updateDate));
    }

    /** owner 의 리소스를 SPECIFIC 공유 + viewer 대상. */
    private void shareTo(long resourceSeq) {
        long shareSeq = jdbc.queryForObject("""
                INSERT INTO tb_share (user_seq, resource_seq, share_type, can_view, can_edit, can_download, can_upload, can_delete, can_share, register_user_seq)
                VALUES (?, ?, 'SPECIFIC_USERS', 'Y', 'N', 'N', 'N', 'N', 'N', ?) RETURNING share_seq
                """, Long.class, owner, resourceSeq, owner);
        jdbc.update("""
                INSERT INTO tb_share_target (user_seq, share_seq, resource_seq, shared_by_user_seq, share_date, can_view, can_edit, can_download, can_upload, can_delete, can_share, register_user_seq)
                VALUES (?, ?, ?, ?, now(), 'Y', 'N', 'N', 'N', 'N', 'N', ?)
                """, viewer, shareSeq, resourceSeq, owner, owner);
    }

    private ResourceListQuery.ResourceListQueryBuilder list(int pageSize) {
        return ResourceListQuery.builder().userSeq(viewer).requestUserSeq(viewer).isDelete(false)
                .page(CursorPaginationQuery.of(null, null, null, pageSize));
    }

    private static List<Long> seqs(List<ResourceGlobalData> rows) {
        return rows.stream().map(ResourceGlobalData::getResourceSeq).toList();
    }

    private static Object cursorValue(ResourceGlobalData last, ResourceSortField sortBy) {
        return switch (sortBy) {
            case NAME -> last.getResourceName();
            case SIZE -> last.getSizeByte();
            case UPDATE -> last.getUpdateDate().toString();
            case OPEN -> (last.getRecentUpdateDate() != null ? last.getRecentUpdateDate() : last.getUpdateDate()).toString();
        };
    }

    private List<Long> walk(ResourceSortField sortBy, SortOrder order, int ps) {
        List<Long> out = new ArrayList<>();
        CursorPaginationQuery page = CursorPaginationQuery.of(null, null, null, ps);
        for (int guard = 0; guard < 20; guard++) {
            List<ResourceGlobalData> rows = repository.findAll(list(ps).sortBy(sortBy).sortOrder(order).page(page).build());
            if (rows.isEmpty()) {
                break;
            }
            out.addAll(seqs(rows));
            if (rows.size() < ps) {
                break;
            }
            ResourceGlobalData last = rows.get(rows.size() - 1);
            page = CursorPaginationQuery.of(cursorValue(last, sortBy), last.getResourceSeq(), last.getResourceType(), ps);
        }
        return out;
    }

    // ================================================================== 테스트

    @Test
    @DisplayName("합집합 — 내 것 전부 + 공유받은 것과 하위(중첩 공유 1회), 미공유·삭제 제외, 뷰어 별표/최근열람, 소유자 컬럼, LIMIT")
    void unionAndMapping() {
        List<ResourceGlobalData> rows = repository.findAll(list(10).sortBy(ResourceSortField.NAME).sortOrder(SortOrder.ASC).build());
        assertThat(seqs(rows)).containsExactly(mFolder, oShared, oSub, mTxt, oChild, oDeep);
        assertThat(seqs(rows)).doesNotContain(oPrivate);

        ResourceGlobalData mine = rows.get(0);
        assertThat(mine.getUserSeq()).isEqualTo(viewer);
        assertThat(mine.getSharedByUserSeq()).isEqualTo(viewer);
        assertThat(mine.getOwnerId()).isEqualTo("global-viewer");
        assertThat(mine.getResourceType()).isEqualTo(ResourceType.FOLDER);
        assertThat(mine.getIsStarred()).isFalse();
        assertThat(mine.getUpdateDate()).isEqualTo(DriveTime.toInstant(LocalDateTime.of(2026, 1, 10, 0, 0)));

        ResourceGlobalData shared = rows.get(1);
        assertThat(shared.getUserSeq()).isEqualTo(owner);
        assertThat(shared.getOwnerSeq()).isEqualTo(owner);
        assertThat(shared.getOwnerId()).isEqualTo("global-owner");
        assertThat(shared.getOwnerName()).isEqualTo("공유자");
        assertThat(shared.getOwnerStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(shared.getOwnerTenantSeq()).isEqualTo(1);
        assertThat(shared.getOwnerTenantName()).isEqualTo("기본 테넌트");
        assertThat(shared.getRegisterUserId()).isEqualTo("global-owner");
        assertThat(shared.getUpdateUserId()).isNull();
        assertThat(shared.getIsLocked()).isFalse();

        ResourceGlobalData myFile = rows.get(3);
        assertThat(myFile.getResourceName()).isEqualTo("m.txt");
        assertThat(myFile.getSizeByte()).isEqualTo(5L);
        assertThat(myFile.getRecentUpdateDate()).as("내 리소스의 열람(shared_by = 나)").isEqualTo(DriveTime.toInstant(LocalDateTime.of(2026, 6, 1, 0, 0)));

        ResourceGlobalData child = rows.get(4);
        assertThat(child.getIsStarred()).as("공유받은 파일의 뷰어 별표").isTrue();
        assertThat(child.getStarredSeq()).isEqualTo(starredSeq);
        assertThat(child.getRecentUpdateDate()).isNull();

        ResourceGlobalData deep = rows.get(5);
        assertThat(deep.getRecentUpdateDate()).as("공유받은 하위 파일의 열람(shared_by = 소유자)").isEqualTo(DriveTime.toInstant(LocalDateTime.of(2026, 5, 1, 0, 0)));

        assertThat(seqs(repository.findAll(list(10).build()))).as("정렬 미지정 = 이름 순").containsExactly(mFolder, oShared, oSub, mTxt, oChild, oDeep);
        assertThat(seqs(repository.findAll(list(2).build()))).as("LIMIT").containsExactly(mFolder, oShared);
    }

    @Test
    @DisplayName("정렬 6종 — 단일 페이지와 커서 왕복이 같다(누락·중복 0), OPEN 은 열람 행 먼저(커서 그룹 판정은 소유자별 tb_recent)")
    void sortAndCursor() {
        assertThat(walk(ResourceSortField.NAME, SortOrder.ASC, 2)).containsExactly(mFolder, oShared, oSub, mTxt, oChild, oDeep);
        assertThat(walk(ResourceSortField.NAME, SortOrder.DESC, 4)).containsExactly(oSub, oShared, mFolder, oDeep, oChild, mTxt);

        List<Long> size = seqs(repository.findAll(list(10).sortBy(ResourceSortField.SIZE).sortOrder(SortOrder.DESC).build()));
        assertThat(size.subList(3, 6)).containsExactly(oChild, oDeep, mTxt);
        assertThat(walk(ResourceSortField.SIZE, SortOrder.DESC, 4)).isEqualTo(size);

        List<Long> upd = seqs(repository.findAll(list(10).sortBy(ResourceSortField.UPDATE).sortOrder(SortOrder.DESC).build()));
        assertThat(upd).containsExactly(oSub, oShared, mFolder, oChild, oDeep, mTxt);
        assertThat(walk(ResourceSortField.UPDATE, SortOrder.DESC, 2)).isEqualTo(upd);
        assertThat(walk(ResourceSortField.UPDATE, SortOrder.ASC, 4)).containsExactly(mFolder, oShared, oSub, mTxt, oDeep, oChild);

        List<Long> openDesc = seqs(repository.findAll(list(10).sortBy(ResourceSortField.OPEN).sortOrder(SortOrder.DESC).build()));
        assertThat(openDesc.subList(3, 6)).as("열람 행(m.txt 06-01, o-deep 05-01) 먼저, 미열람은 수정일 순").containsExactly(mTxt, oDeep, oChild);
        assertThat(walk(ResourceSortField.OPEN, SortOrder.DESC, 2)).isEqualTo(openDesc);
        List<Long> openAsc = seqs(repository.findAll(list(10).sortBy(ResourceSortField.OPEN).sortOrder(SortOrder.ASC).build()));
        assertThat(openAsc.subList(3, 6)).containsExactly(oDeep, mTxt, oChild);
        assertThat(walk(ResourceSortField.OPEN, SortOrder.ASC, 2)).isEqualTo(openAsc);
    }

    @Test
    @DisplayName("필터 — 검색어(이름 부분 일치)·확장자·타입·소유자 아이디·수정일 범위, 공유자 계정 DELETED 면 내 것만")
    void filtersAndGuards() {
        assertThat(seqs(repository.findAll(list(10).searchKeyword("o-").build()))).containsExactly(oShared, oSub, oChild, oDeep);
        assertThat(seqs(repository.findAll(list(10).searchKeyword("deep").build()))).containsExactly(oDeep);
        assertThat(seqs(repository.findAll(list(10).fileType(FileType.PDF).build()))).containsExactly(oChild);
        assertThat(seqs(repository.findAll(list(10).resourceType(ResourceType.FOLDER).build()))).containsExactly(mFolder, oShared, oSub);
        assertThat(seqs(repository.findAll(list(10).userId("global-owner").build()))).containsExactly(oShared, oSub, oChild, oDeep);
        assertThat(seqs(repository.findAll(list(10).userId("global-viewer").build()))).containsExactly(mFolder, mTxt);
        assertThat(seqs(repository.findAll(list(10).from(DriveTime.startOfDay(LocalDate.of(2026, 2, 12))).to(DriveTime.endExclusive(LocalDate.of(2026, 2, 13))).build()))).containsExactly(oChild, oDeep);
        assertThat(repository.findAll(list(10).isDelete(true).build())).as("공유 루트가 삭제가 아니면 공유 구간은 비고 내 삭제 파일만(구 XML 동형)").extracting(ResourceGlobalData::getResourceName).containsExactly("m-del.txt");

        jdbc.update("UPDATE tb_user SET status = 'DELETED' WHERE user_seq = ?", owner);
        assertThat(seqs(repository.findAll(list(10).build()))).as("공유자 노출 불가 → 공유 구간 전체 제외").containsExactly(mFolder, mTxt);
    }
}
