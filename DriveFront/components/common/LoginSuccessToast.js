import { useEffect } from 'react';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import { useTranslations } from 'next-intl';

export default function LoginSuccessToast() {
    const { showSnackbar } = useSnackbarContext();
    const t = useTranslations();

    useEffect(() => {
        const flag = sessionStorage.getItem('loginSuccess');
        const msg = sessionStorage.getItem('loginSuccessMessage');

        if (flag && msg) {
            showSnackbar(msg, 'success');
            sessionStorage.removeItem('loginSuccess');
            sessionStorage.removeItem('loginSuccessMessage');
        }
    }, []);

    return null;
}
