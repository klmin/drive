import Box from '@mui/material/Box';
import React, { useEffect, useRef } from 'react';
import { useState } from 'react';
import { useTranslations } from 'next-intl';
import { Stack, Typography } from '@mui/material';
import palette from '@/theme/palette';
import Head from 'next/head';
import Intellect from '@/components/views/admin/Intellect/Intellect';
import useBrandingConfig from '@/hooks/useBrandingConfig';

export default function AdminIntellect() {
    const t = useTranslations();
    const { tabTitle, serviceName } = useBrandingConfig();

    const [loading, setLoading] = useState(true);
    const tableContainerRef = useRef(null);

    const hiddenOverflow = () => {
        const container = document.querySelector('#container');
        const generalContainer = document.querySelector('#general-container');

        if (generalContainer.scrollHeight > container.clientHeight) {
            container.style.overflowY = 'scroll';
        } else {
            container.scrollTo(0, 0);
            container.style.overflowY = 'hidden';
        }
    };

    useEffect(() => {
        hiddenOverflow();
        window.addEventListener('resize', hiddenOverflow);

        return () => {
            window.removeEventListener('resize', hiddenOverflow);
        };
    }, [loading]);

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
                ref={tableContainerRef}
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
                            {t('AdminSidebar.Intellect')}
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
                    <Intellect loading={loading} setLoading={setLoading} />
                </Box>
            </Box>
        </>
    );
}
