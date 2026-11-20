package com.thinkfree.drive.abstracts;

import com.thinkfree.drive.common.DriveFixtures;
import com.thinkfree.drive.tenant.cache.HostTenantCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link AbstractMvcTest} 의 테넌트 주소 모드 스위치 검증.
 * yml {@code tenant.resolution-mode} 가 PATH 면 요청 경로에 {@code /{slug}} 가 붙고, SUBDOMAIN 이면 Host 만으로 판별한다.
 * 어느 모드든 같은 테넌트 공개 API({@code /api/v1/branding})가 200 이어야 한다.
 * 전환 실행: {@code TENANT_RESOLUTION_MODE=SUBDOMAIN mvn test -Dtest=TenantModeSwitchMvcTest}
 *
 * <p>테넌트 선언 4경로({@code setTestTenant}·{@code setTestSlug}·{@code setTestHost}·{@code setTenantLess})가 모두 같은 규칙으로 요청에 반영되는지 본다.
 * 테넌트 {@code default}(DOMAIN default.drive.thinkfree.com)·{@code default-add}(DOMAIN default-add.drive.thinkfree.com)는
 * 테스트마다 {@link DriveFixtures#tenantAddresses()} 로 만들고 테스트 트랜잭션으로 되돌린다.
 */
@DisplayName("AbstractMvcTest — tenant.resolution-mode 스위치")
@Transactional
class TenantModeSwitchMvcTest extends AbstractMvcTest {

    private static final String BRANDING = "/api/v1/branding";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private HostTenantCache hostTenantCache;

    @BeforeEach
    void pinHost() {
        DriveFixtures.of(jdbc, hostTenantCache).tenantAddresses();
        setTestHost(DEFAULT_TEST_HOST);
    }

    // ---------------------------------------------------------------- setTestHost (호환 경로)

    @Test
    @DisplayName("setTestHost 매치 host: PATH 면 /{slug} 접두 + servletPath 전체, SUBDOMAIN 이면 접두 없음 — 둘 다 200")
    void matchedHostFollowsMode() throws Exception {
        MvcResult result = mockMvc.perform(get(BRANDING)).andExpect(status().isOk()).andReturn();
        String uri = result.getRequest().getRequestURI();

        if (isPathMode()) {
            assertThat(tenantPrefix()).isNotEmpty();
            assertThat(uri).isEqualTo(tenantPrefix() + BRANDING);
        } else {
            assertThat(tenantPrefix()).isEmpty();
            assertThat(uri).isEqualTo(BRANDING);
        }
        assertThat(result.getRequest().getServletPath()).isEqualTo(uri);
        assertThat(result.getRequest().getPathInfo()).isNull();
        assertThat(result.getRequest().getHeader("Host")).isEqualTo(DEFAULT_TEST_HOST);
    }

    @Test
    @DisplayName("헬퍼를 우회한 직접 빌드도 withHost 를 거치면 같은 보정을 받는다")
    void directBuilderGetsSamePrefix() throws Exception {
        MvcResult result = mockMvc.perform(withHost(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(BRANDING)))
                .andExpect(status().isOk()).andReturn();

        assertThat(result.getRequest().getRequestURI()).isEqualTo(tenantPrefix() + BRANDING);
    }

    @Test
    @DisplayName("setTestHost 미매치 host 는 어느 모드든 접두 없이 fallback 으로 간다")
    void unmatchedHostHasNoPrefix() throws Exception {
        setTestHost("no-such-tenant.example.test");

        MvcResult result = mockMvc.perform(get(BRANDING)).andReturn();

        assertThat(tenantSlug()).isNull();
        assertThat(tenantPrefix()).isEmpty();
        assertThat(result.getRequest().getRequestURI()).isEqualTo(BRANDING);
    }

    // ---------------------------------------------------------------- setTestTenant (주 선언)

    @Test
    @DisplayName("setTestTenant(slug): Host 는 그 테넌트 DOMAIN, PATH 면 /t/{slug} 접두 — 둘 다 200")
    void setTestTenantFillsHostAndPrefix() throws Exception {
        setTestTenant("default-add");

        assertThat(getTestHost()).isEqualTo(DEFAULT_ADD_TEST_HOST);
        assertThat(tenantSlug()).isEqualTo("default-add");

        MvcResult result = mockMvc.perform(get(BRANDING)).andExpect(status().isOk()).andReturn();
        String uri = result.getRequest().getRequestURI();

        assertThat(result.getRequest().getHeader("Host")).isEqualTo(DEFAULT_ADD_TEST_HOST);
        assertThat(uri).isEqualTo(isPathMode() ? "/t/default-add" + BRANDING : BRANDING);
        assertThat(result.getRequest().getServletPath()).isEqualTo(uri);
    }

    @Test
    @DisplayName("setTestTenant 와 setTestHost 는 같은 테넌트에 대해 같은 (host, slug) 로 귀결된다")
    void setTestTenantAndSetTestHostConverge() {
        setTestTenant("default-add");
        String hostBySlug = getTestHost();
        String slugBySlug = tenantSlug();

        setTestHost(DEFAULT_ADD_TEST_HOST);

        assertThat(getTestHost()).isEqualTo(hostBySlug);
        assertThat(tenantSlug()).isEqualTo(slugBySlug);
    }

    @Test
    @DisplayName("setTestTenant 없는 슬러그·빈 값은 fallback 으로 흘러가지 않고 즉시 실패한다 (예약어 개념 없음 — /t/{slug} 마커 문법)")
    void setTestTenantFailsFast() {
        assertThatThrownBy(() -> setTestTenant("no-such-slug-0921"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no-such-slug-0921");
        // 루트 라우트와 같은 이름(api)도 예약어가 아니라 그냥 "없는 슬러그" 다
        assertThatThrownBy(() -> setTestTenant("api"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'api' 인 테넌트가 없음");
        assertThatThrownBy(() -> setTestTenant(" "))
                .isInstanceOf(IllegalStateException.class);

        // 실패 후에도 직전 선언(default)이 그대로 유지된다
        assertThat(getTestHost()).isEqualTo(DEFAULT_TEST_HOST);
    }

    // ---------------------------------------------------------------- setTestSlug (주 선언의 static 판)

    @Test
    @DisplayName("setTestSlug(slug): static 으로 슬러그만 두고, 첫 요청 때 Host=DOMAIN 파생 + PATH 면 /t/{slug} 접두 — 둘 다 200")
    void setTestSlugResolvesHostLazily() throws Exception {
        setTestSlug("default-add");

        assertThat(tenantSlug()).isEqualTo("default-add");
        assertThat(getTestHost()).isEqualTo(DEFAULT_ADD_TEST_HOST);

        MvcResult result = mockMvc.perform(get(BRANDING)).andExpect(status().isOk()).andReturn();
        String uri = result.getRequest().getRequestURI();

        assertThat(result.getRequest().getHeader("Host")).isEqualTo(DEFAULT_ADD_TEST_HOST);
        assertThat(uri).isEqualTo(isPathMode() ? "/t/default-add" + BRANDING : BRANDING);
        assertThat(result.getRequest().getServletPath()).isEqualTo(uri);
    }

    @Test
    @DisplayName("setTestSlug 와 setTestTenant 는 같은 슬러그에 대해 같은 (host, slug) 로 귀결된다")
    void setTestSlugAndSetTestTenantConverge() {
        setTestTenant("default-add");
        String hostByTenant = getTestHost();
        String slugByTenant = tenantSlug();

        setTestSlug("default-add");

        assertThat(getTestHost()).isEqualTo(hostByTenant);
        assertThat(tenantSlug()).isEqualTo(slugByTenant);
    }

    @Test
    @DisplayName("setTestSlug 없는 슬러그는 첫 요청(Host 파생)에서 즉시 실패하고, 빈 값은 선언 시점에 실패한다")
    void setTestSlugFailsFastOnFirstRequest() {
        setTestSlug("no-such-slug-0928");
        assertThat(tenantSlug()).isEqualTo("no-such-slug-0928"); // 슬러그 자체는 저장돼 있다

        assertThatThrownBy(() -> mockMvc.perform(get(BRANDING)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("setTestSlug")
                .hasMessageContaining("no-such-slug-0928");

        assertThatThrownBy(() -> setTestSlug(" "))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---------------------------------------------------------------- setTenantLess

    @Test
    @DisplayName("setTenantLess: 양 모드 모두 중립 Host + 접두 없음")
    void tenantLessHasNoPrefixInBothModes() throws Exception {
        setTenantLess();

        assertThat(getTestHost()).isEqualTo(TENANT_LESS_HOST);
        assertThat(tenantSlug()).isNull();
        assertThat(tenantPrefix()).isEmpty();

        MvcResult result = mockMvc.perform(get(BRANDING)).andReturn();
        assertThat(result.getRequest().getRequestURI()).isEqualTo(BRANDING);
        assertThat(result.getRequest().getHeader("Host")).isEqualTo(TENANT_LESS_HOST);
    }
}
