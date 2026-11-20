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
    UPLOAD_SIZE_MISMATCH(HttpStatus.BAD_REQUEST, "UPLOAD_004", "업로드한 파일 크기가 올바르지 않습니다."),
    /** complete 를 다른 요청이 처리 중이다(중복 클릭) — 잠시 뒤 GET 으로 결과를 확인한다 */
    UPLOAD_SESSION_IN_PROGRESS(HttpStatus.CONFLICT, "UPLOAD_005", "업로드 완료를 처리하고 있습니다."),
    /** 세션이 실패·취소·만료로 끝났다 — 처음부터 다시 올린다 */
    UPLOAD_SESSION_GONE(HttpStatus.GONE, "UPLOAD_006", "업로드가 이미 끝났습니다. 처음부터 다시 올려 주세요."),
    /** 파트 PUT 에 Content-Length 가 없다(chunked 전송 불가) */
    UPLOAD_PART_LENGTH_REQUIRED(HttpStatus.LENGTH_REQUIRED, "UPLOAD_007", "파트 크기(Content-Length)가 필요합니다."),
    /** 파트 번호가 범위 밖이거나 파트 크기가 서버가 정한 크기와 다르다 */
    UPLOAD_PART_INVALID(HttpStatus.BAD_REQUEST, "UPLOAD_008", "파트 번호 또는 크기가 올바르지 않습니다."),
    /** complete 시 빠지거나 크기가 다른 파트가 있다 — 세션은 실패로 끝난다 */
    UPLOAD_PARTS_INCOMPLETE(HttpStatus.BAD_REQUEST, "UPLOAD_009", "올라가지 않았거나 크기가 다른 파트가 있습니다.")
    ;

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
