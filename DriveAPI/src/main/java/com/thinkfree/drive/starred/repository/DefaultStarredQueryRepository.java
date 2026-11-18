package com.thinkfree.drive.starred.repository;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.common.search.enums.SortField;
import com.thinkfree.drive.common.search.enums.SortOrder;
import com.thinkfree.drive.jooq.tables.TbResource;
import com.thinkfree.drive.jooq.tables.TbStarred;
import com.thinkfree.drive.jooq.tables.TbTenant;
import com.thinkfree.drive.jooq.tables.TbUser;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.repository.ResourceCursorJooq;
import com.thinkfree.drive.resource.repository.ResourceJooq;
import com.thinkfree.drive.starred.data.StarredData;
import com.thinkfree.drive.starred.query.StarredListQuery;
import com.thinkfree.drive.user.enums.UserStatus;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.DataType;
import org.jooq.Field;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.thinkfree.drive.global.jooq.JooqConditions.eq;
import static com.thinkfree.drive.global.jooq.JooqConditions.present;
import static com.thinkfree.drive.jooq.Tables.TB_RESOURCE;
import static com.thinkfree.drive.jooq.Tables.TB_STARRED;
import static com.thinkfree.drive.jooq.Tables.TB_TENANT;
import static com.thinkfree.drive.jooq.Tables.TB_USER;
import static org.jooq.impl.DSL.*;

/**
 * tb_starred 읽기 — jOOQ(구 StarredMapper.xml 의 findAll, 10-30 작업 B P1 에서 JdbcClient → jOOQ). 쓰기는 {@link DefaultStarredRepository}.
 * <p>
 * 별칭 a=tb_starred, b=tb_resource, c=소유자, f=부모 폴더. 커서 게이트·정렬 키는 {@link ResourceCursorJooq}(정렬 대상 = b) — 기본 정렬이 NAME ASC(VARCHAR, {@code >}), 정렬 컬럼은 항상 b.
 * is_delete 는 조인 조건(값이 null 이면 행 없음, XML 동일). Y/N·enum·시각은 생성 코드의 변환기가 매핑.
 */
@Repository
@RequiredArgsConstructor
public class DefaultStarredQueryRepository implements StarredQueryRepository {

    private static final TbStarred A = TB_STARRED.as("a");
    private static final TbResource B = TB_RESOURCE.as("b");
    private static final TbUser C = TB_USER.as("c");
    private static final TbTenant OT = TB_TENANT.as("ot");
    private static final TbUser D = TB_USER.as("d");
    private static final TbUser E = TB_USER.as("e");
    private static final TbResource F = TB_RESOURCE.as("f");

    private final DSLContext dsl;

