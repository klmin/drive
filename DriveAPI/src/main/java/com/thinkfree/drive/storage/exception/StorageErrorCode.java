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
    NO_SUCH_KEY(HttpStatus.NOT_FOUND, "STORAGE_006", "해당 키가 존재하지 않습니다."),
    /** 멀티파트 업로드가 스토리지에 없다(이미 abort·complete 됐거나 lifecycle 로 정리됨) */
    NO_SUCH_UPLOAD(HttpStatus.CONFLICT, "STORAGE_007", "업로드가 존재하지 않습니다."),
    MULTIPART_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_008", "분할 업로드 처리에 실패했습니다."),
    /** S3 HTTP 커넥션 풀이 모두 사용 중이라 연결을 못 빌림 — 잠시 뒤 다시 시도할 수 있는 상태(storage.http.*) */
    CONNECTION_POOL_EXHAUSTED(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_009", "스토리지 연결이 모두 사용 중입니다. 잠시 후 다시 시도해 주세요.")
    ;
    
        private final HttpStatus status;
        private final String errorCode;
        private final String message;
        
}

