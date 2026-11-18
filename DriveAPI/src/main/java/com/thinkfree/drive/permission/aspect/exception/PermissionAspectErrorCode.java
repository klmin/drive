package com.thinkfree.drive.permission.aspect.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PermissionAspectErrorCode implements ErrorCode {
    PERMISSION_UNAUTHENTICATED_USER(HttpStatus.UNAUTHORIZED, "PERMISSION_ASPECT_001","로그인이 필요합니다."),
    PERMISSION_REQUIRED(HttpStatus.INTERNAL_SERVER_ERROR, "PERMISSION_ASPECT_002", "필수 값이 누락 되었습니다.")
    ;

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}