    @Override
    public List<StarredData> findAll(StarredListQuery q) {
        Condition where = eq(A.USER_SEQ, q.getUserSeq());
        CursorPaginationQuery page = q.getPage();
        if (page != null && present(page.getCursorId()) && present(page.getCursorValue())) {
            where = where.and(cursorGate(q, page));
        }
        if (q.getResourceType() != null) {
            where = where.and(B.RESOURCE_TYPE.eq(q.getResourceType()));
        }
        if (q.getFrom() != null && q.getTo() != null) {
            where = where.and(B.UPDATE_DATE.ge(q.getFrom()).and(B.UPDATE_DATE.lt(q.getTo())));
        }
        if (q.getFileType() != null && q.getFileType().getExtensions() != null && !q.getFileType().getExtensions().isEmpty()) {
            where = where.and(B.EXT.in(q.getFileType().getExtensions()));
        }
        if (present(q.getUserId())) {
            where = where.and(C.USER_ID.eq(q.getUserId())).and(C.STATUS.in(inline(UserStatus.ACTIVE, C.STATUS), inline(UserStatus.INACTIVE, C.STATUS)));
        }
        where = where.and(ResourceJooq.visibleByActiveUser(A.SHARED_BY_USER_SEQ));

        return dsl.select(A.STARRED_SEQ, A.USER_SEQ, B.RESOURCE_SEQ, A.SHARED_BY_USER_SEQ,
                        C.USER_ID.as("owner_id"), C.USER_NAME.as("owner_name"), C.STATUS.as("owner_status"), C.TENANT_SEQ.as("owner_tenant_seq"), OT.TENANT_NAME.as("owner_tenant_name"),
                        B.OWNER_SEQ, B.RESOURCE_TYPE, B.PARENT_FOLDER_SEQ, F.RESOURCE_NAME.as("parent_folder_name"), B.RESOURCE_NAME, B.EXT, F.FULL_PATH_SEQ, F.FULL_PATH, B.SIZE_BYTE, B.IS_LOCKED,
                        D.USER_ID.as("resource_register_user_id"), D.USER_NAME.as("resource_register_user_name"), D.STATUS.as("resource_register_user_status"), B.REGISTER_DATE.as("resource_register_date"),
                        E.USER_ID.as("resource_update_user_id"), E.USER_NAME.as("resource_update_user_name"), E.STATUS.as("resource_update_user_status"), B.UPDATE_DATE.as("resource_update_date"),
                        A.REGISTER_DATE.as("starred_register_date"))
                .from(A)
                .join(B).on(A.SHARED_BY_USER_SEQ.eq(B.USER_SEQ).and(A.RESOURCE_SEQ.eq(B.RESOURCE_SEQ)).and(eq(B.IS_DELETE, q.getIsDelete())))
                .join(C).on(B.OWNER_SEQ.eq(C.USER_SEQ))
                .join(OT).on(C.TENANT_SEQ.eq(OT.TENANT_SEQ))
                .join(D).on(B.REGISTER_USER_SEQ.eq(D.USER_SEQ))
                .leftJoin(E).on(B.UPDATE_USER_SEQ.eq(E.USER_SEQ))
                .leftJoin(F).on(B.USER_SEQ.eq(F.USER_SEQ).and(B.PARENT_FOLDER_SEQ.eq(F.RESOURCE_SEQ)).and(F.RESOURCE_TYPE.eq(inline(ResourceType.FOLDER, F.RESOURCE_TYPE))))
                .where(where)
                .orderBy(orderBy(q.getSortBy(), q.getSortOrder()))
                .limit(val(page == null ? null : page.getPageSize(), SQLDataType.INTEGER))
                .fetch(r -> StarredData.builder()
                        .starredSeq(r.get(A.STARRED_SEQ))
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
                        .fullPathSeq(r.get(F.FULL_PATH_SEQ))
                        .fullPath(r.get(F.FULL_PATH))
                        .sizeByte(r.get(B.SIZE_BYTE))
                        .isLocked(r.get(B.IS_LOCKED))
                        .resourceRegisterUserId(r.get("resource_register_user_id", String.class))
                        .resourceRegisterUserName(r.get("resource_register_user_name", String.class))
                        .resourceRegisterUserStatus(r.get("resource_register_user_status", UserStatus.class))
                        .resourceRegisterDate(r.get("resource_register_date", Instant.class))
                        .resourceUpdateUserId(r.get("resource_update_user_id", String.class))
                        .resourceUpdateUserName(r.get("resource_update_user_name", String.class))
                        .resourceUpdateUserStatus(r.get("resource_update_user_status", UserStatus.class))
                        .resourceUpdateDate(r.get("resource_update_date", Instant.class))
                        .starredRegisterDate(r.get("starred_register_date", Instant.class))
                        .build());
    }

    /** starred-where 의 커서 게이트 — 정렬 컬럼은 항상 b.(둘 다 있을 때), 기본은 b.resource_name ASC(VARCHAR, {@code >}). 분기 = NAME 자연정렬 / SIZE(폴더 0 처리) / 일반. */
    private static Condition cursorGate(StarredListQuery q, CursorPaginationQuery page) {
        SortField sortBy = q.getSortBy();
        SortOrder sortOrder = q.getSortOrder();
        if (sortBy == null) {
            // 기본 정렬 = 이름 자연정렬 ASC(방향 생략) + a.resource_seq {sortOrder} — orderBy 와 같은 키(10-30, 전엔 이름 원문 비교라 "2" 다음 "10" 이 빠짐)
            return ResourceCursorJooq.of(B, page, SQLDataType.VARCHAR, false, sortOrder == SortOrder.DESC).name();
        }
        boolean explicit = sortBy != null && sortOrder != null;
        Field<?> sortColumn = explicit ? B.field(sortBy.getColumn()) : B.RESOURCE_NAME;
        DataType<?> cursorType = explicit ? ResourceCursorJooq.cursorType(sortBy.getColumn()) : SQLDataType.VARCHAR;
        ResourceCursorJooq cursor = ResourceCursorJooq.of(B, page, cursorType, explicit && sortOrder == SortOrder.DESC);
        if (sortBy == SortField.NAME) {
            return cursor.name();
        }
        if (sortBy == SortField.SIZE) {
            return cursor.size(sortColumn);
        }
        return cursor.generic(sortColumn);
    }

    /** starred-order: 폴더 먼저, 정렬 필드(둘 다 있을 때 — NAME 자연정렬 3키, 나머지 b.컬럼), 기본 NAME 자연정렬(방향 생략), a.resource_seq tie-break(sortOrder 방향, 없으면 ASC). */
    private static List<org.jooq.SortField<?>> orderBy(SortField sortBy, SortOrder sortOrder) {
        List<org.jooq.SortField<?>> order = new ArrayList<>();
        order.add(B.RESOURCE_TYPE.desc());
        if (sortBy != null && sortOrder != null) {
            if (sortBy == SortField.NAME) {
                order.addAll(ResourceCursorJooq.nameOrder(B, sortOrder));
            } else {
                order.add(ResourceCursorJooq.direction(B.field(sortBy.getColumn()), sortOrder));
            }
        } else {
            order.addAll(ResourceCursorJooq.nameOrder(B, null));
        }
        order.add(ResourceCursorJooq.direction(A.RESOURCE_SEQ, sortOrder));
        return order;
    }
}
