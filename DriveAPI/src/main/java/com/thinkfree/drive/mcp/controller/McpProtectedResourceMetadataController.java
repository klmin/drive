package com.thinkfree.drive.mcp.controller;

import com.thinkfree.drive.mcp.constants.McpConstants;
import com.thinkfree.drive.util.LogSafe;
import com.thinkfree.drive.oauth2.enums.OAuth2Scope;
import com.thinkfree.drive.mcp.response.McpProtectedResourceMetadataResponse;
import com.thinkfree.drive.mcp.util.McpUrls;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * RFC 9728 OAuth Protected Resource Metadata — MCP RS의 자기 선언.
 *
 * MCP 클라이언트는 /mcp 401 응답의 WWW-Authenticate 헤더에서 이 엔드포인트
 * URL을 얻어 조회하고, authorization_servers에서 인가 서버를 발견해
 * OAuth 플로우를 이어간다. SAS/Spring Security가 만들어주지 않으므로 직접 노출.
 *
 * 인가 서버(AS)는 Drive 서버에 내장(oauth2 패키지, Spring Authorization Server)이라
 * authorization_servers는 이 서버 자신의 베이스 URL을 가리킨다.
 *
 * 어떤 시큐리티 체인의 matcher에도 걸리지 않는 경로라 인증 없이 접근 가능
 * (스펙상 익명 조회가 되어야 한다).
 */
@Slf4j
@RestController
public class McpProtectedResourceMetadataController {

    private static final List<String> BEARER_METHODS_SUPPORTED = List.of("header");

    /** scope 광고 — 클라이언트가 authorize 요청에 scope를 싣게 유도 (동의 화면이 scope 단위라 필요) */
    private static final List<String> SCOPES_SUPPORTED = OAuth2Scope.supportedValues();

    @GetMapping({McpConstants.PROTECTED_RESOURCE_METADATA_PATH, McpConstants.PROTECTED_RESOURCE_METADATA_MCP_PATH})
    public McpProtectedResourceMetadataResponse protectedResourceMetadata(HttpServletRequest request) {
        String baseUrl = McpUrls.baseUrl(request);
        log.info("[McpProtectedResourceMetadata] baseUrl : {}", LogSafe.of(baseUrl));
        return new McpProtectedResourceMetadataResponse(
                baseUrl + McpConstants.MCP_ENDPOINT,
                List.of(baseUrl),
                BEARER_METHODS_SUPPORTED,
                SCOPES_SUPPORTED
        );
    }
}
