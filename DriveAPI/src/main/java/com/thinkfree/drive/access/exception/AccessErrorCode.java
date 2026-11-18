package com.thinkfree.drive.access.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AccessErrorCode implements ErrorCode {

    BLOCKED_COUNTRY(HttpStatus.FORBIDDEN, "ACCESS_001", "접근이 허용되지 않는 국가에서의 요청입니다."),
    INVALID_COUNTRY_CODE(HttpStatus.BAD_REQUEST, "ACCESS_002", "유효하지 않은 국가 코드입니다."),
    EMPTY_ALLOWED_COUNTRIES(HttpStatus.BAD_REQUEST, "ACCESS_003", "접근 허용 국가 또는 IP를 하나 이상 등록한 후 활성화할 수 있습니다."),
    INVALID_IP_CIDR(HttpStatus.BAD_REQUEST, "ACCESS_004", "유효하지 않은 IP 또는 CIDR 형식입니다."),
    BLOCKED_IP(HttpStatus.FORBIDDEN, "ACCESS_005", "접근이 허용되지 않는 IP에서의 요청입니다.");

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
    
}
