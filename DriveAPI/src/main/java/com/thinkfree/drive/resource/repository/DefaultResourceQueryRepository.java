package com.thinkfree.drive.resource.repository;

import com.thinkfree.drive.jooq.tables.TbResource;
import com.thinkfree.drive.jooq.tables.TbResourceVersion;
import com.thinkfree.drive.jooq.tables.TbShare;
import com.thinkfree.drive.jooq.tables.TbTrash;
import com.thinkfree.drive.jooq.tables.TbUser;
import com.thinkfree.drive.partition.PartitionKey;
import com.thinkfree.drive.resource.data.*;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.query.*;
import com.thinkfree.drive.user.enums.UserStatus;
import lombok.RequiredArgsConstructor;
import org.jooq.CommonTableExpression;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Record5;
import org.jooq.Record7;
import org.jooq.Record8;
import org.jooq.Row2;
import org.jooq.Select;
import org.jooq.SelectField;
import org.jooq.Table;
import org.jooq.impl.SQLDataType;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;

import static com.thinkfree.drive.global.jooq.JooqConditions.eq;
import static com.thinkfree.drive.global.jooq.JooqConditions.present;
import static com.thinkfree.drive.jooq.Tables.TB_RESOURCE;
import static com.thinkfree.drive.jooq.Tables.TB_RESOURCE_VERSION;
import static com.thinkfree.drive.jooq.Tables.TB_SHARE;
import static com.thinkfree.drive.jooq.Tables.TB_TRASH;
import static com.thinkfree.drive.jooq.Tables.TB_USER;
import static com.thinkfree.drive.resource.repository.ResourceJooq.*;
import static org.jooq.impl.DSL.*;

/**
 * tb_resource 읽기 — jOOQ 중심(구 ResourceMapper.xml 의 조회 19 statement, 10-29 작업 B P0 에서 JdbcClient → jOOQ). 고정 조건 부분 컬럼 조회(폴더+하위 경로·스토리지 키·이름 중복 seq·공유 토큰 존재)만 JPQL 생성자 식·스칼라({@link ResourceJpaRepository} — 엔티티를 영속 컨텍스트에 올리지 않음). 쓰기는 {@link DefaultResourceRepository}.
 * <p>
 * 단건 조회(findDetail·파일 잠금 판정·공유 링크 토큰)는 JPQL 생성자 식 DTO(10-12 단계 4, W10) — <b>엔티티를 반환·로드하지 않는다</b>(EntityManager·detach 0).
 * 고칠 행의 엔티티 로드는 쓰기 리포({@link DefaultResourceRepository})의 관리 엔티티 — 여기서 엔티티를 떼어내면 같은 tx 에서 그 행을 쥔 쪽의 변경이 유실된다.
 * 목록 조회의 조인·커서·정렬 조각은 {@link ResourceJooq}. SQL 모양(CTE·UNION ALL·정렬·바인딩 위치)은 전환 전 JdbcClient SQL 과 같게 두었다 — ORDER BY 없는 CTE 결과의 순서도 같은 계획에서 나오도록.
 * Y/N·enum·시각은 생성 코드의 변환기(YnBooleanConverter·enumConverter·Instant)가 매핑. 비교 값이 null 이면 구 SQL 의 {@code = NULL} 처럼 참이 아님({@code JooqConditions.eq}). IN 목록이 비면 SQL 없이 빈 결과.
 */
@Repository
@RequiredArgsConstructor
public class DefaultResourceQueryRepository implements ResourceQueryRepository {

    // ------------------------------------------------------------------ 목록 컬럼(resource-base-select)

    private static final Field<String> PARENT_FOLDER_NAME = E.RESOURCE_NAME.as("parent_folder_name");
    private static final Field<String> FULL_PATH_SEQ = E.FULL_PATH_SEQ.as("full_path_seq");
    private static final Field<String> FULL_PATH = E.FULL_PATH.as("full_path");

    /** 단건(findByIdAndIsDelete) — 별표·열람 없음. */
    private static final List<Field<?>> SINGLE_COLUMNS = List.of(A.RESOURCE_SEQ, A.USER_SEQ, A.OWNER_SEQ, OWNER_ID, OWNER_NAME, OWNER_STATUS,
            OWNER_TENANT_SEQ, OWNER_TENANT_NAME, A.RESOURCE_TYPE, A.PARENT_FOLDER_SEQ, PARENT_FOLDER_NAME, A.RESOURCE_NAME, A.EXT,
            FULL_PATH_SEQ, FULL_PATH, A.SIZE_BYTE, A.IS_LOCKED,
            REGISTER_USER_ID, REGISTER_USER_NAME, REGISTER_USER_STATUS, A.REGISTER_DATE,
            UPDATE_USER_ID, UPDATE_USER_NAME, UPDATE_USER_STATUS, A.UPDATE_DATE);

