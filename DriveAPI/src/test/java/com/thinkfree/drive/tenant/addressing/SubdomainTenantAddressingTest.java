package com.thinkfree.drive.tenant.addressing;

import com.thinkfree.drive.app.properties.AppFrontProperty;
import com.thinkfree.drive.basedomain.data.BaseDomainData;
import com.thinkfree.drive.basedomain.service.BaseDomainFindService;
import com.thinkfree.drive.tenant.cache.HostTenantCache;
import com.thinkfree.drive.tenant.data.TenantData;
import com.thinkfree.drive.tenant.enums.TenantStatus;
import com.thinkfree.drive.tenant.properties.TenantProperty;
import com.thinkfree.drive.tenant.resolver.TenantSeqResolver;
import com.thinkfree.drive.tenant.service.TenantFindService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("SubdomainTenantAddressing — Host 기반 매치·origin")
class SubdomainTenantAddressingTest {

    private static final String ADMIN_DOMAIN = "admin.drive.thinkfree.com";
    private static final String GOOGLE_DOMAIN = "google.drive.thinkfree.com";
    private static final int ADMIN_SEQ = 1;
    private static final int GOOGLE_SEQ = 4;

    private TenantFindService tenantFindService;
    private BaseDomainFindService baseDomainFindService;
    private SubdomainTenantAddressing addressing;

    @BeforeEach
    void setUp() {
        tenantFindService = mock(TenantFindService.class);
        baseDomainFindService = mock(BaseDomainFindService.class);
        TenantProperty property = new TenantProperty(300L, 1000, false, null);
        addressing = new SubdomainTenantAddressing(
                new TenantSeqResolver(tenantFindService, property, new HostTenantCache(property)),
                new AppFrontProperty(null, "http://front.example.test:3000", "/login", 3000),
                baseDomainFindService);
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static TenantData tenant(int seq, TenantStatus status, String domain) {
        TenantData t = new TenantData();
        ReflectionTestUtils.setField(t, "tenantSeq", seq);
        ReflectionTestUtils.setField(t, "status", status);
        ReflectionTestUtils.setField(t, "domain", domain);
        return t;
    }

    private static MockHttpServletRequest requestWithHost(String host) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/users/me");
        if (host != null) {
            request.addHeader(HttpHeaders.HOST, host);
        }
        return request;
    }

    @Nested
    @DisplayName("resolve")
    class Resolve {

        @Test
        @DisplayName("Host 전체로 도메인 매치 → 원본 요청 그대로 (래핑 없음)")
        void matchesByHost() {
            when(tenantFindService.findByDomain(ADMIN_DOMAIN)).thenReturn(tenant(ADMIN_SEQ, TenantStatus.ACTIVE, ADMIN_DOMAIN));
            MockHttpServletRequest request = requestWithHost(ADMIN_DOMAIN);

            TenantMatch resolved = addressing.resolve(request);

            assertThat(resolved.isMatched()).isTrue();
            assertThat(resolved.tenantSeq()).isEqualTo(ADMIN_SEQ);
            assertThat(resolved.requestToForward()).isSameAs(request);
        }

        @ParameterizedTest
        @ValueSource(strings = {ADMIN_DOMAIN + ":8080", ADMIN_DOMAIN + ":8443", ADMIN_DOMAIN + ":443"})
        @DisplayName("Host 에 포트가 붙어 있어도 포트를 뗀 도메인으로 조회해 매치된다")
        void matchesByHostWithPort(String hostWithPort) {
            when(tenantFindService.findByDomain(ADMIN_DOMAIN)).thenReturn(tenant(ADMIN_SEQ, TenantStatus.ACTIVE, ADMIN_DOMAIN));
            MockHttpServletRequest request = requestWithHost(hostWithPort);

            TenantMatch resolved = addressing.resolve(request);

            assertThat(resolved.isMatched()).isTrue();
            assertThat(resolved.tenantSeq()).isEqualTo(ADMIN_SEQ);
            verify(tenantFindService).findByDomain(ADMIN_DOMAIN);
            verify(tenantFindService, never()).findByDomain(hostWithPort);
        }

        @Test
        @DisplayName("X-Forwarded-Host 가 Host 보다 우선, 콤마 다중 값은 첫 값")
        void prefersForwardedHost() {
            when(tenantFindService.findByDomain(GOOGLE_DOMAIN)).thenReturn(tenant(GOOGLE_SEQ, TenantStatus.ACTIVE, GOOGLE_DOMAIN));
            MockHttpServletRequest request = requestWithHost(ADMIN_DOMAIN);
            request.addHeader("X-Forwarded-Host", GOOGLE_DOMAIN + ", internal");

            assertThat(addressing.resolve(request).tenantSeq()).isEqualTo(GOOGLE_SEQ);
            verify(tenantFindService, never()).findByDomain(ADMIN_DOMAIN);
        }

