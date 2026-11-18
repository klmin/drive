package com.thinkfree.drive.api.v1.admin.auditlog.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuditLogQueryErrorCode implements ErrorCode {
    // 거버넌스 서버 조회 실패(통신 오류·오류 응답) — 상세 원인은 서버 로그로
    QUERY_FAILED(HttpStatus.BAD_GATEWAY, "AUDIT_LOG_001", "감사 로그 조회에 실패했습니다. 잠시 후 다시 시도해 주세요.")
    ;

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
