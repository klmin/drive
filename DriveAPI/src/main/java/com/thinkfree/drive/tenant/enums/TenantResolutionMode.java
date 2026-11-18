package com.thinkfree.drive.tenant.enums;

/**
 * 요청에서 테넌트를 판별하는 방식. {@code tenant.resolution-mode} 로 설치별 하나만 선택한다.
 * <ul>
 *     <li>{@link #PATH} — {@code /t/{slug}/...} 마커 경로({@code /t/acme/api/v1/...})로 판별. 판별 후 {@code /t/acme} 를
 *         서블릿 contextPath 처럼 취급해 하위 시큐리티/MVC 는 prefix 없는 경로를 본다. 기본값(미지정 시).</li>
 *     <li>{@link #SUBDOMAIN} — Host 헤더 전체({@code acme.drive.example.com})로 판별.</li>
 * </ul>
 */
public enum TenantResolutionMode {
    SUBDOMAIN,
    PATH
}
