package com.thinkfree.drive.permission.repository;

import com.thinkfree.drive.jooq.tables.TbResource;
import com.thinkfree.drive.jooq.tables.TbShare;
import com.thinkfree.drive.jooq.tables.TbShareTarget;
import com.thinkfree.drive.jooq.tables.TbUser;
import com.thinkfree.drive.share.enums.ShareType;
import org.jooq.CommonTableExpression;
import org.jooq.Condition;
import org.jooq.DataType;
import org.jooq.Field;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.SelectField;
import org.jooq.SelectJoinStep;
import org.jooq.SelectOnConditionStep;
import org.jooq.Table;
import org.jooq.impl.SQLDataType;

import java.util.List;

import static com.thinkfree.drive.global.jooq.JooqConditions.eq;
import static com.thinkfree.drive.global.jooq.JooqConditions.ne;
import static com.thinkfree.drive.jooq.Tables.TB_RESOURCE;
import static com.thinkfree.drive.jooq.Tables.TB_SHARE;
import static com.thinkfree.drive.jooq.Tables.TB_SHARE_TARGET;
import static com.thinkfree.drive.jooq.Tables.TB_USER;
import static org.jooq.impl.DSL.*;

/**
 * 리소스 권한 조회 공용 jOOQ 조각 — 구 ResourcePermission.xml 과 SharePermission.xml 이 각자 들고 있던 같은 꼴의 CTE 3개와 SELECT 절(10-30 작업 B P1-② 에서 PermissionSql 문자열 → jOOQ).
 * <p>
 * 흐름 = 시작 CTE(리소스 자신: resource_seq·parent_folder_seq·user_seq) → {@link #resourceRecursive 조상 재귀}(depth 0 = 자신)
 * → {@link #nearestLink 가장 가까운 조상의 링크 공유(l)}·{@link #nearestTarget 요청자 대상 공유(t)}(ROW_NUMBER … ORDER BY depth = 1) → {@link #permissionSelect 두 CTE 를 LEFT JOIN 한 24컬럼}.
 * CTE 컬럼은 이름으로 참조하고, Y/N·enum 은 생성 코드 컬럼의 데이터 타입(변환기 포함)을 붙여 읽는다. 요청자가 null 이면 구 {@code = NULL} 처럼 참이 아님.
 */
final class PermissionJooq {

    private PermissionJooq() {
    }

    static final Name RESOURCE_RECURSIVE = name("resource_recursive");
    private static final Name NEAREST_LINK = name("nearest_link_share_resource");
    private static final Name NEAREST_TARGET = name("nearest_share_target_resource");

    /**
     * 조상 재귀 CTE {@code resource_recursive}(resource_seq·ancestor_resource_seq·parent_folder_seq·user_seq·depth).
     *
     * @param seed         시작 CTE 이름(resource_seq·parent_folder_seq·user_seq 컬럼 필요)
     * @param isDeleteStep 재귀 단계 조건 — 구 XML 의 {@code <if isDelete>} 는 {@code a.is_delete = :isDelete} 또는 없음
     */
    static CommonTableExpression<?> resourceRecursive(String seed, Condition isDeleteStep) {
        TbResource a = TB_RESOURCE.as("a");
        Table<Record> b = table(RESOURCE_RECURSIVE).as("b");
        return RESOURCE_RECURSIVE.as(
                select(col(null, "resource_seq", SQLDataType.BIGINT), col(null, "resource_seq", SQLDataType.BIGINT).as("ancestor_resource_seq"),
                        col(null, "parent_folder_seq", SQLDataType.BIGINT).as("parent_folder_seq"), col(null, "user_seq", SQLDataType.INTEGER), inline(0).as("depth"))
                        .from(table(name(seed)))
                        .unionAll(select(col("b", "resource_seq", SQLDataType.BIGINT), a.RESOURCE_SEQ.as("ancestor_resource_seq"), a.PARENT_FOLDER_SEQ, a.USER_SEQ.as("user_seq"),
                                col("b", "depth", SQLDataType.INTEGER).plus(inline(1)).as("depth"))
                                .from(a)
                                .join(b).on(a.USER_SEQ.eq(col("b", "user_seq", SQLDataType.INTEGER)).and(a.RESOURCE_SEQ.eq(col("b", "parent_folder_seq", SQLDataType.BIGINT))).and(isDeleteStep))));
    }

