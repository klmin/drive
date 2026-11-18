import { useSnackbarContext } from '@/provider/SnackbarProvider';
import api from '@/utils/api';
import {
    saveAuth,
    saveMfaSetup,
    saveMfaVerify,
    saveSsoSignupSession,
    saveSsoLoginError,
    getOAuth2LoginPending,
    removeOAuth2LoginPending,
} from '@/utils/authTokens';
import { useRouter } from '@/hooks/useRouter';
import { useEffect } from 'react';
import { useTranslations } from 'use-intl';
import { ROUTES } from '@/constants/routes';

export default function SsoCallback() {
    const router = useRouter();
    const t = useTranslations();
    const { showSnackbar } = useSnackbarContext();

    // /sso/callback
    useEffect(() => {
        const run = async () => {
            try {
                const res = await api.Get(`auth/sso/session`);

                const data = res.data?.data;
                const token = data?.token;

                switch (data?.status) {
                    case 'login':
                        saveAuth('USER', data);
                        // MCP OAuth 연동 중 시작된 SSO 로그인 — 홈 대신 oauth2-login으로
                        // 복귀해 브리지(JWT→AS 세션 교환)를 이어간다 (MCP_OAUTH_BRIDGE_FLOW.md)
                        if (getOAuth2LoginPending()) {
                            removeOAuth2LoginPending();
                            router.replace(ROUTES.OAUTH2_LOGIN);
                            break;
                        }
                        const dlp = data.defaultLandingPage;
                        router.push(dlp === 'my-drive' ? ROUTES.DRIVE : `/${dlp}`);
                        break;
                    case 'mfa_setup':
                        saveMfaSetup(token);
                        router.replace(ROUTES.MFA_SETUP);
                        break;

                    case 'mfa_verify':
                        saveMfaVerify(token, data.challengeType);
                        router.replace(ROUTES.MFA_VERIFY);
                        break;

                    case 'error':
                        console.log('sso Login 실패 - error', data.errorCode);
                        saveSsoLoginError(data.errorCode);
                        router.replace(ROUTES.LOGIN);
                        break;

                    default:
                        console.log('sso Login 실패 - UNKNOWN');
                        showSnackbar(t('loginToast.fail'), 'error');
                        router.replace(ROUTES.LOGIN);
                }
            } catch (e) {
                console.log('e', e);
                router.replace(ROUTES.LOGIN_SSO_SESSION_INVALID);
            }
        };

        run();
    }, []);

    return null;
}
