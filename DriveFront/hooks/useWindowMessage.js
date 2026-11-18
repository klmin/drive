import { useEffect } from 'react';

function useWindowMessage(callback, expectedMessage) {
    useEffect(() => {
        const receiveMessage = async event => {
            if (!event || !event.data) return;
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
