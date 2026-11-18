package com.thinkfree.drive.oauth2.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.thinkfree.drive.oauth2.service.OAuth2ClientRegistrationResult;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponseType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.util.List;

/**
 * RFC 7591 DCR 등록 응답 — 필드명은 스펙 고정(snake_case, JsonNaming으로 일괄 변환).
 * client_secret 평문은 이 응답에 한 번만 노출된다 (저장은 BCrypt 해시라 재조회 불가).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OAuth2ClientRegistrationResponse(
        String clientId,
        String clientSecret,
        long clientSecretExpiresAt,
        long clientIdIssuedAt,
        String clientName,
        List<String> redirectUris,
        List<String> grantTypes,
        List<String> responseTypes,
        String tokenEndpointAuthMethod,
        String scope
) {

    /** RFC 7591 client_secret_expires_at — 0은 "만료 없음" */
    private static final long SECRET_NEVER_EXPIRES = 0;

    public static OAuth2ClientRegistrationResponse from(OAuth2ClientRegistrationResult result) {
        RegisteredClient client = result.registeredClient();
        return new OAuth2ClientRegistrationResponse(
                client.getClientId(),
                result.clientSecret(),
                SECRET_NEVER_EXPIRES,
                client.getClientIdIssuedAt().getEpochSecond(),
                client.getClientName(),
                List.copyOf(client.getRedirectUris()),
                List.of(AuthorizationGrantType.AUTHORIZATION_CODE.getValue(),
                        AuthorizationGrantType.REFRESH_TOKEN.getValue()),
                List.of(OAuth2AuthorizationResponseType.CODE.getValue()),
                ClientAuthenticationMethod.CLIENT_SECRET_POST.getValue(),
                client.getScopes().isEmpty() ? null : String.join(" ", client.getScopes())
        );
    }
}
