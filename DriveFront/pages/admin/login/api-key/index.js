import { useEffect, useRef, useState } from 'react';
import { useTranslations } from 'next-intl';
import api from '@/utils/api';
import { useRouter } from '@/hooks/useRouter';
import { useDialog } from '@/provider/DialogProvider';
import { applyApiPrefix } from '@/provider/configLoader';
import { clearAuth, setLanguage, removePasswordChangeRequired } from '@/utils/authTokens';
import { clearApiKeySession, setApiKeySession, waitForApiKeyMessage } from '@/utils/apiKeySession';
import { getLicenseBlockStatus, isAuthGracePeriod } from '@/utils/license';
import ApiKeyEntryStatus from '@/components/common/ApiKeyEntryStatus';
import { ROUTES } from '@/constants/routes';

// 라이선스 상태별 알림 다이얼로그 i18n 키 (관리자 로그인 화면과 동일, 모든 상태에서 진입 허용)
const LICENSE_DIALOG_CONFIG = {
    NOT_ISSUED: {
        titleKey: 'admin.noLicenseDialog.title',
        descKey: 'admin.noLicenseDialog.desc',
    },
    SEAT_EXCEEDED: {
        titleKey: 'licenseDialog.seatExceeded.title',
        descKey: 'licenseDialog.seatExceeded.message',
    },
    EXPIRED: {
        titleKey: 'admin.licenseExpiredDialog.title',
        descKey: 'admin.licenseExpiredDialog.desc',
    },
    AUTH_FAILED: {
        titleKey: 'admin.licenseAuthFailDialog.title',
        descKey: 'admin.licenseAuthFailDialog.desc',
    },
};

const AUTH_FAILURE = {
    titleKey: 'common.errorPage.authFail.title',
    descKey: 'common.errorPage.authFail.desc',
};

/** developers iframe 진입 — 부모 창이 보낸 관리자 API Key 로 진입 */
export default function AdminApiKeyLogin() {
    const t = useTranslations();
    const router = useRouter();
    const { openDialog } = useDialog();
    const startedRef = useRef(false);
    const [failure, setFailure] = useState(null);

    useEffect(() => {
        if (!router.isReady || startedRef.current) return;
        startedRef.current = true;

        const fail = () => {
            clearAuth('ADMIN');
            setFailure(AUTH_FAILURE);
        };

        const enter = async () => {
            clearApiKeySession();
            const message = await waitForApiKeyMessage();
            if (!message) return fail();

            if (message.prefix) applyApiPrefix(message.prefix);
            setApiKeySession('ADMIN', message.apiKey, message.prefix);

            const userRes = await api.Get('users', {}, null, { __roleType: 'ADMIN' });
            const user = userRes?.data?.data;
            if (!user) return fail();

            const result = await api.Get('config/license-status');
            let licenseStatus = getLicenseBlockStatus(result);
            // 차단 전 인증 실패 유예 중이면 인증 실패로 안내 (authStatus 는 admins/license 에만 있음)
            if (!licenseStatus && result?.data?.state) {
                const licenseRes = await api.Get('admins/license');
                if (isAuthGracePeriod(licenseRes?.data)) licenseStatus = 'AUTH_FAILED';
            }

            // 응답 없음(NO_RESPONSE)은 안내 없이 진입
            const cfg = LICENSE_DIALOG_CONFIG[licenseStatus];
            if (cfg) {
                await openDialog('alert', {
                    title: t(cfg.titleKey),
                    description: t(cfg.descKey),
                    cancelButtonName: '',
                });
            }

            if (user.userLanguage) setLanguage('ADMIN', user.userLanguage);
            // API Key 진입은 비밀번호 변경을 유도하지 않음
            removePasswordChangeRequired();

            const q = router.query.redirect;
            const redirect = typeof q === 'string' && q.startsWith('/') ? q : '';
            router.replace(redirect || ROUTES.ADMIN_ACCOUNTS_USERS);
        };

        enter();
    }, [router.isReady]);

    return <ApiKeyEntryStatus failure={failure} />;
}
