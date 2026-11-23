package com.thinkfree.drive.api.v1.superadmin.apikey.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.superadmin.apikey.request.SuperAdminApiKeyCreateRequest;
import com.thinkfree.drive.api.v1.superadmin.apikey.request.SuperAdminApiKeyExpireDateUpdateRequest;
import com.thinkfree.drive.api.v1.superadmin.apikey.response.ApiKeyCreateResponse;
import com.thinkfree.drive.api.v1.superadmin.apikey.response.ApiKeyListResponse;
import com.thinkfree.drive.api.v1.superadmin.apikey.response.ApiKeyReissueResponse;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Predicate;

import static com.thinkfree.drive.fixture.ApiKeyPathFixtures.ACTIVATE;
import static com.thinkfree.drive.fixture.ApiKeyPathFixtures.DEACTIVATE;
import static com.thinkfree.drive.fixture.ApiKeyPathFixtures.EXPIRE_DATE;
import static com.thinkfree.drive.fixture.ApiKeyPathFixtures.REISSUE;
import static com.thinkfree.drive.fixture.ApiKeyPathFixtures.SUPER_ADMIN_BASE_URL;
import static com.thinkfree.drive.fixture.ApiKeyScopeFixtures.READ_SCOPES;
import static com.thinkfree.drive.fixture.ApiKeyScopeFixtures.WRITE_SCOPES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@WithMockUserCustom(userSeq = SuperAdminApiKeyControllerTest.LOGIN_USER_SEQ, userId = "superadmin@thinkfree.com", userName = "슈퍼 관리자", roles = "ROLE_SUPER_ADMIN")
@Transactional
class SuperAdminApiKeyControllerTest extends AbstractMvcTest {
    /** 로그인 계정 */
    static final int LOGIN_USER_SEQ = 854;

    /** 키가 속할 테넌트 */
    private static final int TENANT_SEQ = 1;
    /** TENANT_SEQ 에 속한 ADMIN 계정 */
    private static final int ADMIN_USER_SEQ = 1;
    /** TENANT_SEQ 에 속한 USER 계정 */
    private static final int TARGET_USER_SEQ = 41;
    /** TENANT_SEQ 가 아닌 다른 테넌트에 속한 계정 */
    private static final int OTHER_TENANT_USER_SEQ = 956;
    /** 존재하지 않는 테넌트 */
    private static final int MISSING_TENANT_SEQ = 99999;
    /**
     * 정리 대상을 식별하는 접두어. 사람이 우연히 쓸 일이 없는 값이어야 한다 —
     * clearTestApiKeys 가 이 접두어로 DELETE 하므로 실제 키와 겹치면 그대로 지워진다.
     * <p>
     * {@code _} 와 {@code %} 를 넣지 말 것. LIKE 와일드카드라 삭제 범위가 넓어진다.
     */
    private static final String TEST_KEY_NAME_PREFIX = "apikey-it-fixture-a3f19c";

    /** 존재하지 않는 키 */
    private static final int MISSING_API_KEY_SEQ = 99999;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeAll
    static void setUp() {
        initBaseUrl(SUPER_ADMIN_BASE_URL);
    }

    @Test
    @Transactional
    @Disabled("dev DB 고정 데이터 의존(API_KEY_012) — DriveFixtures 로 옮길 때 다시 켠다")
    void create() {
        var request = new SuperAdminApiKeyCreateRequest(TENANT_SEQ, ADMIN_USER_SEQ, TEST_KEY_NAME_PREFIX + " 1", "테스트1", LocalDate.now(), READ_SCOPES, null, null);

        ApiResponse<ApiKeyCreateResponse> response = postForObject(request, new TypeReference<>() {}, status().isCreated());

        assertNotNull(response.getData());
        assertNotNull(response.getData().apiKey());
        assertEquals(CREATED.value(), response.getCode());
    }