    /** 목록(findAll 의 resource_data) — + 별표·열람일. */
    private static final List<Field<?>> LIST_COLUMNS = List.of(A.RESOURCE_SEQ, A.USER_SEQ, A.OWNER_SEQ, OWNER_ID, OWNER_NAME, OWNER_STATUS,
            OWNER_TENANT_SEQ, OWNER_TENANT_NAME, A.RESOURCE_TYPE, A.PARENT_FOLDER_SEQ, PARENT_FOLDER_NAME, A.RESOURCE_NAME, A.EXT,
            FULL_PATH_SEQ, FULL_PATH, A.SIZE_BYTE, A.IS_LOCKED, IS_STARRED, STARRED_SEQ,
            REGISTER_USER_ID, REGISTER_USER_NAME, REGISTER_USER_STATUS, A.REGISTER_DATE,
            UPDATE_USER_ID, UPDATE_USER_NAME, UPDATE_USER_STATUS, A.UPDATE_DATE, RECENT_UPDATE_DATE);

    // findAll 의 CTE 이름·공유 합성 컬럼
    private static final Name RESOURCE_RECURSIVE = name("resource_recursive");
    private static final Field<Long> SHARE_RESOURCE_SEQ = field(name("share_resource_seq"), SQLDataType.BIGINT);
    private static final Field<Long> SHARE_SEQ = field(name("share_seq"), SQLDataType.BIGINT);

    // 계층 CTE(resource_hierarchy) 의 계산 컬럼
    private static final Name RESOURCE_HIERARCHY = name("resource_hierarchy");
    private static final Field<String> HIERARCHY_FULL_PATH = field(name("full_path"), SQLDataType.CLOB);
    private static final Field<Integer> HIERARCHY_DEPTH = field(name("depth"), SQLDataType.INTEGER);

    private final DSLContext dsl;
    private final ResourceJpaRepository jpaRepository;

    // ------------------------------------------------------------------ 목록

