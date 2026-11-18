package com.thinkfree.drive.resource.version.repository;

import com.thinkfree.drive.jooq.tables.TbResource;
import com.thinkfree.drive.jooq.tables.TbResourceVersion;
import com.thinkfree.drive.jooq.tables.TbTenant;
import com.thinkfree.drive.jooq.tables.TbUser;
import com.thinkfree.drive.resource.data.ResourceStorageKeyData;
import com.thinkfree.drive.resource.data.ResourceUsageSummaryData;
import com.thinkfree.drive.resource.repository.ResourceJooq;
import com.thinkfree.drive.resource.version.data.ResourceVersionData;
import com.thinkfree.drive.resource.version.data.ResourceVersionKeyData;
import com.thinkfree.drive.resource.version.data.ResourceVersionWithResourceData;
import com.thinkfree.drive.resource.version.enums.ResourceVersionType;
import com.thinkfree.drive.resource.version.query.ResourceVersionFindQuery;
import com.thinkfree.drive.resource.version.query.ResourceVersionListQuery;
import com.thinkfree.drive.user.enums.UserStatus;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectJoinStep;
import org.jooq.SelectOnConditionStep;
import org.jooq.Table;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static com.thinkfree.drive.global.jooq.JooqConditions.eq;
import static com.thinkfree.drive.jooq.Tables.TB_RESOURCE;
import static com.thinkfree.drive.jooq.Tables.TB_RESOURCE_VERSION;
import static com.thinkfree.drive.jooq.Tables.TB_TENANT;
import static com.thinkfree.drive.jooq.Tables.TB_USER;
import static org.jooq.impl.DSL.*;

/**
 * tb_resource_version 읽기 — 버전 키 3종({@link ResourceVersionKeyData})·스토리지 키·버전+리소스 단건은 JPQL 생성자 식({@link ResourceVersionJpaRepository}), 목록·집계 4종은 jOOQ(구 ResourceVersionMapper.xml 의 조회 8 statement, 10-30 작업 B P1 에서 JdbcClient → jOOQ). 쓰기는 {@link DefaultResourceVersionRepository}.
 * <p>
 * 엔티티를 반환하지 않는다(10-12 단계 2) — 결과가 영속 컨텍스트에 안 올라가므로 어디서 불러도 DB 현재 값이고 detach 가 필요 없다.
 * 목록은 이력(tb_resource_version) UNION ALL 현재(tb_resource) 파생 테이블 + 드라이브 소유자(u/ut)·버전 등록자(o/ot) LEFT JOIN — 등록자가 익명 사용자(테넌트 없음)일 수 있어 INNER JOIN 이면 행이 탈락한다.
 * Y/N·enum·시각은 생성 코드의 변환기가 매핑(파생 테이블의 version_type 문자열만 직접 enum 변환). 비교 값이 null 이면 구 SQL 의 {@code = NULL} 처럼 참이 아님. IN 목록이 비면 SQL 없이 빈 결과.
 */
@Repository
@RequiredArgsConstructor
public class DefaultResourceVersionQueryRepository implements ResourceVersionQueryRepository {

    private static final TbUser U = TB_USER.as("u");
    private static final TbTenant UT = TB_TENANT.as("ut");
    private static final TbUser O = TB_USER.as("o");
    private static final TbTenant OT = TB_TENANT.as("ot");

    // 파생 테이블 a(resource-version-union) 의 컬럼
    private static final Field<Long> A_RESOURCE_VERSION_SEQ = field(name("a", "resource_version_seq"), SQLDataType.BIGINT);
    private static final Field<Integer> A_USER_SEQ = field(name("a", "user_seq"), SQLDataType.INTEGER);
    private static final Field<Long> A_RESOURCE_SEQ = field(name("a", "resource_seq"), SQLDataType.BIGINT);
    private static final Field<Integer> A_OWNER_SEQ = field(name("a", "owner_seq"), SQLDataType.INTEGER);
    private static final Field<Integer> A_VERSION_NUMBER = field(name("a", "version_number"), SQLDataType.INTEGER);
    private static final Field<Long> A_SIZE_BYTE = field(name("a", "size_byte"), SQLDataType.BIGINT);
    private static final Field<String> A_VERSION_TYPE = field(name("a", "version_type"), SQLDataType.VARCHAR);
    private static final Field<Instant> A_VERSION_REGISTER_DATE = field(name("a", "version_register_date"), TB_RESOURCE_VERSION.VERSION_REGISTER_DATE.getDataType());

