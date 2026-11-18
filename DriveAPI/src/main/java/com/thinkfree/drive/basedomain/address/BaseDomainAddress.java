package com.thinkfree.drive.basedomain.address;

import com.thinkfree.drive.basedomain.exception.BaseDomainErrorCode;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 베이스 도메인 입력값 {@code [scheme://]host[:port][/]} 를 스킴·호스트·포트로 나눈 값.
 * <p>
 * 파싱 단계는 <b>모양</b>만 본다 — 스킴은 http·https, 포트는 1~65535, 경로·쿼리·프래그먼트·userinfo·IPv6 리터럴은 거부.
 * 호스트가 어떤 종류(도메인·localhost·IPv4)여야 하는지는 테넌트 주소 모드가 정한다
 * ({@code TenantAddressingStrategy#isAcceptableBaseDomain}).
 * <ul>
 *     <li>호스트는 소문자로 정규화한다(호스트 이름은 대소문자 무관 — RFC 4343).</li>
 *     <li>스킴과 짝이 맞는 기본 포트(http:80, https:443)는 떼어 null 로 둔다(RFC 3986 §6.2.3).</li>
 *     <li>스킴 없이 80·443 을 적으면 스킴을 추론해 채운다({@code host:80} → http, {@code host:443} → https, 포트는 기본 포트라 null).
 *         그 외 포트는 스킴 없이 그대로 둔다.</li>
 *     <li>끝의 {@code /} 하나는 허용하고 버린다(주소창에서 복사한 값).</li>
 * </ul>
 * DB 에는 나눠서 저장한다 — {@code base_domain}(호스트, 테넌트 도메인 조합·판별 키의 재료), {@code scheme}, {@code port}(아웃바운드 전용).
 *
 * @param scheme 입력하지 않았으면 null — 아웃바운드는 요청 스킴, 요청이 없으면 https
 * @param host   소문자 호스트
 * @param port   입력하지 않았거나 스킴의 기본 포트면 null
 */
public record BaseDomainAddress(String scheme, String host, Integer port) {

    public static final String HTTP = "http";
    public static final String HTTPS = "https";

    /** 호스트 이름 전체 최대 길이(RFC 1035 — 점 포함 253자). */
    private static final int MAX_HOST_LENGTH = 253;

    /** 스킴 뒤 authority 에 올 수 있는 문자 — 호스트([a-z0-9.-]) + 포트 구분자. 나머지(@ ? # [ ] 공백 등)는 모양 오류. */
    private static final Pattern AUTHORITY_CHARS = Pattern.compile("^[a-z0-9.\\-]+(:[0-9]{1,5})?$");

    /** 입력값을 파싱한다. 모양이 틀리면 {@link BaseDomainErrorCode#INVALID_BASE_DOMAIN}. */
    public static BaseDomainAddress parse(String input) {
        if (!StringUtils.hasText(input)) {
            throw invalid();
        }
        String v = input.trim().toLowerCase(Locale.ROOT);

        String scheme = null;
        int schemeIdx = v.indexOf("://");
        if (schemeIdx >= 0) {
            scheme = v.substring(0, schemeIdx);
            if (!HTTP.equals(scheme) && !HTTPS.equals(scheme)) {
                throw invalid();
            }
            v = v.substring(schemeIdx + 3);
        }
        if (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        if (!AUTHORITY_CHARS.matcher(v).matches()) {
            throw invalid();
        }

        String host = v;
        Integer port = null;
        int colon = v.indexOf(':');
        if (colon >= 0) {
            host = v.substring(0, colon);
            port = Integer.parseInt(v.substring(colon + 1));
            if (port < 1 || port > 65535) {
                throw invalid();
            }
        }
        if (host.isEmpty() || host.length() > MAX_HOST_LENGTH) {
            throw invalid();
        }
        if (scheme == null && port != null) {
            scheme = schemeOfWellKnownPort(port);
        }
        if (port != null && isDefaultPort(scheme, port)) {
            port = null;
        }
        return new BaseDomainAddress(scheme, host, port);
    }

    /** 화면 표시·응답용 — 저장된 그대로 {@code [scheme://]host[:port]}. */
    public String display() {
        return display(scheme, host, port);
    }

    public static String display(String scheme, String host, Integer port) {
        if (!StringUtils.hasText(host)) {
            return null;
        }
        return (scheme == null ? "" : scheme + "://") + host + (port == null ? "" : ":" + port);
    }

    /**
     * 스킴 없이 적은 포트가 80·443 이면 그 포트가 곧 스킴을 말해 주므로 스킴을 채운다(80 → http, 443 → https).
     * 그대로 두면 아웃바운드가 요청 스킴(요청 없으면 https)과 섞여 {@code https://host:80}·{@code http://host:443} 처럼 열리지 않는 주소가 된다.
     * 그 외 포트는 추론할 근거가 없어 null(요청 스킴을 따름).
     */
    private static String schemeOfWellKnownPort(int port) {
        return switch (port) {
            case 80 -> HTTP;
            case 443 -> HTTPS;
            default -> null;
        };
    }

    private static boolean isDefaultPort(String scheme, int port) {
        return (HTTP.equals(scheme) && port == 80) || (HTTPS.equals(scheme) && port == 443);
    }

    private static ApiRuntimeException invalid() {
        return ApiRuntimeException.type(BaseDomainErrorCode.INVALID_BASE_DOMAIN);
    }
}
