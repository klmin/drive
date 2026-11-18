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
import Head from 'next/head';
import {
    saveAuth,
    isLoggedIn,
    setLoginSuccessSession,
    savePasswordChangeRequired,
    removePasswordChangeRequired,
} from '@/utils/authTokens';
import useBrandingConfig from '@/hooks/useBrandingConfig';
import { ROUTES } from '@/constants/routes';

export default function SuperAdminLogin() {
    const t = useTranslations();
    const router = useRouter();
    const { tabTitle, serviceName } = useBrandingConfig();

    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [showPassword, setShowPassword] = useState(false);
    const [count, setCount] = useState(0);
    const [error, setError] = useState('');
    const [defaultRoute, setDefaultRoute] = useState(ROUTES.SUPERADMIN_ACCOUNTS_TENANTS);
    const [redirect, setRedirect] = useState('');

    const { showSnackbar } = useSnackbarContext();

    const login = useCallback(async () => {
        try {
            if (!email || !password) {
                return showSnackbar(t('Validation.RequiredLoginFields'), 'error');
            }
            const res = await api.Post('auth/super-admin/login', {
                userId: email,
                encPwd: password,
            });

            if (res?.status > 399) {
                if (res?.status === 403) {
                    return setError(t('loginToast.fail'));
                }
                return showSnackbar(t('user.login.toast.fail'), 'error');
            }

            if (res?.data) {
                // 라이선스 상태(config/license-status)는 테넌트 단위 API 라 슈퍼어드민 로그인에서는 조회하지 않는다.
                // 슈퍼어드민 API 는 테넌트 없이(tenant-less) 호출되므로 그 API 를 부르면 다중 테넌트 배포에서 404 가 났다.

                // 쿠키 저장
                saveAuth('SUPER_ADMIN', res.data);

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
                        : ROUTES.SUPERADMIN_ACCOUNTS_TENANTS);
                if (router.asPath !== target) {
                    window.location.replace(target);
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
    }, [email, password, t, showSnackbar]);

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

        if (!isLoggedIn('SUPER_ADMIN')) return;

        const target =
            redirect ||
            (defaultRoute && typeof defaultRoute === 'string'
                ? defaultRoute
                : ROUTES.SUPERADMIN_ACCOUNTS_TENANTS);

        if (router.asPath !== target) {
            window.location.replace(target);
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

    return (
        <>
            <Head>
                <title>{t('meta.superAdmin', { tabTitle })}</title>
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
                                src="/super-admin/ci-bi-favicon-super-admin-badge.svg"
                                alt="admin badge"
                                width={104}
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

SuperAdminLogin.noLayout = true;
