package com.thinkfree.drive.mcp.util;

import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.mcp.constants.McpConstants;
import com.thinkfree.drive.resource.enums.ResourceType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

/**
 * MCP RS 관련 URL 구성 유틸.
 *
 * 온프레미스 설치형 + 테넌트별 서브도메인이라 서버 주소를 고정 설정으로 둘 수 없어,
 * 요청이 들어온 스킴/호스트 기준으로 절대 URL을 만든다.
 * 프록시 뒤 배포를 고려해 X-Forwarded-Host/Proto를 우선 사용 (TenantFilter의 호스트 해석과 동일 기준).
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class McpUrls {

    /**
     * 요청 기준 서버 베이스 URL (기본 포트는 생략) + contextPath.
     * PATH 테넌트 모드에서는 contextPath 가 {@code /t/acme} 로 에뮬레이션되므로 {@code https://host/t/acme} 가 되어
     * SAS 가 requestURI 로 계산하는 issuer 와 일치한다 (SUBDOMAIN 모드는 contextPath 가 비어 기존과 동일).
     */
    public static String baseUrl(HttpServletRequest request) {
        return authority(request) + contextPath(request);
    }

    private static String authority(HttpServletRequest request) {
        String forwardedHost = firstValue(request.getHeader("X-Forwarded-Host"));
        if (StringUtils.hasText(forwardedHost)) {
            String forwardedProto = firstValue(request.getHeader("X-Forwarded-Proto"));
            String scheme = StringUtils.hasText(forwardedProto) ? forwardedProto : request.getScheme();
            // X-Forwarded-Host는 포트가 필요하면 이미 포함하고 있다
            return scheme + "://" + forwardedHost;
        }

        String scheme = request.getScheme();
        int port = request.getServerPort();

        boolean defaultPort = (DriveConstants.HTTP_SCHEME.equals(scheme) && port == 80) || (DriveConstants.HTTPS_SCHEME.equals(scheme) && port == 443);

        StringBuilder url = new StringBuilder(scheme).append("://").append(request.getServerName());
        if (!defaultPort) {
            url.append(':').append(port);
        }
        return url.toString();
    }

    private static String contextPath(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        return StringUtils.hasText(contextPath) ? contextPath : "";
    }

    /** "a.com, b.com"처럼 여러 값이 올 수 있는 forwarded 헤더에서 첫 값만 취한다. */
    private static String firstValue(String headerValue) {
        if (!StringUtils.hasText(headerValue)) {
            return null;
        }
        int comma = headerValue.indexOf(',');
        return (comma >= 0 ? headerValue.substring(0, comma) : headerValue).trim();
    }

    /** WWW-Authenticate에 광고할 메타데이터 URL — RFC 9728 정식 형태인 경로 삽입형(/mcp 접미사) 사용 */
    public static String protectedResourceMetadataUrl(HttpServletRequest request) {
        return baseUrl(request) + McpConstants.PROTECTED_RESOURCE_METADATA_MCP_PATH;
    }

    /**
     * 프런트 미리보기 딥링크(/preview?rs=&ss=) — 브라우저 로그인 세션 기준으로 홈 미리보기 플로우에 위임되어
     * 형식에 따라 미리보기/웹오피스/다운로드로 분기한다. 파일 전용이며 webBaseUrl은 ClientDomainService.getDomain 결과.
     * ss는 접근 스코프 사용자다 — 표시용 소유자(ownerSeq)가 아니라 목록 행의 userSeq/sharedByUserSeq 계열.
     * (공유 폴더에 제3자가 올린 파일은 소유자와 공유 제공 스코프가 달라 ownerSeq로는 권한 해석이 실패한다 — E2E 실측)
     */
    public static String previewUrl(String webBaseUrl, Long resourceSeq, Integer scopeUserSeq) {
        return webBaseUrl + "/preview?rs=" + resourceSeq + "&ss=" + scopeUserSeq;
    }

    /**
     * 프런트 편집 딥링크 — previewUrl에 mode=edit만 추가한 동일 주소.
     * 오피스 편집기 URL 조립은 프런트 몫(웹 편집 진입과 동일 패턴: 진입 시 권한 재검증 후
     * 웹오피스로 redirect, 불가하면 미리보기 폴백). 서버는 딥링크까지만 만든다.
     */
    public static String editUrl(String webBaseUrl, Long resourceSeq, Integer scopeUserSeq) {
        return previewUrl(webBaseUrl, resourceSeq, scopeUserSeq) + "&mode=edit";
    }

    /**
     * 프런트 폴더 열람 딥링크 — 내 스코프는 /drive, 공유 스코프는 /shared (홈 pinned 폴더 클릭 라우팅 동형).
     * pfs는 열어 볼 폴더 자신의 resourceSeq.
     */
    public static String folderUrl(String webBaseUrl, Long resourceSeq, Integer scopeUserSeq, int requestUserSeq) {
        String basePath = scopeUserSeq == requestUserSeq ? "/drive" : "/shared";
        return webBaseUrl + basePath + "?pfs=" + resourceSeq + "&ss=" + scopeUserSeq;
    }

    /** 목록/상세 항목용 — 파일은 미리보기, 폴더는 폴더 열람 링크. 재료 결손 시 null(NON_NULL 직렬화로 필드 생략). */
    public static String webViewUrl(String webBaseUrl, ResourceType resourceType, Long resourceSeq, Integer scopeUserSeq,
                                    int requestUserSeq) {
        if (resourceType == null || resourceSeq == null || scopeUserSeq == null) {
            return null;
        }
        return ResourceType.FOLDER == resourceType
                ? folderUrl(webBaseUrl, resourceSeq, scopeUserSeq, requestUserSeq)
                : previewUrl(webBaseUrl, resourceSeq, scopeUserSeq);
    }
}
