import { useEffect } from 'react';
import { useRouter } from '@/hooks/useRouter';
import { ROUTES } from '@/constants/routes';

export default function NotFound() {
    const router = useRouter();

    useEffect(() => {
        router.replace(ROUTES.ERROR_PAGE);
    }, []);

    return null;
}
