package com.thinkfree.drive.oauth2.scheduler;

import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.oauth2.properties.OAuth2RegistrationProperty;
import java.time.Duration;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * oauth2_authorization 만료 행 청소 — jOOQ DELETE(3 OR 조건)를 메서드 직접 호출로 확인(PostgreSQL Testcontainers, 트랜잭션 롤백).
 * 스케줄러는 직접 생성한다 — 빈 프록시를 거치면 ShedLock 이 바깥 tx 커넥션을 커밋한다.
 * 검증 포인트 = 가장 수명 긴 토큰 기준 만료 판정(refresh → access → code 순), 미래 만료·만료 컬럼 전부 NULL(동의 진행 중) 행은 남음.
 */
@DisplayName("OAuth2AuthorizationCleanupScheduler — jOOQ 만료 행 DELETE(PostgreSQL Testcontainers)")
class OAuth2AuthorizationCleanupSchedulerTest extends RepositoryTest {

    private static final Instant PAST = Instant.now().minus(1, ChronoUnit.HOURS);
    private static final Instant FUTURE = Instant.now().plus(1, ChronoUnit.HOURS);

    @Autowired
    private DSLContext dsl;
    @Autowired
    private JdbcTemplate jdbc;

    private void authorization(String id, Instant refreshExpiresAt, Instant accessExpiresAt, Instant codeExpiresAt) {
        jdbc.update("INSERT INTO oauth2_authorization (id, registered_client_id, principal_name, authorization_grant_type,"
                        + " refresh_token_expires_at, access_token_expires_at, authorization_code_expires_at) VALUES (?, 'cleanup-client', 'alice', 'authorization_code', ?, ?, ?)",
                id, odt(refreshExpiresAt), odt(accessExpiresAt), odt(codeExpiresAt));
    }

    private OAuth2AuthorizationCleanupScheduler scheduler() {
        return new OAuth2AuthorizationCleanupScheduler(dsl,
                new OAuth2RegistrationProperty(10, 500, Duration.ofHours(1), Duration.ofDays(7)));
    }

    private void registeredClient(String id, Instant issuedAt) {
        jdbc.update("INSERT INTO oauth2_registered_client (id, client_id, client_id_issued_at, client_name, client_authentication_methods,"
                        + " authorization_grant_types, redirect_uris, scopes, client_settings, token_settings)"
                        + " VALUES (?, ?, ?, 'c', 'client_secret_post', 'authorization_code', 'https://claude.ai/cb', 'mcp:read', '{}', '{}')",
                id, id + "-cid", odt(issuedAt));
    }

    private void authorizationOf(String id, String registeredClientId) {
        jdbc.update("INSERT INTO oauth2_authorization (id, registered_client_id, principal_name, authorization_grant_type,"
                + " refresh_token_expires_at) VALUES (?, ?, 'alice', 'authorization_code', ?)", id, registeredClientId, odt(FUTURE));
    }

    private void consentOf(String registeredClientId) {
        jdbc.update("INSERT INTO oauth2_authorization_consent (registered_client_id, principal_name, authorities) VALUES (?, 'alice', 'SCOPE_mcp:read')",
                registeredClientId);
    }

    private void connectionOf(String clientId) {
        jdbc.update("INSERT INTO tb_oauth2_client_connection (user_seq, client_id, scopes, register_date, token_issued_date)"
                + " VALUES (1, ?, 'mcp:read', now(), now())", clientId);
    }

    @Test
    @DisplayName("cleanupUnusedRegisteredClients — 유예(7일) 지나고 인가·연결 이력 없는 클라이언트와 그 동의만 삭제")
    void cleanupUnusedRegisteredClients() {
        Instant old = Instant.now().minus(8, ChronoUnit.DAYS);
        registeredClient("dcr-unused-old", old);
        consentOf("dcr-unused-old");
        registeredClient("dcr-unused-recent", Instant.now().minus(1, ChronoUnit.DAYS));
        registeredClient("dcr-old-with-authorization", old);
        authorizationOf("dcr-auth-1", "dcr-old-with-authorization");
        consentOf("dcr-old-with-authorization");
        registeredClient("dcr-old-with-connection", old);
        connectionOf("dcr-old-with-connection-cid");

        scheduler().cleanupUnusedRegisteredClients(Instant.now());

        assertThat(jdbc.queryForList("SELECT id FROM oauth2_registered_client WHERE id LIKE 'dcr-%' ORDER BY id", String.class))
                .containsExactly("dcr-old-with-authorization", "dcr-old-with-connection", "dcr-unused-recent");
        assertThat(jdbc.queryForList("SELECT registered_client_id FROM oauth2_authorization_consent WHERE registered_client_id LIKE 'dcr-%'", String.class))
                .containsExactly("dcr-old-with-authorization");
    }

    private static OffsetDateTime odt(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private List<String> remainingIds() {
        return jdbc.queryForList("SELECT id FROM oauth2_authorization WHERE id LIKE 'cleanup-%' ORDER BY id", String.class);
    }

    @Test
    @DisplayName("cleanupExpiredAuthorizations — refresh 만료(access 미래여도)·refresh 없이 access 만료·토큰 없이 code 만료만 삭제, 미래 만료·전부 NULL 은 남음")
    void cleanupExpiredAuthorizations() {
        authorization("cleanup-refresh-expired", PAST, FUTURE, null);
        authorization("cleanup-refresh-valid", FUTURE, PAST, PAST);
        authorization("cleanup-access-expired", null, PAST, FUTURE);
        authorization("cleanup-access-valid", null, FUTURE, PAST);
        authorization("cleanup-code-expired", null, null, PAST);
        authorization("cleanup-code-valid", null, null, FUTURE);
        authorization("cleanup-consent-pending", null, null, null);

        scheduler().cleanupExpiredAuthorizations();

        assertThat(remainingIds()).containsExactly(
                "cleanup-access-valid", "cleanup-code-valid", "cleanup-consent-pending", "cleanup-refresh-valid");
    }
}
