package com.thinkfree.drive.user.admin.exception.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AdminUserErrorCode implements ErrorCode {
    RESTORE_FAILED(HttpStatus.BAD_REQUEST, "ADMIN_USER_001","계정 복원에 실패했습니다."),
    PERMANENT_DELETE_TARGET_MISMATCH(HttpStatus.BAD_REQUEST, "ADMIN_USER_002","요청한 계정 목록과 실제 삭제 대상 계정 수가 일치하지 않습니다."),
    MARK_USERS_AS_DELETED_MISMATCH(HttpStatus.BAD_REQUEST, "ADMIN_USER_003","요청한 계정 목록과 실제 삭제 처리된 계정 수가 일치하지 않습니다.")
    ;

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
