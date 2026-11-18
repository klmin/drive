import axios from 'axios';
import { clearAuth, inferRoleFromPath, isLoggedIn, normalizeRole } from '@/utils/authTokens';
import { clearApiKeySession, getApiKeySession } from '@/utils/apiKeySession';
import { ROUTES } from '@/constants/routes';
import { resolveLocale } from '@/utils/localeUtils';
import { tenantHref, stripTenantPrefix, isTenantError } from '@/utils/tenantPrefix';

// API 응답 인터셉터
// 모든 API 응답 시 'api-response' 이벤트를 발행해 공유 상태 최신화.
// config 요청은 제외 (무한 루프 방지)
// ClientConfigProvider 해당 이벤트를 구독해 공유 상태를 갱신함.
axios.interceptors.response.use(res => {
    if (!res.config.url?.includes('config')) {
        window.dispatchEvent(new Event('api-response'));
    }
    return res;
});

// export const baseURL = process.env.NEXT_PUBLIC_HOST || process.env.NEXT_PUBLIC_API_URL;
let baseURL = '';
let domain = '';

export const setAppConfig = appConfig => {
    if (appConfig.apiUrl !== undefined) {
        baseURL = appConfig.apiUrl?.replace(/\/$/, ''); // 끝에 / 있으면 제거
        domain = appConfig.domain;
    }
};

export const getBaseURL = () => baseURL || '';
export const getDomain = () => domain || '';

/**
 * navigator.language에서 언어 코드 반환.
 * Drive API 요청의 Accept-Language 헤더에 사용.
 */
export const getLanguageCode = () => {
    if (typeof navigator !== 'undefined' && typeof navigator.language === 'string') {
        return navigator.language;
    }
    return 'en';
};

/** 서버가 어느 역할의 로그인 쿠키를 읽을지 정하는 헤더 */
const ROLE_HEADER = 'X-Drive-Role';

/**
 * 공통 config — 로그인 토큰은 브라우저가 HttpOnly 쿠키로 싣고, 쓸 역할은 `X-Drive-Role` 로 알린다.
 * - config.__roleType 으로 역할 지정('USER'|'ADMIN'|'SUPER_ADMIN'|'ANONYMOUS'), 없으면 현재 경로 기준
 * - 첫 인자 자리는 이전 호출 형태(token, config) 호환용이며 토큰은 쓰지 않는다
 * - developers iframe 은 쿠키 대신 `Authorization: Bearer <API Key>` (developers BE 로 가므로 쿠키를 싣지 않음)
 */
export const getAuthConfig = (tokenOrConfig, maybeConfig) => {
    const config =
        (typeof tokenOrConfig === 'string' || tokenOrConfig === null
            ? maybeConfig
            : tokenOrConfig) || {};
    const apiKeySession = getApiKeySession();

    const headers = {
        'Accept-Language': resolveLocale(getLanguageCode()),
        [ROLE_HEADER]: normalizeRole(config.__roleType),
        ...(apiKeySession ? { Authorization: `Bearer ${apiKeySession.apiKey}` } : {}),
        ...(config.headers || {}),
    };

    return { ...config, headers, withCredentials: !apiKeySession };
};

const reissuing = {};

/** refresh 쿠키로 재발급 — 같은 역할의 재발급은 한 번만 보내고 동시에 401 난 요청들이 결과를 함께 기다린다 */
const reissue = role => {
    if (!reissuing[role]) {
        reissuing[role] = axios
            .post(`${getBaseURL()}/token/reissue`, null, getAuthConfig({ __roleType: role }))
            .then(res => res?.data?.code === 200)
            .catch(() => false)
            .finally(() => {
                delete reissuing[role];
            });
    }
    return reissuing[role];
};

