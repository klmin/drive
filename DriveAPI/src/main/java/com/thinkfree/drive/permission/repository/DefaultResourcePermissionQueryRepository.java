package com.thinkfree.drive.permission.repository;

import com.thinkfree.drive.jooq.tables.TbResource;
import com.thinkfree.drive.jooq.tables.TbShare;
import com.thinkfree.drive.jooq.tables.TbShareTarget;
import com.thinkfree.drive.partition.PartitionKey;
import com.thinkfree.drive.permission.data.ResourcePermissionData;
import com.thinkfree.drive.permission.data.ResourceShareChainData;
import com.thinkfree.drive.permission.data.ResourceShareChainItem;
import com.thinkfree.drive.permission.query.ResourcePermissionQuery;
import com.thinkfree.drive.permission.query.ResourcesPermissionQuery;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.repository.ResourceJooq;
import lombok.RequiredArgsConstructor;
import org.jooq.CommonTableExpression;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Row2;
import org.jooq.Table;
import org.jooq.impl.SQLDataType;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.thinkfree.drive.global.jooq.JooqConditions.eq;
import static com.thinkfree.drive.jooq.Tables.TB_RESOURCE;
import static com.thinkfree.drive.jooq.Tables.TB_SHARE;
import static com.thinkfree.drive.jooq.Tables.TB_SHARE_TARGET;
import static com.thinkfree.drive.permission.repository.PermissionJooq.RESOURCE_RECURSIVE;
import static com.thinkfree.drive.permission.repository.PermissionJooq.col;
import static org.jooq.impl.DSL.*;

/**
 * 리소스 권한 읽기 — jOOQ(구 ResourcePermission.xml 4 statement, 10-30 작업 B P1-② 에서 JdbcClient → jOOQ).
 * <p>
 * 시작 CTE {@code resource_data} = 리소스 자신(단건은 PK, N건은 (user_seq, resource_seq) 행값 IN; isDelete 가 있을 때만 is_delete 조건; 소유 계정 노출 가능).
 * 권한 2종은 {@link PermissionJooq} 의 조상 재귀 → 가장 가까운 링크/대상 공유 → 24컬럼 SELECT. 링크 공유 조건은 "소유자 본인이면 모든 공유, 남이면 공개/조직 링크만".
 * 사슬 2종은 조상 재귀 + 각 조상의 공유·요청자 대상·부모 폴더·대상 폴더 LEFT JOIN, depth 순 → 리소스별로 묶는다(구 resultMap collection — 리소스 첫 등장 순, 사슬은 depth 순).
 * 권한 Y/N 은 NULL 도 false(구 BooleanTypeHandler 와 동일). 단건이 여러 행이면 구 JdbcClient {@code optional()} 처럼 IncorrectResultSizeDataAccessException.
 */
@Repository
@RequiredArgsConstructor
public class DefaultResourcePermissionQueryRepository implements ResourcePermissionQueryRepository {

    private static final String RESOURCE_DATA = "resource_data";

    private final DSLContext dsl;

    @Override
    public ResourcePermissionData findResourcePermission(ResourcePermissionQuery q) {
        TbResource a = TB_RESOURCE.as("a");
        Condition key = eq(a.USER_SEQ, q.getKey().sharedByUserSeq()).and(eq(a.RESOURCE_SEQ, q.getKey().resourceSeq()));
        return DataAccessUtils.singleResult(permissions(key, q.getUserSeq(), q.getIsDelete()));
    }

    @Override
    public List<ResourcePermissionData> findResourcePermissions(ResourcesPermissionQuery q) {
        if (q.getKeys() == null || q.getKeys().isEmpty()) {
            return List.of();
        }
        return permissions(keysIn(q.getKeys()), q.getUserSeq(), q.getIsDelete());
    }

