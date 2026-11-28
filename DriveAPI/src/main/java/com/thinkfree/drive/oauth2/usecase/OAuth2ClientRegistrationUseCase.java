package com.thinkfree.drive.oauth2.usecase;

import com.thinkfree.drive.oauth2.exception.InvalidClientRegistrationException;
import com.thinkfree.drive.oauth2.request.OAuth2ClientRegistrationRequest;
import com.thinkfree.drive.oauth2.response.OAuth2ClientRegistrationResponse;
import com.thinkfree.drive.oauth2.service.OAuth2ClientRegistrationResult;
import com.thinkfree.drive.oauth2.service.OAuth2ClientRegistrationService;
import com.thinkfree.drive.util.LogSafe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * DCR 등록 유스케이스 — 요청 검증 → 등록 서비스 호출 → RFC 7591 응답 조립.
 * 등록 정책(무엇을 저장하나)은 OAuth2ClientRegistrationService, HTTP 매핑은 컨트롤러 담당.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2ClientRegistrationUseCase {

    /** 등록당 redirect_uri 개수 상한 — 익명 엔드포인트라 과도한 입력을 막는다 */
    private static final int MAX_REDIRECT_URIS = 10;

    /** oauth2_registered_client.redirect_uris 는 varchar(1000), SAS 는 쉼표로 이어 저장 — 넘으면 저장 시 500 이 되므로 400 으로 먼저 거른다 */
    private static final int MAX_REDIRECT_URIS_TOTAL_LENGTH = 1000;

    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");

    private static final Set<String> FORBIDDEN_SCHEMES = Set.of("javascript", "data", "vbscript", "file");

    private final OAuth2ClientRegistrationService oauth2ClientRegistrationService;

    public OAuth2ClientRegistrationResponse register(OAuth2ClientRegistrationRequest request) {
        List<String> redirectUris = validRedirectUris(request);
        List<String> scopes = parseScopes(request.scope());
        // RFC 7591 §3.2.1 — 서버는 요청 값을 바꿔 등록할 수 있고 응답이 실제 값을 알린다.
        // public(none)으로 등록하면 SAS 가 refresh token 을 주지 않아 1시간마다 재로그인이 되므로 secret 방식으로 등록한다
        if ("none".equals(request.tokenEndpointAuthMethod())) {
            log.info("[OAuth2 DCR] token_endpoint_auth_method none requested, registering as client_secret_basic/post. client_name : {}",
                    LogSafe.of(request.clientName()));
        }

        OAuth2ClientRegistrationResult result =
                oauth2ClientRegistrationService.register(request.clientName(), redirectUris, scopes);

        return OAuth2ClientRegistrationResponse.from(result);
    }

    private List<String> validRedirectUris(OAuth2ClientRegistrationRequest request) {
        List<String> redirectUris = request.redirectUris() == null ? List.of() : request.redirectUris();
        if (redirectUris.isEmpty() || redirectUris.stream().anyMatch(uri -> !isAllowedRedirectUri(uri))) {
            throw new InvalidClientRegistrationException("invalid_redirect_uri",
                    "redirect_uris is required and each must be https, http on a loopback host, or a private-use scheme, without fragment");
        }
        if (redirectUris.size() > MAX_REDIRECT_URIS
                || String.join(",", redirectUris).length() > MAX_REDIRECT_URIS_TOTAL_LENGTH) {
            throw new InvalidClientRegistrationException("invalid_redirect_uri",
                    "too many or too long redirect_uris");
        }
        return redirectUris;
    }

    /** RFC 7591 scope는 공백 구분 문자열 — 파싱만 담당. 허용 여부 검증은 등록 정책 소유자인 서비스가 한다 */
    private List<String> parseScopes(String scope) {
        if (StringUtils.hasText(scope)) {
            return List.of(scope.trim().split("\\s+"));
        }
        return List.of();
    }

    /**
     * redirect_uri 허용 정책 (OAuth 2.1 §2.3.1 / RFC 8252) — 익명 DCR 이라 누구나 등록할 수 있으므로 등록 시점에 거른다.
     * <ul>
     *   <li>https — 웹 기반 클라이언트(예: claude.ai 콜백)</li>
     *   <li>http — 루프백 호스트만(localhost·127.0.0.1·[::1]) — 로컬 CLI·데스크톱 클라이언트. 그 외 http 는 코드가 평문으로 흐른다</li>
     *   <li>그 밖의 스킴 — 네이티브 앱 private-use 스킴(cursor:// 등)은 허용하되, 브라우저에서 스크립트·로컬 자원으로
     *       해석되는 스킴(javascript·data·vbscript·file)은 거부</li>
     * </ul>
     * fragment 는 금지(RFC 6749 §3.1.2), 절대 URI 여야 한다.
     */
    private boolean isAllowedRedirectUri(String uri) {
        if (!StringUtils.hasText(uri)) {
            return false;
        }
        URI parsed;
        try {
            parsed = new URI(uri);
        } catch (URISyntaxException e) {
            return false;
        }
        String scheme = parsed.getScheme();
        if (scheme == null || parsed.getRawFragment() != null) {
            return false;
        }
        scheme = scheme.toLowerCase(Locale.ROOT);
        return switch (scheme) {
            case "https" -> StringUtils.hasText(parsed.getHost());
            case "http" -> parsed.getHost() != null && LOOPBACK_HOSTS.contains(parsed.getHost().toLowerCase(Locale.ROOT));
            default -> !FORBIDDEN_SCHEMES.contains(scheme);
        };
    }
}
