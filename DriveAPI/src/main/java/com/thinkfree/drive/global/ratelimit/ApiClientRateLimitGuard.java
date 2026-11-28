package com.thinkfree.drive.global.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfree.drive.global.exception.ApiExceptionResponse;
import com.thinkfree.drive.global.exception.GlobalErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 프로그램 호출(API Key·MCP OAuth 연동)의 요청 수 제한 — 키(연동) 하나당 분당 {@code apiKeyPerKey}.
 * <ul>
 *   <li>인증 필터에서 키를 조회한 직후, 대상 계정 조회·마지막 사용일 기록 전에 센다 — 넘은 요청은 DB 를 더 쓰지 않는다</li>
 *   <li>같은 키는 외부 체인(/api/public)·내부 체인·/mcp 에서 한 버킷을 나눠 쓴다</li>
 *   <li>키는 서명 검증·DB 조회로 확인한 값만 쓴다(요청 헤더 원문을 키로 쓰지 않음) — 아무 문자열로 버킷을 만들어 내지 못하게</li>
 * </ul>
 * 필터는 DispatcherServlet 앞이라 전역 예외 처리기를 거치지 않으므로 429 응답은 {@link #writeRejected} 로 직접 쓴다.
 * 지표 {@code drive_rate_limit_rejected_total{rule}}.
 */
@Slf4j
@Component
public class ApiClientRateLimitGuard {

    static final Duration PERIOD = Duration.ofMinutes(1);

    private final RateLimiter rateLimiter;
    private final RateLimitProperty property;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    private final RateLimitRule perApiKey;
    private final RateLimitRule perMcpOAuthClient;

    public ApiClientRateLimitGuard(RateLimiter rateLimiter, RateLimitProperty property, MeterRegistry meterRegistry, ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.property = property;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
        this.perApiKey = new RateLimitRule("api-key", property.apiKeyPerKey(), PERIOD);
        this.perMcpOAuthClient = new RateLimitRule("mcp-oauth", property.apiKeyPerKey(), PERIOD);
    }

    /** DB 에서 찾은 API Key — 테넌트 + 키 번호. 슈퍼관리자 키(테넌트 없음)는 {@code sa} */
    public void checkApiKey(Integer tenantSeq, int apiKeySeq) {
        if (!property.enabled()) {
            return;
        }
        consume(perApiKey, (tenantSeq == null ? "sa" : tenantSeq) + ":" + apiKeySeq);
    }

    /** 설정 파일의 슈퍼관리자 고정 키 — 하나뿐이라 고정 키 */
    public void checkStaticSuperAdminKey() {
        if (!property.enabled()) {
            return;
        }
        consume(perApiKey, "sa:static");
    }

    /** /mcp OAuth 토큰 — 서명을 검증한 토큰의 사용자 + 클라이언트(연동 하나) */
    public void checkMcpOAuth(Integer tenantSeq, int userSeq, String clientId) {
        if (!property.enabled()) {
            return;
        }
        consume(perMcpOAuthClient, (tenantSeq == null ? "none" : tenantSeq) + ":" + userSeq + ":" + clientId);
    }

    /** 필터에서 429 + {@code Retry-After} 응답을 쓴다 — 전역 예외 처리기와 같은 본문(REQUEST_013) */
    public void writeRejected(HttpServletResponse response, RateLimitExceededException e) throws IOException {
        log.warn("rate limit exceeded: rule={}, retryAfter={}s", e.getRule(), e.getRetryAfterSeconds());
        response.setStatus(GlobalErrorCode.TOO_MANY_REQUESTS.getStatus().value());
        response.setHeader("Retry-After", String.valueOf(e.getRetryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(ApiExceptionResponse.of(GlobalErrorCode.TOO_MANY_REQUESTS)));
    }

    private void consume(RateLimitRule rule, String key) {
        Duration wait = rateLimiter.tryConsume(key, rule);
        if (!wait.isZero()) {
            meterRegistry.counter("drive.rate.limit.rejected", "rule", rule.name()).increment();
            throw new RateLimitExceededException(rule.name(), wait);
        }
    }
}
