package com.thinkfree.drive.mcp.security;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * /mcp 요청 관측용 필터 (인증 없음, 로깅만).
 *
 * 요청/응답 각각 로그 한 줄씩만 남긴다. 바디는 업로드 파일 내용·문서 본문 같은 민감정보를 담을 수 있어
 * DEBUG 레벨에서만 그 줄에 포함하고(INFO 는 JSON-RPC 메서드 요약만),
 * 없으면 바디 없이 찍는다.
 *
 * 요청 본문은 미리 읽지 않는다 — 이 필터는 인증 필터 앞이라, 미리 다 읽으면 인증 없는 큰 요청으로 힙을 쓸 수 있다.
 * 다운스트림(인증 뒤 MCP 전송)이 읽는 대로 앞부분 {@value #MAX_REQUEST_CAPTURE_BYTES} 바이트만 복사해 두고,
 * JSON-RPC 요약(method·id·tool)은 응답 로그에서 그 복사본으로 만든다. 인증에서 거절되면 본문을 읽지 않는다.
 *
 * 응답 본문은 버퍼링하면 안 된다 — POST 응답이 SSE 스트림일 수 있어
 * 모아뒀다 흘리면 클라이언트가 이벤트를 못 받는다. 실제 스트림에 그대로
 * 흘리면서 복사본만 떠두는 tee 방식으로 캡처한다. (GET은 장수명 SSE 대기
 * 스트림이라 캡처 제외)
 */
@Slf4j
@RequiredArgsConstructor
public class McpRequestLoggingFilter extends OncePerRequestFilter {

    static final int MAX_REQUEST_CAPTURE_BYTES = 64 * 1024;
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
                    authorization == null ? "(none)" : LogSafe.of(maskCredential(authorization)));
            chain.doFilter(request, response);
            log.info("Response [MCP] {} {} -> status={}", request.getMethod(), LogSafe.of(request.getRequestURI()), response.getStatus());
            return;
        }

        CapturingRequestWrapper wrappedRequest = new CapturingRequestWrapper(request, MAX_REQUEST_CAPTURE_BYTES);

        log.info("Request [MCP] POST {} | Accept={} | Mcp-Session-Id={} | Authorization={} | Content-Length={}",
                LogSafe.of(request.getRequestURI()),
                LogSafe.of(request.getHeader(HttpHeaders.ACCEPT)),
                LogSafe.of(request.getHeader("Mcp-Session-Id")),
                authorization == null ? "(none)" : LogSafe.of(maskCredential(authorization)),
                request.getContentLengthLong());

        TeeResponseWrapper teeResponse = new TeeResponseWrapper(response);

        chain.doFilter(wrappedRequest, teeResponse);

        if (wrappedRequest.isAsyncStarted()) {
            // SSE 등 비동기 응답 — 스트림이 다 흐른 뒤(onComplete)에 찍어야 본문이 온전하다
            wrappedRequest.getAsyncContext().addListener(new AsyncListener() {
                @Override
                public void onComplete(AsyncEvent event) {
                    logResponse(wrappedRequest, teeResponse);
                }

                @Override
                public void onTimeout(AsyncEvent event) {
                    log.info("Response [MCP] POST {} | {} -> async timeout", LogSafe.of(wrappedRequest.getRequestURI()),
                            LogSafe.of(summarize(wrappedRequest)));
                }

                @Override
                public void onError(AsyncEvent event) {
                    log.info("Response [MCP] POST {} | {} -> async error: {}", LogSafe.of(wrappedRequest.getRequestURI()),
                            LogSafe.of(summarize(wrappedRequest)),
                            event.getThrowable() == null ? "(unknown)" : event.getThrowable().toString());
                }

                @Override
                public void onStartAsync(AsyncEvent event) {
                }
            });
            return;
        }

        logResponse(wrappedRequest, teeResponse);
    }

    private void logResponse(CapturingRequestWrapper request, TeeResponseWrapper response) {
        // 세션은 initialize 응답 헤더로 발급되므로, 응답 헤더 우선으로 찍어 발급 순간이 보이게 한다
        String sessionId = response.getHeader("Mcp-Session-Id");
        if (sessionId == null) {
            sessionId = request.getHeader("Mcp-Session-Id");
        }
        String responseBody = response.getCapturedBody();
        String requestBody = log.isDebugEnabled() ? request.capturedBodyForLog() : null;
        log.info("Response [MCP] POST {} | {} -> status={} session={} | Content-Type={}{}{}",
                LogSafe.of(request.getRequestURI()),
                LogSafe.of(summarize(request)),
                response.getStatus(),
                LogSafe.of(sessionId),
                response.getContentType(),
                requestBody == null ? "" : " | requestBody=" + LogSafe.of(requestBody),
                responseBody == null || !log.isDebugEnabled() ? "" : " | body=" + LogSafe.of(responseBody));
    }

    private String summarize(CapturingRequestWrapper request) {
        if (!request.isBodyRead()) {
            return "(body not read)"; // 인증 거절 등 다운스트림이 본문을 읽기 전에 끝남
        }
        return summarizeJsonRpc(request.capturedBody(), request.isTruncated());
    }

    /**
     * JSON-RPC 요약 — 앞부분만 잘린 본문일 수 있어 스트리밍으로 읽고, 중간에 끊겨도 그때까지 찾은 값으로 만든다.
     */
    String summarizeJsonRpc(byte[] body, boolean truncated) {
        if (body.length == 0) {
            return "(empty body)";
        }
        String method = null;
        String id = null;
        String[] tool = new String[1]; // params 안에서 찾자마자 담는다 — 그 뒤에서 잘려도 남게
        boolean complete = false;
        try (JsonParser parser = objectMapper.getFactory().createParser(body)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                return "(unparseable body)";
            }
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String field = parser.currentName();
                JsonToken value = parser.nextToken();
                switch (field) {
                    case "method" -> method = value.isScalarValue() ? parser.getValueAsString() : skip(parser);
                    case "id" -> id = value.isScalarValue() && value != JsonToken.VALUE_NULL ? parser.getValueAsString() : skip(parser);
                    case "params" -> {
                        if (value == JsonToken.START_OBJECT) {
                            findToolName(parser, tool);
                        } else {
                            parser.skipChildren();
                        }
                    }
                    default -> parser.skipChildren();
                }
            }
            complete = true;
        } catch (Exception e) {
            // 잘린 본문 — 찾은 값까지만
        }
        if (!complete && method == null && id == null) {
            return truncated ? "(truncated body)" : "(unparseable body)";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("method=").append(method == null ? "(none)" : method);
        sb.append(" id=").append(id == null ? "-" : id);
        if (tool[0] != null) {
            sb.append(" tool=").append(tool[0]);
        }
        return sb.toString();
    }

    private static void findToolName(JsonParser parser, String[] tool) throws IOException {
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String field = parser.currentName();
            JsonToken value = parser.nextToken();
            if ("name".equals(field) && value.isScalarValue()) {
                tool[0] = parser.getValueAsString();
            } else {
                parser.skipChildren();
            }
        }
    }

    private static String skip(JsonParser parser) throws IOException {
        parser.skipChildren();
        return null;
    }

    private String maskCredential(String authorization) {
        int visible = Math.min(authorization.length(), 15);
        return authorization.substring(0, visible) + "...";
    }

    /**
     * 요청 본문을 미리 읽지 않고, 다운스트림이 읽는 대로 앞부분 {@code limit} 바이트만 복사해 두는 요청 래퍼.
     * 메모리에 담는 양은 본문 크기와 무관하게 {@code limit} 으로 묶인다.
     */
    static class CapturingRequestWrapper extends HttpServletRequestWrapper {

        private final int limit;
        private final ByteArrayOutputStream capture = new ByteArrayOutputStream();
        private long totalRead;
        private ServletInputStream stream;
        private BufferedReader reader;

        CapturingRequestWrapper(HttpServletRequest request, int limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                ServletInputStream real = super.getInputStream();
                stream = new ServletInputStream() {
                    @Override
                    public boolean isFinished() {
                        return real.isFinished();
                    }

                    @Override
                    public boolean isReady() {
                        return real.isReady();
                    }

                    @Override
                    public void setReadListener(ReadListener listener) {
                        real.setReadListener(listener);
                    }

                    @Override
                    public int read() throws IOException {
                        int b = real.read();
                        if (b >= 0) {
                            record(b);
                        }
                        return b;
                    }

                    @Override
                    public int read(byte[] b, int off, int len) throws IOException {
                        int n = real.read(b, off, len);
                        if (n > 0) {
                            record(b, off, n);
                        }
                        return n;
                    }
                };
            }
            return stream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            if (reader == null) {
                String encoding = getCharacterEncoding() != null ? getCharacterEncoding() : StandardCharsets.UTF_8.name();
                reader = new BufferedReader(new InputStreamReader(getInputStream(), Charset.forName(encoding)));
            }
            return reader;
        }

        private synchronized void record(int b) {
            totalRead++;
            if (capture.size() < limit) {
                capture.write(b);
            }
        }

        private synchronized void record(byte[] b, int off, int len) {
            totalRead += len;
            int remain = limit - capture.size();
            if (remain > 0) {
                capture.write(b, off, Math.min(len, remain));
            }
        }

        boolean isBodyRead() {
            return stream != null;
        }

        synchronized byte[] capturedBody() {
            return capture.toByteArray();
        }

        synchronized boolean isTruncated() {
            return totalRead > limit;
        }

        /** DEBUG 로그용 — 없으면 null */
        synchronized String capturedBodyForLog() {
            if (capture.size() == 0) {
                return null;
            }
            String body = capture.toString(StandardCharsets.UTF_8);
            return isTruncated() ? body + "...(truncated, total " + totalRead + " bytes)" : body;
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
