package com.thinkfree.drive.mfa.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MfaErrorCode implements ErrorCode {
    MFA_ALREADY_ENABLED(HttpStatus.CONFLICT, "MFA_001", "이미 MFA가 활성화되어 있습니다."),
    MFA_NOT_PENDING(HttpStatus.BAD_REQUEST, "MFA_002", "MFA 설정이 진행되지 않았습니다."),
    MFA_INVALID_CODE(HttpStatus.BAD_REQUEST, "MFA_003", "인증 코드가 올바르지 않습니다."),
    MFA_NOT_ENABLED(HttpStatus.BAD_REQUEST, "MFA_004", "MFA가 활성화되지 않았습니다."),
    MFA_SECRET_KEY_NOT_FOUND(HttpStatus.BAD_REQUEST,"MFA_005","MFA 설정 정보가 존재하지 않습니다. MFA 설정을 다시 시작해주세요.")
    ;
    

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
