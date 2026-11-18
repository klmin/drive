package com.thinkfree.drive.resource.repository;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.common.search.enums.SortOrder;
import com.thinkfree.drive.global.jooq.JooqCursors;
import com.thinkfree.drive.jooq.tables.TbResource;
import com.thinkfree.drive.resource.enums.ResourceType;
import org.jooq.Condition;
import org.jooq.DataType;
import org.jooq.Field;
import org.jooq.Row4;
import org.jooq.Row5;
import org.jooq.SortField;
import org.jooq.impl.SQLDataType;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

import static org.jooq.impl.DSL.*;

/**
 * tb_resource 를 정렬하는 커서 목록의 공용 커서 게이트·정렬 조각 — 내 드라이브·공유받은 폴더·전체({@link ResourceJooq})·별표·공유받은 목록이 같이 쓴다.
 * 목록마다 다른 것은 tb_resource 별칭({@code r})·정렬 컬럼·방향·OPEN 의 열람 테이블뿐이고, 게이트 모양은 구 SQL 과 같다:
 * 폴더(0)·파일(1) 순위가 커서 행보다 뒤이거나, 같은 순위에서 (정렬 키, resource_seq) 가 커서보다 뒤인 행.
 * <p>
 * 요청 값(커서 타입·커서 값·커서 id)은 전부 바인딩 — 커서 값은 문자열로 바인딩하고 SQL 에서 정렬 컬럼 타입으로 CAST(날짜는 오프셋을 붙여서, 구 SQL 과 같음).
 */
public final class ResourceCursorJooq {

    private static final String NAME_COLLATION = "drive_name_sort";

    private final TbResource r;
    private final boolean desc;
    private final boolean seqDesc;
    private final DataType<?> cursorType;
    private final Field<String> cursorTypeName;
    private final Field<String> cursorValue;
    private final Field<Long> cursorId;

    private ResourceCursorJooq(TbResource r, CursorPaginationQuery page, DataType<?> cursorType, boolean desc, boolean seqDesc) {
        this.r = r;
        this.desc = desc;
        this.seqDesc = seqDesc;
        this.cursorType = cursorType;
        this.cursorTypeName = val(page.getCursorType() == null ? null : page.getCursorType().name(), SQLDataType.VARCHAR);
        this.cursorValue = val(cursorType == SQLDataType.TIMESTAMPWITHTIMEZONE ? JooqCursors.timestamp(page.getCursorValue()) : String.valueOf(page.getCursorValue()), SQLDataType.VARCHAR);
        this.cursorId = val(page.getCursorId(), SQLDataType.BIGINT);
    }

    /**
     * @param r          정렬 대상 tb_resource 별칭 — 순위·tie-break(resource_seq)·NAME 키를 여기서 본다
     * @param cursorType 정렬 컬럼 타입({@link #cursorType(String)})
     * @param desc       정렬 방향이 DESC 인지(비교 연산자 {@code <})
     */
    public static ResourceCursorJooq of(TbResource r, CursorPaginationQuery page, DataType<?> cursorType, boolean desc) {
        return new ResourceCursorJooq(r, page, cursorType, desc, desc);
    }

    /**
     * 정렬 키와 tie-break(resource_seq) 방향이 다른 경우 — 기본 정렬(sortBy 없음)의 ORDER BY 는 키 방향이 고정(이름 ASC·공유일 DESC 등)이고
     * resource_seq 만 요청 sortOrder 방향이라, 게이트도 그대로 맞춘다(10-30 — 둘을 같은 방향으로 보던 게이트가 동률 행을 건너뛰거나 되풀이하던 문제).
     *
     * @param seqDesc tie-break 방향이 DESC 인지
     */
    public static ResourceCursorJooq of(TbResource r, CursorPaginationQuery page, DataType<?> cursorType, boolean desc, boolean seqDesc) {
        return new ResourceCursorJooq(r, page, cursorType, desc, seqDesc);
    }

    /** 정렬 컬럼 이름 → 커서 값 CAST 타입(size_byte = BIGINT, resource_name = VARCHAR, 나머지 날짜). */
    public static DataType<?> cursorType(String column) {
        return "size_byte".equals(column) ? SQLDataType.BIGINT
                : "resource_name".equals(column) ? SQLDataType.VARCHAR : SQLDataType.TIMESTAMPWITHTIMEZONE;
    }