    @Test
    @Transactional
    @Disabled("dev DB 고정 데이터 의존(USER_005) — DriveFixtures 로 옮길 때 다시 켠다")
    void create_whenTargetIsUser_returnsApiKey() {
        var request = new SuperAdminApiKeyCreateRequest(TENANT_SEQ, TARGET_USER_SEQ, TEST_KEY_NAME_PREFIX + " 2", "사용자 자격", LocalDate.now(), READ_SCOPES, null, null);

        ApiResponse<ApiKeyCreateResponse> response = postForObject(request, new TypeReference<>() {}, status().isCreated());

        assertNotNull(response.getData().apiKey());
        assertEquals(CREATED.value(), response.getCode());
    }

    /** 본인(슈퍼관리자) 대상 키는 소속 테넌트가 없어 tenant_seq 가 NULL 로 저장된다 */
    @Test
    @Transactional
    @Disabled("dev DB 고정 데이터 의존(USER_005) — DriveFixtures 로 옮길 때 다시 켠다")
    void create_whenTargetIsSelf_returnsApiKey() {
        var request = new SuperAdminApiKeyCreateRequest(null, null, TEST_KEY_NAME_PREFIX + " 3", "본인 키", LocalDate.now(), READ_SCOPES, null, null);

        ApiResponse<ApiKeyCreateResponse> response = postForObject(request, new TypeReference<>() {}, status().isCreated());

        assertNotNull(response.getData().apiKey());
        assertEquals(CREATED.value(), response.getCode());
    }

    @Test
    @Transactional
    @Disabled("dev DB 고정 데이터 의존(USER_005) — DriveFixtures 로 옮길 때 다시 켠다")
    void create_whenTargetIsSelfWithTenantSeq_returnsBadRequest() {
        var request = new SuperAdminApiKeyCreateRequest(TENANT_SEQ, null, TEST_KEY_NAME_PREFIX + " 8", "본인 키에 테넌트 지정", LocalDate.now(), READ_SCOPES, null, null);

        ApiResponse<Void> response = postForObject(request, new TypeReference<>() {}, status().isBadRequest());

        assertFalse(response.isResult());
    }

    @Test
    @Transactional
    @Disabled("dev DB 고정 데이터 의존(API_KEY_012) — DriveFixtures 로 옮길 때 다시 켠다")
    void create_whenTenantSeqIsMissingForAdminTarget_returnsBadRequest() {
        var request = new SuperAdminApiKeyCreateRequest(null, ADMIN_USER_SEQ, TEST_KEY_NAME_PREFIX + " 4", "테넌트 누락", LocalDate.now(), READ_SCOPES, null, null);

        ApiResponse<Void> response = postForObject(request, new TypeReference<>() {}, status().isBadRequest());

        assertFalse(response.isResult());
    }

    @Test
    @Transactional
    @Disabled("dev DB 고정 데이터 의존(API_KEY_012) — DriveFixtures 로 옮길 때 다시 켠다")
    void create_whenTenantNotExists_returnsNotFound() {
        var request = new SuperAdminApiKeyCreateRequest(MISSING_TENANT_SEQ, ADMIN_USER_SEQ, TEST_KEY_NAME_PREFIX + " 5", "없는 테넌트", LocalDate.now(), READ_SCOPES, null, null);

        ApiResponse<Void> response = postForObject(request, new TypeReference<>() {}, status().isNotFound());

        assertFalse(response.isResult());
    }

    @Test
    @Transactional
    @Disabled("dev DB 고정 데이터 의존(USER_005) — DriveFixtures 로 옮길 때 다시 켠다")
    void create_whenTargetUserNotInTenant_returnsBadRequest() {
        var request = new SuperAdminApiKeyCreateRequest(TENANT_SEQ, OTHER_TENANT_USER_SEQ, TEST_KEY_NAME_PREFIX + " 6", "다른 테넌트 계정", LocalDate.now(), READ_SCOPES, null, null);

        ApiResponse<Void> response = postForObject(request, new TypeReference<>() {}, status().isBadRequest());

        assertFalse(response.isResult());
    }

