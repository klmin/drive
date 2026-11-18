import useBrandingConfig from '@/hooks/useBrandingConfig';
import { useTranslations } from 'next-intl';
import Head from 'next/head';
import { Box } from '@mui/material';
import SuperAdminTenants from '@/components/views/super-admin/accounts/tenants/Tenants';
import palette from '@/theme/palette';

export default function Tenants() {
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
                    backgroundColor: palette.white,
                    overflow: 'hidden',
                    display: 'flex',
                    flexDirection: 'column',
                }}
            >
                <Box
                    sx={{
                        flex: 1,
                        minHeight: 0,
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'flex-start',
                        position: 'relative',
                        backgroundColor: 'inherit',
                        pt: 8,
                        pb: 40,
                        px: {
                            md: 12,
                            xs: 4,
                        },
                        gap: 8,
                    }}
                >
                    <SuperAdminTenants />
                </Box>
            </Box>
        </>
    );
}
