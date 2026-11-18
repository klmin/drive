package com.thinkfree.drive.cleanup.tenant.service;

import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.tenant.exception.TenantErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * 테넌트 삭제 — 대상 테넌트의 사용자(영구 삭제 cascade 포함)·테넌트 범위 행·tb_tenant 만 지우고, 다른 테넌트·전역(tenant_seq NULL) 행은 남는지.
 * 모듈별 서비스 deleteByTenant 경유(DefaultTenantCleanUpService). 테이블마다 대상·다른 테넌트 행을 한 건씩 넣는다(JdbcTemplate). 트랜잭션 롤백 —
 * 커밋 후 정리(스토리지·캐시)는 실행되지 않는다.
 */
@DisplayName("TenantDeletionService — 테넌트·사용자·테넌트 범위 데이터 삭제(PostgreSQL Testcontainers)")
class TenantDeletionServiceTest extends RepositoryTest {

    private static final int SUPER_ADMIN = 1;

    /** tenant_seq 로 지우는 테이블 */
    private static final List<String> TENANT_TABLES = List.of(
            "tb_access_allowed_ip", "tb_access_allowed_country", "tb_api_key", "tb_branding", "tb_license",
            "tb_share_setting", "tb_smtp_config", "tb_sso_provider", "tb_tenant_access_token", "tb_system_config", "tb_tenant");

    @Autowired
    private TenantDeletionService tenantDeletionService;
    @Autowired
    private JdbcTemplate jdbc;

    private int target;
    private int other;
    private int targetUser;
    private int otherUser;

    @BeforeEach
    void setUp() {
        target = insertTenant("cleanup-target");
        other = insertTenant("cleanup-other");
        targetUser = insertUser(target, "cleanup-target@x.com");
        otherUser = insertUser(other, "cleanup-other@x.com");
        insertTenantScopedRows(target, targetUser);
        insertTenantScopedRows(other, otherUser);
        // 전역 SSO 공급자(tenant_seq NULL) + 프로토콜 설정 — 남아야 한다
        int globalProvider = insertProvider(null, "OIDC");
        insertOidcConfig(globalProvider, "global");
    }

    @Test
    @DisplayName("대상 테넌트만 — 사용자는 DELETED·연관 행(역할·재설정 토큰·OAuth 연결·인가) 삭제, 테넌트 범위 행·tb_tenant 삭제, 다른 테넌트·전역 그대로")
    void deleteTenant_onlyTarget() {
        Map<String, Integer> otherBefore = countsBy(other);
        Map<String, Integer> globalBefore = globalCounts();

        tenantDeletionService.deleteTenant(target, SUPER_ADMIN);

        assertThat(countsBy(target)).allSatisfy((table, n) -> assertThat(n).as(table).isZero());
        assertThat(count("select count(*) from tb_sso_jwt_config where issuer = ?", "jwt-" + target)).isZero();
        assertThat(count("select count(*) from tb_sso_oidc_config where client_id = ?", "oidc-" + target)).isZero();

        assertThat(jdbc.queryForObject("select status from tb_user where user_seq = ?", String.class, targetUser)).isEqualTo("DELETED");
        assertThat(userLinkedCounts(targetUser)).allSatisfy((table, n) -> assertThat(n).as(table).isZero());

        assertThat(countsBy(other)).isEqualTo(otherBefore);
        assertThat(count("select count(*) from tb_sso_jwt_config where issuer = ?", "jwt-" + other)).isOne();
        assertThat(count("select count(*) from tb_sso_oidc_config where client_id = ?", "oidc-" + other)).isOne();
        assertThat(jdbc.queryForObject("select status from tb_user where user_seq = ?", String.class, otherUser)).isEqualTo("ACTIVE");
        assertThat(userLinkedCounts(otherUser)).allSatisfy((table, n) -> assertThat(n).as(table).isOne());
        assertThat(globalCounts()).isEqualTo(globalBefore);
    }

    @Test
    @DisplayName("없는 테넌트 — TENANT_NOT_FOUND")
    void deleteTenant_missingTenant() {
        var e = catchThrowableOfType(ApiRuntimeException.class, () -> tenantDeletionService.deleteTenant(999_999, SUPER_ADMIN));

        assertThat(e.getCode()).isEqualTo(TenantErrorCode.TENANT_NOT_FOUND);
    }

    // ------------------------------------------------------------------ 데이터

    private int insertTenant(String name) {
        return jdbc.queryForObject("insert into tb_tenant (tenant_name, tenant_type, user_mode, register_user_seq) "
                + "values (?, 'SYSTEM_MANAGED', 'SINGLE', ?) returning tenant_seq", Integer.class, name, SUPER_ADMIN);
    }

    private int insertUser(int tenantSeq, String userId) {
        int userSeq = jdbc.queryForObject("insert into tb_user (tenant_seq, user_id, user_name, register_user_seq, is_protected) "
                + "values (?, ?, 'u', ?, 'Y') returning user_seq", Integer.class, tenantSeq, userId, SUPER_ADMIN);
        jdbc.update("insert into tb_user_role (user_seq, role_type, register_user_seq) values (?, 'USER', ?)", userSeq, SUPER_ADMIN);
        jdbc.update("insert into tb_password_reset_token (user_seq, token_hash, issued_date, expiration_date, register_date) "
                + "values (?, ?, now(), now() + interval '1 hour', now())", userSeq, String.format("r%063d", userSeq));
        jdbc.update("insert into tb_oauth2_client_connection (user_seq, client_id, scopes, register_date, token_issued_date) "
                + "values (?, 'cleanup-client', 'mcp:read', now(), now())", userSeq);
        jdbc.update("insert into oauth2_authorization_consent (registered_client_id, principal_name, authorities) values ('cleanup-client', ?, 'mcp:read')",
                String.valueOf(userSeq));
        return userSeq;
    }

