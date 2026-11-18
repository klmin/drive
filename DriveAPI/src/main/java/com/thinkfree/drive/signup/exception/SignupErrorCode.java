package com.thinkfree.drive.signup.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SignupErrorCode implements ErrorCode {
    UNSUPPORTED_SERVICE(HttpStatus.BAD_REQUEST, "Signup_001", "지원하지 않는 SSO 프로토콜입니다.")
    ;
    
    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
