package com.thinkfree.drive.trash.repository;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.common.search.enums.SortOrder;
import com.thinkfree.drive.jooq.tables.TbResource;
import com.thinkfree.drive.jooq.tables.TbTenant;
import com.thinkfree.drive.jooq.tables.TbTrash;
import com.thinkfree.drive.jooq.tables.TbUser;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.repository.ResourceCursorJooq;
import com.thinkfree.drive.trash.data.TrashData;
import com.thinkfree.drive.trash.enums.TrashSortField;
import com.thinkfree.drive.trash.query.TrashListQuery;
import com.thinkfree.drive.user.enums.UserStatus;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SortField;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.thinkfree.drive.global.jooq.JooqConditions.eq;
import static com.thinkfree.drive.global.jooq.JooqConditions.present;
import static com.thinkfree.drive.jooq.Tables.TB_RESOURCE;
import static com.thinkfree.drive.jooq.Tables.TB_TENANT;
import static com.thinkfree.drive.jooq.Tables.TB_TRASH;
import static com.thinkfree.drive.jooq.Tables.TB_USER;
import static com.thinkfree.drive.resource.repository.ResourceCursorJooq.after;
import static com.thinkfree.drive.resource.repository.ResourceCursorJooq.direction;
import static com.thinkfree.drive.resource.repository.ResourceCursorJooq.same;
import static org.jooq.impl.DSL.*;

/**
 * tb_trash 읽기 — jOOQ(구 TrashMapper.xml 의 findAll + 조각 trash-join/trash-where/trash-order, 10-30 작업 B P1 에서 JdbcClient → jOOQ: 7테이블 조인 DTO·동적 정렬·커서). 쓰기는 {@link DefaultTrashRepository}.
 * <p>
 * 조인: 리소스 b(INNER) · 소유자 c + 소유자 테넌트 ot(INNER — 테넌트 없는 슈퍼어드민 소유 리소스는 목록에서 빠짐, XML 동일) · 삭제자 d + 테넌트 d_ot(INNER) · 리소스 등록자 f(INNER) · 수정자 e(LEFT) · 부모 폴더 g(LEFT, FOLDER 만).
 * 커서(cursorId·cursorValue 둘 다 있을 때): NAME = 자연정렬 행 비교 + 이름 동률 tie-break, SIZE = COALESCE(size,0) 비교 + tie-break, REGISTER/없음 = a.register_date(삭제일) 비교 + tie-break. 방향은 sortOrder(ASC → '>', 그 외 '<').
 * 정렬: sortBy·sortOrder 둘 다 있을 때만 그 정렬(NAME 3키·REGISTER 삭제일·SIZE 는 b.size_byte), 없으면 a.register_date DESC. tie-break `a.resource_seq {sortOrder}`(sortOrder 없으면 방향 생략 = ASC, XML `${sortOrder}` 동일).
 * 삭제자 아이디 필터는 구 `CommonMapper.user-where-visible-alias`(d.status IN ACTIVE/INACTIVE) 포함. 날짜 범위는 둘 다 있을 때만 `a.register_date >= :from AND a.register_date < :to`(설정 시간대 하루 경계, 컨버터가 계산). 확장자 IN 은 `FileType.extensions`.
 * Y/N·enum·시각은 생성 코드의 변환기가 매핑.
 */
@Repository
@RequiredArgsConstructor
public class DefaultTrashQueryRepository implements TrashQueryRepository {

    private static final TbTrash A = TB_TRASH.as("a");
    private static final TbResource B = TB_RESOURCE.as("b");
    private static final TbUser C = TB_USER.as("c");
    private static final TbTenant OT = TB_TENANT.as("ot");
    private static final TbUser D = TB_USER.as("d");
    private static final TbTenant D_OT = TB_TENANT.as("d_ot");
    private static final TbUser F = TB_USER.as("f");
    private static final TbUser E = TB_USER.as("e");
    private static final TbResource G = TB_RESOURCE.as("g");

    private final DSLContext dsl;

