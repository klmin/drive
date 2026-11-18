package com.thinkfree.drive.sso.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SsoErrorCode implements ErrorCode {
    
    PROVIDER_DISABLED       (HttpStatus.FORBIDDEN,            "SSO_001", "비활성화된 SSO Provider입니다."),
    UNSUPPORTED_PROTOCOL    (HttpStatus.BAD_REQUEST,          "SSO_002", "지원하지 않는 SSO 프로토콜입니다."),
    INVALID_STATE           (HttpStatus.BAD_REQUEST,          "SSO_003", "유효하지 않은 state 파라미터입니다. 다시 로그인해 주세요."),
    OIDC_DISCOVERY_FAILED   (HttpStatus.BAD_GATEWAY,          "SSO_004", "IdP Discovery 조회에 실패했습니다."),
    OIDC_TOKEN_EXCHANGE_FAILED(HttpStatus.BAD_GATEWAY,        "SSO_005", "IdP 토큰 교환에 실패했습니다."),
    OIDC_INVALID_ID_TOKEN   (HttpStatus.UNAUTHORIZED,         "SSO_006", "IdP ID Token 검증에 실패했습니다."),
    EMAIL_ALREADY_EXISTS    (HttpStatus.CONFLICT,             "SSO_007", "이미 가입된 이메일입니다. 기존 계정으로 로그인 후 SSO를 연결해 주세요."),
    INVALID_SIGNUP_TOKEN   (HttpStatus.BAD_REQUEST,          "SSO_008", "유효하지 않은 signup 토큰입니다."),
    ACCOUNT_INACTIVE        (HttpStatus.FORBIDDEN,           "SSO_009", "비활성화된 계정입니다. 관리자에게 문의해 주세요."),
    ACCOUNT_PENDING_DELETE  (HttpStatus.FORBIDDEN,           "SSO_010", "삭제 대기 중인 계정입니다. 관리자에게 문의해 주세요."),
    REQUIRED_SSO_SESSION_TOKEN(HttpStatus.BAD_REQUEST,         "SSO_011", "토큰이 존재하지 않습니다."),
    INVALID_SSO_SESSION_TOKEN(HttpStatus.BAD_REQUEST,         "SSO_012", "유효하지 않은 토큰입니다."),
    INTERNAL_ERROR          (HttpStatus.INTERNAL_SERVER_ERROR,"SSO_013", "SSO 처리 중 오류가 발생했습니다."),
    JWT_INVALID_TOKEN       (HttpStatus.UNAUTHORIZED,         "SSO_014", "JWT 토큰 검증에 실패했습니다."),
    JWT_USER_NOT_FOUND      (HttpStatus.NOT_FOUND,            "SSO_015", "Drive에 등록되지 않은 사용자입니다."),
    JWT_CONFIG_NOT_FOUND    (HttpStatus.BAD_REQUEST,          "SSO_016", "요청한 issuer에 해당하는 JWT SSO 설정이 없습니다."),
    JWT_USER_CONFLICT       (HttpStatus.CONFLICT,             "SSO_017", "현재 로그인된 사용자와 다른 사용자의 토큰입니다."),
    OIDC_USER_NOT_FOUND       (HttpStatus.NOT_FOUND,             "SSO_018", "유저를 찾을 수 없습니다."),
    ;

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
