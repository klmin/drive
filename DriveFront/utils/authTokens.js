import Cookies from 'js-cookie';
import { getLanguageCode } from '@/utils/api';
import { tenantCookiePath, stripTenantPrefix, getTenantSlug } from '@/utils/tenantPrefix';
import { clearApiKeySession, hasApiKeySession } from '@/utils/apiKeySession';

// 역할별 키 — 토큰은 서버가 HttpOnly 쿠키로 관리하므로 여기에는 없다
const KEY = {
    USER: {
        role: 'driveRoleType_user', // 로그인 표시 ('USER' 저장)
        lang: 'driveLanguage_user',
    },
    ANONYMOUS: {
        role: 'driveRoleType_anonymous',
        lang: 'driveLanguage_anonymous',
    },
    ADMIN: {
        role: 'driveRoleType_admin', // 'ADMIN' 저장
        lang: 'driveLanguage_admin',
    },
    SUPER_ADMIN: {
        role: 'driveRoleType_superadmin', // 'SUPER_ADMIN' 저장
        lang: 'driveLanguage_superadmin',
    },
};

/** 역할별 실제 쿠키 키 — 테넌트 경로면 `{slug}_기존키` (SUPER_ADMIN 제외) */
function keysFor(role) {
    const base = KEY[role];
    const slug = role === 'SUPER_ADMIN' ? null : getTenantSlug();
    if (!slug) return base;
    return Object.fromEntries(
        Object.entries(base).map(([field, name]) => [field, `${slug}_${name}`])
    );
}

/** 현재 경로 기반으로 역할 추론: /superadmin/** → SUPER_ADMIN, /admin/** → ADMIN, 그 외 → USER (PATH 모드 접두 `/{slug}` 는 뗀 뒤 판정) */
export function inferRoleFromPath() {
    if (typeof window === 'undefined') return 'USER';
    const pathname = stripTenantPrefix(window.location.pathname);
    if (pathname.startsWith('/superadmin')) return 'SUPER_ADMIN';
    if (pathname.startsWith('/admin')) return 'ADMIN';
    // 공유 공개 페이지: 사용자 로그인이 없으면 익명 로그인 사용
    if (pathname.startsWith('/s/')) {
        return isLoggedIn('USER') ? 'USER' : 'ANONYMOUS';
    }
    return 'USER';
}

/** 관리자·슈퍼관리자 화면인지 — 사용자 전용 API(드라이브·용량·내 프로필 이미지)를 부르지 않는다(토큰 = 화면 역할) */
export function isAdminArea() {
    const role = inferRoleFromPath();
    return role === 'ADMIN' || role === 'SUPER_ADMIN';
}

/** role 없는 경우 경로 기반으로 선택 — 요청의 `X-Drive-Role` 헤더 값 */
export function normalizeRole(roleType) {
    if (
        roleType === 'ADMIN' ||
        roleType === 'USER' ||
        roleType === 'SUPER_ADMIN' ||
        roleType === 'ANONYMOUS'
    )
        return roleType;
    return inferRoleFromPath();
}

/**
 * 역할별 표시·언어 쿠키 path — 서버가 내려주는 로그인 쿠키와 같은 규칙.
 *  - USER/ADMIN/ANONYMOUS: {@link tenantCookiePath} — PATH 테넌트 모드는 `/t/{slug}/`, SUBDOMAIN 모드는 '/'.
 *  - SUPER_ADMIN: 항상 '/'. 슈퍼어드민은 테넌트 소속이 아니고 화면(`/superadmin/**`)·API 가 접두 없는 루트라,
 *    접두 붙은 `/t/{slug}/superadmin/login/` 에서 로그인해도 루트 화면에서 보여야 한다.
 */
function cookiePathFor(role) {
    return role === 'SUPER_ADMIN' ? '/' : tenantCookiePath();
}

/**
 * 로그인 성공 시 - 응답 data 에서 필요한 값을 골라 역할별 로그인 표시·언어를 저장(토큰은 서버가 Set-Cookie 로 내려준다).
 * 쿠키 키는 {@link keysFor}, path 는 {@link cookiePathFor}.
 */
export function saveAuth(roleType, data) {
    const role = normalizeRole(roleType);
    const K = keysFor(role);
    const path = cookiePathFor(role);

    if (role === 'USER') clearAuth('ANONYMOUS');

    Cookies.set(K.role, role, { path });
    if (data?.userLanguage) Cookies.set(K.lang, data.userLanguage, { path });
}

/**
 * 역할별 로그인 여부 (roleType 생략 시 현재 경로로 판단).
 * 토큰은 JS 로 읽을 수 없어 로그인 성공 때 남긴 표시로 판단하고, 실제 유효성은 서버 응답(401 → 재발급)으로 확인한다.
 * developers iframe 은 표시 대신 API Key 세션으로 판단한다.
 */
export function isLoggedIn(roleType) {
    const role = normalizeRole(roleType);
    return hasApiKeySession(role) || !!Cookies.get(keysFor(role).role);
}

/**
 * 해당 역할·현재 테넌트의 클라이언트 로그인 상태만 비움 (다른 역할·테넌트는 건드리지 않음).
 * 서버의 로그인 쿠키는 로그아웃 API 가 지운다.
 */