    @Override
    public ResourceShareChainData findResourceShareChain(ResourcePermissionQuery q) {
        TbResource a = TB_RESOURCE.as("a");
        Condition key = eq(a.USER_SEQ, q.getKey().sharedByUserSeq()).and(eq(a.RESOURCE_SEQ, q.getKey().resourceSeq()));
        List<ResourceShareChainData> list = group(chains(key, q.getUserSeq(), q.getIsDelete()));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<ResourceShareChainData> findResourcesShareChain(ResourcesPermissionQuery q) {
        if (q.getKeys() == null || q.getKeys().isEmpty()) {
            return List.of();
        }
        return group(chains(keysIn(q.getKeys()), q.getUserSeq(), q.getIsDelete()));
    }

    // ------------------------------------------------------------------ SQL 조립

    private List<ResourcePermissionData> permissions(Condition key, Integer userSeq, Boolean isDelete) {
        return PermissionJooq.permissionFrom(dsl.withRecursive(seed(key, isDelete), recursive(isDelete), PermissionJooq.nearestLink(userSeq, true), PermissionJooq.nearestTarget(userSeq))
                        .select(PermissionJooq.columns())
                        .from(table(name(RESOURCE_DATA)).as("r")))
                .fetch(rec -> {
                    PermissionJooq.Row r = PermissionJooq.read(rec);
                    return ResourcePermissionData.builder()
                            .userSeq(r.userSeq()).resourceSeq(r.resourceSeq())
                            .shareSeq(r.shareSeq()).shareResourceSeq(r.shareResourceSeq()).shareType(r.shareType())
                            .shareCanView(r.shareCanView()).shareCanEdit(r.shareCanEdit()).shareCanDownload(r.shareCanDownload())
                            .shareCanUpload(r.shareCanUpload()).shareCanDelete(r.shareCanDelete()).shareCanShare(r.shareCanShare()).shareDepth(r.shareDepth())
                            .shareTargetShareSeq(r.shareTargetShareSeq()).shareTargetSeq(r.shareTargetSeq()).shareTargetShareResourceSeq(r.shareTargetShareResourceSeq())
                            .shareTargetShareType(r.shareTargetShareType())
                            .shareTargetCanView(r.shareTargetCanView()).shareTargetCanEdit(r.shareTargetCanEdit()).shareTargetCanDownload(r.shareTargetCanDownload())
                            .shareTargetCanUpload(r.shareTargetCanUpload()).shareTargetCanDelete(r.shareTargetCanDelete()).shareTargetCanShare(r.shareTargetCanShare())
                            .shareTargetDepth(r.shareTargetDepth())
                            .build();
                });
    }

    /** 사슬 — 조상 노드마다 공유(s)·요청자 대상(t)·부모 폴더(p)·대상 폴더(f), depth 순. */
    private List<ResourceShareChainItem> chains(Condition key, Integer userSeq, Boolean isDelete) {
        Table<Record> r = table(name(RESOURCE_DATA)).as("r");
        Table<Record> rr = table(RESOURCE_RECURSIVE).as("rr");
        TbShare s = TB_SHARE.as("s");
        TbShareTarget t = TB_SHARE_TARGET.as("t");
        TbResource p = TB_RESOURCE.as("p");
        TbResource f = TB_RESOURCE.as("f");
        Field<Long> resourceSeq = col("r", "resource_seq", SQLDataType.BIGINT);
        Field<Integer> rUser = col("r", "user_seq", SQLDataType.INTEGER);
        Field<Long> ancestor = col("rr", "ancestor_resource_seq", SQLDataType.BIGINT);
        Field<String> resourceName = col("r", "resource_name", SQLDataType.VARCHAR);
        Field<Long> parentFolderSeq = col("r", "parent_folder_seq", SQLDataType.BIGINT);
        Field<Integer> depth = col("rr", "depth", SQLDataType.INTEGER);
        return dsl.withRecursive(seed(key, isDelete), recursive(isDelete))
                .select(resourceSeq, ancestor, rUser, resourceName, parentFolderSeq,
                        p.RESOURCE_NAME.as("parent_folder_name"), p.FULL_PATH_SEQ, p.FULL_PATH,
                        f.RESOURCE_SEQ.as("farthest_share_resource_seq"), f.RESOURCE_NAME.as("farthest_resource_name"),
                        s.SHARE_SEQ, s.SHARE_TYPE, t.SHARE_TARGET_SEQ, depth)
                .from(r)
                .join(rr).on(rUser.eq(col("rr", "user_seq", SQLDataType.INTEGER)).and(resourceSeq.eq(col("rr", "resource_seq", SQLDataType.BIGINT))))
                .leftJoin(s).on(s.USER_SEQ.eq(rUser).and(s.RESOURCE_SEQ.eq(ancestor)))
                .leftJoin(t).on(eq(t.USER_SEQ, userSeq).and(t.SHARE_SEQ.eq(s.SHARE_SEQ)).and(t.RESOURCE_SEQ.eq(s.RESOURCE_SEQ)))
                .leftJoin(p).on(p.USER_SEQ.eq(rUser).and(p.RESOURCE_SEQ.eq(parentFolderSeq)).and(p.RESOURCE_TYPE.eq(inline(ResourceType.FOLDER, p.RESOURCE_TYPE))))
                .leftJoin(f).on(f.USER_SEQ.eq(rUser).and(f.RESOURCE_SEQ.eq(t.RESOURCE_SEQ)).and(f.RESOURCE_TYPE.eq(inline(ResourceType.FOLDER, f.RESOURCE_TYPE))))
                .orderBy(depth)
                .fetch(rec -> ResourceShareChainItem.builder()
                        .userSeq(rec.get(rUser))
                        .resourceSeq(rec.get(resourceSeq))
                        .ancestorResourceSeq(rec.get(ancestor))
                        .resourceName(rec.get(resourceName))
                        .parentFolderSeq(rec.get(parentFolderSeq))
                        .parentFolderName(rec.get("parent_folder_name", String.class))
                        .fullPathSeq(rec.get(p.FULL_PATH_SEQ))
                        .fullPath(rec.get(p.FULL_PATH))
                        .farthestShareResourceSeq(rec.get("farthest_share_resource_seq", Long.class))
                        .farthestResourceName(rec.get("farthest_resource_name", String.class))
                        .shareSeq(rec.get(s.SHARE_SEQ))
                        .shareType(rec.get(s.SHARE_TYPE))
                        .shareTargetSeq(rec.get(t.SHARE_TARGET_SEQ))
                        .depth(rec.get(depth))
                        .build());
    }

    /** 시작 CTE resource_data — 리소스 자신(+ isDelete 가 있을 때만 is_delete, 소유 계정 노출 가능). */
    private static CommonTableExpression<?> seed(Condition key, Boolean isDelete) {
        TbResource a = TB_RESOURCE.as("a");
        return name(RESOURCE_DATA).as(
                select(a.RESOURCE_SEQ, a.RESOURCE_SEQ.as("ancestor_resource_seq"), a.PARENT_FOLDER_SEQ.as("parent_folder_seq"), a.USER_SEQ, a.RESOURCE_NAME)
                        .from(a)
                        .where(key)
                        .and(isDelete != null ? a.IS_DELETE.eq(isDelete) : noCondition())
                        .and(ResourceJooq.visibleByActiveUser(a.USER_SEQ)));
    }

    /** 조상 재귀 — 재귀 단계의 is_delete 조건도 isDelete 가 있을 때만. */
    private static CommonTableExpression<?> recursive(Boolean isDelete) {
        return PermissionJooq.resourceRecursive(RESOURCE_DATA, isDelete != null ? TB_RESOURCE.as("a").IS_DELETE.eq(isDelete) : noCondition());
    }

    /** (user_seq, resource_seq) 행값 IN. */
    private static Condition keysIn(List<PartitionKey> keys) {
        TbResource a = TB_RESOURCE.as("a");
        List<Row2<Integer, Long>> rows = keys.stream().map(k -> row(k.sharedByUserSeq(), k.resourceSeq())).toList();
        return row(a.USER_SEQ, a.RESOURCE_SEQ).in(rows);
    }

    /** depth 순 행을 리소스(user_seq, resource_seq)별로 묶는다 — 구 resultMap {@code resourcePermissionChainMap}(id = resource_seq) 과 같은 순서. */
    private static List<ResourceShareChainData> group(List<ResourceShareChainItem> rows) {
        Map<PartitionKey, List<ResourceShareChainItem>> byResource = new LinkedHashMap<>();
        for (ResourceShareChainItem item : rows) {
            byResource.computeIfAbsent(new PartitionKey(item.getUserSeq(), item.getResourceSeq()), k -> new ArrayList<>()).add(item);
        }
        List<ResourceShareChainData> result = new ArrayList<>(byResource.size());
        byResource.forEach((key, chains) -> result.add(ResourceShareChainData.builder()
                .userSeq(key.sharedByUserSeq()).resourceSeq(key.resourceSeq()).chains(chains).build()));
        return result;
    }
}
