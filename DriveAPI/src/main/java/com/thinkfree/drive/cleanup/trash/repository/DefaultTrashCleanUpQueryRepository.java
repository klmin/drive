package com.thinkfree.drive.cleanup.trash.repository;

import com.thinkfree.drive.cleanup.trash.data.TrashCleanUpTargetsData;
import com.thinkfree.drive.cleanup.trash.data.TrashCleanUpTargetsData.TrashCleanUpResourceItem;
import com.thinkfree.drive.cleanup.trash.data.TrashCleanUpTargetsData.TrashCleanUpResourceVersionItem;
import com.thinkfree.drive.cleanup.trash.query.TrashCleanUpByDateQuery;
import com.thinkfree.drive.cleanup.trash.query.TrashCleanUpByUserQuery;
import com.thinkfree.drive.jooq.tables.TbResource;
import com.thinkfree.drive.jooq.tables.TbResourceVersion;
import com.thinkfree.drive.jooq.tables.TbTrash;
import com.thinkfree.drive.jooq.tables.TbUser;
import com.thinkfree.drive.resource.enums.ResourceType;
import lombok.RequiredArgsConstructor;
import org.jooq.CommonTableExpression;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.DataType;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.thinkfree.drive.global.jooq.JooqConditions.eq;
import static com.thinkfree.drive.jooq.Tables.TB_RESOURCE;
import static com.thinkfree.drive.jooq.Tables.TB_RESOURCE_VERSION;
import static com.thinkfree.drive.jooq.Tables.TB_TRASH;
import static com.thinkfree.drive.jooq.Tables.TB_USER;
import static org.jooq.impl.DSL.*;

/**
 * 휴지통 영구삭제 대상 — jOOQ(구 TrashCleanUpMapper.xml findTrashCleanUpTargets, 오버로드 2 = {@code <choose>} 의 파라미터 타입 분기, 10-30 작업 B P1-③ 에서 JdbcClient → jOOQ).
 * <p>
 * CTE 3개는 구 XML 그대로: root_data(tb_trash ⋈ tb_resource, 날짜 또는 사용자 조건) → resource_data(루트 + {@code delete_parent_folder_seq} 가 루트인
 * 같은 소유자의 {@code is_delete = :isDelete} 행, UNION ALL — 한 단계, 재귀 아님) → resource_version_data(LEFT JOIN tb_resource_version) → tb_user.
 * 결과 행을 구 resultMap 과 같이 user_seq → resource_seq → resource_version_seq 로 묶는다(각 묶음은 첫 등장 순, 버전이 없으면 빈 목록 — MyBatis 가 collection 을 빈 리스트로 두던 것과 동일).
 * ORDER BY 는 구 XML 에도 없다. 조건 값이 null 이면 구 {@code = NULL}·{@code <= NULL} 처럼 참이 아님 — {@code isDelete} null 은 하위 0건(루트만).
 */
@Repository
@RequiredArgsConstructor
public class DefaultTrashCleanUpQueryRepository implements TrashCleanUpQueryRepository {

    private static final String ROOT_DATA = "root_data";
    private static final String RESOURCE_DATA = "resource_data";
    private static final String RESOURCE_VERSION_DATA = "resource_version_data";

    // CTE 컬럼(이름 참조) — resource_type 은 생성 코드 변환기(enum)를 붙여 읽는다
    private static final Field<Integer> USER_SEQ = col("a", "user_seq", SQLDataType.INTEGER);
    private static final Field<Long> RESOURCE_SEQ = col("a", "resource_seq", SQLDataType.BIGINT);
    private static final Field<Long> RESOURCE_VERSION_SEQ = col("a", "resource_version_seq", SQLDataType.BIGINT);
    private static final Field<Long> SIZE_BYTE = col("a", "size_byte", SQLDataType.BIGINT);
    private static final Field<Long> VERSION_SIZE_BYTE = col("a", "version_size_byte", SQLDataType.BIGINT);
    private static final Field<String> RESOURCE_SAVE_NAME = col("a", "resource_save_name", SQLDataType.VARCHAR);
    private static final Field<String> VERSION_RESOURCE_SAVE_NAME = col("a", "version_resource_save_name", SQLDataType.VARCHAR);
    private static final Field<ResourceType> RESOURCE_TYPE = col("a", "resource_type", TB_RESOURCE.RESOURCE_TYPE.getDataType());
    private static final Field<String> RESOURCE_NAME = col("a", "resource_name", SQLDataType.VARCHAR);