    /** 관리자 대상 키도 scope 를 받는다 — 검사 대상이 아닐 뿐 값은 저장된다 */
    @Test
    @Transactional
    @Disabled("dev DB 고정 데이터 의존(API_KEY_012) — DriveFixtures 로 옮길 때 다시 켠다")
    void create_whenScopeGivenForAdminTarget_returnsApiKey() {
        var request = new SuperAdminApiKeyCreateRequest(TENANT_SEQ, ADMIN_USER_SEQ, TEST_KEY_NAME_PREFIX + " 7", "관리자에 scope", LocalDate.now(), WRITE_SCOPES, null, null);

        ApiResponse<ApiKeyCreateResponse> response = postForObject(request, new TypeReference<>() {}, status().isCreated());

        assertNotNull(response.getData().apiKey());
        assertEquals(CREATED.value(), response.getCode());
    }

    @Test
    @Transactional
    void create_whenScopesIsMissing_returnsBadRequest() {
        var request = new SuperAdminApiKeyCreateRequest(TENANT_SEQ, ADMIN_USER_SEQ, TEST_KEY_NAME_PREFIX + " 9", "scope 누락", LocalDate.now(), null, null, null);

        ApiResponse<Void> response = postForObject(request, new TypeReference<>() {}, status().isBadRequest());

        assertFalse(response.isResult());
    }

    /** 값은 유효하지만 허용 조합이 아니다 — 백엔드가 빠진 값을 채워주지 않으므로 요청이 거부된다 */
    @Test
    @Transactional
    @Disabled("dev DB 고정 데이터 의존(API_KEY_012) — DriveFixtures 로 옮길 때 다시 켠다")
    void create_whenScopeCombinationIsNotAllowed_returnsBadRequest() {
        var request = new SuperAdminApiKeyCreateRequest(TENANT_SEQ, ADMIN_USER_SEQ, TEST_KEY_NAME_PREFIX + " 10", "조합 위반", LocalDate.now(), List.of("api:write"), null, null);

        ApiResponse<Void> response = postForObject(request, new TypeReference<>() {}, status().isBadRequest());

        assertFalse(response.isResult());
    }

    @Test
    void list() {
        var pagingRequest = new CursorPagingRequest(null, null, null, 5);

        ApiResponse<List<ApiKeyListResponse>> response = getForObject(null, pagingRequest, new TypeReference<>() {}, status().isOk());

        assertNotNull(response.getData());
        assertEquals(OK.value(), response.getCode());
    }

    @Test
    void list_containsTenantAndTargetUser() {
        var pagingRequest = new CursorPagingRequest(null, null, null, 5);

        ApiResponse<List<ApiKeyListResponse>> response = getForObject(null, pagingRequest, new TypeReference<>() {}, status().isOk());

        response.getData().forEach(apiKey -> {
            assertNotNull(apiKey.tenantSeq());
            assertNotNull(apiKey.tenantName());
            assertNotNull(apiKey.userSeq());
            assertNotNull(apiKey.userId());
            assertNotNull(apiKey.roleType());
        });
    }

    @Test
    void list_whenTenantFilterGiven_returnsOnlyThatTenant() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("ps", "5");
        params.add("ts", String.valueOf(TENANT_SEQ));

        ApiResponse<List<ApiKeyListResponse>> response =
                getForObjectWithMultiParams(null, params, new TypeReference<>() {}, status().isOk());

