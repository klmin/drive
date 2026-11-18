package com.thinkfree.drive.permission.enums;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PermissionErrorCode implements ErrorCode {

    RESOURCE_NOT_SHARED(HttpStatus.FORBIDDEN, "PERMISSION_001","리소스가 공유되지 않았습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "PERMISSION_002", "접근 권한이 없습니다"),
    MISSING_VIEW(HttpStatus.BAD_REQUEST, "PERMISSION_003", "VIEW 권한은 반드시 활성화되어야 합니다."),
    INVALID_PREREQUISITE (HttpStatus.BAD_REQUEST, "PERMISSION_004", "필요한 선행 권한이 충족되지 않았습니다."),
    NOT_ALLOWED_IN_CONTEXT(HttpStatus.FORBIDDEN,  "PERMISSION_005", "이 공유 유형에서 허용되지 않는 권한입니다.");
    ;

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}