    @Override
    public List<TrashData> findAll(TrashListQuery q) {
        CursorPaginationQuery page = q.getPage();
        Condition where = eq(A.USER_SEQ, q.getUserSeq());
        if (present(page.getCursorId()) && present(page.getCursorValue())) {
            where = where.and(cursorGate(q.getSortBy(), q.getSortOrder(), page));
        }
        if (q.getResourceType() != null) {
            where = where.and(B.RESOURCE_TYPE.eq(q.getResourceType()));
        }
        if (present(q.getFrom()) && present(q.getTo())) {
            where = where.and(A.REGISTER_DATE.ge(q.getFrom()).and(A.REGISTER_DATE.lt(q.getTo())));
        }
        if (present(q.getFileType())) {
            where = where.and(B.EXT.in(q.getFileType().getExtensions()));
        }
        if (present(q.getDeleteUserId())) {
            where = where.and(D.USER_ID.eq(q.getDeleteUserId())).and(D.STATUS.in(inline(UserStatus.ACTIVE, D.STATUS), inline(UserStatus.INACTIVE, D.STATUS)));
        }

        return dsl.select(A.TRASH_SEQ, A.RESOURCE_SEQ, A.USER_SEQ,
                        C.USER_SEQ.as("owner_seq"), C.USER_ID.as("owner_id"), C.USER_NAME.as("owner_name"), C.STATUS.as("owner_status"), C.TENANT_SEQ.as("owner_tenant_seq"), OT.TENANT_NAME.as("owner_tenant_name"),
                        B.RESOURCE_TYPE, B.PARENT_FOLDER_SEQ, G.RESOURCE_NAME.as("parent_folder_name"), B.RESOURCE_NAME, B.EXT, G.FULL_PATH_SEQ, G.FULL_PATH, B.SIZE_BYTE, B.IS_LOCKED,
                        F.USER_ID.as("register_user_id"), F.USER_NAME.as("register_user_name"), F.STATUS.as("register_user_status"), B.REGISTER_DATE,
                        E.USER_ID.as("update_user_id"), E.USER_NAME.as("update_user_name"), E.STATUS.as("update_user_status"), B.UPDATE_DATE,
                        A.REGISTER_USER_SEQ.as("delete_user_seq"), D.USER_ID.as("delete_user_id"), D.USER_NAME.as("delete_user_name"), D.TENANT_SEQ.as("delete_user_tenant_seq"),
                        D_OT.TENANT_NAME.as("delete_user_tenant_name"), D.STATUS.as("delete_user_status"),
                        A.REGISTER_DATE.as("delete_date"))
                .from(A)
                .join(B).on(A.RESOURCE_SEQ.eq(B.RESOURCE_SEQ).and(A.USER_SEQ.eq(B.USER_SEQ)))
                .join(C).on(B.OWNER_SEQ.eq(C.USER_SEQ))
                .join(OT).on(C.TENANT_SEQ.eq(OT.TENANT_SEQ))
                .join(D).on(A.REGISTER_USER_SEQ.eq(D.USER_SEQ))
                .join(D_OT).on(D.TENANT_SEQ.eq(D_OT.TENANT_SEQ))
                .join(F).on(B.REGISTER_USER_SEQ.eq(F.USER_SEQ))
                .leftJoin(E).on(B.UPDATE_USER_SEQ.eq(E.USER_SEQ))
                .leftJoin(G).on(B.PARENT_FOLDER_SEQ.eq(G.RESOURCE_SEQ).and(G.USER_SEQ.eq(B.USER_SEQ)).and(G.RESOURCE_TYPE.eq(inline(ResourceType.FOLDER, G.RESOURCE_TYPE))))
                .where(where)
                .orderBy(orderBy(q.getSortBy(), q.getSortOrder()))
                .limit(val(page.getPageSize(), SQLDataType.INTEGER))
                .fetch(r -> TrashData.builder()
                        .trashSeq(r.get(A.TRASH_SEQ))
                        .resourceSeq(r.get(A.RESOURCE_SEQ))
                        .userSeq(r.get(A.USER_SEQ))
                        .ownerSeq(r.get("owner_seq", Integer.class))
                        .ownerId(r.get("owner_id", String.class))
                        .ownerName(r.get("owner_name", String.class))
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
                        .registerUserId(r.get("register_user_id", String.class))
                        .registerUserName(r.get("register_user_name", String.class))
                        .registerUserStatus(r.get("register_user_status", UserStatus.class))
                        .registerDate(r.get(B.REGISTER_DATE))
                        .updateUserId(r.get("update_user_id", String.class))
                        .updateUserName(r.get("update_user_name", String.class))
                        .updateUserStatus(r.get("update_user_status", UserStatus.class))
                        .updateDate(r.get(B.UPDATE_DATE))
                        .deleteUserSeq(r.get("delete_user_seq", Integer.class))
                        .deleteUserId(r.get("delete_user_id", String.class))
                        .deleteUserName(r.get("delete_user_name", String.class))
                        .deleteUserTenantSeq(r.get("delete_user_tenant_seq", Integer.class))
                        .deleteUserTenantName(r.get("delete_user_tenant_name", String.class))
                        .deleteUserStatus(r.get("delete_user_status", UserStatus.class))
                        .deleteDate(r.get("delete_date", Instant.class))
                        .build());
    }

