package com.thinkfree.drive.tenant.addressing;

import com.thinkfree.drive.app.properties.AppFrontProperty;
import com.thinkfree.drive.basedomain.data.BaseDomainData;
import com.thinkfree.drive.basedomain.service.BaseDomainFindService;
import com.thinkfree.drive.global.filter.TenantPathRequestWrapper;
import com.thinkfree.drive.tenant.cache.HostTenantCache;
import com.thinkfree.drive.tenant.data.TenantData;
import com.thinkfree.drive.tenant.enums.TenantStatus;
import com.thinkfree.drive.tenant.properties.TenantProperty;
import com.thinkfree.drive.tenant.resolver.TenantSeqResolver;
import com.thinkfree.drive.tenant.service.TenantFindService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("PathTenantAddressing — /t/{slug} 마커 매치·origin")
class PathTenantAddressingTest {

    private static final String BASE_DOMAIN = "drive.thinkfree.com";
    private static final int ACME_SEQ = 7;

    private TenantFindService tenantFindService;
    private BaseDomainFindService baseDomainFindService;
    private AppFrontProperty appFrontProperty;
    private PathTenantAddressing addressing;

    @BeforeEach
    void setUp() {
        tenantFindService = mock(TenantFindService.class);
        baseDomainFindService = mock(BaseDomainFindService.class);
        appFrontProperty = new AppFrontProperty(null, "http://front.example.test:3000", "/login", 3000);
        TenantProperty property = new TenantProperty(300L, 1000, false, null);
        addressing = new PathTenantAddressing(
                new TenantSeqResolver(tenantFindService, property, new HostTenantCache(property)),
                baseDomainFindService, appFrontProperty);

        when(baseDomainFindService.findCurrent()).thenReturn(new BaseDomainData(1, BASE_DOMAIN));
        when(tenantFindService.findBySubdomain("acme")).thenReturn(tenant(ACME_SEQ, TenantStatus.ACTIVE, "acme"));
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static TenantData tenant(int seq, TenantStatus status, String subdomain) {
        TenantData t = new TenantData();
        ReflectionTestUtils.setField(t, "tenantSeq", seq);
        ReflectionTestUtils.setField(t, "status", status);
        ReflectionTestUtils.setField(t, "subdomain", subdomain);
        ReflectionTestUtils.setField(t, "domain", subdomain == null ? BASE_DOMAIN : subdomain + "." + BASE_DOMAIN);
        return t;
    }

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        request.setServletPath(uri);
        return request;
    }

    @Nested
    @DisplayName("resolve")
    class Resolve {

        @Test
        @DisplayName("/t/{slug} 의 둘째 세그먼트를 슬러그로 SUBDOMAIN 직접 조회 → contextPath 에뮬레이션 래퍼로 전달 (Host·베이스 도메인 무관)")
        void matchesMarkerSlug() {
            TenantMatch resolved = addressing.resolve(request("/t/acme/api/v1/users/me"));

            assertThat(resolved.isMatched()).isTrue();
            assertThat(resolved.tenantSeq()).isEqualTo(ACME_SEQ);
            assertThat(resolved.requestToForward()).isInstanceOf(TenantPathRequestWrapper.class);
            assertThat(resolved.requestToForward().getContextPath()).isEqualTo("/t/acme");
            assertThat(resolved.requestToForward().getServletPath()).isEqualTo("/api/v1/users/me");
            assertThat(resolved.requestToForward().getRequestURI()).isEqualTo("/t/acme/api/v1/users/me");
            verify(tenantFindService).findBySubdomain("acme");
            verify(tenantFindService, never()).findByDomain(anyString());
            verify(baseDomainFindService, never()).findCurrent();
        }

