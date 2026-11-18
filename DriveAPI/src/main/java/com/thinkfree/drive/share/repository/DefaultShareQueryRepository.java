package com.thinkfree.drive.share.repository;

import com.thinkfree.drive.jooq.tables.TbResource;
import com.thinkfree.drive.jooq.tables.TbShare;
import com.thinkfree.drive.jooq.tables.TbShareTarget;
import com.thinkfree.drive.jooq.tables.TbTenant;
import com.thinkfree.drive.jooq.tables.TbUser;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.share.data.*;
import com.thinkfree.drive.share.enums.ShareType;
import com.thinkfree.drive.share.query.*;
import com.thinkfree.drive.user.enums.UserStatus;
import lombok.RequiredArgsConstructor;
import org.jooq.CommonTableExpression;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.SQLDataType;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

import static com.thinkfree.drive.global.jooq.JooqConditions.eq;
import static com.thinkfree.drive.global.jooq.JooqConditions.ne;
import static com.thinkfree.drive.jooq.Tables.*;
import static com.thinkfree.drive.share.repository.ShareTreeJooq.RESOURCE_RECURSIVE;
import static com.thinkfree.drive.share.repository.ShareTreeJooq.col;
import static org.jooq.impl.DSL.*;

/**
 * tb_share 읽기 — jOOQ(구 ShareMapper.xml 의 조회 14 statement, 10-30 작업 B P1-② 에서 JdbcClient → jOOQ). 쓰기는 {@link DefaultShareRepository}.
 * <p>
 * nearest 계열 6개는 XML 의 {@code <sql>} 조각 조합 그대로: 루트 CTE(리소스 / 공유 / rootResourceSeq, {@link ShareTreeJooq}) + 재귀(조상 / 하위) + 가장 가까운 공유(제외 조건 3종) + (최상위 노드) + SELECT.
 * resultMap {@code <collection>} 2개(findShareWithTargets·findUnsharedDescendantsByShare)는 행을 읽어 수동으로 묶는다(둘 다 단일 공유 기준이라 그룹은 하나).
 * 단건이 여러 행이면 구 JdbcClient {@code optional()} 처럼 IncorrectResultSizeDataAccessException.
 * 단순 조인 단건 2종·{@code existsByTenant} 는 JPQL({@link ShareJpaRepository}).
 * Y/N·enum·시각은 생성 코드의 변환기가 매핑(nullable 권한 컬럼은 NULL 유지 — MyBatis Boolean 래퍼와 동일). 조건 값이 null 이면 구 {@code = NULL}·{@code <> NULL} 처럼 참이 아님.
 */
@Repository
@RequiredArgsConstructor
public class DefaultShareQueryRepository implements ShareQueryRepository {

    private static final TbShare A = TB_SHARE.as("a");
    private static final TbResource B = TB_RESOURCE.as("b");
    private static final TbShareTarget C = TB_SHARE_TARGET.as("c");
    private static final TbUser D = TB_USER.as("d");
    private static final TbTenant OT = TB_TENANT.as("ot");

    private static final String NEAREST_SHARE_RESOURCE = "nearest_share_resource";
    private static final String ROOT_NODE = "root_node";

    private final DSLContext dsl;
    private final ShareJpaRepository shareJpaRepository;

    // ------------------------------------------------------------------ 단건·대상

    /** JPQL 생성자 식(5차 패스 — 구 JdbcClient 조인). 조건은 {@code =} 라 null 인자는 null. */
    @Override
    public SharedResourceData findSharedResourceByShare(SharedResourceFindQuery q) {
        return shareJpaRepository.findSharedResourceByShare(q.getShareSeq(), q.getUserSeq(), q.getIsDelete()).orElse(null);
    }

    @Override
    public SharedResourceData findSharedResourceByResource(SharedResourceFindQuery q) {
        return shareJpaRepository.findSharedResourceByResource(q.getResourceSeq(), q.getUserSeq(), q.getIsDelete()).orElse(null);
    }