const handleAuthError = async (err, isCsv) => {
    const status = err?.response?.data?.status || err?.response?.status; // 401, 403, 428 등
    const errorCode = err?.response?.data?.errorCode;
    const code = err?.response?.data?.code || 0; // 428

    // 요청 때 넣은 role 정보 있으면 그걸 우선 사용
    const role = err?.config?.__roleType || inferRoleFromPath();
    const isAdmin = role === 'ADMIN';
    const loginPageByRole =
        role === 'SUPER_ADMIN'
            ? ROUTES.SUPERADMIN_LOGIN
            : isAdmin
              ? ROUTES.ADMIN_LOGIN
              : ROUTES.LOGIN;
    // trailingSlash 빌드에서 pathname이 '/login/' 형태로 오므로 끝 슬래시 제거 후 비교 (PATH 모드 접두 `/{slug}` 도 뗀다)
    const pathname = stripTenantPrefix(window.location.pathname).replace(/\/+$/, '') || '/';
    const isLoginPage =
        pathname === ROUTES.ADMIN_LOGIN ||
        pathname === ROUTES.SUPERADMIN_LOGIN ||
        pathname === ROUTES.LOGIN ||
        pathname === ROUTES.OAUTH2_LOGIN; // MCP OAuth 연동 로그인 — 428 등 로그인 에러를 페이지 catch로 전달해야 MFA 분기가 동작
    const isMfaPage = pathname.startsWith(ROUTES.MFA);
    const isJwtAUthPage = pathname.startsWith(ROUTES.SSO_JWT);
    const isErrorPage = pathname.startsWith(ROUTES.ERROR_PAGE);

    // console.log('API error:', { status, errorCode, code, url: err?.config?.url });

    // 테넌트 경로 누락 또는 없는 테넌트 - 에러 페이지 이동
    // clearAuth 하지 않음, 에러 페이지는 루프 방지·슈퍼관리자는 테넌트 미소속이라 이동하지 않음
    if (!isErrorPage && role !== 'SUPER_ADMIN' && isTenantError(errorCode)) {
        window.location.replace(ROUTES.ERROR_PAGE_TENANT);
        return new Promise(() => {});
    }

    // developers iframe 의 API Key 가 유효하지 않음 - 재발급이 없으므로 진입 화면에서 키를 다시 받는다
    // 진입 화면의 요청이면 그 화면이 실패를 표시
    if (status === 401 && getApiKeySession()) {
        if (pathname === ROUTES.LOGIN_API_KEY || pathname === ROUTES.ADMIN_LOGIN_API_KEY) {
            return err?.response?.data;
        }
        clearApiKeySession();
        window.location.replace(
            tenantHref(isAdmin ? ROUTES.ADMIN_LOGIN_API_KEY : ROUTES.LOGIN_API_KEY)
        );
        return new Promise(() => {});
    }

    // 토큰 없음, 만료, 유효하지 않음
    if (status === 401 && errorCode === 'AUTH_002') {
        const originalRequest = err.config;
        const isProbe = !!originalRequest?.__probe;
        // 로그인 표시가 있거나 표시 없이 확인하는 요청이면 재발급 후 한 번만 재시도
        if (
            !isLoginPage &&
            (isProbe || isLoggedIn(role)) &&
            originalRequest &&
            !originalRequest._retry
        ) {
            if (await reissue(role)) {
                originalRequest._retry = true;
                // 재시도 실패도 최초 요청과 같은 규칙으로 처리
                try {
                    return await axios(originalRequest);
                } catch (retryErr) {
                    return await handleAuthError(retryErr, isCsv);
                }
            }
        }

        // 표시 없이 확인하는 요청은 호출부(가드)가 로그인 화면으로 보낸다
        if (isProbe) return err?.response?.data;

        clearAuth(role);
        window.location.replace(tenantHref(loginPageByRole));
        // 페이지 이동 후 호출부 코드가 실행되지 않도록 promise를 영원히 pending 상태로
        return new Promise(() => {});

        // 관리자 권한 필요
    } else if (status === 403 && errorCode === 'AUTH_007') {
        clearAuth(role);
        window.location.replace(tenantHref(loginPageByRole));
        return new Promise(() => {});
    }

    // 차단된 국가나 IP에서의 접근 - 에러 페이지 이동
    else if (
        !isLoginPage &&
        status === 403 &&
        (errorCode === 'ACCESS_001' || errorCode === 'ACCESS_005')
    ) {
        clearAuth(role);
        window.location.replace(tenantHref(ROUTES.ERROR_PAGE_ACCESS));
        return null;
    }

    // 차단된 국가나 IP에서 로그인 시도한 경우 토스트 표시를 위해 에러 내려줌
    else if (
        isLoginPage &&
        status === 403 &&
        (errorCode === 'ACCESS_001' || errorCode === 'ACCESS_005')
    ) {
        sessionStorage.setItem('accessError', errorCode);
        return Promise.reject(err);
    }

    // 권한 없는 다른 테넌트 유저의 접근
    // ex) 공유 링크를 통한 접근, A 테넌트로 로그인한 상태에서 B 테넌트 페이지 접속 시도
    else if (status === 403 && errorCode === 'REQUEST_006') {
        clearAuth(role);
        window.location.replace(tenantHref(ROUTES.ERROR_PAGE_SHARE));
        return new Promise(() => {});
    }

    // 로그인 실패 - 아이디 또는 비밀번호 오류 - 로그인 페이지에서 토스트 표시
    else if (isLoginPage && status === 401 && errorCode === 'AUTH_006') {
        return Promise.reject(err);
    }

    // 로그인 잡김 경고 - 로그인 페이지에서 토스트 표시
    else if (isLoginPage && status === 401 && errorCode === 'AUTH_014') {
        return Promise.reject(err);
    }

    // 로그인 5회 실패로 계정 잠긴 경우 - 로그인 페이지에서 토스트 표시
    else if (isLoginPage && status === 403 && errorCode === 'AUTH_008') {
        return Promise.reject(err);
    }

    // MFA 인증 필요
    else if (code === 428 && (isLoginPage || isJwtAUthPage)) {
        console.log('428');
        return Promise.reject(err);
    }

    // MFA 인증 실패 ex) 토큰 만료, 잘못된 인증 코드
    else if (isMfaPage && (status === 401 || status === 400)) {
        console.log('인증 실패');
        return Promise.reject(err);
    }

    // JWT 로그인 실패
    else if (isJwtAUthPage) {
        // jwt 오류 발생 (jwt 유저와 드라이브에 로그인된 유저 불일치 등)
        return Promise.reject(err);
    }

    return isCsv ? err : err?.response?.data;
};

