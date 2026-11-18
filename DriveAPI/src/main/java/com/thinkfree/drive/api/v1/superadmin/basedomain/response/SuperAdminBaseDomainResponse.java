package com.thinkfree.drive.api.v1.superadmin.basedomain.response;

/**
 * @param baseDomain 호스트만(포트 없음) — 테넌트 도메인 조합 재료
 * @param scheme     입력한 스킴. 없으면 null(아웃바운드는 요청 스킴 → https)
 * @param port       입력한 포트. 없거나 스킴 기본 포트면 null
 * @param address    화면 표시·수정용 {@code [scheme://]host[:port]}
 */
public record SuperAdminBaseDomainResponse(
        Integer baseDomainSeq,
        String baseDomain,
        String scheme,
        Integer port,
        String address
) {
}
