import { useRouter } from '@/hooks/useRouter';
import useDevice from './useDevice';
import { tenantHref } from '@/utils/tenantPrefix';

const useNavigate = () => {
    const router = useRouter();
    const { isMobile } = useDevice();

    const navigate = url => {
        // 모바일에서는 새 페이지로 이동, 데스크탑에서는 SPA 방식으로 라우팅
        if (isMobile) {
            window.location.href = tenantHref(url);
        } else {
            router.push(url);
        }
    };

    return navigate;
};

export default useNavigate;
