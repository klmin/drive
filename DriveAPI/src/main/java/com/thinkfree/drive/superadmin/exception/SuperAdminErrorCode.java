package com.thinkfree.drive.superadmin.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SuperAdminErrorCode implements ErrorCode {
    INVALID_TENANT_ACCESS_TOKEN(HttpStatus.BAD_REQUEST, "SUPER_ADMIN_001", "유효하지 않은 테넌트 접근 토큰입니다."),
    EXPIRED_TENANT_ACCESS_TOKEN(HttpStatus.BAD_REQUEST, "SUPER_ADMIN_002", "만료된 테넌트 접근 토큰입니다.");

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
