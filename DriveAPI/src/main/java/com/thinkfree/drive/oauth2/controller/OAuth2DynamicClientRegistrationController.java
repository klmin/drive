package com.thinkfree.drive.oauth2.controller;

import com.thinkfree.drive.oauth2.constants.OAuth2Constants;
import com.thinkfree.drive.oauth2.exception.InvalidClientRegistrationException;
import com.thinkfree.drive.oauth2.request.OAuth2ClientRegistrationRequest;
import com.thinkfree.drive.oauth2.response.OAuth2ClientRegistrationResponse;
import com.thinkfree.drive.oauth2.response.OAuth2RegistrationErrorResponse;
import com.thinkfree.drive.oauth2.usecase.OAuth2ClientRegistrationUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 익명 DCR — RFC 7591 Dynamic Client Registration (MCP_OAUTH_FLOW.md §2.4).
 *
 * SAS의 OIDC DCR(/connect/register)은 initial access token을 요구하지만 MCP
 * 클라이언트(Claude 등)는 익명으로 등록 요청한다 → SAS 모듈 대신 직접 구현.
 * AS 메타데이터의 registration_endpoint 광고는 OAuth2AuthorizationServerConfig에서 처리.
 *
 * HTTP 매핑만 담당 — 검증·응답 조립은 OAuth2ClientRegistrationUseCase,
 * 등록 정책은 OAuth2ClientRegistrationService.
 *
 * 어떤 시큐리티 체인 matcher에도 안 걸리는 경로라 익명 접근 가능.
 * TODO: 익명 등록 남용 방지용 rate limit (운영 전환 시).
 */
@RestController
@RequiredArgsConstructor
public class OAuth2DynamicClientRegistrationController {

    private final OAuth2ClientRegistrationUseCase oauth2ClientRegistrationUseCase;

    @PostMapping(OAuth2Constants.REGISTRATION_PATH)
    public ResponseEntity<OAuth2ClientRegistrationResponse> register(@RequestBody OAuth2ClientRegistrationRequest request) {
        var response = oauth2ClientRegistrationUseCase.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 검증 실패 → RFC 7591 §3.2.2 에러 응답 (앱 공통 에러 포맷이 아니라 스펙 포맷이어야 함) */
    @ExceptionHandler(InvalidClientRegistrationException.class)
    public ResponseEntity<OAuth2RegistrationErrorResponse> handleInvalidRegistration(InvalidClientRegistrationException e) {
        return ResponseEntity.badRequest().body(new OAuth2RegistrationErrorResponse(e.getError(), e.getMessage()));
    }
}
