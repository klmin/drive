package com.thinkfree.drive.global.security;

import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.apikey.enums.ApiKeyScope;
import com.thinkfree.drive.apikey.exception.ApiKeyErrorCode;
import com.thinkfree.drive.auth.exception.AuthErrorCode;
import com.thinkfree.drive.common.DriveFixtures;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import com.thinkfree.drive.user.enums.RoleType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;

import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 유효한 키는 테스트마다 기본 테넌트에 일반 사용자(USER 역할)를 만들고 그 명의로 새로 만든다.
 * 클래스 전체를 @Transactional 로 묶어(@Nested 도 물려받음) 키 행과 인증 성공 때의 last_used_date 갱신을 되돌린다.
 */
@Slf4j
@DisplayName("SecurityFilterChain 라우팅 및 인증 테스트")
@Transactional
class ApiKeyAuthenticationFilterTest extends AbstractMvcTest {

    private String validApiKey;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void issueValidApiKey() {
        var fixtures = DriveFixtures.of(jdbc);
        int user = fixtures.user(DriveFixtures.DEFAULT_TENANT, RoleType.USER, "키 사용자");
        validApiKey = fixtures.apiKey(DriveFixtures.DEFAULT_TENANT, user, RoleType.USER, EnumSet.of(ApiKeyScope.READ));
    }

    @Nested
    @DisplayName("External API - ApiKeyAuthenticationEntryPoint (401)")
    class EntryPointTest {

        private static final String EXTERNAL_URL = "/api/public/v1/test/ping";

        @Test
        @DisplayName("Authorization 헤더 없음 → 401 + API_KEY_INVALID")
        void noAuthHeader_returns401() throws Exception {
            mockMvc.perform(withHost(MockMvcRequestBuilders.get(EXTERNAL_URL))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value(ApiKeyErrorCode.API_KEY_INVALID.getErrorCode()))
                    .andExpect(jsonPath("$.message").value(ApiKeyErrorCode.API_KEY_INVALID.getMessage()));
        }

        @Test
        @DisplayName("Bearer prefix 없이 전송 → 401 + API_KEY_INVALID")
        void noBearerPrefix_returns401() throws Exception {
            mockMvc.perform(withHost(MockMvcRequestBuilders.get(EXTERNAL_URL))
                            .header("Authorization", "some-api-key")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value(ApiKeyErrorCode.API_KEY_INVALID.getErrorCode()));
        }

        @Test
        @DisplayName("Bearer + 빈 값 → 401 + API_KEY_INVALID")
        void emptyBearerValue_returns401() throws Exception {
            mockMvc.perform(withHost(MockMvcRequestBuilders.get(EXTERNAL_URL))
                            .header("Authorization", "Bearer  ")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value(ApiKeyErrorCode.API_KEY_INVALID.getErrorCode()));
        }

        @Test
        @DisplayName("유효하지 않은 ApiKey → 401 + API_KEY_INVALID")
        void invalidApiKey_returns401() throws Exception {
            mockMvc.perform(withHost(MockMvcRequestBuilders.get(EXTERNAL_URL))
                            .header("Authorization", "Bearer invalid-key-value-12345")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value(ApiKeyErrorCode.API_KEY_INVALID.getErrorCode()))
                    .andExpect(jsonPath("$.message").value(ApiKeyErrorCode.API_KEY_INVALID.getMessage()));
        }
    }
    
    @Nested
    @DisplayName("External API - 인증 성공")
    class SuccessTest {

        private static final String EXTERNAL_URL = "/api/public/v1/test/ping";

        /** 인증에 성공하면 필터가 last_used_date 를 갱신한다 — 롤백하지 않으면 실제 키의 값이 바뀐다 */
        @Test
        @Transactional
        @DisplayName("유효한 ApiKey → 200")
        void validApiKey_returns200() throws Exception {
            mockMvc.perform(withHost(MockMvcRequestBuilders.get(EXTERNAL_URL))
                            .header("Authorization", "Bearer " + validApiKey)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk());
        }
    }
    
    @Nested
    @DisplayName("Internal API - JWT 체인 분리 확인")
    class InternalApiFilterChain {

