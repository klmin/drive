import Image from 'next/image';
import StyledTextField from '@/components/common/input/StyledTextField';
import Box from '@mui/material/Box';
import { Container, Typography, IconButton, InputAdornment, Toolbar, Link } from '@mui/material';
import React, { useCallback, useEffect, useState } from 'react';
import StyledButton from '@/components/common/button/StyledButton';
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined';
import VisibilityOffOutlinedIcon from '@mui/icons-material/VisibilityOffOutlined';
import typography from '@/theme/typography';
import palette from '@/theme/palette';
import api, { getBaseURL } from '@/utils/api';
import { tenantHref } from '@/utils/tenantPrefix';
import {
    saveAuth,
    clearAuth,
    isLoggedIn,
    isApiKeyLogin,
    saveMfaSetup,
    saveMfaVerify,
    setLoginSuccessSession,
    getSsoLoginError,
    removeSsoLoginError,
    getShareLinkToast,
    removeShareLinkToast,
} from '@/utils/authTokens';
import { useRouter } from '@/hooks/useRouter';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import { useTranslations } from 'next-intl';
import { useDialog } from '@/provider/DialogProvider';
import Head from 'next/head';
import { getLicenseBlockStatus } from '@/utils/license';
import useBrandingConfig from '@/hooks/useBrandingConfig';
import { ROUTES } from '@/constants/routes';
import { isEmbedded } from '@/utils/apiKeySession';

// 로그인 차단 다이얼로그 i18n 키 — 미등록 외 차단은 원인을 드러내지 않고 만료로 안내
const LICENSE_BLOCK_DIALOG = {
    NOT_ISSUED: {
        titleKey: 'licenseDialog.unregistered.title',
        descKey: 'licenseDialog.unregistered.message',
    },
    // 응답 없음 — 등록 여부를 확인할 수 없으므로 미등록으로 안내
    NO_RESPONSE: {
        titleKey: 'licenseDialog.unregistered.title',
        descKey: 'licenseDialog.unregistered.message',
    },
    BLOCKED: {
        titleKey: 'common.label.licenseExpired',
        descKey: 'licenseDialog.expired.message',
    },
};
import useLoginConfig from '@/hooks/useLoginConfig';

