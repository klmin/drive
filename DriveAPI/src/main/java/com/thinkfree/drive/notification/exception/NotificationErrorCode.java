package com.thinkfree.drive.notification.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum NotificationErrorCode implements ErrorCode {
    INSERT_FAIL(HttpStatus.INTERNAL_SERVER_ERROR, "NOTIFICATION_001","등록에 실패 했습니다."),
    READ_FAIL(HttpStatus.INTERNAL_SERVER_ERROR, "NOTIFICATION_002","읽음 처리에 실패 했습니다."),
    UNREAD_FAIL(HttpStatus.INTERNAL_SERVER_ERROR, "NOTIFICATION_003","안 읽음 처리에 실패 했습니다."),
    ;

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}