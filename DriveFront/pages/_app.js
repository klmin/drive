import '@/styles/globals.css';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import theme from '@/theme';
import { calendarHeaderFormatOf } from '@/constants/dateFormats';
import { CacheProvider } from '@emotion/react';
import createCache from '@emotion/cache';
import { useState, useEffect, useMemo } from 'react';
import { CookiesProvider, useCookies } from 'react-cookie';
import { SnackbarProvider } from '@/provider/SnackbarProvider';
import { DialogProvider } from '@/provider/DialogProvider';
import { IntlProvider } from 'next-intl';
import useAuth from '@/hooks/useAuth';
import { UserContext } from '@/context/UserContext';
import { ResourcesProvider } from '@/provider/ResourceProvider';
import { AppProvider } from '@/provider/AppConfigProvider';
import { TenantProvider } from '@/provider/TenantProvider';
import { QuotaProvider } from '@/provider/QuotaProvider';
import { UserProfileProvider } from '@/provider/UserProfileProvider';
import DraggableImageBlocker from '@/hooks/draggableImageBlocker';
import AuthLayout from '@/components/common/layout/AuthLayout';
import AdminAuthLayout from '@/components/common/layout/AdminAuthLayout';
import AnonymousAuthLayout from '@/components/common/layout/AnonymousAuthLayout';
import { useRouter } from '@/hooks/useRouter';
import { ClipboardProvider } from '@/provider/ClipboardProvider';
import { isLoggedIn, getLanguage } from '@/utils/authTokens';
import { BrandingConfigProvider } from '@/provider/BrandingConfigProvider';
import LoginSuccessToast from '@/components/common/LoginSuccessToast';
import Head from 'next/head';
import api from '@/utils/api';
import { LoginConfigProvider } from '@/provider/LoginConfigProvider';
import { LicenseProvider } from '@/provider/LicenseProvider';
import { ClientConfigProvider } from '@/provider/ClientConfigProvider';
import SuperAdminAuthLayout from '@/components/common/layout/SuperAdminAuthLayout';
import { ROUTES } from '@/constants/routes';
import { getTenantPrefix } from '@/utils/tenantPrefix';
import { listenIntellectApiKeyRequest } from '@/utils/apiKeySession';

function createEmotionCache() {
    return createCache({ key: 'muicss', prepend: true });
}

function FaviconHead() {
    return (
        <Head>
            <link rel="icon" href={api.getFaviconImage()} />
        </Head>
    );
}

