package com.thinkfree.drive.basedomain.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BaseDomainErrorCode implements ErrorCode {

    BASE_DOMAIN_NOT_FOUND(HttpStatus.NOT_FOUND, "BASE_DOMAIN_001", "베이스 도메인이 설정되지 않았습니다."),
    INVALID_BASE_DOMAIN(HttpStatus.BAD_REQUEST, "BASE_DOMAIN_002", "유효하지 않은 베이스 도메인입니다."),
    DOMAIN_BASE_MISMATCH(HttpStatus.BAD_REQUEST, "BASE_DOMAIN_004", "입력한 도메인이 등록된 베이스 도메인으로 끝나지 않습니다."),
    ;

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
