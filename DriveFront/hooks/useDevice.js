import { useState, useEffect } from 'react';
import checkDevice from '@/utils/etc';

const useDevice = () => {
    const [deviceInfo, setDeviceInfo] = useState({
        isMobile: false,
        isTablet: false,
        isTabletOrMobile: false,
        isPc: true,
    });

    useEffect(() => {
        if (typeof window === 'undefined') return;

        const { isPhone, isTablet } = checkDevice();
        setDeviceInfo({
            isMobile: isPhone,
            isTablet,
            isTabletOrMobile: isPhone || isTablet,
            isPc: !isPhone && !isTablet,
        });
    }, []);

    return deviceInfo;
};

export default useDevice;
