package com.thinkfree.drive.systemconfig.repository;

import com.thinkfree.drive.systemconfig.data.SystemConfigValueData;
import com.thinkfree.drive.systemconfig.enums.SystemConfigKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * tb_system_config 읽기 — JPQL 생성자 식({@link SystemConfigJpaRepository})으로 {@link SystemConfigValueData} 를 바로 받는다.
 * 엔티티를 영속 컨텍스트에 올리지 않으므로 같은 트랜잭션의 upsert(JdbcClient) 뒤에도 항상 DB 현재 값(detach 불필요). 쓰기는 {@link DefaultSystemConfigRepository}.
 */
@Repository
@RequiredArgsConstructor
public class DefaultSystemConfigQueryRepository implements SystemConfigQueryRepository {

    private final SystemConfigJpaRepository jpaRepository;

    @Override
    public List<SystemConfigValueData> findByTenantAndSettingConfigKeys(Integer tenantSeq, List<SystemConfigKey> keys) {
        if (keys.isEmpty()) {
            return List.of();
        }
        List<String> dbKeys = keys.stream().map(SystemConfigKey::getDbKey).toList();
        return jpaRepository.findValuesByTenantAndKeys(tenantSeq, dbKeys);
    }

    @Override
    public SystemConfigValueData findGlobalBySettingKey(String settingKey) {
        return jpaRepository.findGlobalValueBySettingKey(settingKey).orElse(null);
    }
}
