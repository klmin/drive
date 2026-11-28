package com.thinkfree.drive.mcp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** /mcp 로깅 필터 — 인증 전 본문을 미리 읽지 않고, 담는 양을 상한으로 묶는다. */
@DisplayName("McpRequestLoggingFilter — 요청 본문 캡처 상한")
class McpRequestLoggingFilterTest {

    private final McpRequestLoggingFilter filter = new McpRequestLoggingFilter(new ObjectMapper());

    private static MockHttpServletRequest post(byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.setContentType("application/json");
        request.setContent(body);
        return request;
    }

    /** 앞에 JSON-RPC 머리, 뒤에 긴 인자 — 업로드 툴 같은 큰 요청 */
    private static byte[] largeToolCall(int payloadBytes) {
        String head = "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\"drive_upload_file\",\"arguments\":{\"content\":\"";
        return (head + "A".repeat(payloadBytes) + "\"}}}").getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("다운스트림은 본문 전체를 그대로 받고, 필터가 담는 양은 상한까지만")
    void downstreamGetsFullBodyWhileCaptureIsBounded() throws Exception {
        byte[] body = largeToolCall(2 * 1024 * 1024);
        AtomicReference<byte[]> received = new AtomicReference<>();
        AtomicReference<McpRequestLoggingFilter.CapturingRequestWrapper> wrapper = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            received.set(req.getInputStream().readAllBytes());
            wrapper.set((McpRequestLoggingFilter.CapturingRequestWrapper) req);
        };

        filter.doFilter(post(body), new MockHttpServletResponse(), chain);

        assertThat(received.get()).isEqualTo(body);
        assertThat(wrapper.get().capturedBody()).hasSize(McpRequestLoggingFilter.MAX_REQUEST_CAPTURE_BYTES);
        assertThat(wrapper.get().isTruncated()).isTrue();
        assertThat(filter.summarizeJsonRpc(wrapper.get().capturedBody(), true))
                .isEqualTo("method=tools/call id=7 tool=drive_upload_file");
    }

    @Test
    @DisplayName("인증에서 거절돼 다운스트림이 읽지 않으면 본문을 한 바이트도 읽지 않는다")
    void bodyNotReadWhenChainDoesNotRead() throws Exception {
        MockHttpServletRequest request = post(largeToolCall(1024 * 1024));
        AtomicReference<ServletRequest> seen = new AtomicReference<>();
        FilterChain rejecting = (req, res) -> seen.set(req); // 401 처럼 본문을 안 읽고 끝남

        filter.doFilter(request, new MockHttpServletResponse(), rejecting);

        McpRequestLoggingFilter.CapturingRequestWrapper wrapper = (McpRequestLoggingFilter.CapturingRequestWrapper) seen.get();
        assertThat(wrapper.isBodyRead()).isFalse();
        assertThat(wrapper.capturedBody()).isEmpty();
        assertThat(request.getInputStream().available()).as("원본 스트림이 그대로 남음").isEqualTo(request.getContentLength());
    }

    @Test
    @DisplayName("getReader 로 읽어도 다운스트림은 전체, 캡처는 상한")
    void readerPathIsCapturedToo() throws Exception {
        byte[] body = largeToolCall(200 * 1024);
        AtomicReference<String> received = new AtomicReference<>();
        AtomicReference<McpRequestLoggingFilter.CapturingRequestWrapper> wrapper = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = req.getReader().read(buf)) > 0) {
                sb.append(buf, 0, n);
            }
            received.set(sb.toString());
            wrapper.set((McpRequestLoggingFilter.CapturingRequestWrapper) req);
        };

        filter.doFilter(post(body), new MockHttpServletResponse(), chain);

        assertThat(received.get()).isEqualTo(new String(body, StandardCharsets.UTF_8));
        assertThat(wrapper.get().capturedBody()).hasSize(McpRequestLoggingFilter.MAX_REQUEST_CAPTURE_BYTES);
    }

    @Test
    @DisplayName("JSON-RPC 요약 — 온전한 본문·잘린 본문·깨진 본문")
    void summarize() {
        byte[] full = "{\"jsonrpc\":\"2.0\",\"id\":\"a1\",\"method\":\"tools/call\",\"params\":{\"arguments\":{\"x\":[1,2]},\"name\":\"drive_search\"}}"
                .getBytes(StandardCharsets.UTF_8);
        assertThat(filter.summarizeJsonRpc(full, false)).isEqualTo("method=tools/call id=a1 tool=drive_search");

        byte[] notification = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}".getBytes(StandardCharsets.UTF_8);
        assertThat(filter.summarizeJsonRpc(notification, false)).isEqualTo("method=notifications/initialized id=-");

        byte[] cutBeforeMethod = "{\"jsonrpc\":\"2.0\",\"params\":{\"arguments\":{\"content\":\"AAAA".getBytes(StandardCharsets.UTF_8);
        assertThat(filter.summarizeJsonRpc(cutBeforeMethod, true)).isEqualTo("(truncated body)");

        assertThat(filter.summarizeJsonRpc("not json".getBytes(StandardCharsets.UTF_8), false)).isEqualTo("(unparseable body)");
        assertThat(filter.summarizeJsonRpc(new byte[0], false)).isEqualTo("(empty body)");
    }
}
