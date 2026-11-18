package com.thinkfree.drive.resource.repository;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.common.search.enums.SortOrder;
import com.thinkfree.drive.jooq.tables.TbRecent;
import com.thinkfree.drive.jooq.tables.TbResource;
import com.thinkfree.drive.jooq.tables.TbStarred;
import com.thinkfree.drive.jooq.tables.TbTenant;
import com.thinkfree.drive.jooq.tables.TbUser;
import com.thinkfree.drive.resource.enums.ResourceSortField;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.query.ResourceListQuery;
import com.thinkfree.drive.user.enums.UserStatus;
import org.jooq.Condition;
import org.jooq.DataType;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectJoinStep;
import org.jooq.SortField;
import org.jooq.impl.SQLDataType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static com.thinkfree.drive.global.jooq.JooqConditions.eq;
import static com.thinkfree.drive.global.jooq.JooqConditions.present;
import static com.thinkfree.drive.jooq.Tables.TB_RECENT;
import static com.thinkfree.drive.jooq.Tables.TB_RESOURCE;
import static com.thinkfree.drive.jooq.Tables.TB_STARRED;
import static com.thinkfree.drive.jooq.Tables.TB_TENANT;
import static com.thinkfree.drive.jooq.Tables.TB_USER;
import static org.jooq.impl.DSL.*;

/**
 * tb_resource 목록 조회 공용 jOOQ 조각 — 구 ResourceSql(JdbcClient 문자열, 10-30 삭제)을 jOOQ 로 옮긴 것. 조인·커서 게이트·정렬은 SQL 모양까지 구 SQL 과 같게 둔다.
 * 내 드라이브({@link DefaultResourceQueryRepository#findAll})·공유받은 폴더 목록·전체 목록이 같이 쓰고, 세 목록은 별표·열람 조인 기준(본인/뷰어)과 OPEN 커서의 열람 판정식·열람일 식만 다르다. 커서 게이트·정렬 키 본체는 {@link ResourceCursorJooq}.
 * <p>
 * 별칭은 구 XML·ResourceSql 과 같다: a=tb_resource, b=owner(tb_user), ot=owner tenant, c=register user, d=update user, e=parent folder, f=tb_starred, g=tb_recent.
 * 요청 값은 전부 바인딩(커서 타입·커서 값·커서 id 포함 — 구 SQL 과 같이 SQL 쪽에서 CAST·비교), 문장에 끼우는 것은 enum/코드 상수뿐.
 */
public final class ResourceJooq {

    private ResourceJooq() {
    }

    public static final TbResource A = TB_RESOURCE.as("a");
    public static final TbUser B = TB_USER.as("b");
    public static final TbTenant OT = TB_TENANT.as("ot");
    public static final TbUser C = TB_USER.as("c");
    public static final TbUser D = TB_USER.as("d");
    public static final TbResource E = TB_RESOURCE.as("e");
    public static final TbStarred F = TB_STARRED.as("f");
    public static final TbRecent G = TB_RECENT.as("g");

    // ------------------------------------------------------------------ 목록 공통 컬럼(resource-base-select 의 조인 컬럼 별칭)

    public static final Field<String> OWNER_ID = B.USER_ID.as("owner_id");
    public static final Field<String> OWNER_NAME = B.USER_NAME.as("owner_name");
    public static final Field<UserStatus> OWNER_STATUS = B.STATUS.as("owner_status");
    public static final Field<Integer> OWNER_TENANT_SEQ = B.TENANT_SEQ.as("owner_tenant_seq");
    public static final Field<String> OWNER_TENANT_NAME = OT.TENANT_NAME.as("owner_tenant_name");
    public static final Field<Boolean> IS_STARRED = when(F.STARRED_SEQ.isNotNull(), inline(true)).otherwise(inline(false)).as("is_starred");
    public static final Field<Long> STARRED_SEQ = F.STARRED_SEQ.as("starred_seq");
    public static final Field<String> REGISTER_USER_ID = C.USER_ID.as("register_user_id");
    public static final Field<String> REGISTER_USER_NAME = C.USER_NAME.as("register_user_name");
    public static final Field<UserStatus> REGISTER_USER_STATUS = C.STATUS.as("register_user_status");
    public static final Field<String> UPDATE_USER_ID = D.USER_ID.as("update_user_id");
    public static final Field<String> UPDATE_USER_NAME = D.USER_NAME.as("update_user_name");
    public static final Field<UserStatus> UPDATE_USER_STATUS = D.STATUS.as("update_user_status");
    public static final Field<Instant> RECENT_UPDATE_DATE = G.UPDATE_DATE.as("recent_update_date");