    /** 공유 + 대상 목록(대상 없으면 LEFT JOIN 1행 — share_target_seq NULL 이면 대상 없음, 구 resultMap notNullColumn). 대상은 share_target_seq DESC. */
    @Override
    public ShareWithTargetsData findShareWithTargets(ShareWithTargetsFindQuery q) {
        var rows = dsl.select(A.SHARE_SEQ, A.USER_SEQ, A.RESOURCE_SEQ, A.SHARE_TYPE, A.CAN_VIEW, A.CAN_EDIT, A.CAN_DOWNLOAD, A.CAN_UPLOAD, A.CAN_DELETE, A.CAN_SHARE,
                        A.PASSWORD, A.IS_PASSWORD_ENABLED, A.EXPIRE_DATE,
                        C.SHARE_TARGET_SEQ, C.USER_SEQ.as("target_user_seq"), D.USER_ID.as("target_user_id"), D.USER_NAME.as("target_user_name"), D.STATUS.as("target_user_status"),
                        D.TENANT_SEQ.as("target_tenant_seq"), OT.TENANT_NAME.as("target_tenant_name"),
                        C.CAN_VIEW.as("target_can_view"), C.CAN_EDIT.as("target_can_edit"), C.CAN_DOWNLOAD.as("target_can_download"), C.CAN_UPLOAD.as("target_can_upload"),
                        C.CAN_DELETE.as("target_can_delete"), C.CAN_SHARE.as("target_can_share"))
                .from(A)
                .join(B).on(A.RESOURCE_SEQ.eq(B.RESOURCE_SEQ).and(A.USER_SEQ.eq(B.USER_SEQ)))
                .leftJoin(C).on(A.USER_SEQ.eq(C.SHARED_BY_USER_SEQ).and(A.SHARE_SEQ.eq(C.SHARE_SEQ)).and(A.RESOURCE_SEQ.eq(C.RESOURCE_SEQ)))
                .leftJoin(D).on(C.USER_SEQ.eq(D.USER_SEQ))
                .leftJoin(OT).on(D.TENANT_SEQ.eq(OT.TENANT_SEQ))
                .where(defaultWhere(q))
                .orderBy(C.SHARE_TARGET_SEQ.desc())
                .fetch();
        if (rows.isEmpty()) {
            return null;
        }
        List<ShareWithTargetsData.ShareTargetItem> targets = new ArrayList<>(rows.size());
        for (var r : rows) {
            Long shareTargetSeq = r.get(C.SHARE_TARGET_SEQ);
            if (shareTargetSeq != null) {
                targets.add(ShareWithTargetsData.ShareTargetItem.builder()
                        .shareTargetSeq(shareTargetSeq)
                        .userSeq(r.get("target_user_seq", Integer.class))
                        .userId(r.get("target_user_id", String.class))
                        .userName(r.get("target_user_name", String.class))
                        .userStatus(r.get("target_user_status", UserStatus.class))
                        .tenantSeq(r.get("target_tenant_seq", Integer.class))
                        .tenantName(r.get("target_tenant_name", String.class))
                        .canView(r.get("target_can_view", Boolean.class))
                        .canEdit(r.get("target_can_edit", Boolean.class))
                        .canDownload(r.get("target_can_download", Boolean.class))
                        .canUpload(r.get("target_can_upload", Boolean.class))
                        .canDelete(r.get("target_can_delete", Boolean.class))
                        .canShare(r.get("target_can_share", Boolean.class))
                        .build());
            }
        }
        var first = rows.get(0);
        ShareType shareType = first.get(A.SHARE_TYPE);
        return ShareWithTargetsData.builder()
                .shareSeq(first.get(A.SHARE_SEQ))
                .userSeq(first.get(A.USER_SEQ))
                .resourceSeq(first.get(A.RESOURCE_SEQ))
                .shareType(shareType == null ? null : shareType.name())
                .canView(first.get(A.CAN_VIEW))
                .canEdit(first.get(A.CAN_EDIT))
                .canDownload(first.get(A.CAN_DOWNLOAD))
                .canUpload(first.get(A.CAN_UPLOAD))
                .canDelete(first.get(A.CAN_DELETE))
                .canShare(first.get(A.CAN_SHARE))
                .password(first.get(A.PASSWORD))
                .isPasswordEnabled(first.get(A.IS_PASSWORD_ENABLED))
                .expireDate(first.get(A.EXPIRE_DATE))
                .targets(targets)
                .build();
    }