        @Test
        @DisplayName("같은 슬러그 재요청은 캐시 히트 — SUBDOMAIN 조회 1회, 베이스 도메인 조회 없음")
        void cachesBySlug() {
            addressing.resolve(request("/t/acme/api/v1/a"));
            addressing.resolve(request("/t/acme/mcp"));

            verify(tenantFindService, times(1)).findBySubdomain("acme");
            verify(baseDomainFindService, never()).findCurrent();
        }

        @Test
        @DisplayName("마커 뒤 미등록 슬러그는 NONE + 접두를 뗀 래퍼(fallback 테넌트로 진행, SUBDOMAIN 미등록 Host 와 대칭)이고 미스도 캐시된다")
        void unknownSlugIsNoneWithStrippedRequestAndCachedMiss() {
            when(tenantFindService.findBySubdomain("nope")).thenReturn(null);

            TenantMatch first = addressing.resolve(request("/t/nope/api/v1/users/me"));
            TenantMatch second = addressing.resolve(request("/t/nope/api/v1/users/me"));

            assertThat(first.status()).isEqualTo(TenantMatch.Status.NONE);
            assertThat(first.tenantSeq()).isNull();
            assertThat(first.requestToForward()).isInstanceOf(TenantPathRequestWrapper.class);
            assertThat(first.requestToForward().getContextPath()).isEqualTo("/t/nope");
            assertThat(first.requestToForward().getServletPath()).isEqualTo("/api/v1/users/me");
            assertThat(first.requestToForward().getRequestURI()).isEqualTo("/t/nope/api/v1/users/me");
            assertThat(second.status()).isEqualTo(TenantMatch.Status.NONE);
            verify(tenantFindService, times(1)).findBySubdomain("nope");
        }

