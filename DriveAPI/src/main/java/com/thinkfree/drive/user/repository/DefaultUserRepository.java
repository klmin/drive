package com.thinkfree.drive.user.repository;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.jdbc.JdbcAudit;
import com.thinkfree.drive.global.jooq.JooqBatches;
import com.thinkfree.drive.jooq.tables.records.TbUserRecord;
import com.thinkfree.drive.global.jpa.audit.UpdateStamp;
import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.user.enums.MfaMethod;
import com.thinkfree.drive.user.enums.MfaStatus;
import com.thinkfree.drive.user.enums.UserStatus;
import com.thinkfree.drive.user.exception.UserErrorCode;
import com.thinkfree.drive.user.query.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static com.thinkfree.drive.jooq.Tables.TB_USER;

/**
 * tb_user 쓰기. 읽기는 {@link DefaultUserQueryRepository}.
 * <ul>
 *   <li>고칠 행 로드·잠금 = JPA({@code findById}·{@code @Lock} JPQL) — 관리 엔티티를 Service 가 {@link User} 변경 메서드로 고치고 커밋 때 변경 감지(감사는 Auditing, W7 — flush·detach 없음)</li>
 *   <li>JPQL {@code @Modifying} 잔존 = 유지 예외(원자적 증감·빈번한 단일 컬럼·조건부 다건·MFA 조건부 원자 판정 — 이유는 {@link UserJpaRepository} 메서드마다)</li>
 *   <li>jOOQ = 시트 검사가 붙은 INSERT…SELECT(원자적 카운트 비교 — JPQL 에 없는 문장, RETURNING user_seq 라 행마다)와
 *       관리자 일괄 수정 배치 UPDATE(행마다 값이 다른 다건 수정. {@code @DynamicUpdate} 엔티티의 변경 감지는 Hibernate 6.6 에서 JDBC 배치가 안 돼 행마다 왕복, B1).
 *       감사는 {@link JdbcAudit}(시큐리티 사용자 우선, 현재 시각 초 절삭)</li>
 *   <li>SEAT 좌석 소비(등록·활성화)는 테넌트 행 잠금({@link #lockTenantForSeat}) 뒤 좌석 조건 문장 — 다른 행끼리의 동시 요청도 직렬화</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class DefaultUserRepository implements UserRepository {

    private final UserJpaRepository jpaRepository;
    private final DSLContext dsl;

    // ------------------------------------------------------------------ INSERT(jOOQ)

    @Override
    public void lockTenantForSeat(int tenantSeq) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("좌석 잠금은 트랜잭션 안에서만 — 밖이면 잠금이 바로 풀려 직렬화되지 않는다");
        }
        jpaRepository.lockTenant(tenantSeq);
    }

    /**
     * 시트 검사: isSeat 면 같은 테넌트 ACTIVE 수 < constrainValue 일 때만 삽입(0건 = 시트 초과). register_date 등은 DB 기본값.
     * 잠금 뒤 실행하는 INSERT…SELECT 는 새 문장 스냅샷이라 앞 요청이 커밋한 행까지 센다.
     */
    @Override
    public boolean insertUserWithSeatCheck(User user, boolean isSeat, int constrainValue) {
        if (isSeat) {
            lockTenantForSeat(user.getTenantSeq());
        }
        Table<?> t = DSL.select(DSL.count().as("active_count"))
                .from(TB_USER)
                .where(TB_USER.STATUS.eq(UserStatus.ACTIVE))
                .and(TB_USER.TENANT_SEQ.eq(user.getTenantSeq()))
                .asTable("t");
        Field<Integer> activeCount = t.field("active_count", Integer.class);
        Record1<Integer> inserted;
        try {
            inserted = insertSelect(user, t, isSeat ? Objects.requireNonNull(activeCount).lt(constrainValue) : DSL.noCondition());
        } catch (DuplicateKeyException e) {
            // 중복 검사와 INSERT 사이에 같은 테넌트에 같은 userId 가 먼저 들어간 경우(uniq_active_user_id) — 500 대신 중복 오류
            throw ApiRuntimeException.type(UserErrorCode.DUPLICATE_EMAIL);
        }
        if (inserted == null) {
            return false;
        }
        user.assignUserSeq(inserted.value1());
        return true;
    }

    private Record1<Integer> insertSelect(User user, Table<?> t, Condition seatCondition) {
        return dsl.insertInto(TB_USER,
                        TB_USER.USER_ID, TB_USER.TENANT_SEQ, TB_USER.ENC_PWD, TB_USER.SIGNUP_TYPE, TB_USER.USER_NAME, TB_USER.STATUS,
                        TB_USER.TOTAL_QUOTA_BYTE, TB_USER.REGISTER_USER_SEQ, TB_USER.PROFILE_IMAGE_KEY, TB_USER.COUNTRY_CODE, TB_USER.PHONE_NUMBER,
                        TB_USER.IS_PROTECTED)
                .select(DSL.select(
                                DSL.val(user.getUserId(), TB_USER.USER_ID),
                                DSL.val(user.getTenantSeq(), TB_USER.TENANT_SEQ),
                                DSL.val(user.getEncPwd() == null ? null : user.getEncPwd().getValue(), TB_USER.ENC_PWD),
                                DSL.val(user.getSignupType(), TB_USER.SIGNUP_TYPE),
                                DSL.val(user.getUserName(), TB_USER.USER_NAME),
                                DSL.val(user.getStatus(), TB_USER.STATUS),
                                DSL.val(user.getTotalQuotaByte(), TB_USER.TOTAL_QUOTA_BYTE),
                                DSL.val(JdbcAudit.userSeqOr(user.getRegisterUserSeq()), TB_USER.REGISTER_USER_SEQ),
                                DSL.val(user.getProfileImageKey(), TB_USER.PROFILE_IMAGE_KEY),
                                DSL.val(user.getCountryCode(), TB_USER.COUNTRY_CODE),
                                DSL.val(user.getPhoneNumber(), TB_USER.PHONE_NUMBER),
                                DSL.val(Boolean.TRUE.equals(user.getIsProtected()), TB_USER.IS_PROTECTED))
                        .from(t)
                        .where(seatCondition))
                .returningResult(TB_USER.USER_SEQ)
                .fetchOne();
    }

    @Override
    public int insertAll(List<User> entities, boolean isSeat, int constrainValue) {
        int count = 0;
        for (User user : entities) {
            if (!insertUserWithSeatCheck(user, isSeat, constrainValue)) {
                throw ApiRuntimeException.type(UserErrorCode.INSERT_FAIL);
            }
            count++;
        }
        log.info("[유저 데이터 저장] : {}건", count);
        return count;
    }

    // ------------------------------------------------------------------ 고칠 행 로드·잠금(JPA — 관리 엔티티)

    @Override
    public User findById(int userSeq) {
        return jpaRepository.findById(userSeq).orElse(null);
    }

    @Override
    public User lockUser(int userSeq) {
        return jpaRepository.lockByUserSeq(userSeq).orElse(null);
    }

    @Override
    public List<User> lockUsers(List<Integer> userSeqList) {
        if (userSeqList == null || userSeqList.isEmpty()) {
            return List.of();
        }
        return jpaRepository.lockByUserSeqIn(userSeqList);
    }

    // ------------------------------------------------------------------ UPDATE(JPQL — 유지 예외)

    @Override
    public int addUseQuotaByte(int userSeq, Long delta) {
        return jpaRepository.addUseQuotaByte(userSeq, delta);
    }

    @Override
    public int activateUser(Integer loginUserSeq, int userSeq, int tenantSeq, boolean isSeat, int constrainValue) {
        UpdateStamp stamp = UpdateStamp.of(loginUserSeq, null);
        if (isSeat) {
            lockTenantForSeat(tenantSeq);
            return jpaRepository.activateWithSeatCheck(userSeq, UserStatus.ACTIVE, tenantSeq, constrainValue, stamp);
        }
        return jpaRepository.activate(userSeq, UserStatus.ACTIVE, stamp);
    }

    @Override
    public void increaseLoginFailureCount(Integer userSeq) {
        jpaRepository.increaseLoginFailureCount(userSeq, UserStatus.ACTIVE);
    }

    @Override
    public void resetLoginFailureCount(Integer userSeq) {
        jpaRepository.resetLoginFailureCount(userSeq, UserStatus.ACTIVE);
    }

    /** 조건부 다건 — requirePendingDelete 면 PENDING_DELETE 만, 아니면 DELETED 가 아닌 전부. 빈 목록은 0(IN () 오류 방지). */
    @Override
    public int markUsersAsDeleted(UserDeleteMarkQuery q) {
        if (q.getUserSeqList() == null || q.getUserSeqList().isEmpty()) {
            return 0;
        }
        UpdateStamp stamp = UpdateStamp.of(q.getUpdateUserSeq(), q.getUpdateDate());
        if (q.isRequirePendingDelete()) {
            return jpaRepository.markDeletedFromPendingDelete(q.getUserSeqList(), UserStatus.DELETED, UserStatus.PENDING_DELETE,
                    q.getDeleteDate(), stamp);
        }
        return jpaRepository.markDeleted(q.getUserSeqList(), UserStatus.DELETED, q.getDeleteDate(), stamp);
    }

    // ------------------------------------------------------------------ 배치 UPDATE(jOOQ — 유지 예외)

    /**
     * 행 단위 배치({@link JooqBatches} 레코드 배치 — 컬럼 setter 로 값, PK 로 WHERE, 1000행씩 한 PreparedStatement 배치). 처리 건수 = 항목 수(구 MyBatis 배치 executor 와 동일), 예외는 UPDATE_FAIL 로 감싼다.
     * 관리자 단건 수정과 달리 profile_image_key 는 건드리지 않음(XML updateUserInfo 와 동일).
     * 같은 값이어도 UPDATE 가 나가 update_* 가 갱신된다(구 동작). 같은 트랜잭션에 이 행들의 관리 엔티티(잠금 로드)가 있어도 고치지 않았으면
     * dirty 가 아니라 커밋 flush 가 덮어쓰지 않는다 — 단 그 엔티티의 메모리 값은 옛 값.
     */
    @Override
    public int updateAll(List<AdminUserBulkUpdateQuery> queries) {
        if (queries == null || queries.isEmpty()) {
            return 0;
        }
        Integer updateUserSeq = JdbcAudit.userSeqOr(null);
        Instant updateDate = JdbcAudit.now();
        int[] batch;
        try {
            batch = JooqBatches.update(dsl, queries, q -> toUpdateRecord(q, updateUserSeq, updateDate));
        } catch (RuntimeException e) {
            log.error("[유저 데이터 일괄 수정 (batch) 실패]", e);
            throw ApiRuntimeException.type(UserErrorCode.UPDATE_FAIL);
        }
        log.info("[유저 데이터 일괄 수정 (batch)] : {}건", batch.length);
        return batch.length;
    }

    /** 일괄 수정 한 행 — user_seq 는 WHERE 로만, 나머지 set 한 6컬럼이 SET */
    private TbUserRecord toUpdateRecord(AdminUserBulkUpdateQuery q, Integer updateUserSeq, Instant updateDate) {
        TbUserRecord r = dsl.newRecord(TB_USER);
        r.setUserSeq(q.userSeq());
        r.changed(TB_USER.USER_SEQ, false); // 변경 표시를 지우면 SET 에서 빠지고 이 값이 WHERE 의 PK 가 된다
        r.setUserName(q.userName());
        r.setTotalQuotaByte(q.totalQuotaByte());
        r.setPhoneNumber(q.phoneNumber());
        r.setCountryCode(q.countryCode());
        r.setUpdateUserSeq(updateUserSeq);
        r.setUpdateDate(updateDate);
        return r;
    }

    // ------------------------------------------------------------------ MFA(JPQL — 조건부 원자 판정·벌크)

    @Override
    public int setupMfaTotp(UserMfaQuery q) {
        return jpaRepository.setupMfa(q.getUserSeq(), MfaStatus.PENDING, MfaMethod.TOTP, List.of(MfaStatus.DISABLED, MfaStatus.PENDING),
                q.getMfaSecretKey(), q.getMfaSetupDate(), UpdateStamp.of(q.getUpdateUserSeq(), q.getUpdateDate()));
    }

    @Override
    public int confirmMfaTotp(UserMfaQuery q) {
        return jpaRepository.confirmMfa(q.getUserSeq(), MfaStatus.ENABLED, MfaStatus.PENDING, q.getCutoffAt(), q.getMfaConfirmDate(),
                UpdateStamp.of(q.getUpdateUserSeq(), q.getUpdateDate()));
    }

    @Override
    public int clearPendingMfaTotp(UserMfaQuery q) {
        return jpaRepository.clearPendingMfa(MfaStatus.DISABLED, MfaStatus.PENDING, q.getCutoffAt(),
                UpdateStamp.of(q.getUpdateUserSeq(), q.getUpdateDate()));
    }
}
