package com.thinkfree.drive.trash.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TrashErrorCode implements ErrorCode {
    DELETE_FAIL(HttpStatus.BAD_REQUEST, "TRASH_001","삭제에 실패 했습니다.")
    ;

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}