package com.thinkfree.drive.oauth2.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * OAuth2 도메인 웹 API(/api/v1/oauth2/**) 에러 코드.
 * DCR·동의 등 OAuth 프로토콜 엔드포인트의 에러는 RFC 규격 응답(전용 exception)을 쓰므로 여기 두지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum OAuth2ErrorCode implements ErrorCode {
    CONNECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "OAUTH2_001", "연결 정보를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
