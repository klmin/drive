import Box from '@mui/material/Box';
import { Typography } from '@mui/material';
import { useRouter } from '@/hooks/useRouter';
import { useTranslations } from 'next-intl';
import { useEffect } from 'react';
import Head from 'next/head';
import Image from 'next/image';
import useBrandingConfig from '@/hooks/useBrandingConfig';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import { isLoggedIn, getShareLinkToast, removeShareLinkToast } from '@/utils/authTokens';
import { ROUTES } from '@/constants/routes';

export default function ErrorPage() {
    const router = useRouter();
    const t = useTranslations();
    const { tabTitle } = useBrandingConfig();
    const { showSnackbar } = useSnackbarContext();

    // 공유링크 접근 실패 토스트 (s 페이지에서 sessionStorage에 저장 후 이동)
    useEffect(() => {
        const toastType = getShareLinkToast();
        if (!toastType) return;
        removeShareLinkToast();
        if (toastType === 'ACCESS_FAIL') {
            showSnackbar(t('share.access.fail'), 'error');
        } else if (toastType === 'TOO_MANY_REQUESTS') {
            showSnackbar(t('share.access.tooManyRequests'), 'error');
        }
    }, []);

    return (
        <>
            <Head>
                <title>{t('meta.common', { tabTitle })}</title>
            </Head>
            <Box
                sx={{
                    width: '100%',
                    height: '100%',
                    minHeight: '100vh',
                    paddingBottom: '80px',
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'center',
                    alignItems: 'center',
                    gap: '40px',
                    '@media (max-width: 360px)': {
                        paddingBottom: 0,
                        px: '16px',
                    },
                }}
            >
                <Box
                    sx={{
                        width: 360,
                        px: '12px',
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                        gap: '16px',
                        '@media (max-width: 360px)': {
                            width: '100%',
                        },
                    }}
                >
                    <Image src="/error/img-img-error.png" alt="error" width={120} height={120} />

                    <Box
                        sx={{
                            alignSelf: 'stretch',
                            display: 'flex',
                            flexDirection: 'column',
                            alignItems: 'center',
                            gap: '52px',
                        }}
                    >
                        {/* title + message */}
                        <Box
                            sx={{
                                alignSelf: 'stretch',
                                display: 'flex',
                                flexDirection: 'column',
                                alignItems: 'center',
                                gap: '16px',
                            }}
                        >
                            <Typography
                                sx={{
                                    alignSelf: 'stretch',
                                    textAlign: 'center',
                                    color: '#3A3D49',
                                    fontSize: '20px',
                                    fontWeight: 600,
                                    lineHeight: '30px',
                                    wordBreak: 'break-word',
                                }}
                            >
                                {t('user.shareLink.title')}
                            </Typography>
                            <Typography
                                sx={{
                                    alignSelf: 'stretch',
                                    textAlign: 'center',
                                    color: 'rgba(58, 61, 73, 0.80)',
                                    fontSize: '15px',
                                    fontWeight: 400,
                                    lineHeight: '22px',
                                    wordBreak: 'break-word',
                                    whiteSpace: 'pre-line',
                                }}
                            >
                                {t('user.shareLink.desc')}
                            </Typography>
                        </Box>

                        {/* button */}
                        <Box
                            onClick={() => {
                                router.push(isLoggedIn('USER') ? ROUTES.DRIVE : ROUTES.LOGIN);
                            }}
                            sx={{
                                width: 336,
                                height: 40,
                                '@media (max-width: 360px)': {
                                    width: '100%',
                                },
                                py: '8px',
                                bgcolor: 'white',
                                borderRadius: '4px',
                                outline: '1px solid rgba(58, 61, 73, 0.12)',
                                display: 'flex',
                                justifyContent: 'center',
                                alignItems: 'center',
                                gap: '10px',
                                cursor: 'pointer',
                                visibility: router.query.reason === 'access' ? 'hidden' : 'visible',
                                '&:hover': {
                                    bgcolor: 'rgba(58, 61, 73, 0.04)',
                                },
                            }}
                        >
                            <Typography
                                sx={{
                                    textAlign: 'center',
                                    color: '#3A3D49',
                                    fontSize: '14px',
                                    fontWeight: 400,
                                }}
                            >
                                {t('user.shareLink.goToDrive')}
                            </Typography>
                        </Box>
                    </Box>
                </Box>
            </Box>
        </>
    );
}