        @ParameterizedTest
        @ValueSource(strings = {"ADMIN.DRIVE.THINKFREE.COM", "Admin.Drive.Thinkfree.Com", "admin.drive.thinkfree.com:8080"})
        @DisplayName("소문자 정규화·포트 제거 후 매치")
        void normalizesHost(String host) {
            when(tenantFindService.findByDomain(ADMIN_DOMAIN)).thenReturn(tenant(ADMIN_SEQ, TenantStatus.ACTIVE, ADMIN_DOMAIN));

            assertThat(addressing.resolve(requestWithHost(host)).tenantSeq()).isEqualTo(ADMIN_SEQ);
        }

        @Test
        @DisplayName("Host 없음 → NONE, 조회 없음")
        void noHost() {
            assertThat(addressing.resolve(requestWithHost(null)).status()).isEqualTo(TenantMatch.Status.NONE);
            verifyNoInteractions(tenantFindService);
        }

        @Test
        @DisplayName("미등록 → NONE(fallback 은 필터 몫), 존재하되 비활성 → INACTIVE(즉시 404 대상)")
        void unknownVsInactive() {
            when(tenantFindService.findByDomain("unknown.drive.thinkfree.com")).thenReturn(null);
            when(tenantFindService.findByDomain(GOOGLE_DOMAIN)).thenReturn(tenant(GOOGLE_SEQ, TenantStatus.INACTIVE, GOOGLE_DOMAIN));

            assertThat(addressing.resolve(requestWithHost("unknown.drive.thinkfree.com")).status()).isEqualTo(TenantMatch.Status.NONE);
            assertThat(addressing.resolve(requestWithHost(GOOGLE_DOMAIN)).status()).isEqualTo(TenantMatch.Status.INACTIVE);
            verify(tenantFindService, never()).findSingleActiveSelfManagedTenantSeq();
        }

        @Test
        @DisplayName("미등록·비활성 결과도 캐시된다 (테넌트 CRUD 시 invalidateAll)")
        void cachesMissAndInactive() {
            when(tenantFindService.findByDomain("unknown.drive.thinkfree.com")).thenReturn(null);
            when(tenantFindService.findByDomain(GOOGLE_DOMAIN)).thenReturn(tenant(GOOGLE_SEQ, TenantStatus.INACTIVE, GOOGLE_DOMAIN));

            addressing.resolve(requestWithHost("unknown.drive.thinkfree.com"));
            addressing.resolve(requestWithHost("unknown.drive.thinkfree.com"));
            addressing.resolve(requestWithHost(GOOGLE_DOMAIN));
            assertThat(addressing.resolve(requestWithHost(GOOGLE_DOMAIN)).status()).isEqualTo(TenantMatch.Status.INACTIVE);

            verify(tenantFindService, times(1)).findByDomain("unknown.drive.thinkfree.com");
            verify(tenantFindService, times(1)).findByDomain(GOOGLE_DOMAIN);
        }

        @Test
        @DisplayName("같은 host 재요청은 캐시 히트, 다른 host 는 각각 조회")
        void cachesPerHost() {
            when(tenantFindService.findByDomain(ADMIN_DOMAIN)).thenReturn(tenant(ADMIN_SEQ, TenantStatus.ACTIVE, ADMIN_DOMAIN));
            when(tenantFindService.findByDomain(GOOGLE_DOMAIN)).thenReturn(tenant(GOOGLE_SEQ, TenantStatus.ACTIVE, GOOGLE_DOMAIN));

            for (String host : new String[]{ADMIN_DOMAIN, GOOGLE_DOMAIN, ADMIN_DOMAIN, GOOGLE_DOMAIN}) {
                addressing.resolve(requestWithHost(host));
            }

            verify(tenantFindService, times(1)).findByDomain(ADMIN_DOMAIN);
            verify(tenantFindService, times(1)).findByDomain(GOOGLE_DOMAIN);
        }
    }

    @Nested
    @DisplayName("origin")
    class Origin {

        @Test
        @DisplayName("scheme://tenant.domain, 포트 null 이면 생략")
        void tenantDomain() {
            assertThat(addressing.origin(tenant(ADMIN_SEQ, TenantStatus.ACTIVE, ADMIN_DOMAIN), "https", null))
                    .isEqualTo("https://admin.drive.thinkfree.com");
        }

        @Test
        @DisplayName("포트가 있으면 :port")
        void withPort() {
            assertThat(addressing.origin(tenant(ADMIN_SEQ, TenantStatus.ACTIVE, ADMIN_DOMAIN), "http", 3000))
                    .isEqualTo("http://admin.drive.thinkfree.com:3000");
        }

        @Test
        @DisplayName("테넌트 domain 이 비어 있으면(신규 설치) 현재 요청 host 로 폴백 — https://null 방지")
        void fallsBackToRequestHostWhenDomainBlank() {
            MockHttpServletRequest current = requestWithHost("localhost:8080");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(current));

