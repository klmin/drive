import { getTenantPrefix } from '@/utils/tenantPrefix';

/**
 * developers iframe 진입 시 부모 창이 넘긴 API Key·API 주소 prefix.
 * 화면 전체 이동·새로고침에도 남도록 sessionStorage 에 둔다(탭을 닫으면 사라짐). 쿠키에는 저장하지 않는다.
 * 저장할 때의 테넌트 경로와 다른 테넌트에서는 쓰지 않는다.
 */
const STORAGE_KEY = 'driveApiKeySession';

/** Drive → 부모 창: 메시지 받을 준비 완료 */
const READY_MESSAGE = 'drive:ready';
/** 부모 창 → Drive: `{ type, apiKey, prefix }` */
const API_KEY_MESSAGE = 'drive:api-key';
const WAIT_TIMEOUT_MS = 10000;

/** 인텔릭트 팝업 → Drive: API Key 요청 */
const INTELLECT_READY_MESSAGE = 'intellect:ready';
/** Drive → 인텔릭트 팝업: `{ type, apiKey, prefix }` */
const INTELLECT_API_KEY_MESSAGE = 'intellect:api-key';

let cached;

function read() {
    if (cached !== undefined) return cached;
    try {
        cached = JSON.parse(sessionStorage.getItem(STORAGE_KEY)) || null;
    } catch {
        cached = null;
    }
    return cached;
}

/** 진입 화면(USER/ADMIN)에서 받은 API Key 로 세션 시작 */
export function setApiKeySession(role, apiKey, prefix) {
    cached = { role, apiKey, prefix, tenantPrefix: getTenantPrefix() };
    try {
        sessionStorage.setItem(STORAGE_KEY, JSON.stringify(cached));
    } catch {
        // 저장소를 못 쓰면 이 화면에서만 유지
    }
}

/** 현재 테넌트의 API Key 세션 — 없으면 null */
export function getApiKeySession() {
    const session = read();
    return session && session.tenantPrefix === getTenantPrefix() ? session : null;
}

export function clearApiKeySession() {
    cached = null;
    try {
        sessionStorage.removeItem(STORAGE_KEY);
    } catch {
        // 무시
    }
}

/** API Key 세션인지 (role 생략 시 역할 무관) */
export function hasApiKeySession(role) {
    const session = getApiKeySession();
    return !!session && (!role || session.role === role);
}

/** iframe 안에서 열렸는지 — Drive 를 띄울 수 있는 부모는 서버의 frame-ancestors 가 developers 로 제한한다 */
export function isEmbedded() {
    return typeof window !== 'undefined' && window.parent !== window;
}

/**
 * 부모 창에 준비 완료를 알리고 API Key 메시지를 기다린다.
 * 부모 창이 보낸 메시지만 받는다. 시간 안에 안 오면 null.
 */
export function waitForApiKeyMessage() {
    return new Promise(resolve => {
        if (!isEmbedded()) {
            resolve(null);
            return;
        }

        let timer;
        const onMessage = event => {
            if (event.source !== window.parent) return;
            const { type, apiKey, prefix } = event.data || {};
            if (type !== API_KEY_MESSAGE || typeof apiKey !== 'string' || !apiKey) return;
            cleanup();
            resolve({ apiKey, prefix: typeof prefix === 'string' ? prefix : '' });
        };
        const cleanup = () => {
            clearTimeout(timer);
            window.removeEventListener('message', onMessage);
        };

        window.addEventListener('message', onMessage);
        timer = setTimeout(() => {
            cleanup();
            resolve(null);
        }, WAIT_TIMEOUT_MS);

        // 비밀 정보가 없고 부모는 frame-ancestors 로 제한되므로 targetOrigin 은 '*'
        window.parent.postMessage({ type: READY_MESSAGE }, '*');
    });
}

/** developers iframe 안의 사용자 화면인지 — 문서를 팝업으로 열고 인텔릭트에 `em=1` 을 붙인다 */
export function isDevelopersEmbed() {
    return isEmbedded() && hasApiKeySession('USER');
}

/**
 * 이 창이 연 인텔릭트 팝업의 `intellect:ready` 에 보관 중인 API Key·prefix 로 응답한다.
 * 같은 origin 이고 opener 가 이 창인 팝업에만 응답한다. 해제 함수를 반환.
 */
export function listenIntellectApiKeyRequest() {
    const onMessage = event => {
        if (event.origin !== window.location.origin) return;
        if (event.data?.type !== INTELLECT_READY_MESSAGE) return;

        const popup = event.source;
        if (!popup || popup.opener !== window) return;

        const session = getApiKeySession();
        if (!session || session.role !== 'USER') return;

        // 키가 담기므로 targetOrigin 은 Drive origin 으로 지정
        popup.postMessage(
            { type: INTELLECT_API_KEY_MESSAGE, apiKey: session.apiKey, prefix: session.prefix },
            window.location.origin
        );
    };

    window.addEventListener('message', onMessage);
    return () => window.removeEventListener('message', onMessage);
}
