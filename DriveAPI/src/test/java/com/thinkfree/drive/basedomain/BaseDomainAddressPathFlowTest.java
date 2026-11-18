package com.thinkfree.drive.basedomain;

import com.thinkfree.drive.api.v1.superadmin.basedomain.request.SuperAdminBaseDomainUpsertRequest;
import com.thinkfree.drive.api.v1.superadmin.basedomain.response.SuperAdminBaseDomainResponse;
import com.thinkfree.drive.api.v1.superadmin.basedomain.usecase.SuperAdminBaseDomainUseCase;
import com.thinkfree.drive.basedomain.exception.BaseDomainErrorCode;
import com.thinkfree.drive.common.ServiceTest;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.global.service.ClientDomainService;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * PATH 모드(기본) 통합 흐름 — 슈퍼어드민 베이스 도메인 저장(파싱·모드 검증·분해 저장) → 묶인 테넌트 domain 재계산 →
 * 아웃바운드 origin(ClientDomainService) → 인바운드 판별. PostgreSQL Testcontainers, 트랜잭션 롤백.
 * 초기 상태는 V1 시드 그대로(베이스 없음, 기본 테넌트 1 은 domain NULL).
 */
@DisplayName("베이스 도메인 주소 흐름 — PATH 모드")
class BaseDomainAddressPathFlowTest extends ServiceTest {

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

    /** 슈퍼어드민 테넌트 생성이 남기는 모양(slug + slug.베이스호스트 + base_domain_seq 1) 그대로 넣는다. */
    private int insertBoundTenant(String slug, String baseHost) {
        Integer seq = jdbc.queryForObject("""
                INSERT INTO tb_tenant (tenant_name, tenant_type, status, login_type, domain, subdomain, base_domain_seq, register_user_seq)
                VALUES (?, 'SYSTEM_MANAGED', 'ACTIVE', 'LOCAL', ?, ?, 1, 1) RETURNING tenant_seq""",
                Integer.class, "flow-" + slug, slug + "." + baseHost, slug);
        return seq == null ? -1 : seq;
    }

    private Map<String, Object> tenantRow(int tenantSeq) {
        return jdbc.queryForMap("SELECT domain, subdomain, base_domain_seq FROM tb_tenant WHERE tenant_seq = ?", tenantSeq);
    }

    private static void currentRequest(String scheme, int port) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme(scheme);
        request.setServerPort(port);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    @DisplayName("초기 설치 → IP 베이스(스킴·포트) → 도메인 베이스로 교체: 저장 분해·재계산·아웃바운드가 입력값을 따른다")
    void ipThenDomain() {
        // 첫 등록 — 기본 테넌트(domain NULL)는 묶을 대상이 아니다
        saveBase("http://192.168.0.10:8080");
        SuperAdminBaseDomainResponse base = baseDomainUseCase.get();
        assertThat(base.baseDomain()).isEqualTo("192.168.0.10");
        assertThat(base.scheme()).isEqualTo("http");
        assertThat(base.port()).isEqualTo(8080);
        assertThat(base.address()).isEqualTo("http://192.168.0.10:8080");
        assertThat(tenantRow(1).get("domain")).isNull();

        int acme = insertBoundTenant("acme", "192.168.0.10");

        // 요청 없음(비동기)이어도 베이스 스킴·포트 그대로, 요청이 https 여도 그대로
        assertThat(clientDomainService.getDomain(acme)).isEqualTo("http://192.168.0.10:8080/t/acme");
        currentRequest("https", 443);
        assertThat(clientDomainService.getDomain(acme)).isEqualTo("http://192.168.0.10:8080/t/acme");
        assertThat(clientDomainService.getDomain(1)).as("루트 테넌트는 경로 없음").isEqualTo("http://192.168.0.10:8080");
        RequestContextHolder.resetRequestAttributes();

        // 도메인 베이스로 교체 — slug 유지, domain 재계산
        saveBase("https://drive.example.com:8443");
        Map<String, Object> row = tenantRow(acme);
        assertThat(row.get("subdomain")).isEqualTo("acme");
        assertThat(row.get("domain")).isEqualTo("acme.drive.example.com");
        assertThat(clientDomainService.getDomain(acme)).isEqualTo("https://drive.example.com:8443/t/acme");
    }

    @Test
    @DisplayName("스킴·포트를 지우고 다시 저장하면 NULL 로 덮이고, 아웃바운드는 요청 스킴(요청 없으면 https)·포트 생략")
    void clearSchemeAndPort() {
        saveBase("http://drive.example.com:8080");
        int acme = insertBoundTenant("acme", "drive.example.com");

        saveBase("drive.example.com");
        SuperAdminBaseDomainResponse base = baseDomainUseCase.get();
        assertThat(base.scheme()).isNull();
        assertThat(base.port()).isNull();
        assertThat(base.address()).isEqualTo("drive.example.com");

        assertThat(clientDomainService.getDomain(acme)).as("요청 없음 → https").isEqualTo("https://drive.example.com/t/acme");
        currentRequest("http", 8080);
        assertThat(clientDomainService.getDomain(acme)).as("요청 스킴, default 모드는 포트 생략").isEqualTo("http://drive.example.com/t/acme");
    }

