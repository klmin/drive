package com.thinkfree.drive.user.repository;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.global.jooq.JooqConditions;
import com.thinkfree.drive.global.jooq.JooqCursors;
import com.thinkfree.drive.jooq.tables.TbUser;
import com.thinkfree.drive.user.data.UserAuthData;
import com.thinkfree.drive.user.data.UserBulkStatusData;
import com.thinkfree.drive.user.data.UserData;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.data.UserDetailWithRolesData;
import com.thinkfree.drive.user.data.UserListData;
import com.thinkfree.drive.user.data.UserPendingDeleteData;
import com.thinkfree.drive.user.data.UserQuotaData;
import com.thinkfree.drive.user.data.UserSearchData;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;
import com.thinkfree.drive.user.query.*;
import com.thinkfree.drive.userlog.enums.ActivityType;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.thinkfree.drive.jooq.Tables.TB_SHARE_TARGET;
import static com.thinkfree.drive.jooq.Tables.TB_TENANT;
import static com.thinkfree.drive.jooq.Tables.TB_USER;
import static com.thinkfree.drive.jooq.Tables.TB_USER_LOG;
import static com.thinkfree.drive.jooq.Tables.TB_USER_ROLE;

/**
 * tb_user 읽기 — 구 UserMapper.xml 의 조회 24 statement. 쓰기는 {@link DefaultUserRepository}.
 * <ul>
 *   <li>단건 조회 = {@link UserJpaRepository} JPQL 생성자 식 DTO({@code UserDetailData}·{@code UserAuthData} — 10-12 단계 3, 엔티티를 반환하지 않음). PK 조회는 상태 조건 없음(판정은 FindService), userId 조회는 WHERE.
 *       영속 컨텍스트에 안 올라가므로 어디서 불러도 DB 현재 값이고 detach 가 필요 없다.</li>
 *   <li>스칼라·부분 컬럼 4종(tenant seq·테넌트 소속 count·기존 userId·용량) = JPQL(부분 컬럼은 생성자 식이라 영속 컨텍스트에 안 남음)</li>
 *   <li>계정 목록·테넌트 계정 검색·공유 대상/소유주 후보·물리 삭제 대상·일괄 상태·아이디별 테넌트·표시 가능 계정·삭제 대기 목록 = jOOQ(생성 코드 {@code TB_USER}·{@code TB_USER_LOG}·{@code TB_USER_ROLE}·{@code TB_TENANT}·{@code TB_SHARE_TARGET},
 *       동적 조건 + 커서, 역할 조건은 tb_user_role EXISTS, 보유 역할은 ARRAY 상관 서브쿼리)</li>
 * </ul>
 * enum·시각은 jOOQ 생성 코드의 컨버터(forcedType)·INSTANT 매핑.
 * IN 목록이 비면 SQL 을 보내지 않고 빈 결과/0 (MyBatis 때는 {@code IN ()} SQL 오류).
 */
@Repository
@RequiredArgsConstructor
public class DefaultUserQueryRepository implements UserQueryRepository {

    private static final List<UserStatus> VISIBLE_STATUS_LIST = List.of(UserStatus.ACTIVE, UserStatus.INACTIVE);

    private final DSLContext dsl;
    private final UserJpaRepository userJpaRepository;

    // ------------------------------------------------------------------ DTO 단건(JPQL 생성자 식 — 영속 컨텍스트에 안 올림, detach 불필요)

    @Override
    public UserDetailData findDetailByUserSeq(Integer userSeq) {
        return userJpaRepository.findDetailByUserSeq(userSeq).orElse(null);
    }

    @Override
    public List<UserDetailData> findDetailsByUserSeqs(List<Integer> userSeqs) {
        return userJpaRepository.findDetailsByUserSeqs(userSeqs);
    }

    @Override
    public List<UserDetailData> findUndeletedDetailsByTenantSeq(int tenantSeq) {
        return userJpaRepository.findDetailsByTenantSeqAndStatusNot(tenantSeq, UserStatus.DELETED);
    }

    @Override
    public UserDetailData findActiveDetailHoldingRole(Integer userSeq, RoleType roleType) {
        return userJpaRepository.findDetailHoldingRole(userSeq, UserStatus.ACTIVE, roleType).orElse(null);
    }

