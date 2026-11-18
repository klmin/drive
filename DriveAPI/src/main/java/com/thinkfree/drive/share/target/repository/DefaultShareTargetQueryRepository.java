package com.thinkfree.drive.share.target.repository;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.common.search.enums.SortOrder;
import com.thinkfree.drive.jooq.tables.TbRecent;
import com.thinkfree.drive.jooq.tables.TbResource;
import com.thinkfree.drive.jooq.tables.TbShare;
import com.thinkfree.drive.jooq.tables.TbShareTarget;
import com.thinkfree.drive.jooq.tables.TbStarred;
import com.thinkfree.drive.jooq.tables.TbTenant;
import com.thinkfree.drive.jooq.tables.TbUser;
import com.thinkfree.drive.resource.repository.ResourceCursorJooq;
import com.thinkfree.drive.resource.repository.ResourceJooq;
import com.thinkfree.drive.share.query.ShareTargetNearestFindQuery;
import com.thinkfree.drive.share.repository.ShareTreeJooq;
import com.thinkfree.drive.share.target.data.*;
import com.thinkfree.drive.share.target.enums.ShareTargetSortField;
import com.thinkfree.drive.share.target.query.*;
import com.thinkfree.drive.user.enums.UserStatus;
import lombok.RequiredArgsConstructor;
import org.jooq.CommonTableExpression;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.DataType;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SortField;
import org.jooq.Table;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.thinkfree.drive.global.jooq.JooqConditions.eq;
import static com.thinkfree.drive.global.jooq.JooqConditions.ne;
import static com.thinkfree.drive.global.jooq.JooqConditions.present;
import static com.thinkfree.drive.jooq.Tables.*;
import static com.thinkfree.drive.resource.repository.ResourceCursorJooq.direction;
import static com.thinkfree.drive.share.repository.ShareTreeJooq.RESOURCE_RECURSIVE;
import static com.thinkfree.drive.share.repository.ShareTreeJooq.col;
import static org.jooq.impl.DSL.*;

/**
 * tb_share_target 읽기 — jOOQ 중심(구 ShareTargetMapper.xml 의 조회 10 statement, 10-30 작업 B P1 에서 JdbcClient → jOOQ). 고정 조건 파티션 키·대상 사용자 조회는 JPQL 생성자 식({@link ShareTargetJpaRepository}). 쓰기는 {@link DefaultShareTargetRepository}.
 * <p>
 * 공유받은 목록(findAll)의 커서 게이트·정렬 키는 {@link ResourceCursorJooq}(정렬 대상 = b) — 별칭(a=tb_share_target, b=tb_resource, d=소유자, j=tb_recent)·기본 정렬(SHARE DESC)·
 * 정렬 컬럼 접두(SHARE 는 a., 나머지 b.)만 내 드라이브 목록과 다르다.
 * nearest·해제 범위는 XML {@code <sql>} 조각의 CTE 를 그대로(바깥 ORDER BY 없음 — 구 SQL 과 같은 모양이라 같은 계획). resultMap {@code <collection>} 2개(findUnsharedDescendantsByTargets·findShareTargetResourceUsersBy*)는 행을 읽어 LinkedHashMap 으로 묶는다(첫 등장 순서 유지 = MyBatis 동일).
 * Y/N·enum·시각은 생성 코드의 변환기가 매핑. 비교 값이 null 이면 구 SQL 의 {@code = NULL}·{@code <> NULL} 처럼 참이 아님. IN 목록이 비면 SQL 없이 빈 결과.
 */
@Repository
@RequiredArgsConstructor
public class DefaultShareTargetQueryRepository implements ShareTargetQueryRepository {

    // findAll 별칭
    private static final TbShareTarget A = TB_SHARE_TARGET.as("a");
    private static final TbResource B = TB_RESOURCE.as("b");
    private static final TbShare C = TB_SHARE.as("c");
    private static final TbUser D = TB_USER.as("d");
    private static final TbTenant OT = TB_TENANT.as("ot");
    private static final TbUser E = TB_USER.as("e");
    private static final TbUser F = TB_USER.as("f");
    private static final TbUser G = TB_USER.as("g");
    private static final TbUser H = TB_USER.as("h");
    private static final TbStarred I = TB_STARRED.as("i");
    private static final TbRecent J = TB_RECENT.as("j");