export default function App({ Component, pageProps, emotionCache }) {
    const cache = useMemo(() => emotionCache || createEmotionCache(), []);
    const [loading, setLoading] = useState(true);
    const [messages, setMessages] = useState(null);
    const [locale, setLocale] = useState('en');

    // 달력 헤더의 연·월 순서는 로케일마다 다른데 MUI는 항상 "월 년"으로 조립한다.
    // 언어가 정해지는 곳이 여기뿐이라 테마 defaultProps로 얹어 모든 DatePicker에 적용한다.
    const themeWithPickerLocale = useMemo(
        () =>
            createTheme(theme, {
                components: {
                    MuiPickersCalendarHeader: {
                        defaultProps: { format: calendarHeaderFormatOf(locale) },
                    },
                },
            }),
        [locale]
    );
    const router = useRouter();
    const isErrorPage = router.pathname.startsWith(ROUTES.ERROR_PAGE) || router.pathname === '/404';
    const isLoginPage =
        router.pathname === ROUTES.LOGIN ||
        router.pathname === ROUTES.OAUTH2_LOGIN || // MCP OAuth 연동 로그인 — 토큰 있어도 홈으로 안 보내고 브리지 수행
        router.pathname === ROUTES.OAUTH2_CONSENT || // MCP OAuth 동의 화면 — 레이아웃·auth guard 없이 단독 렌더
        router.pathname === ROUTES.ADMIN_LOGIN ||
        router.pathname === ROUTES.SUPERADMIN_LOGIN ||
        router.pathname === ROUTES.LOGIN_API_KEY || // developers iframe 진입 — auth guard 없이 단독 렌더
        router.pathname === ROUTES.ADMIN_LOGIN_API_KEY;
    const usesLoginConfig =
        !router.pathname.startsWith(ROUTES.ADMIN_BASE) &&
        !router.pathname.startsWith(ROUTES.SUPERADMIN_BASE);

    useEffect(() => {
        const cookieLang = getLanguage();
        const browserLang =
            typeof navigator !== 'undefined' && typeof navigator.language === 'string'
                ? navigator.language // "ko", "ko-KR", "en"
                : 'en';
        const shortBrowserLang = browserLang.split('-')[0];
        const isAuto = !cookieLang || cookieLang === 'auto';
        console.log('cookieLang', cookieLang);

        let lang = 'en';
        if (isAuto) {
            lang =
                shortBrowserLang === 'ko' || shortBrowserLang === 'en' || shortBrowserLang === 'es'
                    ? shortBrowserLang
                    : 'en';
        } else {
            lang = cookieLang;
        }

        setLocale(lang);

        const loadMessages = async () => {
            try {
                const msgs = (await import(`../messages/${lang}.json`)).default;
                setMessages(msgs);
            } catch (e) {
                console.error(`Failed to load messages for locale '${lang}'`, e);
                setMessages({});
            } finally {
                setLoading(false);
            }
        };

        loadMessages();
    }, [isLoggedIn()]);

    // developers iframe 에서 연 인텔릭트 팝업의 API Key 요청에 응답
    useEffect(() => listenIntellectApiKeyRequest(), []);

    useEffect(() => {
        if (process.env.NEXT_PUBLIC_PERF_MODE !== '1') return;
        (async () => {
            const { worker } = await import('@/tests/mocks/browser');
            await worker.start({
                onUnhandledRequest: 'bypass', // 모킹 안 한 요청은 그대로 서버로
            });
        })();
    }, []);

    if (!messages || loading) return null;

    console.log(
        'PERF_MODE:',
        process.env.NEXT_PUBLIC_PERF_MODE,
        'DATASET:',
        process.env.NEXT_PUBLIC_PERF_DATASET
    );

    return (
        <TenantProvider>
            <AppProvider>
                <CookiesProvider>
                    <BrandingConfigProvider>
                        <IntlProvider messages={messages} locale={locale}>
                            <CacheProvider value={cache}>
                                <ThemeProvider theme={themeWithPickerLocale}>
                                    <FaviconHead />
                                    <DraggableImageBlocker />
                                    <CssBaseline />
                                    <SnackbarProvider>
                                        <ClipboardProvider>
                                            <DialogProvider>
                                                {isErrorPage ? (
                                                    <Component {...pageProps} />
                                                ) : isLoginPage ? (
                                                    <LoginConfigProvider enabled={usesLoginConfig}>
                                                        <Component {...pageProps} />
                                                    </LoginConfigProvider>
                                                ) : (
                                                    <LoginConfigProvider enabled={usesLoginConfig}>
                                                        <AppWithUser
                                                            Component={Component}
                                                            pageProps={pageProps}
                                                        />
                                                    </LoginConfigProvider>
                                                )}
                                            </DialogProvider>
                                        </ClipboardProvider>
                                    </SnackbarProvider>
                                </ThemeProvider>
                            </CacheProvider>
                        </IntlProvider>
                    </BrandingConfigProvider>
                </CookiesProvider>
            </AppProvider>
        </TenantProvider>
    );
}

