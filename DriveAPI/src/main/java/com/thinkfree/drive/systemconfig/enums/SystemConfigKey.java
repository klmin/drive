package com.thinkfree.drive.systemconfig.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Getter
public enum SystemConfigKey {
    OFFICE_HTTP_STORAGE_ADAPTER_NAME("office_http_storage_adapter_name"),
    OFFICE_SERVICE_MANAGED("office_service_managed"),
    OFFICE_DOMAIN("office_domain"),
    OFFICE_CONNECTION_TYPE("office_connection_type"),
    OFFICE_AUTH_MODE("office_auth_mode"),
    // Historical DB key only; Forward adapter configuration is no longer used.
    OFFICE_ADAPTER_NAME("office_adapter_name"),
    OFFICE_TICKET_SERVICE_TOKEN("office_ticket_service_token"),
    OFFICE_HTTP_STORAGE_REQUEST_JWT_SECRET("office_http_storage_request_jwt_secret"),
    INTELLECT_STATUS("intellect_status"),
    MAX_FILE_UPLOAD_SIZE("max_file_upload_size"),
    MFA_STATUS("mfa_status"),
    ACCESS_RESTRICTION_STATUS("access_restriction_status"),
    SHARE_STATUS("share_status"),
    LICENSE_DEVICE_ID("license_device_id"),
    TENANT_TOTAL_QUOTA_BYTE("tenant_total_quota_byte")
    ;

    public final String dbKey;

    private static final Map<String, SystemConfigKey> DB_KEY_MAP = Arrays.stream(values()).collect(Collectors.toMap(SystemConfigKey::getDbKey, Function.identity()));

    public static SystemConfigKey fromDbKey(String dbKey) {
        return StringUtils.hasText(dbKey) ? DB_KEY_MAP.get(dbKey) : null;
    }

    public boolean isUserVisible() {
        return switch (this) {
            case INTELLECT_STATUS, SHARE_STATUS -> true;
            default -> false;
        };
    }

    public static List<SystemConfigKey> getUserVisibleKeys() {
        return Arrays.stream(values())
                     .filter(SystemConfigKey::isUserVisible)
                     .toList();
    }
}