    private static final Field<Boolean> IS_STARRED = when(I.STARRED_SEQ.isNotNull(), inline(true)).otherwise(inline(false)).as("is_starred");

    // nearest·해제 범위 CTE 이름(root_data·resource_recursive 는 ShareTreeJooq)
    private static final String TARGETS = "targets";

    private final DSLContext dsl;
    private final ShareTargetJpaRepository jpaRepository;

    // ------------------------------------------------------------------ findAll

    @Override
    public List<ShareTargetData> findAll(ShareTargetListQuery q) {
        Condition where = eq(A.USER_SEQ, q.getUserSeq());
        CursorPaginationQuery page = q.getPage();
        if (page != null && present(page.getCursorId()) && present(page.getCursorValue())) {
            where = where.and(cursorGate(q, page));
        }
        if (q.getIsDelete() != null) {
            where = where.and(B.IS_DELETE.eq(q.getIsDelete()));
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
            where = where.and(D.USER_ID.eq(q.getUserId())).and(D.STATUS.in(inline(UserStatus.ACTIVE, D.STATUS), inline(UserStatus.INACTIVE, D.STATUS)));
        }
        where = where.and(ResourceJooq.visibleByActiveUser(A.SHARED_BY_USER_SEQ));

        return dsl.select(A.SHARE_TARGET_SEQ, A.USER_SEQ, A.SHARE_SEQ, A.RESOURCE_SEQ, A.SHARED_BY_USER_SEQ, B.RESOURCE_TYPE, B.RESOURCE_NAME, B.EXT, B.OWNER_SEQ,
                        D.USER_ID.as("owner_id"), D.USER_NAME.as("owner_name"), D.STATUS.as("owner_status"), D.TENANT_SEQ.as("owner_tenant_seq"), OT.TENANT_NAME.as("owner_tenant_name"),
                        B.SIZE_BYTE, B.IS_LOCKED, IS_STARRED, I.STARRED_SEQ.as("starred_seq"), A.SHARE_DATE,
                        E.USER_ID.as("register_user_id"), E.USER_NAME.as("register_user_name"), E.STATUS.as("register_user_status"), A.REGISTER_DATE,
                        G.USER_ID.as("update_user_id"), G.USER_NAME.as("update_user_name"), G.STATUS.as("update_user_status"), A.UPDATE_DATE,
                        F.USER_ID.as("resource_register_user_id"), F.USER_NAME.as("resource_register_user_name"), F.STATUS.as("resource_register_user_status"), B.REGISTER_DATE.as("resource_register_date"),
                        H.USER_ID.as("resource_update_user_id"), H.USER_NAME.as("resource_update_user_name"), H.STATUS.as("resource_update_user_status"), B.UPDATE_DATE.as("resource_update_date"),
                        J.UPDATE_DATE.as("recent_update_date"))
                .from(A)
                .join(B).on(A.SHARED_BY_USER_SEQ.eq(B.USER_SEQ).and(A.RESOURCE_SEQ.eq(B.RESOURCE_SEQ)))
                .join(C).on(A.SHARED_BY_USER_SEQ.eq(C.USER_SEQ).and(A.RESOURCE_SEQ.eq(C.RESOURCE_SEQ)).and(A.SHARE_SEQ.eq(C.SHARE_SEQ)))
                .join(D).on(B.OWNER_SEQ.eq(D.USER_SEQ))
                .join(OT).on(D.TENANT_SEQ.eq(OT.TENANT_SEQ))
                .join(E).on(A.REGISTER_USER_SEQ.eq(E.USER_SEQ))
                .join(F).on(B.REGISTER_USER_SEQ.eq(F.USER_SEQ))
                .leftJoin(G).on(A.UPDATE_USER_SEQ.eq(G.USER_SEQ))
                .leftJoin(H).on(B.UPDATE_USER_SEQ.eq(H.USER_SEQ))
                .leftJoin(I).on(A.RESOURCE_SEQ.eq(I.RESOURCE_SEQ).and(A.USER_SEQ.eq(I.USER_SEQ)).and(A.SHARED_BY_USER_SEQ.eq(I.SHARED_BY_USER_SEQ)))
                .leftJoin(J).on(A.RESOURCE_SEQ.eq(J.RESOURCE_SEQ).and(A.USER_SEQ.eq(J.USER_SEQ)).and(A.SHARED_BY_USER_SEQ.eq(J.SHARED_BY_USER_SEQ)))
                .where(where)
                .orderBy(orderBy(q.getSortBy(), q.getSortOrder()))
                .limit(val(page == null ? null : page.getPageSize(), SQLDataType.INTEGER))
                .fetch(r -> ShareTargetData.builder()
                        .shareTargetSeq(r.get(A.SHARE_TARGET_SEQ))
                        .userSeq(r.get(A.USER_SEQ))
                        .shareSeq(r.get(A.SHARE_SEQ))
                        .resourceSeq(r.get(A.RESOURCE_SEQ))
                        .sharedByUserSeq(r.get(A.SHARED_BY_USER_SEQ))
                        .resourceType(r.get(B.RESOURCE_TYPE))
                        .resourceName(r.get(B.RESOURCE_NAME))
                        .ext(r.get(B.EXT))
                        .ownerSeq(r.get(B.OWNER_SEQ))
                        .ownerId(r.get("owner_id", String.class))
                        .ownerName(r.get("owner_name", String.class))
                        .ownerStatus(r.get("owner_status", UserStatus.class))
                        .ownerTenantSeq(r.get("owner_tenant_seq", Integer.class))
                        .ownerTenantName(r.get("owner_tenant_name", String.class))
                        .sizeByte(r.get(B.SIZE_BYTE))
                        .isLocked(r.get(B.IS_LOCKED))
                        .isStarred(r.get(IS_STARRED))
                        .starredSeq(r.get("starred_seq", Long.class))
                        .shareDate(r.get(A.SHARE_DATE))
                        .registerUserId(r.get("register_user_id", String.class))
                        .registerUserName(r.get("register_user_name", String.class))
                        .registerUserStatus(r.get("register_user_status", UserStatus.class))
                        .registerDate(r.get(A.REGISTER_DATE))
                        .updateUserId(r.get("update_user_id", String.class))
                        .updateUserName(r.get("update_user_name", String.class))
                        .updateUserStatus(r.get("update_user_status", UserStatus.class))
                        .updateDate(r.get(A.UPDATE_DATE))
                        .resourceRegisterUserId(r.get("resource_register_user_id", String.class))
                        .resourceRegisterUserName(r.get("resource_register_user_name", String.class))
                        .resourceRegisterUserStatus(r.get("resource_register_user_status", UserStatus.class))
                        .resourceRegisterDate(r.get("resource_register_date", Instant.class))
                        .resourceUpdateUserId(r.get("resource_update_user_id", String.class))
                        .resourceUpdateUserName(r.get("resource_update_user_name", String.class))
                        .resourceUpdateUserStatus(r.get("resource_update_user_status", UserStatus.class))
                        .resourceUpdateDate(r.get("resource_update_date", Instant.class))
                        .recentUpdateDate(r.get("recent_update_date", Instant.class))
                        .build());
    }

