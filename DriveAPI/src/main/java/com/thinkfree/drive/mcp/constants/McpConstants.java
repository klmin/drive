package com.thinkfree.drive.mcp.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** MCP 리소스 서버(RS) 상수 — 엔드포인트·메타데이터 경로. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class McpConstants {

    /** MCP 엔드포인트 (spring.ai.mcp.server.streamable-http.mcp-endpoint와 일치) */
    public static final String MCP_ENDPOINT = "/mcp";

    /** RFC 9728 Protected Resource Metadata 경로 — /mcp를 보호 자원으로 선언 */
    public static final String PROTECTED_RESOURCE_METADATA_PATH = "/.well-known/oauth-protected-resource";

    /**
     * RFC 9728 §3.1 경로 삽입형 — 보호 자원이 경로(/mcp)를 가지면 well-known을
     * host와 경로 사이에 삽입한 URL이 정식이다. 최신 MCP 클라이언트는 이 형태를
     * 우선 조회하므로 루트형과 함께 둘 다 매핑한다.
     */
    public static final String PROTECTED_RESOURCE_METADATA_MCP_PATH =
            PROTECTED_RESOURCE_METADATA_PATH + MCP_ENDPOINT;

    /**
     * 인증 실패가 API Key(비 JWT 불투명 문자열) 경로였음을 필터가 엔트리포인트로
     * 전달하는 request attribute 키. 필터는 실패 시 응답을 쓰지 않고 체인을 계속
     * 진행하는 설계라 예외로는 원인이 전달되지 않는다 — 같은 request 객체를 통로로 쓴다.
     */
    public static final String AUTH_FAILURE_API_KEY_ATTR =
            McpConstants.class.getName() + ".AUTH_FAILURE_API_KEY";
}
