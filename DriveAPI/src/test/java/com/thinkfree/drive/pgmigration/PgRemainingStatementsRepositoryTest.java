package com.thinkfree.drive.pgmigration;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.api.v1.auth.enums.LoginType;
import com.thinkfree.drive.branding.entity.Branding;
import com.thinkfree.drive.branding.repository.BrandingRepository;
import com.thinkfree.drive.branding.repository.BrandingQueryRepository;
import com.thinkfree.drive.cleanup.trash.query.TrashCleanUpByDateQuery;
import com.thinkfree.drive.cleanup.trash.query.TrashCleanUpByUserQuery;
import com.thinkfree.drive.cleanup.trash.repository.TrashCleanUpQueryRepository;
import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.oauth2.connection.repository.OAuth2ClientConnectionRepository;
import com.thinkfree.drive.resource.entity.Resource;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.repository.ResourceRepository;
import com.thinkfree.drive.share.entity.Share;
import com.thinkfree.drive.share.enums.ShareType;
import com.thinkfree.drive.share.query.SharedResourceFindQuery;
import com.thinkfree.drive.share.repository.ShareRepository;
import com.thinkfree.drive.share.repository.ShareQueryRepository;
import com.thinkfree.drive.sso.protocol.jwt.entity.SsoJwtConfig;
import com.thinkfree.drive.sso.protocol.jwt.enums.KeySourceType;
import com.thinkfree.drive.sso.protocol.jwt.repository.SsoJwtConfigRepository;
import com.thinkfree.drive.sso.protocol.jwt.repository.SsoJwtConfigQueryRepository;
import com.thinkfree.drive.sso.protocol.oidc.entity.SsoOidcConfig;
import com.thinkfree.drive.sso.protocol.oidc.repository.SsoOidcConfigRepository;
import com.thinkfree.drive.sso.provider.entity.SsoProvider;
import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;
import com.thinkfree.drive.sso.provider.enums.SsoProviderScope;
import com.thinkfree.drive.sso.provider.enums.SsoProviderStatus;
import com.thinkfree.drive.sso.provider.query.SsoProviderDisplayNameCheckQuery;
import com.thinkfree.drive.sso.provider.query.SsoProviderListQuery;
import com.thinkfree.drive.sso.provider.repository.SsoProviderRepository;
import com.thinkfree.drive.sso.provider.repository.SsoProviderQueryRepository;
import com.thinkfree.drive.superadmin.loginsetting.entity.SuperAdminLoginSetting;
import com.thinkfree.drive.superadmin.loginsetting.repository.SuperAdminLoginSettingQueryRepository;
import com.thinkfree.drive.superadmin.loginsetting.repository.SuperAdminLoginSettingRepository;
import com.thinkfree.drive.trash.entity.Trash;
import com.thinkfree.drive.trash.repository.TrashRepository;
import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.user.quota.alert.state.entity.UserQuotaAlertState;
import com.thinkfree.drive.user.quota.alert.state.repository.UserQuotaAlertStateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

import static com.thinkfree.drive.fixture.UserFixture.TEST_USER_01;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * PG 전환(TFDRIVE-1747) 1단계에서 런타임(웹/MCP) 으로는 태울 수 없었던 statement 를 Testcontainers PG 에서 직접 실행한다.
 * 대상 = 관리자 SSO/브랜딩/OAuth 연결 화면·스케줄러·bulk 등록에서만 불리는 upsert(ON CONFLICT)·FOR UPDATE·UNION·윈도우 함수 statement.
 * 검증 포인트 = ON CONFLICT 충돌키가 실제 유니크 제약과 맞는지(2회 호출 → 행 1개·값 갱신), Y/N boolean·bytea·timestamptz 왕복.
 * 시드(initialize.sql): tenant 1, user 1(superadmin)·2(admin, tenant 1)·3(anonymous). 감사 인터셉터는 SecurityContext 가 없으면
 * 사용자 컬럼을 건드리지 않으므로 registerUserSeq 는 명시한다(날짜 컬럼은 인터셉터가 채움).
 */
