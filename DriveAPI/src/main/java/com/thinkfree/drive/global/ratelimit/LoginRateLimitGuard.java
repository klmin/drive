package com.thinkfree.drive.global.ratelimit;

import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.util.IpUtil;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 로그인 전 화면의 요청 수 제한 — 비밀번호를 비교하기 전에, 감사 로그를 남기기 전에(컨트롤러) 막는다.
 * <ul>
 *   <li>키에 테넌트를 넣는다(슈퍼관리자 화면은 {@code sa}) — 한 테넌트의 공격이 다른 테넌트 로그인을 막지 않게</li>
 *   <li>아이디는 소문자·공백 제거 뒤 SHA-256 앞 16바이트로 — Redis 에 아이디 원문을 남기지 않는다</li>
 *   <li>IP 는 {@link IpUtil}(신뢰 프록시 기준 클라이언트 IP)</li>
 * </ul>
 * 지표 {@code drive_rate_limit_rejected_total{rule}}.
 */
@Component
public class LoginRateLimitGuard {

    static final Duration PERIOD = Duration.ofMinutes(1);

    private final RateLimiter rateLimiter;
    private final RateLimitProperty property;
    private final MeterRegistry meterRegistry;
    private final RateLimitRule perAccount;
    private final RateLimitRule perIp;
    private final RateLimitRule anonymousPerIp;

    public LoginRateLimitGuard(RateLimiter rateLimiter, RateLimitProperty property, MeterRegistry meterRegistry) {
        this.rateLimiter = rateLimiter;
        this.property = property;
        this.meterRegistry = meterRegistry;
        this.perAccount = new RateLimitRule("login-account", property.loginPerAccount(), PERIOD);
        this.perIp = new RateLimitRule("login-ip", property.loginPerIp(), PERIOD);
        this.anonymousPerIp = new RateLimitRule("anonymous-login-ip", property.anonymousLoginPerIp(), PERIOD);
    }

    /** 아이디·비밀번호 로그인(사용자·관리자·슈퍼관리자) — IP 먼저, 그다음 아이디 */
    public void checkLogin(RoleType screen, String userId, HttpServletRequest request) {
        if (!property.enabled()) {
            return;
        }
        String scope = scope(screen);
        consume(perIp, scope + ":" + IpUtil.extractClientIp(request));
        consume(perAccount, scope + ":" + screen.name() + ":" + accountKey(userId));
    }

    /** 공유 링크 익명 로그인 — IP 만 */
    public void checkAnonymousLogin(HttpServletRequest request) {
        if (!property.enabled()) {
            return;
        }
        consume(anonymousPerIp, scope(RoleType.ANONYMOUS) + ":" + IpUtil.extractClientIp(request));
    }

    private void consume(RateLimitRule rule, String key) {
        Duration wait = rateLimiter.tryConsume(key, rule);
        if (!wait.isZero()) {
            meterRegistry.counter("drive.rate.limit.rejected", "rule", rule.name()).increment();
            throw new RateLimitExceededException(rule.name(), wait);
        }
    }

    private static String scope(RoleType screen) {
        if (screen == RoleType.SUPER_ADMIN) {
            return "sa";
        }
        Integer tenantSeq = TenantContext.get();
        return tenantSeq == null ? "none" : String.valueOf(tenantSeq);
    }

    static String accountKey(String userId) {
        String normalized = userId == null ? "" : userId.trim().toLowerCase(Locale.ROOT);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
