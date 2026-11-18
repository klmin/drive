import Box from '@mui/material/Box';
import React, { useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';
import { Stack, Typography } from '@mui/material';
import palette from '@/theme/palette';
import Head from 'next/head';
import Authentication from '@/components/views/admin/security/authentication/Authentication';
import { useRouter } from '@/hooks/useRouter';
import LoginSettings from '@/components/views/admin/security/authentication/LoginSettings';
import useBrandingConfig from '@/hooks/useBrandingConfig';

const TABS = [
    { key: '2fa', labelKey: 'adminMfa.title', path: '/admin/security/authentication?menu=2fa' },
    {
        key: 'login',
        labelKey: 'adminLogin.title',
        path: '/admin/security/authentication?menu=login',
    },
];

export default function AdminSecurity() {
    const t = useTranslations();
    const router = useRouter();
    const { tabTitle, serviceName } = useBrandingConfig();

    const [menu, setMenu] = useState(router.query.menu || '2fa');

    useEffect(() => {
        if (router.query.menu) {
            setMenu(router.query.menu);
        }
    }, [router.query]);

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
                    overflowY: 'scroll',
                }}
            >
                <Box
                    sx={{
                        display: 'flex',
                        flexDirection: 'column',
                        minHeight: '100vh',
                        alignItems: 'center',
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
                    {/* 탭 바 */}
                    <Stack
                        direction="row"
                        alignItems="center"
                        gap={6} // 24px
                        sx={{
                            alignSelf: 'stretch',
                            borderBottom: `1px solid ${palette.divider_8}`,
                        }}
                    >
                        {TABS.map(({ key, labelKey, path }) => (
                            <Box
                                key={key}
                                onClick={() => router.push(path)}
                                sx={{
                                    height: 30,
                                    pb: 2, // 8px
                                    px: 1, // 4px
                                    mb: '-1px',
                                    borderBottom:
                                        menu === key
                                            ? `2px solid ${palette.btn_point_bg}`
                                            : '2px solid transparent',
                                    cursor: 'pointer',
                                    display: 'flex',
                                    alignItems: 'center',
                                }}
                            >
                                <Typography
                                    noWrap
                                    sx={{
                                        fontSize: '18px',
                                        fontWeight: 600,
                                        color: menu === key ? palette.text_100 : palette.text_60,
                                    }}
                                >
                                    {t(labelKey)}
                                </Typography>
                            </Box>
                        ))}
                    </Stack>

                    {/* 콘텐츠 */}
                    {menu === '2fa' ? <Authentication /> : <LoginSettings />}
                </Box>
            </Box>
        </>
    );
}