        assertNotNull(response.getData());
        response.getData().forEach(apiKey -> assertEquals(TENANT_SEQ, apiKey.tenantSeq()));
    }

    @Test
    void list_whenMine_returnsOnlyKeysRegisteredByLoginUser() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("ps", "100");
        params.add("mine", "true");

        ApiResponse<List<ApiKeyListResponse>> response =
                getForObjectWithMultiParams(null, params, new TypeReference<>() {}, status().isOk());

        response.getData().forEach(apiKey -> {
            assertEquals(LOGIN_USER_SEQ, jdbc.queryForObject(
                    "SELECT register_user_seq FROM tb_api_key WHERE api_key_seq = ?", Integer.class, apiKey.apiKeySeq()));
            assertNotNull(apiKey.userId());
            assertNotNull(apiKey.userName());
        });
    }

    // ── 만료일 수정 ───────────────────────────────────────────────────────────
    // 대상 키 seq 는 개발 DB 상태에 따라 다르므로 상수로 박지 않는다.
    // 이 경로는 조작 범위가 전체라 목록 API 에 나오는 어떤 키든 수정 가능해야 한다.

    private List<ApiKeyListResponse> listedKeys() {
        var pagingRequest = new CursorPagingRequest(null, null, null, 100);
        ApiResponse<List<ApiKeyListResponse>> response =
                getForObject(null, pagingRequest, new TypeReference<>() {}, status().isOk());
        return response.getData();
    }

    private int listedKeySeq(Predicate<ApiKeyListResponse> condition, String skipReason) {
        var seq = listedKeys().stream()
                .filter(condition)
                .map(ApiKeyListResponse::apiKeySeq)
                .findFirst();
        assumeTrue(seq.isPresent(), skipReason);
        return seq.get();
    }

    /** 슈퍼관리자 키는 tenant_seq 가 NULL 이라 테넌트 조건이 붙은 쿼리로는 잡히지 않는다 */
    @Test
    @Transactional
    void updateExpireDate_whenKeyIsSuperAdminRole_returnsOk() {
        int apiKeySeq = listedKeySeq(key -> RoleType.SUPER_ADMIN == key.roleType(),
                "SUPER_ADMIN role 키가 없어 건너뜀");

        String url = StringUtil.buildUrl(apiKeySeq, EXPIRE_DATE);
        var request = new SuperAdminApiKeyExpireDateUpdateRequest(LocalDate.now().plusYears(1));

        ApiResponse<Void> response = patchForObject(url, request, new TypeReference<>() {}, status().isOk());

        assertEquals(OK.value(), response.getCode());
    }

    /** 테넌트 무관 전체가 대상 — 조작 범위가 목록 조회 범위와 같다 */
    @Test
    @Transactional
    void updateExpireDate_whenKeyBelongsToTenant_returnsOk() {
        int apiKeySeq = listedKeySeq(key -> key.tenantSeq() != null, "테넌트 소속 키가 없어 건너뜀");

        String url = StringUtil.buildUrl(apiKeySeq, EXPIRE_DATE);
        var request = new SuperAdminApiKeyExpireDateUpdateRequest(LocalDate.now().plusYears(1));

        ApiResponse<Void> response = patchForObject(url, request, new TypeReference<>() {}, status().isOk());

        assertEquals(OK.value(), response.getCode());
    }

    /** 만료된 키도 연장으로 되살릴 수 있다 */
    @Test
    @Transactional
    void updateExpireDate_whenKeyAlreadyExpired_returnsOk() {
        int apiKeySeq = listedKeySeq(key -> true, "키가 없어 건너뜀");
        jdbc.update("UPDATE tb_api_key SET expire_date = ? WHERE api_key_seq = ?",
                LocalDateTime.now().minusDays(1), apiKeySeq);

        String url = StringUtil.buildUrl(apiKeySeq, EXPIRE_DATE);
        var request = new SuperAdminApiKeyExpireDateUpdateRequest(LocalDate.now().plusYears(1));

        ApiResponse<Void> response = patchForObject(url, request, new TypeReference<>() {}, status().isOk());

        assertEquals(OK.value(), response.getCode());
    }

    @Test
    @Transactional
    void updateExpireDate_whenExpireDateIsPast_returnsBadRequest() {
        String url = StringUtil.buildUrl(listedKeySeq(key -> true, "키가 없어 건너뜀"), EXPIRE_DATE);
        var request = new SuperAdminApiKeyExpireDateUpdateRequest(LocalDate.now().minusDays(1));

        ApiResponse<Void> response = patchForObject(url, request, new TypeReference<>() {}, status().isBadRequest());

        assertFalse(response.isResult());
    }

    @Test
    @Transactional
    void updateExpireDate_whenExpireDateIsMissing_returnsBadRequest() {
        String url = StringUtil.buildUrl(listedKeySeq(key -> true, "키가 없어 건너뜀"), EXPIRE_DATE);
        var request = new SuperAdminApiKeyExpireDateUpdateRequest(null);

        ApiResponse<Void> response = patchForObject(url, request, new TypeReference<>() {}, status().isBadRequest());

        assertFalse(response.isResult());
    }

    @Test
    @Transactional
    void updateExpireDate_whenKeyNotExists_returnsNotFound() {
        String url = StringUtil.buildUrl(MISSING_API_KEY_SEQ, EXPIRE_DATE);
        var request = new SuperAdminApiKeyExpireDateUpdateRequest(LocalDate.now().plusYears(1));

        ApiResponse<Void> response = patchForObject(url, request, new TypeReference<>() {}, status().isNotFound());

        assertFalse(response.isResult());
    }

    // ── 활성화 / 비활성화 / 재발급 ────────────────────────────────────────────
    // 대상 범위는 만료일 수정과 같다 — 테넌트·role 무관 전체.

    /** 슈퍼관리자 키는 tenant_seq 가 NULL 이라 테넌트 조건이 붙은 쿼리로는 잡히지 않는다 */
    @Test
    @Transactional
    void activate_whenKeyIsSuperAdminRole_returnsOk() {
        int apiKeySeq = listedKeySeq(key -> RoleType.SUPER_ADMIN == key.roleType(),
                "SUPER_ADMIN role 키가 없어 건너뜀");

        ApiResponse<Void> response = patchForObject(StringUtil.buildUrl(apiKeySeq, ACTIVATE), null,
                new TypeReference<>() {}, status().isOk());

        assertEquals(OK.value(), response.getCode());
    }

    @Test
    @Transactional
    void deactivate_whenKeyBelongsToTenant_returnsOk() {
        int apiKeySeq = listedKeySeq(key -> key.tenantSeq() != null, "테넌트 소속 키가 없어 건너뜀");

        ApiResponse<Void> response = patchForObject(StringUtil.buildUrl(apiKeySeq, DEACTIVATE), null,
                new TypeReference<>() {}, status().isOk());

        assertEquals(OK.value(), response.getCode());
    }

    @Test
    @Transactional
    void reissue() {
        int apiKeySeq = listedKeySeq(key -> true, "키가 없어 건너뜀");

        ApiResponse<ApiKeyReissueResponse> response = postForObject(StringUtil.buildUrl(apiKeySeq, REISSUE),
                new TypeReference<>() {}, status().isOk());

        assertNotNull(response.getData().apiKey());
        assertEquals(OK.value(), response.getCode());
    }

    @Test
    @Transactional
    void activate_whenKeyNotExists_returnsNotFound() {
        ApiResponse<Void> response = patchForObject(StringUtil.buildUrl(MISSING_API_KEY_SEQ, ACTIVATE), null,
                new TypeReference<>() {}, status().isNotFound());

        assertFalse(response.isResult());
    }

    @Test
    @Transactional
    void reissue_whenKeyNotExists_returnsNotFound() {
        ApiResponse<Void> response = postForObject(StringUtil.buildUrl(MISSING_API_KEY_SEQ, REISSUE),
                new TypeReference<>() {}, status().isNotFound());

        assertFalse(response.isResult());
    }

    /**
     * 남은 테스트 키 정리 — {@code @Transactional} 을 주석 처리해 실행했을 때만 필요하다.
     * 발급 API 에 대응하는 삭제 API 가 없어 직접 지운다.
     * <p>
     * 이름만으로 지우면 같은 이름의 실제 키까지 지워지므로 이 테스트가 만든 것으로 한정한다 —
     * 발급자(register_user_seq)와 테넌트까지 일치해야 삭제.
     */
    @Test
    void clearTestApiKeys() {
        // 본인 대상 키는 tenant_seq 가 NULL 이라 등호로는 걸리지 않는다
        int deleted = jdbc.update(
                "DELETE FROM tb_api_key WHERE key_name LIKE ? AND register_user_seq = ? AND (tenant_seq = ? OR tenant_seq IS NULL)",
                TEST_KEY_NAME_PREFIX + "%", LOGIN_USER_SEQ, TENANT_SEQ);

        log.info("삭제된 테스트 API Key : {}건", deleted);
    }

}
