package com.thinkfree.drive.systemconfig.service;

import com.thinkfree.drive.api.v1.admin.systemconfig.request.*;
import com.thinkfree.drive.systemconfig.converter.SystemConfigConverter;
import com.thinkfree.drive.systemconfig.entity.SystemConfig;
import com.thinkfree.drive.systemconfig.enums.SystemConfigKey;
import com.thinkfree.drive.systemconfig.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigService {
    private final SystemConfigRepository systemConfigRepository;
    private final SystemConfigConverter systemConfigConverter;
    private final SystemConfigFindService settings;

    /** Only the Super Admin controller exposes this operation. No tenant type is changed. */
    @Transactional
    public void upsertManagedOffice(Integer tenantSeq, Integer loginUserSeq, OfficeUpdateRequest request) {
        upsertOffice(tenantSeq, loginUserSeq, request);
        systemConfigRepository.upsert(SystemConfig.createSystemConfig(tenantSeq,
                SystemConfigKey.OFFICE_SERVICE_MANAGED.getDbKey(), "true", loginUserSeq));
    }

    @Transactional
    public boolean upsertOffice(Integer tenantSeq, Integer loginUserSeq, OfficeUpdateRequest request) {
        log.info("{SystemConfigService} : 오피스 설정 업서트");

        var connection = SystemConfig.createSystemConfig(tenantSeq, SystemConfigKey.OFFICE_CONNECTION_TYPE.dbKey,
                request.officeConnectionType(), loginUserSeq);
        // Disabling new Office launches must not invalidate callbacks from documents already open.
        // Keep the existing endpoint and credentials until the administrator changes the connection.
        List<SystemConfig> list;
        if ("none".equals(request.officeConnectionType())) {
            list = List.of(connection);
        } else {
            var domain = SystemConfig.createSystemConfig(tenantSeq, SystemConfigKey.OFFICE_DOMAIN.dbKey,
                    request.officeDomain(), loginUserSeq);
            var token = SystemConfig.createSystemConfig(tenantSeq, SystemConfigKey.OFFICE_TICKET_SERVICE_TOKEN.dbKey,
                    "ticket".equals(request.officeAuthMode()) ? request.officeTicketServiceToken() : "", loginUserSeq);
            var signingKey = SystemConfig.createSystemConfig(tenantSeq, SystemConfigKey.OFFICE_HTTP_STORAGE_REQUEST_JWT_SECRET.dbKey,
                    request.officeHttpStorageRequestJwtSecret(), loginUserSeq);
            var mode = SystemConfig.createSystemConfig(tenantSeq, SystemConfigKey.OFFICE_AUTH_MODE.dbKey,
                    request.officeAuthMode(), loginUserSeq);
            var current = settings.findOfficeConfig(tenantSeq);
            // Omitted names preserve existing settings; old clients must not reset a managed name.
            String adapter = request.officeHttpStorageAdapterName();
            if (adapter == null || adapter.isBlank()) adapter = current.getOfficeHttpStorageAdapterName();
            if (adapter == null || adapter.isBlank()) adapter = String.valueOf(tenantSeq);
            list = List.of(connection, domain, mode, token, signingKey,
                    SystemConfig.createSystemConfig(tenantSeq, SystemConfigKey.OFFICE_HTTP_STORAGE_ADAPTER_NAME.dbKey, adapter, loginUserSeq));

        }

        List<String> keys = list.stream()
                .map(SystemConfig::getSettingKey)
                .toList();

        int existing = systemConfigRepository.countByTenantAndKeys(tenantSeq, keys);   // upsert 직전 판정(쓰기 리포)
        boolean created = (existing == 0);

        systemConfigRepository.upsertBatch(list);

        return created;
    }

    @Transactional
    public void upsertIntellect(Integer tenantSeq, Integer loginUserSeq, IntellectUpdateRequest request) {
        var systemConfig = systemConfigConverter.toUpsertIntellectStatusSystemConfig(request, tenantSeq, loginUserSeq);
        systemConfigRepository.upsert(systemConfig);
    }

    @Transactional
    public void upsertFileSizeLimit(Integer tenantSeq, Integer loginUserSeq, FileSizeLimitUpdateRequest request) {
        var systemConfig = systemConfigConverter.toUpsertFileSizeLimitSystemConfig(request, tenantSeq, loginUserSeq);
        systemConfigRepository.upsert(systemConfig);
    }

    @Transactional
    public void upsertMfaStatus(Integer tenantSeq, Integer loginUserSeq, MfaStatusUpdateRequest request) {
        var systemConfig = systemConfigConverter.toUpsertMfaStatusSystemConfig(request, tenantSeq, loginUserSeq);
        systemConfigRepository.upsert(systemConfig);
    }

    @Transactional
    public void upsertAccessRestrictionStatus(Integer tenantSeq, Integer loginUserSeq, AccessRestrictionUpdateRequest request) {
        var systemConfig = systemConfigConverter.toUpsertAccessRestrictionStatusSystemConfig(request, tenantSeq, loginUserSeq);
        systemConfigRepository.upsert(systemConfig);
    }

    /** 테넌트 공유 기능 사용(share_status) 설정 upsert. */
    @Transactional
    public void upsertShareStatus(Integer tenantSeq, boolean enabled, Integer loginUserSeq) {
        var systemConfig = SystemConfig.createSystemConfig(
                tenantSeq, SystemConfigKey.SHARE_STATUS.getDbKey(), String.valueOf(enabled), loginUserSeq);
        systemConfigRepository.upsert(systemConfig);
    }

    /** 테넌트 총 용량(tenant_total_quota_byte, 양수 byte) upsert. 검증은 호출부(SuperAdminTenantUseCase). */
    @Transactional
    public void upsertTenantTotalQuotaByte(Integer tenantSeq, long totalQuotaByte, Integer loginUserSeq) {
        var systemConfig = SystemConfig.createSystemConfig(
                tenantSeq, SystemConfigKey.TENANT_TOTAL_QUOTA_BYTE.getDbKey(), String.valueOf(totalQuotaByte), loginUserSeq);
        systemConfigRepository.upsert(systemConfig);
    }

    /** 슈퍼어드민(글로벌, tenant_seq IS NULL) 공유 기능 사용 설정 upsert. */
    @Transactional
    public void upsertGlobalShareStatus(boolean enabled, Integer loginUserSeq) {
        var systemConfig = SystemConfig.createSystemConfig(
                null, SystemConfigKey.SHARE_STATUS.getDbKey(), String.valueOf(enabled), loginUserSeq);
        systemConfigRepository.upsertGlobal(systemConfig);
    }

    /**
     * 테넌트 생성 시점에 호출되어 해당 테넌트의 기본 시스템 설정을 일괄 삽입한다.
     * Why: 기존 어드민 화면들은 키 별로 upsert 하지만, 테넌트 생성 단계에서는
     *      하위 화면 진입 전 기본값이 보장되어야 하므로 일괄 초기화한다.
     * 테넌트 총 용량(tenant_total_quota_byte, 양수 byte)은 등록 요청 값으로 함께 삽입 — 검증은 호출부(SuperAdminTenantUseCase).
     */
    @Transactional
    public void initializeDefaultsForTenant(Integer tenantSeq, long totalQuotaByte, Integer loginUserSeq) {
        List<SystemConfig> defaults = List.of(
                SystemConfig.createSystemConfig(tenantSeq, SystemConfigKey.OFFICE_DOMAIN.dbKey, "", loginUserSeq),
                SystemConfig.createSystemConfig(tenantSeq, SystemConfigKey.INTELLECT_STATUS.dbKey, "true", loginUserSeq),
                SystemConfig.createSystemConfig(tenantSeq, SystemConfigKey.MFA_STATUS.dbKey, "false", loginUserSeq),
                SystemConfig.createSystemConfig(tenantSeq, SystemConfigKey.SHARE_STATUS.dbKey, "true", loginUserSeq),
                SystemConfig.createSystemConfig(tenantSeq, SystemConfigKey.TENANT_TOTAL_QUOTA_BYTE.dbKey, String.valueOf(totalQuotaByte), loginUserSeq)
        );
        defaults.forEach(systemConfigRepository::upsert);
    }

    /** 테넌트 삭제 — 이 테넌트의 행 전부 */
    public void deleteByTenant(Integer tenantSeq) {
        systemConfigRepository.deleteByTenant(tenantSeq);
    }
}
