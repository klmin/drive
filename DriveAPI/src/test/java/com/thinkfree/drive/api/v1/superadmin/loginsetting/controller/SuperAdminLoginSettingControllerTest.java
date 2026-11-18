package com.thinkfree.drive.api.v1.superadmin.loginsetting.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.auth.enums.LoginType;
import com.thinkfree.drive.api.v1.common.loginsetting.request.JwtConfigRequest;
import com.thinkfree.drive.api.v1.common.loginsetting.request.OidcConfigRequest;
import com.thinkfree.drive.api.v1.common.loginsetting.request.ProviderItemRequest;
import com.thinkfree.drive.api.v1.common.loginsetting.request.SsoProtocolConfigRequest;
import com.thinkfree.drive.api.v1.common.loginsetting.response.JwtConfigResponse;
import com.thinkfree.drive.api.v1.common.loginsetting.response.OidcConfigResponse;
import com.thinkfree.drive.api.v1.superadmin.loginsetting.request.SuperAdminLoginSettingRequest;
import com.thinkfree.drive.api.v1.superadmin.loginsetting.request.SuperAdminLoginSettingSaveRequest;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import com.thinkfree.drive.sso.protocol.jwt.enums.KeySourceType;
import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;
import com.thinkfree.drive.sso.provider.enums.SsoProviderScope;
import com.thinkfree.drive.sso.provider.enums.SsoProviderStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Disabled
@WithMockUserCustom(roles = "ROLE_SUPER_ADMIN")
class SuperAdminLoginSettingControllerTest extends AbstractMvcTest {

    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/super-admin/login-settings");
    }

    @Test
    void find() throws IOException {
        
        SsoProviderScope s = null;
        LoginType l = LoginType.OIDC;
        var request = new SuperAdminLoginSettingRequest(s, l);
        var params = multiValueMap(request);
       
        ApiResponse<JsonNode> response = readValue(
            contentAsString(get(), null, params, status().isOk()),
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
        }
        
        assertNotNull(data);
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
    }

    @Test
    void saveEnforceFalse() {

        var request = new SuperAdminLoginSettingSaveRequest(null, false, null);

        ApiResponse<Void> response = putForObject(null, request, new TypeReference<>() {}, status().isOk());

        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
    }
    
    @Test
    void saveLocal() {
        
        var request = new SuperAdminLoginSettingSaveRequest(LoginType.LOCAL, true, null);
        ApiResponse<Void> response = putForObject(null, request, new TypeReference<>(){}, status().isOk());
        Assertions.assertEquals(OK.value(), response.getCode());
    }
    
    @Test
    void saveJwt() {
        
        var providers = List.of(
                jwtProviderItem(
                84, "SuperAdmin Jwt", null,
                "https://superadmin.example.com", null, KeySourceType.PUBLIC_KEY,
                "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqjQVOTqpZRINrXCTPzUbS08TkDyBsAOGR9Q6KUWI2iov1gluVY5ziMjtlGnQ0+6nQs5Z+XoRvVYkzG3J5bU6c38H3DNsw9yMr5pXWVCT+YmsQ+EHlag6N03p5vY/e6jmkr1wegYPEtFHJPdyoQUodCj3IaXnLV8wKbcPyQCFQ1185nBeZkwV9juIJvf+8U8UqFK1IpfC7g6sn6qhJ4t8cwa2P1UBdAjPXK9YjG7CUNVh2bUDrQS3b1mQEc4lLQg/KWCE/YAylb21WcS2eM0HT49hWidI/RyJd2M2z2zR5KhDe9tNj+FEERsobahgimnoMxgSQLiAsjmS4abJddHj5wIDAQAB\n-----END PUBLIC KEY-----",
                null, null, "https://error.example.com/redirect1331")
        );
        var request = new SuperAdminLoginSettingSaveRequest(LoginType.JWT, true, providers);
        ApiResponse<Void> response = putForObject(null, request, new TypeReference<>(){}, status().isOk());
        Assertions.assertEquals(OK.value(), response.getCode());
    }

    @Test
    void saveOidc() {
        var providers = List.of(
                oidcProviderItem(null, "Google", SsoProviderStatus.INACTIVE,
                        "https://accounts.google.com", "google-client-id", "google-client-secret"),
                oidcProviderItem(null, "github", SsoProviderStatus.ACTIVE,
                        "https://token.actions.githubusercontent.com", "github-client-id", "github-client-secret"),
                oidcProviderItem(null, "Kakao11", SsoProviderStatus.INACTIVE,
                        "https://kauth.kakao.com", "kakao-client-id11", "kakao-client-secret")
        );
        var request = new SuperAdminLoginSettingSaveRequest(LoginType.OIDC, true, providers);
        ApiResponse<Void> response = putForObject(null, request, new TypeReference<>(){}, status().isOk());
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