    /**
     * share-target-where 의 커서 게이트. 정렬 컬럼 = SHARE 면 a., 나머지 b.(sortBy·sortOrder 둘 다 있을 때), 기본은 a.share_date DESC(연산자 {@code <}, TIMESTAMPTZ).
     * 분기 = NAME 자연정렬 / SIZE(폴더 0 처리) / OPEN(열람 그룹 → 열람일 fallback) / 일반.
     */
    private static Condition cursorGate(ShareTargetListQuery q, CursorPaginationQuery page) {
        ShareTargetSortField sortBy = q.getSortBy();
        SortOrder sortOrder = q.getSortOrder();
        if (sortBy == null) {
            // 기본 정렬 = a.share_date DESC + a.resource_seq {sortOrder} — orderBy 와 같은 키(10-30, 전엔 resource_seq 도 DESC 로 비교해 공유일 동률 행이 빠지거나 반복)
            return ResourceCursorJooq.of(B, page, SQLDataType.TIMESTAMPWITHTIMEZONE, true, sortOrder == SortOrder.DESC).generic(A.SHARE_DATE);
        }
        boolean explicit = sortBy != null && sortOrder != null;
        Field<?> sortColumn = !explicit ? A.SHARE_DATE : sortBy == ShareTargetSortField.SHARE ? A.SHARE_DATE : B.field(sortBy.getColumn());
        DataType<?> cursorType = explicit ? ResourceCursorJooq.cursorType(sortBy.getColumn()) : SQLDataType.TIMESTAMPWITHTIMEZONE;
        ResourceCursorJooq cursor = ResourceCursorJooq.of(B, page, cursorType, !explicit || sortOrder == SortOrder.DESC);
        if (sortBy == ShareTargetSortField.NAME) {
            return cursor.name();
        }
        if (sortBy == ShareTargetSortField.SIZE) {
            return cursor.size(sortColumn);
        }
        if (sortBy == ShareTargetSortField.OPEN) {
            return cursor.open(J.UPDATE_DATE, cursor.recentRank(cursorId -> recentCursorRank(q.getUserSeq(), cursorId)));
        }
        return cursor.generic(sortColumn);
    }

