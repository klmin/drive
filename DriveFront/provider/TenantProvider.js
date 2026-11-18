import { createContext, useContext, useEffect, useMemo } from 'react';
import { getTenantPrefix, getTenantSlug, isInvalidTenantPath } from '@/utils/tenantPrefix';
import { ROUTES } from '@/constants/routes';

const TenantContext = createContext(null);

/** 현재 문서의 테넌트 정보 제공 (prefix: '' 또는 '/t/{slug}', slug: 없으면 null) */
export function TenantProvider({ children }) {
    const value = useMemo(() => ({ prefix: getTenantPrefix(), slug: getTenantSlug() }), []);
    const isInvalid = useMemo(() => isInvalidTenantPath(), []);

    // /t, /t/ 처럼 테넌트 값이 없으면 하위(loadConfig·API 호출) 마운트 없이 에러 페이지로 이동
    useEffect(() => {
        if (isInvalid) window.location.replace(ROUTES.ERROR_PAGE_TENANT);
    }, [isInvalid]);

    if (isInvalid) return null;

    return <TenantContext.Provider value={value}>{children}</TenantContext.Provider>;
}

export const useTenantContext = () => useContext(TenantContext);
