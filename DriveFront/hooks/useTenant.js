import { useTenantContext } from '@/provider/TenantProvider';

export default function useTenant() {
    const { prefix, slug } = useTenantContext();
    return {
        prefix,
        slug,
    };
}
