package com.thinkfree.drive.systemconfig.repository;

import com.thinkfree.drive.systemconfig.data.SystemConfigValueData;
import com.thinkfree.drive.systemconfig.enums.SystemConfigKey;

import java.util.List;

/**
 * tb_system_config 읽기 — {@code SystemConfigFindService} 가 주입. 쓰기(upsert)와 upsert 직전 판정(count)은 {@link SystemConfigRepository}.
 */
public interface SystemConfigQueryRepository {

    /** 테넌트의 설정 중 주어진 키들 — 없는 키는 결과에 없음, 빈 목록이면 빈 결과 */
    List<SystemConfigValueData> findByTenantAndSettingConfigKeys(Integer tenantSeq, List<SystemConfigKey> keys);

    /** 글로벌(tenant_seq IS NULL) 설정 한 키, 없으면 null */
    SystemConfigValueData findGlobalBySettingKey(String settingKey);
}
