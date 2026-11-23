package com.thinkfree.drive.api.v1.admin.apikey.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.admin.apikey.request.AdminApiKeyCreateRequest;
import com.thinkfree.drive.api.v1.admin.apikey.request.AdminApiKeyExpireDateUpdateRequest;
import com.thinkfree.drive.api.v1.admin.apikey.response.ApiKeyCreateResponse;
import com.thinkfree.drive.api.v1.admin.apikey.response.ApiKeyListResponse;
import com.thinkfree.drive.api.v1.admin.apikey.response.ApiKeyReissueResponse;
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

import static com.thinkfree.drive.fixture.ApiKeyPathFixtures.ACTIVATE;
import static com.thinkfree.drive.fixture.ApiKeyPathFixtures.ADMIN_BASE_URL;
import static com.thinkfree.drive.fixture.ApiKeyPathFixtures.DEACTIVATE;
import static com.thinkfree.drive.fixture.ApiKeyPathFixtures.EXPIRE_DATE;
import static com.thinkfree.drive.fixture.ApiKeyPathFixtures.REISSUE;
import static com.thinkfree.drive.fixture.ApiKeyScopeFixtures.READ_SCOPES;
import static com.thinkfree.drive.fixture.ApiKeyScopeFixtures.WRITE_SCOPES;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@WithMockUserCustom(userSeq = AdminApiKeyControllerTest.LOGIN_USER_SEQ)
@Transactional
class AdminApiKeyControllerTest extends AbstractMvcTest {
    /** 로그인 계정 */
    static final int LOGIN_USER_SEQ = 1;
    /** LOGIN_USER_SEQ 가 속한 테넌트 */
    private static final int TENANT_SEQ = 1;

    /** 로그인 관리자(userSeq=1)와 같은 테넌트의 USER 계정 */
    private static final int TARGET_USER_SEQ = 41;
    /** 다른 테넌트에 속한 계정 */
    private static final int OTHER_TENANT_USER_SEQ = 956;
    /** 슈퍼관리자 계정 — 관리자 경로 발급 차단 검증용 */
    static final int SUPER_ADMIN_USER_SEQ = 854;
    /** 이 테스트가 만든 키임을 알아볼 수 있게 이름 앞에 붙이는 접두어 */
    private static final String TEST_KEY_NAME_PREFIX = "apikey-it-fixture-a3f19c";

    /** 존재하지 않는 키 */
    private static final int MISSING_API_KEY_SEQ = 99999;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeAll
    static void setUp() {
        initBaseUrl(ADMIN_BASE_URL);
    }

    // ── 대상 키 선택 ──────────────────────────────────────────────────────────
    // 조작 대상 seq 를 상수로 박지 않는다. 개발 DB 의 seq 는 환경마다 다르고, 이 경로의 대상 테넌트는
    // @WithMockUserCustom 이 아니라 Host 헤더 해석 결과라 더더욱 고정할 수 없다.
    // 조작 범위 == 목록 조회 범위이므로 목록 API 가 곧 "조작 가능해야 하는 키 집합" 의 정의다.

    /** 이 경로가 조작할 수 있어야 하는 키 집합 */
    private List<ApiKeyListResponse> listedKeys() {
        var pagingRequest = new CursorPagingRequest(null, null, null, 100);
        ApiResponse<List<ApiKeyListResponse>> response =
                getForObject(null, pagingRequest, new TypeReference<>() {}, status().isOk());
        return response.getData();
    }

    private int listedKeySeq(RoleType roleType) {
        var seq = listedKeys().stream()
                .filter(key -> roleType == key.roleType())
                .map(ApiKeyListResponse::apiKeySeq)
                .findFirst();
        assumeTrue(seq.isPresent(), () -> "목록에 " + roleType + " role 키가 없어 건너뜀");
        return seq.get();
    }

    /** 현재 요청 테넌트 — 목록에 나온 키가 곧 그 테넌트 소속이다 */
    private int currentTenantSeq() {
        return jdbc.queryForObject("SELECT tenant_seq FROM tb_api_key WHERE api_key_seq = ?",
                Integer.class, listedKeySeq(RoleType.ADMIN));
    }

    private int otherTenantKeySeq() {
        var found = jdbc.queryForList(
                "SELECT api_key_seq FROM tb_api_key WHERE tenant_seq IS NOT NULL AND tenant_seq <> ? LIMIT 1",
                Integer.class, currentTenantSeq());
        assumeTrue(!found.isEmpty(), "다른 테넌트 키가 없어 건너뜀");
        return found.get(0);
    }

