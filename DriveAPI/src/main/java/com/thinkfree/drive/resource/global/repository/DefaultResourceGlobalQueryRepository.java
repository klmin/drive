package com.thinkfree.drive.resource.global.repository;

import com.thinkfree.drive.jooq.tables.TbResource;
import com.thinkfree.drive.jooq.tables.TbShare;
import com.thinkfree.drive.jooq.tables.TbShareTarget;
import com.thinkfree.drive.resource.global.data.ResourceGlobalData;
import com.thinkfree.drive.resource.query.ResourceListQuery;
import com.thinkfree.drive.resource.repository.ResourceJooq;
import com.thinkfree.drive.user.enums.UserStatus;
import lombok.RequiredArgsConstructor;
import org.jooq.CommonTableExpression;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Select;
import org.jooq.SortField;
import org.jooq.Table;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

import static com.thinkfree.drive.global.jooq.JooqConditions.eq;
import static com.thinkfree.drive.global.jooq.JooqConditions.present;
import static com.thinkfree.drive.jooq.Tables.TB_RESOURCE;
import static com.thinkfree.drive.jooq.Tables.TB_SHARE;
import static com.thinkfree.drive.jooq.Tables.TB_SHARE_TARGET;
import static com.thinkfree.drive.resource.repository.ResourceJooq.*;
import static org.jooq.impl.DSL.*;

/**
 * 전체 목록 — jOOQ(구 ResourceGlobalMapper.xml findAll, 10-30 작업 B P1 에서 JdbcClient → jOOQ).
 * <p>
 * CTE 순서 = resource_data(내 리소스 전부: user_seq·is_delete + 필터, 정렬·LIMIT) → shared_data(나를 대상으로 한 공유, 공유자 계정 노출 가능)
 * → shared_recursive(공유 리소스와 그 하위, is_delete 일치) → shared_resource(중첩 공유 중복은 depth 가 작은 1건) → shared_final(같은 필터·정렬·LIMIT)
 * → resource_shared(UNION ALL) → 최종 정렬·LIMIT. 세 번의 정렬은 전부 SELECT 결과(파생 테이블 a)를 정렬하므로 OPEN 열람일은 {@code a.recent_update_date}.
 * shared_final 은 shared_resource 를 별칭 a 로 두고 내 리소스와 같은 조인·필터({@link ResourceJooq} — 별칭으로 렌더링)를 건다.
 * OPEN 커서의 열람 그룹 판정은 커서 리소스의 소유자를 찾아 뷰어의 tb_recent 를 본다({@link ResourceJooq#recentCursorRankByResource}). 검색어는 이름 부분 일치(LIKE).
 */
@Repository
@RequiredArgsConstructor
public class DefaultResourceGlobalQueryRepository implements ResourceGlobalQueryRepository {

    /** resource-global-select — 뷰어의 별표·최근열람 조인 컬럼 포함, 정렬용 numeric/text part 까지. */
    private static final List<Field<?>> COLUMNS = List.of(A.RESOURCE_SEQ, A.USER_SEQ, A.OWNER_SEQ, OWNER_ID, OWNER_NAME, OWNER_STATUS, OWNER_TENANT_SEQ, OWNER_TENANT_NAME,
            A.RESOURCE_TYPE, A.RESOURCE_NAME, A.SIZE_BYTE, A.IS_LOCKED, A.EXT, IS_STARRED, STARRED_SEQ,
            REGISTER_USER_ID, REGISTER_USER_NAME, REGISTER_USER_STATUS, A.REGISTER_DATE,
            UPDATE_USER_ID, UPDATE_USER_NAME, UPDATE_USER_STATUS, A.UPDATE_DATE, RECENT_UPDATE_DATE,
            A.RESOURCE_NAME_NUMERIC_PART, A.RESOURCE_NAME_TEXT_PART);

    /** 파생 테이블 a 의 열람일 — 세 정렬의 OPEN 키. */
    private static final Field<Instant> A_RECENT_UPDATE_DATE = field(name("a", RECENT_UPDATE_DATE.getName()), RECENT_UPDATE_DATE.getDataType());

    private final DSLContext dsl;

