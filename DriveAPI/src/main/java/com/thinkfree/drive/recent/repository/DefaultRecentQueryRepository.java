package com.thinkfree.drive.recent.repository;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.global.jooq.JooqCursors;
import com.thinkfree.drive.jooq.tables.TbRecent;
import com.thinkfree.drive.jooq.tables.TbResource;
import com.thinkfree.drive.jooq.tables.TbStarred;
import com.thinkfree.drive.jooq.tables.TbTenant;
import com.thinkfree.drive.jooq.tables.TbUser;
import com.thinkfree.drive.recent.data.RecentData;
import com.thinkfree.drive.recent.query.RecentListQuery;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.repository.ResourceCursorJooq;
import com.thinkfree.drive.resource.repository.ResourceJooq;
import com.thinkfree.drive.user.enums.UserStatus;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

import static com.thinkfree.drive.global.jooq.JooqConditions.eq;
import static com.thinkfree.drive.global.jooq.JooqConditions.present;
import static com.thinkfree.drive.jooq.Tables.TB_RECENT;
import static com.thinkfree.drive.jooq.Tables.TB_RESOURCE;
import static com.thinkfree.drive.jooq.Tables.TB_STARRED;
import static com.thinkfree.drive.jooq.Tables.TB_TENANT;
import static com.thinkfree.drive.jooq.Tables.TB_USER;
import static org.jooq.impl.DSL.*;

/**
 * tb_recent 읽기 — jOOQ(구 RecentMapper.xml 의 findAll, 10-30 작업 B P1 에서 JdbcClient → jOOQ). 쓰기는 {@link DefaultRecentRepository}.
 * 정렬은 열람일(a.update_date) DESC 고정, 커서는 {@code (update_date, resource_seq)} 단순 비교(XML 동일). is_delete 는 조인 조건(값이 null 이면 행 없음), 별표 조인(f)은 user_seq·resource_seq·shared_by_user_seq(uniq_starred 전체 — 다른 목록과 같음. XML 은 shared_by 가 없어 다른 공유자 별표가 섞이고 행이 늘 수 있었음, 작업 E5).
 * Y/N·enum·시각은 생성 코드의 변환기가 매핑.
 */
@Repository
@RequiredArgsConstructor
public class DefaultRecentQueryRepository implements RecentQueryRepository {

    private static final TbRecent A = TB_RECENT.as("a");
    private static final TbResource B = TB_RESOURCE.as("b");
    private static final TbUser C = TB_USER.as("c");
    private static final TbTenant OT = TB_TENANT.as("ot");
    private static final TbUser D = TB_USER.as("d");
    private static final TbUser E = TB_USER.as("e");
    private static final TbStarred F = TB_STARRED.as("f");
    private static final TbResource G = TB_RESOURCE.as("g");

    private static final Field<Boolean> IS_STARRED = when(F.STARRED_SEQ.isNotNull(), inline(true)).otherwise(inline(false)).as("is_starred");

    private final DSLContext dsl;

