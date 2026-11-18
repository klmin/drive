import Box from '@mui/material/Box';
import React from 'react';
import { useState } from 'react';
import { useTranslations } from 'next-intl';
import AdminAuthLayout from '@/components/common/layout/AdminAuthLayout';
import Office from '@/components/views/admin/setting/Office';
import { Stack, Typography } from '@mui/material';
import palette from '@/theme/palette';
import Head from 'next/head';
import useBrandingConfig from '@/hooks/useBrandingConfig';

export default function AdminOffice() {
    const t = useTranslations();
    const { tabTitle, serviceName } = useBrandingConfig();

    const [loading, setLoading] = useState(true);
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
                id={'container'}
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
                        gap={6}
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
                            {t('AdminSidebar.Office')}
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
                    <Office loading={loading} setLoading={setLoading} />
                </Box>
            </Box>
        </>
    );
}
