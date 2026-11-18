package com.thinkfree.drive.sso.protocol.jwt.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SsoProtocolJwtErrorCode implements ErrorCode {

    JWT_CONFIG_NOT_FOUND(HttpStatus.BAD_REQUEST, "SSO_PROTOCOL_JWT_001", "JWT SSO 설정을 찾을 수 없습니다."),
    DUPLICATE_ISSUER(HttpStatus.CONFLICT, "SSO_PROTOCOL_JWT_002", "이미 사용 중인 issuer 입니다.");

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
