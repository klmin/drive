package com.thinkfree.drive.api.v1.auth.controller;

import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.auth.request.AnonymousLoginRequest;
import com.thinkfree.drive.api.v1.auth.request.EntryKeyVerifyRequest;
import com.thinkfree.drive.api.v1.auth.request.LoginRequest;
import com.thinkfree.drive.api.v1.auth.response.AnonymousLoginResponse;
import com.thinkfree.drive.api.v1.auth.response.AuthLoginResponse;
import com.thinkfree.drive.api.v1.auth.response.LoginConfigResponse;
import com.thinkfree.drive.api.v1.auth.response.LoginResponse;
import com.thinkfree.drive.api.v1.auth.usecase.AuthUseCase;
import com.thinkfree.drive.auth.cookie.AuthCookieManager;
import com.thinkfree.drive.global.ratelimit.LoginRateLimitGuard;
import com.thinkfree.drive.user.enums.RoleType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {
    
    private final AuthUseCase authUseCase;
    private final AuthCookieManager authCookieManager;
    /** 비밀번호 비교·감사 로그 전에 요청 수를 막는다(관리자·슈퍼관리자 화면은 계정 잠금이 없다) */
    private final LoginRateLimitGuard loginRateLimitGuard;

    // 토큰은 Set-Cookie 로만 내려준다(응답 body 에는 없음)

    @Operation(summary = "Sign in",
            description = "Signs in a user. Tokens are returned only as Set-Cookie headers.")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthLoginResponse>> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        loginRateLimitGuard.checkLogin(RoleType.USER, request.getUserId(), httpRequest);
        AuthLoginResponse response = authUseCase.login(request, httpRequest);
        if (response instanceof LoginResponse login) {
            authCookieManager.issue(httpRequest, httpResponse, RoleType.USER, login.getAccessToken(), login.getRefreshToken());
        }
        return ApiResponse.success(response.httpStatus(), response);
    }

    @Operation(summary = "Sign in as an administrator",
            description = "Signs in to the tenant administrator console. Tokens are returned only as Set-Cookie headers.")
    @PostMapping("/admin/login")
    public ResponseEntity<ApiResponse<LoginResponse>> adminLogin(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        loginRateLimitGuard.checkLogin(RoleType.ADMIN, request.getUserId(), httpRequest);
        LoginResponse response = authUseCase.adminLogin(request, httpRequest);
        authCookieManager.issue(httpRequest, httpResponse, RoleType.ADMIN, response.getAccessToken(), response.getRefreshToken());
        return ApiResponse.success(response);
    }

    @Operation(summary = "Sign in as a super administrator",
            description = "Signs in to the super administrator console. Tokens are returned only as Set-Cookie headers.")
    @PostMapping("/super-admin/login")
    public ResponseEntity<ApiResponse<LoginResponse>> superAdminLogin(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        loginRateLimitGuard.checkLogin(RoleType.SUPER_ADMIN, request.getUserId(), httpRequest);
        LoginResponse response = authUseCase.superAdminLogin(request, httpRequest);
        authCookieManager.issue(httpRequest, httpResponse, RoleType.SUPER_ADMIN, response.getAccessToken(), response.getRefreshToken());
        return ApiResponse.success(response);
    }

    @Operation(summary = "Sign in anonymously",
            description = "Issues an anonymous session from a public share link token. No authentication required.")
    @PostMapping("/anonymous/login")
    public ResponseEntity<ApiResponse<AnonymousLoginResponse>> anonymousLogin(@Valid @RequestBody AnonymousLoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        loginRateLimitGuard.checkAnonymousLogin(httpRequest);
        AnonymousLoginResponse response = authUseCase.anonymousLogin(request.getLinkToken());
        authCookieManager.issue(httpRequest, httpResponse, RoleType.ANONYMOUS, response.getAccessToken(), response.getRefreshToken());
        return ApiResponse.success(response);
    }

    /**
     * 인증 없이 받는다 — {@code X-Drive-Role} 로 고른 역할의 access·refresh 쿠키를 항상 지우고,
     * access 토큰이 유효하면 블랙리스트 등록·refresh 삭제까지 한다. 다른 역할·테넌트의 쿠키는 남는다.
     */
    @Operation(summary = "Sign out",
            description = "Clears the sign-in cookies for the role selected by the X-Drive-Role header.")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        AuthCookieManager.resolveCookieRole(httpRequest).ifPresent(cookieRole -> {
            String accessToken = AuthCookieManager.resolveAccessToken(httpRequest, cookieRole).orElse(null);
            authUseCase.logout(accessToken, httpRequest);
            authCookieManager.expire(httpRequest, httpResponse, cookieRole);
        });
        return ApiResponse.success();
    }
    
    @Operation(summary = "Get sign-in options",
            description = "Returns the sign-in methods available to the current tenant.")
    @GetMapping("/login-config")
    public ResponseEntity<ApiResponse<LoginConfigResponse>> loginConfig() {
        LoginConfigResponse response = authUseCase.loginConfig();
        return ApiResponse.success(response);
    }

    @Operation(summary = "Verify a tenant entry key",
            description = "Verifies an entry key issued by a super administrator and signs in to the tenant administrator console. No authentication required.")
    @PostMapping("/admin/entry-key/verify")
    public ResponseEntity<ApiResponse<LoginResponse>> verifyAdminEntryKey(@Valid @RequestBody EntryKeyVerifyRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        LoginResponse response = authUseCase.loginWithEntryKey(request.getToken(), httpRequest);
        // 슈퍼관리자 토큰을 관리자 화면에서 쓰므로 그 테넌트 경로의 ADMIN 쿠키로 발급
        authCookieManager.issue(httpRequest, httpResponse, RoleType.ADMIN, response.getAccessToken(), response.getRefreshToken());
        return ApiResponse.success(response);
    }

}