    /** SELECT_DETAIL(JPQL 생성자 식)과 같은 계정 값 + 보유 역할 — JPQL 은 한 행에 역할 목록을 담을 수 없어 jOOQ 로 */
    @Override
    public UserDetailWithRolesData findDetailWithRoles(Integer userSeq) {
        Field<List<RoleType>> roles = rolesOf(TB_USER.USER_SEQ);
        return dsl.select(TB_USER.USER_SEQ, TB_USER.USER_ID, TB_USER.USER_NAME, TB_USER.STATUS,
                        TB_USER.TOTAL_QUOTA_BYTE, TB_USER.USE_QUOTA_BYTE, TB_USER.REGISTER_USER_SEQ, TB_USER.REGISTER_DATE,
                        TB_USER.UPDATE_USER_SEQ, TB_USER.UPDATE_DATE, TB_USER.USER_LANGUAGE, TB_USER.COUNTRY_CODE, TB_USER.PHONE_NUMBER,
                        TB_USER.DEFAULT_LANDING_PAGE, TB_USER.PROFILE_IMAGE_KEY, TB_USER.TENANT_SEQ, TB_USER.MFA_STATUS, roles, TB_USER.IS_PROTECTED)
                .from(TB_USER)
                .where(TB_USER.USER_SEQ.eq(userSeq))
                .fetchOne(r -> new UserDetailWithRolesData(
                        r.get(TB_USER.USER_SEQ), r.get(TB_USER.USER_ID), r.get(TB_USER.USER_NAME),
                        r.get(TB_USER.STATUS), r.get(TB_USER.TOTAL_QUOTA_BYTE), r.get(TB_USER.USE_QUOTA_BYTE),
                        r.get(TB_USER.REGISTER_USER_SEQ), r.get(TB_USER.REGISTER_DATE),
                        r.get(TB_USER.UPDATE_USER_SEQ), r.get(TB_USER.UPDATE_DATE),
                        // user_language·default_landing_page 는 코드 문자열(JPA 컨버터와 같은 매핑)
                        r.get(TB_USER.USER_LANGUAGE),
                        r.get(TB_USER.COUNTRY_CODE), r.get(TB_USER.PHONE_NUMBER),
                        r.get(TB_USER.DEFAULT_LANDING_PAGE),
                        r.get(TB_USER.PROFILE_IMAGE_KEY), r.get(TB_USER.TENANT_SEQ), r.get(TB_USER.MFA_STATUS),
                        r.get(roles), r.get(TB_USER.IS_PROTECTED)));
    }

    @Override
    public UserAuthData findAuthByUserSeq(Integer userSeq) {
        return userJpaRepository.findAuthByUserSeq(userSeq).orElse(null);
    }

    @Override
    public UserDetailData findActiveDetailByUserId(int tenantSeq, String userId) {
        return userJpaRepository.findDetailInTenant(tenantSeq, userId, UserStatus.ACTIVE, RoleType.USER).orElse(null);
    }

    @Override
    public UserAuthData findActiveAuthByUserId(int tenantSeq, String userId, RoleType roleType) {
        return userJpaRepository.findAuthInTenant(tenantSeq, userId, UserStatus.ACTIVE, roleType).orElse(null);
    }

    @Override
    public UserAuthData findActiveSuperAdminAuthByUserId(String userId) {
        return userJpaRepository.findTenantlessAuth(userId, UserStatus.ACTIVE, RoleType.SUPER_ADMIN).orElse(null);
    }

    @Override
    public UserDetailData findEarliestActiveSuperAdmin() {
        return userJpaRepository.findOldestDetailsByRoleAndStatus(RoleType.SUPER_ADMIN, UserStatus.ACTIVE, Limit.of(1)).stream().findFirst().orElse(null);
    }

    @Override
    public UserDetailData findAnonymousUser() {
        return userJpaRepository.findDetailsByRoleTypeAndStatus(RoleType.ANONYMOUS, UserStatus.ACTIVE, Limit.of(1)).stream().findFirst().orElse(null);
    }

    // ------------------------------------------------------------------ 스칼라

    @Override
    public Integer findTenantSeqByUserSeq(Integer userSeq) {
        return userJpaRepository.findTenantSeqByUserSeq(userSeq).orElse(null);
    }

    @Override
    public Integer findTenantSeqIfProtected(int userSeq) {
        return userJpaRepository.findTenantSeqByUserSeqAndIsProtected(userSeq, Boolean.TRUE).orElse(null);
    }

