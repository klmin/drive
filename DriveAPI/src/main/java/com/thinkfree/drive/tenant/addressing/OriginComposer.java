package com.thinkfree.drive.tenant.addressing;

import com.thinkfree.drive.basedomain.data.BaseDomainData;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 아웃바운드 origin({@code scheme://host[:port]}) 조립 공용 규칙.
 * <ul>
 *     <li>호스트가 <b>베이스 도메인에서 온 경우</b> — 베이스에 스킴이나 포트를 입력해 두었으면 그 값을 그대로 쓴다
 *         (스킴만 입력 → 포트 생략, 포트만 입력 → 스킴은 호출부 값). 둘 다 없으면 호출부 값.</li>
 *     <li>그 외(요청 host·설정 폴백) — 호출부 값.</li>
 * </ul>
 * 호출부 값 = 스킴: 요청 스킴, 요청이 없으면(스케줄러·{@code @Async}) https / 포트: ClientDomainService 모드 규칙(운영 생략, 로컬은 요청 포트).
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class OriginComposer {

    static String compose(String scheme, String host, Integer port) {
        return scheme + "://" + host + (port == null ? "" : ":" + port);
    }

    /** 호스트가 베이스 도메인(또는 그 위의 서브도메인)일 때 — 베이스에 입력한 스킴·포트 우선. */
    static String composeOnBase(BaseDomainData base, String host, String scheme, Integer port) {
        if (base == null || (base.getScheme() == null && base.getPort() == null)) {
            return compose(scheme, host, port);
        }
        String effectiveScheme = base.getScheme() != null ? base.getScheme() : scheme;
        return compose(effectiveScheme, host, base.getPort());
    }
}