export default function Login() {
    const t = useTranslations();
    const router = useRouter();
    const { openDialog } = useDialog();
    const { tabTitle, serviceName } = useBrandingConfig();
    const { loginConfig, isLocal, isOidc, isJwt, passwordResetEnabled } = useLoginConfig();

    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [showPassword, setShowPassword] = useState(false);
    const [count, setCount] = useState(0);
    const [error, setError] = useState('');
    const [defaultRoute, setDefaultRoute] = useState('');
    const [redirect, setRedirect] = useState('');

    const { showSnackbar } = useSnackbarContext();

    const login = useCallback(async () => {
        try {
            if (!email || !password) {
                return showSnackbar(t('Validation.RequiredLoginFields'), 'error');
            }
            const res = await api.Post('auth/login', {
                userId: email,
                encPwd: password,
            });

            if (res?.status > 399) {
                if (res?.status === 403) {
                    return setError(t('loginToast.fail'));
                }

                return showSnackbar(res.message, 'error');
            }

            if (res?.code === 200) {
                const result = await api.Get(`config/license-status`);

                const blockStatus = getLicenseBlockStatus(result);
                if (blockStatus) {
                    // 로그인 API 가 내려준 로그인 쿠키를 지운다
                    await api.Post('auth/logout', null, null, { __roleType: 'USER' });
                    const blockCfg =
                        LICENSE_BLOCK_DIALOG[blockStatus] || LICENSE_BLOCK_DIALOG.BLOCKED;
                    await openDialog('alert', {
                        title: t(blockCfg.titleKey),
                        description: t(blockCfg.descKey),
                        cancelButtonName: '',
                    });
                    return; // 로그인 불가
                }

                const dlp = res?.data?.defaultLandingPage;
                setDefaultRoute(dlp === 'my-drive' ? ROUTES.DRIVE : `/${dlp}`);

                // 쿠키 저장
                saveAuth('USER', res.data);

                const target =
                    redirect ||
                    (defaultRoute && typeof defaultRoute === 'string' ? defaultRoute : ROUTES.HOME);

                if (router.asPath !== target) {
                    window.location.replace(tenantHref(target));
                }

                setLoginSuccessSession(res.data.userLanguage);
            }
        } catch (err) {
            const code = err?.response?.data?.code; // 428이면 mfa인증 필요
            const errorCode = err?.response?.data?.errorCode;

            const data = err?.response?.data?.data;
            const mfaChallengeType = data?.mfaChallengeType;
            const token = data?.token;

            // 로그인 실패
            if (errorCode === 'AUTH_006') {
                showSnackbar(t('user.login.toast.fail'), 'error');
            }

            // 로그인 시도 횟수 초과 경고 - 1회 더 실패 시 계정 잠김
            else if (errorCode === 'AUTH_014') {
                showSnackbar(t('user.login.toast.failWarning'), 'error');
            }

            // 로그인 시도 횟수 초과로 계정 잠김
            else if (errorCode === 'AUTH_008') {
                openDialog('alert', {
                    title: t('user.login.accountLockedDialog.title'),
                    description: t('user.login.accountLockedDialog.desc'),
                    buttonName: t('common.button.confirm'),
                    cancelButtonName: '',
                });
            }

            // 차단된 국가나 IP 로그인 실패 시 토스트 표시
            else if (errorCode === 'ACCESS_001' || errorCode === 'ACCESS_005') {
                sessionStorage.removeItem('accessError');
                showSnackbar(t('adminAccessRestriction.accessDeniedMessage'), 'error');
            }

            // 로컬 로그인 시 MFA 활성화된 유저 -> OTP 인증 필요
            else if (code === 428 && mfaChallengeType) {
                switch (mfaChallengeType) {
                    case 'setup':
                        saveMfaSetup(token);
                        window.location.replace(tenantHref(ROUTES.MFA_SETUP));
                        break;

                    case 'verify':
                        saveMfaVerify(token, data.challengeType);
                        window.location.replace(tenantHref(ROUTES.MFA_VERIFY));
                        break;
                    default:
                        console.log('local Login 실패 - 428');
                        showSnackbar(t('loginToast.fail'), 'error');
                        window.location.replace(tenantHref(ROUTES.LOGIN));
                }
            } else {
                console.log('jwt Login 실패 - ' + errorCode);
                showSnackbar(t('loginToast.fail'), 'error');
                window.location.replace(tenantHref(ROUTES.LOGIN));
            }
        }
    }, [email, password, t, openDialog, showSnackbar]);

    const ssoLogin = async () => {
        try {
            const res = await fetch(
                `${getBaseURL()}/auth/sso/${loginConfig?.ssoProviderSeq}/login`,
                { redirect: 'manual' }
            );
            if (res.type === 'opaqueredirect') {
                window.location.href = `${getBaseURL()}/auth/sso/${loginConfig?.ssoProviderSeq}/login`;
                return;
            }
            if (!res.ok) {
                // 비활성화된 provider 로그인 하는 경우 등
                showSnackbar(t('loginToast.fail'), 'error');
                router.push(ROUTES.LOGIN_ERROR);
                return;
            }
            window.location.href = `${getBaseURL()}/auth/sso/${loginConfig?.ssoProviderSeq}/login`;
        } catch {
            showSnackbar(t('user.login.toast.fail'), 'error');
            router.push(ROUTES.LOGIN);
        }
    };

    // 리다이렉트 저장
    useEffect(() => {
        if (!router.isReady) return;
        const q = router.query.redirect;
        if (typeof q === 'string' && q.startsWith('/')) {
            setRedirect(q);
        } else {
            setRedirect('');
        }
    }, [router.isReady, router.query.redirect]);

    // 로그인 성공 후 이동
    useEffect(() => {
        if (!router.isReady || isEmbedded()) return;

        // API Key 세션으로 직접 진입 시 세션 정리 후 로그인 폼 노출
        if (isApiKeyLogin('USER')) {
            clearAuth('USER');
            return;
        }

        if (!isLoggedIn('USER')) return;

        sessionStorage.removeItem('treeExpandedState');

        const target =
            redirect ||
            (defaultRoute && typeof defaultRoute === 'string' ? defaultRoute : ROUTES.HOME);

        if (router.asPath !== target) {
            window.location.replace(tenantHref(target));
        }
    }, [router.isReady, router.asPath, defaultRoute, redirect]);

    useEffect(() => {
        if (!email || !password) setError('');
    }, [email, password]);

    // SSO 로그인 에러 토스트 (callback 페이지에서 sessionStorage에 저장 후 이동)
    useEffect(() => {
        const errorCode = getSsoLoginError();
        if (!errorCode) return;
        removeSsoLoginError();
        if (errorCode === 'USER_005' || errorCode === 'SSO_018') {
            showSnackbar(t('user.login.oidc.notRegistered'), 'error');
        } else {
            showSnackbar(t('loginToast.fail'), 'error');
        }
    }, []);

    // 공유링크 로그인 유도 토스트 (s 페이지에서 sessionStorage에 저장 후 이동)
    useEffect(() => {
        const toastType = getShareLinkToast();
        if (!toastType) return;
        removeShareLinkToast();
        if (toastType === 'NEED_LOGIN') {
            showSnackbar(t('loginToast.needLogin'), 'error');
        }
    }, []);

    // jwt 로그인이면 에러 페이지 경유 후 리다이렉트
    // developers iframe 은 로그인 폼 대신 API Key 진입 화면으로
    useEffect(() => {
        if (!router.isReady || !loginConfig) return;
        if (isJwt && loginConfig.errorRedirectUrl) {
            sessionStorage.setItem('loginErrorRedirectUrl', loginConfig.errorRedirectUrl);
            router.replace(ROUTES.LOGIN_ERROR);
            return;
        }
        if (isEmbedded()) {
            const q = router.query.redirect;
            const query =
                typeof q === 'string' && q.startsWith('/')
                    ? `?redirect=${encodeURIComponent(q)}`
                    : '';
            router.replace(ROUTES.LOGIN_API_KEY + query);
        }
    }, [router.isReady, loginConfig]);

    const handleKeyDown = async e => {
        if (e.key === 'Enter') {
            e.preventDefault();
            await login();
        }
    };

    if (isEmbedded()) return null;

    return (
        <>
            <Head>
                <title>{t('meta.common', { tabTitle })}</title>
                <meta property="title" content={serviceName} />
            </Head>
            <Container
                maxWidth
                disableGutters
                sx={{
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'center',
                    alignItems: 'center',
                    minHeight: '100vh',
                    backgroundColor: palette.white,
                    pb: '80px',
                    '@media (max-width: 360px)': {
                        pb: 0,
                        px: '16px',
                        py: '211px',
                    },
                }}
            >
                <Box
                    width="100%"
                    sx={{
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                        maxWidth: 360,
                        gap: '40px',
                    }}
                >
                    {/* LoginHeader */}
                    <Box
                        sx={{
                            display: 'flex',
                            flexDirection: 'column',
                            alignItems: 'center',
                            width: '100%',
                            gap: '16px',
                            px: '12px',
                        }}
                    >
                        <Image
                            src={api.getSymbolLogoImage()}
                            alt={'login'}
                            width={64}
                            height={64}
                            style={{ borderRadius: '16px' }}
                        />
                        <Typography
                            sx={{
                                fontSize: '22px',
                                fontWeight: 600,
                                color: palette.text_100,
                                fontFamily: typography.fontFamily,
                                lineHeight: '24px',
                            }}
                        >
                            {serviceName}
                        </Typography>
                        {isOidc && (
                            <Typography
                                sx={{
                                    ...typography.body5,
                                    color: palette.text_80,
                                    fontFamily: typography.fontFamily,
                                }}
                            >
                                {t('user.login.oidc.desc')}
                            </Typography>
                        )}
                    </Box>

                    {/* Local 로그인 Form */}
                    {isLocal && (
                        <Box
                            width={'100%'}
                            sx={{
                                display: 'flex',
                                flexDirection: 'column',
                                gap: '32px',
                                px: '12px',
                            }}
                        >
                            {/* 인풋 그룹 */}
                            <Box sx={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                                <StyledTextField
                                    type={'text'}
                                    value={email}
                                    onChange={e => setEmail(e.target.value)}
                                    size="small"
                                    variant="outlined"
                                    placeholder={t('login.inputEmail')}
                                    fullWidth
                                    height={48}
                                    onKeyDown={handleKeyDown}
                                    InputProps={{
                                        sx: {
                                            '&.MuiInputBase-root.MuiInputBase-sizeSmall': {
                                                padding: '10px 16px',
                                            },
                                        },
                                    }}
                                />
                                <StyledTextField
                                    type={showPassword ? 'text' : 'password'}
                                    value={password}
                                    onChange={e => setPassword(e.target.value)}
                                    size="small"
                                    variant="outlined"
                                    placeholder={t('common.input.pw')}
                                    fullWidth
                                    height={48}
                                    onKeyDown={handleKeyDown}
                                    error={!!error}
                                    errorMessage={error}
                                    InputProps={{
                                        sx: {
                                            '&.MuiInputBase-root.MuiInputBase-sizeSmall': {
                                                paddingTop: '10px',
                                                paddingBottom: '10px',
                                                paddingInlineStart: '16px !important',
                                                paddingInlineEnd: '16px !important',
                                            },
                                        },
                                        endAdornment: (
                                            <InputAdornment position="end">
                                                <IconButton
                                                    onClick={() => setShowPassword(prev => !prev)}
                                                    sx={{ p: 0 }}
                                                >
                                                    {showPassword ? (
                                                        <VisibilityOutlinedIcon
                                                            size={20}
                                                            fontSize={'small'}
                                                            style={{ opacity: 0.8 }}
                                                        />
                                                    ) : (
                                                        <VisibilityOffOutlinedIcon
                                                            fontSize={'small'}
                                                            style={{ opacity: 0.8 }}
                                                        />
                                                    )}
                                                </IconButton>
                                            </InputAdornment>
                                        ),
                                    }}
                                />
                            </Box>

                            {/* 버튼 그룹 */}
                            <Box
                                sx={{
                                    display: 'flex',
                                    flexDirection: 'column',
                                    alignItems: 'center',
                                    gap: '24px',
                                }}
                            >
                                <StyledButton
                                    sx={{
                                        width: '100%',
                                        height: 48,
                                        backgroundColor: palette.btn_point_bg,
                                        borderRadius: '6px',
                                        '&:hover': { backgroundColor: '#1e2640' },
                                    }}
                                    onClick={login}
                                >
                                    <Typography
                                        sx={{
                                            ...typography.body1,
                                            fontFamily: typography.fontFamily,
                                            color: palette.white,
                                        }}
                                    >
                                        {t('common.action.login')}
                                    </Typography>
                                </StyledButton>

                                {passwordResetEnabled && (
                                    <Box
                                        sx={{
                                            display: 'flex',
                                            justifyContent: 'center',
                                            gap: '8px',
                                        }}
                                    >
                                        <Typography
                                            sx={{
                                                ...typography.body5,
                                                fontFamily: typography.fontFamily,
                                                color: palette.text_80,
                                            }}
                                        >
                                            {t('login.forgotPw')}
                                        </Typography>
                                        <Link
                                            component="button"
                                            type="button"
                                            underline="none"
                                            onClick={() => openDialog('findPassword')}
                                            style={{
                                                ...typography.body5,
                                                fontFamily: typography.fontFamily,
                                                color: palette.text_tab_selected,
                                            }}
                                        >
                                            {t('login.findPassword')}
                                        </Link>
                                    </Box>
                                )}
                            </Box>
                        </Box>
                    )}

                    {/** SSO 로그인 버튼 */}
                    {isOidc && (
                        <Box
                            sx={{
                                width: '100%',
                                display: 'flex',
                                flexDirection: 'column',
                                alignItems: 'center',
                                px: '12px',
                            }}
                        >
                            <StyledButton
                                sx={{
                                    width: '100%',
                                    height: 48,
                                    backgroundColor: palette.white,
                                    borderRadius: '6px',
                                    border: `1px solid ${palette.btn_border}`,
                                }}
                                onClick={ssoLogin}
                            >
                                <Typography
                                    sx={{
                                        ...typography.body4_5,
                                        fontFamily: typography.fontFamily,
                                        color: palette.text_100,
                                    }}
                                >
                                    {t('user.login.oidc.loginBtn')}
                                </Typography>
                            </StyledButton>
                        </Box>
                    )}
                </Box>
            </Container>
        </>
    );
}

Login.noLayout = true;
