import useBrandingConfig from '@/hooks/useBrandingConfig';
import { useTranslations } from 'next-intl';
import Head from 'next/head';
import { Box } from '@mui/material';
import SuperAdminShareFeature from '@/components/views/super-admin/service/share/SuperAdminShareFeature';

export default function Share() {
    const t = useTranslations();
    const { tabTitle, serviceName } = useBrandingConfig();

    return (
        <>
            <Head>
                <title>{t('meta.superAdmin', { tabTitle })}</title>
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
                    <SuperAdminShareFeature />
                </Box>
            </Box>
        </>
    );
}
