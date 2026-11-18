import Box from '@mui/material/Box';
import React from 'react';
import { useTranslations } from 'next-intl';
import { Stack, Typography } from '@mui/material';
import palette from '@/theme/palette';
import Head from 'next/head';
import Branding from '@/components/views/admin/service/branding/Branding';
import useBrandingConfig from '@/hooks/useBrandingConfig';

export default function AdminService() {
    const t = useTranslations();
    const { tabTitle, serviceName } = useBrandingConfig();

    return (
        <>
            <Head>
                <title>{t('meta.admin', { tabTitle })}</title>
                <meta property="title" content={serviceName} />
            </Head>
            <Box
                sx={{
                    height: '100vh',
                    backgroundColor: 'white',
                    overflowY: 'auto',
                    pb: 16,
                }}
            >
                <Box
                    sx={{
                        width: 'var(--admin-main-w)',
                        position: 'fixed',
                        background: 'white',
                        zIndex: 999,
                        pt: {
                            xs: 8, // 32px
                            md: 8, // 32px
                        },
                        px: {
                            md: 12, // 48px
                            xs: 4, // 16px
                        },
                    }}
                >
                    <Stack
                        direction={'row'}
                        alignItems={'flex-end'}
                        gap={1}
                        sx={{
                            position: 'relative',
                            '&:after': {
                                position: 'absolute',
                                bottom: 0,
                                left: 0,
                                width: '100%',
                                height: '1px',
                                backgroundColor: palette.divider_8,
                            },
                        }}
                    >
                        <Typography noWrap variant={'h4_h5'} color={'text_100'}>
                            {t('AdminSidebar.Branding')}
                        </Typography>
                    </Stack>
                </Box>
                <Box
                    sx={{
                        display: 'flex',
                        flexDirection: 'column',
                        minHeight: '100vh',
                        alignItems: 'center',
                        position: 'relative',
                        backgroundColor: 'inherit',
                        pb: 16,
                        pt: {
                            xs: 8, // 32px
                            md: 8, // 32px
                        },
                        px: {
                            md: 12, // 48px
                            xs: 4, // 16px
                        },
                    }}
                >
                    <Branding />
                </Box>
            </Box>
        </>
    );
}
