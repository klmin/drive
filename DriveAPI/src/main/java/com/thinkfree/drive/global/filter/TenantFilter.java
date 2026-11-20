package com.thinkfree.drive.global.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfree.drive.util.LogSafe;
import com.thinkfree.drive.global.FilterResponseWriter;
import com.thinkfree.drive.global.security.SecurityPaths;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.tenant.addressing.StaticBundleIndex;
import com.thinkfree.drive.tenant.addressing.TenantAddressingStrategy;
import com.thinkfree.drive.tenant.addressing.TenantMatch;
import com.thinkfree.drive.tenant.exception.TenantErrorCode;
import com.thinkfree.drive.tenant.resolver.TenantSeqResolver;
import com.thinkfree.drive.util.RequestPathUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.micrometer.common.KeyValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.filter.ServerHttpObservationFilter;

import java.io.IOException;

/**
 * 테넌트 판별 필터(모드 무관, 단일). 모드별 차이는 {@link TenantAddressingStrategy} 구현 빈이 맡는다.
 * <ol>
 *     <li>{@link SecurityPaths#isSystemPublic}, 루트({@code /}), 정적 번들 최상위 세그먼트({@link StaticBundleIndex}),
 *         슈퍼어드민 전용 API({@link SecurityPaths#isSuperAdminApi}) → 필터 스킵(TenantContext 없이 진행)
 *         (정적 자산은 절대 경로로 요청되어 슬러그가 없고 정적 핸들러는 테넌트를 읽지 않는다. 슈퍼어드민은 테넌트 소속이 아니고
 *         usecase 가 TenantContext 를 읽지 않는다. 접두 붙은 {@code /t/acme/_next/…}·{@code /t/acme/api/v1/super-admin/…} 는
 *         첫 세그먼트가 마커 {@code t} 라 스킵되지 않고 2번으로 진행 — 그 테넌트가 set 되지만 무해)</li>
 *     <li>전략 매치 MATCHED(존재+ACTIVE) → {@link TenantContext} set + 전략이 돌려준 요청(PATH 모드는 contextPath 래퍼)으로 체인 진행</li>
 *     <li>전략 매치 INACTIVE(존재+비활성)·NOT_FOUND(PATH 모드 마커 뒤 슬러그가 비었거나 형식 불일치) → fallback 없이 404</li>
 *     <li>NONE(마커 없음·Host 미매치·미등록 슬러그) → {@link TenantSeqResolver#resolveFallback()}
 *         (활성 SELF_MANAGED 1개 → 그 테넌트, dev fallback) → 전략이 준 요청(PATH 모드 미등록 슬러그는 접두를 뗀 래퍼)
 *         또는 원본으로 진행. 양 모드 공통이며 HEAD(Host 단일 판별) 시절의 "미등록 → fallback" 과 같은 규칙</li>
 *     <li>fallback 도 없음 → 테넌트 선택 경로({@link SecurityPaths#isTenantOptional}: 브랜딩·로그아웃·토큰 재발급)면
 *         TenantContext 없이 같은 요청으로 진행(다중 테넌트 배포의 슈퍼어드민 포털이 접두 없이 부르는 공유 경로),
 *         그 외 → 404 {@link TenantErrorCode#TENANT_HOST_NOT_FOUND}</li>
 * </ol>
 * 404 중 화면 요청(GET + Accept text/html)은 JSON 대신 {@code /error-page?reason=tenant} 로 리다이렉트한다.
 * {@code .well-known} 은 예외가 아니다 — 주소(접두·Host·RFC 삽입형 {@code /.well-known/…/t/{slug}})로 매치되면
 * 그 테넌트, 아니면 fallback 테넌트를 달고 진행한다.
 * <p>
 * 필터 순서: CorsFilter(HIGHEST_PRECEDENCE) 다음, Spring Security 체인 앞. 등록은 {@link GlobalFilterConfig}.
 */
@Slf4j
@RequiredArgsConstructor
public class TenantFilter extends OncePerRequestFilter {

    /** 테넌트 오류 에러 페이지 (DriveFront ROUTES.ERROR_PAGE_TENANT — '이전 페이지로 이동' 버튼 미표시) */
    private static final String TENANT_ERROR_PAGE = "/error-page?reason=tenant";

