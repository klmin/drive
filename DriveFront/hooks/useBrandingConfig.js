import { useBrandingConfigContext } from '@/provider/BrandingConfigProvider';

export default function useBrandingConfig() {
    const { tabTitle, serviceName, plan } = useBrandingConfigContext();
    return {
        tabTitle,
        serviceName,
        plan,
    };
}
