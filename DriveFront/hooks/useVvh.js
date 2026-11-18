import { useLayoutEffect } from 'react';

export function useVvh() {
    useLayoutEffect(() => {
        const setVvh = () => {
            const h = window.visualViewport?.height ?? window.innerHeight;
            document.documentElement.style.setProperty('--vvh', `${h * 0.01}px`);
        };

        setVvh();
        window.visualViewport?.addEventListener('resize', setVvh);
        window.addEventListener('resize', setVvh);

        return () => {
            window.visualViewport?.removeEventListener('resize', setVvh);
            window.removeEventListener('resize', setVvh);
        };
    }, []);
}