    /**
     * 가장 가까운 조상의 링크 공유(l) — 남의 리소스에 열려 있는 링크는 전체 공개, 또는 같은 테넌트의 조직 공개(ou = 소유자, ru = 요청자).
     *
     * @param ownerOrOpen true = 소유자 본인이면 유형과 무관하게 가장 가까운 공유, 남이면 열린 링크만(구 ResourcePermission.xml) / false = 열린 링크만(SharePermission)
     */
    static CommonTableExpression<?> nearestLink(Integer requester, boolean ownerOrOpen) {
        Table<Record> r = table(RESOURCE_RECURSIVE).as("r");
        TbUser ou = TB_USER.as("ou");
        TbUser ru = TB_USER.as("ru");
        TbShare s = TB_SHARE.as("s");
        Field<Integer> rUser = col("r", "user_seq", SQLDataType.INTEGER);
        Condition open = s.SHARE_TYPE.eq(inline(ShareType.PUBLIC_LINK, s.SHARE_TYPE))
                .or(s.SHARE_TYPE.eq(inline(ShareType.ORG_LINK, s.SHARE_TYPE)).and(ou.TENANT_SEQ.eq(ru.TENANT_SEQ)));
        Condition link = ownerOrOpen ? eq(rUser, requester).or(ne(rUser, requester).and(open)) : open;
        Table<?> ranked = select(col("r", "resource_seq", SQLDataType.BIGINT), rUser, s.RESOURCE_SEQ.as("share_resource_seq"), s.SHARE_SEQ, s.SHARE_TYPE,
                        s.CAN_VIEW, s.CAN_EDIT, s.CAN_DOWNLOAD, s.CAN_UPLOAD, s.CAN_DELETE, s.CAN_SHARE, col("r", "depth", SQLDataType.INTEGER), rank())
                .from(r)
                .leftJoin(ou).on(ou.USER_SEQ.eq(rUser))
                .leftJoin(ru).on(eq(ru.USER_SEQ, requester))
                .join(s).on(s.USER_SEQ.eq(rUser).and(s.RESOURCE_SEQ.eq(col("r", "ancestor_resource_seq", SQLDataType.BIGINT))).and(link))
                .asTable("a");
        return NEAREST_LINK.as(select(asterisk()).from(ranked).where(col(null, "rn", SQLDataType.INTEGER).eq(inline(1))));
    }

    /** 가장 가까운 조상의 요청자 대상 공유(t). */
    static CommonTableExpression<?> nearestTarget(Integer requester) {
        Table<Record> r = table(RESOURCE_RECURSIVE).as("r");
        TbShare s = TB_SHARE.as("s");
        TbShareTarget t = TB_SHARE_TARGET.as("t");
        Field<Integer> rUser = col("r", "user_seq", SQLDataType.INTEGER);
        Table<?> ranked = select(col("r", "resource_seq", SQLDataType.BIGINT), rUser, s.RESOURCE_SEQ.as("share_resource_seq"), s.SHARE_SEQ, t.SHARE_TARGET_SEQ, s.SHARE_TYPE,
                        t.CAN_VIEW, t.CAN_EDIT, t.CAN_DOWNLOAD, t.CAN_UPLOAD, t.CAN_DELETE, t.CAN_SHARE, col("r", "depth", SQLDataType.INTEGER), rank())
                .from(r)
                .join(s).on(s.USER_SEQ.eq(rUser).and(s.RESOURCE_SEQ.eq(col("r", "ancestor_resource_seq", SQLDataType.BIGINT))))
                .join(t).on(eq(t.USER_SEQ, requester).and(t.RESOURCE_SEQ.eq(s.RESOURCE_SEQ)).and(t.SHARED_BY_USER_SEQ.eq(s.USER_SEQ)).and(t.SHARE_SEQ.eq(s.SHARE_SEQ)))
                .asTable("a");
        return NEAREST_TARGET.as(select(asterisk()).from(ranked).where(col(null, "rn", SQLDataType.INTEGER).eq(inline(1))));
    }

    private static Field<Integer> rank() {
        return rowNumber().over(partitionBy(col("r", "resource_seq", SQLDataType.BIGINT)).orderBy(col("r", "depth", SQLDataType.INTEGER))).as("rn");
    }

    // ------------------------------------------------------------------ 24컬럼 SELECT

    private static final List<String> FLAG_COLUMNS = List.of("can_view", "can_edit", "can_download", "can_upload", "can_delete", "can_share");

    private static final Field<Integer> USER_SEQ = col("r", "user_seq", SQLDataType.INTEGER);
    private static final Field<Long> RESOURCE_SEQ = col("r", "resource_seq", SQLDataType.BIGINT);
    private static final Field<Long> L_SHARE_SEQ = col("l", "share_seq", SQLDataType.BIGINT);
    private static final Field<Long> L_SHARE_RESOURCE_SEQ = col("l", "share_resource_seq", SQLDataType.BIGINT);
    private static final Field<ShareType> L_SHARE_TYPE = col("l", "share_type", TB_SHARE.SHARE_TYPE.getDataType());
    private static final List<Field<Boolean>> L_FLAGS = flags("l", TB_SHARE.CAN_VIEW.getDataType());
    private static final Field<Integer> L_DEPTH = col("l", "depth", SQLDataType.INTEGER);
    private static final Field<Long> T_SHARE_SEQ = col("t", "share_seq", SQLDataType.BIGINT);
    private static final Field<Long> T_SHARE_TARGET_SEQ = col("t", "share_target_seq", SQLDataType.BIGINT);
    private static final Field<Long> T_SHARE_RESOURCE_SEQ = col("t", "share_resource_seq", SQLDataType.BIGINT);
    private static final Field<ShareType> T_SHARE_TYPE = col("t", "share_type", TB_SHARE.SHARE_TYPE.getDataType());
    private static final List<Field<Boolean>> T_FLAGS = flags("t", TB_SHARE_TARGET.CAN_VIEW.getDataType());
    private static final Field<Integer> T_DEPTH = col("t", "depth", SQLDataType.INTEGER);

