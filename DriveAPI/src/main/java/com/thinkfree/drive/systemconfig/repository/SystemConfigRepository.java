package com.thinkfree.drive.systemconfig.repository;

import com.thinkfree.drive.systemconfig.entity.SystemConfig;

import java.util.List;

/**
 * tb_system_config 쓰기 — {@code SystemConfigService} 가 주입. 읽기는 {@link SystemConfigQueryRepository}.
 */
public interface SystemConfigRepository {

    void upsert(SystemConfig systemConfig);

    void upsertBatch(List<SystemConfig> list);

    /** upsert 직전 판정 — 테넌트에 이미 있는 키 수(0 이면 이번 upsert 가 신규 등록) */
    Integer countByTenantAndKeys(Integer tenantSeq, List<String> keys);

    /**
     * 글로벌(tenant_seq IS NULL) 설정 upsert.
     * 유니크 키가 (COALESCE(tenant_seq,0), setting_key) 함수 유니크라 글로벌 NULL 도 (0, key) 로 잡힌다
     * → 단일 INSERT ... ON CONFLICT DO UPDATE(`upsert`) 한 방으로 충분(update→insert 폴백 불필요).
     */
    void upsertGlobal(SystemConfig systemConfig);

    /** 글로벌 설정이 없을 때만 넣는다. 이미 있으면 그대로 둔다. */
    void insertGlobalIfAbsent(SystemConfig systemConfig);
    /** 테넌트 삭제 — 이 테넌트의 행 전부. 지운 행 수 */
    int deleteByTenant(Integer tenantSeq);
}
