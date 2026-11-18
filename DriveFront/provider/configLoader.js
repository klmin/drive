import { setAppConfig } from '@/utils/api';
import { getTenantPrefix } from '@/utils/tenantPrefix';
import { getApiKeySession } from '@/utils/apiKeySession';

/**
 * API base URL 조립.
 *  - 백엔드 origin: next dev 3000 포트 직접 접속은 8080 직결, 그 외(Caddy 경유·jar 배포)는 같은 origin.
 *  - 테넌트 접두: PATH 모드면 현재 URL 에서 역산한 `/{slug}` 를 origin 뒤에 붙인다(SUBDOMAIN 모드는 '').
 *    → `/{slug}/api/v1/...`, `/{slug}/oauth2/...`, `/{slug}/branding/images/...`
 */
export async function loadConfig() {
    // developers iframe 에서 새로고침·전체 이동한 경우 저장해 둔 prefix 를 다시 적용
    const apiKeySession = getApiKeySession();
    if (apiKeySession?.prefix) return applyApiPrefix(apiKeySession.prefix);

    const { hostname, port, protocol } = window.location;
    // next dev(3000)에 직접 붙은 경우(localhost·개발 PC IP)만 백엔드 8080 직결 — Caddy 경유·배포 빌드는 같은 origin
    const backendOrigin =
        process.env.NODE_ENV === 'development' && port === '3000'
            ? `${protocol}//${hostname}:8080`
            : '';

    return applyConfig(backendOrigin);
}

/**
 * developers iframe — API 주소를 부모 창이 넘긴 developers BE 주소(prefix) 기준으로 바꾼다.
 * 경로는 그대로(`{prefix}/{slug}/api/v1/...`)이고 developers BE 가 prefix 를 떼고 Drive BE 로 넘긴다.
 * API 래퍼·다운로드·프로필·브랜딩 이미지 주소가 함께 따라간다.
 */
export function applyApiPrefix(prefix) {
    return applyConfig(prefix.replace(/\/+$/, ''));
}

function applyConfig(origin) {
    const tenantPrefix = getTenantPrefix();
    const appConfig = {
        apiUrl: `${origin}${tenantPrefix}/api/v1`,
        domain: `${origin}${tenantPrefix}`,
        tenantPrefix,
    };

    setAppConfig(appConfig);

    // 전역 접근을 위해 window에도 저장 가능
    window.__APP_CONFIG__ = appConfig;

    return appConfig;
}
