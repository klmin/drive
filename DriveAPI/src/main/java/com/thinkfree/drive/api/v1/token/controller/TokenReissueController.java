package com.thinkfree.drive.api.v1.token.controller;

import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.token.response.TokenResponse;
import com.thinkfree.drive.api.v1.token.usecase.TokenReissueUseCase;
import com.thinkfree.drive.auth.cookie.AuthCookieManager;
import com.thinkfree.drive.auth.exception.AuthErrorCode;
import com.thinkfree.drive.global.annotation.AnonymousAccessible;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.user.enums.RoleType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/token/reissue")
@Tag(name = "Authentication")
public class TokenReissueController {
    private final TokenReissueUseCase tokenReissueUseCase;
    private final AuthCookieManager authCookieManager;

    /** {@code X-Drive-Role} 로 고른 refresh 쿠키로 재발급하고(필터가 이미 검증), 같은 이름의 쿠키로 다시 내려준다 */
    @Operation(summary = "Refresh tokens",
            description = "Reissues tokens using the refresh cookie for the role selected by the X-Drive-Role header.")
    @AnonymousAccessible
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> reissueTokens(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                                    HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        RoleType cookieRole = AuthCookieManager.resolveCookieRole(httpRequest)
                .orElseThrow(() -> ApiRuntimeException.type(AuthErrorCode.AUTH_INVALID_TOKEN));
        String refreshToken = AuthCookieManager.resolveRefreshToken(httpRequest, cookieRole)
                .orElseThrow(() -> ApiRuntimeException.type(AuthErrorCode.AUTH_INVALID_TOKEN));

        TokenResponse response = tokenReissueUseCase.reissueTokens(userDetails.getUserSeq(), refreshToken, httpRequest);
        authCookieManager.issue(httpRequest, httpResponse, cookieRole, response.getAccessToken(), response.getRefreshToken());
        return ApiResponse.success();
    }
}