    private final ResourceVersionJpaRepository jpaRepository;
    private final DSLContext dsl;

    // ------------------------------------------------------------------ 목록

    @Override
    public List<ResourceVersionData> findAll(ResourceVersionListQuery q) {
        var page = q.getPage();
        return versionJoin(dsl.select(A_RESOURCE_VERSION_SEQ, A_USER_SEQ, U.USER_NAME, U.TENANT_SEQ, UT.TENANT_NAME, A_RESOURCE_SEQ, A_VERSION_NUMBER, A_SIZE_BYTE, A_VERSION_TYPE,
                                A_OWNER_SEQ, O.USER_NAME.as("owner_name"), O.STATUS.as("owner_status"), O.TENANT_SEQ.as("owner_tenant_seq"), OT.TENANT_NAME.as("owner_tenant_name"),
                                A_VERSION_REGISTER_DATE)
                        .from(versionUnion(q)))
                .where(ResourceJooq.visibleByActiveUser(A_USER_SEQ))
                .orderBy(A_VERSION_NUMBER.desc())
                .offset(val(page.getOffset(), SQLDataType.INTEGER))
                .limit(val(page.getPageSize(), SQLDataType.INTEGER))
                .fetch(r -> ResourceVersionData.builder()
                        .resourceVersionSeq(r.get(A_RESOURCE_VERSION_SEQ))
                        .userSeq(r.get(A_USER_SEQ))
                        .userName(r.get(U.USER_NAME))
                        .tenantSeq(r.get(U.TENANT_SEQ))
                        .tenantName(r.get(UT.TENANT_NAME))
                        .resourceSeq(r.get(A_RESOURCE_SEQ))
                        .versionNumber(r.get(A_VERSION_NUMBER))
                        .sizeByte(r.get(A_SIZE_BYTE))
                        .versionType(r.get(A_VERSION_TYPE) == null ? null : ResourceVersionType.valueOf(r.get(A_VERSION_TYPE)))
                        .ownerSeq(r.get(A_OWNER_SEQ))
                        .ownerName(r.get("owner_name", String.class))
                        .ownerStatus(r.get("owner_status", UserStatus.class))
                        .ownerTenantSeq(r.get("owner_tenant_seq", Integer.class))
                        .ownerTenantName(r.get("owner_tenant_name", String.class))
                        .versionRegisterDate(r.get(A_VERSION_REGISTER_DATE))
                        .build());
    }

    @Override
    public Long countAll(ResourceVersionListQuery q) {
        return versionJoin(dsl.select(count()).from(versionUnion(q)))
                .where(ResourceJooq.visibleByActiveUser(A_USER_SEQ))
                .fetchSingle(0, Long.class);
    }

    /** resource-version-union: 이력(상태 필터·리소스 is_delete 조인) + 현재 버전(tb_resource). 두 번째 분기의 NULL 은 PG 가 text 로 추론하므로 CAST. */
    private static Table<?> versionUnion(ResourceVersionListQuery q) {
        TbResourceVersion v = TB_RESOURCE_VERSION.as("a");
        TbResource b = TB_RESOURCE.as("b");
        TbResource cur = TB_RESOURCE;
        return select(v.RESOURCE_VERSION_SEQ, v.USER_SEQ, v.RESOURCE_SEQ, v.OWNER_SEQ, v.VERSION_NUMBER, v.SIZE_BYTE, inline(ResourceVersionType.HISTORY.name()).as("version_type"),
                        v.VERSION_REGISTER_DATE, v.REGISTER_USER_SEQ)
                .from(v)
                .join(b).on(v.RESOURCE_SEQ.eq(b.RESOURCE_SEQ).and(v.USER_SEQ.eq(b.USER_SEQ)))
                .where(eq(v.USER_SEQ, q.getUserSeq()))
                .and(eq(v.RESOURCE_SEQ, q.getResourceSeq()))
                .and(eq(v.STATUS, q.getResourceVersionStatus()))
                .and(eq(b.IS_DELETE, q.getIsDelete()))
                .unionAll(select(castNull(SQLDataType.BIGINT).as("resource_version_seq"), cur.USER_SEQ, cur.RESOURCE_SEQ, cur.OWNER_SEQ, cur.CURRENT_VERSION_NUMBER.as("version_number"), cur.SIZE_BYTE,
                        inline(ResourceVersionType.CURRENT.name()).as("version_type"), cur.VERSION_REGISTER_DATE, cur.REGISTER_USER_SEQ)
                        .from(cur)
                        .where(eq(cur.USER_SEQ, q.getUserSeq()))
                        .and(eq(cur.RESOURCE_SEQ, q.getResourceSeq()))
                        .and(eq(cur.IS_DELETE, q.getIsDelete())))
                .asTable("a");
    }

