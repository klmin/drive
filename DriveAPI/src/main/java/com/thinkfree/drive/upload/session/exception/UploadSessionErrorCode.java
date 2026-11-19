package com.thinkfree.drive.upload.session.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 업로드 세션 오류(docs/design/upload-session-redesign.md §5.3). */
@Getter
@RequiredArgsConstructor
public enum UploadSessionErrorCode implements ErrorCode {

    /** 세션이 PENDING 이 아니다(끝났거나 complete 처리 중) — 클라이언트는 남은 파트 전송을 멈춘다 */
    UPLOAD_SESSION_FAILED(HttpStatus.CONFLICT, "UPLOAD_001", "업로드가 더 이상 진행될 수 없습니다."),
    /** 사용자당 진행 중 세션 수 상한 */
    UPLOAD_SESSION_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "UPLOAD_002", "동시에 진행할 수 있는 업로드 수를 넘었습니다."),
    /** 세션이 없거나 요청자의 것이 아니다(존재 숨김) */
    UPLOAD_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "UPLOAD_003", "업로드를 찾을 수 없습니다."),
    /** 받은 내용의 크기가 신고 크기와 다르다 */
    UPLOAD_SIZE_MISMATCH(HttpStatus.BAD_REQUEST, "UPLOAD_004", "업로드한 파일 크기가 올바르지 않습니다.")
    ;

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