export function clearAuth(roleType) {
    const role = normalizeRole(roleType);
    const K = keysFor(role);
    const path = cookiePathFor(role);

    Cookies.remove(K.role, { path });
    Cookies.remove(K.lang, { path });
    if (hasApiKeySession(role)) clearApiKeySession();

    if (role === 'USER') {
        localStorage.removeItem('shareEnabled');
        removeMfaVerify();
        removeMfaSetup();
        removeSsoSignupSession();
        removePasswordChangeRequired();
    }
}

/** developers iframe 의 API Key 세션인지 (로그아웃 메뉴 숨김 등) */
export function isApiKeyLogin(roleType) {
    return hasApiKeySession(normalizeRole(roleType));
}

/** 언어 쿠키 */
export function getLanguage(roleType) {
    const K = keysFor(normalizeRole(roleType));
    return Cookies.get(K.lang) ?? null;
}
export function setLanguage(roleType, lang) {
    const role = normalizeRole(roleType);
    Cookies.set(keysFor(role).lang, lang, { path: cookiePathFor(role) });
}

/** MFA 토큰 */
/** MFA 인증 */
// 로그인 시도 결과로부터 저장
export function saveMfaVerify(token, type) {
    sessionStorage.setItem('driveMfaVerifyToken', token);
    sessionStorage.setItem('driveChallengeType', type);
}
// 로그인 중 MFA 인증 시 사용
export function getMfaVerify() {
    return {
        token: sessionStorage.getItem('driveMfaVerifyToken') || null,
        type: sessionStorage.getItem('driveChallengeType') || null,
    };
}
// 인증 완료 후 제거
export function removeMfaVerify() {
    sessionStorage.removeItem('driveMfaVerifyToken');
    sessionStorage.removeItem('driveChallengeType');
}

/** 로그인 중 MFA 초기 설정 - setup */
// 로그인 시도 결과로부터 저장
export function saveMfaSetup(token) {
    sessionStorage.setItem('driveMfaSetupToken', token);
}
// mfa 인증 qr uri를 가져올 때 사용
export function getMfaSetup() {
    return sessionStorage.getItem('driveMfaSetupToken') || null;
}
// qr uri 가져온 후 제거
export function removeMfaSetup() {
    sessionStorage.removeItem('driveMfaSetupToken');
}

/** 로그인 성공 토스트 세션 */
const loginSuccessMessages = {
    ko: '로그인 성공',
    en: 'Logged in successfully.',
    es: 'Inicio de sesión correcto.',
};

export function setLoginSuccessSession(userLanguage) {
    const lang = userLanguage === 'auto' ? getLanguageCode() : userLanguage;
    sessionStorage.setItem('loginSuccess', '1');
    sessionStorage.setItem(
        'loginSuccessMessage',
        loginSuccessMessages[lang] ?? loginSuccessMessages.en
    );
}

/** 초기 비밀번호 변경 유도 세션 */
export function savePasswordChangeRequired() {
    sessionStorage.setItem('drivePasswordChangeRequired', '');
}
export function getPasswordChangeRequired() {
    return sessionStorage.getItem('drivePasswordChangeRequired') !== null;
}
export function removePasswordChangeRequired() {
    sessionStorage.removeItem('drivePasswordChangeRequired');
}

/** MCP OAuth 연동 로그인 진행 세션 — oauth2-login 페이지가 SSO(IdP 왕복) 시작 전에 저장,
 *  /sso/callback이 로그인 성공 시 홈 대신 oauth2-login으로 복귀시키는 근거 */
export function saveOAuth2LoginPending() {
    sessionStorage.setItem('oauth2LoginPending', '1');
}
export function getOAuth2LoginPending() {
    return sessionStorage.getItem('oauth2LoginPending') !== null;
}
export function removeOAuth2LoginPending() {
    sessionStorage.removeItem('oauth2LoginPending');
}

/** SSO 로그인 에러 세션 */
export function saveSsoLoginError(errorCode) {
    sessionStorage.setItem('ssoLoginErrorCode', errorCode ?? '');
}
export function getSsoLoginError() {
    return sessionStorage.getItem('ssoLoginErrorCode');
}
export function removeSsoLoginError() {
    sessionStorage.removeItem('ssoLoginErrorCode');
}

/** 공유링크 접근 토스트 세션 (이동 전 저장 후, 도착한 페이지에서 표시) */
export function saveShareLinkToast(type) {
    sessionStorage.setItem('shareLinkToastType', type ?? '');
}
export function getShareLinkToast() {
    return sessionStorage.getItem('shareLinkToastType');
}
export function removeShareLinkToast() {
    sessionStorage.removeItem('shareLinkToastType');
}

/** SSO 회원가입 세션 */
export function saveSsoSignupSession({ token, email, name }) {
    sessionStorage.setItem('ssoSignupToken', token ?? '');
    sessionStorage.setItem('ssoSignupEmail', email ?? '');
    sessionStorage.setItem('ssoSignupName', name ?? '');
}
export function getSsoSignupSession() {
    return {
        token: sessionStorage.getItem('ssoSignupToken') || null,
        email: sessionStorage.getItem('ssoSignupEmail') || null,
        name: sessionStorage.getItem('ssoSignupName') || null,
    };
}
export function removeSsoSignupSession() {
    sessionStorage.removeItem('ssoSignupToken');
    sessionStorage.removeItem('ssoSignupEmail');
    sessionStorage.removeItem('ssoSignupName');
}