    @Override
    public int countByUserSeqsAndTenant(List<Integer> userSeqList, Integer tenantSeq) {
        if (userSeqList == null || userSeqList.isEmpty()) {
            return 0;
        }
        return (int) userJpaRepository.countByTenantSeqAndUserSeqIn(tenantSeq, userSeqList);
    }

    @Override
    public boolean existsUserId(Integer tenantSeq, String userId) {
        return dsl.fetchExists(TB_USER, userIdScope(tenantSeq).and(TB_USER.USER_ID.eq(userId)));
    }

    @Override
    public Set<String> findExistingUserIds(Integer tenantSeq, List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(dsl.select(TB_USER.USER_ID)
                .from(TB_USER)
                .where(userIdScope(tenantSeq))
                .and(TB_USER.USER_ID.in(userIds))
                .fetch(TB_USER.USER_ID));
    }

    /**
     * userId 유일 범위 = 같은 테넌트의 DELETED 아닌 계정 — 유니크 인덱스 uniq_active_user_id((COALESCE(tenant_seq, 0)), user_id) WHERE status &lt;&gt; 'DELETED' 와 같은 식.
     * tenantSeq null(슈퍼관리자·익명)은 0 으로 맞춰 테넌트 없는 계정끼리 비교한다.
     */
    private static Condition userIdScope(Integer tenantSeq) {
        return DSL.coalesce(TB_USER.TENANT_SEQ, 0).eq(tenantSeq == null ? 0 : tenantSeq)
                .and(TB_USER.STATUS.ne(UserStatus.DELETED));
    }

    @Override
    public UserQuotaData findUserQuotaByUserSeq(Integer userSeq) {
        return userJpaRepository.findQuotaByUserSeqAndStatus(userSeq, UserStatus.ACTIVE).orElse(null);
    }

    @Override
    public long sumTotalQuotaByteByTenantSeq(Integer tenantSeq) {
        return userJpaRepository.sumTotalQuotaByteByTenantSeq(tenantSeq);
    }

    // ------------------------------------------------------------------ DTO

    /**
     * 계정 목록: roles 중 하나라도 보유(tb_user_role EXISTS — 서비스가 USER·ADMIN 으로 검증·기본값), status 가 있으면 그 상태만
     * (서비스가 ACTIVE/INACTIVE 로 검증), 없으면 노출 가능 상태 전체. userId 부분 일치, user_seq ASC 커서.
     */
    @Override
    public List<UserListData> findDisplayableUsers(UserListQuery query) {
        Condition condition = listCondition(query);
        if (JooqConditions.present(query.getUserId())) {
            // 구 SQL(LIKE CONCAT('%', :userId, '%'))과 같게 입력의 % · _ 를 이스케이프하지 않는다
            condition = condition.and(TB_USER.USER_ID.like("%" + query.getUserId() + "%"));
        }
        return list(condition, query.getPage());
    }

    private static Condition listCondition(UserListQuery query) {
        Condition condition = holdsAnyRole(query.getRoles())
                .and(TB_USER.TENANT_SEQ.eq(query.getTenantSeq()))
                .and(query.getStatus() != null
                        ? TB_USER.STATUS.eq(query.getStatus())
                        : TB_USER.STATUS.in(VISIBLE_STATUS_LIST));
        Long cursorId = query.getPage().getCursorId();
        if (cursorId != null) {
            // user_seq 는 integer, 커서는 Long — int 범위 밖 커서도 구 SQL(bigint 비교)과 같은 결과가 되게 끝을 막는다
            condition = condition.and(cursorId > Integer.MAX_VALUE ? DSL.falseCondition()
                    : cursorId < Integer.MIN_VALUE ? DSL.noCondition()
                    : TB_USER.USER_SEQ.gt(cursorId.intValue()));
        }
        return condition;
    }

