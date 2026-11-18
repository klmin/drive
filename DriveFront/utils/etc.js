export default function checkDevice() {
    const IPAD_WIDTH_BASIS = 768;
    const ATABLET_WIDTH_BASIS = 533; //갤럭시 탭 10.1 기준
    const uat = navigator.userAgent.toLowerCase(),
        isMobile = navigator.maxTouchPoints === 5,
        isIos = isMobile && /(mac os x)/i.test(uat),
        isAndroid = isMobile && /(linux)/i.test(uat),
        // isPhone = /(ip(hone|od)|blackberry|iemobile|kindle|netfront|silk-accelerated|(hpw|web)os|fennec|minimo|opera m(obi|ini)|blazer|dolfin|dolphin|skyfire|zune)/i.test(uat),
        winW = window.screen.width;
    let isIphone = /(iphone)/i.test(uat),
        isIpad = /(ipad)/i.test(uat) || (isIos && !isIphone),
        isAphone = isAndroid && /(mobile)/i.test(uat),
        isAtap = /(android)/i.test(uat) && !isAphone,
        isMobileUat = isIpad || isIphone || isAphone || isAtap;
    // 브라우저에서 제공하는 데스크탑 보기 모드일 경우 user agent로 분류 불가능
    // 해당 경우에만 phone size로 분류
    if (isMobile && !isMobileUat) {
        if (isIos) {
            if (winW < IPAD_WIDTH_BASIS) {
                isIphone = true;
            } else {
                isIpad = true;
            }
        } else {
            if (winW < ATABLET_WIDTH_BASIS) {
                isAphone = true;
            } else {
                isAtap = true;
            }
        }
    }
    return {
        isMobile: isMobile,
        isPhone: isIphone || isAphone,
        isTablet: isIpad || isAtap,
        isAndroid: isAndroid,
        isIos: isIos,
        isIphone: isIphone,
        isIpad: isIpad,
        isWindows: /(windows)/i.test(uat),
        isChrome: /(chrome|crios)/i.test(uat),
        IsMac: /macintosh/.test(uat) || isIos,
        IsLinux: /linux/.test(uat),
        isTizen: /tizen/.test(uat),
    };
}