    @Override
    public List<ShareUserData> findUsersByShare(ShareUsersFindQuery q) {
        return dsl.select(D.USER_SEQ, D.USER_ID, D.USER_NAME, D.STATUS, D.TENANT_SEQ, OT.TENANT_NAME)
                .from(A)
                .join(B).on(A.RESOURCE_SEQ.eq(B.RESOURCE_SEQ).and(A.USER_SEQ.eq(B.USER_SEQ)))
                .join(C).on(A.USER_SEQ.eq(C.SHARED_BY_USER_SEQ).and(A.SHARE_SEQ.eq(C.SHARE_SEQ)).and(A.RESOURCE_SEQ.eq(C.RESOURCE_SEQ)))
                .join(D).on(C.USER_SEQ.eq(D.USER_SEQ))
                .join(OT).on(D.TENANT_SEQ.eq(OT.TENANT_SEQ))
                .where(defaultWhere(q))
                .orderBy(C.SHARE_TARGET_SEQ.desc())
                .fetch(r -> ShareUserData.builder()
                        .userSeq(r.get(D.USER_SEQ))
                        .userId(r.get(D.USER_ID))
                        .userName(r.get(D.USER_NAME))
                        .userStatus(r.get(D.STATUS))
                        .tenantSeq(r.get(D.TENANT_SEQ))
                        .tenantName(r.get(OT.TENANT_NAME))
                        .build());
    }

    @Override
    public boolean existsByTenant(Integer tenantSeq) {
        return shareJpaRepository.existsByTenant(tenantSeq);
    }

    /** share-default-find-join-where. */
    private static Condition defaultWhere(ShareDefaultFindQuery q) {
        return eq(A.SHARE_SEQ, q.getShareSeq()).and(eq(A.USER_SEQ, q.getUserSeq())).and(eq(B.IS_DELETE, q.getIsDelete()));
    }

    // ------------------------------------------------------------------ nearest

    @Override
    public SharedResourceNearestData findNearestAncestorShareByResource(SharedResourceNearestFindQuery q) {
        Condition exclude = ne(TB_SHARE.as("s").RESOURCE_SEQ, q.getResourceSeq());
        return rootAncestor(ShareTreeJooq.rootByResource(q.getUserSeq(), q.getResourceSeq(), q.getIsDelete()), q.getIsDelete(), exclude);
    }

    @Override
    public SharedResourceNearestData findNearestSelfOrAncestorShareByResource(SharedResourceNearestFindQuery q) {
        return rootAncestor(ShareTreeJooq.rootByResource(q.getUserSeq(), q.getResourceSeq(), q.getIsDelete()), q.getIsDelete(), noCondition());
    }

    @Override
    public SharedResourceNearestData findNearestDescendantShareByResource(SharedResourceNearestFindQuery q) {
        Condition exclude = ne(TB_SHARE.as("s").RESOURCE_SEQ, q.getResourceSeq());
        return descendant(q, exclude);
    }

    @Override
    public SharedResourceNearestData findNearestAncestorShareByShare(SharedResourceNearestFindQuery q) {
        Condition exclude = ne(TB_SHARE.as("s").SHARE_SEQ, q.getShareSeq());
        Field<Long> shareSeq = col(null, "share_seq", SQLDataType.BIGINT);
        return DataAccessUtils.singleResult(dsl.withRecursive(ShareTreeJooq.rootByShare(q.getUserSeq(), q.getShareSeq(), q.getIsDelete()),
                        ShareTreeJooq.resourceRecursive(false, q.getIsDelete()), nearestShare(exclude))
                .select(shareSeq)
                .from(table(name(NEAREST_SHARE_RESOURCE)))
                .fetch(r -> SharedResourceNearestData.builder().shareSeq(r.get(shareSeq)).build()));
    }

    @Override
    public SharedResourceNearestData findNearestAncestorShareAndRootByShare(SharedResourceNearestFindQuery q) {
        Condition exclude = ne(TB_SHARE.as("s").SHARE_SEQ, q.getShareSeq());
        return rootAncestor(ShareTreeJooq.rootByShare(q.getUserSeq(), q.getShareSeq(), q.getIsDelete()), q.getIsDelete(), exclude);
    }

    @Override
    public SharedResourceNearestData findNearestDescendantShareByShare(SharedResourceNearestFindQuery q) {
        Condition exclude = ne(TB_SHARE.as("s").SHARE_SEQ, q.getShareSeq());
        return descendant(q, exclude);
    }