    @Test
    @DisplayName("포트만 입력 — 스킴은 요청(없으면 https), 포트는 입력값 / 스킴만 입력 — 포트 생략")
    void partialInput() {
        saveBase("localhost:3000");
        int acme = insertBoundTenant("acme", "localhost");

        assertThat(clientDomainService.getDomain(acme)).isEqualTo("https://localhost:3000/t/acme");
        currentRequest("http", 3000);
        assertThat(clientDomainService.getDomain(acme)).isEqualTo("http://localhost:3000/t/acme");
        RequestContextHolder.resetRequestAttributes();

        saveBase("https://drive.example.com");
        assertThat(clientDomainService.getDomain(acme)).isEqualTo("https://drive.example.com/t/acme");
    }

    @Test
    @DisplayName("기본 포트는 저장하지 않고(https:443), 짝이 다른 조합(http:443)은 그대로 둔다")
    void defaultPort() {
        saveBase("HTTPS://Drive.Example.com:443/");
        SuperAdminBaseDomainResponse base = baseDomainUseCase.get();
        assertThat(base.address()).isEqualTo("https://drive.example.com");
        assertThat(base.port()).isNull();

        saveBase("http://drive.example.com:443");
        assertThat(baseDomainUseCase.get().address()).isEqualTo("http://drive.example.com:443");
    }

    @Test
    @DisplayName("스킴 없이 80·443 을 넣으면 스킴을 추론해 sync·async 모두 열리는 주소가 나간다")
    void wellKnownPortWithoutScheme() {
        saveBase("drive.example.com:80");
        SuperAdminBaseDomainResponse base = baseDomainUseCase.get();
        assertThat(base.scheme()).isEqualTo("http");
        assertThat(base.port()).isNull();
        assertThat(base.address()).isEqualTo("http://drive.example.com");
        int acme = insertBoundTenant("acme", "drive.example.com");

        assertThat(clientDomainService.getDomain(acme)).as("요청 없음(async)도 http").isEqualTo("http://drive.example.com/t/acme");
        currentRequest("https", 443);
        assertThat(clientDomainService.getDomain(acme)).as("https 요청이어도 http").isEqualTo("http://drive.example.com/t/acme");
        RequestContextHolder.resetRequestAttributes();

        saveBase("drive.example.com:443");
        assertThat(baseDomainUseCase.get().address()).isEqualTo("https://drive.example.com");
        currentRequest("http", 80);
        assertThat(clientDomainService.getDomain(acme)).as("http 요청이어도 https").isEqualTo("https://drive.example.com/t/acme");
    }

    @Test
    @DisplayName("인바운드는 베이스 스킴·포트와 무관 — /t/slug 로 판별")
    void inbound() {
        saveBase("https://drive.example.com:8443");
        int acme = insertBoundTenant("acme", "drive.example.com");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/t/acme/api/v1/users/me");
        request.addHeader("Host", "drive.example.com:8443");
        TenantMatch match = addressingStrategy.resolve(request);
        assertThat(match.isMatched()).isTrue();
        assertThat(match.tenantSeq()).isEqualTo(acme);
    }

    @ParameterizedTest(name = "거부: {0}")
    @ValueSource(strings = {"ftp://drive.example.com", "drive.example.com:0", "drive.example.com:65536",
            "https://drive.example.com/app", "[::1]:8080", "intranet", "user@drive.example.com", "256.1.1.1"})
    void rejects(String input) {
        assertThatThrownBy(() -> baseDomainUseCase.upsert(new SuperAdminBaseDomainUpsertRequest(input)))
                .isInstanceOf(ApiRuntimeException.class)
                .satisfies(e -> assertThat(((ApiRuntimeException) e).getCode()).isEqualTo(BaseDomainErrorCode.INVALID_BASE_DOMAIN));
        assertThat(baseDomainUseCase.get().baseDomain()).as("저장 없음").isNull();
    }

    @Test
    @DisplayName("SUBDOMAIN 모드에서 쓰던 데이터(slug + slug.도메인) — PATH 로 바꿔도 재저장 없이 /t/slug 판별")
    void fromSubdomainMode() {
        saveBase("drive.example.com");
        int acme = insertBoundTenant("acme", "drive.example.com");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/t/acme/api/v1/users/me");
        TenantMatch match = addressingStrategy.resolve(request);
        assertThat(match.tenantSeq()).isEqualTo(acme);
        assertThat(clientDomainService.getDomain(acme)).isEqualTo("https://drive.example.com/t/acme");
    }
}
