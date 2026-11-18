package com.thinkfree.drive.tenant.addressing;

import com.thinkfree.drive.util.RequestPathUtil;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * PATH 테넌트 모드의 경로 마커. URL 문법 = {@code /t/{slug}/…}.
 * <p>
 * 첫 세그먼트가 고정 마커 {@link #SEGMENT} 이면 테넌트 요청, 아니면 루트 요청이라는 결정적 규칙이라
 * 슬러그가 루트 라우트(컨트롤러·프론트 번들)와 경쟁하지 않는다 → 슬러그 예약어가 필요 없다.
 * 남는 규칙 하나: 루트 라우트(프론트 pages/public, 컨트롤러)에 {@code t} 를 만들지 않는다
 * (정적 번들은 기동 시 {@link StaticBundleIndex} 가 warn).
 * <p>
 * 예외 문법 하나 — RFC 8414 §3.1 / RFC 9728 §3.1 의 <b>경로 삽입형</b> 메타데이터 URL
 * {@code /.well-known/{doc}/t/{slug}[/…]}. OAuth 클라이언트(MCP SDK 포함)는 issuer {@code https://host/t/acme} 의
 * AS 메타데이터를 {@code /.well-known/oauth-authorization-server/t/acme} 로 조회한다(마커가 셋째 세그먼트).
 * {@link #slugOfWellKnownInsertion} 이 이 형식에서 슬러그를 꺼낸다.
 * <p>
 * 프론트 상수 {@code DriveFront/utils/tenantPrefix.js} 의 {@code TENANT_PATH_MARKER} 와 값이 일치해야 한다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TenantPathMarker {

    /** 마커 세그먼트. */
    public static final String SEGMENT = "t";

    /** 마커 접두({@code /t}). */
    public static final String PREFIX = "/" + SEGMENT;

    /** RFC 5785 well-known 루트 세그먼트. */
    public static final String WELL_KNOWN_SEGMENT = ".well-known";

    /** 마커 바로 뒤 세그먼트가 슬러그(0 기준 인덱스). 마커가 한 세그먼트이므로 1. */
    private static final int SLUG_SEGMENT_INDEX = 1;

    /** 삽입형 {@code /.well-known/{doc}/t/{slug}} 에서 마커·슬러그 위치(0 기준). */
    private static final int WELL_KNOWN_MARKER_INDEX = 2;
    private static final int WELL_KNOWN_SLUG_INDEX = 3;

    /** 슬러그의 경로 접두({@code /t/acme}). contextPath 에뮬레이션·아웃바운드 URL 조립 공용. */
    public static String prefixOf(String slug) {
        return PREFIX + "/" + slug;
    }

    /**
     * 경로에서 슬러그 세그먼트를 꺼낸다.
     * <ul>
     *     <li>첫 세그먼트가 마커가 아니면 {@code null} — 테넌트 요청이 아님(루트 요청)</li>
     *     <li>마커는 있는데 뒤가 비어 있으면({@code /t}, {@code /t/}, {@code /t//x}) 빈 문자열 — 형식 검증에서 걸러진다</li>
     *     <li>그 외 마커 다음 세그먼트 그대로(형식 검증은 호출자 몫, {@code DomainComposer.isValidSubdomain})</li>
     * </ul>
     */
    public static String slugOf(String path) {
        if (!SEGMENT.equals(RequestPathUtil.firstSegment(path))) {
            return null;
        }
        return RequestPathUtil.segment(path, SLUG_SEGMENT_INDEX);
    }

    /**
     * RFC 경로 삽입형 {@code /.well-known/{doc}/t/{slug}[/…]} 에서 슬러그를 꺼낸다.
     * 첫 세그먼트가 {@code .well-known} 이고 셋째 세그먼트가 마커일 때만 넷째 세그먼트(빈 문자열 가능), 아니면 {@code null}.
     * 형식 검증은 호출자 몫. 이 형식의 요청은 접두를 떼지 않고 원본 그대로 넘긴다(SAS 가 requestURI 로 issuer 를 계산).
     */
    public static String slugOfWellKnownInsertion(String path) {
        if (!WELL_KNOWN_SEGMENT.equals(RequestPathUtil.firstSegment(path))
                || !SEGMENT.equals(RequestPathUtil.segment(path, WELL_KNOWN_MARKER_INDEX))) {
            return null;
        }
        return RequestPathUtil.segment(path, WELL_KNOWN_SLUG_INDEX);
    }
}