    private final DSLContext dsl;

    @Override
    public List<TrashCleanUpTargetsData> findTrashCleanUpTargets(TrashCleanUpByDateQuery query) {
        TbTrash a = TB_TRASH.as("a");
        Condition threshold = query.thresholdDate() == null ? falseCondition() : a.REGISTER_DATE.le(query.thresholdDate());
        return find(threshold, query.isDelete());
    }

    @Override
    public List<TrashCleanUpTargetsData> findTrashCleanUpTargets(TrashCleanUpByUserQuery query) {
        return find(eq(TB_TRASH.as("a").USER_SEQ, query.userSeq()), query.isDelete());
    }

    /** @param rootCondition 별칭 a = tb_trash 기준 루트 조건(날짜 또는 사용자) */
    private List<TrashCleanUpTargetsData> find(Condition rootCondition, Boolean isDelete) {
        TbTrash a = TB_TRASH.as("a");
        TbResource b = TB_RESOURCE.as("b");
        CommonTableExpression<?> rootData = name(ROOT_DATA).as(
                select(b.RESOURCE_SEQ, b.USER_SEQ, b.RESOURCE_TYPE, b.SIZE_BYTE, b.RESOURCE_SAVE_NAME, b.RESOURCE_NAME)
                        .from(a)
                        .join(b).on(a.RESOURCE_SEQ.eq(b.RESOURCE_SEQ).and(a.USER_SEQ.eq(b.USER_SEQ)))
                        .where(rootCondition));

        Table<Record> root = table(name(ROOT_DATA)).as("a");
        CommonTableExpression<?> resourceData = name(RESOURCE_DATA).as(
                select(b.RESOURCE_SEQ, b.USER_SEQ, b.RESOURCE_TYPE, b.SIZE_BYTE, b.RESOURCE_SAVE_NAME, b.RESOURCE_NAME)
                        .from(root)
                        .join(b).on(b.DELETE_PARENT_FOLDER_SEQ.eq(RESOURCE_SEQ))
                        .where(USER_SEQ.eq(b.USER_SEQ))
                        .and(eq(b.IS_DELETE, isDelete))
                        .unionAll(select(col(null, "resource_seq", SQLDataType.BIGINT), col(null, "user_seq", SQLDataType.INTEGER), col(null, "resource_type", TB_RESOURCE.RESOURCE_TYPE.getDataType()),
                                col(null, "size_byte", SQLDataType.BIGINT), col(null, "resource_save_name", SQLDataType.VARCHAR), col(null, "resource_name", SQLDataType.VARCHAR))
                                .from(table(name(ROOT_DATA)))));

        TbResourceVersion v = TB_RESOURCE_VERSION.as("b");
        CommonTableExpression<?> resourceVersionData = name(RESOURCE_VERSION_DATA).as(
                select(USER_SEQ, RESOURCE_SEQ, v.RESOURCE_VERSION_SEQ, SIZE_BYTE, v.SIZE_BYTE.as("version_size_byte"),
                        RESOURCE_SAVE_NAME, v.RESOURCE_SAVE_NAME.as("version_resource_save_name"), col("a", "resource_type", SQLDataType.VARCHAR), RESOURCE_NAME)
                        .from(table(name(RESOURCE_DATA)).as("a"))
                        .leftJoin(v).on(RESOURCE_SEQ.eq(v.RESOURCE_SEQ).and(USER_SEQ.eq(v.USER_SEQ))));

        TbUser u = TB_USER.as("b");
        List<Row> rows = dsl.with(rootData, resourceData, resourceVersionData)
                .select(USER_SEQ, RESOURCE_SEQ, RESOURCE_VERSION_SEQ, SIZE_BYTE, VERSION_SIZE_BYTE, RESOURCE_SAVE_NAME,
                        VERSION_RESOURCE_SAVE_NAME, RESOURCE_TYPE, RESOURCE_NAME, u.USER_NAME, u.TENANT_SEQ)
                .from(table(name(RESOURCE_VERSION_DATA)).as("a"))
                .join(u).on(USER_SEQ.eq(u.USER_SEQ))
                .fetch(r -> new Row(r.get(USER_SEQ), r.get(RESOURCE_SEQ), r.get(RESOURCE_VERSION_SEQ), r.get(SIZE_BYTE), r.get(VERSION_SIZE_BYTE),
                        r.get(RESOURCE_SAVE_NAME), r.get(VERSION_RESOURCE_SAVE_NAME), r.get(RESOURCE_TYPE), r.get(RESOURCE_NAME),
                        r.get(u.USER_NAME), r.get(u.TENANT_SEQ)));
        return group(rows);
    }

