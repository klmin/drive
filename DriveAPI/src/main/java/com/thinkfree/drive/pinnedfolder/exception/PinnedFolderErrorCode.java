package com.thinkfree.drive.pinnedfolder.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PinnedFolderErrorCode implements ErrorCode {
    DELETE_FAIL(HttpStatus.BAD_REQUEST, "PINNED_FOLDER_001","삭제에 실패 했습니다."),
    CONFLICT(HttpStatus.CONFLICT, "PINNED_FOLDER_002", "데이터가 이미 존재합니다."),
    LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "PINNED_FOLDER_003", "고정 폴더는 최대 3개까지만 등록할 수 있습니다."),
    ;

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}