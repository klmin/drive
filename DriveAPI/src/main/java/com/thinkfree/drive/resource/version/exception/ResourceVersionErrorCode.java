package com.thinkfree.drive.resource.version.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ResourceVersionErrorCode implements ErrorCode {
    UPDATE_FAIL(HttpStatus.BAD_REQUEST, "RESOURCE_VERSION_001","수정에 실패 했습니다."),
    DELETE_FAIL(HttpStatus.BAD_REQUEST, "RESOURCE_VERSION_002","삭제에 실패 했습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "RESOURCE_VERSION_003","데이터를 찾을 수 없습니다."),
    VERSION_PROCESSING_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "RESOURCE_VERSION_005","버전 처리 중 예기치 못한 오류가 발생했습니다."),
    RESTORE_FAIL(HttpStatus.INTERNAL_SERVER_ERROR, "RESOURCE_VERSION_006","버전 복원 중 예기치 못한 오류가 발생했습니다."),
    INSERT_FAIL(HttpStatus.INTERNAL_SERVER_ERROR, "RESOURCE_VERSION_007","등록에 실패 했습니다.")
    ;

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}