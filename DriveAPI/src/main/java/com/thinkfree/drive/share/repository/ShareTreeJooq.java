package com.thinkfree.drive.share.repository;

import com.thinkfree.drive.jooq.tables.TbResource;
import com.thinkfree.drive.jooq.tables.TbShare;
import org.jooq.CommonTableExpression;
import org.jooq.Condition;
import org.jooq.DataType;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.SQLDataType;

import static com.thinkfree.drive.global.jooq.JooqConditions.eq;
import static com.thinkfree.drive.jooq.Tables.TB_RESOURCE;
import static com.thinkfree.drive.jooq.Tables.TB_SHARE;
import static org.jooq.impl.DSL.*;

/**
 * 공유 트리 탐색 공용 jOOQ 조각 — 구 ShareMapper.xml·ShareTargetMapper.xml 의 {@code <sql>} 루트 CTE({@code root_data})와 재귀({@code resource_recursive}).
 * 공유 nearest·해제 범위({@link DefaultShareQueryRepository})와 공유 대상 nearest·해제 범위({@code DefaultShareTargetQueryRepository})가 같이 쓴다.
 * <p>
 * root_data = (resource_seq, user_seq, parent_folder_seq, resource_type), resource_recursive = (resource_seq = 루트 고정, node_seq = 지금 노드, parent_folder_seq, user_seq, resource_type, depth).
 * CTE 컬럼은 이름으로 참조. 조건 값이 null 이면 구 {@code = NULL} 처럼 참이 아님.
 */
public final class ShareTreeJooq {

    private ShareTreeJooq() {
    }

    public static final String ROOT_DATA = "root_data";
    public static final String RESOURCE_RECURSIVE = "resource_recursive";

    /** share-resource-root-data-cte / share-nearest-root-resource-cte — 리소스 하나(user_seq·resource_seq·is_delete). */
    public static CommonTableExpression<?> rootByResource(Integer userSeq, Long resourceSeq, Boolean isDelete) {
        TbResource r = TB_RESOURCE;
        return name(ROOT_DATA).as(
                select(r.RESOURCE_SEQ, r.PARENT_FOLDER_SEQ, r.USER_SEQ, r.RESOURCE_TYPE)
                        .from(r)
                        .where(eq(r.USER_SEQ, userSeq))
                        .and(eq(r.RESOURCE_SEQ, resourceSeq))
                        .and(eq(r.IS_DELETE, isDelete)));
    }

    /** share-nearest-share-root-data-cte — 공유의 리소스(is_delete 일치). */
    public static CommonTableExpression<?> rootByShare(Integer userSeq, Long shareSeq, Boolean isDelete) {
        TbShare a = TB_SHARE.as("a");
        TbResource b = TB_RESOURCE.as("b");
        return name(ROOT_DATA).as(
                select(a.RESOURCE_SEQ, a.USER_SEQ, b.PARENT_FOLDER_SEQ, b.RESOURCE_TYPE)
                        .from(a)
                        .join(b).on(a.USER_SEQ.eq(b.USER_SEQ).and(a.RESOURCE_SEQ.eq(b.RESOURCE_SEQ)).and(eq(b.IS_DELETE, isDelete)))
                        .where(eq(a.USER_SEQ, userSeq))
                        .and(eq(a.SHARE_SEQ, shareSeq)));
    }

    /**
     * share-nearest-resource-ancestor/descendant-recursive — 루트에서 조상(부모 방향) 또는 하위(자식 방향)로, 재귀 단계도 is_delete 일치.
     *
     * @param descendants true = 하위(a.parent_folder_seq = b.node_seq), false = 조상(a.resource_seq = b.parent_folder_seq)
     */
    public static CommonTableExpression<?> resourceRecursive(boolean descendants, Boolean isDelete) {
        TbResource a = TB_RESOURCE.as("a");
        Table<Record> b = table(name(RESOURCE_RECURSIVE)).as("b");
        Condition step = descendants
                ? a.PARENT_FOLDER_SEQ.eq(col("b", "node_seq", SQLDataType.BIGINT))
                : a.RESOURCE_SEQ.eq(col("b", "parent_folder_seq", SQLDataType.BIGINT));
        return name(RESOURCE_RECURSIVE).as(
                select(col(null, "resource_seq", SQLDataType.BIGINT), col(null, "resource_seq", SQLDataType.BIGINT).as("node_seq"), col(null, "parent_folder_seq", SQLDataType.BIGINT),
                        col(null, "user_seq", SQLDataType.INTEGER), col(null, "resource_type", SQLDataType.VARCHAR), inline(0).as("depth"))
                        .from(table(name(ROOT_DATA)))
                        .unionAll(select(col("b", "resource_seq", SQLDataType.BIGINT), a.RESOURCE_SEQ.as("node_seq"), a.PARENT_FOLDER_SEQ, a.USER_SEQ, a.RESOURCE_TYPE.coerce(SQLDataType.VARCHAR),
                                col("b", "depth", SQLDataType.INTEGER).plus(inline(1)))
                                .from(a)
                                .join(b).on(a.USER_SEQ.eq(col("b", "user_seq", SQLDataType.INTEGER)).and(step).and(eq(a.IS_DELETE, isDelete)))));
    }

    /** CTE·파생 테이블 컬럼(이름 참조) — {@code table} 이 null 이면 한정자 없이. */
    public static <T> Field<T> col(String table, String column, DataType<T> type) {
        return table == null ? field(name(column), type) : field(name(table, column), type);
    }
}
