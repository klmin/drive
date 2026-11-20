import { useEffect } from 'react';

function useWindowMessage(callback, expectedMessage) {
    useEffect(() => {
        const receiveMessage = async event => {
            if (!event || !event.data) return;
            // 같은 창이 보낸 신호만(drive_reload·quota_reload 는 window.postMessage 로 자기 자신에게 보낸다) —
            // 다른 출처의 창(iframe·opener)이 목록·용량 새로고침을 일으키지 못하게
            if (event.origin !== window.location.origin) return;
            if (!expectedMessage || event.data === expectedMessage) {
                await callback(event.data, event);
            }
        };

        window.addEventListener('message', receiveMessage);

        return () => {
            window.removeEventListener('message', receiveMessage);
        };
    }, [callback, expectedMessage]);
}

export default useWindowMessage;