    /** 조상 재귀 + 가장 가까운 공유 + 최상위 노드(가장 깊은 조상) — root_node 1행에 nearest 를 ON TRUE 로 붙인다. */
    private SharedResourceNearestData rootAncestor(CommonTableExpression<?> root, Boolean isDelete, Condition exclude) {
        Table<Record> rr = table(name(RESOURCE_RECURSIVE));
        CommonTableExpression<?> rootNode = name(ROOT_NODE).as(
                select(col(null, "node_seq", SQLDataType.BIGINT).as("root_resource_seq"), col(null, "resource_type", SQLDataType.VARCHAR))
                        .from(rr)
                        .orderBy(col(null, "depth", SQLDataType.INTEGER).desc())
                        .limit(inline(1)));
        Field<Long> rootResourceSeq = col("a", "root_resource_seq", SQLDataType.BIGINT);
        Field<ResourceType> resourceType = col("a", "resource_type", TB_RESOURCE.RESOURCE_TYPE.getDataType());
        Field<ShareType> shareType = col("b", "share_type", TB_SHARE.SHARE_TYPE.getDataType());
        Field<Long> shareSeq = col("b", "share_seq", SQLDataType.BIGINT);
        Field<Long> resourceSeq = col("b", "resource_seq", SQLDataType.BIGINT);
        return DataAccessUtils.singleResult(dsl.withRecursive(root, ShareTreeJooq.resourceRecursive(false, isDelete), nearestShare(exclude), rootNode)
                .select(rootResourceSeq, resourceType, shareType, shareSeq, resourceSeq)
                .from(table(name(ROOT_NODE)).as("a"))
                .leftJoin(table(name(NEAREST_SHARE_RESOURCE)).as("b")).on(trueCondition())
                .fetch(r -> SharedResourceNearestData.builder()
                        .rootResourceSeq(r.get(rootResourceSeq))
                        .resourceType(r.get(resourceType))
                        .shareType(r.get(shareType))
                        .shareSeq(r.get(shareSeq))
                        .resourceSeq(r.get(resourceSeq))
                        .build()));
    }

    /** 하위 재귀(rootResourceSeq 부터) + 가장 가까운 공유. */
    private SharedResourceNearestData descendant(SharedResourceNearestFindQuery q, Condition exclude) {
        Field<ShareType> shareType = col(null, "share_type", TB_SHARE.SHARE_TYPE.getDataType());
        Field<Long> shareSeq = col(null, "share_seq", SQLDataType.BIGINT);
        Field<Long> resourceSeq = col(null, "resource_seq", SQLDataType.BIGINT);
        return DataAccessUtils.singleResult(dsl.withRecursive(ShareTreeJooq.rootByResource(q.getUserSeq(), q.getRootResourceSeq(), q.getIsDelete()),
                        ShareTreeJooq.resourceRecursive(true, q.getIsDelete()), nearestShare(exclude))
                .select(shareType, shareSeq, resourceSeq)
                .from(table(name(NEAREST_SHARE_RESOURCE)))
                .fetch(r -> SharedResourceNearestData.builder()
                        .shareType(r.get(shareType))
                        .shareSeq(r.get(shareSeq))
                        .resourceSeq(r.get(resourceSeq))
                        .build()));
    }

    /**
     * share-nearest-resource-share-cte(제외 = 리소스) / -include-self-cte(제외 없음) / share-nearest-share-cte(제외 = 공유) — 탐색 노드에 걸린 공유 중 depth 최소 1건.
     *
     * @param exclude 별칭 s = tb_share 기준 제외 조건(없으면 noCondition)
     */
    private static CommonTableExpression<?> nearestShare(Condition exclude) {
        Table<Record> r = table(name(RESOURCE_RECURSIVE)).as("r");
        TbShare s = TB_SHARE.as("s");
        Table<?> x = select(s.SHARE_TYPE, s.SHARE_SEQ, s.RESOURCE_SEQ,
                        rowNumber().over(partitionBy(col("r", "resource_seq", SQLDataType.BIGINT)).orderBy(col("r", "depth", SQLDataType.INTEGER))).as("rn"))
                .from(r)
                .join(s).on(s.USER_SEQ.eq(col("r", "user_seq", SQLDataType.INTEGER)).and(s.RESOURCE_SEQ.eq(col("r", "node_seq", SQLDataType.BIGINT))))
                .where(exclude)
                .asTable("x");
        return name(NEAREST_SHARE_RESOURCE).as(select(asterisk()).from(x).where(col(null, "rn", SQLDataType.INTEGER).eq(inline(1))));
    }

    // ------------------------------------------------------------------ 해제 범위·조상 공유