    /**
     * 목록(resource_data — 조인·필터·커서·정렬·FETCH FIRST) → 조상 재귀(resource_recursive) → 가장 가까운 조상 공유(shared_pick) → is_shared/share_seq 합성.
     * 권한 플래그는 소유자라 전부 TRUE. 바깥 SELECT 에 ORDER BY 가 없다(구 SQL 과 같음 — resource_data 순서 그대로 나온다).
     */
    @Override
    public List<ResourceData> findAll(ResourceListQuery q) {
        Condition parent = present(q.getParentFolderSeq()) ? A.PARENT_FOLDER_SEQ.eq(q.getParentFolderSeq()) : A.PARENT_FOLDER_SEQ.isNull();
        Condition where = eq(A.USER_SEQ, q.getUserSeq())
                .and(eq(A.IS_DELETE, q.getIsDelete()))
                .and(parent)
                .and(baseWhere(q, recentCursorRank(q.getUserSeq())));
        CommonTableExpression<Record> resourceData = name("resource_data").as(
                join(select(LIST_COLUMNS).from(A))
                        .where(where)
                        .orderBy(orderBy(q.getSortBy(), q.getSortOrder(), G.UPDATE_DATE))
                        .limit(val(q.getPage().getPageSize(), SQLDataType.INTEGER)));

        // resource_recursive: 목록 행에서 부모를 따라 올라가며 조상(ancestor_seq)·깊이
        Table<Record> r = resourceData.as("r");
        Table<Record> rb = table(RESOURCE_RECURSIVE).as("b");
        Field<Long> rbResourceSeq = field(name("b", "resource_seq"), SQLDataType.BIGINT);
        Field<Long> rbParentFolderSeq = field(name("b", "parent_folder_seq"), SQLDataType.BIGINT);
        Field<Integer> rbDepth = field(name("b", "depth"), SQLDataType.INTEGER);
        Field<Integer> rbUserSeq = field(name("b", "user_seq"), SQLDataType.INTEGER);
        CommonTableExpression<Record5<Long, Long, Long, Integer, Integer>> resourceRecursive = RESOURCE_RECURSIVE.as(
                select(r.field(A.RESOURCE_SEQ), r.field(A.RESOURCE_SEQ).as("ancestor_seq"), r.field(A.PARENT_FOLDER_SEQ), inline(0).as("depth"), r.field(A.USER_SEQ))
                        .from(r)
                        .unionAll(select(rbResourceSeq, A.RESOURCE_SEQ.as("ancestor_seq"), A.PARENT_FOLDER_SEQ, rbDepth.plus(inline(1)).as("depth"), A.USER_SEQ)
                                .from(A)
                                .join(rb).on(A.USER_SEQ.eq(rbUserSeq).and(A.RESOURCE_SEQ.eq(rbParentFolderSeq)))
                                .where(eq(A.IS_DELETE, q.getIsDelete()))));

        // shared_pick: 조상 중 공유가 걸린 가장 가까운(깊이 최소) 하나
        Table<Record5<Long, Long, Long, Integer, Integer>> rr = resourceRecursive.as("r");
        TbShare s = TB_SHARE.as("s");
        Field<Long> rrResourceSeq = rr.field("resource_seq", Long.class);
        Table<?> x = select(rr.asterisk(), s.RESOURCE_SEQ.as("share_resource_seq"), s.SHARE_SEQ,
                rowNumber().over(partitionBy(rrResourceSeq).orderBy(rr.field("depth", Integer.class))).as("rn"))
                .from(rr)
                .join(s).on(s.USER_SEQ.eq(rr.field("user_seq", Integer.class)).and(s.RESOURCE_SEQ.eq(rr.field("ancestor_seq", Long.class))))
                .asTable("x");
        CommonTableExpression<Record> sharedPick = name("shared_pick").as(select(x.asterisk()).from(x).where(field(name("x", "rn"), SQLDataType.INTEGER).eq(inline(1))));

        // shared_pick 은 SELECT * 라 컬럼을 이름으로 참조
        Table<Record> a = resourceData.as("a");
        Table<Record> b = sharedPick.as("b");
        Field<Long> shareResourceSeq = field(name("b", SHARE_RESOURCE_SEQ.getName()), SQLDataType.BIGINT);
        Field<Long> shareSeq = field(name("b", SHARE_SEQ.getName()), SQLDataType.BIGINT);
        Function<Field<?>, Field<?>> column = a::field;
        List<SelectField<?>> columns = List.of(
                a.field(A.RESOURCE_SEQ), a.field(A.USER_SEQ), a.field(A.OWNER_SEQ), a.field(OWNER_ID), a.field(OWNER_NAME), a.field(OWNER_STATUS), a.field(OWNER_TENANT_SEQ), a.field(OWNER_TENANT_NAME),
                a.field(A.RESOURCE_TYPE), a.field(A.PARENT_FOLDER_SEQ), a.field(PARENT_FOLDER_NAME), a.field(A.RESOURCE_NAME), a.field(A.EXT), a.field(FULL_PATH_SEQ), a.field(FULL_PATH), a.field(A.SIZE_BYTE), a.field(A.IS_LOCKED),
                a.field(IS_STARRED), a.field(STARRED_SEQ),
                when(shareResourceSeq.isNotNull(), inline(true)).otherwise(inline(false)).as("is_shared"),
                when(a.field(A.RESOURCE_SEQ).eq(shareResourceSeq), shareSeq).as("share_seq"),
                shareResourceSeq, castNull(SQLDataType.BIGINT).as("share_target_seq"),
                inline(true).as("can_view"), inline(true).as("can_edit"), inline(true).as("can_download"), inline(true).as("can_upload"), inline(true).as("can_delete"), inline(true).as("can_share"),
                a.field(REGISTER_USER_ID), a.field(REGISTER_USER_NAME), a.field(REGISTER_USER_STATUS), a.field(A.REGISTER_DATE),
                a.field(UPDATE_USER_ID), a.field(UPDATE_USER_NAME), a.field(UPDATE_USER_STATUS), a.field(A.UPDATE_DATE), a.field(RECENT_UPDATE_DATE));

        return dsl.withRecursive(resourceData, resourceRecursive, sharedPick)
                .select(columns)
                .from(a)
                .leftJoin(b).on(a.field(A.USER_SEQ).eq(field(name("b", "user_seq"), SQLDataType.INTEGER)).and(a.field(A.RESOURCE_SEQ).eq(field(name("b", "resource_seq"), SQLDataType.BIGINT))))
                .fetch(rec -> base(rec, column)
                        .isStarred(rec.get(a.field(IS_STARRED)))
                        .starredSeq(rec.get(a.field(STARRED_SEQ)))
                        .isShared(rec.get("is_shared", Boolean.class))
                        .shareSeq(rec.get("share_seq", Long.class))
                        .shareResourceSeq(rec.get(shareResourceSeq))
                        .shareTargetSeq(rec.get("share_target_seq", Long.class))
                        .canView(rec.get("can_view", Boolean.class))
                        .canEdit(rec.get("can_edit", Boolean.class))
                        .canDownload(rec.get("can_download", Boolean.class))
                        .canUpload(rec.get("can_upload", Boolean.class))
                        .canDelete(rec.get("can_delete", Boolean.class))
                        .canShare(rec.get("can_share", Boolean.class))
                        .recentUpdateDate(rec.get(a.field(RECENT_UPDATE_DATE)))
                        .build());
    }

