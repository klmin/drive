package com.thinkfree.drive.resource.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ResourceErrorCode implements ErrorCode {
    DELETE_FAIL(HttpStatus.BAD_REQUEST, "RESOURCE_001","삭제에 실패 했습니다."),
    UPDATE_FAIL(HttpStatus.BAD_REQUEST, "RESOURCE_002","수정에 실패 했습니다."),
    INSERT_FAIL(HttpStatus.BAD_REQUEST, "RESOURCE_003","등록에 실패 했습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "RESOURCE_005", "데이터를 찾을 수 없습니다."),
    CONFLICT(HttpStatus.CONFLICT, "RESOURCE_006", "리소스가 이미 존재합니다."),
    RENAME_FAIL(HttpStatus.BAD_REQUEST, "RESOURCE_007", "이름 변경에 실패 했습니다."),
    MOVE_FAIL(HttpStatus.BAD_REQUEST, "RESOURCE_008", "이동에 실패 했습니다."),
    RESTORE_FAIL(HttpStatus.BAD_REQUEST, "RESOURCE_009", "복원에 실패 했습니다."),
    DOWNLOAD_FAIL(HttpStatus.BAD_REQUEST, "RESOURCE_010", "다운로드에 실패 했습니다."),
    INVALID_TARGET(HttpStatus.BAD_REQUEST, "RESOURCE_011", "업데이트할 리소스가 지정되지 않았습니다."),
    OVERWRITE_FAIL(HttpStatus.BAD_REQUEST, "RESOURCE_012", "리소스 덮어쓰기에 실패했습니다."),
    OVERWRITE_REVERT_FAIL(HttpStatus.CONFLICT,"RESOURCE_013", "리소스 덮어쓰기 복원에 실패했습니다. 최신 변경 상태와 일치하지 않습니다."),
    MISSING_RESOURCE_SEQ(HttpStatus.BAD_REQUEST, "RESOURCE_014","리소스 식별자(resourceSeq)가 누락되었습니다."),
    LOCK_FAIL(HttpStatus.BAD_REQUEST, "RESOURCE_015", "리소스 잠금에 실패했습니다."),
    UNLOCK_FAIL(HttpStatus.BAD_REQUEST, "RESOURCE_016", "리소스 잠금 해제에 실패했습니다."),
    CREATE_LINK_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "RESOURCE_017","링크 생성에 실패했습니다."),
    INVALID_NAME(HttpStatus.BAD_REQUEST, "RESOURCE_018", "사용할 수 없는 이름입니다.")
    ;

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}