const api = {
    getBaseURL: () => {
        return getBaseURL();
    },

    getProfileImage: userSeq => {
        if (!userSeq) return '';
        return getBaseURL() + '/users/' + userSeq + '/profile-image';
    },

    getFaviconImage: () => getDomain() + `/branding/images/favicon`,
    getSymbolLogoImage: () => getDomain() + `/branding/images/logo-icon`,
    getBasicLogoImage: () => getDomain() + `/branding/images/logo-image`,
    getEmailLogoImage: () => getDomain() + `/branding/images/email-logo`,

    Get: async (url, data = {}, token = null, config = {}) => {
        try {
            const res = await axios.get(`${getBaseURL()}/${url}`, {
                ...getAuthConfig(token, config),
                ...data,
            });
            return res;
        } catch (err) {
            return await handleAuthError(err);
        }
    },

    Post: async (url, data, token = null, config = {}) => {
        try {
            const res = await axios.post(
                `${getBaseURL()}/${url}`,
                data,
                getAuthConfig(token, config) ? getAuthConfig(token, config) : {}
            );
            return res.data;
        } catch (err) {
            return await handleAuthError(err);
        }
    },

    PostRaw: async (url, data, token = null, config = {}) => {
        try {
            const res = await axios.post(
                `${getBaseURL()}/${url}`,
                data,
                getAuthConfig(token, config)
            );
            return res;
        } catch (err) {
            console.log(err);
            return await handleAuthError(err);
        }
    },

    Put: async (url, data, token = null, config = {}) => {
        try {
            const res = await axios.put(
                `${getBaseURL()}/${url}`,
                data,
                getAuthConfig(token, config)
            );
            return res;
        } catch (err) {
            return await handleAuthError(err);
        }
    },

    Delete: async (url, data, token = null, config = {}) => {
        try {
            const res = await axios.delete(`${getBaseURL()}/${url}`, {
                ...getAuthConfig(token, config),
                ...data,
            });
            return res;
        } catch (err) {
            return await handleAuthError(err);
        }
    },
    Patch: async (url, data, token = null, config = {}) => {
        try {
            const res = await axios.patch(
                `${getBaseURL()}/${url}`,
                data,
                getAuthConfig(token, config)
            );
            return res;
        } catch (err) {
            return await handleAuthError(err);
        }
    },
    // csv 파일 전송 -> 일괄 사용자 등록 실패 결과 파일 응답
    PostForm: async (url, data = {}, token = null, config = {}, isCsv = false) => {
        try {
            const formData = new FormData();

            Object.entries(data).forEach(([key, value]) => {
                if (value !== undefined && value !== null) {
                    formData.append(key, value);
                }
            });

            const cfg = getAuthConfig(token, {
                ...config,
                headers: {
                    ...(config.headers || {}),
                    'Content-Type': 'multipart/form-data',
                },
            });

            const res = await axios.post(`${getBaseURL()}/${url}`, formData, cfg);

            return res;
        } catch (err) {
            return await handleAuthError(err, isCsv);
        }
    },
    PatchForm: async (url, data = {}, token, config = {}) => {
        try {
            const formData = new FormData();

            Object.entries(data).forEach(([key, value]) => {
                if (value !== undefined && value !== null) {
                    formData.append(key, value);
                }
            });

            const cfg = getAuthConfig(token, {
                ...config,
                headers: {
                    ...(config.headers || {}),
                    'Content-Type': 'multipart/form-data',
                },
            });

            const res = await axios.patch(`${getBaseURL()}/${url}`, formData, cfg);

            return res;
        } catch (err) {
            return await handleAuthError(err);
        }
    },
    PutForm: async (url, data = {}, token, config = {}) => {
        try {
            const formData = new FormData();

            Object.entries(data).forEach(([key, value]) => {
                if (value !== undefined && value !== null) {
                    formData.append(key, value);
                }
            });

            const cfg = getAuthConfig(token, {
                ...config,
                headers: {
                    ...(config.headers || {}),
                    'Content-Type': 'multipart/form-data',
                },
            });

            const res = await axios.put(`${getBaseURL()}/${url}`, formData, cfg);

            return res;
        } catch (err) {
            return await handleAuthError(err);
        }
    },
};

export default api;
