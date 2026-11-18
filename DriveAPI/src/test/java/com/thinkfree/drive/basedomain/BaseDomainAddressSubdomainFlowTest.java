package com.thinkfree.drive.basedomain;

import com.thinkfree.drive.api.v1.superadmin.basedomain.request.SuperAdminBaseDomainUpsertRequest;
import com.thinkfree.drive.api.v1.superadmin.basedomain.usecase.SuperAdminBaseDomainUseCase;
import com.thinkfree.drive.basedomain.exception.BaseDomainErrorCode;
import com.thinkfree.drive.common.ServiceTest;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.global.service.ClientDomainService;
import com.thinkfree.drive.tenant.addressing.SubdomainTenantAddressing;
import com.thinkfree.drive.tenant.addressing.TenantAddressingStrategy;
import com.thinkfree.drive.tenant.addressing.TenantMatch;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * SUBDOMAIN 모드 통합 흐름 — 베이스 저장 → 테넌트 domain 재계산 → 인바운드 Host(포트 제거) 판별 → 아웃바운드 origin.
 * PATH 모드에서 쓰던 데이터(IP 베이스)를 도메인 베이스로 다시 저장해 재분배되는지도 본다. PostgreSQL Testcontainers, 트랜잭션 롤백.
 */
@TestPropertySource(properties = "tenant.resolution-mode=SUBDOMAIN")
@DisplayName("베이스 도메인 주소 흐름 — SUBDOMAIN 모드")
class BaseDomainAddressSubdomainFlowTest extends ServiceTest {

    @Autowired
    private SuperAdminBaseDomainUseCase baseDomainUseCase;
    @Autowired
    private ClientDomainService clientDomainService;
    @Autowired
    private TenantAddressingStrategy addressingStrategy;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager em;

    /** SpringBootTest(MOCK) 는 테스트마다 가짜 요청(http)을 RequestContextHolder 에 넣는다 — 비우고 시작해 "요청 없음(@Async·스케줄러)" 을 기본으로. */
    @BeforeEach
    void login() {
        RequestContextHolder.resetRequestAttributes();
        CustomUserDetails details = Mockito.mock(CustomUserDetails.class);
        given(details.getUserSeq()).willReturn(1);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(details, null, List.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private void saveBase(String input) {
        baseDomainUseCase.upsert(new SuperAdminBaseDomainUpsertRequest(input));
        em.flush();
        em.clear();
    }

    private int insertBoundTenant(String slug, String baseHost) {
        Integer seq = jdbc.queryForObject("""
                INSERT INTO tb_tenant (tenant_name, tenant_type, status, login_type, domain, subdomain, base_domain_seq, register_user_seq)
                VALUES (?, 'SYSTEM_MANAGED', 'ACTIVE', 'LOCAL', ?, ?, 1, 1) RETURNING tenant_seq""",
                Integer.class, "flow-" + slug, slug + "." + baseHost, slug);
        return seq == null ? -1 : seq;
    }

    private Map<String, Object> tenantRow(int tenantSeq) {
        return jdbc.queryForMap("SELECT domain, subdomain FROM tb_tenant WHERE tenant_seq = ?", tenantSeq);
    }

    private TenantMatch resolveHost(String host) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.addHeader("Host", host);
        return addressingStrategy.resolve(request);
    }

    @Test
    @DisplayName("SUBDOMAIN 전략이 로딩됨")
    void strategyLoaded() {
        assertThat(addressingStrategy).isInstanceOf(SubdomainTenantAddressing.class);
    }

    @Test
    @DisplayName("스킴·도메인·포트 베이스 — tenant.domain 은 포트 없는 slug.호스트, 인바운드는 포트를 떼고 매칭, 아웃바운드는 입력 스킴·포트")
    void domainWithSchemeAndPort() {
        saveBase("https://drive.example.com:8443");
        int acme = insertBoundTenant("acme", "drive.example.com");

        assertThat(tenantRow(acme).get("domain")).isEqualTo("acme.drive.example.com");
        assertThat(resolveHost("acme.drive.example.com:8443").tenantSeq()).isEqualTo(acme);
        assertThat(resolveHost("acme.drive.example.com").tenantSeq()).as("포트 없이 와도 같은 테넌트").isEqualTo(acme);
        assertThat(clientDomainService.getDomain(acme)).isEqualTo("https://acme.drive.example.com:8443");
    }

    @Test
    @DisplayName("localhost:포트 베이스 — acme.localhost 로 판별·아웃바운드")
    void localhostWithPort() {
        saveBase("http://localhost:3000");
        int acme = insertBoundTenant("acme", "localhost");

        assertThat(resolveHost("acme.localhost:3000").tenantSeq()).isEqualTo(acme);
        assertThat(clientDomainService.getDomain(acme)).isEqualTo("http://acme.localhost:3000");
    }

    @ParameterizedTest(name = "거부: {0}")
    @ValueSource(strings = {"192.168.0.10", "192.168.0.10:8080", "http://192.168.0.10:8080", "intranet"})
    @DisplayName("IP·점 없는 이름은 스킴·포트와 무관하게 거부")
    void rejectsIp(String input) {
        assertThatThrownBy(() -> baseDomainUseCase.upsert(new SuperAdminBaseDomainUpsertRequest(input)))
                .isInstanceOf(ApiRuntimeException.class)
                .satisfies(e -> assertThat(((ApiRuntimeException) e).getCode()).isEqualTo(BaseDomainErrorCode.INVALID_BASE_DOMAIN));
    }

    @Test
    @DisplayName("PATH 모드에서 쓰던 IP 베이스 데이터 → 도메인 베이스로 다시 저장하면 slug 유지·domain 재분배·Host 판별 정상")
    void fromPathModeIpBase() {
        // PATH 모드가 남긴 모양: 베이스 IP + 테넌트 acme.192.168.0.10
        jdbc.update("INSERT INTO tb_base_domain (base_domain_seq, base_domain, scheme, port, register_user_seq) VALUES (1, '192.168.0.10', 'http', 8080, 1)");
        int acme = insertBoundTenant("acme", "192.168.0.10");
        assertThat(resolveHost("acme.192.168.0.10").isMatched()).as("DB 에는 있어도 의미 없는 Host").isTrue();

        saveBase("https://drive.example.com");

        Map<String, Object> row = tenantRow(acme);
        assertThat(row.get("subdomain")).isEqualTo("acme");
        assertThat(row.get("domain")).isEqualTo("acme.drive.example.com");
        assertThat(resolveHost("acme.drive.example.com").tenantSeq()).isEqualTo(acme);
        assertThat(resolveHost("acme.192.168.0.10").isMatched()).as("옛 domain 은 더 이상 매칭 안 됨").isFalse();
        assertThat(clientDomainService.getDomain(acme)).isEqualTo("https://acme.drive.example.com");
    }

    @Test
    @DisplayName("베이스에 묶이지 않은 테넌트(일반 설정으로 넣은 domain)는 베이스 스킴·포트를 쓰지 않는다")
    void unboundTenant() {
        saveBase("http://drive.example.com:8080");
        jdbc.update("UPDATE tb_tenant SET domain = 'legacy.example.org' WHERE tenant_seq = 1");

        assertThat(clientDomainService.getDomain(1)).as("요청 없음 → https, 포트 생략").isEqualTo("https://legacy.example.org");
    }
}