@DisplayName("PG 전환 — 런타임 미도달 statement 직접 실행(Testcontainers)")
class PgRemainingStatementsRepositoryTest extends RepositoryTest {

    private static final int TENANT_SEQ = 1;
    private static final int ADMIN_SEQ = 2;

    @Autowired JdbcTemplate jdbc;
    @Autowired SsoProviderRepository ssoProviderRepository;
    @Autowired SsoProviderQueryRepository ssoProviderQueryRepository;
    @Autowired SsoJwtConfigRepository ssoJwtConfigRepository;
    @Autowired SsoJwtConfigQueryRepository ssoJwtConfigQueryRepository;
    @Autowired SsoOidcConfigRepository ssoOidcConfigRepository;
    @Autowired SuperAdminLoginSettingRepository superAdminLoginSettingRepository;
    @Autowired SuperAdminLoginSettingQueryRepository superAdminLoginSettingQueryRepository;
    @Autowired BrandingRepository brandingRepository;
    @Autowired BrandingQueryRepository brandingQueryRepository;
    @Autowired OAuth2ClientConnectionRepository oauth2ClientConnectionRepository;
    @Autowired UserQuotaAlertStateRepository userQuotaAlertStateRepository;
    @Autowired TrashCleanUpQueryRepository trashCleanUpQueryRepository;
    @Autowired ShareRepository shareRepository;
    @Autowired ShareQueryRepository shareQueryRepository;
    @Autowired ResourceRepository resourceRepository;
    @Autowired TrashRepository trashRepository;

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private SsoProvider newProvider(String name, Integer seq, SsoProviderStatus status, int sortOrder) {
        return SsoProvider.builder()
                .ssoProviderSeq(seq).tenantSeq(TENANT_SEQ).displayName(name)
                .protocolType(SsoProviderProtocolType.OIDC).status(status).scope(SsoProviderScope.TENANT)
                .sortOrder(sortOrder).registerUserSeq(ADMIN_SEQ).build();
    }

    private boolean existsDisplayName(SsoProviderScope scope, Integer tenantSeq, String name, Integer excludeSeq) {
        return ssoProviderQueryRepository.existsDisplayName(SsoProviderDisplayNameCheckQuery.builder()
                .scope(scope).tenantSeq(tenantSeq).displayName(name).excludeSsoProviderSeq(excludeSeq).build());
    }

    private int upsertProvider(String name) {
        Integer seq = ssoProviderRepository.upsert(newProvider(name, null, SsoProviderStatus.ACTIVE, 1));
        assertThat(seq).as("keyColumn 으로 identity 회수(반환값 — W4)").isNotNull();
        return seq;
    }

