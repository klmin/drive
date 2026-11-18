import Box from '@mui/material/Box';
import React from 'react';
import { useTranslations } from 'next-intl';
import { Stack, Typography } from '@mui/material';
import palette from '@/theme/palette';
import Head from 'next/head';
import AccessControll from '@/components/views/admin/security/access-control/AccessControll';
import useBrandingConfig from '@/hooks/useBrandingConfig';

export default function AdminSecurity() {
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
                        px: {
                            md: 12,
                            xs: 4,
                        },
                        pt: {
                            xs: 7,
                            md: 5,
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
                                content: '""',
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
                            {t('AdminSidebar.AccessControl')}
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
                        px: {
                            md: 12,
                            xs: 4,
                        },
                    }}
                >
                    <AccessControll />
                </Box>
            </Box>
        </>
    );
}