        @Test
        @DisplayName("Whitelist 경로 → 인증 없이 200")
        void whitelist_returns200() throws Exception {
            mockMvc.perform(withHost(MockMvcRequestBuilders.get("/api/v1/common/ping"))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("withMock → 기존 filter 인증 성공")
        @WithMockUserCustom
        void share_status_returns200() throws Exception {
            mockMvc.perform(withHost(MockMvcRequestBuilders.get("/api/v1/admins/system-config/shares/exists"))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("쿠키 인증 성공 + 유효하지 않은 ApiKey → 쿠키 계정으로 200 (쿠키 우선)")
        @WithMockUserCustom
        void cookieAuthenticated_invalidApiKey_returns200() throws Exception {
            mockMvc.perform(withHost(MockMvcRequestBuilders.get("/api/v1/admins/system-config/shares/exists"))
                            .header("Authorization", "Bearer invalid-key-value-12345")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk());
        }

        /** USER 키로 인증됐다면 관리자 경로는 403 — 200 이면 키를 보지 않고 쿠키(ADMIN) 계정으로 처리된 것 */
        @Test
        @Transactional
        @DisplayName("쿠키(ADMIN) 인증 성공 + 유효한 USER ApiKey → 쿠키 계정으로 관리자 경로 200 (쿠키 우선)")
        @WithMockUserCustom
        void cookieAuthenticated_validUserApiKey_keepsCookieAccount() throws Exception {
            mockMvc.perform(withHost(MockMvcRequestBuilders.get("/api/v1/admins/system-config/shares/exists"))
                            .header("Authorization", "Bearer " + validApiKey)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("인증 필요 경로 + 토큰 없음 → 401 (JWT EntryPoint)")
        void protectedPath_noToken_returns401() throws Exception {
            mockMvc.perform(withHost(MockMvcRequestBuilders.get("/api/v1/files"))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value(AuthErrorCode.AUTH_INVALID_TOKEN.getErrorCode()));
        }

        /** 인증에 성공하면 필터가 last_used_date 를 갱신한다 — 롤백하지 않으면 실제 키의 값이 바뀐다 */
        @Test
        @Transactional
        @DisplayName("인증 필요 경로 + 유효한 ApiKey → 200 (내부 체인 API Key 인증)")
        void protectedPath_validApiKey_returns200() throws Exception {
            mockMvc.perform(withHost(MockMvcRequestBuilders.get("/api/v1/users"))
                            .header("Authorization", "Bearer " + validApiKey)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk());
        }

        @Test
        @Transactional
        @DisplayName("유효한 ApiKey + 키 역할과 같은 X-Drive-Role → 200")
        void protectedPath_validApiKeyWithSameRoleHeader_returns200() throws Exception {
            mockMvc.perform(withHost(MockMvcRequestBuilders.get("/api/v1/users"))
                            .header("Authorization", "Bearer " + validApiKey)
                            .header("X-Drive-Role", "USER")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("유효한 ApiKey + 키 역할과 다른 X-Drive-Role → 401 + API_KEY_INVALID")
        void protectedPath_validApiKeyWithOtherRoleHeader_returns401() throws Exception {
            mockMvc.perform(withHost(MockMvcRequestBuilders.get("/api/v1/users"))
                            .header("Authorization", "Bearer " + validApiKey)
                            .header("X-Drive-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value(ApiKeyErrorCode.API_KEY_INVALID.getErrorCode()));
        }

        /** 키는 요청 주소의 테넌트로만 조회된다 — 같은 키라도 다른 테넌트 주소면 인증되지 않는다 */
        @Test
        @Transactional
        @DisplayName("다른 테넌트 주소 + 유효한 ApiKey → 401 + API_KEY_INVALID")
        void protectedPath_validApiKeyOnOtherTenant_returns401() throws Exception {
            String slug = "apikey-other-" + System.nanoTime();
            jdbc.update("INSERT INTO tb_tenant (tenant_name, domain, subdomain, register_user_seq) VALUES (?, ?, ?, 1)",
                    slug, slug + ".drive.thinkfree.com", slug);

            setTestTenant(slug);
            try {
                mockMvc.perform(withHost(MockMvcRequestBuilders.get("/api/v1/users"))
                                .header("Authorization", "Bearer " + validApiKey)
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.errorCode").value(ApiKeyErrorCode.API_KEY_INVALID.getErrorCode()));
            } finally {
                setTestHost(DEFAULT_TEST_HOST);
            }
        }

        @Test
        @DisplayName("인증 필요 경로 + 유효하지 않은 ApiKey → 401 + API_KEY_INVALID")
        void protectedPath_invalidApiKey_returns401() throws Exception {
            mockMvc.perform(withHost(MockMvcRequestBuilders.get("/api/v1/users"))
                            .header("Authorization", "Bearer invalid-key-value-12345")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value(ApiKeyErrorCode.API_KEY_INVALID.getErrorCode()));
        }
    }
    
}
