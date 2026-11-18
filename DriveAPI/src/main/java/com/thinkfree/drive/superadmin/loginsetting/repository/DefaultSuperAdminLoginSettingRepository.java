package com.thinkfree.drive.superadmin.loginsetting.repository;

import com.thinkfree.drive.global.jdbc.JdbcAudit;
import com.thinkfree.drive.superadmin.loginsetting.entity.SuperAdminLoginSetting;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import static com.thinkfree.drive.jooq.Tables.TB_SUPER_ADMIN_LOGIN_SETTING;
import static org.jooq.impl.DSL.excluded;

/**
 * tb_super_admin_login_setting 쓰기 — jOOQ 구현(구 SuperAdminLoginSettingMapper.xml 의 upsert·upsertEnforce). 읽기는 {@link DefaultSuperAdminLoginSettingQueryRepository}.
 * <p>
 * 단일 행(PK = {@link SuperAdminLoginSetting#SINGLETON_SEQ}, CHECK singleton). upsert 는 PK 충돌 → 갱신 컬럼만 다르다:
 * <ul>
 *   <li>upsert — login_type + is_enforced</li>
 *   <li>upsertEnforce — is_enforced 만(login_type 보존; 신규 행이면 login_type NULL)</li>
 * </ul>
 * update_* 는 register 바인딩 값으로(MyBatis 때 규칙 그대로), 감사 컬럼은 {@link JdbcAudit}(시큐리티 사용자 우선(없으면 선언 값), 시각은 선언 값 없으면 현재 시각 초 절삭).
 * login_type 은 enum name 문자열(MyBatis EnumTypeHandler 와 동일), is_enforced 는 Y/N(생성 코드의 Boolean 변환).
 * JPA 가 아닌 이유 = {@code ON CONFLICT} upsert 는 JPQL 에 없는 문장(로드 → 변경 감지로는 "행이 없으면 INSERT" 를 원자적으로 못 함).
 * seq 는 엔티티 값과 무관하게 싱글턴 1 고정(XML 과 동일).
 */
@Repository
@RequiredArgsConstructor
public class DefaultSuperAdminLoginSettingRepository implements SuperAdminLoginSettingRepository {

    private final DSLContext dsl;

    @Override
    public int upsert(SuperAdminLoginSetting e) {
        return dsl.insertInto(TB_SUPER_ADMIN_LOGIN_SETTING,
                        TB_SUPER_ADMIN_LOGIN_SETTING.SUPER_ADMIN_LOGIN_SETTING_SEQ, TB_SUPER_ADMIN_LOGIN_SETTING.LOGIN_TYPE, TB_SUPER_ADMIN_LOGIN_SETTING.IS_ENFORCED,
                        TB_SUPER_ADMIN_LOGIN_SETTING.REGISTER_USER_SEQ, TB_SUPER_ADMIN_LOGIN_SETTING.REGISTER_DATE)
                .values(SuperAdminLoginSetting.SINGLETON_SEQ, e.getLoginType(), e.getIsEnforced(),
                        JdbcAudit.userSeqOr(e.getRegisterUserSeq()), JdbcAudit.nowOr(e.getRegisterDate()))
                .onConflict(TB_SUPER_ADMIN_LOGIN_SETTING.SUPER_ADMIN_LOGIN_SETTING_SEQ)
                .doUpdate()
                .set(TB_SUPER_ADMIN_LOGIN_SETTING.LOGIN_TYPE, excluded(TB_SUPER_ADMIN_LOGIN_SETTING.LOGIN_TYPE))
                .set(TB_SUPER_ADMIN_LOGIN_SETTING.IS_ENFORCED, excluded(TB_SUPER_ADMIN_LOGIN_SETTING.IS_ENFORCED))
                .set(TB_SUPER_ADMIN_LOGIN_SETTING.UPDATE_USER_SEQ, excluded(TB_SUPER_ADMIN_LOGIN_SETTING.REGISTER_USER_SEQ))
                .set(TB_SUPER_ADMIN_LOGIN_SETTING.UPDATE_DATE, excluded(TB_SUPER_ADMIN_LOGIN_SETTING.REGISTER_DATE))
                .execute();
    }

    @Override
    public int upsertEnforce(SuperAdminLoginSetting e) {
        return dsl.insertInto(TB_SUPER_ADMIN_LOGIN_SETTING,
                        TB_SUPER_ADMIN_LOGIN_SETTING.SUPER_ADMIN_LOGIN_SETTING_SEQ, TB_SUPER_ADMIN_LOGIN_SETTING.IS_ENFORCED,
                        TB_SUPER_ADMIN_LOGIN_SETTING.REGISTER_USER_SEQ, TB_SUPER_ADMIN_LOGIN_SETTING.REGISTER_DATE)
                .values(SuperAdminLoginSetting.SINGLETON_SEQ, e.getIsEnforced(),
                        JdbcAudit.userSeqOr(e.getRegisterUserSeq()), JdbcAudit.nowOr(e.getRegisterDate()))
                .onConflict(TB_SUPER_ADMIN_LOGIN_SETTING.SUPER_ADMIN_LOGIN_SETTING_SEQ)
                .doUpdate()
                .set(TB_SUPER_ADMIN_LOGIN_SETTING.IS_ENFORCED, excluded(TB_SUPER_ADMIN_LOGIN_SETTING.IS_ENFORCED))
                .set(TB_SUPER_ADMIN_LOGIN_SETTING.UPDATE_USER_SEQ, excluded(TB_SUPER_ADMIN_LOGIN_SETTING.REGISTER_USER_SEQ))
                .set(TB_SUPER_ADMIN_LOGIN_SETTING.UPDATE_DATE, excluded(TB_SUPER_ADMIN_LOGIN_SETTING.REGISTER_DATE))
                .execute();
    }
}
