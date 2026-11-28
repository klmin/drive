import Image from 'next/image';
import StyledTextField from '@/components/common/input/StyledTextField';
import Box from '@mui/material/Box';
import { CircularProgress, Container, Typography, IconButton, InputAdornment } from '@mui/material';
import React, { useCallback, useEffect, useState } from 'react';
import StyledButton from '@/components/common/button/StyledButton';
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined';
import VisibilityOffOutlinedIcon from '@mui/icons-material/VisibilityOffOutlined';
import typography from '@/theme/typography';
import palette from '@/theme/palette';
import axios from 'axios';
import api, { getBaseURL, getDomain } from '@/utils/api';
import {
    saveAuth,
    isLoggedIn,
    saveMfaSetup,
    saveMfaVerify,
    saveOAuth2LoginPending,
    removeOAuth2LoginPending,
} from '@/utils/authTokens';
import { useRouter } from '@/hooks/useRouter';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import { useTranslations } from 'next-intl';
import { useDialog } from '@/provider/DialogProvider';
import Head from 'next/head';
import useBrandingConfig from '@/hooks/useBrandingConfig';
import useLoginConfig from '@/hooks/useLoginConfig';
import { ROUTES } from '@/constants/routes';
import { tenantHref } from '@/utils/tenantPrefix';

/**
 * MCP OAuth 연동용 로그인 페이지 (C안 브리지 — MCP_OAUTH_BRIDGE_FLOW.md).
 *
 * 진입 경로: MCP 클라이언트가 연 브라우저가 백엔드 /oauth2/authorize에 도착
 * → 미인증이면 백엔드가 이 라우트로 302 (원 요청은 백엔드 세션에 저장됨).
 *
 * 이 페이지가 하는 일:
 * 1. 이미 로그인돼 있으면(액세스 토큰 쿠키 존재) 화면 없이 바로 브리지 호출
 * 2. 아니면 기존 /login과 동일하게 테넌트 설정(local/oidc/jwt)대로 로그인 UI 렌더
 * 3. 어떤 방식이든 토큰 확보 후 POST /oauth2/session(브리지) → 응답의 redirectUrl
 *    (원본 authorize URL)로 브라우저 이동 — 이후 코드 발급→MCP 콜백은 백엔드/브라우저가 처리
 *
 * 기존 /login과의 차이는 "성공 후 홈 이동" 대신 "브리지 후 authorize 복귀" 뿐이다.
 */