    private void insertTenantScopedRows(int t, int userSeq) {
        jdbc.update("insert into tb_access_allowed_ip (tenant_seq, role_type, ip_address, register_user_seq) values (?, 'ADMIN', '10.0.0.1', ?)", t, SUPER_ADMIN);
        jdbc.update("insert into tb_access_allowed_country (tenant_seq, country_iso_code, register_user_seq) values (?, 'KR', ?)", t, SUPER_ADMIN);
        // 테넌트에 속하지만 사용자 삭제 cascade 가 지우지 않는 키(다른 소유자) — 테넌트 정리에서 지워야 한다
        jdbc.update("insert into tb_api_key (tenant_seq, key_name, key_purpose, api_key, register_user_seq, user_seq, scope, expire_date) "
                + "values (?, 'k', 'p', ?, ?, ?, 'READ', now() + interval '1 day')", t, "cleanup-key-" + t, SUPER_ADMIN, SUPER_ADMIN);
        jdbc.update("insert into tb_branding (tenant_seq, tab_title, service_name, register_user_seq) values (?, 't', 's', ?)", t, SUPER_ADMIN);
        jdbc.update("insert into tb_license (tenant_seq, data) values (?, ?)", t, new byte[]{1});
        jdbc.update("insert into tb_share_setting (tenant_seq, register_user_seq) values (?, ?)", t, SUPER_ADMIN);
        jdbc.update("insert into tb_smtp_config (tenant_seq, client_name, host, port, encryption_type, auth_type, username, from_address, test_receiver_email, register_user_seq) "
                + "values (?, 'c', 'h', 25, 'NONE', 'NONE', 'u', 'f@x.com', 'r@x.com', ?)", t, SUPER_ADMIN);
        int jwtProvider = insertProvider(t, "JWT");
        jdbc.update("insert into tb_sso_jwt_config (sso_provider_seq, issuer, key_source_type, jwks_uri, register_user_seq) values (?, ?, 'JWKS', 'https://x/jwks', ?)",
                jwtProvider, "jwt-" + t, SUPER_ADMIN);
        insertOidcConfig(insertProvider(t, "OIDC"), String.valueOf(t));
        jdbc.update("insert into tb_tenant_access_token (super_admin_user_seq, tenant_seq, token_hash, issued_date, expiration_date, register_date) "
                + "values (?, ?, ?, now(), now() + interval '5 minute', now())", SUPER_ADMIN, t, String.format("%064d", t));
        jdbc.update("insert into tb_system_config (tenant_seq, setting_key, value_text, register_user_seq) values (?, 'cleanup_test', 'v', ?)", t, SUPER_ADMIN);
    }

    private int insertProvider(Integer tenantSeq, String protocol) {
        return jdbc.queryForObject("insert into tb_sso_provider (tenant_seq, protocol_type, scope, register_user_seq, display_name) values (?, ?, ?, ?, ?) returning sso_provider_seq",
                Integer.class, tenantSeq, protocol, tenantSeq == null ? "GLOBAL" : "TENANT", SUPER_ADMIN, protocol + "-" + tenantSeq);
    }

    private void insertOidcConfig(int providerSeq, String suffix) {
        jdbc.update("insert into tb_sso_oidc_config (sso_provider_seq, issuer_url, client_id, client_secret, register_user_seq) values (?, 'https://idp', ?, 's', ?)",
                providerSeq, "oidc-" + suffix, SUPER_ADMIN);
    }

    // ------------------------------------------------------------------ 확인

    private Map<String, Integer> countsBy(int tenantSeq) {
        return TENANT_TABLES.stream().collect(Collectors.toMap(Function.identity(),
                table -> count("select count(*) from " + table + " where tenant_seq = ?", tenantSeq)));
    }

    /** 사용자 영구 삭제 cascade 대상 중 이번에 추가한 것 + 역할 */
    private Map<String, Integer> userLinkedCounts(int userSeq) {
        return Map.of(
                "tb_user_role", count("select count(*) from tb_user_role where user_seq = ?", userSeq),
                "tb_password_reset_token", count("select count(*) from tb_password_reset_token where user_seq = ?", userSeq),
                "tb_oauth2_client_connection", count("select count(*) from tb_oauth2_client_connection where user_seq = ?", userSeq),
                "oauth2_authorization_consent", count("select count(*) from oauth2_authorization_consent where principal_name = ?", String.valueOf(userSeq)));
    }

    /** 전역 행 — tenant_seq NULL 인 설정·공급자와 그 프로토콜 설정 */
    private Map<String, Integer> globalCounts() {
        return Map.of(
                "tb_share_setting", count("select count(*) from tb_share_setting where tenant_seq is null"),
                "tb_system_config", count("select count(*) from tb_system_config where tenant_seq is null"),
                "tb_sso_provider", count("select count(*) from tb_sso_provider where tenant_seq is null"),
                "tb_sso_oidc_config", count("select count(*) from tb_sso_oidc_config where client_id = 'oidc-global'"));
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }
}
