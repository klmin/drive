// provider/AppProvider.jsx
import React, { createContext, useEffect, useState } from 'react';
import { loadConfig } from '@/provider/configLoader';

export const AppConfigContext = createContext(null);

export function AppProvider({ children }) {
    const [config, setConfig] = useState(null);

    useEffect(() => {
        async function init() {
            const cfg = await loadConfig();
            setConfig(cfg);
        }
        init();
    }, []);

    if (!config) {
        // 아직 설정 안 불러왔을 때 로딩 화면
        // return <div>Loading configuration...</div>;
        return null;
    }

    return <AppConfigContext.Provider value={config}>{children}</AppConfigContext.Provider>;
}