    /** 계정 목록 — 최근 로그인(tb_user_log LOGIN 의 MAX) LEFT JOIN, user_seq ASC, pageSize 가 없으면 제한 없음(구 FETCH FIRST NULL). */
    private List<UserListData> list(Condition condition, CursorPaginationQuery page) {
        Field<Instant> maxLoginDate = DSL.max(TB_USER_LOG.REGISTER_DATE).as("last_login_date");
        Table<?> lastLogin = DSL.select(TB_USER_LOG.USER_SEQ, maxLoginDate)
                .from(TB_USER_LOG)
                .where(TB_USER_LOG.ACTIVITY_TYPE.eq(ActivityType.LOGIN))
                .groupBy(TB_USER_LOG.USER_SEQ)
                .asTable("l");
        Field<Integer> lastLoginUserSeq = lastLogin.field(TB_USER_LOG.USER_SEQ);
        Field<Instant> lastLoginDate = lastLogin.field(maxLoginDate);
        Field<List<RoleType>> roles = rolesOf(TB_USER.USER_SEQ);

        var select = dsl.select(TB_USER.USER_SEQ, TB_USER.USER_ID, TB_USER.STATUS, TB_USER.USER_NAME,
                        TB_USER.TOTAL_QUOTA_BYTE, TB_USER.USE_QUOTA_BYTE,
                        TB_USER.REGISTER_USER_SEQ, TB_USER.REGISTER_DATE, TB_USER.UPDATE_USER_SEQ, TB_USER.UPDATE_DATE, lastLoginDate, roles, TB_USER.IS_PROTECTED)
                .from(TB_USER)
                .leftJoin(lastLogin).on(lastLoginUserSeq.eq(TB_USER.USER_SEQ))
                .where(condition)
                .orderBy(TB_USER.USER_SEQ.asc());
        var rows = page.getPageSize() == null ? select.fetch() : select.limit(page.getPageSize()).fetch();

        return rows.map(r -> UserListData.builder()
                .userSeq(r.get(TB_USER.USER_SEQ))
                .userId(r.get(TB_USER.USER_ID))
                .userName(r.get(TB_USER.USER_NAME))
                .status(r.get(TB_USER.STATUS))
                .totalQuotaByte(r.get(TB_USER.TOTAL_QUOTA_BYTE))
                .useQuotaByte(r.get(TB_USER.USE_QUOTA_BYTE))
                .registerUserSeq(r.get(TB_USER.REGISTER_USER_SEQ))
                .registerDate(r.get(TB_USER.REGISTER_DATE))
                .updateUserSeq(r.get(TB_USER.UPDATE_USER_SEQ))
                .updateDate(r.get(TB_USER.UPDATE_DATE))
                .lastLoginDate(r.get(lastLoginDate))
                .roles(r.get(roles))
                .isProtected(r.get(TB_USER.IS_PROTECTED))
                .build());
    }

    /** 보유 역할 — ARRAY 상관 서브쿼리라 계정당 1행(역할 수만큼 행이 늘지 않음), 역할 행이 없으면 빈 배열 */
    private static Field<List<RoleType>> rolesOf(Field<Integer> userSeq) {
        return DSL.array(DSL.select(TB_USER_ROLE.ROLE_TYPE).from(TB_USER_ROLE).where(TB_USER_ROLE.USER_SEQ.eq(userSeq)))
                .convertFrom(roles -> roles == null ? List.<RoleType>of() : Arrays.stream(roles).sorted().toList())
                .as("roles");
    }

    private static Condition holdsAnyRole(List<RoleType> roles) {
        return DSL.exists(DSL.selectOne().from(TB_USER_ROLE)
                .where(TB_USER_ROLE.USER_SEQ.eq(TB_USER.USER_SEQ).and(TB_USER_ROLE.ROLE_TYPE.in(roles))));
    }

    private static Condition holdsRole(RoleType role) {
        return DSL.exists(DSL.selectOne().from(TB_USER_ROLE)
                .where(TB_USER_ROLE.USER_SEQ.eq(TB_USER.USER_SEQ).and(TB_USER_ROLE.ROLE_TYPE.eq(role))));
    }

    /** userId·userName 부분 일치 — 구 SQL 의 PG CONCAT 은 NULL 을 빈 문자열로 취급(전체 일치), % · _ 는 이스케이프하지 않는다 */
    private static Condition idOrNameLike(String keyword) {
        String pattern = "%" + (keyword == null ? "" : keyword) + "%";
        return TB_USER.USER_ID.like(pattern).or(TB_USER.USER_NAME.like(pattern));
    }

