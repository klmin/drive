package com.thinkfree.drive.cleanup.share.enums;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CleanUpShareErrorCode implements ErrorCode {

    CLEAN_UP_SHARE_INVALID_SHARE_TYPE(HttpStatus.NOT_FOUND, "CLEAN_UP_SHARE_001","유효하지 않은 공유 유형 입니다.")
    ;

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}