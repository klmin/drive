package com.thinkfree.drive.systemconfig.repository;

import org.springframework.data.jpa.repository.Modifying;
import com.thinkfree.drive.systemconfig.data.SystemConfigValueData;
import com.thinkfree.drive.systemconfig.entity.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * tb_system_config Spring Data JPA 선언 인터페이스 — **패키지 전용**. {@link DefaultSystemConfigQueryRepository}(조회 2종 — JPQL 생성자 식)·{@link DefaultSystemConfigRepository}(upsert 직전 count) 만 주입받아 위임한다.
 * upsert 는 JdbcClient(Default 리포).
 */
interface SystemConfigJpaRepository extends JpaRepository<SystemConfig, Integer> {

    String SELECT_VALUE = "select new com.thinkfree.drive.systemconfig.data.SystemConfigValueData(c.settingKey, c.valueText) from SystemConfig c ";

    @Transactional(readOnly = true)
    @Query("select count(c) from SystemConfig c where c.tenantSeq = :tenantSeq and c.settingKey in :keys")
    long countByTenantAndKeys(@Param("tenantSeq") Integer tenantSeq, @Param("keys") Collection<String> keys);

    @Transactional(readOnly = true)
    @Query(SELECT_VALUE + "where c.tenantSeq = :tenantSeq and c.settingKey in :keys")
    List<SystemConfigValueData> findValuesByTenantAndKeys(@Param("tenantSeq") Integer tenantSeq, @Param("keys") Collection<String> keys);

    @Transactional(readOnly = true)
    @Query(SELECT_VALUE + "where c.tenantSeq is null and c.settingKey = :settingKey")
    Optional<SystemConfigValueData> findGlobalValueBySettingKey(@Param("settingKey") String settingKey);

    /** 테넌트 삭제 — 이 테넌트의 행 전부(조건부 다건이라 JPQL @Modifying) */
    @Transactional
    @Modifying
    @Query("delete from SystemConfig s where s.tenantSeq = :tenantSeq")
    int deleteByTenantSeq(@Param("tenantSeq") Integer tenantSeq);
}