    /** 공유 대상·소유주 후보 공통 — 소속 테넌트 INNER JOIN(테넌트 없는 계정 제외), 이름순 */
    private List<UserData> candidates(Condition condition) {
        return dsl.select(TB_USER.USER_SEQ, TB_USER.USER_ID, TB_USER.USER_NAME, TB_USER.STATUS, TB_USER.TENANT_SEQ, TB_TENANT.TENANT_NAME)
                .from(TB_USER)
                .join(TB_TENANT).on(TB_TENANT.TENANT_SEQ.eq(TB_USER.TENANT_SEQ))
                .where(condition)
                .orderBy(TB_USER.USER_NAME)
                .fetch(r -> UserData.builder()
                        .userSeq(r.get(TB_USER.USER_SEQ))
                        .userId(r.get(TB_USER.USER_ID))
                        .userName(r.get(TB_USER.USER_NAME))
                        .status(r.get(TB_USER.STATUS))
                        .tenantSeq(r.get(TB_USER.TENANT_SEQ))
                        .tenantName(r.get(TB_TENANT.TENANT_NAME))
                        .build());
    }

    @Override
    public List<UserData> findVisibleUsers(UserVisibleQuery query) {
        List<Integer> seqs = query.getUserSeqList();
        if (seqs == null || seqs.isEmpty()) {
            return List.of();
        }
        return dsl.select(TB_USER.USER_SEQ, TB_USER.USER_ID, TB_USER.USER_NAME, TB_USER.TENANT_SEQ, TB_TENANT.TENANT_NAME)
                .from(TB_USER)
                .leftJoin(TB_TENANT).on(TB_TENANT.TENANT_SEQ.eq(TB_USER.TENANT_SEQ))
                .where(TB_USER.USER_SEQ.in(seqs))
                .and(TB_USER.STATUS.ne(UserStatus.DELETED))
                .fetch(r -> UserData.builder()
                        .userSeq(r.get(TB_USER.USER_SEQ))
                        .userId(r.get(TB_USER.USER_ID))
                        .userName(r.get(TB_USER.USER_NAME))
                        .tenantSeq(r.get(TB_USER.TENANT_SEQ))
                        .tenantName(r.get(TB_TENANT.TENANT_NAME))
                        .build());
    }

    @Override
    public List<UserData> findUserTenantsByUserIds(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return dsl.select(TB_USER.USER_ID, TB_USER.TENANT_SEQ)
                .from(TB_USER)
                .where(TB_USER.USER_ID.in(userIds))
                .and(TB_USER.STATUS.ne(UserStatus.DELETED))
                .fetch(r -> UserData.builder()
                        .userId(r.get(TB_USER.USER_ID))
                        .tenantSeq(r.get(TB_USER.TENANT_SEQ))
                        .build());
    }

    /** 테넌트 계정 검색 — roleTypes 중 하나라도 보유(tb_user_role EXISTS), userId·userName 부분 일치, 이름순. 항목에 보유 역할 전부 */
    @Override
    public List<UserSearchData> searchTenantUsers(UserTenantSearchQuery query) {
        if (isEmpty(query.getUserStatuses()) || isEmpty(query.getRoleTypes())) {
            return List.of();
        }
        Field<List<RoleType>> roles = rolesOf(TB_USER.USER_SEQ);
        return dsl.select(TB_USER.USER_SEQ, TB_USER.USER_ID, TB_USER.USER_NAME, TB_USER.STATUS, TB_USER.TENANT_SEQ, TB_USER.IS_PROTECTED, roles)
                .from(TB_USER)
                .where(TB_USER.TENANT_SEQ.eq(query.getTenantSeq()))
                .and(TB_USER.STATUS.in(query.getUserStatuses()))
                .and(holdsAnyRole(query.getRoleTypes()))
                .and(idOrNameLike(query.getSearchKeyword()))
                .orderBy(TB_USER.USER_NAME)
                .fetch(r -> UserSearchData.builder()
                        .userSeq(r.get(TB_USER.USER_SEQ))
                        .userId(r.get(TB_USER.USER_ID))
                        .userName(r.get(TB_USER.USER_NAME))
                        .status(r.get(TB_USER.STATUS))
                        .tenantSeq(r.get(TB_USER.TENANT_SEQ))
                        .isProtected(Boolean.TRUE.equals(r.get(TB_USER.IS_PROTECTED)))
                        .roles(r.get(roles))
                        .build());
    }

    /** 공유 대상 후보(share 미지정) — roleType 보유, tenantSeq 가 있으면 그 테넌트로 한정(외부공유 불허), null 이면 전 테넌트. */
    @Override
    public List<UserData> findShareCandidates(UserSearchQuery query) {
        if (isEmpty(query.getUserStatuses())) {
            return List.of();
        }
        Condition condition = TB_USER.STATUS.in(query.getUserStatuses())
                .and(holdsRole(query.getRoleType()))
                .and(query.getTenantSeq() != null ? TB_USER.TENANT_SEQ.eq(query.getTenantSeq()) : DSL.noCondition())
                .and(idOrNameLike(query.getSearchKeyword()));
        return candidates(condition);
    }

