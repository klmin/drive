package com.thinkfree.drive.systemconfig.entity;

import com.thinkfree.drive.global.jpa.audit.BaseAuditEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * tb_system_config — 테넌트(또는 글로벌, tenant_seq NULL)별 설정 키·값. 쓰기는 전부 upsert(JdbcClient, 함수 유니크 {@code (COALESCE(tenant_seq, 0), setting_key)}),
 * 조회는 JPQL 생성자 식({@code DefaultSystemConfigQueryRepository} — SystemConfigValueData). 감사 4필드는 {@link BaseAuditEntity} — 값은 {@link #createSystemConfig} 가 채운 것을 upsert 가 그대로 바인딩한다.
 * 변경 메서드 없음.
 */
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "tb_system_config")
public class SystemConfig extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer configSeq;
    private Integer tenantSeq;
    private String settingKey;
    private String valueText;

    private SystemConfig(Integer tenantSeq, String settingKey, String valueText, Integer loginUserSeq) {
        Instant now = Instant.now();
        this.tenantSeq = tenantSeq;
        this.settingKey = settingKey;
        this.valueText = valueText;
        assignRegisterAudit(loginUserSeq, now);
        changeUpdateMetadata(loginUserSeq, now);
    }

    public static SystemConfig createSystemConfig(Integer tenantSeq, String settingKey, String valueText, Integer loginUserSeq) {
        return new SystemConfig(tenantSeq, settingKey, valueText, loginUserSeq);
    }
}