    /** 커서 행의 열람 그룹 — tb_share_target → tb_recent 를 직접 조회(XML 동일). */
    private static Field<Integer> recentCursorRank(Integer userSeq, Field<Long> cursorId) {
        TbShareTarget cs = TB_SHARE_TARGET.as("cs");
        TbRecent r = TB_RECENT.as("r");
        return when(exists(selectOne().from(cs)
                .join(r).on(r.USER_SEQ.eq(cs.USER_SEQ).and(r.RESOURCE_SEQ.eq(cs.RESOURCE_SEQ)).and(r.SHARED_BY_USER_SEQ.eq(cs.SHARED_BY_USER_SEQ)))
                .where(eq(cs.USER_SEQ, userSeq))
                .and(cs.RESOURCE_SEQ.eq(cursorId))
                .and(r.UPDATE_DATE.isNotNull())), inline(0)).otherwise(inline(1));
    }

    /** share-target-order: 폴더 먼저, 정렬 필드(둘 다 있을 때 — NAME 자연정렬 3키·OPEN 열람일 fallback·SHARE 는 a.), 기본 a.share_date DESC, a.resource_seq tie-break(sortOrder 방향, 없으면 ASC). */
    private static List<SortField<?>> orderBy(ShareTargetSortField sortBy, SortOrder sortOrder) {
        List<SortField<?>> order = new ArrayList<>();
        order.add(B.RESOURCE_TYPE.desc());
        if (sortBy != null && sortOrder != null) {
            switch (sortBy) {
                case NAME -> order.addAll(ResourceCursorJooq.nameOrder(B, sortOrder));
                case OPEN -> order.addAll(ResourceCursorJooq.openOrder(B, J.UPDATE_DATE, sortOrder));
                case SHARE -> order.add(direction(A.SHARE_DATE, sortOrder));
                default -> order.add(direction(B.field(sortBy.getColumn()), sortOrder));
            }
        } else {
            order.add(A.SHARE_DATE.desc());
        }
        order.add(direction(A.RESOURCE_SEQ, sortOrder));
        return order;
    }

    // ------------------------------------------------------------------ 단순 조회

    @Override
    public List<ShareTargetPartitionKeyData> findShareTargetPartitionKeys(ShareTargetFindQuery q) {
        return jpaRepository.findPartitionKeys(q.getShareSeq(), q.getSharedByUserSeq());
    }