        @Test
        @DisplayName("존재하되 비활성 테넌트는 INACTIVE (fallback 없이 404 대상)")
        void inactive() {
            when(tenantFindService.findBySubdomain("acme")).thenReturn(tenant(ACME_SEQ, TenantStatus.INACTIVE, "acme"));

            assertThat(addressing.resolve(request("/t/acme/api/v1/users/me")).status()).isEqualTo(TenantMatch.Status.INACTIVE);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                // 루트 경로(컨트롤러·MCP·SAS·정적)
                "/api/v1/users/me", "/mcp", "/oauth2/authorize", "/.well-known/x", "/_next/static/x.js", "/",
                // 구 문법 /{slug} — 마커가 없으니 슬러그로 보지 않는다(조회 없음)
                "/acme/api/v1/users/me",
                // 마커와 비슷하지만 다른 첫 세그먼트
                "/tt/acme/api", "/T/acme/api"})
        @DisplayName("첫 세그먼트가 마커 t 가 아니면 조회 없이 NONE(원본 요청, fallback 대상)")
        void noMarkerIsNone(String uri) {
            TenantMatch resolved = addressing.resolve(request(uri));

            assertThat(resolved.status()).isEqualTo(TenantMatch.Status.NONE);
            assertThat(resolved.requestToForward()).isNull();
            verify(tenantFindService, never()).findBySubdomain(anyString());
            verify(baseDomainFindService, never()).findCurrent();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "/.well-known/oauth-authorization-server/t/acme",
                "/.well-known/openid-configuration/t/acme",
                "/.well-known/oauth-protected-resource/t/acme/mcp"})
        @DisplayName("RFC 경로 삽입형 /.well-known/{doc}/t/{slug} 는 넷째 세그먼트를 슬러그로 매치하고 요청은 원본 그대로(SAS issuer 계산)")
        void wellKnownInsertionMatchesWithOriginalRequest(String uri) {
            MockHttpServletRequest original = request(uri);

            TenantMatch resolved = addressing.resolve(original);

            assertThat(resolved.isMatched()).isTrue();
            assertThat(resolved.tenantSeq()).isEqualTo(ACME_SEQ);
            assertThat(resolved.requestToForward()).isSameAs(original);
            assertThat(resolved.requestToForward().getContextPath()).isEmpty();
            verify(tenantFindService).findBySubdomain("acme");
        }

        @Test
        @DisplayName("삽입형 슬러그가 비활성이면 INACTIVE, 미등록·형식 불일치면 NONE(원본 — SAS/fallback 몫)")
        void wellKnownInsertionInactiveOrUnknown() {
            when(tenantFindService.findBySubdomain("acme")).thenReturn(tenant(ACME_SEQ, TenantStatus.INACTIVE, "acme"));
            when(tenantFindService.findBySubdomain("nope")).thenReturn(null);

            assertThat(addressing.resolve(request("/.well-known/oauth-authorization-server/t/acme")).status())
                    .isEqualTo(TenantMatch.Status.INACTIVE);
            TenantMatch unknown = addressing.resolve(request("/.well-known/oauth-authorization-server/t/nope"));
            assertThat(unknown.status()).isEqualTo(TenantMatch.Status.NONE);
            assertThat(unknown.requestToForward()).isNull();
            TenantMatch invalid = addressing.resolve(request("/.well-known/oauth-authorization-server/t/UPPER"));
            assertThat(invalid.status()).isEqualTo(TenantMatch.Status.NONE);
            assertThat(invalid.requestToForward()).isNull();

            verify(tenantFindService, never()).findBySubdomain("UPPER");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                // 삽입형이 아닌 .well-known — 마커가 셋째 세그먼트에 없다
                "/.well-known/oauth-authorization-server", "/.well-known/oauth-protected-resource/mcp",
                "/.well-known/oauth-authorization-server/acme", "/.well-known/t/acme"})
        @DisplayName(".well-known 이라도 셋째 세그먼트가 마커가 아니면 조회 없이 NONE")
        void wellKnownWithoutMarkerIsNone(String uri) {
            assertThat(addressing.resolve(request(uri)).status()).isEqualTo(TenantMatch.Status.NONE);

            verify(tenantFindService, never()).findBySubdomain(anyString());
        }

        @ParameterizedTest
        @ValueSource(strings = {
                // 슬러그 없음
                "/t", "/t/", "/t//x",
                // 형식 불일치(대문자·_·앞뒤 하이픈)
                "/t/INVALID_x/api", "/t/Acme/api", "/t/-acme/api", "/t/acme-/api"})
        @DisplayName("마커는 있는데 슬러그가 비었거나 형식 불일치면 조회 없이 NOT_FOUND(접두를 만들 수 없어 fallback 없이 404)")
        void markerWithInvalidSlugIsNotFound(String uri) {
            assertThat(addressing.resolve(request(uri)).status()).isEqualTo(TenantMatch.Status.NOT_FOUND);

            verify(tenantFindService, never()).findBySubdomain(anyString());
            verify(baseDomainFindService, never()).findCurrent();
        }

        @Test
        @DisplayName("퍼센트 인코딩된 슬러그(/t/%61cme)는 디코딩 슬러그가 유효해도 NOT_FOUND — 래퍼 contextPath 와 원본 requestURI 가 어긋나 500 이 나는 것을 막는다")
        void percentEncodedSlugIsNotFound() {
            MockHttpServletRequest encoded = new MockHttpServletRequest();
            encoded.setRequestURI("/t/%61cme/api/v1/users/me");
            encoded.setServletPath("/t/acme/api/v1/users/me");

            assertThat(addressing.resolve(encoded).status()).isEqualTo(TenantMatch.Status.NOT_FOUND);
            verify(tenantFindService, never()).findBySubdomain(anyString());
        }

        @Test
        @DisplayName("루트 라우트와 같은 이름의 슬러그(drive·api·mcp)도 마커 뒤라면 그냥 슬러그 — 예약어 없음")
        void routeLikeSlugIsJustASlug() {
            when(tenantFindService.findBySubdomain("drive")).thenReturn(tenant(11, TenantStatus.ACTIVE, "drive"));
            when(tenantFindService.findBySubdomain("mcp")).thenReturn(tenant(12, TenantStatus.ACTIVE, "mcp"));

            TenantMatch drive = addressing.resolve(request("/t/drive/drive/"));
            TenantMatch mcp = addressing.resolve(request("/t/mcp/mcp"));

            assertThat(drive.isMatched()).isTrue();
            assertThat(drive.tenantSeq()).isEqualTo(11);
            assertThat(drive.requestToForward().getServletPath()).isEqualTo("/drive/");
            assertThat(mcp.isMatched()).isTrue();
            assertThat(mcp.tenantSeq()).isEqualTo(12);
            assertThat(mcp.requestToForward().getServletPath()).isEqualTo("/mcp");
        }

        @Test
        @DisplayName("베이스 도메인 미설정·Host 무엇이든 슬러그 매치는 그대로 된다 (PATH 판별은 경로만 본다)")
        void independentOfBaseDomainAndHost() {
            when(baseDomainFindService.findCurrent()).thenReturn(null);
            MockHttpServletRequest req = request("/t/acme/api/v1/users/me");
            req.addHeader(HttpHeaders.HOST, "other.example.com");

            TenantMatch resolved = addressing.resolve(req);

            assertThat(resolved.isMatched()).isTrue();
            assertThat(resolved.tenantSeq()).isEqualTo(ACME_SEQ);
            verify(baseDomainFindService, never()).findCurrent();
        }
    }

    @Nested
    @DisplayName("isAcceptableBaseDomain — PATH 는 베이스 도메인이 곧 공개 호스트라 IPv4·localhost 도 허용")
    class AcceptableBaseDomain {

        @Test
        @DisplayName("도메인·IPv4·localhost 허용, 잘못된 IP·점 없는 다른 호스트명·빈 값은 거부")
        void domainOrIpv4() {
            assertThat(addressing.isAcceptableBaseDomain("drive.thinkfree.com")).isTrue();
            assertThat(addressing.isAcceptableBaseDomain("192.168.0.10")).isTrue();
            assertThat(addressing.isAcceptableBaseDomain("localhost")).isTrue();
            assertThat(addressing.isAcceptableBaseDomain("256.1.1.1")).isFalse();
            assertThat(addressing.isAcceptableBaseDomain("intranet")).isFalse();
            assertThat(addressing.isAcceptableBaseDomain("")).isFalse();
            assertThat(addressing.isAcceptableBaseDomain(null)).isFalse();
        }

        @Test
        @DisplayName("isAcceptableTenantDomain: 도메인·IPv4 베이스 위 합성값(acme.192.168.0.10)·순수 IPv4(루트 테넌트) 허용, 그 외 거부")
        void tenantDomainAllowsIpv4Base() {
            assertThat(addressing.isAcceptableTenantDomain("acme.drive.thinkfree.com")).isTrue();
            assertThat(addressing.isAcceptableTenantDomain("acme.192.168.0.10")).isTrue();
            assertThat(addressing.isAcceptableTenantDomain("192.168.0.10")).isTrue();
            assertThat(addressing.isAcceptableTenantDomain("acme.256.1.1.1")).isFalse();
            assertThat(addressing.isAcceptableTenantDomain("Acme.192.168.0.10")).isFalse();
            assertThat(addressing.isAcceptableTenantDomain("localhost")).isTrue();
            assertThat(addressing.isAcceptableTenantDomain("acme.localhost")).isTrue();
            assertThat(addressing.isAcceptableTenantDomain("intranet")).isFalse();
            assertThat(addressing.isAcceptableTenantDomain("")).isFalse();
        }

        @Test
        @DisplayName("IPv4 베이스 도메인이면 origin 은 scheme://ip[:port]/t/slug")
        void originWithIpv4BaseDomain() {
            when(baseDomainFindService.findCurrent()).thenReturn(new BaseDomainData(1, "192.168.0.10"));

            assertThat(addressing.origin(tenant(ACME_SEQ, TenantStatus.ACTIVE, "acme"), "http", 8080))
                    .isEqualTo("http://192.168.0.10:8080/t/acme");
        }
    }

    @Nested
    @DisplayName("origin")
    class Origin {

        @Test
        @DisplayName("scheme://baseDomain[/t/slug]")
        void basePlusSlug() {
            assertThat(addressing.origin(tenant(ACME_SEQ, TenantStatus.ACTIVE, "acme"), "https", null))
                    .isEqualTo("https://drive.thinkfree.com/t/acme");
        }

        @Test
        @DisplayName("포트는 host 뒤, 마커·슬러그는 그 뒤")
        void withPort() {
            assertThat(addressing.origin(tenant(ACME_SEQ, TenantStatus.ACTIVE, "acme"), "http", 8080))
                    .isEqualTo("http://drive.thinkfree.com:8080/t/acme");
        }

        @Test
        @DisplayName("슬러그 없는 루트 테넌트는 경로를 붙이지 않는다")
        void rootTenant() {
            assertThat(addressing.origin(tenant(1, TenantStatus.ACTIVE, null), "https", null))
                    .isEqualTo("https://drive.thinkfree.com");
        }

        @Test
        @DisplayName("베이스 도메인 미설정 → 현재 요청 host(X-Forwarded-Host 우선, 포트 제거)로 폴백")
        void fallsBackToRequestHost() {
            when(baseDomainFindService.findCurrent()).thenReturn(null);
            MockHttpServletRequest current = request("/t/acme/api/v1/x");
            current.addHeader(HttpHeaders.HOST, "localhost:8081");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(current));

            assertThat(addressing.origin(tenant(ACME_SEQ, TenantStatus.ACTIVE, "acme"), "http", 8081))
                    .isEqualTo("http://localhost:8081/t/acme");
        }

        @Test
        @DisplayName("베이스에 입력한 스킴·포트가 호출부 값보다 우선 — 요청이 없는 비동기(https 폴백)에도 http://ip:port 그대로")
        void baseSchemeAndPortWin() {
            when(baseDomainFindService.findCurrent()).thenReturn(new BaseDomainData(1, "192.168.0.10", "http", 8080));

            assertThat(addressing.origin(tenant(ACME_SEQ, TenantStatus.ACTIVE, "acme"), "https", null))
                    .isEqualTo("http://192.168.0.10:8080/t/acme");
            assertThat(addressing.origin(tenant(ACME_SEQ, TenantStatus.ACTIVE, "acme"), "http", 3000))
                    .isEqualTo("http://192.168.0.10:8080/t/acme");
        }

        @Test
        @DisplayName("베이스에 포트만 입력 — 스킴은 호출부(요청) 값, 스킴만 입력 — 포트 생략")
        void basePartial() {
            when(baseDomainFindService.findCurrent()).thenReturn(new BaseDomainData(1, "localhost", null, 8443));
            assertThat(addressing.origin(tenant(ACME_SEQ, TenantStatus.ACTIVE, "acme"), "https", null))
                    .isEqualTo("https://localhost:8443/t/acme");

            when(baseDomainFindService.findCurrent()).thenReturn(new BaseDomainData(1, "drive.thinkfree.com", "https", null));
            assertThat(addressing.origin(tenant(ACME_SEQ, TenantStatus.ACTIVE, "acme"), "http", 3000))
                    .isEqualTo("https://drive.thinkfree.com/t/acme");
        }

        @Test
        @DisplayName("베이스 도메인·요청 모두 없으면 app.front.base-url 의 host")
        void fallsBackToFrontBaseUrl() {
            when(baseDomainFindService.findCurrent()).thenReturn(null);

            assertThat(addressing.origin(tenant(ACME_SEQ, TenantStatus.ACTIVE, "acme"), "https", null))
                    .isEqualTo("https://front.example.test/t/acme");
        }
    }
}
