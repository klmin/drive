import StyledTextField from '@/components/common/input/StyledTextField';
import { Container, Box, Typography } from '@mui/material';
import React, { useEffect, useState } from 'react';
import StyledButton from '@/components/common/button/StyledButton';
import palette from '@/theme/palette';
import Head from 'next/head';
import { useTranslations } from 'next-intl';
import typography from '@/theme/typography';
import { useRouter } from '@/hooks/useRouter';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import api from '@/utils/api';
import {
    getMfaVerify,
    removeMfaVerify,
    saveAuth,
    setLoginSuccessSession,
    getOAuth2LoginPending,
    removeOAuth2LoginPending,
} from '@/utils/authTokens';
import useBrandingConfig from '@/hooks/useBrandingConfig';
import useLoginConfig from '@/hooks/useLoginConfig';
import { ROUTES } from '@/constants/routes';

export default function Verify() {
    const [otpCode, setOtpCode] = useState('');
    const t = useTranslations();
    const { showSnackbar } = useSnackbarContext();
    const router = useRouter();
    const { tabTitle, serviceName } = useBrandingConfig();
    const { token, type } = getMfaVerify();
    const { loginConfig, isJwt: isJwtLogin, errorRedirectUrl } = useLoginConfig();

    // otpCode 검증
    const handleVerify = async () => {
        if (type === 'totp') {
            try {
                const res = await api.Post('auth/mfa/verify', {
                    t: token,
                    c: otpCode,
                });
                console.log(res?.data);
                // 요청 수 제한(429) — api 공통 처리가 에러를 throw 하지 않고 본문을 돌려준다
                if (res?.errorCode === 'REQUEST_013') {
                    showSnackbar(t('mfaVerify.tooManyRequests'), 'error');
                    return;
                }
                if (res?.code === 200) {
                    // 쿠키 저장
                    saveAuth('USER', res.data);

                    removeMfaVerify();

                    setLoginSuccessSession(res.data.userLanguage);

                    // MCP OAuth 연동 중 시작된 MFA — 홈 대신 oauth2-login으로 복귀해
                    // 브리지(JWT→AS 세션 교환)를 이어간다 (MCP_OAUTH_BRIDGE_FLOW.md)
                    if (getOAuth2LoginPending()) {
                        removeOAuth2LoginPending();
                        router.replace(ROUTES.OAUTH2_LOGIN);
                        return;
                    }

                    // OTP 검증 성공
                    router.push(
                        res?.data?.defaultLandingPage === 'my-drive' ? ROUTES.DRIVE : ROUTES.HOME
                    );
                }
            } catch (err) {
                console.log('err', err);
                const status = err?.response?.data?.status;

                console.log(status);
                if (status === 400) {
                    // OTP 검증 실패 (잘못된 코드)
                    showSnackbar(t('mfaVerify.invalidCode'), 'error');
                } else if (status === 401) {
                    // OTP 검증 실패 (만료된 ct)
                    showSnackbar(t('mfaVerify.expiredChallenge'), 'error');
                } else {
                    showSnackbar(t('mfaVerify.expiredChallenge'), 'error');
                }
            }
        }
    };

    const handleKeyDown = async e => {
        if (e.key === 'Enter') {
            e.preventDefault();
            await handleVerify();
        }
    };

    useEffect(() => {
        if (!token && loginConfig !== null) {
            removeMfaVerify();
            router.replace(isJwtLogin ? `${errorRedirectUrl}` : ROUTES.LOGIN);
        }
    }, [loginConfig]);

    if (!token) return null;

    return (
        <>
            <Head>
                <title>{t('meta.common', { tabTitle })}</title>
                <meta property="title" content={serviceName} />
            </Head>

            <Container
                maxWidth
                sx={{
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'center',
                    alignItems: 'center',
                    minHeight: '100vh',
                    pb: '80px',
                }}
            >
                <Box
                    sx={{
                        width: 400,
                        pt: '32px',
                        pb: '24px',
                        px: '32px',
                        backgroundColor: palette.white,
                        borderRadius: '16px',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: '24px',
                    }}
                >
                    {/* 타이틀 + 설명 + 입력 영역 */}
                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                        {/* 타이틀 */}
                        <Typography
                            sx={{
                                textAlign: 'center',
                                color: palette.text_100,
                                ...typography.h4,
                            }}
                        >
                            {t('user.login.mfa.title')}
                        </Typography>

                        {/* 설명 */}
                        <Typography
                            sx={{
                                textAlign: 'center',
                                color: palette.text_80,
                                ...typography.body5,
                            }}
                        >
                            {t('mfaVerify.description')}
                        </Typography>

                        {/* 입력 필드 */}
                        <StyledTextField
                            type="text"
                            value={otpCode}
                            onChange={e => setOtpCode(e.target.value)}
                            placeholder={t('mfaVerify.placeholder')}
                            variant="outlined"
                            fullWidth
                            height={40}
                            onKeyDown={handleKeyDown}
                            InputProps={{
                                sx: {
                                    '&.MuiInputBase-root.MuiInputBase-sizeSmall': {
                                        borderRadius: '4px',
                                        backgroundColor: `${palette.white} !important`,
                                        color: palette.text_100,
                                        ...typography.body3,
                                    },
                                },
                            }}
                        />
                    </Box>

                    {/* 로그인 버튼 */}
                    <StyledButton
                        color="btn_point_bg"
                        sx={{ width: '100%', height: 40, borderRadius: '4px' }}
                        onClick={handleVerify}
                        disabled={!otpCode}
                    >
                        <Typography
                            sx={{
                                ...typography.body5,
                                color: !otpCode ? palette.text_40 : palette.white,
                            }}
                        >
                            {t('common.action.login')}
                        </Typography>
                    </StyledButton>
                </Box>
            </Container>
        </>
    );
}