    /** trash-where 의 커서 게이트 — sortBy 별 비교 컬럼/타입, 값 동률이면 a.resource_seq tie-break. sortBy 가 없으면 삭제일 DESC('<'). */
    private static Condition cursorGate(TrashSortField sortBy, SortOrder sortOrder, CursorPaginationQuery page) {
        boolean desc = !(sortBy != null && sortOrder == SortOrder.ASC);
        if (sortBy == TrashSortField.NAME) {
            ResourceCursorJooq cursor = ResourceCursorJooq.of(B, page, SQLDataType.VARCHAR, desc);
            return cursor.nameRow()
                    .or(same(B.RESOURCE_NAME, cursor.typedValue()).and(after(A.RESOURCE_SEQ, desc, cursor.cursorId())));
        }
        if (sortBy == TrashSortField.SIZE) {
            ResourceCursorJooq cursor = ResourceCursorJooq.of(B, page, SQLDataType.BIGINT, desc);
            Field<Long> size = coalesce(B.SIZE_BYTE, inline(0L));
            Field<?> cursorSize = coalesce(cursor.typedValue(), inline(0L));
            return after(size, desc, cursorSize)
                    .or(same(size, cursorSize).and(after(A.RESOURCE_SEQ, desc, cursor.cursorId())));
        }
        ResourceCursorJooq cursor = ResourceCursorJooq.of(B, page, SQLDataType.TIMESTAMPWITHTIMEZONE, desc);
        // 기본 정렬(sortBy 없음)의 tie-break 는 a.resource_seq {sortOrder} — orderBy 와 같은 방향(10-30, 전엔 삭제일과 같은 DESC 로 비교해 삭제일 동률 행이 빠지거나 반복)
        boolean seqDesc = sortBy == null ? sortOrder == SortOrder.DESC : desc;
        return after(A.REGISTER_DATE, desc, cursor.typedValue())
                .or(same(A.REGISTER_DATE, cursor.typedValue()).and(after(A.RESOURCE_SEQ, seqDesc, cursor.cursorId())));
    }

    /** trash-order: sortBy·sortOrder 둘 다 있을 때만 그 정렬, 없으면 삭제일 DESC. tie-break 는 `a.resource_seq {sortOrder}`(없으면 방향 생략). */
    private static List<SortField<?>> orderBy(TrashSortField sortBy, SortOrder sortOrder) {
        List<SortField<?>> order = new ArrayList<>();
        if (sortBy != null && sortOrder != null) {
            switch (sortBy) {
                case NAME -> order.addAll(ResourceCursorJooq.nameOrder(B, sortOrder));
                case REGISTER -> order.add(direction(A.REGISTER_DATE, sortOrder));
                // 커서 게이트와 같은 식(폴더 크기 NULL = 0) — 그대로 b.size_byte 면 PG 는 ASC 에서 NULL 을 맨 뒤·DESC 에서 맨 앞에 둬
                // 커서(COALESCE 0)와 어긋나 페이지 누락·중복(구 MySQL 은 ASC 에서 NULL 이 맨 앞이라 0바이트 동률 때만 어긋났다)
                case SIZE -> order.add(direction(coalesce(B.SIZE_BYTE, inline(0L)), sortOrder));
                default -> order.add(direction(B.field(sortBy.getColumn()), sortOrder));
            }
        } else {
            order.add(A.REGISTER_DATE.desc());
        }
        order.add(direction(A.RESOURCE_SEQ, sortOrder));
        return order;
    }
}
