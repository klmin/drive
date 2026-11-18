'use client';

import React, { createContext, useState, useEffect, useContext } from 'react';
import api from '@/utils/api';
import useWindowMessage from '@/hooks/useWindowMessage';
import { isLoggedIn, isAdminArea } from '@/utils/authTokens';

const QuotaContext = createContext(null);

export function QuotaProvider({ children }) {
    const [quota, setQuota] = useState(null);
    const [isFetched, setIsFetched] = useState(false);

    useWindowMessage(() => getQuota({}, true), 'quota_reload');

    useEffect(() => {
        if (isFetched || !isLoggedIn('USER')) return;

        getQuota();
    }, [isLoggedIn, isFetched]);

    const getQuota = async () => {
        if (isAdminArea()) return;
        try {
            const res = await api.Get('users/quota', {});

            if (res?.data?.data) {
                setQuota(res.data.data);
                setIsFetched(true);
            }
            console.log('quota');
        } catch (error) {
            console.error('Failed to fetch resources:', error);
        }
    };

    const refreshQuota = () => {
        setIsFetched(false);
    };

    const value = { quota, refreshQuota };

    return <QuotaContext.Provider value={value}>{children}</QuotaContext.Provider>;
}

export function useQuota() {
    const context = useContext(QuotaContext);
    if (!context) {
        throw new Error('useQuota must be used within a ResourcesProvider');
    }
    return context;
}
