package com.thinkfree.drive.oauth2.connection.repository;

import com.thinkfree.drive.common.RepositoryTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SAS 테이블(oauth2_authorization·consent) 연결 해제 삭제 — jOOQ DELETE 2종(PostgreSQL Testcontainers, 트랜잭션 롤백).
 * 검증 포인트 = (principal_name, registered_client_id) 둘 다 맞는 행만 지우고 삭제 건수를 돌려준다(다른 사용자·다른 클라이언트 행은 남음).
 */
@DisplayName("DefaultOAuth2AuthorizationRevokeRepository — jOOQ DELETE(PostgreSQL Testcontainers)")
class DefaultOAuth2AuthorizationRevokeRepositoryTest extends RepositoryTest {

    private static final String CLIENT = "revoke-client";
    private static final String OTHER_CLIENT = "revoke-other-client";

    @Autowired
    private OAuth2AuthorizationRevokeRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    private void authorization(String id, String principal, String clientId) {
        jdbc.update("INSERT INTO oauth2_authorization (id, registered_client_id, principal_name, authorization_grant_type) VALUES (?, ?, ?, 'authorization_code')",
                id, clientId, principal);
    }

    private void consent(String principal, String clientId) {
        jdbc.update("INSERT INTO oauth2_authorization_consent (registered_client_id, principal_name, authorities) VALUES (?, ?, 'SCOPE_read')", clientId, principal);
    }

    private int authorizations(String principal, String clientId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM oauth2_authorization WHERE principal_name = ? AND registered_client_id = ?", Integer.class, principal, clientId);
    }

    private int consents(String principal, String clientId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM oauth2_authorization_consent WHERE principal_name = ? AND registered_client_id = ?", Integer.class, principal, clientId);
    }

    @Test
    @DisplayName("deleteAuthorizations — 같은 (principal, client) 행만 삭제·건수 반환, 다른 사용자·클라이언트는 남음, 대상 없으면 0")
    void deleteAuthorizations() {
        authorization("revoke-a1", "alice", CLIENT);
        authorization("revoke-a2", "alice", CLIENT);
        authorization("revoke-a3", "alice", OTHER_CLIENT);
        authorization("revoke-b1", "bob", CLIENT);

        assertThat(repository.deleteAuthorizations("alice", CLIENT)).isEqualTo(2);
        assertThat(authorizations("alice", CLIENT)).isZero();
        assertThat(authorizations("alice", OTHER_CLIENT)).as("다른 클라이언트").isEqualTo(1);
        assertThat(authorizations("bob", CLIENT)).as("다른 사용자").isEqualTo(1);
        assertThat(repository.deleteAuthorizations("alice", CLIENT)).isZero();
    }

    @Test
    @DisplayName("deleteConsents — 같은 (principal, client) 행만 삭제·건수 반환, 다른 사용자·클라이언트는 남음, 대상 없으면 0")
    void deleteConsents() {
        consent("alice", CLIENT);
        consent("alice", OTHER_CLIENT);
        consent("bob", CLIENT);

        assertThat(repository.deleteConsents("alice", CLIENT)).isEqualTo(1);
        assertThat(consents("alice", CLIENT)).isZero();
        assertThat(consents("alice", OTHER_CLIENT)).as("다른 클라이언트").isEqualTo(1);
        assertThat(consents("bob", CLIENT)).as("다른 사용자").isEqualTo(1);
        assertThat(repository.deleteConsents("alice", CLIENT)).isZero();
    }
}