    /** NAME — 자연정렬 5키 행 비교 + 이름 동률 tie-break. */
    public Condition name() {
        return after()
                .or(sameRank().and(nameRow()))
                .or(sameRank().and(same(r.RESOURCE_NAME, typed())).and(seqAfter()));
    }

    /** 자연정렬 5키 행 비교만(숫자 접두 유무, 숫자, 나머지, 이름, seq) — 폴더/파일 순위 없이 정렬하는 휴지통도 쓴다. */
    public Condition nameRow() {
        Field<String> text = cast(cursorValue, SQLDataType.CLOB);
        Field<String> digits = function("regexp_substr", String.class, text, inline("^[0-9]{1,9}"));
        Field<Boolean> rowNoDigits = field(r.RESOURCE_NAME_NUMERIC_PART.isNull());
        Field<Integer> rowDigits = coalesce(r.RESOURCE_NAME_NUMERIC_PART, inline(0));
        Field<String> rowText = r.RESOURCE_NAME_TEXT_PART.collate(NAME_COLLATION);
        Field<String> rowName = r.RESOURCE_NAME.collate(NAME_COLLATION);
        Field<Boolean> curNoDigits = field(digits.isNull());
        Field<Integer> curDigits = coalesce(cast(digits, SQLDataType.INTEGER), inline(0));
        Field<String> curText = function("regexp_replace", String.class, text, inline("^[0-9]{0,9}"), inline(""));
        Field<String> curName = cast(cursorValue, SQLDataType.VARCHAR);
        if (desc != seqDesc) {
            // 이름 키와 resource_seq 방향이 다름(기본 정렬) — 이름 키 4개로 앞뒤, 같으면 resource_seq 를 seqDesc 방향으로
            Row4<Boolean, Integer, String, String> rowKey = row(rowNoDigits, rowDigits, rowText, rowName);
            Row4<Boolean, Integer, String, String> cursorKey = row(curNoDigits, curDigits, curText, curName);
            return (desc ? rowKey.lt(cursorKey) : rowKey.gt(cursorKey)).or(rowKey.eq(cursorKey).and(seqAfter()));
        }
        Row5<Boolean, Integer, String, String, Long> rowKey = row(rowNoDigits, rowDigits, rowText, rowName, r.RESOURCE_SEQ);
        Row5<Boolean, Integer, String, String, Long> cursorKey = row(curNoDigits, curDigits, curText, curName, cursorId);
        return desc ? rowKey.lt(cursorKey) : rowKey.gt(cursorKey);
    }

    /** 정렬 컬럼 타입으로 CAST 한 커서 값. */
    public Field<?> typedValue() {
        return typed();
    }

    /** 커서 리소스 id 바인딩. */
    public Field<Long> cursorId() {
        return cursorId;
    }

    /** SIZE — 폴더 구간은 크기 NULL = 0 으로 보고 seq 만, 파일 구간은 (크기, seq). */
    public Condition size(Field<?> sortColumn) {
        Condition cursorIsFolder = cursorTypeName.eq(inline("FOLDER"));
        Condition cursorIsFile = cursorTypeName.eq(inline("FILE"));
        Condition rowIsFolder = r.RESOURCE_TYPE.eq(inline(ResourceType.FOLDER, r.RESOURCE_TYPE));
        Condition rowIsFile = r.RESOURCE_TYPE.eq(inline(ResourceType.FILE, r.RESOURCE_TYPE));
        return after()
                .or(cursorIsFolder.and(rowIsFolder).and(coalesce(r.SIZE_BYTE, inline(0L)).eq(coalesce(cast(cursorValue, SQLDataType.BIGINT), inline(0L)))).and(seqAfter()))
                .or(cursorIsFile.and(rowIsFile).and(after(sortColumn, desc, typed())))
                .or(cursorIsFile.and(rowIsFile).and(same(sortColumn, typed())).and(seqAfter()));
    }

