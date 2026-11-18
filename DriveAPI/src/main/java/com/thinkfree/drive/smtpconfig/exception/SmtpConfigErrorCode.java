package com.thinkfree.drive.smtpconfig.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SmtpConfigErrorCode implements ErrorCode {
    SMTP_CONFIG_NOT_FOUND(HttpStatus.NOT_FOUND, "SMTP_CONFIG_001", "SMTP 설정 정보를 찾을 수 없습니다."),
    SMTP_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "SMTP_CONFIG_002", "SMTP 메일 발송이 실패하였습니다. 설정을 다시 확인해주세요.");

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
