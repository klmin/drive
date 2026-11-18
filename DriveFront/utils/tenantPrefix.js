/**
 * 테넌트 주소 접두(PATH 모드 `/t/{slug}`) 판별·부착 유틸.
 *
 * 백엔드 `tenant.resolution-mode` 가
 *  - SUBDOMAIN : 테넌트는 Host 로 판별되고 경로에 접두가 없다 → 접두 ''.
 *  - PATH      : 모든 URL 이 `/t/{slug}/...` 로 오고 백엔드 API 도 `/t/{slug}/api/v1/...` 로 불러야 한다.
 * 프론트는 모드를 설정으로 알지 않고, **현재 문서 URL 에서 접두를 역산**한다. 그래서 같은 빌드가 두 모드에서 동작한다.
 *
 * 판별 규칙 (TENANT_PATH_MARKER_HANDOFF.md §2·§4-1):
 *  `location.pathname` 의 첫 세그먼트가 고정 마커 `t` 이면 둘째 세그먼트가 슬러그 → 접두 `/t/{slug}`. 아니면 ''.
 *  결정적 규칙이라 페이지 라우트(`__NEXT_DATA__.page`) 역산·예약어 목록이 필요 없고, 404 페이지도 접두를 유지한다.
 *  `/superadmin/**` 은 루트 라우트(tenant-less)라 마커가 없어 자동으로 ''.
 *
 * 이 모듈은 의존성이 없는 leaf 모듈이다 — api.js ↔ authTokens.js 순환에 끼지 않도록 여기서만 import 한다.
 */

/** 테넌트 경로 마커 세그먼트. 백엔드 `TenantPathMarker.SEGMENT` 와 값이 일치해야 한다. */
export const TENANT_PATH_MARKER = 't';

/** 테넌트 판별 실패 errorCode (백엔드 `TenantErrorCode.TENANT_HOST_NOT_FOUND` — 미등록·비활성·테넌트 누락 공통) */
export const TENANT_NOT_FOUND_ERROR_CODE = 'TENANT_004';

const MARKER_PATTERN = new RegExp(`^/${TENANT_PATH_MARKER}/([^/]+)`);

let cachedPrefix; // undefined = 아직 계산 안 함(브라우저에서 첫 호출 때 1회 계산·고정)

/**
 * pathname 에서 접두를 계산한다. 순수 함수(테스트·재사용용).
 * @param {string} pathname  location.pathname (끝 슬래시 유무 무관)
 * @returns {string} '' 또는 '/t/{slug}'
 */
export function detectTenantPrefix(pathname) {
    if (typeof pathname !== 'string') return '';
    const m = MARKER_PATTERN.exec(pathname);
    return m ? `/${TENANT_PATH_MARKER}/${m[1]}` : '';
}

/** 현재 문서의 테넌트 접두. SUBDOMAIN 모드·슈퍼어드민·마커 없음이면 ''. 브라우저에서 첫 호출 때 계산해 고정한다. */
export function getTenantPrefix() {
    if (typeof window === 'undefined') return ''; // 빌드(정적 export) 시점 — 캐시하지 않음
    if (cachedPrefix === undefined) {
        cachedPrefix = detectTenantPrefix(window.location.pathname);
    }
    return cachedPrefix;
}

/** 현재 문서의 테넌트 슬러그. 접두가 없으면 null. */
export function getTenantSlug() {
    const prefix = getTenantPrefix();
    return prefix ? prefix.slice(`/${TENANT_PATH_MARKER}/`.length) : null;
}

/** pathname 이 슬러그 없는 마커 경로(`/t`, `/t/`, `/t//…`)인지 여부 */
export function isEmptyTenantPath(pathname) {
    if (typeof pathname !== 'string') return false;
    const [first, second] = pathname.split('/').slice(1);
    return first === TENANT_PATH_MARKER && !second;
}

/** 현재 문서가 슬러그 없는 마커 경로인지 여부 */
export function isInvalidTenantPath() {
    if (typeof window === 'undefined') return false;
    return isEmptyTenantPath(window.location.pathname);
}

/** 테넌트 판별 실패 errorCode 여부 */
export function isTenantError(errorCode) {
    return errorCode === TENANT_NOT_FOUND_ERROR_CODE;
}

/** 테스트·재판별용 캐시 초기화 */
export function resetTenantPrefixCache() {
    cachedPrefix = undefined;
}

/**
 * 앱 내부 절대 경로(`/login`, `/login?x=1`)에 접두를 붙인다.
 * 이미 접두가 붙어 있거나, 외부 URL(`http://…`, `//…`), 상대 경로면 그대로 반환한다(멱등).
 *
 * next/router 의 URL 객체(`{ pathname, query }`)도 받는다 — `pathname` 만 접두를 붙인 새 객체를 돌려준다
 * (쿼리 갱신·shallow 라우팅 호출부가 이 형태를 쓴다).
 */
export function tenantHref(path) {
    if (path && typeof path === 'object') {
        const prefix = getTenantPrefix();
        if (!prefix || typeof path.pathname !== 'string') return path;
        return { ...path, pathname: tenantHref(path.pathname) };
    }
    if (typeof path !== 'string' || path === '') return path;
    const prefix = getTenantPrefix();
    if (!prefix) return path;
    if (!path.startsWith('/') || path.startsWith('//')) return path; // 상대 경로·프로토콜 상대 URL
    if (path === prefix || path.startsWith(`${prefix}/`) || path.startsWith(`${prefix}?`))
        return path;
    return prefix + path;
}

/** 접두가 붙은 pathname(`/t/acme/admin/login/`)에서 접두를 뗀 앱 내부 경로(`/admin/login/`)를 돌려준다. */
export function stripTenantPrefix(pathname) {
    if (typeof pathname !== 'string') return pathname;
    const prefix = getTenantPrefix();
    if (!prefix) return pathname;
    if (pathname === prefix) return '/';
    if (pathname.startsWith(`${prefix}/`)) return pathname.slice(prefix.length);
    return pathname;
}

/** 테넌트 주소 입력란 앞 고정 표기 — `{baseDomain}/t/` (PATH 모드) */
export function tenantAddressPrefix(baseDomain) {
    return `${baseDomain}/${TENANT_PATH_MARKER}/`;
}

/** 토큰 쿠키 path — 같은 origin 의 다른 테넌트(`/t/a/`, `/t/b/`)가 토큰을 덮어쓰지 않도록 접두로 스코프한다. SUBDOMAIN 모드는 '/'. */
export function tenantCookiePath() {
    return `${getTenantPrefix()}/`;
}
