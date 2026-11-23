package com.thinkfree.drive.api.v1.user.apikey.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.user.apikey.request.UserApiKeyCreateRequest;
import com.thinkfree.drive.api.v1.user.apikey.request.UserApiKeyExpireDateUpdateRequest;
import com.thinkfree.drive.api.v1.user.apikey.response.ApiKeyCreateResponse;
import com.thinkfree.drive.api.v1.user.apikey.response.ApiKeyListResponse;
import com.thinkfree.drive.api.v1.user.apikey.response.ApiKeyReissueResponse;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import com.thinkfree.drive.global.time.DriveTime;
import com.thinkfree.drive.util.StringUtil;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import static com.thinkfree.drive.fixture.ApiKeyPathFixtures.ACTIVATE;
import static com.thinkfree.drive.fixture.ApiKeyPathFixtures.DEACTIVATE;
import static com.thinkfree.drive.fixture.ApiKeyPathFixtures.EXPIRE_DATE;
import static com.thinkfree.drive.fixture.ApiKeyPathFixtures.REISSUE;
import static com.thinkfree.drive.fixture.ApiKeyPathFixtures.USER_BASE_URL;
import static com.thinkfree.drive.fixture.ApiKeyScopeFixtures.READ_SCOPES;
import static com.thinkfree.drive.fixture.ApiKeyScopeFixtures.WRITE_SCOPES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** tenantSeq=1 에 속한 활성 USER 계정으로 테스트 진행 */
@Slf4j
@WithMockUserCustom(userSeq = UserApiKeyControllerTest.LOGIN_USER_SEQ, userId = "user@thinkfree.com", userName = "사용자", roles = "ROLE_USER")
@Transactional
class UserApiKeyControllerTest extends AbstractMvcTest {
    /** 로그인 계정 */
    static final int LOGIN_USER_SEQ = 41;
    /** LOGIN_USER_SEQ 가 속한 테넌트 */
    private static final int TENANT_SEQ = 1;
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
    @Autowired
    private EntityManager em;

    @BeforeAll
    static void setUp() {
        initBaseUrl(USER_BASE_URL);
    }

    @Test
    @Transactional
    @Disabled("dev DB 고정 데이터 의존(USER_005) — DriveFixtures 로 옮길 때 다시 켠다")
    void create() {
        var request = new UserApiKeyCreateRequest(TEST_KEY_NAME_PREFIX + " 1", "테스트1", LocalDate.now(), WRITE_SCOPES);

        ApiResponse<ApiKeyCreateResponse> response = postForObject(request, new TypeReference<>() {}, status().isCreated());

        assertNotNull(response.getData());
        assertNotNull(response.getData().apiKey());
        assertEquals(CREATED.value(), response.getCode());
    }

    @Test
    @Transactional
    @Disabled("dev DB 고정 데이터 의존(USER_005) — DriveFixtures 로 옮길 때 다시 켠다")
    void create_whenScopeIsReadOnly_returnsApiKey() {
        var request = new UserApiKeyCreateRequest(TEST_KEY_NAME_PREFIX + " 2", "읽기 전용", LocalDate.now(), READ_SCOPES);

        ApiResponse<ApiKeyCreateResponse> response = postForObject(request, new TypeReference<>() {}, status().isCreated());

        assertNotNull(response.getData().apiKey());
        assertEquals(CREATED.value(), response.getCode());
    }

    @Test
    @Transactional
    void create_whenScopesIsEmpty_returnsBadRequest() {
        var request = new UserApiKeyCreateRequest(TEST_KEY_NAME_PREFIX + " 3", "scope 누락", LocalDate.now(), List.of());

        ApiResponse<Void> response = postForObject(request, new TypeReference<>() {}, status().isBadRequest());

        assertFalse(response.isResult());
    }

    @Test
    @Transactional
    @Disabled("dev DB 고정 데이터 의존(USER_005) — DriveFixtures 로 옮길 때 다시 켠다")
    void create_whenScopeIsUnsupported_returnsBadRequest() {
        var request = new UserApiKeyCreateRequest(TEST_KEY_NAME_PREFIX + " 4", "미지원 scope", LocalDate.now(), List.of("unknown:scope"));

        ApiResponse<Void> response = postForObject(request, new TypeReference<>() {}, status().isBadRequest());

        assertFalse(response.isResult());
    }

