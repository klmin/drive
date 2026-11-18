package com.thinkfree.drive.oauth2.connection.repository;

import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.oauth2.connection.dto.OAuth2ClientConnectionData;
import com.thinkfree.drive.oauth2.connection.entity.OAuth2ClientConnection;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OAuth2ClientConnection 리포 회귀 — 쓰기({@link DefaultOAuth2ClientConnectionRepository}: upsert 만 JdbcClient ON CONFLICT, findByUserSeqAndClientId(파생 쿼리)·findById 는 관리 엔티티 —
 * 마지막 사용일은 변경 메서드 → 변경 감지, 해제는 delete, W2 10-12)·읽기({@link DefaultOAuth2ClientConnectionQueryRepository}: SAS 테이블 LEFT JOIN 목록, JdbcClient).
 * tb_oauth2_client_connection 에 FK 없음(user_seq 임의 값). 트랜잭션 롤백 — 변경 감지·삭제 반영은 {@code entityManager.flush()} 후 JdbcTemplate 으로 DB 확인.
 * 운영에서 upsert(토큰 발급 트랜잭션)와 findByUserSeqAndClientId(RS 필터)·findById(해제 트랜잭션)는 다른 트랜잭션이라, 같은 트랜잭션에서 섞는 이 테스트는 upsert 뒤 {@code clear()} 로 새 트랜잭션을 흉내 낸다.
 */
@DisplayName("DefaultOAuth2ClientConnectionRepository / DefaultOAuth2ClientConnectionQueryRepository — JPA + JdbcClient upsert(PostgreSQL Testcontainers)")
class DefaultOAuth2ClientConnectionRepositoryTest extends RepositoryTest {

    private static final int USER_A = 910001;
    private static final int USER_B = 910002;