    private final TenantAddressingStrategy addressingStrategy;
    private final TenantSeqResolver tenantSeqResolver;
    private final ObjectMapper objectMapper;
    private final StaticBundleIndex staticBundleIndex;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = RequestPathUtil.pathWithinApplication(request);
        return SecurityPaths.isSystemPublic(path)
                || "/".equals(path)
                || staticBundleIndex.isRootSegment(RequestPathUtil.firstSegment(path))
                || SecurityPaths.isSuperAdminApi(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        TenantMatch match = addressingStrategy.resolve(request);
        
        if (match.isMatched()) {
            if (redirectBareTenantRoot(request, match.requestToForward(), response)) {
                return;
            }
            runWithTenant(match.tenantSeq(), match.requestToForward(), response, filterChain);
            return;
        }

        if (match.isInactive() || match.isNotFound()) {
            log.error("[TenantFilter] tenant {} — host : {}, path : {}",
                    match.isInactive() ? "inactive" : "path marker without a valid slug",
                    LogSafe.of(request.getHeader("Host")), LogSafe.of(RequestPathUtil.pathWithinApplication(request)));
            writeTenantNotFound(request, response);
            return;
        }

        HttpServletRequest forward = match.requestToForwardOr(request);
        Integer fallbackSeq = tenantSeqResolver.resolveFallback();
        
        if (fallbackSeq == null) {
            // 테넌트 선택 경로(브랜딩·로그아웃·재발급)는 접두를 뗀 뒤의 경로로 판정 — 슈퍼어드민 포털이 접두 없이 부르는 공유 경로라
            // 주소로 테넌트를 특정할 수 없는 다중 테넌트 배포에서도 TenantContext 없이 진행한다(usecase 는 null → 기본값/미참조).
            if (SecurityPaths.isTenantOptional(RequestPathUtil.pathWithinApplication(forward))) {
                log.debug("[TenantFilter] tenant-optional path without tenant — host : {}, path : {}",
                        LogSafe.of(request.getHeader("Host")), LogSafe.of(RequestPathUtil.pathWithinApplication(request)));
                runWithTenant(null, forward, response, filterChain);
                return;
            }
            log.error("[TenantFilter] tenant not found — host : {}, path : {}",
                    LogSafe.of(request.getHeader("Host")), LogSafe.of(RequestPathUtil.pathWithinApplication(request)));
            writeTenantNotFound(request, response);
            return;
        }
        if (redirectBareTenantRoot(request, forward, response)) {
            return;
        }
        runWithTenant(fallbackSeq, forward, response, filterChain);
    }

    /**
     * PATH 모드에서 접두만 있고 끝슬래시가 없는 {@code /t/acme}(GET·HEAD)는 {@code /t/acme/} 로 302.
     * 래퍼의 애플리케이션 내부 경로가 빈 문자열이 되어 정적 인덱스(루트 페이지)에 매핑되지 않기 때문 — 서블릿 컨테이너가
     * contextPath 만 있는 요청을 슬래시로 리다이렉트하는 관례와 같다. 쿼리스트링은 유지. SUBDOMAIN 모드(원본 요청)는 해당 없음.
     */
    private static boolean redirectBareTenantRoot(HttpServletRequest original, HttpServletRequest forward,
                                                  HttpServletResponse response) {
        if (!(forward instanceof TenantPathRequestWrapper) || !RequestPathUtil.pathWithinApplication(forward).isEmpty()) {
            return false;
        }
        String method = original.getMethod();
        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            return false;
        }
        String query = original.getQueryString();
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader("Location", original.getRequestURI() + "/" + (query == null ? "" : "?" + query));
        return true;
    }

    /** 테넌트 판별 실패 — 화면 요청은 테넌트 오류 에러 페이지로 리다이렉트, 그 외는 404 TENANT_004 JSON */
    private void writeTenantNotFound(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (isPageRequest(request)) {
            response.sendRedirect(request.getContextPath() + TENANT_ERROR_PAGE);
            return;
        }
        FilterResponseWriter.write(response, TenantErrorCode.TENANT_HOST_NOT_FOUND, objectMapper);
    }

    /** 브라우저 화면 이동 요청(GET + Accept text/html) 여부 */
    private static boolean isPageRequest(HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return "GET".equals(request.getMethod()) && accept != null && accept.contains(MediaType.TEXT_HTML_VALUE);
    }

    /**
     * 요청 트레이스 span 속성 — Tempo 에서 {@code { span.tenant.seq = 3 }} 로 테넌트별로 좁힌다.
     * 높은 카디널리티 값이라 메트릭 태그가 아니라 span 에만 남는다(http_server_requests 시계열은 늘지 않는다).
     */
    static final String TENANT_SPAN_KEY = "tenant.seq";

    /** 체인 진행의 단일 경로 —{@code tenantSeq} 가 null(테넌트 선택 경로의 tenant-less 진행)이어도 같은 try/finally 로 clear 한다. */
    private static void runWithTenant(Integer tenantSeq, HttpServletRequest request, HttpServletResponse response,
                                      FilterChain filterChain) throws ServletException, IOException {
        try {
            if (tenantSeq != null) {
                TenantContext.set(tenantSeq);
                // 지금 넣는다 — HTTP 관측(이 필터보다 앞)은 TenantContext 를 지운 뒤에 닫히므로 그때 읽으면 비어 있다
                ServerHttpObservationFilter.findObservationContext(request)
                        .ifPresent(context -> context.addHighCardinalityKeyValue(KeyValue.of(TENANT_SPAN_KEY, String.valueOf(tenantSeq))));
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