    /** resource-version-join: u/ut = 드라이브 소유자(a.user_seq), o/ot = 버전 등록자(a.owner_seq). */
    private static <R extends Record> SelectOnConditionStep<R> versionJoin(SelectJoinStep<R> from) {
        return from.leftJoin(U).on(A_USER_SEQ.eq(U.USER_SEQ))
                .leftJoin(UT).on(U.TENANT_SEQ.eq(UT.TENANT_SEQ))
                .leftJoin(O).on(A_OWNER_SEQ.eq(O.USER_SEQ))
                .leftJoin(OT).on(O.TENANT_SEQ.eq(OT.TENANT_SEQ));
    }

    // ------------------------------------------------------------------ 버전 키(JPQL 생성자 식)

    @Override
    public List<ResourceVersionKeyData> findAllByResource(ResourceVersionListQuery q) {
        return jpaRepository.findKeysByResourceSeqAndUserSeq(q.getResourceSeq(), q.getUserSeq());
    }

    @Override
    public List<ResourceVersionKeyData> findAllByResourceList(ResourceVersionListQuery q) {
        if (q.getResourceSeqList() == null || q.getResourceSeqList().isEmpty()) {
            return List.of();
        }
        return jpaRepository.findKeysByResourceSeqInAndUserSeq(q.getResourceSeqList(), q.getUserSeq());
    }

    /** 4조건 단건(user_seq·resource_version_seq·resource_seq·status). 조건 값이 null 이면 null(구 {@code = NULL} 0건과 같음). */
    @Override
    public ResourceVersionKeyData findResourceVersion(ResourceVersionFindQuery q) {
        return jpaRepository.findKey(q.getUserSeq(), q.getResourceVersionSeq(), q.getResourceSeq(), q.getStatus()).orElse(null);
    }

    /** JPQL 생성자 식 + 엔티티 조인(5차 패스 — 구 JdbcClient·손 매퍼). 조건은 {@code =} 라 null 인자는 null. */
    @Override
    public ResourceVersionWithResourceData findResourceVersionWithResource(ResourceVersionFindQuery q) {
        return jpaRepository.findWithResource(q.getResourceVersionSeq(), q.getResourceSeq(), q.getUserSeq(), q.getStatus()).orElse(null);
    }

    // ------------------------------------------------------------------ 저장 키·사용량

    @Override
    public List<ResourceStorageKeyData> findStorageKeysByUserSeqList(List<Integer> userSeqList) {
        if (userSeqList == null || userSeqList.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findStorageKeysByUserSeqIn(userSeqList);
    }

    @Override
    public ResourceUsageSummaryData findUsageSummaryByUserSeq(int userSeq) {
        TbResourceVersion v = TB_RESOURCE_VERSION.as("v");
        TbResource r = TB_RESOURCE.as("r");
        Field<BigDecimal> zero = inline(BigDecimal.ZERO);
        return dsl.select(coalesce(sum(v.SIZE_BYTE), zero),
                        coalesce(sum(when(r.IS_DELETE.eq(inline(false, r.IS_DELETE)), v.SIZE_BYTE)), zero),
                        coalesce(sum(when(r.IS_DELETE.eq(inline(true, r.IS_DELETE)), v.SIZE_BYTE)), zero))
                .from(v)
                .join(r).on(r.RESOURCE_SEQ.eq(v.RESOURCE_SEQ).and(r.USER_SEQ.eq(v.USER_SEQ)))
                .where(v.USER_SEQ.eq(userSeq))
                .fetchSingle(rec -> new ResourceUsageSummaryData(rec.value1().longValue(), rec.value2().longValue(), rec.value3().longValue()));
    }
}