    /** 문서함 소유주 필터 검색: roleType 보유 + (나 자신 + 나에게 공유한 소유주 + 내가 공유한 사람 — share_target 양방향). */
    @Override
    public List<UserData> findOwnerCandidates(UserSearchQuery query) {
        Integer me = query.getRequestUserSeq();
        if (isEmpty(query.getUserStatuses()) || me == null) {
            return List.of();   // 구 SQL 도 요청자가 null 이면 세 조건 모두 거짓 — 0건
        }
        Condition related = TB_USER.USER_SEQ.eq(me)
                .or(TB_USER.USER_SEQ.in(DSL.select(TB_SHARE_TARGET.SHARED_BY_USER_SEQ).from(TB_SHARE_TARGET).where(TB_SHARE_TARGET.USER_SEQ.eq(me))))
                .or(TB_USER.USER_SEQ.in(DSL.select(TB_SHARE_TARGET.USER_SEQ).from(TB_SHARE_TARGET).where(TB_SHARE_TARGET.SHARED_BY_USER_SEQ.eq(me))));
        Condition condition = TB_USER.STATUS.in(query.getUserStatuses())
                .and(holdsRole(query.getRoleType()))
                .and(related)
                .and(idOrNameLike(query.getSearchKeyword()));
        return candidates(condition);
    }

    /**
     * 삭제 대기 목록 — 기간 필터는 시작·끝이 둘 다 있을 때만, 커서는 (pending_delete_date DESC, user_seq DESC) 튜플(cursorValue·cursorId 둘 다 있을 때).
     * 삭제 처리자(update_user_seq)는 INNER JOIN — 처리자가 없는 행은 빠진다. pageSize 가 없으면 제한 없음(구 FETCH FIRST NULL).
     */
    @Override
    public List<UserPendingDeleteData> findPendingDeleteUsers(UserPendingDeleteListQuery query) {
        if (query.getTenantSeq() == null) {
            return List.of();   // 구 SQL 도 tenant_seq = NULL 비교라 0건
        }
        var page = query.getPage();
        TbUser deleter = TB_USER.as("b");
        Condition condition = TB_USER.STATUS.eq(UserStatus.PENDING_DELETE)
                .and(TB_USER.TENANT_SEQ.eq(query.getTenantSeq()));
        if (query.getPendingDeleteStartDate() != null && query.getPendingDeleteEndDate() != null) {
            condition = condition.and(TB_USER.PENDING_DELETE_DATE.ge(query.getPendingDeleteStartDate()))
                    .and(TB_USER.PENDING_DELETE_DATE.lt(query.getPendingDeleteEndDate()));
        }
        if (query.getDeleteScheduleStartDate() != null && query.getDeleteScheduleEndDate() != null) {
            condition = condition.and(TB_USER.DELETE_SCHEDULE_TIME.ge(query.getDeleteScheduleStartDate()))
                    .and(TB_USER.DELETE_SCHEDULE_TIME.lt(query.getDeleteScheduleEndDate()));
        }
        if (JooqConditions.present(page.getCursorId()) && JooqConditions.present(page.getCursorValue())) {
            // 커서 문자열 해석은 구 SQL 과 같이 DB CAST — 해석 못 하는 값이면 DB 오류(구 동작 그대로)
            Field<Instant> cursorValue = DSL.field("CAST({0} AS TIMESTAMPTZ)", SQLDataType.INSTANT, DSL.val(JooqCursors.timestamp(page.getCursorValue())));
            condition = condition.and(TB_USER.PENDING_DELETE_DATE.lt(cursorValue)
                    .or(TB_USER.PENDING_DELETE_DATE.eq(cursorValue).and(TB_USER.USER_SEQ.coerce(Long.class).lt(page.getCursorId()))));
        }
        Field<List<RoleType>> roles = rolesOf(TB_USER.USER_SEQ);

        var select = dsl.select(TB_USER.USER_SEQ, TB_USER.USER_ID, TB_USER.USER_NAME, TB_USER.STATUS,
                        TB_USER.PENDING_DELETE_DATE, TB_USER.DELETE_SCHEDULE_TIME, TB_USER.UPDATE_USER_SEQ,
                        deleter.USER_ID, deleter.STATUS, roles)
                .from(TB_USER)
                .join(deleter).on(deleter.USER_SEQ.eq(TB_USER.UPDATE_USER_SEQ))
                .where(condition)
                .orderBy(TB_USER.PENDING_DELETE_DATE.desc(), TB_USER.USER_SEQ.desc());
        var rows = page.getPageSize() == null ? select.fetch() : select.limit(page.getPageSize()).fetch();

        return rows.map(r -> UserPendingDeleteData.builder()
                .userSeq(r.get(TB_USER.USER_SEQ))
                .userId(r.get(TB_USER.USER_ID))
                .userName(r.get(TB_USER.USER_NAME))
                .status(r.get(TB_USER.STATUS))
                .roles(r.get(roles))
                .pendingDeleteDate(r.get(TB_USER.PENDING_DELETE_DATE))
                .deleteScheduleTime(r.get(TB_USER.DELETE_SCHEDULE_TIME))
                .deleteUserSeq(r.get(TB_USER.UPDATE_USER_SEQ))
                .deleteUserId(r.get(deleter.USER_ID))
                .deleteUserStatus(r.get(deleter.STATUS))
                .build());
    }

