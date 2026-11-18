package com.thinkfree.drive.systemconfig.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SystemConfigErrorCode implements ErrorCode {
    SYSTEM_CONFIG_NOT_FOUND(HttpStatus.NOT_FOUND, "SYSTEM_CONFIG_001", "SYSTEM 설정 정보를 찾을 수 없습니다."),
    GENERAL_NOT_FOUND(HttpStatus.NOT_FOUND, "SYSTEM_CONFIG_002", "GENERAL 설정 정보를 찾을 수 없습니다."),
    OFFICE_CONFIG_NOT_FOUND(HttpStatus.NOT_FOUND, "SYSTEM_CONFIG_003", "OFFICE 설정 정보를 찾을 수 없습니다."),
    APP_CONFIG_NOT_FOUND(HttpStatus.NOT_FOUND, "SYSTEM_CONFIG_004", "APP 설정 정보를 찾을 수 없습니다."),
    INTELLECT_CONFIG_NOT_FOUND(HttpStatus.NOT_FOUND, "SYSTEM_CONFIG_005", "INTELLECT 설정 정보를 찾을 수 없습니다."),
    PRODUCT_INFO_CONFIG_NOT_FOUND(HttpStatus.NOT_FOUND, "SYSTEM_CONFIG_006", "PRODUCT 설정 정보를 찾을 수 없습니다."),
    FILE_SIZE_LIMIT_EXCEEDED(HttpStatus.PAYLOAD_TOO_LARGE, "SYSTEM_CONFIG_007", "파일 크기가 업로드 제한을 초과했습니다."),
    GENERAL_UPDATE_REQUIRES_SUPER_ADMIN(HttpStatus.FORBIDDEN, "SYSTEM_CONFIG_008", "다중 테넌트 환경에서는 슈퍼관리자만 일반 설정을 수정할 수 있습니다."),
    TENANT_TOTAL_QUOTA_NOT_FOUND(HttpStatus.NOT_FOUND, "SYSTEM_CONFIG_009", "테넌트 총 용량 설정 정보를 찾을 수 없습니다."),
    OFFICE_MANAGED_BY_SERVICE(HttpStatus.FORBIDDEN, "SYSTEM_CONFIG_010", "Developers가 관리하는 Office 연결 설정은 테넌트 관리자가 변경할 수 없습니다."),
    ;

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
