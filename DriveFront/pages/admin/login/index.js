import Image from 'next/image';
import StyledTextField from '@/components/common/input/StyledTextField';
import Box from '@mui/material/Box';
import { Container, Typography, IconButton, InputAdornment, Toolbar } from '@mui/material';
import React, { useCallback, useEffect, useState } from 'react';
import StyledButton from '@/components/common/button/StyledButton';
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined';
import VisibilityOffOutlinedIcon from '@mui/icons-material/VisibilityOffOutlined';
import typography from '@/theme/typography';
import palette from '@/theme/palette';
import api from '@/utils/api';
import { useRouter } from '@/hooks/useRouter';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import { useTranslations } from 'next-intl';
import { useDialog } from '@/provider/DialogProvider';
import Head from 'next/head';
import {
    saveAuth,
    clearAuth,
    isLoggedIn,
    isApiKeyLogin,
    setLoginSuccessSession,
    savePasswordChangeRequired,
    removePasswordChangeRequired,
} from '@/utils/authTokens';
import useBrandingConfig from '@/hooks/useBrandingConfig';
import { getLicenseBlockStatus, isAuthGracePeriod } from '@/utils/license';
import { ROUTES } from '@/constants/routes';
import { tenantHref } from '@/utils/tenantPrefix';
import { isEmbedded } from '@/utils/apiKeySession';

// 라이선스 상태별 알림 다이얼로그 i18n 키 (모든 상태에서 로그인은 허용됨)
const LICENSE_DIALOG_CONFIG = {
    // 미발급
    NOT_ISSUED: {
        titleKey: 'admin.noLicenseDialog.title',
        descKey: 'admin.noLicenseDialog.desc',
    },
    // 시트수 초과
    SEAT_EXCEEDED: {
        titleKey: 'licenseDialog.seatExceeded.title',
        descKey: 'licenseDialog.seatExceeded.message',
    },
    // 만료
    EXPIRED: {
        titleKey: 'admin.licenseExpiredDialog.title',
        descKey: 'admin.licenseExpiredDialog.desc',
    },
    // 인증 실패 (30일 유예 중 포함)
    AUTH_FAILED: {
        titleKey: 'admin.licenseAuthFailDialog.title',
        descKey: 'admin.licenseAuthFailDialog.desc',
    },
};

