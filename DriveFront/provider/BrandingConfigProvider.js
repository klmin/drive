import { createContext, useContext, useEffect, useState } from 'react';
import api from '@/utils/api';
import { LICENSE_PLAN } from '@/utils/license';

const BrandingConfigContext = createContext(null);

export function BrandingConfigProvider({ children }) {
    const [tabTitle, setTabTitle] = useState(null);
    const [serviceName, setServiceName] = useState(null);
    // 라이선스 플랜(SUB_CATEGORY), 미등록·테넌트 없음은 빈 값
    const [plan, setPlan] = useState(null);

    useEffect(() => {
        (async () => {
            const res = await api.Get('branding', {});
            if (res?.status === 200) {
                setTabTitle(res.data?.tabTitle || 'Thinkfree Drive');
                setServiceName(res.data?.serviceName || 'Thinkfree Drive');
                const plan = res.data?.plan || null;
                // 새 플랜 추가 감지용 — 목록에 없는 값은 워터마크 미노출로 처리된다
                if (plan && !Object.values(LICENSE_PLAN).includes(plan)) {
                    console.warn(`[license] unknown plan: ${plan}`);
                }
                setPlan(plan);
            }
        })();
    }, []);

    return (
        <BrandingConfigContext.Provider value={{ tabTitle, serviceName, plan }}>
            {children}
        </BrandingConfigContext.Provider>
    );
}

export const useBrandingConfigContext = () => useContext(BrandingConfigContext);