function AppWithUser({ Component, pageProps }) {
    const router = useRouter();

    const isUserLoginPage = router.pathname === ROUTES.LOGIN;
    const isAdminLoginPage = router.pathname === ROUTES.ADMIN_LOGIN;
    const isSuperAdminLoginPage = router.pathname === ROUTES.SUPERADMIN_LOGIN;
    const isLoginPage = isUserLoginPage || isAdminLoginPage || isSuperAdminLoginPage;
    const isAdminArea = router.asPath.startsWith(ROUTES.ADMIN_BASE);
    const isSuperAdminArea = router.asPath.startsWith(ROUTES.SUPERADMIN_BASE);
    const isResetPasswordPage = router.pathname.startsWith(ROUTES.RESET_PASSWORD);
    const isMfaPage = router.pathname.startsWith(ROUTES.MFA);
    const isSsoAuthPage = router.pathname.startsWith(ROUTES.SSO);
    const isLoginErrorPage = router.pathname === ROUTES.LOGIN_ERROR;
    const isErrorPage =
        router.asPath.startsWith(ROUTES.ERROR_PAGE) ||
        router.pathname.startsWith(ROUTES.ERROR_PAGE_SHARE);

    const isShareLinkPage = router.pathname === ROUTES.SHARE_LINK && !!router.query.t;
    // 익명 토큰으로 접근하는 공개 공유 페이지 (파일 미리보기, 폴더 탐색)
    const isSharePublicPage =
        router.pathname === ROUTES.SHARE_PUBLIC_PREVIEW ||
        router.pathname === ROUTES.SHARE_PUBLIC_FOLDER;
    // 슈퍼어드민 계정으로 테넌트 어드민에 접속할 때 거치는 임시 인증 페이지
    const isAdminAuthEntryPage = router.pathname === ROUTES.ADMIN_AUTH_ENTRY;
    const redirectParam = isShareLinkPage ? `redirect=${encodeURIComponent(router.asPath)}` : '';
    // 로그인이 필요한 화면 — 로그인 표시가 없으면 useAuth 가 users 로 한 번 확인
    const isProtectedPage = !(
        isShareLinkPage ||
        isSharePublicPage ||
        isLoginPage ||
        isAdminAuthEntryPage ||
        isResetPasswordPage ||
        isMfaPage ||
        isSsoAuthPage ||
        isLoginErrorPage ||
        isErrorPage
    );

    const [user, checkedPath] = useAuth({ probe: isProtectedPage });
    const memoUser = useMemo(() => user, [user]);

    useEffect(() => {
        if (!router.isReady) return;

        // 공유링크/공개 공유 페이지는 자체 인증하므로 auth guard 스킵
        if (isShareLinkPage || isSharePublicPage) return;

        const go = target => {
            // 슈퍼어드민 영역은 테넌트 접두 없는 루트 — 접두 붙은 페이지에서 넘어갈 때는 접두 캐시를 버리기 위해 전체 이동
            if (target.startsWith(ROUTES.SUPERADMIN_BASE) && getTenantPrefix()) {
                window.location.replace(target);
                return;
            }
            if (router.asPath !== target) {
                router.replace(target);
            }
        };

        // 1) 로그인 페이지 동작: 해당 페이지 "역할" 로그인만 본다
        if (isLoginPage) {
            if (isSuperAdminLoginPage) {
                if (isLoggedIn('SUPER_ADMIN')) {
                    go(ROUTES.SUPERADMIN_ACCOUNTS_TENANTS);
                }
                return; // 슈퍼 관리자 로그인 페이지 그대로 렌더
            }
            if (isAdminLoginPage) {
                if (isLoggedIn('ADMIN')) {
                    go(ROUTES.ADMIN_ACCOUNTS_USERS);
                }
                return; // 관리자 로그인 페이지 그대로 렌더
            } else {
                if (isLoggedIn('USER')) {
                    go(ROUTES.HOME);
                }
                return; // 사용자 로그인 페이지 그대로 렌더
            }
        }

        // 로그인 표시가 없으면 현재 경로의 users 확인이 실패로 끝난 뒤에만 로그인 화면으로 (이전 화면의 null 은 무시)
        const requireLogin = (role, to) => {
            if (isLoggedIn(role) || user !== null || checkedPath !== router.asPath) return;
            go(to);
        };

        // 2) 보호 페이지: 현재 영역에 맞는 로그인만 요구 (만료는 api.js 가 재발급 처리)
        if (isSuperAdminArea) {
            requireLogin('SUPER_ADMIN', ROUTES.SUPERADMIN_LOGIN);
            return;
        } else if (isAdminArea) {
            if (isAdminAuthEntryPage) return; // 슈퍼어드민 → 테넌트어드민 임시 인증, 토큰 없이 접근 허용
            requireLogin('ADMIN', ROUTES.ADMIN_LOGIN + (redirectParam ? `?${redirectParam}` : ''));
            return;
        } else {
            if (isResetPasswordPage) return; // 비밀번호 재설정 페이지는 토큰 없이 접근 허용
            if (isMfaPage) return; // MFA 페이지는 MFA 토큰 있으면 접근 허용
            if (isSsoAuthPage) return; // SSO 로그인 관련 페이지는 토큰 없이 접근 허용
            if (isLoginErrorPage) return; // 로그인 오류 페이지는 토큰 없이 접근 허용
            if (isErrorPage) return; // 일반 오류 페이지는 토큰 없이 접근 허용
            requireLogin('USER', ROUTES.LOGIN + (redirectParam ? `?${redirectParam}` : ''));
        }
    }, [
        router.isReady,
        router.asPath,
        isLoginPage,
        isAdminLoginPage,
        isAdminArea,
        redirectParam,
        user,
        checkedPath,
    ]);

    // 렌더링 분기 (훅 선언 이후)

    // useAuth 완료 전: 렌더 보류
    if (user === undefined) {
        return null;
    }

    // 익명 토큰으로 접근하는 공개 공유 페이지: UserContext 제공, AuthLayout 없이 렌더
    // /s/folder는 AnonymousAuthLayout(사이드바 등 익명용 레이아웃) 적용
    if (isSharePublicPage) {
        return (
            <UserContext.Provider value={memoUser}>
                <ClientConfigProvider>
                    <ResourcesProvider>
                        <DialogProvider>
                            {router.pathname === ROUTES.SHARE_PUBLIC_FOLDER ? (
                                <AnonymousAuthLayout>
                                    <Component {...pageProps} />
                                </AnonymousAuthLayout>
                            ) : (
                                <Component {...pageProps} />
                            )}
                        </DialogProvider>
                    </ResourcesProvider>
                </ClientConfigProvider>
            </UserContext.Provider>
        );
    }

    // SSO 인증 페이지/에러 페이지/공유링크 첫 진입 페이지: config 등 불필요한 API 호출 없이 컴포넌트만 렌더
    if (isSsoAuthPage || isErrorPage || isShareLinkPage || isLoginErrorPage) {
        return <Component {...pageProps} />;
    }

    // 인증 불필요 페이지: UserContext 없이 컴포넌트만 렌더
    if (isLoginPage || isResetPasswordPage || isMfaPage || isAdminAuthEntryPage) {
        return (
            <ClientConfigProvider>
                <ResourcesProvider>
                    <DialogProvider>
                        <Component {...pageProps} />
                    </DialogProvider>
                </ResourcesProvider>
            </ClientConfigProvider>
        );
    }

    // 레이아웃 선택
    const renderWithLayout = () => {
        // MCP 딥링크 미리보기(/preview) — 인증·프로바이더는 일반 사용자 페이지와 동일하되
        // AuthLayout(GNB·사이드바)만 빼고 단독 렌더 (미리보기 다이얼로그 전용 화면)
        if (router.pathname === ROUTES.PREVIEW) {
            return <Component {...pageProps} />;
        }

        if (isSuperAdminArea) {
            return (
                <SuperAdminAuthLayout>
                    <LoginSuccessToast />
                    <Component {...pageProps} />
                </SuperAdminAuthLayout>
            );
        }

        if (isAdminArea) {
            return (
                <LicenseProvider>
                    <AdminAuthLayout>
                        <LoginSuccessToast />
                        <Component {...pageProps} />
                    </AdminAuthLayout>
                </LicenseProvider>
            );
        }

        return (
            <AuthLayout>
                <LoginSuccessToast />
                <Component {...pageProps} />
            </AuthLayout>
        );
    };

    // Providers 래핑
    return (
        <UserContext.Provider value={memoUser}>
            <ClientConfigProvider>
                <ResourcesProvider>
                    <QuotaProvider>
                        <UserProfileProvider>
                            <DialogProvider>{renderWithLayout()}</DialogProvider>
                        </UserProfileProvider>
                    </QuotaProvider>
                </ResourcesProvider>
            </ClientConfigProvider>
        </UserContext.Provider>
    );
}
