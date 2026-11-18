package com.thinkfree.drive.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.util.DigestUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 프론트 정적 export(classpath:/static) 서빙과 브라우저 캐시 정책.
 * <ul>
 *   <li>{@code /_next/static/**}, {@code /intellect/_next/static/**} — 파일명에 콘텐츠 해시 또는 빌드 ID 가 붙어 내용이 바뀌면 URL 도 바뀐다.
 *       1년 immutable 로 재검증도 생략한다.</li>
 *   <li>그 외(HTML·public 이미지 등) — URL 이 고정이라 no-cache 로 매번 재검증한다. 재검증 기준은 파일 내용 해시 ETag 다.
 *       Last-Modified 만으로는 "서버 파일이 더 옛 시각"인 롤백을 304 로 판정해 새 HTML 이 남고, 롤백된 서버에 없는 chunk 를 불러 ChunkLoadError 가 난다.
 *       ETag 는 같음/다름만 보므로 롤백·재빌드 모두 정확하다(If-None-Match 가 있으면 Spring 이 If-Modified-Since 보다 우선 비교).
 *       캐시 헤더가 아예 없으면 브라우저가 Last-Modified 휴리스틱으로 옛 HTML 을 재검증 없이 쓴다.</li>
 * </ul>
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    private static final Duration HASHED_ASSET_MAX_AGE = Duration.ofDays(365);

    /** 리소스 URL·수정 시각·크기 → 내용 해시. 같은 파일은 한 번만 해시하고, 파일이 바뀌면(개발 중 재복사 등) 키가 달라져 다시 계산한다. */
    private final Map<String, String> contentEtags = new ConcurrentHashMap<>();

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/_next/static/**")
                .addResourceLocations("classpath:/static/_next/static/")
                .setCacheControl(CacheControl.maxAge(HASHED_ASSET_MAX_AGE).cachePublic().immutable());

        registry.addResourceHandler("/intellect/_next/static/**")
                .addResourceLocations("classpath:/static/intellect/_next/static/")
                .setCacheControl(CacheControl.maxAge(HASHED_ASSET_MAX_AGE).cachePublic().immutable());

        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache())
                .setEtagGenerator(this::contentEtag)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {

                        Resource exact = location.createRelative(resourcePath);

                        if (exact.exists() && exact.isReadable()){
                            return exact;
                        }

                        if (!resourcePath.contains(".")) {
                            String base = resourcePath.endsWith("/") ? resourcePath : resourcePath + "/";
                            Resource indexHtml = location.createRelative(base + "index.html");
                            if (indexHtml.exists() && indexHtml.isReadable()){
                                return indexHtml;
                            }

                            Resource pageHtml = location.createRelative(resourcePath + ".html");
                            if (pageHtml.exists() && pageHtml.isReadable()){
                                return pageHtml;
                            }
                        }

                        return null;
                    }
                });
    }

    /**
     * 파일 내용 MD5(보안 용도 아님, 버전 식별만). 읽기 실패 시 null → ETag 없이 Last-Modified 로만 재검증.
     * 해시는 맵 잠금 밖에서 계산한다(computeIfAbsent 안 I/O 회피). 기동 직후 동시 요청이면 두 번 계산될 수 있으나 결과가 같아 무해하다.
     */
    private String contentEtag(Resource resource) {
        try {
            String key = resource.getURL() + "|" + resource.lastModified() + "|" + resource.contentLength();
            String cached = contentEtags.get(key);
            if (cached != null) {
                return cached;
            }
            String etag;
            try (InputStream in = resource.getInputStream()) {
                etag = DigestUtils.md5DigestAsHex(in);
            }
            contentEtags.putIfAbsent(key, etag);
            return etag;
        } catch (IOException e) {
            return null;
        }
    }
}