    @Override
    public ResourceData findByIdAndIsDelete(ResourceFindQuery q) {
        return baseJoin(dsl.select(SINGLE_COLUMNS).from(A))
                .where(eq(A.RESOURCE_SEQ, q.getResourceSeq()))
                .and(eq(A.IS_DELETE, q.getIsDelete()))
                .and(eq(A.USER_SEQ, q.getUserSeq()))
                .and(visibleByActiveUser(A.USER_SEQ))
                .fetchOptional(rec -> base(rec, Function.identity()).build())
                .orElse(null);
    }

    /** 공통 컬럼 매핑 — {@code column} 은 SELECT 절의 컬럼 상수를 실제 조회 필드로 바꾼다(직접 조회 = 그대로, CTE = CTE 의 같은 이름 컬럼). */
    @SuppressWarnings("unchecked")
    private static ResourceData.ResourceDataBuilder base(Record rec, Function<Field<?>, Field<?>> column) {
        Function<Field<?>, Object> v = f -> rec.get(column.apply(f));
        return ResourceData.builder()
                .resourceSeq((Long) v.apply(A.RESOURCE_SEQ))
                .userSeq((Integer) v.apply(A.USER_SEQ))
                .ownerSeq((Integer) v.apply(A.OWNER_SEQ))
                .ownerId((String) v.apply(OWNER_ID))
                .ownerName((String) v.apply(OWNER_NAME))
                .ownerStatus((UserStatus) v.apply(OWNER_STATUS))
                .ownerTenantSeq((Integer) v.apply(OWNER_TENANT_SEQ))
                .ownerTenantName((String) v.apply(OWNER_TENANT_NAME))
                .resourceType((ResourceType) v.apply(A.RESOURCE_TYPE))
                .parentFolderSeq((Long) v.apply(A.PARENT_FOLDER_SEQ))
                .parentFolderName((String) v.apply(PARENT_FOLDER_NAME))
                .resourceName((String) v.apply(A.RESOURCE_NAME))
                .ext((String) v.apply(A.EXT))
                .fullPathSeq((String) v.apply(FULL_PATH_SEQ))
                .fullPath((String) v.apply(FULL_PATH))
                .sizeByte((Long) v.apply(A.SIZE_BYTE))
                .isLocked(Boolean.TRUE.equals(v.apply(A.IS_LOCKED)))
                .registerUserId((String) v.apply(REGISTER_USER_ID))
                .registerUserName((String) v.apply(REGISTER_USER_NAME))
                .registerUserStatus((UserStatus) v.apply(REGISTER_USER_STATUS))
                .registerDate((Instant) v.apply(A.REGISTER_DATE))
                .updateUserId((String) v.apply(UPDATE_USER_ID))
                .updateUserName((String) v.apply(UPDATE_USER_NAME))
                .updateUserStatus((UserStatus) v.apply(UPDATE_USER_STATUS))
                .updateDate((Instant) v.apply(A.UPDATE_DATE));
    }

    /**
     * 파티션 단건 + 소유 계정 노출 가능(ACTIVE/INACTIVE) — JPQL 생성자 식 한 번(10-12 단계 4, 그 전은 엔티티 로드 후 detach). 영속 컨텍스트에 안 올라가 항상 DB 현재 값.
     * 선택 조건 type·is_delete 는 Java 판정(값이 있을 때만 — 구 동적 WHERE 와 같음). 공유 토큰을 뺀 전 컬럼.
     */
    @Override
    public ResourceDetailData findDetail(ResourceFindQuery q) {
        if (q.getUserSeq() == null || q.getResourceSeq() == null) {
            return null;
        }
        ResourceDetailData r = jpaRepository.findVisibleDetailByPartition(q.getUserSeq(), q.getResourceSeq(), UserStatus.displayableStatuses()).orElse(null);
        return r != null && matches(q, r.getResourceType(), r.getIsDelete()) ? r : null;
    }

    /** 선택 조건(resourceType·isDelete) 판정 — PK 한 행이라 Java 로(null 조건은 무시) */
    private static boolean matches(ResourceFindQuery q, ResourceType resourceType, Boolean isDelete) {
        boolean typeMatches = q.getResourceType() == null || q.getResourceType() == resourceType;
        boolean deleteMatches = q.getIsDelete() == null || q.getIsDelete().equals(isDelete);
        return typeMatches && deleteMatches;
    }

    // ------------------------------------------------------------------ 존재·토큰

    @Override
    public boolean existsResourceName(ResourceExistsQuery q) {
        return !findSeqByName(q).isEmpty();
    }

    /** 같은 이름이 둘 이상이면 {@code IncorrectResultSizeDataAccessException}(구 JdbcClient optional 동일). */
    @Override
    public ResourceExistsData findResourceByExists(ResourceExistsQuery q) {
        Long resourceSeq = DataAccessUtils.singleResult(findSeqByName(q));
        return resourceSeq == null ? null : ResourceExistsData.builder().resourceSeq(resourceSeq).build();
    }

