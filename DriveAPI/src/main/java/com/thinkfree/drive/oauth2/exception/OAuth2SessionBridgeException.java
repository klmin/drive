package com.thinkfree.drive.oauth2.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * JWT→세션 브리지 실패 — 에러 코드와 HTTP 상태를 담아 컨트롤러에서 JSON으로 변환된다.
 *
 * 에러 코드 계약 (프론트 oauth2-login 페이지가 분기):
 * - invalid_token(401): Drive JWT 누락/만료/블랙리스트/테넌트 불일치 → 프론트는 재로그인 유도
 * - no_pending_authorization(400): 세션에 대기 중인 authorize 요청 없음
 *   (authorize를 거치지 않고 직접 진입) → 프론트는 안내 후 홈으로
 */
@Getter
public class OAuth2SessionBridgeException extends RuntimeException {

    public static final String INVALID_TOKEN = "invalid_token";
    public static final String NO_PENDING_AUTHORIZATION = "no_pending_authorization";

    private final String error;
    private final HttpStatus status;

    public OAuth2SessionBridgeException(String error, HttpStatus status, String description) {
        super(description);
        this.error = error;
        this.status = status;
    }

    public static OAuth2SessionBridgeException invalidToken(String description) {
        return new OAuth2SessionBridgeException(INVALID_TOKEN, HttpStatus.UNAUTHORIZED, description);
    }

    public static OAuth2SessionBridgeException noPendingAuthorization() {
        return new OAuth2SessionBridgeException(NO_PENDING_AUTHORIZATION, HttpStatus.BAD_REQUEST,
                "No pending OAuth2 authorization request in session");
    }
}