export default function AdminLogin() {
    const t = useTranslations();
    const router = useRouter();
    const { openDialog } = useDialog();
    const { tabTitle, serviceName } = useBrandingConfig();

    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [showPassword, setShowPassword] = useState(false);
    const [count, setCount] = useState(0);
    const [error, setError] = useState('');
    const [defaultRoute, setDefaultRoute] = useState(ROUTES.ADMIN_ACCOUNTS_USERS);
    const [redirect, setRedirect] = useState('');

    const { showSnackbar } = useSnackbarContext();

    const login = useCallback(async () => {
        try {
            if (!email || !password) {
                return showSnackbar(t('Validation.RequiredLoginFields'), 'error');
            }
            const res = await api.Post('auth/admin/login', {
                userId: email,
                encPwd: password,
            });

            // 요청 수 제한(429) — 잠시 뒤 다시
            if (res?.errorCode === 'REQUEST_013') {
                return showSnackbar(t('loginToast.tooManyRequests'), 'error');
            }

            if (res?.status > 399) {
                if (res?.status === 403) {
                    return setError(t('loginToast.fail'));
                }

                return showSnackbar(t('user.login.toast.fail'), 'error');
            }

            if (res?.data) {
                const result = await api.Get('config/license-status');

                let licenseStatus = getLicenseBlockStatus(result);
                // 미등록 안내는 최초 로그인 다음 로그인부터
                if (licenseStatus === 'NOT_ISSUED' && res?.data?.isFirstLogin) {
                    licenseStatus = null;
                }
                // 차단 전 인증 실패 유예 중이면 인증 실패로 안내 (authStatus 는 admins/license 에만 있음)
                if (!licenseStatus && result?.data?.state) {
                    const licenseRes = await api.Get('admins/license');
                    if (isAuthGracePeriod(licenseRes?.data)) licenseStatus = 'AUTH_FAILED';
                }

                // 응답 없음(NO_RESPONSE)은 안내 없이 로그인
                const cfg = LICENSE_DIALOG_CONFIG[licenseStatus];
                if (cfg) {
                    await openDialog('alert', {
                        title: t(cfg.titleKey),
                        description: t(cfg.descKey),
                        cancelButtonName: '',
                    });
                }

                // 쿠키 저장
                saveAuth('ADMIN', res.data);

                // 비밀번호 변경 유도 세션 설정 (로그인 성공 시점에 설정)
                if (res?.data?.hasChangedPassword === false) {
                    savePasswordChangeRequired();
                } else {
                    removePasswordChangeRequired();
                }

                setLoginSuccessSession(res.data.userLanguage);

                const target =
                    redirect ||
                    (defaultRoute && typeof defaultRoute === 'string'
                        ? defaultRoute
                        : ROUTES.ADMIN_ACCOUNTS_USERS);
                if (router.asPath !== target) {
                    window.location.replace(tenantHref(target));
                }
            }
        } catch (err) {
            const errorCode = err?.response?.data?.errorCode;

            // 로그인 실패
            if (errorCode === 'AUTH_006') {
                showSnackbar(t('user.login.toast.fail'), 'error');
            }

            // 차단된 국가나 IP 로그인 실패 시 토스트 표시
            else if (errorCode === 'ACCESS_001' || errorCode === 'ACCESS_005') {
                sessionStorage.removeItem('accessError');
                showSnackbar(t('adminAccessRestriction.accessDeniedMessage'), 'error');
            } else {
                console.log(err);
                showSnackbar(t('loginToast.fail'), 'error');
            }
        }
    }, [email, password, t, openDialog, showSnackbar]);

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
        if (!router.isReady) return;

        // developers iframe 은 로그인 폼 대신 API Key 진입 화면으로
        if (isEmbedded()) {
            const q = router.query.redirect;
            const to =
                typeof q === 'string' && q.startsWith('/')
                    ? `?redirect=${encodeURIComponent(q)}`
                    : '';
            router.replace(ROUTES.ADMIN_LOGIN_API_KEY + to);
            return;
        }

        // API Key 세션으로 직접 진입 시 세션 정리 후 로그인 폼 노출
        if (isApiKeyLogin('ADMIN')) {
            clearAuth('ADMIN');
            return;
        }

        if (!isLoggedIn('ADMIN')) return;

        const target =
            redirect ||
            (defaultRoute && typeof defaultRoute === 'string'
                ? defaultRoute
                : ROUTES.ADMIN_ACCOUNTS_USERS);

        if (router.asPath !== target) {
            window.location.replace(tenantHref(target));
        }
    }, [router.isReady, router.asPath, defaultRoute, redirect]);

    useEffect(() => {
        if (!email || !password) setError('');
    }, [email, password]);

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
                <title>{t('meta.admin', { tabTitle })}</title>
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
                        <Box
                            sx={{
                                alignSelf: 'stretch',
                                display: 'flex',
                                flexDirection: 'column',
                                alignItems: 'center',
                                gap: '14px',
                            }}
                        >
                            <Typography
                                sx={{
                                    alignSelf: 'stretch',
                                    textAlign: 'center',
                                    fontSize: '22px',
                                    fontWeight: 600,
                                    color: palette.text_100,
                                    fontFamily: typography.fontFamily,
                                    lineHeight: '24px',
                                }}
                            >
                                {serviceName}
                            </Typography>
                            <Image
                                src="/admin/ci-bi-favicon-admin-badge.svg"
                                alt="admin badge"
                                width={52}
                                height={16}
                            />
                        </Box>
                    </Box>

                    {/* Form */}
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
                        </Box>
                    </Box>
                </Box>
            </Container>
        </>
    );
}

AdminLogin.noLayout = true;
