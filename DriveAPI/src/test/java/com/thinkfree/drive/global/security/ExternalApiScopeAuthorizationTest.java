package com.thinkfree.drive.global.security;

import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.apikey.enums.ApiKeyScope;
import com.thinkfree.drive.apikey.exception.ApiKeyErrorCode;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import com.thinkfree.drive.global.enums.AuthSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 외부 체인({@code /api/public/**})의 API Key scope 검사 검증.
 * <p>
 * 검사는 2단이다 — 체인 게이트가 {@code api:*} 를 하나라도 요구해 읽기 권한을 겸하고,
 * 쓰기 endpoint 만 {@code @RequiredApiScope(WRITE)} 로 따로 본다.
 * <p>
 * 인가는 핸들러 매핑보다 먼저 일어나므로 "허용" 은 200 이 아니라 <b>403 이 아님</b> 으로 단언한다 —
 * 본문 없는 POST 는 400 이 나지만 그것도 인가를 통과했다는 뜻이다.
 */
@DisplayName("외부 API scope 인가")
class ExternalApiScopeAuthorizationTest extends AbstractMvcTest {

    /** 요구 scope READ */
    private static final String READ_TARGET = "/api/public/v1/resources";
    /** 요구 scope WRITE */
    private static final String WRITE_TARGET = "/api/public/v1/documents";
    /** 요구 scope 선언 없음 — 게이트만 통과하면 되는 경로 */
    private static final String NO_SCOPE_TARGET = "/api/public/v1/notifications";

    /** {@code AbstractMvcTest.testHost} 는 static 이라 다른 테스트가 바꿔 둔 값이 남는다 —
     *  호스트가 다르면 TenantBoundaryFilter 가 인가 전에 403 을 내므로 매번 고정한다 */
    @BeforeEach
    void pinTestHost() {
        setTestHost(DEFAULT_TEST_HOST);
    }

    private void expectAllowed(String url) throws Exception {
        mockMvc.perform(withHost(MockMvcRequestBuilders.get(url)))
                .andExpect(result -> assertNotEquals(
                        HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus(),
                        url + " 는 이 scope 로 인가를 통과해야 한다"));
    }

    private void expectWriteAllowed() throws Exception {
        mockMvc.perform(withHost(MockMvcRequestBuilders.post(WRITE_TARGET)))
                .andExpect(result -> assertNotEquals(
                        HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus(),
                        WRITE_TARGET + " 는 이 scope 로 인가를 통과해야 한다"));
    }

    @Nested
    @DisplayName("읽기 전용 키")
    @WithMockUserCustom(roles = "ROLE_USER", authSource = AuthSource.EXTERNAL,
            scopes = {ApiKeyScope.READ, ApiKeyScope.MCP_READ})
    class ReadOnlyKey {

        @Test
        @DisplayName("READ endpoint → 허용 (게이트 통과로 충분)")
        void readTarget_isAllowed() throws Exception {
            expectAllowed(READ_TARGET);
        }

        /** scope 미보유는 role 미허용과 구분되는 에러코드로 응답해야 한다 */
        @Test
        @DisplayName("WRITE endpoint → 403 + API_KEY_014")
        void writeTarget_returns403() throws Exception {
            mockMvc.perform(withHost(MockMvcRequestBuilders.post(WRITE_TARGET)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode")
                            .value(ApiKeyErrorCode.API_KEY_SCOPE_INSUFFICIENT.getErrorCode()));
        }

        @Test
        @DisplayName("요구 scope 없는 endpoint → 허용")
        void noScopeTarget_isAllowed() throws Exception {
            expectAllowed(NO_SCOPE_TARGET);
        }
    }

    @Nested
    @DisplayName("읽기+쓰기 키")
    @WithMockUserCustom(roles = "ROLE_USER", authSource = AuthSource.EXTERNAL,
            scopes = {ApiKeyScope.READ, ApiKeyScope.WRITE, ApiKeyScope.MCP_READ, ApiKeyScope.MCP_WRITE})
    class ReadWriteKey {

        @Test
        @DisplayName("READ endpoint → 허용")
        void readTarget_isAllowed() throws Exception {
            expectAllowed(READ_TARGET);
        }

        @Test
        @DisplayName("WRITE endpoint → 허용")
        void writeTarget_isAllowed() throws Exception {
            expectWriteAllowed();
        }
    }

    /** api:* 가 하나도 없는 키는 게이트에서 막힌다 — endpoint 의 선언 유무와 무관하다 */
    @Nested
    @DisplayName("scope 없는 키")
    @WithMockUserCustom(roles = "ROLE_USER", authSource = AuthSource.EXTERNAL)
    class NoScopeKey {

        @Test
        @DisplayName("READ endpoint → 403")
        void readTarget_returns403() throws Exception {
            mockMvc.perform(withHost(MockMvcRequestBuilders.get(READ_TARGET)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("WRITE endpoint → 403")
        void writeTarget_returns403() throws Exception {
            mockMvc.perform(withHost(MockMvcRequestBuilders.post(WRITE_TARGET)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("요구 scope 없는 endpoint → 403 (게이트에 막힌다)")
        void noScopeTarget_returns403() throws Exception {
            mockMvc.perform(withHost(MockMvcRequestBuilders.get(NO_SCOPE_TARGET)))
                    .andExpect(status().isForbidden());
        }
    }
}