    @Autowired
    private OAuth2ClientConnectionRepository repository;
    @Autowired
    private OAuth2ClientConnectionQueryRepository queryRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    private void registeredClient(String clientId, String clientName) {
        jdbc.update("INSERT INTO oauth2_registered_client (id, client_id, client_name, client_authentication_methods, authorization_grant_types, scopes, client_settings, token_settings) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                "id-" + clientId, clientId, clientName, "client_secret_basic", "authorization_code", "mcp:read", "{}", "{}");
    }

    private int count(int userSeq, String clientId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tb_oauth2_client_connection WHERE user_seq = ? AND client_id = ?", Integer.class, userSeq, clientId);
    }

    private Map<String, Object> row(int userSeq, String clientId) {
        return jdbc.queryForMap("SELECT connection_seq, scopes, register_date, token_issued_date, last_used_date FROM tb_oauth2_client_connection WHERE user_seq = ? AND client_id = ?", userSeq, clientId);
    }

    private static Instant instant(Object ts) {
        return ts == null ? null : ((Timestamp) ts).toInstant();
    }

    @Test
    @DisplayName("upsert(신규 → register = token_issued 현재 시각 / 충돌 → scopes·token_issued_date 만 갱신, register_date 유지) · findByUserSeqAndClientId·findById 관리 엔티티 · changeLastUsedDate 변경 감지 · 목록(clientName LEFT JOIN·DESC) · delete")
    void upsertFindTouchListDelete() throws Exception {
        Instant before = Instant.now().minusSeconds(1);
        registeredClient("pg-mcp", "MCP Client");

        // 신규 upsert — register_date = token_issued_date = 현재 시각
        repository.upsert(USER_A, "pg-mcp", "mcp:read");
        Map<String, Object> created = row(USER_A, "pg-mcp");
        long seq = ((Number) created.get("connection_seq")).longValue();
        assertThat(instant(created.get("register_date"))).isAfter(before);
        assertThat(created.get("token_issued_date")).isEqualTo(created.get("register_date"));
        assertThat(created.get("last_used_date")).isNull();

        // 충돌 upsert — 같은 (user_seq, client_id): 행 1개·같은 seq, scopes·token_issued_date 갱신, register_date 유지
        Thread.sleep(1100);   // 초 절삭 시각이 달라지게
        repository.upsert(USER_A, "pg-mcp", "mcp:read mcp:write");
        assertThat(count(USER_A, "pg-mcp")).isEqualTo(1);
        Map<String, Object> again = row(USER_A, "pg-mcp");
        assertThat(((Number) again.get("connection_seq")).longValue()).isEqualTo(seq);
        assertThat(again.get("scopes")).isEqualTo("mcp:read mcp:write");
        assertThat(instant(again.get("token_issued_date"))).isAfter(instant(created.get("token_issued_date")));
        assertThat(again.get("register_date")).as("충돌 시 register_date 유지").isEqualTo(created.get("register_date"));

        // findByUserSeqAndClientId — 관리 엔티티(없는 조합은 null)
        entityManager.clear();
        OAuth2ClientConnection managed = repository.findByUserSeqAndClientId(USER_A, "pg-mcp");
        assertThat(entityManager.contains(managed)).as("관리 엔티티").isTrue();
        assertThat(managed.getConnectionSeq()).isEqualTo(seq);
        assertThat(managed.getScopes()).isEqualTo("mcp:read mcp:write");
        assertThat(repository.findByUserSeqAndClientId(USER_B, "pg-mcp")).isNull();
        assertThat(repository.findByUserSeqAndClientId(USER_A, "pg-other")).isNull();

        // changeLastUsedDate — flush(커밋) 때 변경 감지로 이 컬럼만 UPDATE
        Instant fixed = Instant.parse("2026-03-03T03:03:03Z");
        managed.changeLastUsedDate(fixed);
        assertThat(row(USER_A, "pg-mcp").get("last_used_date")).as("flush 전 — 메모리만").isNull();
        entityManager.flush();
        Map<String, Object> touched = row(USER_A, "pg-mcp");
        assertThat(instant(touched.get("last_used_date"))).isEqualTo(fixed);
        assertThat(touched.get("scopes")).as("다른 컬럼 그대로(@DynamicUpdate)").isEqualTo("mcp:read mcp:write");

        // findById — PK 로드(소유자 판정은 Service — DefaultOAuth2ClientConnectionServiceTest), 같은 영속성 컨텍스트라 같은 인스턴스
        assertThat(repository.findById(seq)).isSameAs(managed);
        assertThat(repository.findById(999999L)).isNull();

        // 목록 — SAS 테이블 LEFT JOIN(클라이언트 행 없으면 clientName null), register_date DESC
        Thread.sleep(1100);
        repository.upsert(USER_A, "pg-noclient", "mcp:read");
        repository.upsert(USER_B, "pg-mcp", "mcp:read");
        List<OAuth2ClientConnectionData> list = queryRepository.findAllWithClientNameByUserSeq(USER_A);
        assertThat(list).extracting(OAuth2ClientConnectionData::getClientId).containsExactly("pg-noclient", "pg-mcp");
        OAuth2ClientConnectionData mcp = list.get(1);
        assertThat(mcp.getConnectionSeq()).isEqualTo(seq);
        assertThat(mcp.getClientName()).isEqualTo("MCP Client");
        assertThat(mcp.getScopes()).isEqualTo("mcp:read mcp:write");
        assertThat(mcp.getLastUsedDate()).isEqualTo(fixed);
        assertThat(list.get(0).getClientName()).as("클라이언트 행 없음 → null").isNull();
        assertThat(queryRepository.findAllWithClientNameByUserSeq(USER_B)).extracting(OAuth2ClientConnectionData::getClientId).containsExactly("pg-mcp");

        // delete — 관리 엔티티 삭제, flush(커밋) 때 DELETE
        repository.delete(managed);
        entityManager.flush();
        assertThat(count(USER_A, "pg-mcp")).isZero();
        assertThat(queryRepository.findAllWithClientNameByUserSeq(USER_A)).extracting(OAuth2ClientConnectionData::getClientId).containsExactly("pg-noclient");
        assertThat(count(USER_B, "pg-mcp")).as("다른 사용자 행 무영향").isEqualTo(1);
    }
}
