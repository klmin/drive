package com.thinkfree.drive.global.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfree.drive.apikey.data.ApiKeyData;
import com.thinkfree.drive.apikey.enums.ApiKeyStatus;
import com.thinkfree.drive.apikey.service.ApiKeyFindService;
import com.thinkfree.drive.apikey.service.ApiKeyService;
import com.thinkfree.drive.global.security.ApiKeyAuthenticationFilter;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.superadmin.properties.SuperAdminExternalProperty;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.service.UserFindService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("API Key·MCP 요청 수 제한 — 키 하나당 버킷")
class ApiClientRateLimitTest {

    private static final String KEY_A = "keyA-B6u6gFwHhyBum9Js_LRrcISG54dEC";
    private static final String KEY_B = "keyB-B6u6gFwHhyBum9Js_LRrcISG54dEC";
    private static final int TENANT = 1;
    private static final int USER_SEQ = 10;

    final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    final ApiKeyFindService apiKeyFindService = mock(ApiKeyFindService.class);
    final ApiKeyService apiKeyService = mock(ApiKeyService.class);
    final UserFindService userFindService = mock(UserFindService.class);
    final SuperAdminExternalProperty superAdminExternalProperty = mock(SuperAdminExternalProperty.class);

    private static RateLimitProperty property(boolean enabled, long perKey) {
        return new RateLimitProperty(enabled, 10, 100, 30, 5, 3, 20, perKey);
    }

    private ApiClientRateLimitGuard guard(boolean enabled, long perKey) {
        return new ApiClientRateLimitGuard(new LocalRateLimiter(), property(enabled, perKey), meters, new ObjectMapper());
    }

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        given(superAdminExternalProperty.matches(anyString())).willReturn(false);
        key(KEY_A, 1);
        key(KEY_B, 2);
        UserDetailData account = mock(UserDetailData.class);
        given(account.getUserSeq()).willReturn(USER_SEQ);
        given(account.getUserId()).willReturn("u@test.com");
        given(account.getTenantSeq()).willReturn(TENANT);
        given(userFindService.findActiveDetailHoldingRole(USER_SEQ, RoleType.USER)).willReturn(account);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private void key(String apiKey, int apiKeySeq) {
        given(apiKeyFindService.findActiveByApiKey(apiKey, TENANT)).willReturn(new ApiKeyData(apiKeySeq, TENANT, USER_SEQ, "k", null, apiKey,
                RoleType.USER, "api:read", ApiKeyStatus.ACTIVE, Instant.now().plusSeconds(600), null, 1, Instant.now(), false));
    }

    /** 요청 하나 — 체인까지 갔으면 true */
    private boolean call(ApiKeyAuthenticationFilter filter, String apiKey, MockHttpServletResponse response) throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/v1/users/exists");
        request.addHeader("Authorization", "Bearer " + apiKey);
        boolean[] reached = {false};
        FilterChain chain = (req, res) -> reached[0] = true;
        filter.doFilter(request, response, chain);
        return reached[0];
    }

    @Test
    @DisplayName("한도까지 통과, 넘으면 429 + Retry-After + REQUEST_013 본문, 마지막 사용일 기록 안 함, 지표 증가")
    void rejectsOverLimit() throws Exception {
        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(apiKeyFindService, apiKeyService, userFindService, superAdminExternalProperty, guard(true, 2));

        assertThat(call(filter, KEY_A, new MockHttpServletResponse())).isTrue();
        assertThat(call(filter, KEY_A, new MockHttpServletResponse())).isTrue();

        MockHttpServletResponse rejected = new MockHttpServletResponse();
        assertThat(call(filter, KEY_A, rejected)).isFalse();
        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(Long.parseLong(rejected.getHeader("Retry-After"))).isPositive();
        assertThat(rejected.getContentAsString()).contains("REQUEST_013");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        verify(apiKeyService, times(2)).updateLastUsedDate(any(), any());
        assertThat(meters.counter("drive.rate.limit.rejected", "rule", "api-key").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("키마다 따로 — 한 키가 다 써도 다른 키는 통과")
    void separateBucketPerKey() throws Exception {
        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(apiKeyFindService, apiKeyService, userFindService, superAdminExternalProperty, guard(true, 1));

        assertThat(call(filter, KEY_A, new MockHttpServletResponse())).isTrue();
        assertThat(call(filter, KEY_A, new MockHttpServletResponse())).isFalse();
        assertThat(call(filter, KEY_B, new MockHttpServletResponse())).isTrue();
    }

    @Test
    @DisplayName("외부 체인·내부 체인 필터가 같은 키의 버킷을 나눠 쓴다")
    void sharedAcrossChains() throws Exception {
        ApiClientRateLimitGuard shared = guard(true, 2);
        ApiKeyAuthenticationFilter external = new ApiKeyAuthenticationFilter(apiKeyFindService, apiKeyService, userFindService, superAdminExternalProperty, shared);
        ApiKeyAuthenticationFilter internal = ApiKeyAuthenticationFilter.rejectingSuperAdminKey(apiKeyFindService, apiKeyService, userFindService, superAdminExternalProperty, shared);

        assertThat(call(external, KEY_A, new MockHttpServletResponse())).isTrue();
        assertThat(call(internal, KEY_A, new MockHttpServletResponse())).isTrue();
        assertThat(call(external, KEY_A, new MockHttpServletResponse())).isFalse();
    }

    @Test
    @DisplayName("없는 키는 세지 않는다 — 아무 문자열로 버킷을 만들지 못함")
    void unknownKeyNotCounted() throws Exception {
        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(apiKeyFindService, apiKeyService, userFindService, superAdminExternalProperty, guard(true, 1));

        for (int i = 0; i < 3; i++) {
            assertThat(call(filter, "unknown-key", new MockHttpServletResponse())).isTrue(); // 인증 없이 체인 → entry point 401
        }
        assertThat(call(filter, KEY_A, new MockHttpServletResponse())).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    @DisplayName("가드 — 테넌트·키 번호·MCP 연동이 키에 들어가고, 꺼져 있으면 제한하지 않음")
    void guardKeys() {
        ApiClientRateLimitGuard guard = guard(true, 1);
        guard.checkApiKey(1, 5);
        assertThatThrownBy(() -> guard.checkApiKey(1, 5)).isInstanceOf(RateLimitExceededException.class);
        assertThatNoException().isThrownBy(() -> guard.checkApiKey(2, 5));      // 다른 테넌트
        assertThatNoException().isThrownBy(() -> guard.checkApiKey(null, 5));   // 슈퍼관리자 키

        guard.checkMcpOAuth(1, USER_SEQ, "client-a");
        assertThatThrownBy(() -> guard.checkMcpOAuth(1, USER_SEQ, "client-a"))
                .isInstanceOf(RateLimitExceededException.class)
                .extracting("rule").isEqualTo("mcp-oauth");
        assertThatNoException().isThrownBy(() -> guard.checkMcpOAuth(1, USER_SEQ, "client-b"));

        guard.checkStaticSuperAdminKey();
        assertThatThrownBy(guard::checkStaticSuperAdminKey).isInstanceOf(RateLimitExceededException.class);

        ApiClientRateLimitGuard disabled = guard(false, 1);
        for (int i = 0; i < 5; i++) {
            disabled.checkApiKey(1, 5);
            disabled.checkMcpOAuth(1, USER_SEQ, "client-a");
            disabled.checkStaticSuperAdminKey();
        }
    }
}