    /**
     * OPEN — 열람 있는 행(0) 먼저, 미열람(1) 뒤, 그 안에서 (열람일 없으면 수정일, seq). ORDER BY 와 같은 키.
     *
     * @param recentUpdateDate 조인한 열람 테이블의 열람일(행 그룹 판정·정렬 키)
     * @param cursorRecentRank 커서 행의 열람 그룹 — 열람 테이블을 커서 리소스로 직접 조회한 식
     */
    public Condition open(Field<Instant> recentUpdateDate, Field<Integer> cursorRecentRank) {
        Field<Integer> rowRecentRank = when(recentUpdateDate.isNull(), inline(1)).otherwise(inline(0));
        Field<Instant> recentDate = coalesce(recentUpdateDate, r.UPDATE_DATE);
        Field<?> cursorDate = cast(cursorValue, SQLDataType.TIMESTAMPWITHTIMEZONE);
        return after()
                .or(sameRank().and(rowRecentRank.gt(cursorRecentRank)
                        .or(rowRecentRank.eq(cursorRecentRank).and(after(recentDate, desc, cursorDate)
                                .or(same(recentDate, cursorDate).and(seqAfter()))))));
    }

    /** 그 밖의 정렬 컬럼 — (정렬 키, seq). */
    public Condition generic(Field<?> sortColumn) {
        return after()
                .or(sameRank().and(after(sortColumn, desc, typed())))
                .or(sameRank().and(same(sortColumn, typed())).and(seqAfter()));
    }

    private Field<?> typed() {
        return cast(cursorValue, cursorType);
    }

    private Field<Integer> typeRank() {
        return when(r.RESOURCE_TYPE.eq(inline(ResourceType.FOLDER, r.RESOURCE_TYPE)), inline(0)).otherwise(inline(1));
    }

    private Field<Integer> cursorRank() {
        return when(cursorTypeName.eq(inline("FOLDER")), inline(0)).otherwise(inline(1));
    }

    private Condition after() {
        return typeRank().gt(cursorRank());
    }

    private Condition sameRank() {
        return typeRank().eq(cursorRank());
    }

    private Condition seqAfter() {
        return after(r.RESOURCE_SEQ, seqDesc, cursorId);
    }

    /** 정렬 방향의 "뒤" — DESC 면 {@code <}, 아니면 {@code >}. 정렬 키와 CAST 한 커서 값의 타입은 SQL 이 맞추므로 렌더링만 한다. */
    @SuppressWarnings("unchecked")
    public static Condition after(Field<?> left, boolean desc, Field<?> right) {
        Field<Object> l = (Field<Object>) left;
        Field<Object> rr = (Field<Object>) right;
        return desc ? l.lt(rr) : l.gt(rr);
    }

    @SuppressWarnings("unchecked")
    public static Condition same(Field<?> left, Field<?> right) {
        return ((Field<Object>) left).eq((Field<Object>) right);
    }

    // ------------------------------------------------------------------ 정렬

    /** 자연정렬 3키(숫자 부분, 나머지, 이름 — 뒤 둘은 drive_name_sort). {@code sortOrder} 가 null 이면 방향을 쓰지 않는다. */
    public static List<SortField<?>> nameOrder(TbResource r, SortOrder sortOrder) {
        return List.of(direction(r.RESOURCE_NAME_NUMERIC_PART, sortOrder),
                direction(r.RESOURCE_NAME_TEXT_PART.collate(NAME_COLLATION), sortOrder),
                direction(r.RESOURCE_NAME.collate(NAME_COLLATION), sortOrder));
    }

    /** OPEN 정렬 키 — 열람 없음 뒤로, (열람일 없으면 수정일). */
    public static List<SortField<?>> openOrder(TbResource r, Field<Instant> recentUpdateDate, SortOrder sortOrder) {
        return List.of(field(recentUpdateDate.isNull()).sortDefault(), direction(coalesce(recentUpdateDate, r.UPDATE_DATE), sortOrder));
    }

    /** {@code sortOrder} 방향 — null 이면 방향 생략(구 SQL 의 빈 {@code ${sortOrder}}). */
    public static SortField<?> direction(Field<?> field, SortOrder sortOrder) {
        if (sortOrder == null) {
            return field.sortDefault();
        }
        return sortOrder == SortOrder.DESC ? field.desc() : field.asc();
    }

    /** 커서 리소스 id 를 받아 판정식을 만드는 함수형 — OPEN 열람 그룹 판정식이 커서 id 바인딩을 공유하도록. */
    public Field<Integer> recentRank(Function<Field<Long>, Field<Integer>> byCursorId) {
        return byCursorId.apply(cursorId);
    }
}
