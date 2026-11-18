import Box from '@mui/material/Box';
import React from 'react';
import { useTranslations } from 'next-intl';
import { Typography } from '@mui/material';
import palette from '@/theme/palette';
import Head from 'next/head';
import useBrandingConfig from '@/hooks/useBrandingConfig';
import SystemInfo from '@/components/views/admin/setting/SystemInfo';

export default function AdminGeneral() {
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
                }}
            >
                <Box
                    sx={{
                        display: 'flex',
                        flexDirection: 'column',
                        minHeight: '100vh',
                        alignItems: 'flex-start',
                        position: 'relative',
                        backgroundColor: 'inherit',
                        pt: 8, // 32px
                        pb: 40, // 160px
                        px: {
                            md: 12,
                            xs: 4,
                        },
                        gap: 8, // 32px - 탭바와 콘텐츠 사이 간격
                    }}
                >
                    {/** 타이틀 */}
                    <Box
                        sx={{
                            alignSelf: 'stretch',
                            display: 'flex',
                            flexDirection: 'column',
                            gap: '12px',
                        }}
                    >
                        <Typography sx={{ typography: 'h2', color: palette.text_100 }}>
                            {t('admin.sidebar.systemInfo')}
                        </Typography>
                    </Box>
                    <Box sx={{ alignSelf: 'stretch', display: 'flex', justifyContent: 'center' }}>
                        <SystemInfo />
                    </Box>
                </Box>
            </Box>
        </>
    );
}
