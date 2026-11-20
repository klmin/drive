package com.thinkfree.drive.global.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.tenant.addressing.StaticBundleIndex;
import com.thinkfree.drive.tenant.addressing.TenantMatch;
import com.thinkfree.drive.tenant.addressing.TenantAddressingStrategy;
import com.thinkfree.drive.tenant.cache.HostTenantCache;
import com.thinkfree.drive.tenant.properties.TenantProperty;
import com.thinkfree.drive.tenant.resolver.TenantSeqResolver;
import com.thinkfree.drive.tenant.service.TenantFindService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 모드 무관 공용 필터 규칙. 모드별 매치 로직은 {@code SubdomainTenantAddressingTest}/{@code PathTenantAddressingTest}.
 */
@DisplayName("TenantFilter — 공용 판별 규칙")
class TenantFilterTest {

    private static final int MATCHED_SEQ = 7;
    private static final int SINGLE_SEQ = 1;
    private static final int DEV_FALLBACK_SEQ = 99;

    private TenantAddressingStrategy strategy;
    private TenantFindService tenantFindService;
    private TenantProperty tenantProperty;
    private StaticBundleIndex staticBundleIndex;
    private TenantFilter filter;

    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        strategy = mock(TenantAddressingStrategy.class);
        when(strategy.resolve(any())).thenReturn(TenantMatch.none());
        tenantFindService = mock(TenantFindService.class);
        // Mockito 기본값은 Integer 도 0 → 단일테넌트 fallback 으로 오인되므로 "없음" 을 명시
        when(tenantFindService.findSingleActiveSelfManagedTenantSeq()).thenReturn(null);
        tenantProperty = new TenantProperty(300L, 1000, false, null);
        // 기본은 빈 인덱스(번들 없는 서버) — 정적 스킵 규칙은 StaticBundleSkip 에서 stub
        staticBundleIndex = mock(StaticBundleIndex.class);
        filter = newFilter();

        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    private TenantFilter newFilter() {
        TenantSeqResolver resolver = new TenantSeqResolver(tenantFindService, tenantProperty, new HostTenantCache(tenantProperty));
        return new TenantFilter(strategy, resolver, new ObjectMapper(), staticBundleIndex);
    }

