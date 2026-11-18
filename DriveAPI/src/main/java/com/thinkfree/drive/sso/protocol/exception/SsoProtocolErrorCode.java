package com.thinkfree.drive.sso.protocol.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SsoProtocolErrorCode implements ErrorCode {
    
    UNSUPPORTED_PROTOCOL(HttpStatus.BAD_REQUEST, "SSO_PROTOCOL_001", "지원하지 않는 SSO 프로토콜입니다."),
    PROTOCOL_CONFIG_MISMATCH(HttpStatus.BAD_REQUEST, "SSO_PROTOCOL_002", "프로토콜 타입과 설정 정보가 일치하지 않습니다.")
    ;
    
    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
