package com.thinkfree.drive.oauth2.connection.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;

/**
 * 작업 E11 — 컨텍스트의 OAuth2AuthorizationService 빈(ConnectionRecordingAuthorizationService, @Transactional 프록시)으로
 * 토큰 저장(SAS JdbcOAuth2AuthorizationService)과 연결 upsert 가 한 트랜잭션인지 실제 DB 로 확인한다.
 * 트랜잭션 없이 커밋되므로 만든 행은 끝에 지운다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("ConnectionRecordingAuthorizationService — 토큰 저장 + 연결 upsert 한 트랜잭션(실제 프록시·DB)")
class ConnectionRecordingAuthorizationServiceTest {

    private static final int USER_SEQ = 2;

    @Autowired
    private OAuth2AuthorizationService authorizationService;
    @Autowired
    private RegisteredClientRepository registeredClientRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoSpyBean
    private OAuth2ClientConnectionService connectionService;

    private RegisteredClient client;

    @BeforeEach
    void registerClient() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        client = RegisteredClient.withId("e11-rc-" + suffix).clientId("e11-client-" + suffix)
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("https://example.com/cb").scope("drive").build();
        registeredClientRepository.save(client);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM oauth2_authorization WHERE registered_client_id = ?", client.getId());
        jdbc.update("DELETE FROM tb_oauth2_client_connection WHERE client_id = ?", client.getClientId());
        jdbc.update("DELETE FROM oauth2_registered_client WHERE id = ?", client.getId());
    }

    private OAuth2Authorization authorizationWithAccessToken() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        OAuth2AccessToken token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "e11-" + UUID.randomUUID(), now, now.plusSeconds(300));
        return OAuth2Authorization.withRegisteredClient(client).id(UUID.randomUUID().toString()).principalName(String.valueOf(USER_SEQ))
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).authorizedScopes(Set.of("drive")).accessToken(token).build();
    }

    private int authorizationRows(OAuth2Authorization authorization) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM oauth2_authorization WHERE id = ?", Integer.class, authorization.getId());
    }

    private int connectionRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tb_oauth2_client_connection WHERE client_id = ?", Integer.class, client.getClientId());
    }

    @Test
    @DisplayName("성공 — 토큰 행과 연결 행이 함께 커밋된다")
    void saveCommitsTokenAndConnection() {
        OAuth2Authorization authorization = authorizationWithAccessToken();

        authorizationService.save(authorization);

        assertThat(authorizationRows(authorization)).isEqualTo(1);
        assertThat(connectionRows()).isEqualTo(1);
    }

    @Test
    @DisplayName("연결 upsert 실패 — 예외가 전파되고 토큰 저장도 롤백된다")
    void upsertFailureRollsBackTokenSave() {
        OAuth2Authorization authorization = authorizationWithAccessToken();
        willThrow(new IllegalStateException("upsert fail")).given(connectionService).recordIssuance(anyInt(), anyString(), any());

        assertThatThrownBy(() -> authorizationService.save(authorization)).isInstanceOf(IllegalStateException.class);

        assertThat(authorizationRows(authorization)).as("같은 트랜잭션이라 토큰 행도 없음").isZero();
        assertThat(connectionRows()).isZero();
    }
}
