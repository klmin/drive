package com.thinkfree.drive.share.setting.repository;

import com.thinkfree.drive.global.jdbc.JdbcAudit;
import com.thinkfree.drive.jooq.tables.records.TbShareSettingRecord;
import com.thinkfree.drive.share.setting.entity.ShareSetting;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.TableField;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

import static com.thinkfree.drive.jooq.Tables.TB_SHARE_SETTING;
import static org.jooq.impl.DSL.excluded;

/**
 * tb_share_setting 쓰기 — upsert 5종은 jOOQ(ON CONFLICT 는 JPQL 로 표현 불가), 단건 insert 는 JPA(구 ShareSettingMapper.xml 의 insert·upsert 5종). 읽기는 {@link DefaultShareSettingQueryRepository}.
 * <p>
 * 표현식 유니크 {@code tb_share_setting_uk_scope ((COALESCE(tenant_seq, 0)))} 가 글로벌(tenant_seq NULL → 0) 1행·테넌트별 1행을 보장하므로
 * 모든 upsert 는 {@code ON CONFLICT ((COALESCE(tenant_seq, 0)))} 한 문장이다. 갱신 컬럼만 다르다:
 * <ul>
 *   <li>upsertGlobal — 5 플래그 전부</li>
 *   <li>upsertGlobalEnforce — is_enforced 만(상세 플래그 보존)</li>
 *   <li>upsertGlobalShareOff — is_enforced + is_external_share_allowed</li>
 *   <li>upsertTenant — is_enforced 제외 4 플래그(테넌트 행은 CHECK 로 항상 'N')</li>
 *   <li>upsertTenantShareOff — is_external_share_allowed 만</li>
 * </ul>
 * 감사 컬럼: 등록 감사는 {@link JdbcAudit}(시큐리티 사용자 우선(없으면 선언 값), 시각은 선언 값 없으면 현재 시각 초 절삭). 신규 행은 update_* 를 비워 두고(NULL),
 * 충돌(실제 갱신) 때만 update_* 에 같은 감사 값을 넣는다(JPA Auditing {@code modifyOnCreate = false} 와 같은 규칙).
 * <p>
 * {@code insert}(테넌트 생성 시 기본 행)는 upsert 가 아니라 JPA {@code save} — IDENTITY 키라 저장 순간 INSERT(제약 위반도 그 자리에서), 등록 감사는 Spring Data Auditing.
 * upsert 가 JPA 가 아닌 이유 = 로드 → 변경 감지로 바꾸면 "없으면 INSERT" 를 원자적으로 못 함.
 */
@Repository
@RequiredArgsConstructor
public class DefaultShareSettingRepository implements ShareSettingRepository {

    /**
     * 충돌 대상 = 표현식 인덱스 tb_share_setting_uk_scope 의 {@code COALESCE(tenant_seq, 0)}(PG 는 인덱스 표현식과 같아야 추론한다).
     * 0 은 반드시 {@code inline}(바인드 {@code ?} 면 인덱스 표현식과 달라 "no unique or exclusion constraint matching" 오류).
     */
    private static final Field<Integer> SCOPE_KEY = DSL.coalesce(TB_SHARE_SETTING.TENANT_SEQ, DSL.inline(0));

    private static final List<TableField<TbShareSettingRecord, Boolean>> GLOBAL = List.of(
            TB_SHARE_SETTING.IS_ENFORCED, TB_SHARE_SETTING.IS_EXTERNAL_SHARE_ALLOWED, TB_SHARE_SETTING.IS_SPECIFIC_USERS_ALLOWED,
            TB_SHARE_SETTING.IS_ORG_LINK_ALLOWED, TB_SHARE_SETTING.IS_PUBLIC_ALLOWED);
    private static final List<TableField<TbShareSettingRecord, Boolean>> GLOBAL_ENFORCE = List.of(TB_SHARE_SETTING.IS_ENFORCED);
    private static final List<TableField<TbShareSettingRecord, Boolean>> GLOBAL_SHARE_OFF = List.of(
            TB_SHARE_SETTING.IS_ENFORCED, TB_SHARE_SETTING.IS_EXTERNAL_SHARE_ALLOWED);
    private static final List<TableField<TbShareSettingRecord, Boolean>> TENANT = List.of(
            TB_SHARE_SETTING.IS_EXTERNAL_SHARE_ALLOWED, TB_SHARE_SETTING.IS_SPECIFIC_USERS_ALLOWED,
            TB_SHARE_SETTING.IS_ORG_LINK_ALLOWED, TB_SHARE_SETTING.IS_PUBLIC_ALLOWED);
    private static final List<TableField<TbShareSettingRecord, Boolean>> TENANT_SHARE_OFF = List.of(TB_SHARE_SETTING.IS_EXTERNAL_SHARE_ALLOWED);

    private final DSLContext dsl;
    private final ShareSettingJpaRepository jpaRepository;

    @Override
    public void upsertGlobal(ShareSetting entity) {
        write(GLOBAL, entity);
    }

    @Override
    public void upsertGlobalEnforce(ShareSetting entity) {
        write(GLOBAL_ENFORCE, entity);
    }

    @Override
    public void upsertGlobalShareOff(ShareSetting entity) {
        write(GLOBAL_SHARE_OFF, entity);
    }

    @Override
    public void upsertTenant(ShareSetting entity) {
        write(TENANT, entity);
    }

    @Override
    public void upsertTenantShareOff(ShareSetting entity) {
        write(TENANT_SHARE_OFF, entity);
    }

    @Override
    public void insert(ShareSetting entity) {
        jpaRepository.save(entity);
    }

    /** upsert 5종 공통 — INSERT 는 5 플래그 전부(update_* 는 넣지 않음), 충돌 때는 {@code updatedFlags} + 수정 감사(등록 감사와 같은 값) */
    private void write(List<TableField<TbShareSettingRecord, Boolean>> updatedFlags, ShareSetting e) {
        Integer registerUserSeq = JdbcAudit.userSeqOr(e.getRegisterUserSeq());
        Instant registerDate = JdbcAudit.nowOr(e.getRegisterDate());
        var upsert = dsl.insertInto(TB_SHARE_SETTING,
                        TB_SHARE_SETTING.TENANT_SEQ, TB_SHARE_SETTING.IS_ENFORCED, TB_SHARE_SETTING.IS_EXTERNAL_SHARE_ALLOWED,
                        TB_SHARE_SETTING.IS_SPECIFIC_USERS_ALLOWED, TB_SHARE_SETTING.IS_ORG_LINK_ALLOWED, TB_SHARE_SETTING.IS_PUBLIC_ALLOWED,
                        TB_SHARE_SETTING.REGISTER_USER_SEQ, TB_SHARE_SETTING.REGISTER_DATE)
                .values(e.getTenantSeq(), e.getIsEnforced(), e.getIsExternalShareAllowed(),
                        e.getIsSpecificUsersAllowed(), e.getIsOrgLinkAllowed(), e.getIsPublicAllowed(),
                        registerUserSeq, registerDate)
                .onConflict(SCOPE_KEY)
                .doUpdate()
                .set(TB_SHARE_SETTING.UPDATE_USER_SEQ, registerUserSeq)
                .set(TB_SHARE_SETTING.UPDATE_DATE, registerDate);
        for (TableField<TbShareSettingRecord, Boolean> flag : updatedFlags) {
            upsert = upsert.set(flag, excluded(flag));
        }
        upsert.execute();
    }

    @Override
    public int deleteByTenant(Integer tenantSeq) {
        return jpaRepository.deleteByTenantSeq(tenantSeq);
    }
}
