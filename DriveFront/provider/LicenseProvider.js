import { createContext, useContext, useState, useEffect } from 'react';
import { useRouter } from '@/hooks/useRouter';
import api from '@/utils/api';
import { getDaysLeft, getLicenseBlockStatus, isAuthGracePeriod } from '@/utils/license';

const LicenseContext = createContext(null);

export function LicenseProvider({ children }) {
    const router = useRouter();
    // { status: keyof BANNER_CONFIG, params: object } | null
    const [banner, setBanner] = useState(null);
    const [paidWhiteLabel, setPaidWhiteLabel] = useState(false);

    // TODO: config/license-status가 admins/license 데이터를 포함해 내려주므로, 추후 config/license-status 단일 호출로 통합 가능
    const getLicenseInfo = async () => {
        const res = await api.Get('admins/license', {});
        // 미등록, 예전 형식(6997) 모두 새로 등록해야 하므로 미등록 배너
        if (res?.code === 6998 || res?.code === 6997) {
            setBanner({
                status: 'NOT_ISSUED',
            });
        }
        if (res?.status !== 200) return;

        const data = res.data;
        setPaidWhiteLabel(data?.paidWhiteLabel || false);

        let blockStatus = null;
        if (!data?.state || data?.availableSeatNum < 0) {
            blockStatus = getLicenseBlockStatus(await api.Get('config/license-status', {}));
        }

        if (blockStatus === 'SEAT_EXCEEDED') {
            setBanner({
                status: 'SEAT_EXCEEDED',
                params: {
                    usedSeats: data?.currentSeatNum,
                    totalSeats: data?.constrainValue,
                },
            });
        } else if (blockStatus === 'EXPIRED') {
            setBanner({ status: 'EXPIRED', params: { expireDate: data?.expiresOn } });
        } else if (blockStatus === 'AUTH_FAILED' || isAuthGracePeriod(data)) {
            // 인증 실패 — 30일 유예 중(사용 가능)이어도 즉시 안내
            setBanner({ status: 'AUTH_FAILED' });
        } else if (blockStatus) {
            setBanner(null);
        } else {
            // 만료 예정 — 잔여 일수: 만료일 - 오늘
            const dDay = getDaysLeft(data?.expiresOn);
            if (dDay >= 0 && dDay <= 30) {
                setBanner({
                    status: 'EXPIRING',
                    params: { expireDate: data?.expiresOn, dDay },
                });
            } else {
                setBanner(null);
            }
        }
    };

    // 다른 탭에서의 변경(시트 활성화/비활성화, 삭제 등)을 반영하기 위해 메뉴 라우팅 시마다 재조회
    useEffect(() => {
        getLicenseInfo();
    }, [router.pathname]);

    const value = { banner, paidWhiteLabel, refreshLicense: getLicenseInfo };

    return <LicenseContext.Provider value={value}>{children}</LicenseContext.Provider>;
}

export function useLicense() {
    const context = useContext(LicenseContext);
    if (!context) {
        throw new Error('useLicense must be used within a LicenseProvider');
    }
    return context;
}
