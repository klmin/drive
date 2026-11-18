package com.thinkfree.drive.cleanup.trash.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TrashCleanUpErrorCode implements ErrorCode {

    DELETE_FAIL(HttpStatus.BAD_REQUEST, "TRASH_CLEAN_UP_001","삭제에 실패 했습니다.");

    private final HttpStatus status;
    private final String errorCode;
    private final String message;

}