    /** where-resource-exists(JPQL, 10-08): user·type·is_delete + parent(값 / 없으면 IS NULL — 두 벌) + resource_name = originalFileName, excludeResourceSeq 제외. */
    private List<Long> findSeqByName(ResourceExistsQuery q) {
        List<Long> seqs = q.getParentFolderSeq() != null
                ? jpaRepository.findSeqByName(q.getUserSeq(), q.getResourceType(), q.getIsDelete(), q.getParentFolderSeq(), q.getOriginalFileName())
                : jpaRepository.findRootSeqByName(q.getUserSeq(), q.getResourceType(), q.getIsDelete(), q.getOriginalFileName());
        if (q.getExcludeResourceSeq() == null) {
            return seqs;
        }
        return seqs.stream().filter(seq -> !seq.equals(q.getExcludeResourceSeq())).toList();
    }

    @Override
    public boolean existsByShareToken(String shareToken) {
        return jpaRepository.existsByShareToken(shareToken);
    }

    /** 공유 토큰 → 리소스 + 소유자 테넌트(FETCH FIRST 1). */
    @Override
    public ResourceTokenAccessData findResourceByShareToken(String shareToken, boolean isDelete) {
        TbResource r = TB_RESOURCE.as("r");
        TbUser u = TB_USER.as("u");
        return dsl.select(r.RESOURCE_SEQ, r.USER_SEQ, u.TENANT_SEQ, r.RESOURCE_TYPE)
                .from(r)
                .join(u).on(r.USER_SEQ.eq(u.USER_SEQ))
                .where(eq(r.SHARE_TOKEN, shareToken))
                .and(r.IS_DELETE.eq(isDelete))
                .limit(inline(1))
                .fetchOptional(rec -> ResourceTokenAccessData.builder()
                        .resourceSeq(rec.get(r.RESOURCE_SEQ))
                        .userSeq(rec.get(r.USER_SEQ))
                        .tenantSeq(rec.get(u.TENANT_SEQ))
                        .resourceType(rec.get(r.RESOURCE_TYPE))
                        .build())
                .orElse(null);
    }

    /** 미삭제 리소스의 공유 토큰 + 소유자 테넌트 — JPQL 생성자 식(5차 패스, 구 JdbcClient 조인). */
    @Override
    public ResourceLinkTokenData findShareTokenForLink(int userSeq, long resourceSeq) {
        return jpaRepository.findShareTokenForLink(userSeq, resourceSeq, false).orElse(null);
    }

    // ------------------------------------------------------------------ 계층

    /** 휴지통 루트(tb_trash) 하위로 삭제된 리소스 + 루트 자신(UNION ALL — 루트 쪽은 user 조건 없음, 구 SQL 과 같음). */
    @Override
    public List<ResourceDeleteParentData> findAllByDeleteParentFolderSeq(ResourceDeleteParentListQuery q) {
        TbTrash b = TB_TRASH.as("b");
        TbResource self = TB_RESOURCE;
        return dsl.select(A.RESOURCE_SEQ, A.RESOURCE_NAME, A.RESOURCE_SAVE_NAME, A.RESOURCE_TYPE, A.SIZE_BYTE)
                .from(A)
                .join(b).on(A.DELETE_PARENT_FOLDER_SEQ.eq(b.RESOURCE_SEQ).and(A.USER_SEQ.eq(b.USER_SEQ)))
                .where(eq(b.RESOURCE_SEQ, q.getResourceSeq()))
                .and(eq(b.REGISTER_USER_SEQ, q.getUserSeq()))
                .and(A.IS_DELETE.eq(inline(true, A.IS_DELETE)))
                .unionAll(select(self.RESOURCE_SEQ, self.RESOURCE_NAME, self.RESOURCE_SAVE_NAME, self.RESOURCE_TYPE, self.SIZE_BYTE)
                        .from(self)
                        .where(eq(self.RESOURCE_SEQ, q.getResourceSeq()))
                        .and(self.IS_DELETE.eq(inline(true, self.IS_DELETE))))
                .fetch(rec -> ResourceDeleteParentData.builder()
                        .resourceSeq(rec.value1())
                        .resourceName(rec.value2())
                        .resourceSaveName(rec.value3())
                        .resourceType(rec.value4())
                        .sizeByte(rec.value5())
                        .build());
    }