    /** owner·owner tenant·register/update user·parent folder 조인(구 ResourceSql.BASE_JOIN). */
    public static <R extends Record> SelectJoinStep<R> baseJoin(SelectJoinStep<R> from) {
        return from.join(B).on(A.OWNER_SEQ.eq(B.USER_SEQ))
                .join(OT).on(B.TENANT_SEQ.eq(OT.TENANT_SEQ))
                .join(C).on(A.REGISTER_USER_SEQ.eq(C.USER_SEQ))
                .leftJoin(D).on(A.UPDATE_USER_SEQ.eq(D.USER_SEQ))
                .leftJoin(E).on(A.PARENT_FOLDER_SEQ.eq(E.RESOURCE_SEQ).and(E.RESOURCE_TYPE.eq(inline(ResourceType.FOLDER, E.RESOURCE_TYPE))).and(A.USER_SEQ.eq(E.USER_SEQ)));
    }

    /** + 내 별표·내 최근열람(소유자 = 요청자 본인 기준, 구 ResourceSql.JOIN). */
    public static <R extends Record> SelectJoinStep<R> join(SelectJoinStep<R> from) {
        return baseJoin(from)
                .leftJoin(F).on(A.RESOURCE_SEQ.eq(F.RESOURCE_SEQ).and(A.USER_SEQ.eq(F.USER_SEQ)).and(A.USER_SEQ.eq(F.SHARED_BY_USER_SEQ)))
                .leftJoin(G).on(A.RESOURCE_SEQ.eq(G.RESOURCE_SEQ).and(A.USER_SEQ.eq(G.USER_SEQ)).and(A.USER_SEQ.eq(G.SHARED_BY_USER_SEQ)));
    }

    /**
     * + 뷰어의 별표·최근열람 — 소유자가 남일 수 있는 목록(공유받은 폴더·전체, 구 ResourceSql.viewerJoin). 뷰어가 null 이면 구 {@code = NULL} 처럼 조인 행 없음.
     */
    public static <R extends Record> SelectJoinStep<R> viewerJoin(SelectJoinStep<R> from, Integer viewerSeq) {
        return baseJoin(from)
                .leftJoin(F).on(eq(F.USER_SEQ, viewerSeq).and(A.RESOURCE_SEQ.eq(F.RESOURCE_SEQ)).and(A.USER_SEQ.eq(F.SHARED_BY_USER_SEQ)))
                .leftJoin(G).on(eq(G.USER_SEQ, viewerSeq).and(A.RESOURCE_SEQ.eq(G.RESOURCE_SEQ)).and(A.USER_SEQ.eq(G.SHARED_BY_USER_SEQ)));
    }

    /** 해당 계정 컬럼이 노출 가능 상태(ACTIVE/INACTIVE)일 때만(구 ResourceSql.visibleByActiveUser). */
    public static Condition visibleByActiveUser(Field<Integer> userFk) {
        TbUser u = TB_USER.as("u");
        return exists(selectOne().from(u).where(u.USER_SEQ.eq(userFk)).and(u.STATUS.in(inline(UserStatus.ACTIVE, u.STATUS), inline(UserStatus.INACTIVE, u.STATUS))));
    }

    /**
     * 내 드라이브 OPEN 커서 행의 열람 그룹(0 = 열람 있음, 1 = 없음) — 요청자 {@code userSeq} 의 tb_recent(shared_by = 본인)를 커서 리소스로 직접 조회(구 ResourceSql.RECENT_CURSOR_RANK).
     * ORDER BY 의 g(tb_recent 조인)와 같은 키여야 커서가 맞다.
     */
    public static Function<Field<Long>, Field<Integer>> recentCursorRank(Integer userSeq) {
        return recentCursorRank(userSeq, userSeq);
    }

    /**
     * {@link #recentCursorRank(Integer)} 의 열람한 사람·소유자 지정판 — 공유받은 폴더 목록은 뷰어(requestUserSeq)의 tb_recent(shared_by = 소유자 userSeq).
     */
    public static Function<Field<Long>, Field<Integer>> recentCursorRank(Integer viewerSeq, Integer sharedBySeq) {
        return cursorId -> {
            TbRecent r = TB_RECENT.as("r");
            return when(exists(selectOne().from(r)
                    .where(eq(r.USER_SEQ, viewerSeq))
                    .and(r.RESOURCE_SEQ.eq(cursorId))
                    .and(eq(r.SHARED_BY_USER_SEQ, sharedBySeq))
                    .and(r.UPDATE_DATE.isNotNull())), inline(0)).otherwise(inline(1));
        };
    }

    /**
     * 전체 목록(내 것 + 공유받은 것)용 — 커서 리소스의 소유자를 tb_resource 에서 찾아 뷰어의 tb_recent(shared_by = 소유자)를 본다(구 ResourceSql.RECENT_CURSOR_RANK_BY_RESOURCE).
     */
    public static Function<Field<Long>, Field<Integer>> recentCursorRankByResource(Integer viewerSeq) {
        return cursorId -> {
            TbResource cr = TB_RESOURCE.as("cr");
            TbRecent r = TB_RECENT.as("r");
            return when(exists(selectOne().from(cr)
                    .join(r).on(eq(r.USER_SEQ, viewerSeq).and(r.RESOURCE_SEQ.eq(cr.RESOURCE_SEQ)).and(r.SHARED_BY_USER_SEQ.eq(cr.USER_SEQ)))
                    .where(cr.RESOURCE_SEQ.eq(cursorId))
                    .and(r.UPDATE_DATE.isNotNull())), inline(0)).otherwise(inline(1));
        };
    }