            assertThat(addressing.origin(tenant(ADMIN_SEQ, TenantStatus.ACTIVE, null), "http", 8080))
                    .isEqualTo("http://localhost:8080");
        }

        @Test
        @DisplayName("domain·요청 모두 없으면 app.front.base-url 의 host")
        void fallsBackToFrontBaseUrlWhenNoRequest() {
            assertThat(addressing.origin(tenant(ADMIN_SEQ, TenantStatus.ACTIVE, ""), "https", null))
                    .isEqualTo("https://front.example.test");
        }

        @Test
        @DisplayName("베이스에 묶인 테넌트 — 베이스에 입력한 스킴·포트가 호출부 값보다 우선")
        void baseSchemeAndPortWin() {
            when(baseDomainFindService.findCurrent()).thenReturn(new BaseDomainData(1, "drive.thinkfree.com", "http", 8443));
            TenantData bound = tenant(ADMIN_SEQ, TenantStatus.ACTIVE, ADMIN_DOMAIN);
            ReflectionTestUtils.setField(bound, "baseDomainSeq", 1);

            assertThat(addressing.origin(bound, "https", null)).isEqualTo("http://admin.drive.thinkfree.com:8443");
            assertThat(addressing.origin(bound, "https", 3000)).isEqualTo("http://admin.drive.thinkfree.com:8443");
        }

        @Test
        @DisplayName("베이스에 포트만 입력 — 스킴은 호출부(요청) 값")
        void basePortOnly() {
            when(baseDomainFindService.findCurrent()).thenReturn(new BaseDomainData(1, "drive.thinkfree.com", null, 8443));
            TenantData bound = tenant(ADMIN_SEQ, TenantStatus.ACTIVE, ADMIN_DOMAIN);
            ReflectionTestUtils.setField(bound, "baseDomainSeq", 1);

            assertThat(addressing.origin(bound, "https", null)).isEqualTo("https://admin.drive.thinkfree.com:8443");
        }

        @Test
        @DisplayName("베이스에 스킴만 입력 — 포트는 생략(호출부 포트 무시)")
        void baseSchemeOnly() {
            when(baseDomainFindService.findCurrent()).thenReturn(new BaseDomainData(1, "drive.thinkfree.com", "https", null));
            TenantData bound = tenant(ADMIN_SEQ, TenantStatus.ACTIVE, ADMIN_DOMAIN);
            ReflectionTestUtils.setField(bound, "baseDomainSeq", 1);

            assertThat(addressing.origin(bound, "http", 3000)).isEqualTo("https://admin.drive.thinkfree.com");
        }

        @Test
        @DisplayName("베이스에 묶이지 않은 테넌트(base_domain_seq null) — 베이스 스킴·포트를 적용하지 않음")
        void unboundTenantIgnoresBase() {
            when(baseDomainFindService.findCurrent()).thenReturn(new BaseDomainData(1, "drive.thinkfree.com", "http", 8443));

            assertThat(addressing.origin(tenant(ADMIN_SEQ, TenantStatus.ACTIVE, ADMIN_DOMAIN), "https", null))
                    .isEqualTo("https://admin.drive.thinkfree.com");
            verify(baseDomainFindService, never()).findCurrent();
        }
    }

    @Nested
    @DisplayName("isAcceptableBaseDomain — SUBDOMAIN 은 앞에 서브도메인을 붙여야 하므로 도메인·localhost 만")
    class AcceptableBaseDomain {

        @Test
        @DisplayName("도메인·localhost 는 허용, IPv4·점 없는 다른 호스트명·빈 값은 거부")
        void domainOrLocalhost() {
            assertThat(addressing.isAcceptableBaseDomain("drive.thinkfree.com")).isTrue();
            assertThat(addressing.isAcceptableBaseDomain("localhost")).isTrue();
            assertThat(addressing.isAcceptableBaseDomain("192.168.0.10")).isFalse();
            assertThat(addressing.isAcceptableBaseDomain("intranet")).isFalse();
            assertThat(addressing.isAcceptableBaseDomain("")).isFalse();
            assertThat(addressing.isAcceptableBaseDomain(null)).isFalse();
        }

        @Test
        @DisplayName("isAcceptableTenantDomain: 합성 domain 이 실제 Host 라 도메인(acme.localhost 포함)·localhost 만 — IPv4 계열은 거부")
        void tenantDomainIsDomainOnly() {
            assertThat(addressing.isAcceptableTenantDomain("acme.drive.thinkfree.com")).isTrue();
            assertThat(addressing.isAcceptableTenantDomain("drive.thinkfree.com")).isTrue();
            assertThat(addressing.isAcceptableTenantDomain("acme.localhost")).isTrue();
            assertThat(addressing.isAcceptableTenantDomain("localhost")).isTrue();
            assertThat(addressing.isAcceptableTenantDomain("acme.192.168.0.10")).isFalse();
            assertThat(addressing.isAcceptableTenantDomain("192.168.0.10")).isFalse();
        }
    }
}