    /** 값은 유효하지만 허용 조합이 아니다 — 백엔드가 빠진 값을 채워주지 않으므로 요청이 거부된다 */
    @Test
    @Transactional
    @Disabled("dev DB 고정 데이터 의존(USER_005) — DriveFixtures 로 옮길 때 다시 켠다")
    void create_whenScopeCombinationIsNotAllowed_returnsBadRequest() {
        var request = new UserApiKeyCreateRequest(TEST_KEY_NAME_PREFIX + " 7", "조합 위반", LocalDate.now(), List.of("api:write"));

        ApiResponse<Void> response = postForObject(request, new TypeReference<>() {}, status().isBadRequest());

        assertFalse(response.isResult());
    }

    @Test
    @Transactional
    void create_whenExpireDateIsPast_returnsBadRequest() {
        var request = new UserApiKeyCreateRequest(TEST_KEY_NAME_PREFIX + " 5", "만료일 과거", LocalDate.now().minusDays(1), READ_SCOPES);

        ApiResponse<Void> response = postForObject(request, new TypeReference<>() {}, status().isBadRequest());

        assertFalse(response.isResult());
    }

    @Test
    @Transactional
    void create_whenExpireDateIsMissing_returnsBadRequest() {
        var request = new UserApiKeyCreateRequest(TEST_KEY_NAME_PREFIX + " 6", "만료일 누락", null, READ_SCOPES);

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
    void list_containsScopeAndExpiry() {
        var pagingRequest = new CursorPagingRequest(null, null, null, 5);

        ApiResponse<List<ApiKeyListResponse>> response = getForObject(null, pagingRequest, new TypeReference<>() {}, status().isOk());

        response.getData().forEach(apiKey -> {
            assertNotNull(apiKey.scopes());
            assertNotNull(apiKey.expireDate());
            assertNotNull(apiKey.maskedApiKey());
        });
    }

    // ── 만료일 수정 ───────────────────────────────────────────────────────────
    // 대상 키 seq 는 개발 DB 상태에 따라 다르므로 상수로 박지 않는다.
    // 조작 범위 == 목록 조회 범위(register_user_seq 기준)이므로 목록 API 에서 고른다.

    /** 이 경로가 조작할 수 있어야 하는 키 집합 — 본인이 발급한 키 */
    private int ownKeySeq() {
        var pagingRequest = new CursorPagingRequest(null, null, null, 100);
        ApiResponse<List<ApiKeyListResponse>> response =
                getForObject(null, pagingRequest, new TypeReference<>() {}, status().isOk());

        var seq = response.getData().stream().map(ApiKeyListResponse::apiKeySeq).findFirst();
        assumeTrue(seq.isPresent(), "본인이 발급한 키가 없어 건너뜀");
        return seq.get();
    }

    private int otherRegisterUserKeySeq() {
        var found = jdbc.queryForList(
                "SELECT api_key_seq FROM tb_api_key WHERE register_user_seq <> ? LIMIT 1",
                Integer.class, LOGIN_USER_SEQ);
        assumeTrue(!found.isEmpty(), "남이 발급한 키가 없어 건너뜀");
        return found.get(0);
    }

    @Test
    @Transactional
    void updateExpireDate() {
        int apiKeySeq = ownKeySeq();
        String url = StringUtil.buildUrl(apiKeySeq, EXPIRE_DATE);
        LocalDate expireDate = LocalDate.now().plusYears(1);
        var request = new UserApiKeyExpireDateUpdateRequest(expireDate);

        ApiResponse<Void> response = patchForObject(url, request, new TypeReference<>() {}, status().isOk());

        assertEquals(OK.value(), response.getCode());
        // 날짜만 받아 그 날 23:59:59 로 저장
        em.flush();
        OffsetDateTime saved = jdbc.queryForObject("SELECT expire_date FROM tb_api_key WHERE api_key_seq = ?",
                OffsetDateTime.class, apiKeySeq);
        assertEquals(DriveTime.endOfDay(expireDate), saved.toInstant());
    }

    /** 만료된 키도 연장으로 되살릴 수 있다 */
    @Test
    @Transactional
    void updateExpireDate_whenKeyAlreadyExpired_returnsOk() {
        int apiKeySeq = ownKeySeq();
        jdbc.update("UPDATE tb_api_key SET expire_date = ? WHERE api_key_seq = ?",
                LocalDateTime.now().minusDays(1), apiKeySeq);

        String url = StringUtil.buildUrl(apiKeySeq, EXPIRE_DATE);
        var request = new UserApiKeyExpireDateUpdateRequest(LocalDate.now().plusYears(1));

        ApiResponse<Void> response = patchForObject(url, request, new TypeReference<>() {}, status().isOk());

        assertEquals(OK.value(), response.getCode());
    }

    @Test
    @Transactional
    void updateExpireDate_whenExpireDateIsPast_returnsBadRequest() {
        String url = StringUtil.buildUrl(ownKeySeq(), EXPIRE_DATE);
        var request = new UserApiKeyExpireDateUpdateRequest(LocalDate.now().minusDays(1));

        ApiResponse<Void> response = patchForObject(url, request, new TypeReference<>() {}, status().isBadRequest());

        assertFalse(response.isResult());
    }

    @Test
    @Transactional
    void updateExpireDate_whenExpireDateIsMissing_returnsBadRequest() {
        String url = StringUtil.buildUrl(ownKeySeq(), EXPIRE_DATE);
        var request = new UserApiKeyExpireDateUpdateRequest(null);

        ApiResponse<Void> response = patchForObject(url, request, new TypeReference<>() {}, status().isBadRequest());

        assertFalse(response.isResult());
    }

    /**
     * 남이 발급한 키는 같은 테넌트라도 수정 불가 — 대상은 register_user_seq 기준이라 목록에도 보이지 않는다.
     * 관리자가 이 사용자를 대상으로 대신 만들어준 키도 여기에 해당한다.
     */
    @Test
    @Transactional
    void updateExpireDate_whenKeyRegisteredByOther_returnsNotFound() {
        String url = StringUtil.buildUrl(otherRegisterUserKeySeq(), EXPIRE_DATE);
        var request = new UserApiKeyExpireDateUpdateRequest(LocalDate.now().plusYears(1));

        ApiResponse<Void> response = patchForObject(url, request, new TypeReference<>() {}, status().isNotFound());

        assertFalse(response.isResult());
    }

    @Test
    @Transactional
    void updateExpireDate_whenKeyNotExists_returnsNotFound() {
        String url = StringUtil.buildUrl(MISSING_API_KEY_SEQ, EXPIRE_DATE);
        var request = new UserApiKeyExpireDateUpdateRequest(LocalDate.now().plusYears(1));

        ApiResponse<Void> response = patchForObject(url, request, new TypeReference<>() {}, status().isNotFound());

        assertFalse(response.isResult());
    }

    // ── 활성화 / 비활성화 / 재발급 ────────────────────────────────────────────
    // 대상 범위는 만료일 수정과 같다 — 본인이 발급한 키.

    @Test
    @Transactional
    void activate() {
        String url = StringUtil.buildUrl(ownKeySeq(), ACTIVATE);

        ApiResponse<Void> response = patchForObject(url, null, new TypeReference<>() {}, status().isOk());

        assertEquals(OK.value(), response.getCode());
    }

    @Test
    @Transactional
    void deactivate() {
        String url = StringUtil.buildUrl(ownKeySeq(), DEACTIVATE);

        ApiResponse<Void> response = patchForObject(url, null, new TypeReference<>() {}, status().isOk());

        assertEquals(OK.value(), response.getCode());
    }

    @Test
    @Transactional
    void reissue() {
        String url = StringUtil.buildUrl(ownKeySeq(), REISSUE);

        ApiResponse<ApiKeyReissueResponse> response = postForObject(url, new TypeReference<>() {}, status().isOk());

        assertNotNull(response.getData().apiKey());
        assertEquals(OK.value(), response.getCode());
    }

    @Test
    @Transactional
    void deactivate_whenKeyRegisteredByOther_returnsNotFound() {
        String url = StringUtil.buildUrl(otherRegisterUserKeySeq(), DEACTIVATE);

        ApiResponse<Void> response = patchForObject(url, null, new TypeReference<>() {}, status().isNotFound());

        assertFalse(response.isResult());
    }

    @Test
    @Transactional
    void reissue_whenKeyRegisteredByOther_returnsNotFound() {
        String url = StringUtil.buildUrl(otherRegisterUserKeySeq(), REISSUE);

        ApiResponse<Void> response = postForObject(url, new TypeReference<>() {}, status().isNotFound());

        assertFalse(response.isResult());
    }

    @Test
    @Transactional
    void activate_whenKeyNotExists_returnsNotFound() {
        String url = StringUtil.buildUrl(MISSING_API_KEY_SEQ, ACTIVATE);

        ApiResponse<Void> response = patchForObject(url, null, new TypeReference<>() {}, status().isNotFound());

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
        int deleted = jdbc.update(
                "DELETE FROM tb_api_key WHERE key_name LIKE ? AND register_user_seq = ? AND tenant_seq = ?",
                TEST_KEY_NAME_PREFIX + "%", LOGIN_USER_SEQ, TENANT_SEQ);

        log.info("삭제된 테스트 API Key : {}건", deleted);
    }

}
