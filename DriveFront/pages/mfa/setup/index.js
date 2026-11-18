import { QRCodeSVG } from 'qrcode.react';
import StyledTextField from '@/components/common/input/StyledTextField';
import { Container, Box, Typography, InputAdornment } from '@mui/material';
import React, { useEffect, useRef, useState } from 'react';
import StyledButton from '@/components/common/button/StyledButton';
import palette from '@/theme/palette';
import typography from '@/theme/typography';
import Head from 'next/head';
import { useTranslations } from 'next-intl';
import {
    getMfaSetup,
    removeMfaSetup,
    saveAuth,
    setLoginSuccessSession,
    getOAuth2LoginPending,
    removeOAuth2LoginPending,
} from '@/utils/authTokens';
import api from '@/utils/api';
import { useRouter } from '@/hooks/useRouter';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import useBrandingConfig from '@/hooks/useBrandingConfig';
import { ROUTES } from '@/constants/routes';

export default function Setup() {
    const t = useTranslations();
    const router = useRouter();
    const { showSnackbar } = useSnackbarContext();
    const { tabTitle, serviceName } = useBrandingConfig();

    const [otpAuthUri, setOtpAuthUri] = useState('');
    const [otpCode, setOtpCode] = useState('');
    const [confirmToken, setConfirmToken] = useState();
    const [timeLeft, setTimeLeft] = useState(0);
    const timerRef = useRef(null);

    const formatTime = seconds => {
        const m = Math.floor(seconds / 60)
            .toString()
            .padStart(2, '0');
        const s = (seconds % 60).toString().padStart(2, '0');
        return `${m}:${s}`;
    };

    const startTimer = expiresIn => {
        clearInterval(timerRef.current);
        setTimeLeft(expiresIn);
        timerRef.current = setInterval(() => {
            setTimeLeft(prev => {
                if (prev <= 1) {
                    clearInterval(timerRef.current);
                    return 0;
                }
                return prev - 1;
            });
        }, 1000);
    };

    const getOtpAuthUri = async () => {
        try {
            const res = await api.Post('auth/mfa/setup', {
                m: 'totp',
                t: getMfaSetup(),
            });
            if (res?.code === 200) {
                setOtpAuthUri(res.data.otpAuthUri);
                setConfirmToken(res.data.confirmToken);
                startTimer(res.data.expiresIn - 2);
            }
        } catch (err) {
            showSnackbar(t('mfaVerify.expiredChallenge'), 'error');
        }
    };

    useEffect(() => () => clearInterval(timerRef.current), []);

    const handleConfirm = async () => {
        try {
            const res = await api.Post('auth/mfa/confirm', {
                m: 'totp',
                c: otpCode,
                t: confirmToken,
            });

            if (res?.code === 200) {
                removeMfaSetup();

                // 쿠키 저장
                saveAuth('USER', res.data);

                setLoginSuccessSession(res.data.userLanguage);

                // MCP OAuth 연동 중 시작된 MFA 설정 — 홈 대신 oauth2-login으로 복귀해
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
            console.log('err?.response', err?.response);
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
    };

    useEffect(() => {
        getOtpAuthUri();
    }, []); // eslint-disable-line react-hooks/exhaustive-deps

    const handleKeyDown = async e => {
        if (e.key === 'Enter') {
            e.preventDefault();
            await handleConfirm();
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
                        display: 'flex',
                        flexDirection: 'column',
                        gap: '24px',
                    }}
                >
                    {/* 타이틀 + QR + 입력 영역 */}
                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                        {/* 타이틀 */}
                        <Typography
                            sx={{
                                textAlign: 'center',
                                color: palette.text_100,
                                ...typography.h4,
                            }}
                        >
                            {t('user.login.mfaSetup.title')}
                        </Typography>

                        {/* QR 코드 + 설명 */}
                        <Box
                            sx={{
                                display: 'flex',
                                flexDirection: 'column',
                                alignItems: 'center',
                                gap: '20px',
                                pt: '32px',
                                pb: '32px',
                            }}
                        >
                            <QRCodeSVG value={otpAuthUri} size={140} bgColor="#FFFFFF" />
                            <Typography
                                sx={{
                                    textAlign: 'center',
                                    color: palette.text_80,
                                    ...typography.body5,
                                }}
                            >
                                {t('mfaSetup.description')}
                            </Typography>
                        </Box>

                        {/* 입력 필드 */}
                        <Box sx={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                            <StyledTextField
                                type="text"
                                value={otpCode}
                                onChange={e => setOtpCode(e.target.value)}
                                placeholder={t('mfaSetup.placeholder')}
                                variant="outlined"
                                fullWidth
                                height={40}
                                onKeyDown={handleKeyDown}
                                InputProps={{
                                    endAdornment: (
                                        <InputAdornment position="end">
                                            <Typography
                                                sx={{
                                                    ...typography.body5,
                                                    color: palette.status_error,
                                                    whiteSpace: 'nowrap',
                                                }}
                                            >
                                                {formatTime(timeLeft)}
                                            </Typography>
                                        </InputAdornment>
                                    ),
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
                    </Box>

                    {/* 확인 버튼 */}
                    <StyledButton
                        color="btn_point_bg"
                        sx={{ width: '100%', height: 40, borderRadius: '4px' }}
                        onClick={handleConfirm}
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
