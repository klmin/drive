package com.thinkfree.drive.recent.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum RecentErrorCode implements ErrorCode {

    NOT_FOUND(HttpStatus.NOT_FOUND, "RECENT_001", "데이터를 찾을 수 없습니다."),
    DELETE_FAIL(HttpStatus.BAD_REQUEST, "RECENT_002","삭제에 실패 했습니다.")
    ;

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
