package com.thinkfree.drive.mcp.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.thinkfree.drive.util.LogSafe;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * /mcp 요청 관측용 임시 필터 (인증 없음, 로깅만).
 *
 * 요청/응답 각각 로그 한 줄씩만 남긴다 — 바디가 있으면 그 줄에 포함하고,
 * 없으면 바디 없이 찍는다.
 *
 * 요청 바디를 진입 시점 로그에 포함해야 하므로 본문을 미리 읽어 캐시하는
 * 래퍼(CachedBodyRequestWrapper)로 감싼 뒤 체인에 넘긴다.
 *
 * 응답 본문은 버퍼링하면 안 된다 — POST 응답이 SSE 스트림일 수 있어
 * 모아뒀다 흘리면 클라이언트가 이벤트를 못 받는다. 실제 스트림에 그대로
 * 흘리면서 복사본만 떠두는 tee 방식으로 캡처한다. (GET은 장수명 SSE 대기
 * 스트림이라 캡처 제외)
 *
 * 인증 1단계 적용 시 이 필터 자리를 ApiKeyAuthenticationFilter로 교체한다.
 */
@Slf4j
@RequiredArgsConstructor
public class McpRequestLoggingFilter extends OncePerRequestFilter {

    private static final int MAX_RESPONSE_CAPTURE_BYTES = 8192;
    private static final int MAX_RESPONSE_LOG_LENGTH = 2000;

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            log.info("Request [MCP] {} {} | Accept={} | Mcp-Session-Id={} | Authorization={}",
                    request.getMethod(),
                    LogSafe.of(request.getRequestURI()),
                    LogSafe.of(request.getHeader(HttpHeaders.ACCEPT)),
                    LogSafe.of(request.getHeader("Mcp-Session-Id")),
                    authorization == null ? "(none)" : maskCredential(authorization));
            chain.doFilter(request, response);
            log.info("Response [MCP] {} {} -> status={}", request.getMethod(), LogSafe.of(request.getRequestURI()), response.getStatus());
            return;
        }

        CachedBodyRequestWrapper wrappedRequest = new CachedBodyRequestWrapper(request);
        byte[] requestBody = wrappedRequest.getBody();

        log.info("Request [MCP] POST {} | Accept={} | Mcp-Session-Id={} | Authorization={} | {}{}",
                LogSafe.of(request.getRequestURI()),
                LogSafe.of(request.getHeader(HttpHeaders.ACCEPT)),
                LogSafe.of(request.getHeader("Mcp-Session-Id")),
                authorization == null ? "(none)" : maskCredential(authorization),
                LogSafe.of(summarizeJsonRpc(requestBody)),
                requestBody.length == 0 ? "" : " | body=" + LogSafe.of(new String(requestBody, StandardCharsets.UTF_8)));

        TeeResponseWrapper teeResponse = new TeeResponseWrapper(response);

        chain.doFilter(wrappedRequest, teeResponse);

        if (wrappedRequest.isAsyncStarted()) {
            // SSE 등 비동기 응답 — 스트림이 다 흐른 뒤(onComplete)에 찍어야 본문이 온전하다
            wrappedRequest.getAsyncContext().addListener(new AsyncListener() {
                @Override
                public void onComplete(AsyncEvent event) {
                    logResponse(wrappedRequest, requestBody, teeResponse);
                }

                @Override
                public void onTimeout(AsyncEvent event) {
                    log.info("Response [MCP] POST {} | {} -> async timeout", LogSafe.of(wrappedRequest.getRequestURI()),
                            LogSafe.of(summarizeJsonRpc(requestBody)));
                }

                @Override
                public void onError(AsyncEvent event) {
                    log.info("Response [MCP] POST {} | {} -> async error: {}", LogSafe.of(wrappedRequest.getRequestURI()),
                            LogSafe.of(summarizeJsonRpc(requestBody)),
                            event.getThrowable() == null ? "(unknown)" : event.getThrowable().toString());
                }

                @Override
                public void onStartAsync(AsyncEvent event) {
                }
            });
            return;
        }

        logResponse(wrappedRequest, requestBody, teeResponse);
    }

    private void logResponse(HttpServletRequest request, byte[] requestBody, TeeResponseWrapper response) {
        // 세션은 initialize 응답 헤더로 발급되므로, 응답 헤더 우선으로 찍어 발급 순간이 보이게 한다
        String sessionId = response.getHeader("Mcp-Session-Id");
        if (sessionId == null) {
            sessionId = request.getHeader("Mcp-Session-Id");
        }
        String responseBody = response.getCapturedBody();
        log.info("Response [MCP] POST {} | {} -> status={} session={} | Content-Type={}{}",
                LogSafe.of(request.getRequestURI()),
                LogSafe.of(summarizeJsonRpc(requestBody)),
                response.getStatus(),
                LogSafe.of(sessionId),
                response.getContentType(),
                responseBody == null ? "" : " | body=" + LogSafe.of(responseBody));
    }

    private String summarizeJsonRpc(byte[] body) {
        if (body.length == 0) {
            return "(empty body)";
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            StringBuilder sb = new StringBuilder();
            sb.append("method=").append(node.path("method").asText("(none)"));
            sb.append(" id=").append(node.hasNonNull("id") ? node.get("id").asText() : "-");

            JsonNode toolName = node.path("params").path("name");
            if (!toolName.isMissingNode()) {
                sb.append(" tool=").append(toolName.asText());
            }
            return sb.toString();
        } catch (Exception e) {
            return "(unparseable body)";
        }
    }

    private String maskCredential(String authorization) {
        int visible = Math.min(authorization.length(), 15);
        return authorization.substring(0, visible) + "...";
    }

    /**
     * 요청 본문을 진입 시점에 전부 읽어 캐시하고, 다운스트림에는 복사본
     * 스트림을 흘려주는 요청 래퍼. MCP JSON-RPC 요청은 크기가 작아
     * 선읽기 부담이 없다.
     */
    private static class CachedBodyRequestWrapper extends HttpServletRequestWrapper {

        private final byte[] body;

        CachedBodyRequestWrapper(HttpServletRequest request) throws IOException {
            super(request);
            this.body = request.getInputStream().readAllBytes();
        }

        byte[] getBody() {
            return body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    try {
                        listener.onDataAvailable();
                        if (in.available() == 0) {
                            listener.onAllDataRead();
                        }
                    } catch (IOException e) {
                        listener.onError(e);
                    }
                }

                @Override
                public int read() {
                    return in.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return in.read(b, off, len);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding() != null ? getCharacterEncoding() : StandardCharsets.UTF_8.name();
            return new BufferedReader(new InputStreamReader(getInputStream(), Charset.forName(encoding)));
        }
    }

    /**
     * 실제 응답 스트림에 그대로 쓰면서 앞부분 복사본만 떠두는 응답 래퍼.
     * 버퍼링하지 않으므로 SSE 스트리밍을 방해하지 않는다.
     */
    private static class TeeResponseWrapper extends HttpServletResponseWrapper {

        private final ByteArrayOutputStream capture = new ByteArrayOutputStream();
        private ServletOutputStream outputStream;
        private PrintWriter writer;

        TeeResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (outputStream == null) {
                ServletOutputStream real = super.getOutputStream();
                outputStream = new ServletOutputStream() {
                    @Override
                    public boolean isReady() {
                        return real.isReady();
                    }

                    @Override
                    public void setWriteListener(WriteListener writeListener) {
                        real.setWriteListener(writeListener);
                    }

                    @Override
                    public void write(int b) throws IOException {
                        real.write(b);
                        if (capture.size() < MAX_RESPONSE_CAPTURE_BYTES) {
                            capture.write(b);
                        }
                    }

                    @Override
                    public void write(byte[] b, int off, int len) throws IOException {
                        real.write(b, off, len);
                        int remain = MAX_RESPONSE_CAPTURE_BYTES - capture.size();
                        if (remain > 0) {
                            capture.write(b, off, Math.min(len, remain));
                        }
                    }

                    @Override
                    public void flush() throws IOException {
                        real.flush();
                    }

                    @Override
                    public void close() throws IOException {
                        real.close();
                    }
                };
            }
            return outputStream;
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (writer == null) {
                String encoding = getCharacterEncoding() != null ? getCharacterEncoding() : StandardCharsets.UTF_8.name();
                writer = new PrintWriter(new OutputStreamWriter(getOutputStream(), encoding), true);
            }
            return writer;
        }

        /** 캡처된 본문이 없으면 null — 호출부에서 body 세그먼트를 통째로 생략한다. */
        String getCapturedBody() {
            if (capture.size() == 0) {
                return null;
            }
            String body = capture.toString(StandardCharsets.UTF_8).replace("\r", "").replace("\n", "\\n");
            if (body.length() > MAX_RESPONSE_LOG_LENGTH) {
                return body.substring(0, MAX_RESPONSE_LOG_LENGTH) + "...(truncated)";
            }
            return body;
        }
    }
}