    /**
     * 물리 삭제 대상 — userSeqList(있을 때만 IN)·deleteDueAt(있을 때만 schedule < due). 둘 다 없으면 PENDING_DELETE 전체.
     * roleType = ADMIN 보유면 ADMIN, 아니면 USER(영구 삭제 감사 이벤트 ADMIN_/USER_ 분기용)
     */
    @Override
    public List<UserPendingDeleteData> findPendingDeleteTargets(UserPendingDeleteTargetQuery query) {
        Condition condition = TB_USER.STATUS.eq(UserStatus.PENDING_DELETE);
        if (!isEmpty(query.getUserSeqList())) {
            condition = condition.and(TB_USER.USER_SEQ.in(query.getUserSeqList()));
        }
        if (query.getDeleteDueAt() != null) {
            condition = condition.and(TB_USER.DELETE_SCHEDULE_TIME.lt(query.getDeleteDueAt()));
        }
        Field<Boolean> holdsAdmin = DSL.field(holdsRole(RoleType.ADMIN)).as("holds_admin");
        return dsl.select(TB_USER.USER_SEQ, TB_USER.USER_ID, TB_USER.USER_NAME, TB_USER.TENANT_SEQ, TB_USER.PROFILE_IMAGE_KEY, holdsAdmin)
                .from(TB_USER)
                .where(condition)
                .fetch(r -> UserPendingDeleteData.builder()
                        .userSeq(r.get(TB_USER.USER_SEQ))
                        .userId(r.get(TB_USER.USER_ID))
                        .userName(r.get(TB_USER.USER_NAME))
                        .roleType(Boolean.TRUE.equals(r.get(holdsAdmin)) ? RoleType.ADMIN : RoleType.USER)
                        .tenantSeq(r.get(TB_USER.TENANT_SEQ))
                        .profileImageKey(r.get(TB_USER.PROFILE_IMAGE_KEY))
                        .build());
    }

    /** 일괄 등록 대상의 기존 상태 — 테넌트·USER 보유·보이는 상태(ACTIVE·INACTIVE) 한정 */
    @Override
    public List<UserBulkStatusData> findUserStatusByUserIds(UserBulkStatusQuery query) {
        if (isEmpty(query.getUserIds()) || query.getTenantSeq() == null) {
            return List.of();   // 구 JPQL 도 tenantSeq null 이면 = null 비교라 0건
        }
        return dsl.select(TB_USER.USER_SEQ, TB_USER.USER_ID, TB_USER.STATUS)
                .from(TB_USER)
                .where(TB_USER.TENANT_SEQ.eq(query.getTenantSeq()))
                .and(TB_USER.USER_ID.in(query.getUserIds()))
                .and(TB_USER.STATUS.in(VISIBLE_STATUS_LIST))
                .and(holdsRole(RoleType.USER))
                .fetch(r -> UserBulkStatusData.builder()
                        .userSeq(r.get(TB_USER.USER_SEQ))
                        .userId(r.get(TB_USER.USER_ID))
                        .status(r.get(TB_USER.STATUS))
                        .build());
    }

    private static boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }
}