    private static List<Field<Boolean>> flags(String table, DataType<Boolean> type) {
        return FLAG_COLUMNS.stream().map(c -> col(table, c, type)).toList();
    }

    /** 24컬럼(구 permission SELECT 의 별칭 그대로). */
    static List<SelectField<?>> columns() {
        List<SelectField<?>> columns = new java.util.ArrayList<>(List.of(USER_SEQ, RESOURCE_SEQ, L_SHARE_SEQ, L_SHARE_RESOURCE_SEQ, L_SHARE_TYPE));
        for (int i = 0; i < FLAG_COLUMNS.size(); i++) {
            columns.add(L_FLAGS.get(i).as("share_" + FLAG_COLUMNS.get(i)));
        }
        columns.add(L_DEPTH.as("share_depth"));
        columns.add(T_SHARE_SEQ.as("share_target_share_seq"));
        columns.add(T_SHARE_TARGET_SEQ);
        columns.add(T_SHARE_RESOURCE_SEQ.as("share_target_share_resource_seq"));
        columns.add(T_SHARE_TYPE.as("share_target_share_type"));
        for (int i = 0; i < FLAG_COLUMNS.size(); i++) {
            columns.add(T_FLAGS.get(i).as("share_target_" + FLAG_COLUMNS.get(i)));
        }
        columns.add(T_DEPTH.as("share_target_depth"));
        return columns;
    }

    /** 시작 CTE 의 각 리소스에 t·l 을 LEFT JOIN — 호출부가 {@code with…} 의 select 에 이어 붙인다. */
    static <R extends Record> SelectOnConditionStep<R> permissionFrom(SelectJoinStep<R> from) {
        return from
                .leftJoin(table(NEAREST_TARGET).as("t")).on(col("t", "user_seq", SQLDataType.INTEGER).eq(USER_SEQ).and(col("t", "resource_seq", SQLDataType.BIGINT).eq(RESOURCE_SEQ)))
                .leftJoin(table(NEAREST_LINK).as("l")).on(col("l", "user_seq", SQLDataType.INTEGER).eq(USER_SEQ).and(col("l", "resource_seq", SQLDataType.BIGINT).eq(RESOURCE_SEQ)));
    }

    /** {@link #columns} 결과 행 — ResourcePermissionData·SharePermissionData 가 같은 컬럼이라 공용으로 읽는다. 권한 Y/N 은 NULL 도 false(구 BooleanTypeHandler 와 동일). */
    record Row(Integer userSeq, Long resourceSeq,
               Long shareSeq, Long shareResourceSeq, ShareType shareType,
               Boolean shareCanView, Boolean shareCanEdit, Boolean shareCanDownload, Boolean shareCanUpload, Boolean shareCanDelete, Boolean shareCanShare, Integer shareDepth,
               Long shareTargetShareSeq, Long shareTargetSeq, Long shareTargetShareResourceSeq, ShareType shareTargetShareType,
               Boolean shareTargetCanView, Boolean shareTargetCanEdit, Boolean shareTargetCanDownload, Boolean shareTargetCanUpload, Boolean shareTargetCanDelete, Boolean shareTargetCanShare,
               Integer shareTargetDepth) {
    }

    static Row read(Record rec) {
        return new Row(
                rec.get(USER_SEQ), rec.get(RESOURCE_SEQ),
                rec.get(L_SHARE_SEQ), rec.get(L_SHARE_RESOURCE_SEQ), rec.get(L_SHARE_TYPE),
                yes(rec, "share_can_view"), yes(rec, "share_can_edit"), yes(rec, "share_can_download"),
                yes(rec, "share_can_upload"), yes(rec, "share_can_delete"), yes(rec, "share_can_share"),
                rec.get("share_depth", Integer.class),
                rec.get("share_target_share_seq", Long.class), rec.get(T_SHARE_TARGET_SEQ), rec.get("share_target_share_resource_seq", Long.class),
                rec.get("share_target_share_type", ShareType.class),
                yes(rec, "share_target_can_view"), yes(rec, "share_target_can_edit"), yes(rec, "share_target_can_download"),
                yes(rec, "share_target_can_upload"), yes(rec, "share_target_can_delete"), yes(rec, "share_target_can_share"),
                rec.get("share_target_depth", Integer.class));
    }

    private static Boolean yes(Record rec, String column) {
        return Boolean.TRUE.equals(rec.get(column, Boolean.class));
    }

    /** CTE·파생 테이블 컬럼(이름 참조) — {@code table} 이 null 이면 한정자 없이. */
    static <T> Field<T> col(String table, String column, DataType<T> type) {
        return table == null ? field(name(column), type) : field(name(table, column), type);
    }
}
