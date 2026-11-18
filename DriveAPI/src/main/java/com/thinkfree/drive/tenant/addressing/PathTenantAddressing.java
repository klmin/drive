package com.thinkfree.drive.tenant.addressing;

import com.thinkfree.drive.app.properties.AppFrontProperty;
import com.thinkfree.drive.basedomain.data.BaseDomainData;
import com.thinkfree.drive.basedomain.service.BaseDomainFindService;
import com.thinkfree.drive.common.validation.DomainComposer;
import com.thinkfree.drive.common.validation.DomainValidator;
import com.thinkfree.drive.global.filter.TenantPathRequestWrapper;
import com.thinkfree.drive.tenant.data.TenantData;
import com.thinkfree.drive.tenant.resolver.TenantSeqResolver;
import com.thinkfree.drive.tenant.resolver.TenantSeqResolver.Lookup;
import com.thinkfree.drive.util.RequestPathUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;


/**
 * PATH 모드({@code tenant.resolution-mode: PATH}, 기본 — 미지정 시 이 전략). URL 문법 = {@code /t/{slug}/…} ({@link TenantPathMarker}).
 * <p>
 * 인바운드:
 * <ul>
 *     <li>첫 세그먼트가 마커 {@code t} — 둘째 세그먼트를 슬러그로 보고 {@code TB_TENANT.SUBDOMAIN} 을 <b>직접</b> 조회한다
 *         (Host·베이스 도메인은 판별에 쓰지 않는다. SUBDOMAIN 모드가 Host = DOMAIN 으로 찾는 것과 대칭).
 *         ACTIVE → MATCHED + {@link TenantPathRequestWrapper}({@code /t/slug} 를 contextPath 처럼, 하위 시큐리티·MVC·SAS 는 접두 없는 경로를 본다).
 *         INACTIVE → INACTIVE(404). <b>미등록 슬러그 → NONE + 접두를 뗀 래퍼</b> — SUBDOMAIN 모드의 미등록 Host 와 같이 fallback
 *         (활성 SELF_MANAGED 1개 / dev fallback)을 탄다. 슬러그가 비었거나 형식 불일치({@code /t/}, {@code /t/UPPER})면 접두를
 *         만들 수 없어 NOT_FOUND(404).</li>
 *     <li>RFC 8414/9728 <b>경로 삽입형</b> {@code /.well-known/{doc}/t/{slug}[/…]} — OAuth 클라이언트가 issuer
 *         {@code https://host/t/slug} 의 AS 메타데이터를 조회하는 표준 URL(MCP SDK 1순위). 슬러그를 넷째 세그먼트에서 읽어
 *         같은 규칙으로 조회하되 요청은 <b>원본 그대로</b> 넘긴다(SAS 가 requestURI 로 issuer 를 계산). 미등록·형식 불일치는 NONE(원본).</li>
 *     <li>그 외(마커 없음) — 조회 없이 NONE(원본). 루트 요청·슈퍼어드민 API 는 fallback 테넌트를 달고 진행한다.</li>
 * </ul>
 * 아웃바운드: {@code scheme://publicHost[:port]/t/slug}. publicHost = 베이스 도메인, 미설정이면 현재 요청 host,
 * 그것도 없으면 {@code app.front.base-url} 의 host. 슬러그 없는 루트 테넌트는 경로를 붙이지 않는다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "tenant", name = "resolution-mode", havingValue = "PATH", matchIfMissing = true)
public class PathTenantAddressing implements TenantAddressingStrategy {

    private final TenantSeqResolver tenantSeqResolver;
    private final BaseDomainFindService baseDomainFindService;
    private final AppFrontProperty appFrontProperty;

    @Override
    public TenantMatch resolve(HttpServletRequest request) {
        String uri = RequestPathUtil.pathWithinApplication(request);
        String slug = TenantPathMarker.slugOf(uri);
        if (slug != null) {
            return resolveMarker(request, slug);
        }
        String insertedSlug = TenantPathMarker.slugOfWellKnownInsertion(uri);
        if (insertedSlug != null) {
            return resolveWellKnownInsertion(request, insertedSlug);
        }
        return TenantMatch.none();
    }

    /** {@code /t/{slug}/…} — 매치·fallback 모두 접두를 뗀 래퍼로 진행. */
    private TenantMatch resolveMarker(HttpServletRequest request, String slug) {
        String prefix = TenantPathMarker.prefixOf(slug);
        if (!DomainComposer.isValidSubdomain(slug) || !rawUriStartsWith(request, prefix)) {
            return TenantMatch.notFound();
        }
        Lookup lookup = tenantSeqResolver.lookupBySubdomain(slug);
        HttpServletRequest stripped = new TenantPathRequestWrapper(request, prefix);
        return switch (lookup.status()) {
            case ACTIVE -> TenantMatch.matched(lookup.tenantSeq(), stripped);
            case INACTIVE -> TenantMatch.inactive();
            case NOT_FOUND -> TenantMatch.none(stripped);
        };
    }

    /**
     * 래퍼 contextPath({@code 원본 contextPath + /t/slug})는 <b>디코딩 전</b> requestURI 의 앞부분과 글자 그대로 일치해야 한다 —
     * Spring {@code RequestPath.parse(requestURI, contextPath)} 가 불일치면 예외(500). 슬러그가 퍼센트 인코딩되어 온 경우
     * ({@code /t/%61cme/x} → 디코딩 슬러그 {@code acme}) 가 그 예라 매치 대상에서 제외한다.
     */
    private static boolean rawUriStartsWith(HttpServletRequest request, String prefix) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }
        String contextPath = request.getContextPath();
        String expected = (contextPath == null ? "" : contextPath) + prefix;
        return uri.equals(expected) || uri.startsWith(expected + "/");
    }

    /** {@code /.well-known/{doc}/t/{slug}[/…]} — 요청은 원본 그대로(SAS issuer 계산이 requestURI 기준). */
    private TenantMatch resolveWellKnownInsertion(HttpServletRequest request, String slug) {
        if (!DomainComposer.isValidSubdomain(slug)) {
            return TenantMatch.none();
        }
        Lookup lookup = tenantSeqResolver.lookupBySubdomain(slug);
        return switch (lookup.status()) {
            case ACTIVE -> TenantMatch.matched(lookup.tenantSeq(), request);
            case INACTIVE -> TenantMatch.inactive();
            case NOT_FOUND -> TenantMatch.none();
        };
    }

    /** 베이스 도메인이 있으면 그 호스트 + 베이스에 입력한 스킴·포트({@link OriginComposer#composeOnBase}), 없으면 폴백 host + 호출부 값. */
    @Override
    public String origin(TenantData tenant, String scheme, Integer port) {
        BaseDomainData base = baseDomainOrNull();
        String origin = base != null
                ? OriginComposer.composeOnBase(base, base.getBaseDomain(), scheme, port)
                : OriginComposer.compose(scheme, publicHost(), port);
        return StringUtils.hasText(tenant.getSubdomain()) ? origin + TenantPathMarker.prefixOf(tenant.getSubdomain()) : origin;
    }

    /** PATH 모드의 베이스 도메인 호스트는 서브도메인을 붙이지 않는 공개 호스트 그 자체라 도메인·localhost·IPv4(DNS 없는 온프레미스) 모두 받는다. */
    @Override
    public boolean isAcceptableBaseDomain(String host) {
        return DomainValidator.isValidDomainOrIpv4(host) || DomainValidator.isLocalhost(host);
    }

    /**
     * PATH 모드의 {@code tenant.domain} 은 등록 시 {@code subdomain.baseDomain} 으로 합성해 저장만 하는 값(판별은 SUBDOMAIN 컬럼,
     * 아웃바운드는 베이스 도메인). 베이스가 IPv4 면 {@code acme.192.168.0.10} 이 되므로 그 형식과 순수 IPv4(루트 테넌트)도 허용.
     */
    @Override
    public boolean isAcceptableTenantDomain(String domain) {
        return DomainValidator.isValidDomainOrIpv4(domain) || DomainValidator.isValidLabelsOnIpv4(domain)
                || DomainValidator.isLocalhost(domain);
    }

    /** 아웃바운드 origin 전용 — 인바운드 판별에는 쓰지 않는다. */
    private BaseDomainData baseDomainOrNull() {
        BaseDomainData baseDomain = baseDomainFindService.findCurrent();
        return (baseDomain == null || !StringUtils.hasText(baseDomain.getBaseDomain())) ? null : baseDomain;
    }

    /** 베이스 도메인이 없을 때의 아웃바운드 host: 현재 요청 host → app.front.base-url host. 둘 다 없으면 베이스 도메인 미설정 예외. */
    private String publicHost() {
        String fallback = PublicHostFallback.requestHostOrConfigured(appFrontProperty);
        if (fallback != null) {
            return fallback;
        }
        return baseDomainFindService.findCurrentOrThrow().getBaseDomain();
    }
}