    @Test
    void list() {
        Integer ps = 5;
        Long ci = null;
        Object cv = null;

        var cursorPagingRequest = new CursorPagingRequest(cv, ci, null, ps);
        ApiResponse<List<ApiKeyListResponse>> response = getForObject(null, cursorPagingRequest, new TypeReference<>() {}, status().isOk());

        assertNotNull(response.getData());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
    }

    @Test
    void list_containsTargetUserAndRoleType() {
        var cursorPagingRequest = new CursorPagingRequest(null, null, null, 5);

        ApiResponse<List<ApiKeyListResponse>> response = getForObject(null, cursorPagingRequest, new TypeReference<>() {}, status().isOk());

        response.getData().forEach(apiKey -> {
            assertNotNull(apiKey.userSeq());
            assertNotNull(apiKey.userId());
            assertNotNull(apiKey.roleType());
            assertNotNull(apiKey.scopes());
            assertNotNull(apiKey.expireDate());
        });
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

    @Test
    @Transactional
    @Disabled("dev DB 고정 데이터 의존(RESPONSE_001) — DriveFixtures 로 옮길 때 다시 켠다")
    void create() {
        //setTestHost("default-add.drive.thinkfree.com");
        var request = new AdminApiKeyCreateRequest(null, TEST_KEY_NAME_PREFIX + " 1", "테스트1", LocalDate.now().plusYears(1), READ_SCOPES);

        ApiResponse<ApiKeyCreateResponse> response = postForObject(request, new TypeReference<>() {}, status().isCreated());

        assertNotNull(response.getData());
        assertNotNull(response.getData().apiKey());
        assertEquals(CREATED.value(), response.getCode());
    }

    @Test
    @Transactional
    void create_whenKeyNameIsBlank_returnsBadRequest() {
        var request = new AdminApiKeyCreateRequest(null, "  ", "사내 CRUD 테스트", LocalDate.now().plusYears(1), READ_SCOPES);

        ApiResponse<Void> response = postForObject(request, new TypeReference<>() {}, status().isBadRequest());
        assertFalse(response.isResult());
        
    }

    @Test
    @Transactional
    void create_whenKeyPurposeIsBlank_returnsBadRequest() {
        var request = new AdminApiKeyCreateRequest(null, "TotalPay 테스트", "  ", LocalDate.now().plusYears(1), READ_SCOPES);

        ApiResponse<Void> response = postForObject(request, new TypeReference<>() {}, status().isBadRequest());
        assertFalse(response.isResult());

    }

    @Test
    @Transactional
    @Disabled("dev DB 고정 데이터 의존(USER_005) — DriveFixtures 로 옮길 때 다시 켠다")
    void create_whenTargetIsUser_returnsApiKey() {
        var request = new AdminApiKeyCreateRequest(TARGET_USER_SEQ, TEST_KEY_NAME_PREFIX + " 2", "사용자 자격", LocalDate.now(), READ_SCOPES);

        ApiResponse<ApiKeyCreateResponse> response = postForObject(request, new TypeReference<>() {}, status().isCreated());

        assertNotNull(response.getData().apiKey());
        assertEquals(CREATED.value(), response.getCode());
    }

    /** 관리자 대상 키도 scope 를 받는다 — 검사 대상이 아닐 뿐 값은 저장된다 */
    @Test
    @Transactional
    @Disabled("dev DB 고정 데이터 의존(RESPONSE_001) — DriveFixtures 로 옮길 때 다시 켠다")
    void create_whenScopeGivenForAdminTarget_returnsApiKey() {
        var request = new AdminApiKeyCreateRequest(null, TEST_KEY_NAME_PREFIX + " 3", "관리자에 scope", LocalDate.now(), WRITE_SCOPES);

        ApiResponse<ApiKeyCreateResponse> response = postForObject(request, new TypeReference<>() {}, status().isCreated());

        assertNotNull(response.getData().apiKey());
        assertEquals(CREATED.value(), response.getCode());
    }

    @Test
    @Transactional
    void create_whenScopesIsMissing_returnsBadRequest() {
        var request = new AdminApiKeyCreateRequest(TARGET_USER_SEQ, TEST_KEY_NAME_PREFIX + " 4", "scope 누락", LocalDate.now(), null);

        ApiResponse<Void> response = postForObject(request, new TypeReference<>() {}, status().isBadRequest());

        assertFalse(response.isResult());
    }

    /** 값은 유효하지만 허용 조합이 아니다 — 백엔드가 빠진 값을 채워주지 않으므로 요청이 거부된다 */
    @Test
    @Transactional
    @Disabled("dev DB 고정 데이터 의존(USER_005) — DriveFixtures 로 옮길 때 다시 켠다")
    void create_whenScopeCombinationIsNotAllowed_returnsBadRequest() {
        var request = new AdminApiKeyCreateRequest(TARGET_USER_SEQ, TEST_KEY_NAME_PREFIX + " 8", "조합 위반", LocalDate.now(), List.of("api:write"));

        ApiResponse<Void> response = postForObject(request, new TypeReference<>() {}, status().isBadRequest());

        assertFalse(response.isResult());
    }

    /** 없는 계정과 같은 404 — userSeq 를 훑어 다른 테넌트의 계정 존재 여부를 알아내지 못하게 한다 */
    @Test
    @Transactional
    void create_whenTargetUserInOtherTenant_returnsNotFound() {
        var request = new AdminApiKeyCreateRequest(OTHER_TENANT_USER_SEQ, TEST_KEY_NAME_PREFIX + " 5", "다른 테넌트 계정", LocalDate.now(), READ_SCOPES);

        ApiResponse<Void> response = postForObject(request, new TypeReference<>() {}, status().isNotFound());

        assertFalse(response.isResult());
    }

    /** 슈퍼관리자는 진입 키로 관리자 화면에 들어와도 이 경로로는 발급할 수 없다 */
    @Test
    @Transactional
    @WithMockUserCustom(userSeq = SUPER_ADMIN_USER_SEQ, userId = "superadmin@thinkfree.com", userName = "슈퍼 관리자", roles = "ROLE_SUPER_ADMIN")
    void create_whenLoginIsSuperAdmin_returnsForbidden() {
        var request = new AdminApiKeyCreateRequest(null, TEST_KEY_NAME_PREFIX + " 7", "슈퍼관리자 경로 발급", LocalDate.now(), READ_SCOPES);

        ApiResponse<Void> response = postForObject(request, new TypeReference<>() {}, status().isForbidden());

        assertFalse(response.isResult());
    }

    @Test
    @Transactional
    void create_whenExpireDateIsPast_returnsBadRequest() {
        var request = new AdminApiKeyCreateRequest(null, TEST_KEY_NAME_PREFIX + " 6", "만료일 과거", LocalDate.now().minusDays(1), READ_SCOPES);

        ApiResponse<Void> response = postForObject(request, new TypeReference<>() {}, status().isBadRequest());

        assertFalse(response.isResult());
    }

    @Test
    @Transactional
    void activate() {
        //setTestHost("default-add.drive.thinkfree.com");
        String url = StringUtil.buildUrl(listedKeySeq(RoleType.ADMIN), ACTIVATE);

        ApiResponse<Void> response = patchForObject(url, null, new TypeReference<>() {}, status().isOk());

        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
    }
    
    @Test
    @Transactional
    void deactivate() {
        String url = StringUtil.buildUrl(listedKeySeq(RoleType.ADMIN), DEACTIVATE);

        ApiResponse<Void> response = patchForObject(url, null, new TypeReference<>() {}, status().isOk());

        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
    }

    @Test
    @Transactional
    void updateExpireDate() {
        String url = StringUtil.buildUrl(listedKeySeq(RoleType.ADMIN), EXPIRE_DATE);
        var request = new AdminApiKeyExpireDateUpdateRequest(LocalDate.now().plusYears(1));

        ApiResponse<Void> response = patchForObject(url, request, new TypeReference<>() {}, status().isOk());

        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
    }

    /** 만료된 키도 연장으로 되살릴 수 있다 — 만료 판정이 조회 시점 NOW() 비교라 status 복구가 필요 없다 */
    @Test
    @Transactional
    void updateExpireDate_whenKeyAlreadyExpired_returnsOk() {
        int apiKeySeq = listedKeySeq(RoleType.ADMIN);
        jdbc.update("UPDATE tb_api_key SET expire_date = ? WHERE api_key_seq = ?",
                LocalDateTime.now().minusDays(1), apiKeySeq);

        String url = StringUtil.buildUrl(apiKeySeq, EXPIRE_DATE);
        var request = new AdminApiKeyExpireDateUpdateRequest(LocalDate.now().plusYears(1));

        ApiResponse<Void> response = patchForObject(url, request, new TypeReference<>() {}, status().isOk());

        assertEquals(OK.value(), response.getCode());
    }

    /** 자기 테넌트의 USER role 키도 수정 대상 — 조작 범위가 목록 조회 범위와 같다 */
    @Test
    @Transactional
    void updateExpireDate_whenTargetIsUserRoleKey_returnsOk() {
        String url = StringUtil.buildUrl(listedKeySeq(RoleType.USER), EXPIRE_DATE);
        var request = new AdminApiKeyExpireDateUpdateRequest(LocalDate.now().plusYears(1));

        ApiResponse<Void> response = patchForObject(url, request, new TypeReference<>() {}, status().isOk());

        assertEquals(OK.value(), response.getCode());
    }

    @Test
    @Transactional
    void updateExpireDate_whenExpireDateIsPast_returnsBadRequest() {
        String url = StringUtil.buildUrl(listedKeySeq(RoleType.ADMIN), EXPIRE_DATE);
        var request = new AdminApiKeyExpireDateUpdateRequest(LocalDate.now().minusDays(1));

        ApiResponse<Void> response = patchForObject(url, request, new TypeReference<>() {}, status().isBadRequest());

        assertFalse(response.isResult());
    }

    @Test
    @Transactional
    void updateExpireDate_whenExpireDateIsMissing_returnsBadRequest() {
        String url = StringUtil.buildUrl(listedKeySeq(RoleType.ADMIN), EXPIRE_DATE);
        var request = new AdminApiKeyExpireDateUpdateRequest(null);

        ApiResponse<Void> response = patchForObject(url, request, new TypeReference<>() {}, status().isBadRequest());

        assertFalse(response.isResult());
    }

    /** 다른 테넌트 키는 @TenantApiKey 가 컨트롤러 진입 시점에 404 로 막는다 */
    @Test
    @Transactional
    void updateExpireDate_whenKeyInOtherTenant_returnsNotFound() {
        String url = StringUtil.buildUrl(otherTenantKeySeq(), EXPIRE_DATE);
        var request = new AdminApiKeyExpireDateUpdateRequest(LocalDate.now().plusYears(1));

        ApiResponse<Void> response = patchForObject(url, request, new TypeReference<>() {}, status().isNotFound());

        assertFalse(response.isResult());
    }

    @Test
    @Transactional
    void updateExpireDate_whenKeyNotExists_returnsNotFound() {
        String url = StringUtil.buildUrl(MISSING_API_KEY_SEQ, EXPIRE_DATE);
        var request = new AdminApiKeyExpireDateUpdateRequest(LocalDate.now().plusYears(1));

        ApiResponse<Void> response = patchForObject(url, request, new TypeReference<>() {}, status().isNotFound());

        assertFalse(response.isResult());
    }

    /** 자기 테넌트의 USER role 키도 조작 대상 — 조작 범위가 목록 조회 범위와 같다 */
    @Test
    @Transactional
    void deactivate_whenTargetIsUserRoleKey_returnsOk() {
        String url = StringUtil.buildUrl(listedKeySeq(RoleType.USER), DEACTIVATE);

        ApiResponse<Void> response = patchForObject(url, null, new TypeReference<>() {}, status().isOk());

        assertEquals(OK.value(), response.getCode());
    }

    /** 다른 테넌트 키는 @TenantApiKey 가 컨트롤러 진입 시점에 404 로 막는다 */
    @Test
    @Transactional
    void deactivate_whenKeyInOtherTenant_returnsNotFound() {
        String url = StringUtil.buildUrl(otherTenantKeySeq(), DEACTIVATE);

        ApiResponse<Void> response = patchForObject(url, null, new TypeReference<>() {}, status().isNotFound());

        assertFalse(response.isResult());
    }

    /** 변경 건수 0 은 실패가 아니라 "없음" — 만료일 수정과 같은 404 로 통일 */
    @Test
    @Transactional
    void reissue_whenKeyNotExists_returnsNotFound() {
        String url = StringUtil.buildUrl(MISSING_API_KEY_SEQ, REISSUE);

        ApiResponse<Void> response = postForObject(url, new TypeReference<>() {}, status().isNotFound());

        assertFalse(response.isResult());
    }

    @Test
    @Transactional
    void reissue() {
        String url = StringUtil.buildUrl(listedKeySeq(RoleType.ADMIN), REISSUE);

        ApiResponse<ApiKeyReissueResponse> response = postForObject(url, new TypeReference<>() {}, status().isOk());

        assertNotNull(response.getData());
        assertNotNull(response.getData().apiKey());
        assertEquals(OK.value(), response.getCode());
    }
    

}
