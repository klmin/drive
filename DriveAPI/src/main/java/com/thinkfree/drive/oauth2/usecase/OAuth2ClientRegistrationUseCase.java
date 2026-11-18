package com.thinkfree.drive.oauth2.usecase;

import com.thinkfree.drive.oauth2.exception.InvalidClientRegistrationException;
import com.thinkfree.drive.oauth2.request.OAuth2ClientRegistrationRequest;
import com.thinkfree.drive.oauth2.response.OAuth2ClientRegistrationResponse;
import com.thinkfree.drive.oauth2.service.OAuth2ClientRegistrationResult;
import com.thinkfree.drive.oauth2.service.OAuth2ClientRegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.List;

/**
 * DCR 등록 유스케이스 — 요청 검증 → 등록 서비스 호출 → RFC 7591 응답 조립.
 * 등록 정책(무엇을 저장하나)은 OAuth2ClientRegistrationService, HTTP 매핑은 컨트롤러 담당.
 */
@Service
@RequiredArgsConstructor
public class OAuth2ClientRegistrationUseCase {

    private final OAuth2ClientRegistrationService oauth2ClientRegistrationService;

    public OAuth2ClientRegistrationResponse register(OAuth2ClientRegistrationRequest request) {
        List<String> redirectUris = validRedirectUris(request);
        List<String> scopes = parseScopes(request.scope());

        OAuth2ClientRegistrationResult result =
                oauth2ClientRegistrationService.register(request.clientName(), redirectUris, scopes);

        return OAuth2ClientRegistrationResponse.from(result);
    }

    private List<String> validRedirectUris(OAuth2ClientRegistrationRequest request) {
        List<String> redirectUris = request.redirectUris() == null ? List.of() : request.redirectUris();
        if (redirectUris.isEmpty() || redirectUris.stream().anyMatch(uri -> !isValidUri(uri))) {
            throw new InvalidClientRegistrationException("invalid_redirect_uri",
                    "redirect_uris is required and must contain valid URIs");
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

    private boolean isValidUri(String uri) {
        try {
            return uri != null && URI.create(uri).getScheme() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
