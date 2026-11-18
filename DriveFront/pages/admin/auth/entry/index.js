import { useEffect } from 'react';
import { useRouter } from '@/hooks/useRouter';
import { Box, CircularProgress } from '@mui/material';
import api from '@/utils/api';
import { saveAuth } from '@/utils/authTokens';
import { ROUTES } from '@/constants/routes';

export default function AdminAuthEntry() {
    const router = useRouter();

    useEffect(() => {
        if (!router.isReady) return;

        const { key } = router.query;
        if (!key) {
            router.replace(ROUTES.ADMIN_LOGIN);
            return;
        }

        const verify = async () => {
            try {
                const res = await api.Post('auth/admin/entry-key/verify', { token: key });
                if (res?.code === 200) {
                    saveAuth('ADMIN', res.data);
                    router.replace(ROUTES.ADMIN_ACCOUNTS_USERS);
                } else {
                    router.replace(ROUTES.ADMIN_LOGIN);
                }
            } catch {
                router.replace(ROUTES.ADMIN_LOGIN);
            }
        };

        verify();
    }, [router.isReady, router.query]);

    return (
        <Box
            sx={{
                display: 'flex',
                justifyContent: 'center',
                alignItems: 'center',
                minHeight: '100vh',
            }}
        >
            <CircularProgress />
        </Box>
    );
}

AdminAuthEntry.noLayout = true;