    @Override
    public List<ShareTargetUserData> findShareTargetUsersByShares(ShareTargetUsersQuery q) {
        if (q.getShareSeqList() == null || q.getShareSeqList().isEmpty()) {
            return List.of();
        }
        return jpaRepository.findUsersByShares(q.getSharedByUserSeq(), q.getShareSeqList(), q.getIsDelete());
    }

    @Override
    public List<ShareTargetUserData> findShareTargetUsersByResource(ShareTargetUsersQuery q) {
        return jpaRepository.findUsersByResource(q.getSharedByUserSeq(), q.getTargetUserSeq(), q.getResourceSeq(), q.getIsDelete());
    }

    @Override
    public List<ShareTargetUserData> findShareTargetUsersByDeletedResource(ShareTargetUsersQuery q) {
        return jpaRepository.findUsersByDeletedResource(q.getSharedByUserSeq(), q.getResourceSeq());
    }

    /** 대상 사용자 목록 — 리소스가 대상 사용자 드라이브(c)에 is_delete 상태로 있을 때만, 이름은 그 리소스 이름. */
    @Override
    public List<ShareTargetResourceUsersData> findShareTargetResourceUsersByResources(ShareTargetResourceUsersQuery q) {
        if (q.getResourceSeqList() == null || q.getResourceSeqList().isEmpty()) {
            return List.of();
        }
        TbShareTarget a = TB_SHARE_TARGET.as("a");
        TbShare b = TB_SHARE.as("b");
        TbResource c = TB_RESOURCE.as("c");
        return resourceUsers(dsl.select(a.USER_SEQ, a.RESOURCE_SEQ, c.RESOURCE_NAME)
                .from(a)
                .join(b).on(a.SHARED_BY_USER_SEQ.eq(b.USER_SEQ).and(a.SHARE_SEQ.eq(b.SHARE_SEQ)).and(a.RESOURCE_SEQ.eq(b.RESOURCE_SEQ)))
                .join(c).on(eq(c.USER_SEQ, q.getTargetUserSeq()).and(c.RESOURCE_SEQ.eq(b.RESOURCE_SEQ)).and(eq(c.IS_DELETE, q.getIsDelete())))
                .where(eq(a.SHARED_BY_USER_SEQ, q.getSharedByUserSeq()))
                .and(a.RESOURCE_SEQ.in(q.getResourceSeqList()))
                .fetch(r -> new ResourceUserRow(r.value2(), r.value3(), r.value1())));
    }

    /** 삭제된 리소스의 대상 사용자 목록 — 리소스 조인 없음(이름 NULL). */
    @Override
    public List<ShareTargetResourceUsersData> findShareTargetResourceUsersByDeletedResources(ShareTargetResourceUsersQuery q) {
        if (q.getResourceSeqList() == null || q.getResourceSeqList().isEmpty()) {
            return List.of();
        }
        TbShareTarget a = TB_SHARE_TARGET.as("a");
        TbShare b = TB_SHARE.as("b");
        return resourceUsers(dsl.select(a.USER_SEQ, a.RESOURCE_SEQ, castNull(SQLDataType.VARCHAR).as("resource_name"))
                .from(a)
                .join(b).on(a.SHARED_BY_USER_SEQ.eq(b.USER_SEQ).and(a.SHARE_SEQ.eq(b.SHARE_SEQ)).and(a.RESOURCE_SEQ.eq(b.RESOURCE_SEQ)))
                .where(eq(a.SHARED_BY_USER_SEQ, q.getSharedByUserSeq()))
                .and(a.RESOURCE_SEQ.in(q.getResourceSeqList()))
                .fetch(r -> new ResourceUserRow(r.value2(), r.value3(), r.value1())));
    }

    private record ResourceUserRow(Long resourceSeq, String resourceName, Integer userSeq) {
    }

