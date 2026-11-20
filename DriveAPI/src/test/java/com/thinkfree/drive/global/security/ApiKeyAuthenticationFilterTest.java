package com.thinkfree.drive.global.security;

import org.junit.jupiter.api.Disabled;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.apikey.exception.ApiKeyErrorCode;
import com.thinkfree.drive.auth.exception.AuthErrorCode;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@DisplayName("SecurityFilterChain 라우팅 및 인증 테스트")
class ApiKeyAuthenticationFilterTest extends AbstractMvcTest {

    private static final String VALID_API_KEY = "B6u6gFwHhyBum9Js_LRrcISG54dEC-HpZ45E4gtlvsQ";

    @Autowired
    private JdbcTemplate jdbc;
    
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
        @Disabled("dev DB 데이터 의존 — 유효한 API 키 필요(test 프로필 Testcontainers 에 없음). 테스트 픽스처로 옮길 때 다시 켠다")
        void validApiKey_returns200() throws Exception {
            mockMvc.perform(withHost(MockMvcRequestBuilders.get(EXTERNAL_URL))
                            .header("Authorization", "Bearer " + VALID_API_KEY)
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
                            .header("Authorization", "Bearer " + VALID_API_KEY)
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
        @Disabled("dev DB 데이터 의존 — 유효한 API 키 필요(test 프로필 Testcontainers 에 없음). 테스트 픽스처로 옮길 때 다시 켠다")
        void protectedPath_validApiKey_returns200() throws Exception {
            mockMvc.perform(withHost(MockMvcRequestBuilders.get("/api/v1/users"))
                            .header("Authorization", "Bearer " + VALID_API_KEY)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk());
        }

        @Test
        @Transactional
        @DisplayName("유효한 ApiKey + 키 역할과 같은 X-Drive-Role → 200")
        @Disabled("dev DB 데이터 의존 — 유효한 API 키 필요(test 프로필 Testcontainers 에 없음). 테스트 픽스처로 옮길 때 다시 켠다")
        void protectedPath_validApiKeyWithSameRoleHeader_returns200() throws Exception {
            mockMvc.perform(withHost(MockMvcRequestBuilders.get("/api/v1/users"))
                            .header("Authorization", "Bearer " + VALID_API_KEY)
                            .header("X-Drive-Role", "USER")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("유효한 ApiKey + 키 역할과 다른 X-Drive-Role → 401 + API_KEY_INVALID")
        void protectedPath_validApiKeyWithOtherRoleHeader_returns401() throws Exception {
            mockMvc.perform(withHost(MockMvcRequestBuilders.get("/api/v1/users"))
                            .header("Authorization", "Bearer " + VALID_API_KEY)
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
                                .header("Authorization", "Bearer " + VALID_API_KEY)
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
