import { useEffect, useRef, useState } from 'react';
import api from '@/utils/api';
import { useRouter } from '@/hooks/useRouter';
import { applyApiPrefix } from '@/provider/configLoader';
import { clearAuth, setLanguage } from '@/utils/authTokens';
import { clearApiKeySession, setApiKeySession, waitForApiKeyMessage } from '@/utils/apiKeySession';
import { getLicenseBlockStatus } from '@/utils/license';
import ApiKeyEntryStatus from '@/components/common/ApiKeyEntryStatus';
import { ROUTES } from '@/constants/routes';

// 진입 차단 안내 i18n 키 (사용자 로그인 화면과 동일)
const LICENSE_BLOCK_MESSAGE = {
    NOT_ISSUED: {
        titleKey: 'licenseDialog.unregistered.title',
        descKey: 'licenseDialog.unregistered.message',
    },
    // 응답 없음 — 등록 여부를 확인할 수 없으므로 미등록으로 안내
    NO_RESPONSE: {
        titleKey: 'licenseDialog.unregistered.title',
        descKey: 'licenseDialog.unregistered.message',
    },
    BLOCKED: {
        titleKey: 'common.label.licenseExpired',
        descKey: 'licenseDialog.expired.message',
    },
};

const AUTH_FAILURE = {
    titleKey: 'common.errorPage.authFail.title',
    descKey: 'common.errorPage.authFail.desc',
};

/** developers iframe 진입 — 부모 창이 보낸 사용자 API Key 로 진입 */
export default function ApiKeyLogin() {
    const router = useRouter();
    const startedRef = useRef(false);
    const [failure, setFailure] = useState(null);

    useEffect(() => {
        if (!router.isReady || startedRef.current) return;
        startedRef.current = true;

        const fail = message => {
            clearAuth('USER');
            setFailure(message);
        };

        const enter = async () => {
            clearApiKeySession();
            const message = await waitForApiKeyMessage();
            if (!message) return fail(AUTH_FAILURE);

            if (message.prefix) applyApiPrefix(message.prefix);
            setApiKeySession('USER', message.apiKey, message.prefix);

            const userRes = await api.Get('users', {}, null, { __roleType: 'USER' });
            const user = userRes?.data?.data;
            if (!user) return fail(AUTH_FAILURE);

            const result = await api.Get('config/license-status');
            const blockStatus = getLicenseBlockStatus(result);
            if (blockStatus) {
                return fail(LICENSE_BLOCK_MESSAGE[blockStatus] || LICENSE_BLOCK_MESSAGE.BLOCKED);
            }

            if (user.userLanguage) setLanguage('USER', user.userLanguage);
            sessionStorage.removeItem('treeExpandedState');

            const q = router.query.redirect;
            const redirect = typeof q === 'string' && q.startsWith('/') ? q : '';
            const dlp = user.defaultLandingPage;
            const defaultRoute = dlp
                ? dlp === 'my-drive'
                    ? ROUTES.DRIVE
                    : `/${dlp}`
                : ROUTES.HOME;
            router.replace(redirect || defaultRoute);
        };

        enter();
    }, [router.isReady]);

    return <ApiKeyEntryStatus failure={failure} />;
}
