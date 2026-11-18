package com.thinkfree.drive.notification.event.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum NotificationEventErrorCode implements ErrorCode {
    REQUIRED(HttpStatus.INTERNAL_SERVER_ERROR, "NOTIFICATION_EVENT_001","필수 값이 누락 되었습니다."),
    ;

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}