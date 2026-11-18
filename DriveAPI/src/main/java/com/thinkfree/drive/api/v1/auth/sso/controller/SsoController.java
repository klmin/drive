package com.thinkfree.drive.api.v1.auth.sso.controller;

import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.auth.sso.request.SsoJwtLoginRequest;
import com.thinkfree.drive.api.v1.auth.sso.request.SsoOidcCallbackRequest;
import com.thinkfree.drive.api.v1.auth.sso.request.SsoSignupRequest;
import com.thinkfree.drive.api.v1.auth.sso.response.SsoJwtLoginResponse;
import com.thinkfree.drive.api.v1.auth.sso.response.SsoSessionResponse;
import com.thinkfree.drive.api.v1.auth.sso.response.SsoSignupResponse;
import com.thinkfree.drive.api.v1.auth.sso.usecase.SsoUseCase;
import com.thinkfree.drive.auth.cookie.AuthCookieManager;
import com.thinkfree.drive.sso.constants.SsoConstants;
import com.thinkfree.drive.user.enums.RoleType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Duration;

@Slf4j
@Tag(name = "SSO")
@RestController
@RequestMapping(SsoConstants.SSO_PATH)
@RequiredArgsConstructor
public class SsoController {
    
    private static final Duration SSO_SESSION_TOKEN_MAX_AGE = Duration.ofMinutes(1);

    private final SsoUseCase ssoUseCase;
    private final AuthCookieManager authCookieManager;

    @Operation(summary = "Start SSO sign-in",
            description = "Redirects to the identity provider's authorization page.")
    @GetMapping("/{ssoProviderSeq}/login")
    public void ssoLogin(@PathVariable Integer ssoProviderSeq, HttpServletResponse response) throws IOException {
        response.sendRedirect(ssoUseCase.authorizationUrl(ssoProviderSeq));
    }
    
    @Operation(summary = "Handle the OIDC callback",
            description = "Processes the identity provider's response, stores a short-lived session cookie, and redirects back to Drive.")
    @GetMapping(SsoConstants.Oidc.OIDC_CALLBACK_SUFFIX)
    public void callback(@ModelAttribute @Valid SsoOidcCallbackRequest request, HttpServletRequest httpRequest, HttpServletResponse response) throws IOException {
        var callbackResponse = ssoUseCase.handleOidcCallback(request);
        addHttpOnlyCookie(httpRequest, response, callbackResponse.token(), (int) SSO_SESSION_TOKEN_MAX_AGE.toSeconds());
        response.sendRedirect(callbackResponse.redirectUrl());
    }
    
    @Operation(summary = "Complete SSO sign-in",
            description = "Exchanges the SSO session cookie from the callback for a sign-in result.")
    @GetMapping("/session")
    public ResponseEntity<ApiResponse<SsoSessionResponse>> session(@CookieValue(value=SsoConstants.SSO_SESSION_TOKEN, required = false) String ssoSessionToken, HttpServletResponse httpResponse, HttpServletRequest httpRequest) {
        log.info("ssoSessionToken : {}", ssoSessionToken);
        clearSsoSessionCookie(httpRequest, httpResponse);
        var response = ssoUseCase.resolveSession(ssoSessionToken, httpRequest);
        if (response instanceof SsoSessionResponse.Login login) {
            authCookieManager.issue(httpRequest, httpResponse, RoleType.USER, login.accessToken(), login.refreshToken());
        }
        return ApiResponse.success(response);
        
    }
    
    @Deprecated
    @Operation(summary = "Complete SSO sign-up", deprecated = true)
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<SsoSignupResponse>> signupComplete(@Valid @RequestBody SsoSignupRequest request, HttpServletRequest httpServletRequest) {
        var result = ssoUseCase.signup(request, httpServletRequest);
        return ApiResponse.created(result);
    }
    
    @Operation(summary = "Sign in with a JWT",
            description = "Signs in using a JWT issued by a trusted external system.")
    @PostMapping("/jwt")
    public ResponseEntity<ApiResponse<SsoJwtLoginResponse>> jwtLogin(@Valid @RequestBody SsoJwtLoginRequest request,
                                                                     HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        // "이미 로그인" 판단용 — SSO 는 사용자 로그인이라 사용자 쿠키만 본다
        String driveAccessToken = AuthCookieManager.resolveAccessToken(httpRequest, RoleType.USER).orElse(null);
        SsoJwtLoginResponse response = ssoUseCase.jwtLogin(request.token(), driveAccessToken, httpRequest);
        if (response instanceof SsoJwtLoginResponse.Login login) {
            authCookieManager.issue(httpRequest, httpResponse, RoleType.USER, login.accessToken(), login.refreshToken());
        }
        return ApiResponse.success(response.httpStatus(), response);
    }

    private void clearSsoSessionCookie(HttpServletRequest request, HttpServletResponse response) {
        Cookie cookie = new Cookie(SsoConstants.SSO_SESSION_TOKEN, null);
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());
        // PATH 테넌트 모드에서는 contextPath 가 /acme 로 에뮬레이션됨 — 테넌트 간 쿠키 공유 방지 (SUBDOMAIN 모드는 "/")
        cookie.setPath(request.getContextPath() + "/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    private void addHttpOnlyCookie(HttpServletRequest request, HttpServletResponse response, String value, int maxAge) {
        Cookie cookie = new Cookie(SsoConstants.SSO_SESSION_TOKEN, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());
        // PATH 테넌트 모드에서는 contextPath 가 /acme 로 에뮬레이션됨 — 테넌트 간 쿠키 공유 방지 (SUBDOMAIN 모드는 "/")
        cookie.setPath(request.getContextPath() + "/");
        cookie.setMaxAge(maxAge);
        response.addCookie(cookie);
    }
}
