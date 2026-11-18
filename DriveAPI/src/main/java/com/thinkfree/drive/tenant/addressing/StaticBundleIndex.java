package com.thinkfree.drive.tenant.addressing;

import com.thinkfree.drive.util.RequestPathUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 프론트 정적 번들({@code classpath:/static}) 의 <b>최상위 세그먼트 집합</b>. 기동 시 한 번 스캔해 불변으로 보관한다.
 * <p>
 * Next 정적 export 는 자산 URL 을 절대 경로({@code /_next/…}, {@code /common/…}, {@code /favicon.ico})로 굽는다.
 * PATH 테넌트 모드에서 페이지가 {@code /t/acme/drive} 로 열려도 브라우저는 origin 기준 {@code /_next/…} 로 요청하므로
 * 슬러그가 없고, 정적 핸들러({@code StaticResourceConfig})는 테넌트를 읽지 않는다. 따라서 이 집합에 속한 첫 세그먼트의 요청은
 * {@code TenantFilter} 가 판별 자체를 건너뛴다(fallback 없는 다중 테넌트 배포에서 정적 파일이 404 JSON 으로 막히는 것을 방지).
 * <ul>
 *     <li>목록을 손으로 적지 않는다 — 실제 번들에 존재하는 이름만 수집(임의 경로 우회 없음)</li>
 *     <li>{@code classpath*:/static/**} 로 전체를 훑어 첫 세그먼트만 취한다 — jar 에 디렉터리 엔트리가 없어도 빠지지 않게</li>
 *     <li>{@code drive.html} 은 확장자 생략 라우팅({@code /drive})을 위해 {@code drive} 도 함께</li>
 *     <li>번들이 없는 서버({@code frontend.skip}, 프론트 분리 배포)나 스캔 실패는 빈 집합 → 기존 동작과 동일</li>
 *     <li>최상위에 테넌트 마커 {@link TenantPathMarker#SEGMENT} 가 있으면 기동 시 warn — 루트 라우트에 {@code t} 를 만들지 않는다는 규칙 가드</li>
 * </ul>
 */
@Slf4j
@Component
public class StaticBundleIndex {

    private static final String STATIC_ROOT = "classpath*:/static/";
    private static final String STATIC_ALL = "classpath*:/static/**";
    private static final String HTML_SUFFIX = ".html";
    private static final String STATIC_MARKER = "/static/";

    private final Set<String> rootSegments;

    public StaticBundleIndex(ResourceLoader resourceLoader) {
        this.rootSegments = Collections.unmodifiableSet(scan(ResourcePatternUtils.getResourcePatternResolver(resourceLoader)));
        log.info("[StaticBundleIndex] static bundle root segments : {} {}", rootSegments.size(), rootSegments);
        if (rootSegments.contains(TenantPathMarker.SEGMENT)) {
            log.warn("[StaticBundleIndex] 정적 번들 최상위에 테넌트 경로 마커 '{}' 가 있다 — PATH 모드 /{}/{slug} 와 충돌하므로 루트 라우트/파일명을 바꿔야 한다",
                    TenantPathMarker.SEGMENT, TenantPathMarker.SEGMENT);
        }
    }

    /** 경로 첫 세그먼트가 정적 번들 최상위 항목(디렉터리·파일·{@code *.html} 의 확장자 생략형)인지. */
    public boolean isRootSegment(String segment) {
        return StringUtils.hasText(segment) && rootSegments.contains(segment);
    }

    private static Set<String> scan(ResourcePatternResolver resolver) {
        Set<String> segments = new LinkedHashSet<>();
        try {
            List<String> roots = rootUrls(resolver);
            for (Resource resource : resolver.getResources(STATIC_ALL)) {
                String relative = relativize(resource.getURL().toString(), roots);
                String first = UriUtils.decode(RequestPathUtil.firstSegment(relative), StandardCharsets.UTF_8);
                if (!StringUtils.hasText(first)) {
                    continue;
                }
                segments.add(first);
                if (first.endsWith(HTML_SUFFIX)) {
                    segments.add(first.substring(0, first.length() - HTML_SUFFIX.length()));
                }
            }
        } catch (IOException | RuntimeException e) {
            log.warn("[StaticBundleIndex] static bundle scan failed — empty index (tenant filter skips nothing)", e);
            segments.clear();
        }
        return segments;
    }

    /** 스캔 루트({@code static/} 디렉터리) URL 문자열들. 파일시스템·jar 어느 쪽이든 하위 리소스 URL 의 접두가 된다. */
    private static List<String> rootUrls(ResourcePatternResolver resolver) throws IOException {
        List<String> roots = new ArrayList<>();
        for (Resource root : resolver.getResources(STATIC_ROOT)) {
            String url = root.getURL().toString();
            roots.add(url.endsWith("/") ? url : url + "/");
        }
        return roots;
    }

    /**
     * 리소스 URL 에서 {@code static/} 이하 상대 경로. 루트 접두가 맞으면 그것을 떼고,
     * 아니면(루트 URL 형식 불일치) jar 구분자({@code !/}) 뒤의 첫 {@code /static/} 이후를 취한다.
     */
    private static String relativize(String url, List<String> roots) {
        for (String root : roots) {
            if (url.startsWith(root)) {
                return url.substring(root.length());
            }
        }
        int bang = url.lastIndexOf("!/");
        String tail = bang < 0 ? url : url.substring(bang + 1);
        int idx = tail.indexOf(STATIC_MARKER);
        return idx < 0 ? "" : tail.substring(idx + STATIC_MARKER.length());
    }
}