    /** resultMap shareTargetResourceUsersMap — resource_seq 로 묶고 targetUsers 는 {@code <id>} 라 중복 제거. */
    private static List<ShareTargetResourceUsersData> resourceUsers(List<ResourceUserRow> rows) {
        Map<Long, ShareTargetResourceUsersData.ShareTargetResourceUsersDataBuilder> builders = new LinkedHashMap<>();
        Map<Long, List<Integer>> users = new LinkedHashMap<>();
        for (ResourceUserRow row : rows) {
            builders.computeIfAbsent(row.resourceSeq(), k -> ShareTargetResourceUsersData.builder().resourceSeq(row.resourceSeq()).resourceName(row.resourceName()));
            List<Integer> list = users.computeIfAbsent(row.resourceSeq(), k -> new ArrayList<>());
            if (row.userSeq() != null && !list.contains(row.userSeq())) {
                list.add(row.userSeq());
            }
        }
        List<ShareTargetResourceUsersData> result = new ArrayList<>(builders.size());
        builders.forEach((seq, b) -> result.add(b.targetUsers(users.get(seq)).build()));
        return result;
    }

    // ------------------------------------------------------------------ nearest·해제 범위

    @Override
    public List<ShareTargetNearestData> findNearestAncestorShareTargets(ShareTargetNearestFindQuery q) {
        return nearest(q, noCondition());
    }

    @Override
    public List<ShareTargetNearestData> findNearestAncestorShareTargetsByUsers(ShareTargetNearestFindQuery q) {
        if (q.getTargetUserSeqList() == null || q.getTargetUserSeqList().isEmpty()) {
            return List.of();
        }
        return nearest(q, TB_SHARE_TARGET.as("a").USER_SEQ.in(q.getTargetUserSeqList()));
    }

