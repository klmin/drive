package com.thinkfree.drive.auth.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {
    AUTH_EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_001", "토큰의 유효기간이 만료되었습니다."),
    AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_002", "토큰이 유효하지 않습니다."),
    INVALID_PERMISSION(HttpStatus.FORBIDDEN, "AUTH_003", "권한이 없습니다. 로그인 먼저 해주세요."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH_004", "리프레시 토큰을 찾을 수 없습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH_005", "토큰으로부터 추출된 seq를 가진 유저를 찾을 수 없습니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "AUTH_006", "아이디 또는 비밀번호가 올바르지 않습니다."),
    ADMIN_PRIVILEGE_REQUIRED(HttpStatus.FORBIDDEN, "AUTH_007", "관리자 권한이 필요합니다."),
    ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "AUTH_008", "로그인 5회 실패로 계정이 잠겼습니다. 15분 뒤 다시 시도해 주세요."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH_009", "권한이 없는 유저의 접근입니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_010", "유효하지 않은 토큰입니다."),
    CHALLENGE_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_011", "MFA 인증 시간이 만료되었습니다. 다시 로그인해주세요."),
    INVALID_TOKEN_TYPE(HttpStatus.UNAUTHORIZED, "AUTH_012", "토큰 타입이 유효하지 않습니다."),
    LOGIN_TYPE_MISMATCH(HttpStatus.FORBIDDEN, "AUTH_013", "현재 설정된 로그인 방식이 허용되지 않습니다."),
    LOGIN_FAILURE_WARNING(HttpStatus.UNAUTHORIZED, "AUTH_014", "로그인을 연속 실패했습니다. 1회 더 실패 시 계정이 잠깁니다.");

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}