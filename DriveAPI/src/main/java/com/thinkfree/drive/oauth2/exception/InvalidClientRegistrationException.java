package com.thinkfree.drive.oauth2.exception;

import lombok.Getter;

/** DCR 등록 요청 검증 실패 — RFC 7591 에러 코드와 설명을 담아 400 응답으로 변환된다. */
@Getter
public class InvalidClientRegistrationException extends RuntimeException {

    private final String error;

    public InvalidClientRegistrationException(String error, String description) {
        super(description);
        this.error = error;
    }
}