    @Override
    public List<ResourceHierarchyData> findResourceHierarchy(ResourceHierarchyQuery q) {
        if (q.getResourceSeqList() == null || q.getResourceSeqList().isEmpty()) {
            return List.of();
        }
        Condition start = A.RESOURCE_SEQ.in(q.getResourceSeqList()).and(eq(A.USER_SEQ, q.getUserSeq()));
        if (q.getResourceType() != null) {
            start = start.and(A.RESOURCE_TYPE.eq(q.getResourceType()));
        }
        return hierarchy(start, q.getIsDelete());
    }

    /** (user_seq, resource_seq) 파티션 키 행값 IN. */
    @Override
    public List<ResourceHierarchyData> findResourceHierarchyByPartitionKeys(ResourceHierarchyPartitionKeysQuery q) {
        if (q.getKeys() == null || q.getKeys().isEmpty()) {
            return List.of();
        }
        List<Row2<Integer, Long>> keys = q.getKeys().stream().map((PartitionKey k) -> row(k.sharedByUserSeq(), k.resourceSeq())).toList();
        return hierarchy(row(A.USER_SEQ, A.RESOURCE_SEQ).in(keys), q.getIsDelete());
    }

    /**
     * 시작 행에서 자식으로 내려가는 재귀 CTE — 경로(full_path)는 이름을 '/' 로 잇고 폴더는 끝에 '/' 를 붙인다. 깊이·타입(폴더 먼저)·이름 순.
     * isDelete 가 있으면 시작 행·재귀 단계 모두에 is_delete 조건(없으면 둘 다 없음).
     */
    private List<ResourceHierarchyData> hierarchy(Condition start, Boolean isDelete) {
        Condition startCondition = isDelete == null ? start : start.and(A.IS_DELETE.eq(isDelete));
        Table<Record> b = table(RESOURCE_HIERARCHY).as("b");
        Field<Long> bResourceSeq = field(name("b", "resource_seq"), SQLDataType.BIGINT);
        Field<Integer> bUserSeq = field(name("b", "user_seq"), SQLDataType.INTEGER);
        Field<String> bFullPath = field(name("b", "full_path"), SQLDataType.CLOB);
        Field<Integer> bDepth = field(name("b", "depth"), SQLDataType.INTEGER);
        Condition joinCondition = A.PARENT_FOLDER_SEQ.eq(bResourceSeq).and(A.USER_SEQ.eq(bUserSeq));
        if (isDelete != null) {
            joinCondition = joinCondition.and(A.IS_DELETE.eq(isDelete));
        }
        CommonTableExpression<Record8<Long, Integer, Long, ResourceType, String, String, String, Integer>> cte = RESOURCE_HIERARCHY.as(
                select(A.RESOURCE_SEQ, A.USER_SEQ, A.PARENT_FOLDER_SEQ, A.RESOURCE_TYPE, A.RESOURCE_NAME, A.RESOURCE_SAVE_NAME,
                        cast(A.RESOURCE_NAME, SQLDataType.CLOB).as(HIERARCHY_FULL_PATH), inline(0).as(HIERARCHY_DEPTH))
                        .from(A)
                        .where(startCondition)
                        .unionAll(select(A.RESOURCE_SEQ, A.USER_SEQ, A.PARENT_FOLDER_SEQ, A.RESOURCE_TYPE, A.RESOURCE_NAME, A.RESOURCE_SAVE_NAME,
                                bFullPath.concat(inline("/")).concat(A.RESOURCE_NAME).as(HIERARCHY_FULL_PATH), bDepth.plus(inline(1)))
                                .from(A)
                                .join(b).on(joinCondition)));
        Field<ResourceType> type = cte.field(A.RESOURCE_TYPE);
        Field<String> fullPath = cte.field(HIERARCHY_FULL_PATH);
        Field<Integer> depth = cte.field(HIERARCHY_DEPTH);
        Select<Record7<Long, Long, ResourceType, String, String, String, Integer>> select = dsl.withRecursive(cte)
                .select(cte.field(A.RESOURCE_SEQ), cte.field(A.PARENT_FOLDER_SEQ), type, cte.field(A.RESOURCE_SAVE_NAME), cte.field(A.RESOURCE_NAME),
                        when(type.eq(inline(ResourceType.FILE, A.RESOURCE_TYPE)), fullPath).otherwise(fullPath.concat(inline("/"))).as(HIERARCHY_FULL_PATH), depth)
                .from(cte)
                .orderBy(depth, type.desc(), cte.field(A.RESOURCE_NAME));
        return dsl.fetch(select).map(rec -> ResourceHierarchyData.builder()
                .resourceSeq(rec.value1())
                .parentFolderSeq(rec.value2())
                .resourceType(rec.value3())
                .resourceSaveName(rec.value4())
                .resourceName(rec.value5())
                .fullPath(rec.value6())
                .depth(rec.value7())
                .build());
    }