    /**
     * share-target-root-data-cte → targets(대상 + 대상 사용자·공유자 테넌트) → 조상 재귀 → share_ancestors(다른 공유의 대상) → 대상 사용자별 가장 가까운(깊이 최소) 1건.
     *
     * @param targetUserFilter 대상 사용자 목록 한정(별칭 a = tb_share_target) — 없으면 noCondition
     */
    private List<ShareTargetNearestData> nearest(ShareTargetNearestFindQuery q, Condition targetUserFilter) {
        TbShareTarget a = TB_SHARE_TARGET.as("a");
        TbShare b = TB_SHARE.as("b");
        TbUser u = TB_USER.as("u");
        TbUser ou = TB_USER.as("ou");
        CommonTableExpression<?> targets = name(TARGETS).as(
                select(a.SHARE_TARGET_SEQ, a.SHARED_BY_USER_SEQ, a.USER_SEQ, u.TENANT_SEQ, ou.TENANT_SEQ.as("owner_tenant_seq"))
                        .from(a)
                        .join(b).on(a.SHARED_BY_USER_SEQ.eq(b.USER_SEQ).and(a.SHARE_SEQ.eq(b.SHARE_SEQ)).and(a.RESOURCE_SEQ.eq(b.RESOURCE_SEQ)))
                        .join(u).on(a.USER_SEQ.eq(u.USER_SEQ))
                        .join(ou).on(b.USER_SEQ.eq(ou.USER_SEQ))
                        .where(targetUserFilter)
                        .and(eq(b.USER_SEQ, q.getUserSeq()))
                        .and(eq(a.SHARE_SEQ, q.getShareSeq())));

        // share_ancestors: 조상 노드에 걸린 다른 공유(shareSeq 제외)의 대상
        Table<Record> r = table(name(RESOURCE_RECURSIVE)).as("r");
        TbShare s = TB_SHARE.as("s");
        TbShareTarget t = TB_SHARE_TARGET.as("t");
        CommonTableExpression<?> shareAncestors = name("share_ancestors").as(
                select(t.USER_SEQ.as("share_target_user_seq"), s.RESOURCE_SEQ, s.SHARE_SEQ, s.USER_SEQ, t.SHARE_TARGET_SEQ.as("ancestor_share_target_seq"), col("r", "depth", SQLDataType.INTEGER))
                        .from(r)
                        .join(s).on(s.USER_SEQ.eq(col("r", "user_seq", SQLDataType.INTEGER)).and(s.RESOURCE_SEQ.eq(col("r", "node_seq", SQLDataType.BIGINT))))
                        .join(t).on(s.SHARE_SEQ.eq(t.SHARE_SEQ).and(s.USER_SEQ.eq(t.SHARED_BY_USER_SEQ)).and(s.RESOURCE_SEQ.eq(t.RESOURCE_SEQ)))
                        .where(ne(s.SHARE_SEQ, q.getShareSeq())));

        // nearest_share_target: 대상 사용자별 깊이 최소 1건(조상 공유가 없으면 ancestor NULL)
        Table<Record> tt = table(name(TARGETS)).as("t");
        Table<Record> sa = table(name("share_ancestors")).as("s");
        Table<?> x = select(col("t", "user_seq", SQLDataType.INTEGER), col("t", "shared_by_user_seq", SQLDataType.INTEGER), col("t", "share_target_seq", SQLDataType.BIGINT),
                        col("t", "tenant_seq", SQLDataType.INTEGER), col("t", "owner_tenant_seq", SQLDataType.INTEGER), col("s", "ancestor_share_target_seq", SQLDataType.BIGINT),
                        rowNumber().over(partitionBy(col("t", "user_seq", SQLDataType.INTEGER)).orderBy(col("s", "depth", SQLDataType.INTEGER))).as("rn"))
                .from(tt)
                .leftJoin(sa).on(col("t", "shared_by_user_seq", SQLDataType.INTEGER).eq(col("s", "user_seq", SQLDataType.INTEGER))
                        .and(col("t", "user_seq", SQLDataType.INTEGER).eq(col("s", "share_target_user_seq", SQLDataType.INTEGER))))
                .asTable("x");
        CommonTableExpression<?> nearestShareTarget = name("nearest_share_target").as(
                select(asterisk()).from(x).where(col(null, "rn", SQLDataType.INTEGER).eq(inline(1))));

        Field<Integer> userSeq = col(null, "user_seq", SQLDataType.INTEGER);
        Field<Integer> sharedByUserSeq = col(null, "shared_by_user_seq", SQLDataType.INTEGER);
        Field<Long> shareTargetSeq = col(null, "share_target_seq", SQLDataType.BIGINT);
        Field<Integer> tenantSeq = col(null, "tenant_seq", SQLDataType.INTEGER);
        Field<Integer> ownerTenantSeq = col(null, "owner_tenant_seq", SQLDataType.INTEGER);
        Field<Long> ancestorShareTargetSeq = col(null, "ancestor_share_target_seq", SQLDataType.BIGINT);
        return dsl.withRecursive(ShareTreeJooq.rootByShare(q.getUserSeq(), q.getShareSeq(), q.getIsDelete()), targets, ShareTreeJooq.resourceRecursive(false, q.getIsDelete()), shareAncestors, nearestShareTarget)
                .select(userSeq, sharedByUserSeq, shareTargetSeq, tenantSeq, ownerTenantSeq, ancestorShareTargetSeq)
                .from(table(name("nearest_share_target")))
                .fetch(rec -> ShareTargetNearestData.builder()
                        .userSeq(rec.get(userSeq))
                        .sharedByUserSeq(rec.get(sharedByUserSeq))
                        .shareTargetSeq(rec.get(shareTargetSeq))
                        .ancestorShareTargetSeq(rec.get(ancestorShareTargetSeq))
                        .tenantSeq(rec.get(tenantSeq))
                        .ownerTenantSeq(rec.get(ownerTenantSeq))
                        .build());
    }

