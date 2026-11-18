package com.thinkfree.drive.postprocess.orchestrator.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PostProcessingErrorCode implements ErrorCode {
    
    QUOTA_STATE_EVALUATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "POST_PROCESSING_001", "용량 상태 평가 중 오류가 발생했습니다."),
    QUOTA_STATE_UPSERT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "POST_PROCESSING_002", "용량 상태 저장 중 오류가 발생했습니다."),
    REQUIRED_FIELD_MISSING(HttpStatus.BAD_REQUEST, "POST_PROCESSING_003", "필수 값이 누락되었습니다."),
    POST_PROCESSING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "POST_PROCESSING_004", "업로드 후 처리 중 오류가 발생했습니다."),
    SHARING_NOTIFY_CREATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "POST_PROCESSING_005", "공유 알림 데이터 생성 중 오류가 발생했습니다."),
    UNSHARING_NOTIFY_CREATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "POST_PROCESSING_006", "공유해제 알림 데이터 생성 중 오류가 발생했습니다."),
    TRASH_NOTIFY_CREATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "POST_PROCESSING_007", "휴지통 알림 데이터 생성 중 오류가 발생했습니다."),
    MOVE_NOTIFY_CREATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "POST_PROCESSING_008", "리소스 이동 알림 데이터 생성 중 오류가 발생했습니다."),
    ;

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
    
}
