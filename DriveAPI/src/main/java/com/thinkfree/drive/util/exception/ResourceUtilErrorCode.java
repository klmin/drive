package com.thinkfree.drive.util.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ResourceUtilErrorCode implements ErrorCode{
    
    FILE_STREAM_TRANSFER_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "RESOURCE_UTIL_001", "파일 스트림 전송에 실패했습니다."),
    ZIP_CREATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "RESOURCE_UTIL_002", "ZIP 압축 생성에 실패했습니다.")
    ;
    
    private final HttpStatus status;
    private final String errorCode;
    private final String message;
        
}

