package com.thinkfree.drive.storage.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum StorageErrorCode implements ErrorCode{
    
    UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_001", "업로드에 실패했습니다."),
    DOWNLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_002", "다운로드에 실패했습니다."),
    COPY_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_003", "복제에 실패했습니다."),
    GET_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_004", "정보 가져오기에 실패했습니다."),
    DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_005", "삭제에 실패했습니다."),
    NO_SUCH_KEY(HttpStatus.NOT_FOUND, "STORAGE_006", "해당 키가 존재하지 않습니다.")
    ;
    
        private final HttpStatus status;
        private final String errorCode;
        private final String message;
        
}