    /**
     * resource-base-where(구 ResourceSql.baseWhere): 커서 게이트(sortBy/sortOrder 별) + 수정일 범위 + 확장자 + 소유자 아이디 + 리소스 타입.
     * 호출부가 user_seq·is_delete·parent 조건과 함께 and 로 잇는다.
     *
     * @param recentCursorRank OPEN 커서 행의 열람 그룹 판정식(커서 id 바인딩을 받아 만든다)
     */
    public static Condition baseWhere(ResourceListQuery q, Function<Field<Long>, Field<Integer>> recentCursorRank) {
        Condition c = noCondition();
        CursorPaginationQuery page = q.getPage();
        if (page != null && present(page.getCursorId()) && present(page.getCursorValue())) {
            c = c.and(cursorGate(q, page, recentCursorRank));
        }
        if (q.getFrom() != null && q.getTo() != null) {
            c = c.and(A.UPDATE_DATE.ge(q.getFrom()).and(A.UPDATE_DATE.lt(q.getTo())));
        }
        if (q.getFileType() != null && q.getFileType().getExtensions() != null && !q.getFileType().getExtensions().isEmpty()) {
            c = c.and(A.EXT.in(q.getFileType().getExtensions()));
        }
        if (present(q.getUserId())) {
            c = c.and(B.USER_ID.eq(q.getUserId())).and(B.STATUS.in(inline(UserStatus.ACTIVE, B.STATUS), inline(UserStatus.INACTIVE, B.STATUS)));
        }
        if (q.getResourceType() != null) {
            c = c.and(A.RESOURCE_TYPE.eq(q.getResourceType()));
        }
        return c;
    }

    /**
     * 커서 게이트 — {@link ResourceCursorJooq}. 정렬 컬럼은 sortBy·sortOrder 가 둘 다 있을 때 그 컬럼, 아니면 이름(VARCHAR, {@code >}).
     */
    private static Condition cursorGate(ResourceListQuery q, CursorPaginationQuery page, Function<Field<Long>, Field<Integer>> recentCursorRank) {
        ResourceSortField sortBy = q.getSortBy();
        SortOrder sortOrder = q.getSortOrder();
        if (sortBy == null) {
            // 기본 정렬 = 이름 자연정렬 ASC(방향 생략) + resource_seq {sortOrder} — orderBy 와 같은 키(10-30, 전엔 이름 원문 비교라 "2" 다음 "10" 이 빠짐)
            return ResourceCursorJooq.of(A, page, SQLDataType.VARCHAR, false, sortOrder == SortOrder.DESC).name();
        }
        boolean explicit = sortBy != null && sortOrder != null;
        Field<?> sortColumn = explicit ? A.field(sortBy.getColumn()) : A.RESOURCE_NAME;
        DataType<?> cursorType = explicit ? ResourceCursorJooq.cursorType(sortBy.getColumn()) : SQLDataType.VARCHAR;
        ResourceCursorJooq cursor = ResourceCursorJooq.of(A, page, cursorType, explicit && sortOrder == SortOrder.DESC);

        if (sortBy == ResourceSortField.NAME) {
            return cursor.name();
        }
        if (sortBy == ResourceSortField.SIZE) {
            return cursor.size(sortColumn);
        }
        if (sortBy == ResourceSortField.OPEN) {
            // 커서 행의 열람 그룹은 tb_recent 를 직접 조회해 판정(recentCursorRank) — ORDER BY 의 g 와 같은 키
            return cursor.open(G.UPDATE_DATE, cursor.recentRank(recentCursorRank));
        }
        return cursor.generic(sortColumn);
    }

    /**
     * resource-order(구 ResourceSql.orderBy): 폴더 먼저, 정렬 필드(NAME 은 자연정렬 3키·OPEN 은 열람일 fallback), resource_seq tie-break.
     * sortOrder 가 없으면 방향을 쓰지 않는다(ASC).
     *
     * @param recentDate OPEN 정렬의 열람일 식 — 조인 결과를 직접 정렬하면 {@code g.update_date}
     */
    public static List<SortField<?>> orderBy(ResourceSortField sortBy, SortOrder sortOrder, Field<Instant> recentDate) {
        List<SortField<?>> order = new ArrayList<>();
        order.add(A.RESOURCE_TYPE.desc());
        if (sortBy != null && sortOrder != null) {
            switch (sortBy) {
                case NAME -> order.addAll(ResourceCursorJooq.nameOrder(A, sortOrder));
                case OPEN -> order.addAll(ResourceCursorJooq.openOrder(A, recentDate, sortOrder));
                default -> order.add(ResourceCursorJooq.direction(A.field(sortBy.getColumn()), sortOrder));
            }
        } else {
            order.addAll(ResourceCursorJooq.nameOrder(A, null));
        }
        order.add(ResourceCursorJooq.direction(A.RESOURCE_SEQ, sortOrder));
        return order;
    }
}
