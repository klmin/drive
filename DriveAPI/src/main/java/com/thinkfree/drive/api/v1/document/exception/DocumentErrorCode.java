package com.thinkfree.drive.api.v1.document.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum DocumentErrorCode implements ErrorCode {
    DEFAULT_UPLOAD_FAIL(HttpStatus.INTERNAL_SERVER_ERROR, "DOCUMENT_001","기본 문서 양식 업로드에 실패했습니다."),
    DEFAULT_GET_FAIL(HttpStatus.INTERNAL_SERVER_ERROR, "DOCUMENT_002","기본 문서 양식 가져오기에 실패했습니다."),
    DEFAULT_NOT_FOUND(HttpStatus.NOT_FOUND, "DOCUMENT_003","요청한 기본 문서 양식이 존재하지 않습니다.")
    ;

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}