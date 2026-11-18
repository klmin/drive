package com.thinkfree.drive.oauth2.controller;

import com.thinkfree.drive.auth.cookie.AuthCookieManager;
import com.thinkfree.drive.oauth2.constants.OAuth2Constants;
import com.thinkfree.drive.oauth2.exception.OAuth2SessionBridgeException;
import com.thinkfree.drive.oauth2.response.OAuth2SessionBridgeErrorResponse;
import com.thinkfree.drive.oauth2.response.OAuth2SessionBridgeResponse;
import com.thinkfree.drive.oauth2.usecase.OAuth2SessionBridgeUseCase;
import com.thinkfree.drive.user.enums.RoleType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C안 브리지 HTTP 매핑 — 프론트 oauth2-login 페이지가 기존 로그인 완료 후 호출한다.
 * 검증·세션 확립은 OAuth2SessionBridgeUseCase 담당 (MCP_OAUTH_BRIDGE_FLOW.md).
 *
 * DCR 컨트롤러처럼 어떤 시큐리티 체인 matcher에도 안 걸리는 경로 — 인증은
 * 체인이 아니라 유스케이스의 Drive JWT 검증으로 수행한다. 토큰은 사용자 로그인 쿠키에서 읽되
 * {@code X-Drive-Role: USER} 헤더가 있을 때만 — 커스텀 헤더라 다른 사이트 요청은 CORS 사전 요청에서 막혀 CSRF 를 겸한다.
 */
@RestController
@RequiredArgsConstructor
public class OAuth2SessionBridgeController {

    private final OAuth2SessionBridgeUseCase oauth2SessionBridgeUseCase;

    @PostMapping(OAuth2Constants.SESSION_BRIDGE_PATH)
    public OAuth2SessionBridgeResponse bridge(HttpServletRequest request, HttpServletResponse response) {
        String accessToken = AuthCookieManager.resolveCookieRole(request)
                .filter(role -> role == RoleType.USER)
                .flatMap(role -> AuthCookieManager.resolveAccessToken(request, role))
                .orElse(null);
        return oauth2SessionBridgeUseCase.bridge(accessToken, request, response);
    }

    @ExceptionHandler(OAuth2SessionBridgeException.class)
    public ResponseEntity<OAuth2SessionBridgeErrorResponse> handleBridgeFailure(OAuth2SessionBridgeException e) {
        return ResponseEntity.status(e.getStatus())
                .body(new OAuth2SessionBridgeErrorResponse(e.getError(), e.getMessage()));
    }
}
