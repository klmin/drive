package com.thinkfree.drive.starred.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum StarredErrorCode implements ErrorCode {

    DELETE_FAIL(HttpStatus.BAD_REQUEST, "STARRED_001","삭제에 실패 했습니다."),
    CONFLICT(HttpStatus.CONFLICT, "STARRED_002", "이미 중요문서함에 등록된 리소스입니다.")
    ;

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}