    /** CTE 컬럼(이름 참조) — {@code table} 이 null 이면 한정자 없이. */
    private static <T> Field<T> col(String table, String column, DataType<T> type) {
        return table == null ? field(name(column), type) : field(name(table, column), type);
    }

    // ------------------------------------------------------------------ 구 resultMap(trashCleanUpTargetsResultMap) 의 3단 묶음

    private static List<TrashCleanUpTargetsData> group(List<Row> rows) {
        Map<Integer, UserGroup> users = new LinkedHashMap<>();
        for (Row r : rows) {
            UserGroup user = users.computeIfAbsent(r.userSeq, k -> new UserGroup(r));
            if (r.resourceSeq == null) {
                continue;
            }
            ResourceGroup resource = user.resources.computeIfAbsent(r.resourceSeq, k -> new ResourceGroup(r));
            if (r.resourceVersionSeq != null && !resource.versionSeqs.contains(r.resourceVersionSeq)) {
                resource.versionSeqs.add(r.resourceVersionSeq);
                resource.versions.add(TrashCleanUpResourceVersionItem.builder()
                        .resourceVersionSeq(r.resourceVersionSeq)
                        .sizeByte(r.versionSizeByte)
                        .resourceSaveName(r.versionResourceSaveName)
                        .build());
            }
        }
        List<TrashCleanUpTargetsData> result = new ArrayList<>(users.size());
        for (UserGroup user : users.values()) {
            List<TrashCleanUpResourceItem> resources = new ArrayList<>(user.resources.size());
            for (ResourceGroup resource : user.resources.values()) {
                Row r = resource.first;
                resources.add(TrashCleanUpResourceItem.builder()
                        .userSeq(r.userSeq)
                        .resourceSeq(r.resourceSeq)
                        .resourceType(r.resourceType)
                        .sizeByte(r.sizeByte)
                        .resourceSaveName(r.resourceSaveName)
                        .resourceName(r.resourceName)
                        .resourceVersionTargets(resource.versions)
                        .build());
            }
            Row r = user.first;
            result.add(TrashCleanUpTargetsData.builder()
                    .userSeq(r.userSeq)
                    .userName(r.userName)
                    .tenantSeq(r.tenantSeq)
                    .resourceTargets(resources)
                    .build());
        }
        return result;
    }

    private static final class UserGroup {
        private final Row first;
        private final Map<Long, ResourceGroup> resources = new LinkedHashMap<>();

        private UserGroup(Row first) {
            this.first = first;
        }
    }

    private static final class ResourceGroup {
        private final Row first;
        private final List<Long> versionSeqs = new ArrayList<>();
        private final List<TrashCleanUpResourceVersionItem> versions = new ArrayList<>();

        private ResourceGroup(Row first) {
            this.first = first;
        }
    }

    private record Row(Integer userSeq, Long resourceSeq, Long resourceVersionSeq, Long sizeByte, Long versionSizeByte,
                       String resourceSaveName, String versionResourceSaveName, ResourceType resourceType, String resourceName,
                       String userName, Integer tenantSeq) {
    }
}
