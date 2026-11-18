package com.thinkfree.drive.systemconfig.service;

import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.systemconfig.data.*;
import com.thinkfree.drive.systemconfig.data.SystemConfigValueData;
import com.thinkfree.drive.systemconfig.enums.SystemConfigKey;
import com.thinkfree.drive.systemconfig.exception.SystemConfigErrorCode;
import com.thinkfree.drive.systemconfig.repository.SystemConfigQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SystemConfigFindService {

    private final SystemConfigQueryRepository systemConfigQueryRepository;

    private Map<SystemConfigKey, SystemConfigValueData> findConfigMap(Integer tenantSeq, List<SystemConfigKey> keys){
        var values = systemConfigQueryRepository.findByTenantAndSettingConfigKeys(tenantSeq, keys);
        return values.stream().collect(Collectors.toMap(s -> SystemConfigKey.fromDbKey(s.getSettingKey()), Function.identity()));
    }

    private <T> T loadConfig(Integer tenantSeq, List<SystemConfigKey> keys, SystemConfigErrorCode errorCode, Function<Map<SystemConfigKey, SystemConfigValueData>, T> mapper){

        var systemConfigMap = findConfigMap(tenantSeq, keys);

        boolean missing = keys.stream().anyMatch(k -> !systemConfigMap.containsKey(k));

        if (missing) {
            throw ApiRuntimeException.type(errorCode);
        }

        return mapper.apply(systemConfigMap);
    }

    public boolean isOfficeServiceManaged(Integer tenantSeq) {
        var values = findConfigMap(tenantSeq, List.of(SystemConfigKey.OFFICE_SERVICE_MANAGED));
        return Boolean.parseBoolean(officeValue(values, SystemConfigKey.OFFICE_SERVICE_MANAGED));
    }

    public OfficeData findOfficeConfig(Integer tenantSeq) {
        var values = findConfigMap(tenantSeq, List.of(SystemConfigKey.OFFICE_DOMAIN, SystemConfigKey.OFFICE_AUTH_MODE, SystemConfigKey.OFFICE_CONNECTION_TYPE,
                SystemConfigKey.OFFICE_TICKET_SERVICE_TOKEN,
                SystemConfigKey.OFFICE_HTTP_STORAGE_REQUEST_JWT_SECRET, SystemConfigKey.OFFICE_HTTP_STORAGE_ADAPTER_NAME));
        return OfficeData.builder()
                .officeConnectionType(officeValue(values, SystemConfigKey.OFFICE_CONNECTION_TYPE).isBlank()
                    ? (officeValue(values, SystemConfigKey.OFFICE_DOMAIN).isBlank() ? "none" : "onPremise")
                    : officeValue(values, SystemConfigKey.OFFICE_CONNECTION_TYPE))
                .officeDomain(officeValue(values, SystemConfigKey.OFFICE_DOMAIN))
                .officeAuthMode(officeValue(values, SystemConfigKey.OFFICE_AUTH_MODE).isBlank() ? "ticket" : officeValue(values, SystemConfigKey.OFFICE_AUTH_MODE))
                .officeTicketServiceToken(officeValue(values, SystemConfigKey.OFFICE_TICKET_SERVICE_TOKEN))
                .officeHttpStorageAdapterName(officeValue(values, SystemConfigKey.OFFICE_HTTP_STORAGE_ADAPTER_NAME).isBlank()
                    ? String.valueOf(tenantSeq) : officeValue(values, SystemConfigKey.OFFICE_HTTP_STORAGE_ADAPTER_NAME))
                .officeHttpStorageRequestJwtSecret(officeValue(values, SystemConfigKey.OFFICE_HTTP_STORAGE_REQUEST_JWT_SECRET))
                .build();
    }

    private static String officeValue(Map<SystemConfigKey, SystemConfigValueData> values, SystemConfigKey key) {
        var value = values.get(key);
        return value == null ? "" : value.getValueText();
    }

    public IntellectData findIntellectConfig(Integer tenantSeq) {
        var intellectStatus = SystemConfigKey.INTELLECT_STATUS;
        Map<SystemConfigKey, SystemConfigValueData> configMap = findConfigMap(tenantSeq, List.of(intellectStatus));
        SystemConfigValueData config = configMap.get(intellectStatus);
        boolean enabled = config != null && Boolean.parseBoolean(config.getValueText());
        return IntellectData.builder().enabled(enabled).build();
    }

    public MfaStatusData findMfaStatusConfig(Integer tenantSeq) {
        var mfaStatus = SystemConfigKey.MFA_STATUS;
        Map<SystemConfigKey, SystemConfigValueData> configMap = findConfigMap(tenantSeq, List.of(mfaStatus));
        SystemConfigValueData config = configMap.get(mfaStatus);
        boolean enabled = config != null && Boolean.parseBoolean(config.getValueText());
        return MfaStatusData.builder().enabled(enabled).build();
    }

    public FileSizeLimitData findFileSizeLimitConfig(Integer tenantSeq) {
        var maxFileUploadSize = SystemConfigKey.MAX_FILE_UPLOAD_SIZE;
        Map<SystemConfigKey, SystemConfigValueData> configMap = findConfigMap(tenantSeq, List.of(maxFileUploadSize));
        return FileSizeLimitData.builder()
                               .maxFileUploadSize(parseLongOrNull(configMap.get(maxFileUploadSize)))
                               .build();
    }

    public void validateFileSizeLimit(Integer tenantSeq, long fileSizeBytes) {
        Long maxSize = findFileSizeLimitConfig(tenantSeq).getMaxFileUploadSize();
        if (maxSize == null || maxSize == DriveConstants.SIZE_LIMIT_UNLIMITED) {
            return;
        }
        if (fileSizeBytes > maxSize) {
            throw ApiRuntimeException.type(SystemConfigErrorCode.FILE_SIZE_LIMIT_EXCEEDED);
        }
    }

    /** 테넌트 총 용량(byte), 키가 없으면 null — 슈퍼어드민 조회·수정(키 없는 테넌트를 설정해 복구하는 경로)용. */
    public Long findTenantTotalQuotaByte(Integer tenantSeq) {
        var tenantTotalQuotaByte = SystemConfigKey.TENANT_TOTAL_QUOTA_BYTE;
        SystemConfigValueData config = findConfigMap(tenantSeq, List.of(tenantTotalQuotaByte)).get(tenantTotalQuotaByte);
        return config == null ? null : Long.parseLong(config.getValueText());
    }

    /** 테넌트 총 용량(byte), 키가 없으면 TENANT_TOTAL_QUOTA_NOT_FOUND — 판정에 쓰는 값이라 임의 기본값으로 대신하지 않는다. */
    public long findTenantTotalQuotaByteOrThrow(Integer tenantSeq) {
        Long totalQuotaByte = findTenantTotalQuotaByte(tenantSeq);
        if (totalQuotaByte == null) {
            throw ApiRuntimeException.type(SystemConfigErrorCode.TENANT_TOTAL_QUOTA_NOT_FOUND);
        }
        return totalQuotaByte;
    }

    private Long parseLongOrNull(SystemConfigValueData config) {
        if (config == null){
            return null;
        }
        long value = Long.parseLong(config.getValueText());
        return value == DriveConstants.SIZE_LIMIT_UNLIMITED ? null : value;
    }

    public AccessRestrictionData findAccessRestrictionStatusConfig(Integer tenantSeq) {
        var accessRestrictionStatus = SystemConfigKey.ACCESS_RESTRICTION_STATUS;
        Map<SystemConfigKey, SystemConfigValueData> configMap = findConfigMap(tenantSeq, List.of(accessRestrictionStatus));
        SystemConfigValueData config = configMap.get(accessRestrictionStatus);
        boolean enabled = config != null && Boolean.parseBoolean(config.getValueText());
        return AccessRestrictionData.builder().enabled(enabled).build();
    }
    
    public ShareStatusData findShareStatusConfig(Integer tenantSeq) {
        var shareStatus = SystemConfigKey.SHARE_STATUS;
        Map<SystemConfigKey, SystemConfigValueData> configMap = findConfigMap(tenantSeq, List.of(shareStatus));
        SystemConfigValueData config = configMap.get(shareStatus);
        boolean enabled = config != null && Boolean.parseBoolean(config.getValueText());
        return ShareStatusData.builder().enabled(enabled).build();
    }

    /** 슈퍼어드민(글로벌, tenant_seq IS NULL) 공유 기능 사용 여부. 전체 적용(enforce) 시 사용된다. */
    public ShareStatusData findGlobalShareStatusConfig() {
        SystemConfigValueData config = systemConfigQueryRepository.findGlobalBySettingKey(SystemConfigKey.SHARE_STATUS.getDbKey());
        boolean enabled = config != null && Boolean.parseBoolean(config.getValueText());
        return ShareStatusData.builder().enabled(enabled).build();
    }

    public ClientConfigData findClientConfig(Integer tenantSeq, boolean shareStatus) {

        Map<SystemConfigKey, SystemConfigValueData> configMap = findConfigMap(tenantSeq, SystemConfigKey.getUserVisibleKeys());

        Boolean intellectStatus = Optional.ofNullable(configMap.get(SystemConfigKey.INTELLECT_STATUS)).map(c -> Boolean.parseBoolean(c.getValueText())).orElse(false);

        return ClientConfigData.builder()
                              .intellectStatus(intellectStatus)
                              .officeStatus(findOfficeConfig(tenantSeq).isOfficeEnabled())
                              .shareStatus(shareStatus)
                              .build();
    }
}
