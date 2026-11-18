package com.thinkfree.drive.systemconfig.repository;

import com.thinkfree.drive.global.jooq.JooqBatches;
import com.thinkfree.drive.systemconfig.entity.SystemConfig;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Query;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.thinkfree.drive.jooq.Tables.TB_SYSTEM_CONFIG;
import static org.jooq.impl.DSL.excluded;

/**
 * tb_system_config 쓰기 — upsert 는 jOOQ(구 SystemConfigMapper.xml 의 upsert statement), upsert 직전 판정 count 는 JPQL({@link SystemConfigJpaRepository}). 조회는 {@link DefaultSystemConfigQueryRepository}(JPQL 생성자 식).
 * <p>
 * 유니크 키는 함수 인덱스 {@code (COALESCE(tenant_seq, 0), setting_key)} 라 글로벌(tenant_seq NULL) 행도 (0, key) 로 잡힌다
 * → 테넌트·글로벌 모두 같은 {@code ON CONFLICT} 한 문장으로 upsert 된다(JPQL 로 표현 불가).
 * 감사 컬럼은 {@link SystemConfig#createSystemConfig} 가 엔티티에 채운 값을 그대로 바인딩한다(MyBatis 때도 인터셉터가 아닌 엔티티 값이 들어갔다).
 * 단 update_* 는 충돌(갱신) 때만 쓴다 — 새 행은 NULL(10-07, 다른 엔티티와 같은 "INSERT 때 update_* 비움" 규칙. DDL 도 NULL 허용으로 변경).
 * 배치는 {@link JooqBatches#batched} — 행마다 같은 upsert 를 실행하면 PreparedStatement 하나로 모아 {@code executeBatch}(1000행씩).
 */
@Repository
@RequiredArgsConstructor
public class DefaultSystemConfigRepository implements SystemConfigRepository {

    /**
     * 충돌 대상의 표현식 원소 — 인덱스 uk_tb_tenant_setting_key 의 {@code COALESCE(tenant_seq, 0)} 과 같아야 PG 가 추론한다.
     * 0 은 반드시 {@code inline}(바인드 {@code ?} 면 인덱스 표현식과 달라 "no unique or exclusion constraint matching" 오류).
     */
    private static final Field<Integer> TENANT_KEY = DSL.coalesce(TB_SYSTEM_CONFIG.TENANT_SEQ, DSL.inline(0));

    private final SystemConfigJpaRepository jpaRepository;
    private final DSLContext dsl;

    @Override
    public void upsert(SystemConfig systemConfig) {
        upsertQuery(dsl, systemConfig).execute();
    }

    @Override
    public void upsertBatch(List<SystemConfig> list) {
        if (list.isEmpty()) {
            return;
        }
        JooqBatches.batched(dsl, list, (batchedDsl, systemConfig) -> upsertQuery(batchedDsl, systemConfig).execute());
    }

    @Override
    public Integer countByTenantAndKeys(Integer tenantSeq, List<String> keys) {
        if (keys.isEmpty()) {
            return 0;
        }
        return Math.toIntExact(jpaRepository.countByTenantAndKeys(tenantSeq, keys));
    }

    @Override
    public void upsertGlobal(SystemConfig systemConfig) {
        upsert(systemConfig);
    }

    /** 새 행은 update_* NULL(등록 후 미수정), 충돌(갱신) 때만 엔티티의 update 값을 직접 바인딩한다. */
    private static Query upsertQuery(DSLContext dsl, SystemConfig c) {
        return dsl.insertInto(TB_SYSTEM_CONFIG,
                        TB_SYSTEM_CONFIG.TENANT_SEQ, TB_SYSTEM_CONFIG.SETTING_KEY, TB_SYSTEM_CONFIG.VALUE_TEXT,
                        TB_SYSTEM_CONFIG.REGISTER_DATE, TB_SYSTEM_CONFIG.REGISTER_USER_SEQ)
                .values(c.getTenantSeq(), c.getSettingKey(), c.getValueText(), c.getRegisterDate(), c.getRegisterUserSeq())
                .onConflict(TENANT_KEY, TB_SYSTEM_CONFIG.SETTING_KEY)
                .doUpdate()
                .set(TB_SYSTEM_CONFIG.VALUE_TEXT, excluded(TB_SYSTEM_CONFIG.VALUE_TEXT))
                .set(TB_SYSTEM_CONFIG.UPDATE_DATE, c.getUpdateDate())
                .set(TB_SYSTEM_CONFIG.UPDATE_USER_SEQ, c.getUpdateUserSeq());
    }

    /** 글로벌 행이 없을 때만 넣는다. 여러 WAS 가 동시에 넣어도 먼저 들어간 값이 남는다(새 행이라 update_* 는 NULL). */
    @Override
    public void insertGlobalIfAbsent(SystemConfig systemConfig) {
        dsl.insertInto(TB_SYSTEM_CONFIG,
                        TB_SYSTEM_CONFIG.TENANT_SEQ, TB_SYSTEM_CONFIG.SETTING_KEY, TB_SYSTEM_CONFIG.VALUE_TEXT,
                        TB_SYSTEM_CONFIG.REGISTER_DATE, TB_SYSTEM_CONFIG.REGISTER_USER_SEQ)
                .values(null, systemConfig.getSettingKey(), systemConfig.getValueText(), systemConfig.getRegisterDate(), systemConfig.getRegisterUserSeq())
                .onConflict(TENANT_KEY, TB_SYSTEM_CONFIG.SETTING_KEY)
                .doNothing()
                .execute();
    }

    @Override
    public int deleteByTenant(Integer tenantSeq) {
        return jpaRepository.deleteByTenantSeq(tenantSeq);
    }
}
