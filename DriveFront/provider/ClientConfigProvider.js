// ClientConfigProvider.js

import api from '@/utils/api';
import { isLoggedIn, inferRoleFromPath } from '@/utils/authTokens';
import { createContext, useContext, useEffect, useMemo, useRef, useState } from 'react';

const ClientConfigContext = createContext(null);

const debounce = (fn, delay) => {
    let timer;
    const debounced = (...args) => {
        clearTimeout(timer);
        timer = setTimeout(() => fn(...args), delay);
    };
    return debounced;
};

export function ClientConfigProvider({ children }) {
    // null = 아직 모름(/config 응답 전)
    const [officeEnabled, setOfficeEnabled] = useState(null);
    const [shareEnabled, setShareEnabled] = useState(null);
    const [intellectEnabled, setIntellectEnabled] = useState(null);
    const [isMultiTenant, setIsMultiTenant] = useState(null);
    // 테넌트 유형(SELF_MANAGED·SYSTEM_MANAGED)·사용자 모드(MULTI·SINGLE) — 생성 후 바뀌지 않아 한 번만 읽는다
    const [tenantType, setTenantType] = useState(null);
    const [userMode, setUserMode] = useState(null);
    const tenantConfigLoadedRef = useRef(false);
    // TFO 접속 주소가 https 인지 — 오피스가 켜졌을 때 한 번만 읽는다
    const [officeHttps, setOfficeHttps] = useState(null);
    const officeConfigLoadedRef = useRef(false);

    const fetchTenantConfig = () => {
        if (tenantConfigLoadedRef.current) return;
        tenantConfigLoadedRef.current = true;
        api.Get('config/tenant')
            .then(res => {
                if (res?.status !== 200) {
                    tenantConfigLoadedRef.current = false;
                    return;
                }
                const data = res.data?.data;
                setTenantType(data?.tenantType ?? null);
                setUserMode(data?.userMode ?? null);
            })
            .catch(() => {
                tenantConfigLoadedRef.current = false;
            });
    };

    const fetchOfficeConfig = () => {
        if (officeConfigLoadedRef.current) return;
        officeConfigLoadedRef.current = true;
        api.Get('config/office')
            // 실패하면 재시도 없이 기존 방식(iframe 미리보기)으로 둔다 — api-response 재조회 반복 방지
            .then(res =>
                setOfficeHttps(res?.status !== 200 || res.data?.data?.officeHttps !== false)
            )
            .catch(() => setOfficeHttps(true));
    };

    const fetchClientConfig = () => {
        // 미인증 상태(MFA·비밀번호 재설정 등 인증 전 페이지)에서는 호출 생략 —
        // config는 인증 필요 API라 401(AUTH_002)이 나고 handleAuthError가 로그인으로 튕겨버린다.
        // 로그인 완료 응답이 api-response 이벤트를 발행하므로 로그인하면 자동 재시도된다.
        // 조회하지 않거나 실패하면 오피스는 아직 모를 때만 off 로 확정한다(미리보기 로딩이 끝나도록)
        const settleOfficeOff = () => setOfficeEnabled(prev => prev ?? false);
        const role = inferRoleFromPath();
        if (!isLoggedIn(role)) return settleOfficeOff();
        // 슈퍼어드민 영역은 config(테넌트 단위 API)를 소비하지 않는다 — 슈퍼어드민은 테넌트 없이(tenant-less) 호출되므로
        // 다중 테넌트 배포에서 404 만 나는 죽은 호출이라 생략한다.
        if (role === 'SUPER_ADMIN') return settleOfficeOff();
        fetchTenantConfig();
        api.Get('config')
            .then(res => {
                if (res?.status !== 200) return settleOfficeOff();

                const data = res.data?.data;

                setShareEnabled(data?.shareStatus === true);
                setIntellectEnabled(data?.intellectStatus === true);
                setOfficeEnabled(data?.officeStatus === true);
                if (data?.officeStatus === true) fetchOfficeConfig();
                setIsMultiTenant((data?.tenantCount ?? 0) >= 2);
            })
            .catch(settleOfficeOff);
    };

    const debouncedFetch = useMemo(() => debounce(fetchClientConfig, 300), []);

    useEffect(() => {
        fetchClientConfig();
    }, []);

    useEffect(() => {
        window.addEventListener('api-response', debouncedFetch);

        return () => {
            window.removeEventListener('api-response', debouncedFetch);
        };
    }, [debouncedFetch]);

    return (
        <ClientConfigContext.Provider
            value={{
                officeEnabled,
                officeHttps,
                shareEnabled,
                intellectEnabled,
                isMultiTenant,
                tenantType,
                userMode,
            }}
        >
            {children}
        </ClientConfigContext.Provider>
    );
}

export const useClientConfigContext = () => useContext(ClientConfigContext);