    @Override
    public List<RecentData> findAll(RecentListQuery q) {
        Condition where = eq(A.USER_SEQ, q.getUserSeq());
        CursorPaginationQuery page = q.getPage();
        if (page != null && present(page.getCursorId()) && present(page.getCursorValue())) {
            Field<?> cursorDate = cast(val(JooqCursors.timestamp(page.getCursorValue()), SQLDataType.VARCHAR), SQLDataType.TIMESTAMPWITHTIMEZONE);
            Field<Long> cursorId = val(page.getCursorId(), SQLDataType.BIGINT);
            where = where.and(ResourceCursorJooq.after(A.UPDATE_DATE, true, cursorDate)
                    .or(ResourceCursorJooq.same(A.UPDATE_DATE, cursorDate).and(A.RESOURCE_SEQ.lt(cursorId))));
        }
        if (q.getResourceType() != null) {
            where = where.and(B.RESOURCE_TYPE.eq(q.getResourceType()));
        }
        if (q.getFrom() != null && q.getTo() != null) {
            where = where.and(A.UPDATE_DATE.ge(q.getFrom()).and(A.UPDATE_DATE.lt(q.getTo())));
        }
        if (q.getFileType() != null && q.getFileType().getExtensions() != null && !q.getFileType().getExtensions().isEmpty()) {
            where = where.and(B.EXT.in(q.getFileType().getExtensions()));
        }
        if (present(q.getUserId())) {
            where = where.and(C.USER_ID.eq(q.getUserId())).and(C.STATUS.in(inline(UserStatus.ACTIVE, C.STATUS), inline(UserStatus.INACTIVE, C.STATUS)));
        }
        where = where.and(ResourceJooq.visibleByActiveUser(A.SHARED_BY_USER_SEQ));

        return dsl.select(A.RECENT_SEQ, A.USER_SEQ, B.RESOURCE_SEQ, A.SHARED_BY_USER_SEQ,
                        C.USER_ID.as("owner_id"), C.USER_NAME.as("owner_name"), B.OWNER_SEQ, C.STATUS.as("owner_status"), C.TENANT_SEQ.as("owner_tenant_seq"), OT.TENANT_NAME.as("owner_tenant_name"),
                        B.RESOURCE_TYPE, B.PARENT_FOLDER_SEQ, G.RESOURCE_NAME.as("parent_folder_name"), B.RESOURCE_NAME, B.EXT, G.FULL_PATH_SEQ, G.FULL_PATH, B.SIZE_BYTE, B.IS_LOCKED,
                        IS_STARRED, F.STARRED_SEQ,
                        D.USER_ID.as("resource_register_user_id"), D.USER_NAME.as("resource_register_user_name"), D.STATUS.as("resource_register_user_status"), B.REGISTER_DATE.as("resource_register_date"),
                        E.USER_ID.as("resource_update_user_id"), E.USER_NAME.as("resource_update_user_name"), E.STATUS.as("resource_update_user_status"), B.UPDATE_DATE.as("resource_update_date"),
                        A.REGISTER_DATE.as("recent_register_date"), A.UPDATE_DATE.as("recent_update_date"))
                .from(A)
                .join(B).on(A.SHARED_BY_USER_SEQ.eq(B.USER_SEQ).and(A.RESOURCE_SEQ.eq(B.RESOURCE_SEQ)).and(eq(B.IS_DELETE, q.getIsDelete())))
                .join(C).on(B.OWNER_SEQ.eq(C.USER_SEQ))
                .join(OT).on(C.TENANT_SEQ.eq(OT.TENANT_SEQ))
                .join(D).on(B.REGISTER_USER_SEQ.eq(D.USER_SEQ))
                .leftJoin(E).on(B.UPDATE_USER_SEQ.eq(E.USER_SEQ))
                .leftJoin(F).on(A.USER_SEQ.eq(F.USER_SEQ).and(A.RESOURCE_SEQ.eq(F.RESOURCE_SEQ)).and(A.SHARED_BY_USER_SEQ.eq(F.SHARED_BY_USER_SEQ)))
                .leftJoin(G).on(B.USER_SEQ.eq(G.USER_SEQ).and(B.PARENT_FOLDER_SEQ.eq(G.RESOURCE_SEQ)).and(G.RESOURCE_TYPE.eq(inline(ResourceType.FOLDER, G.RESOURCE_TYPE))))
                .where(where)
                .orderBy(A.UPDATE_DATE.desc(), A.RESOURCE_SEQ.desc())
                .limit(val(page == null ? null : page.getPageSize(), SQLDataType.INTEGER))
                .fetch(r -> RecentData.builder()
                        .recentSeq(r.get(A.RECENT_SEQ))
                        .userSeq(r.get(A.USER_SEQ))
                        .resourceSeq(r.get(B.RESOURCE_SEQ))
                        .sharedByUserSeq(r.get(A.SHARED_BY_USER_SEQ))
                        .ownerId(r.get("owner_id", String.class))
                        .ownerName(r.get("owner_name", String.class))
                        .ownerSeq(r.get(B.OWNER_SEQ))
                        .ownerStatus(r.get("owner_status", UserStatus.class))
                        .ownerTenantSeq(r.get("owner_tenant_seq", Integer.class))
                        .ownerTenantName(r.get("owner_tenant_name", String.class))
                        .resourceType(r.get(B.RESOURCE_TYPE))
                        .parentFolderSeq(r.get(B.PARENT_FOLDER_SEQ))
                        .parentFolderName(r.get("parent_folder_name", String.class))
                        .resourceName(r.get(B.RESOURCE_NAME))
                        .ext(r.get(B.EXT))
                        .fullPathSeq(r.get(G.FULL_PATH_SEQ))
                        .fullPath(r.get(G.FULL_PATH))
                        .sizeByte(r.get(B.SIZE_BYTE))
                        .isLocked(r.get(B.IS_LOCKED))
                        .isStarred(r.get(IS_STARRED))
                        .starredSeq(r.get(F.STARRED_SEQ))
                        .resourceRegisterUserId(r.get("resource_register_user_id", String.class))
                        .resourceRegisterUserName(r.get("resource_register_user_name", String.class))
                        .resourceRegisterUserStatus(r.get("resource_register_user_status", UserStatus.class))
                        .resourceRegisterDate(r.get("resource_register_date", Instant.class))
                        .resourceUpdateUserId(r.get("resource_update_user_id", String.class))
                        .resourceUpdateUserName(r.get("resource_update_user_name", String.class))
                        .resourceUpdateUserStatus(r.get("resource_update_user_status", UserStatus.class))
                        .resourceUpdateDate(r.get("resource_update_date", Instant.class))
                        .recentRegisterDate(r.get("recent_register_date", Instant.class))
                        .recentUpdateDate(r.get("recent_update_date", Instant.class))
                        .build());
    }
}
