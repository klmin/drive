import React, { createContext, useContext, useEffect, useRef, useState, useCallback } from 'react';

const ClipboardContext = createContext(null);

export function ClipboardProvider({ children }) {
    const ref = useRef({ mode: 'copy', items: [] });
    const [clipboard, setClipboardState] = useState(ref.current);

    const setClipboard = useCallback(next => {
        ref.current = typeof next === 'function' ? next(ref.current) : next;
        setClipboardState(ref.current);
        try {
            sessionStorage.setItem('appClipboard', JSON.stringify(ref.current));
        } catch {}
    }, []);

    const getClipboard = useCallback(() => ref.current, []);

    const clearClipboard = useCallback(
        () => setClipboard({ mode: 'copy', items: [] }),
        [setClipboard]
    );

    useEffect(() => {
        const saved = sessionStorage.getItem('appClipboard');
        if (saved) {
            try {
                ref.current = JSON.parse(saved);
                setClipboardState(ref.current);
            } catch {}
        }
    }, []);

    // 디버깅: 재마운트 여부 확인
    useEffect(() => {
        // mount
        // console.log('ClipboardProvider mounted');
        return () => {
            // console.log('ClipboardProvider unmounted');
        };
    }, []);

    return (
        <ClipboardContext.Provider
            value={{ clipboard, setClipboard, getClipboard, clearClipboard }}
        >
            {children}
        </ClipboardContext.Provider>
    );
}

export function useClipboard() {
    const ctx = useContext(ClipboardContext);
    if (!ctx) throw new Error('useClipboard must be used within ClipboardProvider');
    return ctx;
}