    @Override
    public List<ResourceGlobalData> findAll(ResourceListQuery q) {
        Integer userSeq = q.getUserSeq();
        Field<Integer> pageSize = val(q.getPage() == null ? null : q.getPage().getPageSize(), SQLDataType.INTEGER);
        Condition filter = baseWhere(q, recentCursorRankByResource(userSeq));
        if (present(q.getSearchKeyword())) {
            filter = filter.and(A.RESOURCE_NAME.like(concat(inline("%"), val(q.getSearchKeyword()), inline("%"))));
        }
        List<SortField<?>> order = orderBy(q.getSortBy(), q.getSortOrder(), A_RECENT_UPDATE_DATE);

        // resource_data: 내 리소스
        CommonTableExpression<?> resourceData = name("resource_data").as(sorted(
                viewerJoin(select(COLUMNS).from(A), userSeq)
                        .where(eq(A.USER_SEQ, userSeq))
                        .and(eq(A.IS_DELETE, q.getIsDelete()))
                        .and(filter), order, pageSize));

        // shared_data: 나를 대상으로 한 공유
        TbShareTarget st = TB_SHARE_TARGET.as("st");
        TbShare s = TB_SHARE.as("s");
        CommonTableExpression<?> sharedData = name("shared_data").as(
                select(s.RESOURCE_SEQ, s.USER_SEQ)
                        .from(st)
                        .join(s).on(s.USER_SEQ.eq(st.SHARED_BY_USER_SEQ).and(st.SHARE_SEQ.eq(s.SHARE_SEQ)))
                        .where(eq(st.USER_SEQ, userSeq))
                        .and(visibleByActiveUser(st.SHARED_BY_USER_SEQ)));

        // shared_recursive: 공유 리소스와 그 하위(is_delete 일치), 깊이
        TbResource r = TB_RESOURCE.as("r");
        Table<Record> sd = table(name("shared_data")).as("s");
        Table<Record> rb = table(name("shared_recursive")).as("b");
        CommonTableExpression<?> sharedRecursive = name("shared_recursive").as(
                select(r.RESOURCE_SEQ, r.USER_SEQ, r.OWNER_SEQ, r.PARENT_FOLDER_SEQ, r.RESOURCE_NAME, r.RESOURCE_TYPE, r.EXT, r.SIZE_BYTE, r.IS_LOCKED,
                        r.REGISTER_USER_SEQ, r.UPDATE_USER_SEQ, r.REGISTER_DATE, r.UPDATE_DATE, r.RESOURCE_NAME_NUMERIC_PART, r.RESOURCE_NAME_TEXT_PART, inline(0).as("depth"))
                        .from(r)
                        .join(sd).on(r.USER_SEQ.eq(field(name("s", "user_seq"), SQLDataType.INTEGER)).and(r.RESOURCE_SEQ.eq(field(name("s", "resource_seq"), SQLDataType.BIGINT))))
                        .where(eq(r.IS_DELETE, q.getIsDelete()))
                        .unionAll(select(A.RESOURCE_SEQ, A.USER_SEQ, A.OWNER_SEQ, A.PARENT_FOLDER_SEQ, A.RESOURCE_NAME, A.RESOURCE_TYPE, A.EXT, A.SIZE_BYTE, A.IS_LOCKED,
                                A.REGISTER_USER_SEQ, A.UPDATE_USER_SEQ, A.REGISTER_DATE, A.UPDATE_DATE, A.RESOURCE_NAME_NUMERIC_PART, A.RESOURCE_NAME_TEXT_PART,
                                field(name("b", "depth"), SQLDataType.INTEGER).plus(inline(1)))
                                .from(A)
                                .join(rb).on(A.USER_SEQ.eq(field(name("b", "user_seq"), SQLDataType.INTEGER)).and(A.PARENT_FOLDER_SEQ.eq(field(name("b", "resource_seq"), SQLDataType.BIGINT))))
                                .where(eq(A.IS_DELETE, q.getIsDelete()))));

        // shared_resource: 중첩 공유로 같은 리소스가 여러 번 나오면 depth 가 작은 1건
        Table<Record> sr = table(name("shared_recursive")).as("sr");
        Table<?> sharedOrdered = select(sr.asterisk(), rowNumber().over(partitionBy(field(name("sr", "resource_seq"))).orderBy(field(name("sr", "depth")))).as("rn"))
                .from(sr)
                .asTable("shared_ordered");
        CommonTableExpression<?> sharedResource = name("shared_resource").as(
                select(asterisk()).from(sharedOrdered).where(field(name("rn"), SQLDataType.INTEGER).eq(inline(1))));

        // shared_final: 공유받은 리소스에 같은 조인·필터·정렬·LIMIT
        CommonTableExpression<?> sharedFinal = name("shared_final").as(sorted(
                viewerJoin(select(COLUMNS).from(table(name("shared_resource")).as(A.getName())), userSeq)
                        .where(filter), order, pageSize));

        CommonTableExpression<?> resourceShared = name("resource_shared").as(
                select(asterisk()).from(table(name("resource_data")))
                        .unionAll(select(asterisk()).from(table(name("shared_final")))));

        Table<Record> a = table(name("resource_shared")).as("a");
        List<Field<?>> out = COLUMNS.stream().<Field<?>>map(f -> field(name("a", f.getName()), f.getDataType())).toList();
        return dsl.withRecursive(resourceData, sharedData, sharedRecursive, sharedResource, sharedFinal, resourceShared)
                .select(out)
                .from(a)
                .orderBy(order)
                .limit(pageSize)
                .fetch(rec -> ResourceGlobalData.builder()
                        .resourceSeq(rec.get(out(out, A.RESOURCE_SEQ)))
                        .userSeq(rec.get(out(out, A.USER_SEQ)))
                        .ownerSeq(rec.get(out(out, A.OWNER_SEQ)))
                        .ownerId(rec.get(out(out, OWNER_ID)))
                        .ownerName(rec.get(out(out, OWNER_NAME)))
                        .ownerStatus(rec.get(out(out, OWNER_STATUS)))
                        .ownerTenantSeq(rec.get(out(out, OWNER_TENANT_SEQ)))
                        .ownerTenantName(rec.get(out(out, OWNER_TENANT_NAME)))
                        .resourceType(rec.get(out(out, A.RESOURCE_TYPE)))
                        .resourceName(rec.get(out(out, A.RESOURCE_NAME)))
                        .ext(rec.get(out(out, A.EXT)))
                        .sizeByte(rec.get(out(out, A.SIZE_BYTE)))
                        .isLocked(Boolean.TRUE.equals(rec.get(out(out, A.IS_LOCKED))))
                        .isStarred(Boolean.TRUE.equals(rec.get(out(out, IS_STARRED))))
                        .starredSeq(rec.get(out(out, STARRED_SEQ)))
                        .registerUserId(rec.get(out(out, REGISTER_USER_ID)))
                        .registerUserName(rec.get(out(out, REGISTER_USER_NAME)))
                        .registerUserStatus(rec.get(out(out, REGISTER_USER_STATUS)))
                        .registerDate(rec.get(out(out, A.REGISTER_DATE)))
                        .updateUserId(rec.get(out(out, UPDATE_USER_ID)))
                        .updateUserName(rec.get(out(out, UPDATE_USER_NAME)))
                        .updateUserStatus(rec.get(out(out, UPDATE_USER_STATUS)))
                        .updateDate(rec.get(out(out, A.UPDATE_DATE)))
                        .recentUpdateDate(rec.get(out(out, RECENT_UPDATE_DATE)))
                        .build());
    }

    /** {@code SELECT * FROM (조인 SELECT) a ORDER BY … FETCH FIRST n} — 구 SQL 처럼 SELECT 결과(파생 테이블 a)를 정렬한다. */
    private static Select<Record> sorted(Select<?> inner, List<SortField<?>> order, Field<Integer> pageSize) {
        return select(asterisk()).from(inner.asTable(A.getName())).orderBy(order).limit(pageSize);
    }

    /** 바깥 SELECT 의 같은 이름 컬럼(조회 컬럼 상수 → 파생 테이블 a 의 컬럼). */
    @SuppressWarnings("unchecked")
    private static <T> Field<T> out(List<Field<?>> out, Field<T> column) {
        return (Field<T>) out.get(COLUMNS.indexOf(column));
    }
}
