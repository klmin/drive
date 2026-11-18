package com.thinkfree.drive.common.file.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FileErrorCode implements ErrorCode{
    
    CLASSPATH_READ_FAIL(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_001","클래스패스 리소스 읽기에 실패했습니다."),
    CLASSPATH_NOT_FOUND(HttpStatus.NOT_FOUND, "FILE_002","클래스패스 리소스가 존재하지 않습니다.")
    ;
    
    private final HttpStatus status;
    private final String errorCode;
    private final String message;
        
}

