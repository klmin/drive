package com.thinkfree.drive.auditlog.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfree.drive.api.v1.auth.enums.LoginType;
import com.thinkfree.drive.auditlog.context.AuditLogContext;
import com.thinkfree.drive.auditlog.context.AuditLogContextHolder;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.common.AuditMockTest;
import com.thinkfree.drive.objectmapper.util.ObjectMapperUtil;
import com.thinkfree.drive.tenant.service.TenantFindService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * SettingAuditService가 각 이벤트에 대해 ThreadLocal 홀더에 채우는 context 내용을 검증한다.
 * 호출부(AdminLoginSettingUseCase 등)가 <b>어떤 인자로 이 서비스를 호출하는지</b>는 각 UseCaseAuditTest에서 다룬다.
 */
@DisplayName("DefaultSettingAuditService context 기록 내용 테스트")
class DefaultSettingAuditServiceTest extends AuditMockTest {

    @Mock private TenantFindService tenantFindService;
    // 델타 JSON 직렬화가 필요해 실제 구현 주입(mock이면 afterValue가 비어 검증 불가)
    @Spy private ObjectMapperUtil objectMapperUtil = new ObjectMapperUtil(new ObjectMapper());

    @InjectMocks
    private DefaultSettingAuditService settingAuditService;

    // EventName: SETTING_UPDATE (objectName=loginMethod)
    @Test
    @DisplayName("recordLoginMethodChangeIfNeeded - 값이 바뀌면 SETTING_UPDATE(objectName=loginMethod) 1건 기록")
    void recordLoginMethodChangeIfNeeded_changed_writesContext() {
        settingAuditService.recordLoginMethodChangeIfNeeded(LoginType.OIDC, LoginType.LOCAL);

        var c = AuditLogContextHolder.getAll();
        assertThat(c).hasSize(1);
        AuditLogContext ctx = c.get(0);
        assertThat(ctx.getEventName()).isEqualTo(EventName.SETTING_UPDATE);
        assertThat(ctx.getObjectName()).isEqualTo("loginMethod");
        assertThat(ctx.getBeforeValue()).isEqualTo("OIDC");
        assertThat(ctx.getAfterValue()).isEqualTo("LOCAL");
    }

    @Test
    @DisplayName("recordLoginMethodChangeIfNeeded - 값이 그대로면 기록하지 않는다")
    void recordLoginMethodChangeIfNeeded_unchanged_recordsNothing() {
        settingAuditService.recordLoginMethodChangeIfNeeded(LoginType.LOCAL, LoginType.LOCAL);

        assertThat(AuditLogContextHolder.getAll()).isEmpty();
    }

    // EventName: SETTING_UPDATE (objectName=externalShare)
    @Test
    @DisplayName("recordExternalShareChangeIfNeeded - 값이 바뀌면 SETTING_UPDATE(objectName=externalShare, OFF→ON) 1건 기록")
    void recordExternalShareChangeIfNeeded_changed_writesContext() {
        settingAuditService.recordExternalShareChangeIfNeeded(false, true);

        var c = AuditLogContextHolder.getAll();
        assertThat(c).hasSize(1);
        AuditLogContext ctx = c.get(0);
        assertThat(ctx.getEventName()).isEqualTo(EventName.SETTING_UPDATE);
        assertThat(ctx.getObjectName()).isEqualTo("externalShare");
        assertThat(ctx.getBeforeValue()).isEqualTo("OFF");
        assertThat(ctx.getAfterValue()).isEqualTo("ON");
    }

    @Test
    @DisplayName("recordExternalShareChangeIfNeeded - 값이 그대로면 기록하지 않는다")
    void recordExternalShareChangeIfNeeded_unchanged_recordsNothing() {
        settingAuditService.recordExternalShareChangeIfNeeded(true, true);

        assertThat(AuditLogContextHolder.getAll()).isEmpty();
    }