export default function OAuth2Login() {
    const t = useTranslations();
    const router = useRouter();
    const { openDialog } = useDialog();
    const { tabTitle, serviceName } = useBrandingConfig();
    const { loginConfig, isLocal, isOidc, isJwt } = useLoginConfig();

    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [showPassword, setShowPassword] = useState(false);
    const [error, setError] = useState('');
    // 이미 로그인된 사용자의 무화면 브리지 시도 중에는 폼 대신 스피너 렌더
    const [bridging, setBridging] = useState(true);

    const { showSnackbar } = useSnackbarContext();

    /**
     * Drive 사용자 로그인 → 백엔드 AS 세션 교환 후 원본 authorize URL로 이동.
     * withCredentials 필수 — 사용자 로그인 쿠키와 authorize 302 때 발급된 백엔드 세션 쿠키를 실어야
     * 저장된 authorize 요청을 찾고, 응답으로 확립된 로그인 세션 쿠키를 받는다.
     * 서버는 X-Drive-Role: USER 일 때만 사용자 로그인 쿠키를 읽는다.
     */
    const bridge = useCallback(async () => {
        try {
            const res = await axios.post(`${getDomain()}/oauth2/session`, null, {
                headers: { 'X-Drive-Role': 'USER' },
                withCredentials: true,
            });
            // 브라우저 네비게이션이어야 이후 백엔드 302(→ MCP 콜백)를 따라간다 (XHR 금지)
            window.location.replace(res.data.redirectUrl);
        } catch (err) {
            const errorCode = err?.response?.data?.error;
            if (errorCode === 'no_pending_authorization') {
                // authorize를 거치지 않고 직접 진입 — 연동 요청이 없으므로 홈으로
                router.replace(ROUTES.HOME);
                return;
            }
            // invalid_token(만료 등): 로그인 폼으로 전환해 재로그인 유도
            setBridging(false);
            if (errorCode !== 'invalid_token') {
                showSnackbar(t('loginToast.fail'), 'error');
            }
        }
    }, [router, showSnackbar, t]);

    // 진입 시: SSO 복귀 플래그 정리 + 이미 로그인돼 있으면 무화면 브리지
    useEffect(() => {
        if (!router.isReady) return;
        removeOAuth2LoginPending();

        if (isLoggedIn('USER')) {
            bridge();
        } else {
            setBridging(false);
        }
    }, [router.isReady]);

    // 기존 /login의 local 로그인과 동일 API — 성공 후 홈 이동 대신 브리지 호출
    const login = useCallback(async () => {
        try {
            if (!email || !password) {
                return showSnackbar(t('Validation.RequiredLoginFields'), 'error');
            }
            const res = await api.Post('auth/login', {
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
                return showSnackbar(res.message, 'error');
            }

            if (res?.code === 200) {
                saveAuth('USER', res.data);
                setBridging(true);
                await bridge();
            }
        } catch (err) {
            const code = err?.response?.data?.code; // 428이면 mfa인증 필요
            const errorCode = err?.response?.data?.errorCode;
            const data = err?.response?.data?.data;
            const mfaChallengeType = data?.mfaChallengeType;
            const token = data?.token;

            if (errorCode === 'AUTH_006') {
                showSnackbar(t('user.login.toast.fail'), 'error');
            } else if (errorCode === 'AUTH_014') {
                showSnackbar(t('user.login.toast.failWarning'), 'error');
            } else if (errorCode === 'AUTH_008') {
                openDialog('alert', {
                    title: t('user.login.accountLockedDialog.title'),
                    description: t('user.login.accountLockedDialog.desc'),
                    buttonName: t('common.button.confirm'),
                    cancelButtonName: '',
                });
            } else if (errorCode === 'ACCESS_001' || errorCode === 'ACCESS_005') {
                sessionStorage.removeItem('accessError');
                showSnackbar(t('adminAccessRestriction.accessDeniedMessage'), 'error');
            } else if (code === 428 && mfaChallengeType) {
                // 기존 MFA 페이지로 이동 — pending 플래그를 남겨두면 MFA 성공 후
                // 홈 대신 이 페이지로 복귀해 무화면 브리지로 이어진다 (SSO 콜백과 동일 패턴)
                switch (mfaChallengeType) {
                    case 'setup':
                        saveOAuth2LoginPending();
                        saveMfaSetup(token);
                        window.location.replace(tenantHref(ROUTES.MFA_SETUP));
                        break;
                    case 'verify':
                        saveOAuth2LoginPending();
                        saveMfaVerify(token, data.challengeType);
                        window.location.replace(tenantHref(ROUTES.MFA_VERIFY));
                        break;
                    default:
                        showSnackbar(t('loginToast.fail'), 'error');
                }
            } else {
                showSnackbar(t('loginToast.fail'), 'error');
            }
        }
    }, [email, password, t, openDialog, showSnackbar, bridge]);

    // 기존 /login의 SSO 로그인과 동일 — IdP 왕복 후 /sso/callback이
    // 저장된 플래그를 보고 홈 대신 이 페이지로 복귀시킨다 (복귀 후 무화면 브리지)
    const ssoLogin = async () => {
        try {
            saveOAuth2LoginPending();
            const res = await fetch(
                `${getBaseURL()}/auth/sso/${loginConfig?.ssoProviderSeq}/login`,
                { redirect: 'manual' }
            );
            if (res.type === 'opaqueredirect' || res.ok) {
                window.location.href = `${getBaseURL()}/auth/sso/${loginConfig?.ssoProviderSeq}/login`;
                return;
            }
            removeOAuth2LoginPending();
            showSnackbar(t('loginToast.fail'), 'error');
        } catch {
            removeOAuth2LoginPending();
            showSnackbar(t('user.login.toast.fail'), 'error');
        }
    };

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
                {bridging ? (
                    <CircularProgress size={32} />
                ) : (
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
                                                        onClick={() =>
                                                            setShowPassword(prev => !prev)
                                                        }
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

                        {/* JWT(포털 핸드오프) 테넌트: 이 화면에서 직접 로그인 불가 —
                            포털에서 먼저 로그인한 뒤 MCP 연동을 다시 시도해야 한다 */}
                        {isJwt && (
                            <Typography
                                sx={{
                                    ...typography.body5,
                                    color: palette.text_80,
                                    fontFamily: typography.fontFamily,
                                    px: '12px',
                                    textAlign: 'center',
                                }}
                            >
                                {t('loginToast.needLogin')}
                            </Typography>
                        )}
                    </Box>
                )}
            </Container>
        </>
    );
}

OAuth2Login.noLayout = true;
