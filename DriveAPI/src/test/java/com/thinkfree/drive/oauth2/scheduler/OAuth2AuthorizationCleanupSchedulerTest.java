package com.thinkfree.drive.oauth2.scheduler;

import com.thinkfree.drive.common.RepositoryTest;
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

        new OAuth2AuthorizationCleanupScheduler(dsl).cleanupExpiredAuthorizations();

        assertThat(remainingIds()).containsExactly(
                "cleanup-access-valid", "cleanup-code-valid", "cleanup-consent-pending", "cleanup-refresh-valid");
    }
}