    // EventName: SETTING_UPDATE (objectName=globalLoginPolicy) - 테넌트별 fan-out
    @Test
    @DisplayName("recordGlobalLoginPolicyChangeIfNeeded - 강제 여부가 바뀌면 영향 테넌트마다 SETTING_UPDATE(globalLoginPolicy)를 fan-out하고 tenantSeq를 row별로 채운다")
    void recordGlobalLoginPolicyChangeIfNeeded_changed_fanOutPerTenant() {
        given(tenantFindService.findSelfManagedTenantSeqs()).willReturn(List.of(1, 2, 3));

        settingAuditService.recordGlobalLoginPolicyChangeIfNeeded(true, false);

        var contexts = AuditLogContextHolder.getAll();
        assertThat(contexts).hasSize(3);
        assertThat(contexts).allSatisfy(c -> {
            assertThat(c.getEventName()).isEqualTo(EventName.SETTING_UPDATE);
            assertThat(c.getObjectName()).isEqualTo("globalLoginPolicy");
            assertThat(c.getBeforeValue()).isEqualTo("GLOBAL");
            assertThat(c.getAfterValue()).isEqualTo("TENANT");
        });
        assertThat(contexts).extracting(AuditLogContext::getTenantSeq)
                .containsExactlyInAnyOrder(1, 2, 3);
    }

    @Test
    @DisplayName("recordGlobalLoginPolicyChangeIfNeeded - 강제 여부가 그대로면 기록하지 않는다")
    void recordGlobalLoginPolicyChangeIfNeeded_unchanged_recordsNothing() {
        settingAuditService.recordGlobalLoginPolicyChangeIfNeeded(false, false);

        assertThat(AuditLogContextHolder.getAll()).isEmpty();
    }

    // EventName: SETTING_UPDATE (objectName=globalSharePolicy) - 테넌트별 fan-out
    @Test
    @DisplayName("recordGlobalSharePolicyChangeIfNeeded - 강제 여부가 바뀌면 영향 테넌트마다 SETTING_UPDATE(globalSharePolicy)를 fan-out하고 tenantSeq를 row별로 채운다")
    void recordGlobalSharePolicyChangeIfNeeded_changed_fanOutPerTenant() {
        given(tenantFindService.findSelfManagedTenantSeqs()).willReturn(List.of(1, 2, 3));

        settingAuditService.recordGlobalSharePolicyChangeIfNeeded(true, false);

        var contexts = AuditLogContextHolder.getAll();
        assertThat(contexts).hasSize(3);
        assertThat(contexts).allSatisfy(c -> {
            assertThat(c.getEventName()).isEqualTo(EventName.SETTING_UPDATE);
            assertThat(c.getObjectName()).isEqualTo("globalSharePolicy");
            assertThat(c.getBeforeValue()).isEqualTo("GLOBAL");
            assertThat(c.getAfterValue()).isEqualTo("TENANT");
        });
        assertThat(contexts).extracting(AuditLogContext::getTenantSeq)
                .containsExactlyInAnyOrder(1, 2, 3);
    }

    @Test
    @DisplayName("recordGlobalSharePolicyChangeIfNeeded - 강제 여부가 그대로면 기록하지 않는다")
    void recordGlobalSharePolicyChangeIfNeeded_unchanged_recordsNothing() {
        settingAuditService.recordGlobalSharePolicyChangeIfNeeded(false, false);

        assertThat(AuditLogContextHolder.getAll()).isEmpty();
    }

    // EventName: SETTING_ALLOWED_IP_CHANGE (델타 방식, afterValue=added/removed)
    @Test
    @DisplayName("recordAllowedIpChangeIfNeeded - IP 목록이 바뀌면 SETTING_ALLOWED_IP_CHANGE(afterValue에 added/removed 델타)")
    void recordAllowedIpChangeIfNeeded_changed_writesContext() {
        settingAuditService.recordAllowedIpChangeIfNeeded(List.of("1.1.1.1/32"), List.of("2.2.2.2/32"));

        var c = AuditLogContextHolder.getAll();
        assertThat(c).hasSize(1);
        AuditLogContext ctx = c.get(0);
        assertThat(ctx.getEventName()).isEqualTo(EventName.SETTING_ALLOWED_IP_CHANGE);
        assertThat(ctx.getBeforeValue()).isNull();               // 델타 방식이라 before 없음
        assertThat(ctx.getAfterValue()).contains("2.2.2.2/32");  // added
        assertThat(ctx.getAfterValue()).contains("1.1.1.1/32");  // removed
    }

    @Test
    @DisplayName("recordAllowedIpChangeIfNeeded - IP 목록이 그대로면 기록하지 않는다")
    void recordAllowedIpChangeIfNeeded_unchanged_recordsNothing() {
        settingAuditService.recordAllowedIpChangeIfNeeded(List.of("1.1.1.1/32"), List.of("1.1.1.1/32"));

        assertThat(AuditLogContextHolder.getAll()).isEmpty();
    }
}
