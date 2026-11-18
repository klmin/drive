package com.thinkfree.drive.user.quota.alert.state.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserQuotaAlertStateErrorCode implements ErrorCode {
    UPSERT_FAIL(HttpStatus.INTERNAL_SERVER_ERROR, "USER_QUOTA_ALERT_STATE_001","용량 산정 중 오류가 발생하였습니다."),
    ;

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