    /** 공유 루트 + 하위 전체에서 "다른 공유가 걸린 노드 + 그 서브트리"(exclude_share_resources, 재귀) 를 뺀 나머지. */
    @Override
    public UnsharedResourceData findUnsharedDescendantsByShare(UnsharedResourcesFindQuery q) {
        Table<Record> ra = table(name(RESOURCE_RECURSIVE)).as("a");
        TbShare b = TB_SHARE.as("b");
        Table<Record> rr = table(name(RESOURCE_RECURSIVE)).as("r");
        Table<Record> sd = table(name("exclude_share_resources")).as("sd");
        CommonTableExpression<?> exclude = name("exclude_share_resources").as(
                select(b.SHARE_SEQ, col("a", "node_seq", SQLDataType.BIGINT), col("a", "user_seq", SQLDataType.INTEGER))
                        .from(ra)
                        .join(b).on(col("a", "user_seq", SQLDataType.INTEGER).eq(b.USER_SEQ).and(col("a", "node_seq", SQLDataType.BIGINT).eq(b.RESOURCE_SEQ)))
                        .where(ne(b.SHARE_SEQ, q.getShareSeq()))
                        .unionAll(select(col("sd", "share_seq", SQLDataType.BIGINT), col("r", "node_seq", SQLDataType.BIGINT), col("r", "user_seq", SQLDataType.INTEGER))
                                .from(rr)
                                .join(sd).on(col("r", "parent_folder_seq", SQLDataType.BIGINT).eq(col("sd", "node_seq", SQLDataType.BIGINT)))));

        Field<Integer> userSeq = col("a", "user_seq", SQLDataType.INTEGER);
        Field<Long> nodeSeq = col("a", "node_seq", SQLDataType.BIGINT);
        Table<Record> e = table(name("exclude_share_resources")).as("b");
        var rows = dsl.withRecursive(ShareTreeJooq.rootByShare(q.getUserSeq(), q.getShareSeq(), q.getIsDelete()), ShareTreeJooq.resourceRecursive(true, q.getIsDelete()), exclude)
                .select(userSeq.as("shared_by_user_seq"), userSeq, nodeSeq.as("resource_seq"))
                .from(ra)
                .whereNotExists(selectOne().from(e)
                        .where(col("b", "user_seq", SQLDataType.INTEGER).eq(userSeq))
                        .and(col("b", "node_seq", SQLDataType.BIGINT).eq(nodeSeq)))
                .fetch();
        if (rows.isEmpty()) {
            return null;
        }
        return UnsharedResourceData.builder()
                .sharedByUserSeq(rows.get(0).value1())
                .unsharedResources(rows.stream().map(r -> UnsharedResourceItem.builder().userSeq(r.value2()).resourceSeq(r.value3()).build()).toList())
                .build();
    }

    /**
     * findAncestorSharesByResource — 4컬럼(XML 동일). 자기 자신 + 모든 조상의 공유(호출부가 전부 모아 대상 사용자를 합친다 — 순서 무관).
     * 구 XML 의 {@code ROW_NUMBER() … AS rn} 은 거르지 않는 죽은 컬럼이라 뺐다(5차 패스 E3).
     */
    @Override
    public List<SharedResourceData> findAncestorSharesByResource(SharedResourceFindQuery q) {
        Table<Record> a = table(name(RESOURCE_RECURSIVE)).as("a");
        TbShare b = TB_SHARE.as("b");
        CommonTableExpression<?> sharedResources = name("shared_resources").as(
                select(col("a", "node_seq", SQLDataType.BIGINT), col("a", "resource_type", SQLDataType.VARCHAR), b.SHARE_SEQ, b.SHARE_TYPE)
                        .from(a)
                        .join(b).on(b.RESOURCE_SEQ.eq(col("a", "node_seq", SQLDataType.BIGINT)).and(b.USER_SEQ.eq(col("a", "user_seq", SQLDataType.INTEGER)))));
        Field<Long> resourceSeq = col(null, "node_seq", SQLDataType.BIGINT).as("resource_seq");
        Field<Long> shareSeq = col(null, "share_seq", SQLDataType.BIGINT);
        Field<ResourceType> resourceType = col(null, "resource_type", TB_RESOURCE.RESOURCE_TYPE.getDataType());
        Field<ShareType> shareType = col(null, "share_type", TB_SHARE.SHARE_TYPE.getDataType());
        return dsl.withRecursive(ShareTreeJooq.rootByResource(q.getUserSeq(), q.getResourceSeq(), q.getIsDelete()), ShareTreeJooq.resourceRecursive(false, q.getIsDelete()), sharedResources)
                .select(resourceSeq, shareSeq, resourceType, shareType)
                .from(table(name("shared_resources")))
                .fetch(r -> SharedResourceData.builder()
                        .resourceSeq(r.get(resourceSeq))
                        .shareSeq(r.get(shareSeq))
                        .resourceType(r.get(resourceType))
                        .shareType(r.get(shareType))
                        .build());
    }
}
