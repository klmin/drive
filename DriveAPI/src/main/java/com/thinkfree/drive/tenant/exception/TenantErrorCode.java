package com.thinkfree.drive.tenant.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TenantErrorCode implements ErrorCode {

    TENANT_NOT_FOUND(HttpStatus.NOT_FOUND, "TENANT_001", "테넌트를 찾을 수 없습니다."),
    TENANT_LOGIN_TYPE_UPDATE_FAIL(HttpStatus.BAD_REQUEST,  "TENANT_003", "로그인 유형 수정에 실패 했습니다."),
    TENANT_HOST_NOT_FOUND(HttpStatus.NOT_FOUND, "TENANT_004", "요청 주소에 해당하는 테넌트가 없습니다."),
    TENANT_DOMAIN_ALREADY_EXISTS(HttpStatus.CONFLICT, "TENANT_006", "이미 사용 중인 도메인입니다."),
    INVALID_TENANT_DOMAIN(HttpStatus.BAD_REQUEST, "TENANT_007", "유효하지 않은 도메인 입니다."),
    TENANT_MISMATCH(HttpStatus.FORBIDDEN, "TENANT_008", "사용자 테넌트와 요청 테넌트가 일치하지 않습니다."),
    TENANT_USER_MODE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "TENANT_009", "SELF_MANAGED 테넌트는 SINGLE 사용자 모드를 쓸 수 없습니다."),
    TENANT_DELETE_NOT_ALLOWED(HttpStatus.FORBIDDEN, "TENANT_010", "SYSTEM_MANAGED 테넌트만 삭제할 수 있습니다."),
    SYSTEM_MANAGED_RESTRICTED(HttpStatus.FORBIDDEN, "TENANT_011", "이 테넌트에서는 허용되지 않는 작업입니다.")
    ;

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
