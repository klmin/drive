'use client';

import React, { createContext, useState, useEffect, useContext } from 'react';
import api from '@/utils/api';
import useWindowMessage from '@/hooks/useWindowMessage';
import { isLoggedIn, isAdminArea } from '@/utils/authTokens';
import { useUser } from '@/context/UserContext';

const ResourcesContext = createContext(null);

export function ResourcesProvider({ children }) {
    const [resources, setResources] = useState([]);
    const [userSeq, setUserSeq] = useState([]);
    const user = useUser();

    const [isFetched, setIsFetched] = useState(false);

    useWindowMessage(() => getResources(true), 'drive_reload');

    useEffect(() => {
        if (userSeq !== user?.userSeq) setIsFetched(false);
        if (isFetched || !user || !isLoggedIn('USER')) return;

        getResources();
    }, [user, isFetched, userSeq]);

    const getResources = async () => {
        if (isAdminArea()) return;
        try {
            const res = await api.Get('resources/tree', {});

            if (res?.data?.data) {
                const userSeq = user?.userSeq;
                setResources(res.data.data);
                setIsFetched(true);
                setUserSeq(userSeq);
            }
        } catch (error) {
            console.error('Failed to fetch resources:', error);
        }
    };

    const refreshResources = () => {
        setIsFetched(false);
    };

    const value = { resources, refreshResources, getResources };

    return <ResourcesContext.Provider value={value}>{children}</ResourcesContext.Provider>;
}

export function useResources() {
    const context = useContext(ResourcesContext);
    if (!context) {
        throw new Error('useResources must be used within a ResourcesProvider');
    }
    return context;
}
