package com.thinkfree.drive.api.v1.admin.loginsetting.controller;

import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.admin.loginsetting.request.LoginSettingRequest;
import com.thinkfree.drive.api.v1.admin.loginsetting.request.LoginSettingSaveRequest;
import com.thinkfree.drive.api.v1.auth.enums.LoginType;
import com.thinkfree.drive.api.v1.common.loginsetting.request.JwtConfigRequest;
import com.thinkfree.drive.api.v1.common.loginsetting.request.OidcConfigRequest;
import com.thinkfree.drive.api.v1.common.loginsetting.request.ProviderItemRequest;
import com.thinkfree.drive.api.v1.common.loginsetting.request.SsoProtocolConfigRequest;
import com.thinkfree.drive.api.v1.common.loginsetting.response.JwtConfigResponse;
import com.thinkfree.drive.api.v1.common.loginsetting.response.OidcConfigResponse;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import com.thinkfree.drive.sso.protocol.jwt.enums.KeySourceType;
import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;
import com.thinkfree.drive.sso.provider.enums.SsoProviderStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockUserCustom(tenantSeq = 5)
@Transactional
class AdminLoginSettingControllerTest extends AbstractMvcTest {

    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/admins/login-settings");
        setTestHost("test5.drive.thinkfree.com");
    }

    @Test
    @Disabled("dev DB 고정 데이터 의존(REQUEST_006) — DriveFixtures 로 옮길 때 다시 켠다")
    void find() throws IOException {
        
        var request = new LoginSettingRequest(LoginType.LOCAL);
        
        ApiResponse<JsonNode> response = readValue(
            contentAsString(get(), null, null, status().isOk()),
            new TypeReference<>() {}
        );
        Assertions.assertNotNull(response.getData());
        Assertions.assertEquals(OK.value(), response.getCode());
        
        
        JsonNode data = response.getData();
        String loginType = data.get("loginType").asText();
        JsonNode configsNode = data.get("configs");

        assertNotNull(configsNode);
        assertTrue(configsNode.isArray());

        switch (loginType) {
            case "local" -> assertEquals(0, configsNode.size());
            case "oidc" -> {
                List<OidcConfigResponse> configs =
                    objectMapper.readerForListOf(OidcConfigResponse.class).readValue(configsNode);
                assertNotNull(configs);
            }
            case "jwt" -> {
                List<JwtConfigResponse> configs =
                    objectMapper.readerForListOf(JwtConfigResponse.class).readValue(configsNode);
                assertNotNull(configs);
            }
            default -> throw new IllegalArgumentException("Unknown loginType: " + loginType);
        }

        assertNotNull(data);
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
    }

    @Test
    @Disabled("dev DB 고정 데이터 의존(REQUEST_006) — DriveFixtures 로 옮길 때 다시 켠다")
    void saveLocal() {
        
        var request = new LoginSettingSaveRequest(LoginType.LOCAL, null);
        ApiResponse<Void> response = patchForObject(null, request, new TypeReference<>(){}, status().isOk());
        Assertions.assertEquals(OK.value(), response.getCode());
    }

    @Test
    @Disabled("dev DB 고정 데이터 의존(REQUEST_006) — DriveFixtures 로 옮길 때 다시 켠다")
    void saveJwt() {
        
        var provider = List.of(
                jwtProviderItem(
                54, "JWT Provider", null,
                "https://issuer.example.com", null, KeySourceType.PUBLIC_KEY,
                "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkq\n-----END PUBLIC KEY-----",
                null, "email11", "https://error.example.com/redirect11")
        );
        var request = new LoginSettingSaveRequest(LoginType.JWT, provider);
        ApiResponse<Void> response = patchForObject(null, request, new TypeReference<>(){}, status().isOk());
        Assertions.assertEquals(OK.value(), response.getCode());
    }

    @Test
    @Disabled("dev DB 고정 데이터 의존(REQUEST_006) — DriveFixtures 로 옮길 때 다시 켠다")
    void saveOidc() {
        var providers = List.of(
                oidcProviderItem(59, "Google", SsoProviderStatus.INACTIVE,
                        "https://accounts.google.com", "google-client-id", "google-client-secret"),
                oidcProviderItem(null, "GitHub11", SsoProviderStatus.ACTIVE,
                        "https://token.actions.githubusercontent.com", "github-client-id", "github-client-secret"),
               oidcProviderItem(null, "Kakao11", SsoProviderStatus.INACTIVE,
                        "https://kauth.kakao.com", "kakao-client-id11", "kakao-client-secret")
        );
        var request = new LoginSettingSaveRequest(LoginType.OIDC, providers);
        ApiResponse<Void> response = patchForObject(null, request, new TypeReference<>(){}, status().isOk());
        Assertions.assertEquals(OK.value(), response.getCode());
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private ProviderItemRequest jwtProviderItem(
            Integer ssoProviderSeq,
            String displayName,
            SsoProviderStatus status,
            String issuer,
            String audience,
            KeySourceType keySourceType,
            String publicKey,
            String jwksUri,
            String emailClaimName,
            String errorRedirectUrl
    ) {
        var config = new JwtConfigRequest(SsoProviderProtocolType.JWT, issuer, audience, keySourceType, publicKey, jwksUri, emailClaimName, errorRedirectUrl);
        return createProviderItem(ssoProviderSeq, displayName, status, config);
    }

    private ProviderItemRequest oidcProviderItem(
            Integer ssoProviderSeq,
            String displayName,
            SsoProviderStatus status,
            String issuerUrl,
            String clientId,
            String clientSecret
    ) {
        var config = new OidcConfigRequest(SsoProviderProtocolType.OIDC, issuerUrl, clientId, clientSecret);
        return createProviderItem(ssoProviderSeq, displayName, status, config);
    }

    private ProviderItemRequest createProviderItem(Integer ssoProviderSeq, String displayName, SsoProviderStatus status, SsoProtocolConfigRequest config){
        return new ProviderItemRequest(ssoProviderSeq, displayName, status, config);
    }
}
