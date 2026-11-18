const getOS = () => {
    if (typeof navigator === 'undefined') return 'unknown';

    // 1) UA-CH 우선 (Chromium 기반)
    const uaDataPlatform = navigator.userAgentData && navigator.userAgentData.platform;
    const ua = navigator.userAgent || '';

    // 2) iPadOS 보정: iPadOS 13+는 Mac으로 위장하지만 터치포인트로 구분 가능
    const isIPadOS =
        /iPad/.test(ua) ||
        ((/Macintosh/.test(ua) || /Mac OS X/.test(ua)) && navigator.maxTouchPoints > 1);

    if (uaDataPlatform) {
        const p = uaDataPlatform.toLowerCase();
        if (p.includes('mac') || isIPadOS) return 'mac';
        if (p.includes('win')) return 'windows';
        if (p.includes('linux') || p.includes('x11')) return 'linux';
    }

    // 3) UA 문자열 파싱 (사파리/구형 브라우저 대응)
    if (isIPadOS || /mac|macintosh|mac os x/i.test(ua)) return 'mac';
    if (/windows|win32|win64/i.test(ua)) return 'windows';
    if (/linux|x11/i.test(ua)) return 'linux';

    return 'unknown';
};

export const isMac = () => getOS() === 'mac';
export const isWindows = () => getOS() === 'windows';
export const isLinux = () => getOS() === 'linux';

// "mod+shift+p" 같은 포맷을 표시용 문자열로 변환
export const formatShortcut = combo => {
    const mac = isMac();
    const parts = (combo || '').split('+');
    const map = k => {
        const key = k.trim().toLowerCase();
        if (key === 'mod') return mac ? '⌘' : 'Ctrl';
        if (key === 'shift') return mac ? '⇧' : 'Shift';
        if (key === 'alt' || key === 'option') return mac ? '⌥' : 'Alt';
        if (key === 'ctrl' || key === 'control') return mac ? '⌃' : 'Ctrl';
        return key.length === 1 ? key.toUpperCase() : k; // 문자키는 대문자
    };
    return mac ? parts.map(map).join('') : parts.map(map).join('+');
};
