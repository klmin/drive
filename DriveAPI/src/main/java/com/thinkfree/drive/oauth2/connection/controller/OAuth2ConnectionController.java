package com.thinkfree.drive.oauth2.connection.controller;

import com.thinkfree.drive.global.annotation.ParameterDoc;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.oauth2.connection.response.OAuth2ConnectionResponse;
import com.thinkfree.drive.oauth2.connection.usecase.OAuth2ConnectionUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 연동 앱 관리 API — 로그인 사용자가 자신의 OAuth 연결(MCP 클라이언트 등)을 조회·해제한다.
 * /api/v1 경로라 기존 /api 시큐리티 체인(Drive JWT 인증)을 그대로 탄다.
 * 해제는 connection 행 + SAS 인가(refresh)·동의 행을 지우며, 기발급 access 토큰은
 * /mcp 필터의 연결 검증이 즉시 차단한다 (MCP_OAUTH_CONNECTIONS_HANDOFF.md).
 */
@RestController
@RequestMapping("/api/v1/oauth2/connections")
@RequiredArgsConstructor
@Tag(name = "Connected apps")
public class OAuth2ConnectionController {

    private final OAuth2ConnectionUseCase oauth2ConnectionUseCase;

    @ExternalApi
    @Operation(summary = "List your OAuth connections",
            description = "Returns the external OAuth connections linked to the signed-in user's account.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<OAuth2ConnectionResponse>>> findConnections(@AuthenticationPrincipal CustomUserDetails details) {
        return ApiResponse.success(oauth2ConnectionUseCase.findConnections(details.getUserSeq()));
    }

    @ExternalApi
    @Operation(summary = "Disconnect an OAuth connection",
            description = "Removes the selected external OAuth connection from the signed-in user's account.")
    @DeleteMapping("/{connectionSeq}")
    public ResponseEntity<ApiResponse<Void>> disconnect(@ParameterDoc(title = "OAuth connection ID", description = "Numeric connectionSeq returned by the OAuth connection list.") @PathVariable Long connectionSeq,
                                                        @AuthenticationPrincipal CustomUserDetails details) {
        oauth2ConnectionUseCase.disconnect(connectionSeq, details.getUserSeq());
        return ApiResponse.success();
    }
}
