package com.thinkfree.drive.sso.handler.oidc;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.restclient.config.RestClientFactory;
import com.thinkfree.drive.restclient.exception.ExternalApiException;
import com.thinkfree.drive.sso.constants.SsoConstants;
import com.thinkfree.drive.sso.model.SsoUserInfo;
import com.thinkfree.drive.sso.exception.SsoErrorCode;
import com.thinkfree.drive.sso.handler.SsoAuthHandler;
import com.thinkfree.drive.sso.handler.params.OidcCallbackParams;
import com.thinkfree.drive.sso.handler.params.SsoCallbackParams;
import com.thinkfree.drive.sso.protocol.data.SsoProtocolData;
import com.thinkfree.drive.sso.protocol.oidc.data.SsoOidcConfigData;
import com.thinkfree.drive.sso.provider.data.SsoProviderData;
import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * OIDC 로그인 — 디스커버리 문서·RestClient·JwtDecoder 를 캐시한다.
 * <ul>
 *   <li>디스커버리: 받은 뒤 {@link #DISCOVERY_TTL} 이면 만료 — IdP 의 엔드포인트·{@code jwks_uri} 변경이 재기동 없이 반영된다.
 *       토큰 교환·ID Token 검증이 실패하면 그 issuer 의 디스커버리를 바로 버려 다음 시도에서 다시 받는다</li>
 *   <li>디코더: 키 {@code issuer|jwksUri|clientId} — 검증기가 issuer·aud 를 품고 있어 셋 중 하나라도 바뀌면 새로 만든다.
 *       JWK 키 회전은 디코더 안(Nimbus JWK 소스)이 모르는 kid 를 보면 다시 받는다</li>
 *   <li>모두 크기 상한 — 관리자가 issuer URL 을 바꿔 가며 저장해도 무한히 쌓이지 않는다</li>
 * </ul>
 */
@Slf4j
@Component
public class OidcAuthHandler implements SsoAuthHandler {

    static final Duration DISCOVERY_TTL = Duration.ofHours(1);
    static final Duration IDLE_TTL = Duration.ofHours(6);
    static final long MAX_ENTRIES = 1_000;

    private final Cache<String, RestClient> clientCache;
    private final Cache<String, OidcDiscoveryDocument> discoveryCache;
    private final Cache<String, JwtDecoder> decoderCache;

    private final RestClientFactory restClientFactory;

    @Autowired
    public OidcAuthHandler(RestClientFactory restClientFactory) {
        this(restClientFactory, Ticker.systemTicker());
    }

    OidcAuthHandler(RestClientFactory restClientFactory, Ticker ticker) {
        this.restClientFactory = restClientFactory;
        this.clientCache = Caffeine.newBuilder().ticker(ticker).expireAfterAccess(IDLE_TTL).maximumSize(MAX_ENTRIES).build();
        this.discoveryCache = Caffeine.newBuilder().ticker(ticker).expireAfterWrite(DISCOVERY_TTL).maximumSize(MAX_ENTRIES).build();
        this.decoderCache = Caffeine.newBuilder().ticker(ticker).expireAfterAccess(IDLE_TTL).maximumSize(MAX_ENTRIES).build();
    }

    @Override
    public SsoProviderProtocolType supports() {
        return SsoProviderProtocolType.OIDC;
    }

    @Override
    public String callbackPath() {
        return SsoConstants.Oidc.OIDC_CALLBACK_PATH;
    }

    @Override
    public String buildAuthorizationUrl(SsoProtocolData ssoProtocolData, String state, String redirectUri) {
        SsoOidcConfigData oidcConfig = (SsoOidcConfigData) ssoProtocolData;
        OidcDiscoveryDocument discovery = fetchDiscovery(oidcConfig.getIssuerUrl());
        return UriComponentsBuilder.fromUriString(discovery.authorizationEndpoint())
                .queryParam(SsoConstants.Oidc.PARAM_RESPONSE_TYPE, SsoConstants.Oidc.DEFAULT_RESPONSE_TYPE)
                .queryParam(SsoConstants.Oidc.PARAM_CLIENT_ID, oidcConfig.getClientId())
                .queryParam(SsoConstants.Oidc.PARAM_REDIRECT_URI, redirectUri)
                .queryParam(SsoConstants.Oidc.PARAM_SCOPE, resolveScopes(oidcConfig.getScopes()))
                .queryParam(SsoConstants.Oidc.PARAM_STATE, state)
                .build()
                .encode()
                .toUriString();
    }

    private String resolveScopes(String scopes){
        return StringUtils.isBlank(scopes) ? SsoConstants.Oidc.DEFAULT_SCOPES : scopes;
    }

    private RestClient clientFor(String baseUrl) {
        return clientCache.get(baseUrl, restClientFactory::createGuardedRestClient);
    }

    private OidcDiscoveryDocument fetchDiscovery(String issuerUrl) {
        // 실패(예외)는 캐시에 남지 않는다 — 다음 요청이 다시 받는다
        return discoveryCache.get(issuerUrl, url -> {
            try {
                OidcDiscoveryDocument doc = clientFor(url).get()
                        .uri(SsoConstants.Oidc.DISCOVERY_PATH)
                        .retrieve()
                        .body(OidcDiscoveryDocument.class);

                if (doc == null){
                    throw ApiRuntimeException.type(SsoErrorCode.OIDC_DISCOVERY_FAILED);
                }
                log.info("[OIDC] Discovery 조회 완료. issuerUrl : {}", url);
                return doc;
            } catch (ExternalApiException | ResourceAccessException e) {
                log.error("[OIDC] Discovery 조회 실패. issuerUrl : {}", url, e);
                throw ApiRuntimeException.type(SsoErrorCode.OIDC_DISCOVERY_FAILED);
            }
        });
    }

    @Override
    public SsoUserInfo handleCallback(SsoProviderData provider, SsoProtocolData config, SsoCallbackParams params) {

        OidcCallbackParams oidcParams = (OidcCallbackParams) params;
        SsoOidcConfigData oidcConfig = (SsoOidcConfigData) config;
        OidcDiscoveryDocument discovery = fetchDiscovery(oidcConfig.getIssuerUrl());

        Jwt idToken;
        try {
            OidcTokenResponse tokenResponse = exchangeCode(oidcConfig, discovery, oidcParams.code(), oidcParams.redirectUri());
            log.info("[OIDC] token exchange 완료. providerSeq : {}", oidcConfig.getSsoProviderSeq());
            idToken = validateIdToken(tokenResponse.idToken(), discovery.jwksUri(), oidcConfig.getClientId(), discovery.issuer());
        } catch (RuntimeException e) {
            // 엔드포인트·jwks_uri 가 바뀌었을 수 있다 — 다음 시도에서 디스커버리를 다시 받는다
            discoveryCache.invalidate(oidcConfig.getIssuerUrl());
            throw e;
        }

        String subject = idToken.getSubject();
        String email   = idToken.getClaimAsString(DriveConstants.EMAIL);
        String name    = idToken.getClaimAsString(DriveConstants.NAME);

        log.info("[OIDC] claims 추출 완료. providerSeq : {}, subject : {}, email : {}",
                oidcConfig.getSsoProviderSeq(), subject, StringUtils.isBlank(email) ? "없음" : "존재");

        return new SsoUserInfo(subject, email, name);
    }

    private OidcTokenResponse exchangeCode(SsoOidcConfigData config, OidcDiscoveryDocument discovery, String code, String redirectUri) {

        String basicAuth = buildBasicAuthHeader(config.getClientId(), config.getClientSecret());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add(SsoConstants.Oidc.PARAM_GRANT_TYPE, SsoConstants.Oidc.DEFAULT_GRANT_TYPE);
        body.add(SsoConstants.Oidc.PARAM_CODE, code);
        body.add(SsoConstants.Oidc.PARAM_REDIRECT_URI, redirectUri);

        try {
            OidcTokenResponse response = clientFor(discovery.tokenEndpoint())
                    .post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .header(HttpHeaders.AUTHORIZATION, basicAuth)
                    .body(body)
                    .retrieve()
                    .body(OidcTokenResponse.class);

            if (response == null) {
                throw ApiRuntimeException.type(SsoErrorCode.OIDC_TOKEN_EXCHANGE_FAILED);
            }
            return response;
        } catch (ExternalApiException | ResourceAccessException e) {
            log.error("[OIDC] token exchange 실패.", e);
            throw ApiRuntimeException.type(SsoErrorCode.OIDC_TOKEN_EXCHANGE_FAILED);
        }
    }

    private String buildBasicAuthHeader(String clientId, String clientSecret) {
        String credentials = clientId + ":" + clientSecret;
        return DriveConstants.BASIC_AUTH_PREFIX + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    /** 검증기가 issuer·aud 를 품으므로 셋 다 키에 — 이전엔 issuer 가 빠져 issuer 만 바뀌면 옛 issuer 로 검증했다 */
    static String decoderKey(String issuerUrl, String jwksUri, String clientId) {
        return issuerUrl + "|" + jwksUri + "|" + clientId;
    }

    private Jwt validateIdToken(String idToken, String jwksUri, String clientId, String issuerUrl) {
        JwtDecoder decoder = decoderCache.get(decoderKey(issuerUrl, jwksUri, clientId), k -> {
            NimbusJwtDecoder nimbusDecoder = NimbusJwtDecoder.withJwkSetUri(jwksUri).restOperations(restClientFactory.guardedRestOperations()).build();

            OAuth2TokenValidator<Jwt> audienceValidator = token -> {
                List<String> audience = token.getAudience();
                if (audience != null && audience.contains(clientId)) {
                    return OAuth2TokenValidatorResult.success();
                }
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "ID Token aud에 client_id가 없음: " + clientId, null));
            };

            OAuth2TokenValidator<Jwt> combined = new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuerUrl), audienceValidator);
            nimbusDecoder.setJwtValidator(combined);
            return nimbusDecoder;
        });
        try {
            return decoder.decode(idToken);
        } catch (Exception e) {
            log.error("[OIDC] ID Token 검증 실패.", e);
            throw ApiRuntimeException.type(SsoErrorCode.OIDC_INVALID_ID_TOKEN);
        }
    }

}
