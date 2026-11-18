package com.thinkfree.drive.share.target.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ShareTargetErrorCode implements ErrorCode {
    INSERT_FAIL(HttpStatus.INTERNAL_SERVER_ERROR, "SHARE_TARGET_001","등록에 실패 했습니다.")
    ;

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}