package com.thinkfree.drive.sso.handler.oidc;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class OidcAuthHandler implements SsoAuthHandler {
    
    private final Map<String, RestClient> clientCache = new ConcurrentHashMap<>();
    private final Map<String, OidcDiscoveryDocument> discoveryCache = new ConcurrentHashMap<>();
    private final Map<String, JwtDecoder> decoderCache = new ConcurrentHashMap<>();
    
    private final RestClientFactory restClientFactory;
    
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
        return clientCache.computeIfAbsent(baseUrl, restClientFactory::createGuardedRestClient);
    }
    
    private OidcDiscoveryDocument fetchDiscovery(String issuerUrl) {
        return discoveryCache.computeIfAbsent(issuerUrl, url -> {
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

        OidcTokenResponse tokenResponse = exchangeCode(oidcConfig, discovery, oidcParams.code(), oidcParams.redirectUri());
        log.info("[OIDC] token exchange 완료. providerSeq : {}", oidcConfig.getSsoProviderSeq());
        log.info("tokenResponse : {}", tokenResponse);
        
        Jwt idToken = validateIdToken(tokenResponse.idToken(), discovery.jwksUri(), oidcConfig.getClientId(), discovery.issuer());
        log.info("idToken.getClaims : {}", idToken.getClaims());
        
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
    
    private Jwt validateIdToken(String idToken, String jwksUri, String clientId, String issuerUrl) {
        String cacheKey = jwksUri + "|" + clientId;
        JwtDecoder decoder = decoderCache.computeIfAbsent(cacheKey, k -> {
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
