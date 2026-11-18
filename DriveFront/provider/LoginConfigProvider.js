import { createContext, useContext, useEffect, useState } from 'react';
import api from '@/utils/api';

const LoginConfigContext = createContext(null);

/** @param enabled false 면 조회하지 않음 (로그인 설정을 쓰지 않는 관리자·슈퍼관리자 영역) */
export function LoginConfigProvider({ children, enabled = true }) {
    const [loginConfig, setLoginConfig] = useState(null);

    const fetchLoginConfig = () => {
        api.Get('auth/login-config').then(res => {
            if (res?.data?.code === 200) {
                setLoginConfig(res.data.data);
            }
        });
    };

    useEffect(() => {
        if (enabled && !loginConfig) fetchLoginConfig();
    }, [enabled]);

    return (
        <LoginConfigContext.Provider value={{ loginConfig, refetchLoginConfig: fetchLoginConfig }}>
            {children}
        </LoginConfigContext.Provider>
    );
}

export const useLoginConfigContext = () => useContext(LoginConfigContext);