    /** 폴더 하위(재귀, is_delete 조건) 리소스 행 + 그 리소스들의 버전 행(UNION ALL — 리소스 행은 resource_version_seq NULL). */
    @Override
    public List<ResourceVersionHierarchyData> findResourceVersionsByFolderHierarchy(ResourceVersionHierarchyQuery q) {
        TbResource self = TB_RESOURCE;
        Table<Record> b = table(RESOURCE_HIERARCHY).as("b");
        CommonTableExpression<Record5<Long, Integer, Long, ResourceType, Long>> cte = RESOURCE_HIERARCHY.as(
                select(self.RESOURCE_SEQ, self.USER_SEQ, self.PARENT_FOLDER_SEQ, self.RESOURCE_TYPE, self.SIZE_BYTE)
                        .from(self)
                        .where(eq(self.USER_SEQ, q.getUserSeq()))
                        .and(eq(self.RESOURCE_SEQ, q.getResourceSeq()))
                        .and(eq(self.IS_DELETE, q.getIsDelete()))
                        .unionAll(select(A.RESOURCE_SEQ, A.USER_SEQ, A.PARENT_FOLDER_SEQ, A.RESOURCE_TYPE, A.SIZE_BYTE)
                                .from(A)
                                .join(b).on(A.PARENT_FOLDER_SEQ.eq(field(name("b", "resource_seq"), SQLDataType.BIGINT))
                                        .and(A.USER_SEQ.eq(field(name("b", "user_seq"), SQLDataType.INTEGER)))
                                        .and(eq(A.IS_DELETE, q.getIsDelete())))));
        Table<Record5<Long, Integer, Long, ResourceType, Long>> h = cte.as("a");
        TbResourceVersion v = TB_RESOURCE_VERSION.as("b");
        return dsl.withRecursive(cte)
                .select(h.field(self.RESOURCE_SEQ), castNull(SQLDataType.BIGINT).as("resource_version_seq"), h.field(self.USER_SEQ), h.field(self.PARENT_FOLDER_SEQ), h.field(self.RESOURCE_TYPE), h.field(self.SIZE_BYTE))
                .from(h)
                .unionAll(select(h.field(self.RESOURCE_SEQ), v.RESOURCE_VERSION_SEQ, h.field(self.USER_SEQ), h.field(self.PARENT_FOLDER_SEQ), h.field(self.RESOURCE_TYPE), v.SIZE_BYTE)
                        .from(h)
                        .join(v).on(h.field(self.RESOURCE_SEQ).eq(v.RESOURCE_SEQ).and(h.field(self.USER_SEQ).eq(v.USER_SEQ))))
                .fetch(rec -> ResourceVersionHierarchyData.builder()
                        .resourceSeq(rec.value1())
                        .resourceVersionSeq(rec.value2())
                        .userSeq(rec.value3())
                        .parentFolderSeq(rec.value4())
                        .resourceType(rec.value5())
                        .sizeByte(rec.value6())
                        .build());
    }

    /** 루트(부모 NULL)부터 같은 타입·삭제 상태로 내려가는 트리 — 깊이, 이름 자연정렬(숫자부·문자부 drive_name_sort). */
    @Override
    public List<ResourceTreeData> findResourceTree(ResourceFindQuery q) {
        TbResource self = TB_RESOURCE;
        Table<Record> b = table(RESOURCE_HIERARCHY).as("b");
        Field<Integer> depth = field(name("depth"), SQLDataType.INTEGER);
        Condition sameKind = eq(A.IS_DELETE, q.getIsDelete()).and(eq(A.RESOURCE_TYPE, q.getResourceType()));
        CommonTableExpression<Record8<Long, String, Integer, Long, ResourceType, Integer, String, Integer>> cte = RESOURCE_HIERARCHY.as(
                select(self.RESOURCE_SEQ, self.RESOURCE_NAME, self.USER_SEQ, self.PARENT_FOLDER_SEQ, self.RESOURCE_TYPE, self.RESOURCE_NAME_NUMERIC_PART, self.RESOURCE_NAME_TEXT_PART, inline(0).as(depth))
                        .from(self)
                        .where(eq(self.USER_SEQ, q.getUserSeq()))
                        .and(eq(self.IS_DELETE, q.getIsDelete()))
                        .and(eq(self.RESOURCE_TYPE, q.getResourceType()))
                        .and(self.PARENT_FOLDER_SEQ.isNull())
                        .unionAll(select(A.RESOURCE_SEQ, A.RESOURCE_NAME, A.USER_SEQ, A.PARENT_FOLDER_SEQ, A.RESOURCE_TYPE, A.RESOURCE_NAME_NUMERIC_PART, A.RESOURCE_NAME_TEXT_PART,
                                field(name("b", "depth"), SQLDataType.INTEGER).plus(inline(1)))
                                .from(A)
                                .join(b).on(A.PARENT_FOLDER_SEQ.eq(field(name("b", "resource_seq"), SQLDataType.BIGINT))
                                        .and(A.USER_SEQ.eq(field(name("b", "user_seq"), SQLDataType.INTEGER)))
                                        .and(sameKind))));
        return dsl.withRecursive(cte)
                .select(cte.field(self.RESOURCE_SEQ), cte.field(self.RESOURCE_NAME), cte.field(self.PARENT_FOLDER_SEQ), cte.field(self.RESOURCE_TYPE))
                .from(cte)
                .orderBy(cte.field(depth), cte.field(self.RESOURCE_NAME_NUMERIC_PART),
                        cte.field(self.RESOURCE_NAME_TEXT_PART).collate("drive_name_sort"), cte.field(self.RESOURCE_NAME).collate("drive_name_sort"))
                .fetch(rec -> ResourceTreeData.builder()
                        .resourceSeq(rec.value1())
                        .resourceName(rec.value2())
                        .parentFolderSeq(rec.value3())
                        .resourceType(rec.value4())
                        .build());
    }

