package com.thinkfree.drive.tenant.addressing;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 전략 매치 결과 (4상태).
 * <ul>
 *     <li>{@link Status#MATCHED} — 존재 + ACTIVE. {@code requestToForward} 로 체인 진행
 *         (PATH 모드는 테넌트 prefix 를 contextPath 로 에뮬레이션한 래퍼, SUBDOMAIN 모드는 원본).</li>
 *     <li>{@link Status#INACTIVE} — 주소로 테넌트가 특정되지만 비활성. fallback 없이 404.</li>
 *     <li>{@link Status#NOT_FOUND} — 주소가 테넌트 문법은 갖췄으나 슬러그가 비었거나 형식 불일치(PATH 모드 {@code /t/}, {@code /t/UPPER}).
 *         접두를 만들 수 없어 fallback 없이 404.</li>
 *     <li>{@link Status#NONE} — 주소로 특정되는 테넌트 없음(마커 없음·Host 미매치·<b>미등록 슬러그</b>). fallback 대상.
 *         {@code requestToForward} 가 있으면(PATH 모드 미등록 슬러그 — 접두를 뗀 래퍼) fallback 테넌트로 그 요청을 넘기고,
 *         없으면 원본 요청을 넘긴다. SUBDOMAIN 모드의 미등록 Host 가 fallback 을 타는 것과 대칭.</li>
 * </ul>
 */
public record TenantMatch(Status status, Integer tenantSeq, HttpServletRequest requestToForward) {

    public enum Status { MATCHED, INACTIVE, NOT_FOUND, NONE }

    private static final TenantMatch INACTIVE = new TenantMatch(Status.INACTIVE, null, null);
    private static final TenantMatch NOT_FOUND = new TenantMatch(Status.NOT_FOUND, null, null);
    private static final TenantMatch NONE = new TenantMatch(Status.NONE, null, null);

    public static TenantMatch matched(int tenantSeq, HttpServletRequest requestToForward) {
        return new TenantMatch(Status.MATCHED, tenantSeq, requestToForward);
    }

    public static TenantMatch inactive() {
        return INACTIVE;
    }

    public static TenantMatch notFound() {
        return NOT_FOUND;
    }

    /** 특정되는 테넌트 없음 — fallback 시 원본 요청 그대로. */
    public static TenantMatch none() {
        return NONE;
    }

    /** 특정되는 테넌트 없음 — fallback 시 {@code requestToForward}(접두를 뗀 래퍼)로 진행. */
    public static TenantMatch none(HttpServletRequest requestToForward) {
        return new TenantMatch(Status.NONE, null, requestToForward);
    }

    public boolean isMatched() {
        return status == Status.MATCHED;
    }

    public boolean isInactive() {
        return status == Status.INACTIVE;
    }

    public boolean isNotFound() {
        return status == Status.NOT_FOUND;
    }

    /** fallback 시 체인에 넘길 요청 — 전략이 준 래퍼가 있으면 그것, 없으면 {@code original}. */
    public HttpServletRequest requestToForwardOr(HttpServletRequest original) {
        return requestToForward != null ? requestToForward : original;
    }
}
