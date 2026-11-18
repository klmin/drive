'use client';

import { useEffect, useState } from 'react';
import { useRouter } from '@/hooks/useRouter';
import { Box, Stack, Typography } from '@mui/material';
import Image from 'next/image';
import palette from '@/theme/palette';
import { useTranslations } from 'next-intl';
import useLoginConfig from '@/hooks/useLoginConfig';
import { ROUTES } from '@/constants/routes';

export default function LoginError() {
    const t = useTranslations();
    const router = useRouter();
    const { loginConfig, refetchLoginConfig } = useLoginConfig();
    const [count, setCount] = useState(5);
    const [storedRedirectUrl, setStoredRedirectUrl] = useState('');

    useEffect(() => {
        const url = sessionStorage.getItem('loginErrorRedirectUrl') || '';
        setStoredRedirectUrl(url);
    }, []);

    // sessionStorage는 새로고침 직후 등 loginConfig 로딩 전 사용할 임시값이고,
    // loginConfig가 로드되면 항상 그 값을 우선한다 (sessionStorage 유실/불일치 시 /login <-> /login/error 무한 루프 방지)
    const errorRedirectUrl = loginConfig?.errorRedirectUrl || storedRedirectUrl;

    useEffect(() => {
        if (count <= 0) {
            sessionStorage.removeItem('loginErrorRedirectUrl');
            if (errorRedirectUrl) {
                window.location.href = errorRedirectUrl;
            } else {
                refetchLoginConfig();
                router.replace(ROUTES.LOGIN);
            }
            return;
        }
        const timer = setTimeout(() => setCount(prev => prev - 1), 1000);
        return () => clearTimeout(timer);
    }, [count, errorRedirectUrl]);

    return (
        <Box
            sx={{
                width: '100%',
                height: '100vh',
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'center',
                alignItems: 'center',
                pb: '80px',
                '@media (max-width: 360px)': {
                    pb: 0,
                    px: '16px',
                    py: '211px',
                },
                bgcolor: palette.white,
                overflow: 'hidden',
            }}
        >
            <Stack
                alignItems="center"
                gap="16px"
                sx={{
                    width: 360,
                    px: '12px',
                    '@media (max-width: 360px)': {
                        width: '100%',
                    },
                }}
            >
                {/* 일러스트 */}
                <Box sx={{ width: 120, height: 120, position: 'relative' }}>
                    <Image
                        src="/login/img-login-error.png"
                        alt="login error"
                        fill
                        style={{ objectFit: 'contain' }}
                        priority
                    />
                </Box>

                <Stack alignItems="center" gap="52px">
                    {/* 텍스트 */}
                    <Stack alignItems="center" gap="16px" sx={{ alignSelf: 'stretch' }}>
                        <Typography
                            sx={{
                                width: 360,
                                fontSize: 20,
                                fontWeight: 600,
                                lineHeight: '30px',
                                color: palette.text_100,
                                textAlign: 'center',
                                '@media (max-width: 360px)': {
                                    width: '100%',
                                },
                            }}
                        >
                            {t('user.login.errorPage.title')}
                        </Typography>
                        <Typography
                            sx={{
                                width: 336,
                                fontSize: 15,
                                fontWeight: 400,
                                lineHeight: '22px',
                                color: palette.text_80,
                                textAlign: 'center',
                                '@media (max-width: 360px)': {
                                    width: '100%',
                                },
                                whiteSpace: 'pre-line',
                            }}
                        >
                            {t('user.login.errorPage.body')}
                        </Typography>
                    </Stack>

                    {/* 카운트다운 */}
                    <Stack direction="row" alignItems="center" gap="8px">
                        <Box sx={{ width: 20, height: 20, position: 'relative' }}>
                            <Image
                                src="/login/icon-ic-login-error-timer.svg"
                                alt="timer"
                                fill
                                style={{ objectFit: 'contain' }}
                            />
                        </Box>
                        <Typography
                            sx={{
                                fontSize: 14,
                                fontWeight: 600,
                                lineHeight: '20px',
                                color: palette.text_40,
                                textAlign: 'center',
                            }}
                        >
                            {t('user.login.errorPage.bodyAfter', { count })}
                        </Typography>
                    </Stack>
                </Stack>
            </Stack>
        </Box>
    );
}