    @Test
    @DisplayName("DefaultSsoProviderRepository.upsert — 같은 seq 2회 → 행 1개·값 갱신(ON CONFLICT (sso_provider_seq)) / findAll 커서(SSO_PROVIDER_SEQ < cursorId)")
    void ssoProvider_upsert_and_cursorList() {
        int seq = upsertProvider("pg-sso-a");
        ssoProviderRepository.upsert(newProvider("pg-sso-a2", seq, SsoProviderStatus.INACTIVE, 7));

        assertThat(count("SELECT COUNT(*) FROM tb_sso_provider WHERE sso_provider_seq = ?", seq)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT display_name FROM tb_sso_provider WHERE sso_provider_seq = ?", String.class, seq)).isEqualTo("pg-sso-a2");
        assertThat(jdbc.queryForObject("SELECT status FROM tb_sso_provider WHERE sso_provider_seq = ?", String.class, seq)).isEqualTo("INACTIVE");
        assertThat(jdbc.queryForObject("SELECT sort_order FROM tb_sso_provider WHERE sso_provider_seq = ?", Integer.class, seq)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT update_user_seq FROM tb_sso_provider WHERE sso_provider_seq = ?", Integer.class, seq)).isEqualTo(ADMIN_SEQ);

        int seq2 = upsertProvider("pg-sso-b");

        var firstPage = ssoProviderQueryRepository.findAll(SsoProviderListQuery.builder()
                .tenantSeq(TENANT_SEQ).page(CursorPaginationQuery.of(null, null, null, 10)).build());
        assertThat(firstPage).extracting("ssoProviderSeq").contains(seq, seq2);
        assertThat(firstPage.stream().filter(d -> d.getSsoProviderSeq() == seq).findFirst().orElseThrow().getTenantName()).isEqualTo("기본 테넌트");

        var afterCursor = ssoProviderQueryRepository.findAll(SsoProviderListQuery.builder()
                .tenantSeq(TENANT_SEQ).page(CursorPaginationQuery.of(null, (long) seq2, null, 10)).build());
        assertThat(afterCursor).extracting("ssoProviderSeq").contains(seq).doesNotContain(seq2);

        // existsDisplayName — uniq_sso_provider_scope_tenant_name 과 같은 키. tenant 분기·GLOBAL(IS NULL) 분기·자기 제외·대소문자 구분
        assertThat(existsDisplayName(SsoProviderScope.TENANT, TENANT_SEQ, "pg-sso-b", null)).isTrue();
        assertThat(existsDisplayName(SsoProviderScope.TENANT, TENANT_SEQ, "pg-sso-b", seq2)).as("자기 자신 제외").isFalse();
        assertThat(existsDisplayName(SsoProviderScope.TENANT, TENANT_SEQ, "pg-sso-b", seq)).as("다른 공급자가 쓰는 이름").isTrue();
        assertThat(existsDisplayName(SsoProviderScope.TENANT, TENANT_SEQ + 1, "pg-sso-b", null)).as("다른 테넌트").isFalse();
        assertThat(existsDisplayName(SsoProviderScope.GLOBAL, null, "pg-sso-b", null)).as("GLOBAL(tenant IS NULL) 분기").isFalse();
        assertThat(existsDisplayName(SsoProviderScope.TENANT, TENANT_SEQ, "PG-SSO-B", null)).as("대소문자 구분(deterministic 콜레이션)").isFalse();
    }

    @Test
    @DisplayName("SsoJwtConfigMapper.upsert — provider 당 1행(ON CONFLICT (sso_provider_seq)), JWKS 체크 제약 통과, findByIssuer")
    void ssoJwtConfig_upsert() {
        int seq = upsertProvider("pg-jwt");
        String issuer1 = "https://idp.example.test/" + seq;
        String issuer2 = issuer1 + "/v2";
        ssoJwtConfigRepository.upsert(SsoJwtConfig.builder().ssoProviderSeq(seq).issuer(issuer1)
                .keySourceType(KeySourceType.JWKS).jwksUri("https://idp.example.test/jwks").emailClaimName("email").registerUserSeq(ADMIN_SEQ).build());
        ssoJwtConfigRepository.upsert(SsoJwtConfig.builder().ssoProviderSeq(seq).issuer(issuer2).audience("drive")
                .keySourceType(KeySourceType.JWKS).jwksUri("https://idp.example.test/jwks2").emailClaimName("mail").registerUserSeq(ADMIN_SEQ).build());

        assertThat(count("SELECT COUNT(*) FROM tb_sso_jwt_config WHERE sso_provider_seq = ?", seq)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT issuer FROM tb_sso_jwt_config WHERE sso_provider_seq = ?", String.class, seq)).isEqualTo(issuer2);
        assertThat(jdbc.queryForObject("SELECT audience FROM tb_sso_jwt_config WHERE sso_provider_seq = ?", String.class, seq)).isEqualTo("drive");
        assertThat(ssoJwtConfigQueryRepository.findByIssuer(issuer2)).isNotNull();
        assertThat(ssoJwtConfigQueryRepository.findByIssuer(issuer1)).isNull();
    }

    @Test
    @DisplayName("SsoOidcConfigMapper.upsert — provider 당 1행, EXCLUDED 갱신, allowIdpInitiated Y/N 왕복")
    void ssoOidcConfig_upsert() {
        int seq = upsertProvider("pg-oidc");
        ssoOidcConfigRepository.upsert(SsoOidcConfig.builder().ssoProviderSeq(seq).issuerUrl("https://oidc.example.test")
                .clientId("client-1").clientSecret("secret-1").scopes("openid email").allowIdpInitiated(false).registerUserSeq(ADMIN_SEQ).build());
        ssoOidcConfigRepository.upsert(SsoOidcConfig.builder().ssoProviderSeq(seq).issuerUrl("https://oidc.example.test")
                .clientId("client-2").clientSecret("secret-2").scopes("openid email profile").allowIdpInitiated(true).registerUserSeq(ADMIN_SEQ).updateUserSeq(ADMIN_SEQ).build());

        assertThat(count("SELECT COUNT(*) FROM tb_sso_oidc_config WHERE sso_provider_seq = ?", seq)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT client_id FROM tb_sso_oidc_config WHERE sso_provider_seq = ?", String.class, seq)).isEqualTo("client-2");
        assertThat(jdbc.queryForObject("SELECT allow_idp_initiated FROM tb_sso_oidc_config WHERE sso_provider_seq = ?", String.class, seq)).isEqualTo("Y");
    }

    @Test
    @DisplayName("SuperAdminLoginSettingRepository.upsert / upsertEnforce(JdbcClient 이관 후에도 유지) — 싱글턴(seq=1) 행 1개, enforce 만 갱신 시 loginType 보존")
    void superAdminLoginSetting_upsert_then_upsertEnforce() {
        superAdminLoginSettingRepository.upsert(SuperAdminLoginSetting.builder().loginType(LoginType.OIDC).isEnforced(false).registerUserSeq(ADMIN_SEQ).build());
        superAdminLoginSettingRepository.upsertEnforce(SuperAdminLoginSetting.builder().isEnforced(true).registerUserSeq(ADMIN_SEQ).build());

        assertThat(count("SELECT COUNT(*) FROM tb_super_admin_login_setting")).isEqualTo(1);
        var data = superAdminLoginSettingQueryRepository.find();
        assertThat(data.isEnforced()).isTrue();
        assertThat(data.loginType()).isEqualTo(LoginType.OIDC);
        assertThat(data.updateUserSeq()).isEqualTo(ADMIN_SEQ);
    }

    @Test
    @DisplayName("BrandingMapper.upsertTextOnly / upsertWithImages — 테넌트 당 1행(ON CONFLICT (tenant_seq)), bytea 왕복")
    void branding_upsert() {
        brandingRepository.upsertTextOnly(Branding.builder().tenantSeq(TENANT_SEQ).tabTitle("Drive").serviceName("Drive").useDefault(true).registerUserSeq(ADMIN_SEQ).build());
        brandingRepository.upsertTextOnly(Branding.builder().tenantSeq(TENANT_SEQ).tabTitle("Drive2").serviceName("Drive2").useDefault(false).registerUserSeq(ADMIN_SEQ).build());
        assertThat(count("SELECT COUNT(*) FROM tb_branding WHERE tenant_seq = ?", TENANT_SEQ)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT service_name FROM tb_branding WHERE tenant_seq = ?", String.class, TENANT_SEQ)).isEqualTo("Drive2");
        assertThat(jdbc.queryForObject("SELECT use_default FROM tb_branding WHERE tenant_seq = ?", String.class, TENANT_SEQ)).isEqualTo("N");

        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0, 1, 2, 3};
        brandingRepository.upsertWithImages(Branding.builder().tenantSeq(TENANT_SEQ).tabTitle("Drive3").serviceName("Drive3").useDefault(false)
                .faviconData(png).faviconContentType("image/png").logoIconData(png).logoIconContentType("image/png")
                .logoImageData(png).logoImageContentType("image/png").emailLogoData(png).emailLogoContentType("image/png")
                .registerUserSeq(ADMIN_SEQ).build());
        assertThat(count("SELECT COUNT(*) FROM tb_branding WHERE tenant_seq = ?", TENANT_SEQ)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT favicon_data FROM tb_branding WHERE tenant_seq = ?", byte[].class, TENANT_SEQ)).isEqualTo(png);
        assertThat(jdbc.queryForObject("SELECT email_logo_content_type FROM tb_branding WHERE tenant_seq = ?", String.class, TENANT_SEQ)).isEqualTo("image/png");
        assertThat(brandingQueryRepository.findMeta(TENANT_SEQ).getServiceName()).isEqualTo("Drive3");
    }

    @Test
    @DisplayName("OAuth2ClientConnectionMapper.upsert — (user_seq, client_id) 당 1행, EXCLUDED 로 scopes·token_issued_date 갱신, 날짜는 인터셉터 주입")
    void oauth2ClientConnection_upsert() {
        oauth2ClientConnectionRepository.upsert(ADMIN_SEQ, "pg-client", "mcp:read");
        oauth2ClientConnectionRepository.upsert(ADMIN_SEQ, "pg-client", "mcp:read mcp:write");

        assertThat(count("SELECT COUNT(*) FROM tb_oauth2_client_connection WHERE user_seq = ? AND client_id = ?", ADMIN_SEQ, "pg-client")).isEqualTo(1);
        var found = oauth2ClientConnectionRepository.findByUserSeqAndClientId(ADMIN_SEQ, "pg-client");
        assertThat(found).isNotNull();
        assertThat(found.getScopes()).isEqualTo("mcp:read mcp:write");
        assertThat(found.getRegisterDate()).isNotNull();
        assertThat(found.getTokenIssuedDate()).isNotNull();
    }

    @Test
    @DisplayName("UserQuotaAlertStateMapper.upsert — user_seq 당 1행(파티션 테이블), lockLastThresholdPercentByUserSeq(FOR UPDATE, 스칼라) 로 갱신값 확인")
    void userQuotaAlertState_upsert_and_lock() {
        userQuotaAlertStateRepository.upsert(UserQuotaAlertState.builder().userSeq(ADMIN_SEQ).lastThresholdPercent(80).build());
        userQuotaAlertStateRepository.upsert(UserQuotaAlertState.builder().userSeq(ADMIN_SEQ).lastThresholdPercent(90).updateUserSeq(ADMIN_SEQ).updateDate(Instant.now()).build());

        assertThat(count("SELECT COUNT(*) FROM tb_user_quota_alert_state WHERE user_seq = ?", ADMIN_SEQ)).isEqualTo(1);
        assertThat(userQuotaAlertStateRepository.lockLastThresholdPercentByUserSeq(ADMIN_SEQ)).isEqualTo(90);
    }

    @Test
    @DisplayName("UserMapper.findByUserSeqListForUpdate → lockUsers(@Lock JPQL IN, W7)")
    void user_findByUserSeqListForUpdate() {
        User u = TEST_USER_01.toUser();
        userRepository.insertUserWithSeatCheck(u, false, 0);
        List<User> locked = userRepository.lockUsers(List.of(ADMIN_SEQ, u.getUserSeq()));
        assertThat(locked).extracting(User::getUserSeq).containsExactlyInAnyOrder(ADMIN_SEQ, u.getUserSeq());
    }

    private Resource folder(int userSeq, String name, Long parentSeq, String fullPathSeq, String fullPath) {
        Resource r = Resource.builder().userSeq(userSeq).ownerSeq(userSeq).resourceType(ResourceType.FOLDER).parentFolderSeq(parentSeq)
                .resourceName(name).originalFileName(name).fullPathSeq(fullPathSeq).fullPath(fullPath).copyNumber(0).registerUserSeq(userSeq).build();
        resourceRepository.save(r);
        assertThat(r.getResourceSeq()).isNotNull();
        return r;
    }

    private Resource file(int userSeq, String name, Long parentSeq, String fullPathSeq, String fullPath) {
        Resource r = Resource.builder().userSeq(userSeq).ownerSeq(userSeq).latestOwnerSeq(userSeq).resourceType(ResourceType.FILE).parentFolderSeq(parentSeq)
                .resourceName(name).originalFileName(name).fullPathSeq(fullPathSeq).fullPath(fullPath).copyNumber(0)
                .ext("txt").contentType("text/plain").sizeByte(5L).versionRegisterDate(Instant.now()).registerUserSeq(userSeq).build();
        resourceRepository.save(r);
        assertThat(r.getResourceSeq()).isNotNull();
        return r;
    }

    @Test
    @DisplayName("DefaultTrashCleanUpQueryRepository.findTrashCleanUpTargets(구 TrashCleanUpMapper) — 휴지통 루트 + DELETE_PARENT_FOLDER_SEQ 하위(UNION ALL 재귀), 사용자/날짜 두 파라미터 타입")
    void trashCleanUp_findTargets() {
        Resource root = folder(ADMIN_SEQ, "pg-trash", null, "", "pg-trash/");
        Resource child = file(ADMIN_SEQ, "in-trash.txt", root.getResourceSeq(), root.getResourceSeq() + "/", "pg-trash/in-trash.txt");
        jdbc.update("UPDATE tb_resource SET is_delete = 'Y' WHERE resource_seq = ?", root.getResourceSeq());
        jdbc.update("UPDATE tb_resource SET is_delete = 'Y', delete_parent_folder_seq = ? WHERE resource_seq = ?", root.getResourceSeq(), child.getResourceSeq());
        trashRepository.save(Trash.builder().userSeq(ADMIN_SEQ).resourceSeq(root.getResourceSeq()).registerUserSeq(ADMIN_SEQ).build());

        var byUser = trashCleanUpQueryRepository.findTrashCleanUpTargets(new TrashCleanUpByUserQuery(ADMIN_SEQ, true));
        assertThat(byUser).isNotEmpty();
        var target = byUser.stream().filter(t -> t.getUserSeq() == ADMIN_SEQ).findFirst().orElseThrow();
        assertThat(target.getResourceTargets()).extracting("resourceSeq").contains(root.getResourceSeq(), child.getResourceSeq());

        var byDate = trashCleanUpQueryRepository.findTrashCleanUpTargets(new TrashCleanUpByDateQuery(Instant.now().plusSeconds(60), true));
        assertThat(byDate).extracting("userSeq").contains(ADMIN_SEQ);
    }

    @Test
    @DisplayName("ShareMapper.findAncestorSharesByResource — 재귀 조상 탐색 + ROW_NUMBER() OVER: 손자 파일에서 루트 폴더 공유를 찾는다")
    void share_findAncestorSharesByResource() {
        Resource root = folder(ADMIN_SEQ, "pg-share", null, "", "pg-share/");
        Resource mid = folder(ADMIN_SEQ, "mid", root.getResourceSeq(), root.getResourceSeq() + "/", "pg-share/mid/");
        Resource leaf = file(ADMIN_SEQ, "leaf.txt", mid.getResourceSeq(), root.getResourceSeq() + "/" + mid.getResourceSeq() + "/", "pg-share/mid/leaf.txt");
        Share share = Share.builder().userSeq(ADMIN_SEQ).resourceSeq(root.getResourceSeq()).shareType(ShareType.ORG_LINK)
                .canView(true).canEdit(false).canDownload(true).canUpload(false).canDelete(false).canShare(false)
                .isPasswordEnabled(false).registerUserSeq(ADMIN_SEQ).build();
        shareRepository.save(share);
        assertThat(share.getShareSeq()).isNotNull();

        var found = shareQueryRepository.findAncestorSharesByResource(SharedResourceFindQuery.builder()
                .resourceSeq(leaf.getResourceSeq()).userSeq(ADMIN_SEQ).isDelete(false).build());
        assertThat(found).isNotEmpty();
        assertThat(found).extracting("shareSeq").contains(share.getShareSeq());
        assertThat(found).extracting("resourceSeq").contains(root.getResourceSeq());
        assertThat(found.stream().filter(d -> d.getShareSeq().equals(share.getShareSeq())).findFirst().orElseThrow().getShareType()).isEqualTo(ShareType.ORG_LINK);
    }
}