    /** GET 요청(MockHttpServletRequest 기본 method 는 빈 문자열이라 명시). 끝슬래시 리다이렉트는 GET·HEAD 에만 적용된다. */
    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI(uri);
        request.setServletPath(uri);
        return request;
    }

    private HttpServletRequest passedRequest() throws ServletException, java.io.IOException {
        ArgumentCaptor<HttpServletRequest> captor = ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(chain).doFilter(captor.capture(), any());
        return captor.getValue();
    }

    @Nested
    @DisplayName("전략 매치 성공")
    class StrategyHit {

        @Test
        @DisplayName("전략이 돌려준 요청으로 체인을 태우고 TenantContext 를 set 한다")
        void forwardsStrategyRequestWithContext() throws Exception {
            MockHttpServletRequest original = request("/t/acme/api/v1/users/me");
            MockHttpServletRequest forwarded = request("/api/v1/users/me");
            when(strategy.resolve(original)).thenReturn(TenantMatch.matched(MATCHED_SEQ, forwarded));
            doAnswer(inv -> {
                assertThat(TenantContext.get()).isEqualTo(MATCHED_SEQ);
                return null;
            }).when(chain).doFilter(any(), any());

            filter.doFilter(original, response, chain);

            assertThat(passedRequest()).isSameAs(forwarded);
            assertThat(TenantContext.get()).isNull();
            verify(tenantFindService, never()).findSingleActiveSelfManagedTenantSeq();
        }

        @Test
        @DisplayName("요청 관측 컨텍스트에 tenant.seq 를 span 속성(high cardinality)으로 남긴다 — 메트릭 태그(low)에는 넣지 않는다")
        void tagsRequestObservationWithTenant() throws Exception {
            MockHttpServletRequest original = request("/t/acme/api/v1/users/me");
            MockHttpServletRequest forwarded = request("/api/v1/users/me");
            var observation = new org.springframework.http.server.observation.ServerRequestObservationContext(forwarded, response);
            forwarded.setAttribute(org.springframework.web.filter.ServerHttpObservationFilter.CURRENT_OBSERVATION_CONTEXT_ATTRIBUTE, observation);
            when(strategy.resolve(original)).thenReturn(TenantMatch.matched(MATCHED_SEQ, forwarded));

            filter.doFilter(original, response, chain);

            assertThat(observation.getHighCardinalityKeyValue(TenantFilter.TENANT_SPAN_KEY).getValue()).isEqualTo(String.valueOf(MATCHED_SEQ));
            assertThat(observation.getLowCardinalityKeyValue(TenantFilter.TENANT_SPAN_KEY)).isNull();
        }

        @Test
        @DisplayName("접두 붙은 슈퍼어드민 API(/t/acme/api/v1/super-admin/**)는 skip 대상이 아니라 전략 매치 → 그 테넌트로 진행(무해)")
        void prefixedSuperAdminPathFollowsMatchedTenant() throws Exception {
            MockHttpServletRequest original = request("/t/acme/api/v1/super-admin/tenants");
            MockHttpServletRequest forwarded = request("/api/v1/super-admin/tenants");
            when(strategy.resolve(original)).thenReturn(TenantMatch.matched(MATCHED_SEQ, forwarded));
            doAnswer(inv -> {
                assertThat(TenantContext.get()).isEqualTo(MATCHED_SEQ);
                return null;
            }).when(chain).doFilter(any(), any());

            filter.doFilter(original, response, chain);

            verify(strategy).resolve(original);
            assertThat(passedRequest()).isSameAs(forwarded);
        }

        @Test
        @DisplayName("주소로 특정된 테넌트가 비활성(INACTIVE)이면 fallback 없이 404, 테넌트 선택 경로(브랜딩)·접두 붙은 슈퍼어드민 API 라도 404")
        void inactiveTenantIs404WithoutFallback() throws Exception {
            when(tenantFindService.findSingleActiveSelfManagedTenantSeq()).thenReturn(SINGLE_SEQ);
            when(strategy.resolve(any())).thenReturn(TenantMatch.inactive());

            filter.doFilter(request("/api/v1/users/me"), response, chain);
            MockHttpServletResponse second = new MockHttpServletResponse();
            filter.doFilter(request("/t/acme/api/v1/super-admin/tenants"), second, chain);
            MockHttpServletResponse third = new MockHttpServletResponse();
            filter.doFilter(request("/api/v1/branding"), third, chain);
            assertThat(third.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());

            assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(response.getContentAsString()).contains("TENANT_004");
            assertThat(second.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
            verify(chain, never()).doFilter(any(), any());
            verify(tenantFindService, never()).findSingleActiveSelfManagedTenantSeq();
            assertThat(TenantContext.get()).isNull();
        }

        @Test
        @DisplayName("마커 문법 불량(NOT_FOUND, PATH 모드 /t/·/t/UPPER)이면 접두를 만들 수 없어 fallback 없이 404")
        void notFoundTenantIs404WithoutFallback() throws Exception {
            when(tenantFindService.findSingleActiveSelfManagedTenantSeq()).thenReturn(SINGLE_SEQ);
            when(strategy.resolve(any())).thenReturn(TenantMatch.notFound());

            filter.doFilter(request("/t/"), response, chain);
            MockHttpServletResponse second = new MockHttpServletResponse();
            filter.doFilter(request("/t/UPPER/api/v1/super-admin/tenants"), second, chain);

            assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(response.getContentAsString()).contains("TENANT_004");
            assertThat(second.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
            verify(chain, never()).doFilter(any(), any());
            verify(tenantFindService, never()).findSingleActiveSelfManagedTenantSeq();
            assertThat(TenantContext.get()).isNull();
        }

        @Test
        @DisplayName("PATH 모드 접두만 있는 GET /t/acme(끝슬래시 없음)는 /t/acme/ 로 302, 쿼리 유지, 체인 미진행")
        void bareTenantRootRedirectsToTrailingSlash() throws Exception {
            MockHttpServletRequest bare = request("/t/acme");
            bare.setMethod("GET");
            when(strategy.resolve(bare)).thenReturn(TenantMatch.matched(MATCHED_SEQ, new TenantPathRequestWrapper(bare, "/t/acme")));
            MockHttpServletRequest withQuery = request("/t/acme");
            withQuery.setQueryString("pfs=3&x=1");
            when(strategy.resolve(withQuery)).thenReturn(TenantMatch.matched(MATCHED_SEQ, new TenantPathRequestWrapper(withQuery, "/t/acme")));

            filter.doFilter(bare, response, chain);
            MockHttpServletResponse second = new MockHttpServletResponse();
            filter.doFilter(withQuery, second, chain);

            assertThat(response.getStatus()).isEqualTo(HttpStatus.FOUND.value());
            assertThat(response.getHeader("Location")).isEqualTo("/t/acme/");
            assertThat(second.getHeader("Location")).isEqualTo("/t/acme/?pfs=3&x=1");
            verify(chain, never()).doFilter(any(), any());
            assertThat(TenantContext.get()).isNull();
        }

        @Test
        @DisplayName("접두만 있어도 POST 는 리다이렉트하지 않고 그대로 진행, 끝슬래시 있는 /t/acme/ 와 SUBDOMAIN 원본 요청도 진행")
        void bareTenantRootNonGetAndSlashPassThrough() throws Exception {
            MockHttpServletRequest post = request("/t/acme");
            post.setMethod("POST");
            when(strategy.resolve(post)).thenReturn(TenantMatch.matched(MATCHED_SEQ, new TenantPathRequestWrapper(post, "/t/acme")));
            MockHttpServletRequest slash = request("/t/acme/");
            when(strategy.resolve(slash)).thenReturn(TenantMatch.matched(MATCHED_SEQ, new TenantPathRequestWrapper(slash, "/t/acme")));
            MockHttpServletRequest subdomainRoot = request("/api/v1/x");
            when(strategy.resolve(subdomainRoot)).thenReturn(TenantMatch.matched(MATCHED_SEQ, subdomainRoot));

            filter.doFilter(post, response, chain);
            filter.doFilter(slash, new MockHttpServletResponse(), chain);
            filter.doFilter(subdomainRoot, new MockHttpServletResponse(), chain);

            assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
            verify(chain, times(3)).doFilter(any(), any());
        }

        @Test
        @DisplayName("미등록 슬러그 fallback 도 접두만 있는 GET /t/nope 는 /t/nope/ 로 302")
        void bareUnknownSlugRedirectsBeforeFallback() throws Exception {
            when(tenantFindService.findSingleActiveSelfManagedTenantSeq()).thenReturn(SINGLE_SEQ);
            MockHttpServletRequest bare = request("/t/nope");
            when(strategy.resolve(bare)).thenReturn(TenantMatch.none(new TenantPathRequestWrapper(bare, "/t/nope")));

            filter.doFilter(bare, response, chain);

            assertThat(response.getStatus()).isEqualTo(HttpStatus.FOUND.value());
            assertThat(response.getHeader("Location")).isEqualTo("/t/nope/");
            verify(chain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("체인에서 예외가 나도 TenantContext 는 clear 된다")
        void clearsContextWhenChainThrows() throws Exception {
            MockHttpServletRequest original = request("/api/v1/users/me");
            when(strategy.resolve(original)).thenReturn(TenantMatch.matched(MATCHED_SEQ, original));
            doThrow(new ServletException("downstream")).when(chain).doFilter(any(), any());

            assertThatThrownBy(() -> filter.doFilter(original, response, chain)).isInstanceOf(ServletException.class);

            assertThat(TenantContext.get()).isNull();
        }
    }

    @Nested
    @DisplayName("슈퍼어드민 전용 API — 접두 없는 요청은 판별 skip(TenantContext 없이 진행)")
    class SuperAdminApiSkip {

        @ParameterizedTest
        @ValueSource(strings = {
                "/api/v1/auth/super-admin/login",
                "/api/v1/super-admin/tenants",
                "/api/v1/super-admin/tenants/3/users",
                "/api/public/v1/super-admin/tenants"
                // 슈퍼어드민 프론트 라우트(/superadmin/**)는 여기 없다 — 정적 번들 최상위 세그먼트라
                // StaticBundleIndex 스킵이 담당한다 (StaticBundleSkip 에서 검증)
        })
        @DisplayName("fallback 테넌트가 있어도 조회·전략 없이 TenantContext null 로 원본 요청 통과")
        void skipsWithoutTenant(String uri) throws Exception {
            when(tenantFindService.findSingleActiveSelfManagedTenantSeq()).thenReturn(SINGLE_SEQ);
            doAnswer(inv -> {
                assertThat(TenantContext.get()).isNull();
                return null;
            }).when(chain).doFilter(any(), any());

            MockHttpServletRequest original = request(uri);
            filter.doFilter(original, response, chain);

            assertThat(passedRequest()).isSameAs(original);
            assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
            verifyNoInteractions(strategy, tenantFindService);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "/api/v1/super-admins/tenants",          // 유사 접두
                "/api/v1/auth/super-admin/login/extra",  // 로그인은 정확 매치만
                "/api/v1/admins/users"                   // 일반 관리자 API
        })
        @DisplayName("슈퍼어드민 패턴 밖의 경로는 skip 되지 않는다")
        void nonSuperAdminPathsAreNotSkipped(String uri) throws Exception {
            filter.doFilter(request(uri), response, chain);

            verify(strategy).resolve(any());
            assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
            verify(chain, never()).doFilter(any(), any());
        }
    }

    @Nested
    @DisplayName("테넌트 선택 경로(브랜딩·로그아웃·재발급) — 매치·fallback 모두 없을 때만 tenant-less 통과")
    class TenantOptional {

        @ParameterizedTest
        @ValueSource(strings = {
                "/api/v1/branding",
                "/branding/images/favicon",
                "/branding/images/logo-icon",
                "/api/v1/auth/logout",
                "/api/v1/token/reissue",
                "/api/v1/users",
                "/api/v1/users/profile-image",
                "/api/v1/users/859/profile-image"
        })
        @DisplayName("fallback 테넌트가 없으면 404 대신 TenantContext null 로 원본 요청 통과")
        void passesWithoutTenantWhenNoFallback(String uri) throws Exception {
            doAnswer(inv -> {
                assertThat(TenantContext.get()).isNull();
                return null;
            }).when(chain).doFilter(any(), any());

            MockHttpServletRequest original = request(uri);
            filter.doFilter(original, response, chain);

            assertThat(passedRequest()).isSameAs(original);
            assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
            verify(strategy).resolve(original);
            verify(tenantFindService).findSingleActiveSelfManagedTenantSeq();
        }

        @ParameterizedTest
        @ValueSource(strings = {"/api/v1/branding", "/branding/images/favicon", "/api/v1/auth/logout", "/api/v1/token/reissue"})
        @DisplayName("fallback 테넌트가 있으면 평소처럼 그 테넌트를 달고 통과(SUBDOMAIN 모드 단일 테넌트 브랜딩 유지)")
        void fallsBackNormallyWhenAvailable(String uri) throws Exception {
            when(tenantFindService.findSingleActiveSelfManagedTenantSeq()).thenReturn(SINGLE_SEQ);
            doAnswer(inv -> {
                assertThat(TenantContext.get()).isEqualTo(SINGLE_SEQ);
                return null;
            }).when(chain).doFilter(any(), any());

            MockHttpServletRequest original = request(uri);
            filter.doFilter(original, response, chain);

            assertThat(passedRequest()).isSameAs(original);
        }

        @Test
        @DisplayName("전략이 매치하면 그 테넌트가 set 된다(테넌트 브랜딩)")
        void matchedTenantWins() throws Exception {
            MockHttpServletRequest original = request("/api/v1/branding");
            when(strategy.resolve(original)).thenReturn(TenantMatch.matched(MATCHED_SEQ, original));
            doAnswer(inv -> {
                assertThat(TenantContext.get()).isEqualTo(MATCHED_SEQ);
                return null;
            }).when(chain).doFilter(any(), any());

            filter.doFilter(original, response, chain);

            verify(chain).doFilter(any(), any());
            verify(tenantFindService, never()).findSingleActiveSelfManagedTenantSeq();
        }

        @Test
        @DisplayName("PATH 모드 미등록 슬러그 /t/nope/api/v1/branding 은 접두를 뗀 래퍼 경로로 판정해 래퍼로 tenant-less 통과")
        void strippedWrapperPathIsJudged() throws Exception {
            MockHttpServletRequest original = request("/t/nope/api/v1/branding");
            HttpServletRequest stripped = new TenantPathRequestWrapper(original, "/t/nope");
            when(strategy.resolve(original)).thenReturn(TenantMatch.none(stripped));
            doAnswer(inv -> {
                assertThat(TenantContext.get()).isNull();
                return null;
            }).when(chain).doFilter(any(), any());

            filter.doFilter(original, response, chain);

            HttpServletRequest passed = passedRequest();
            assertThat(passed).isSameAs(stripped);
            assertThat(passed.getServletPath()).isEqualTo("/api/v1/branding");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "/api/v1/brandings",            // 유사 경로
                "/api/v1/auth/login-config",    // 같은 TENANT_PUBLIC 이지만 목록 밖
                "/.well-known/oauth-authorization-server/t/nope",
                "/api/v1/users/quota",          // users 하위 테넌트 종속 경로 — 정확 매치·profile-image 만 허용
                "/api/v1/users/search",
                "/api/v1/config"                // 슈퍼어드민 영역은 프론트가 호출 생략
        })
        @DisplayName("목록 밖 경로는 fallback 없으면 종전대로 404")
        void otherPathsStill404(String uri) throws Exception {
            filter.doFilter(request(uri), response, chain);

            assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(response.getContentAsString()).contains("TENANT_004");
            verify(chain, never()).doFilter(any(), any());
        }
    }

    @Nested
    @DisplayName("전략 매치 실패 — fallback")
    class Fallback {

        @Test
        @DisplayName("활성 SELF_MANAGED 테넌트가 1개면 그 테넌트로, 요청은 원본 그대로 넘긴다")
        void singleTenant() throws Exception {
            when(tenantFindService.findSingleActiveSelfManagedTenantSeq()).thenReturn(SINGLE_SEQ);
            doAnswer(inv -> {
                assertThat(TenantContext.get()).isEqualTo(SINGLE_SEQ);
                return null;
            }).when(chain).doFilter(any(), any());

            MockHttpServletRequest original = request("/api/v1/users/me");
            filter.doFilter(original, response, chain);

            assertThat(passedRequest()).isSameAs(original);
        }

        @Test
        @DisplayName("전략이 NONE 에 요청(접두를 뗀 래퍼)을 실어 주면 fallback 테넌트로 그 요청을 넘긴다 (PATH 모드 미등록 슬러그)")
        void singleTenantForwardsStrategyRequest() throws Exception {
            when(tenantFindService.findSingleActiveSelfManagedTenantSeq()).thenReturn(SINGLE_SEQ);
            MockHttpServletRequest original = request("/t/nope/api/v1/users/me");
            HttpServletRequest stripped = new TenantPathRequestWrapper(original, "/t/nope");
            when(strategy.resolve(original)).thenReturn(TenantMatch.none(stripped));
            doAnswer(inv -> {
                assertThat(TenantContext.get()).isEqualTo(SINGLE_SEQ);
                return null;
            }).when(chain).doFilter(any(), any());

            filter.doFilter(original, response, chain);

            HttpServletRequest passed = passedRequest();
            assertThat(passed).isSameAs(stripped);
            assertThat(passed.getContextPath()).isEqualTo("/t/nope");
            assertThat(passed.getServletPath()).isEqualTo("/api/v1/users/me");
            assertThat(TenantContext.get()).isNull();
        }

        @Test
        @DisplayName("NONE 에 요청이 실려 있어도 fallback 테넌트가 없으면 404")
        void strategyRequestDoesNotBypass404() throws Exception {
            MockHttpServletRequest original = request("/t/nope/api/v1/users/me");
            when(strategy.resolve(original)).thenReturn(TenantMatch.none(new TenantPathRequestWrapper(original, "/t/nope")));

            filter.doFilter(original, response, chain);

            assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(response.getContentAsString()).contains("TENANT_004");
            verify(chain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("단일 테넌트 fallback 결과는 캐시되어 두 번째 요청은 DB 조회가 없다")
        void singleTenantCached() throws Exception {
            when(tenantFindService.findSingleActiveSelfManagedTenantSeq()).thenReturn(SINGLE_SEQ);

            filter.doFilter(request("/api/v1/a"), response, chain);
            filter.doFilter(request("/api/v1/b"), new MockHttpServletResponse(), chain);

            verify(tenantFindService, times(1)).findSingleActiveSelfManagedTenantSeq();
            verify(chain, times(2)).doFilter(any(), any());
        }

        @Test
        @DisplayName("단일 테넌트가 없고 dev fallback 이 켜져 있으면 devDefaultTenantSeq")
        void devFallback() throws Exception {
            tenantProperty = new TenantProperty(300L, 1000, true, DEV_FALLBACK_SEQ);
            filter = newFilter();
            doAnswer(inv -> {
                assertThat(TenantContext.get()).isEqualTo(DEV_FALLBACK_SEQ);
                return null;
            }).when(chain).doFilter(any(), any());

            filter.doFilter(request("/api/v1/users/me"), response, chain);

            verify(chain).doFilter(any(), any());
        }

        @Test
        @DisplayName("단일 테넌트 fallback 이 dev fallback 보다 우선한다")
        void singleTenantBeatsDevFallback() throws Exception {
            tenantProperty = new TenantProperty(300L, 1000, true, DEV_FALLBACK_SEQ);
            filter = newFilter();
            when(tenantFindService.findSingleActiveSelfManagedTenantSeq()).thenReturn(SINGLE_SEQ);
            doAnswer(inv -> {
                assertThat(TenantContext.get()).isEqualTo(SINGLE_SEQ);
                return null;
            }).when(chain).doFilter(any(), any());

            filter.doFilter(request("/api/v1/users/me"), response, chain);

            verify(chain).doFilter(any(), any());
        }

        @Test
        @DisplayName("전부 실패하면 404 TENANT_004 JSON 을 쓰고 체인을 타지 않는다")
        void notFound() throws Exception {
            filter.doFilter(request("/api/v1/users/me"), response, chain);

            assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(response.getContentType()).contains("application/json");
            assertThat(response.getContentAsString()).contains("TENANT_004");
            verify(chain, never()).doFilter(any(), any());
            assertThat(TenantContext.get()).isNull();
        }
    }

    @Nested
    @DisplayName("화면 요청의 테넌트 판별 실패 — JSON 대신 /error-page?reason=tenant 리다이렉트")
    class PageRequestNotFound {

        private static final String BROWSER_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
        private static final String AXIOS_ACCEPT = "application/json, text/plain, */*";

        private MockHttpServletRequest pageRequest(String uri) {
            MockHttpServletRequest request = request(uri);
            request.addHeader("Accept", BROWSER_ACCEPT);
            return request;
        }

        @Test
        @DisplayName("마커 문법 불량(NOT_FOUND)·비활성(INACTIVE)·fallback 없음 모두 GET + text/html 이면 /error-page?reason=tenant 로 302, JSON 본문 없음")
        void redirectsPageRequestsToTenantErrorPage() throws Exception {
            when(strategy.resolve(any())).thenReturn(TenantMatch.notFound());
            filter.doFilter(pageRequest("/t/"), response, chain);

            when(strategy.resolve(any())).thenReturn(TenantMatch.inactive());
            MockHttpServletResponse inactive = new MockHttpServletResponse();
            filter.doFilter(pageRequest("/t/acme/drive/"), inactive, chain);

            when(strategy.resolve(any())).thenReturn(TenantMatch.none());
            MockHttpServletResponse noFallback = new MockHttpServletResponse();
            filter.doFilter(pageRequest("/t/nope/drive/"), noFallback, chain);

            for (MockHttpServletResponse r : new MockHttpServletResponse[]{response, inactive, noFallback}) {
                assertThat(r.getStatus()).isEqualTo(HttpStatus.FOUND.value());
                assertThat(r.getRedirectedUrl()).isEqualTo("/error-page?reason=tenant");
                assertThat(r.getContentAsString()).isEmpty();
            }
            verify(chain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("API 요청(axios Accept)·GET 이 아닌 요청은 text/html 이 있어도 종전대로 TENANT_004 JSON")
        void keepsJsonForApiRequests() throws Exception {
            when(strategy.resolve(any())).thenReturn(TenantMatch.notFound());
            MockHttpServletRequest api = request("/t/api/v1/users/me");
            api.addHeader("Accept", AXIOS_ACCEPT);
            MockHttpServletRequest post = pageRequest("/t/");
            post.setMethod("POST");

            filter.doFilter(api, response, chain);
            MockHttpServletResponse second = new MockHttpServletResponse();
            filter.doFilter(post, second, chain);

            assertThat(response.getContentAsString()).contains("TENANT_004");
            assertThat(second.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(second.getContentAsString()).contains("TENANT_004");
        }
    }

    @Nested
    @DisplayName("시스템 공개 경로")
    class SystemPublicSkip {

        @ParameterizedTest
        @ValueSource(strings = {
                "/h2-console/login", "/error", "/swagger-ui/index.html", "/api-docs/v1", "/actuator/health",
                "/api/v1/common/ping", "/api/v1/geoip/country-code", "/api/v1/build/info"
        })
        @DisplayName("SYSTEM_PUBLIC 은 전략 조회 없이 통과")
        void skips(String uri) throws Exception {
            filter.doFilter(request(uri), response, chain);

            assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
            verify(chain).doFilter(any(), any());
            verifyNoInteractions(strategy, tenantFindService);
        }
    }

    @Nested
    @DisplayName("프론트 정적 자산 — 번들 최상위 세그먼트 스킵")
    class StaticBundleSkip {

        private static final java.util.Set<String> BUNDLE_ROOTS =
                java.util.Set.of("_next", "common", "favicon.ico", "drive", "index.html", "superadmin");

        @BeforeEach
        void stubIndex() {
            when(staticBundleIndex.isRootSegment(any())).thenAnswer(inv -> {
                String segment = inv.getArgument(0);
                return segment != null && BUNDLE_ROOTS.contains(segment);
            });
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "/_next/static/chunks/main.js", "/common/close.svg", "/favicon.ico", "/drive", "/drive/", "/index.html",
                // 슈퍼어드민 프론트 라우트 — 번들 스킵이 담당한다
                "/superadmin", "/superadmin/login/"
        })
        @DisplayName("번들에 있는 첫 세그먼트는 전략·fallback 조회 없이 TenantContext null 로 통과")
        void skipsBundleAssets(String uri) throws Exception {
            doAnswer(inv -> {
                assertThat(TenantContext.get()).isNull();
                return null;
            }).when(chain).doFilter(any(), any());

            MockHttpServletRequest original = request(uri);
            filter.doFilter(original, response, chain);

            assertThat(passedRequest()).isSameAs(original);
            assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
            verifyNoInteractions(strategy, tenantFindService);
        }

        @Test
        @DisplayName("루트(/) 는 인덱스와 무관하게 통과")
        void rootPathSkipped() throws Exception {
            filter.doFilter(request("/"), response, chain);

            verify(chain).doFilter(any(), any());
            verifyNoInteractions(strategy, tenantFindService, staticBundleIndex);
        }

        @Test
        @DisplayName("마커 접두가 붙은 /t/acme/_next/… 는 스킵되지 않고 전략 매치로 진행(접두 제거 후 정적 서빙)")
        void prefixedAssetGoesThroughStrategy() throws Exception {
            MockHttpServletRequest original = request("/t/acme/_next/static/x.js");
            MockHttpServletRequest forwarded = request("/_next/static/x.js");
            when(strategy.resolve(original)).thenReturn(TenantMatch.matched(MATCHED_SEQ, forwarded));

            filter.doFilter(original, response, chain);

            verify(strategy).resolve(original);
            assertThat(passedRequest()).isSameAs(forwarded);
        }

        @Test
        @DisplayName("인덱스가 비어 있으면(번들 없는 서버) 기존대로 전략·fallback 을 탄다")
        void emptyIndexKeepsLegacyBehaviour() throws Exception {
            doReturn(false).when(staticBundleIndex).isRootSegment(any());

            filter.doFilter(request("/_next/static/x.js"), response, chain);

            verify(strategy).resolve(any());
            verify(tenantFindService).findSingleActiveSelfManagedTenantSeq();
            assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
            verify(chain, never()).doFilter(any(), any());
        }
    }
}