    /**
     * 대상 사용자별 해제 범위 — 다른 공유(shareSeq 제외)의 대상인 노드 + 그 서브트리를 사용자별로 제외. resultMap unsharedUserResultMap — user_seq 로 묶은 resourceSeqList(첫 등장 순서).
     */
    @Override
    public List<UnsharedTargetsData> findUnsharedDescendantsByTargets(UnsharedTargetsFindQuery q) {
        if (q.getTargetUserSeqList() == null || q.getTargetUserSeqList().isEmpty()) {
            return List.of();
        }
        TbShareTarget a = TB_SHARE_TARGET.as("a");
        TbShare b = TB_SHARE.as("b");
        CommonTableExpression<?> targets = name(TARGETS).as(
                select(a.USER_SEQ)
                        .from(a)
                        .join(b).on(a.SHARED_BY_USER_SEQ.eq(b.USER_SEQ).and(a.SHARE_SEQ.eq(b.SHARE_SEQ)).and(a.RESOURCE_SEQ.eq(b.RESOURCE_SEQ)))
                        .where(a.USER_SEQ.in(q.getTargetUserSeqList()))
                        .and(eq(b.USER_SEQ, q.getUserSeq()))
                        .and(eq(a.SHARE_SEQ, q.getShareSeq())));

        // exclude_share_resources: 다른 공유가 걸린 노드(그 공유의 대상 사용자별) + 그 하위
        Table<Record> ra = table(name(RESOURCE_RECURSIVE)).as("a");
        TbShare sb = TB_SHARE.as("b");
        TbShareTarget sc = TB_SHARE_TARGET.as("c");
        Table<Record> t = table(name(TARGETS)).as("t");
        Table<Record> rr = table(name(RESOURCE_RECURSIVE)).as("r");
        Table<Record> sd = table(name("exclude_share_resources")).as("sd");
        CommonTableExpression<?> exclude = name("exclude_share_resources").as(
                select(col("a", "node_seq", SQLDataType.BIGINT), col("a", "user_seq", SQLDataType.INTEGER), col("t", "user_seq", SQLDataType.INTEGER).as("share_target_user_seq"))
                        .from(ra)
                        .join(sb).on(col("a", "user_seq", SQLDataType.INTEGER).eq(sb.USER_SEQ).and(col("a", "node_seq", SQLDataType.BIGINT).eq(sb.RESOURCE_SEQ)))
                        .leftJoin(sc).on(sb.USER_SEQ.eq(sc.SHARED_BY_USER_SEQ).and(sb.RESOURCE_SEQ.eq(sc.RESOURCE_SEQ)))
                        .join(t).on(col("t", "user_seq", SQLDataType.INTEGER).eq(sc.USER_SEQ))
                        .where(ne(sb.SHARE_SEQ, q.getShareSeq()))
                        .unionAll(select(col("r", "node_seq", SQLDataType.BIGINT), col("r", "user_seq", SQLDataType.INTEGER), col("sd", "share_target_user_seq", SQLDataType.INTEGER))
                                .from(rr)
                                .join(sd).on(col("r", "parent_folder_seq", SQLDataType.BIGINT).eq(col("sd", "node_seq", SQLDataType.BIGINT)))));

        Field<Integer> userSeq = col("t", "user_seq", SQLDataType.INTEGER);
        Field<Long> resourceSeq = col("r", "node_seq", SQLDataType.BIGINT).as("resource_seq");
        Table<Record> e = table(name("exclude_share_resources")).as("e");
        var rows = dsl.withRecursive(ShareTreeJooq.rootByShare(q.getUserSeq(), q.getShareSeq(), q.getIsDelete()), ShareTreeJooq.resourceRecursive(true, q.getIsDelete()), targets, exclude)
                .select(userSeq, resourceSeq)
                .from(rr)
                .crossJoin(t)
                .whereNotExists(selectOne().from(e)
                        .where(col("e", "share_target_user_seq", SQLDataType.INTEGER).eq(userSeq))
                        .and(col("e", "node_seq", SQLDataType.BIGINT).eq(col("r", "node_seq", SQLDataType.BIGINT))))
                .fetch();
        Map<Integer, List<Long>> grouped = new LinkedHashMap<>();
        for (var row : rows) {
            grouped.computeIfAbsent(row.get(userSeq), k -> new ArrayList<>()).add(row.get(resourceSeq));
        }
        List<UnsharedTargetsData> result = new ArrayList<>(grouped.size());
        grouped.forEach((user, seqs) -> result.add(UnsharedTargetsData.builder().userSeq(user).resourceSeqList(seqs).build()));
        return result;
    }
}