    @Override
    public List<ResourcePathData> findFolderAndDescendants(ResourcePathQuery q) {
        return jpaRepository.findFolderAndDescendants(q.userSeq(), q.fullPathSeq(), q.isDelete());
    }

    @Override
    public List<ResourceStorageKeyData> findStorageKeysByUserSeqList(List<Integer> userSeqList) {
        if (userSeqList == null || userSeqList.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findStorageKeysByUserSeqIn(userSeqList);
    }

    /** 전체·활성·휴지통 바이트 합(행이 없으면 0). */
    @Override
    public ResourceUsageSummaryData findUsageSummaryByUserSeq(int userSeq) {
        TbResource self = TB_RESOURCE;
        Field<BigDecimal> zero = inline(BigDecimal.ZERO);
        return dsl.select(coalesce(sum(self.SIZE_BYTE), zero),
                        coalesce(sum(when(self.IS_DELETE.eq(inline(false, self.IS_DELETE)), self.SIZE_BYTE)), zero),
                        coalesce(sum(when(self.IS_DELETE.eq(inline(true, self.IS_DELETE)), self.SIZE_BYTE)), zero))
                .from(self)
                .where(self.USER_SEQ.eq(userSeq))
                .fetchSingle(rec -> new ResourceUsageSummaryData(rec.value1().longValue(), rec.value2().longValue(), rec.value3().longValue()));
    }

    // ------------------------------------------------------------------ 잠금 상태

    /**
     * 파일은 파티션 단건 DTO 의 is_locked(5차 패스 — 구 타입·잠금 조회 2문을 조회 하나로, 구 SQL 처럼 {@code isLocked} 인자는 보지 않음).
     * W10: 구 엔티티 로드 + detach 는 같은 tx 에서 그 행을 관리 엔티티로 쥔 쪽의 인스턴스를 떼어내(변경 유실) JPQL 생성자 식 DTO 로 바꿨다.
     * 폴더(또는 없는 리소스 — 결과 false)는 하위 트리 재귀 CTE 에 {@code isLocked} 와 같은 행이 있는지.
     */
    @Override
    public boolean isLockedResource(ResourceLockUnlockQuery q) {
        if (q.userSeq() == null || q.resourceSeq() == null) {
            return false;
        }
        ResourceDetailData r = jpaRepository.findDetailByUserSeqAndResourceSeq(q.userSeq(), q.resourceSeq()).orElse(null);
        if (r == null) {
            return false;
        }
        if (r.getResourceType() == ResourceType.FILE) {
            return Boolean.TRUE.equals(r.getIsLocked());
        }
        TbResource b = TB_RESOURCE.as("b");
        Name resourceTree = name("resource_tree");
        Table<Record> rt = table(resourceTree).as("rt");
        var cte = resourceTree.as(
                select(A.RESOURCE_SEQ, A.PARENT_FOLDER_SEQ, A.IS_LOCKED)
                        .from(A)
                        .where(A.RESOURCE_SEQ.eq(q.resourceSeq()))
                        .and(A.USER_SEQ.eq(q.userSeq()))
                        .unionAll(select(b.RESOURCE_SEQ, b.PARENT_FOLDER_SEQ, b.IS_LOCKED)
                                .from(b)
                                .join(rt).on(b.PARENT_FOLDER_SEQ.eq(field(name("rt", "resource_seq"), SQLDataType.BIGINT)).and(b.USER_SEQ.eq(q.userSeq())))));
        return Boolean.TRUE.equals(dsl.withRecursive(cte)
                .select(field(exists(selectOne().from(cte).where(eq(cte.field(A.IS_LOCKED), q.isLocked())))))
                .fetchOne(0, Boolean.class));
    }
}
