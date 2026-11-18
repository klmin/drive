import { useEffect } from 'react';
import api from '@/utils/api';
import { useRouter } from '@/hooks/useRouter';
import { saveAuth, saveMfaSetup, saveMfaVerify, setLoginSuccessSession } from '@/utils/authTokens';
import { ROUTES } from '@/constants/routes';

export default function JwtLogin() {
    const router = useRouter();

    // useLoginConfig 훅을 쓰지 않는 이유:
    // 이 페이지는 마운트 즉시 login-config 조회 → JWT 인증을 순차 실행해야 하므로
    // loginConfig가 null인 채로 인증이 실행될 수 있음
    const getLoginSettingInfo = async () => {
        const res = await api.Get('auth/login-config', {});
        if (res?.data?.code === 200) {
            const data = res?.data?.data;
            return data;
        }
        return null;
    };

    const authenticateWithJwt = async settingInfo => {
        try {
            const res = await api.Post('auth/sso/jwt', {
                token: router.query?.t,
            });
            console.log(res?.data);
            if (res?.code === 200) {
                // 로그인 필요 없음 - 이미 로그인 된 상태 (접속하려는 토큰 정보의 유저 = 드라이브에 로그인된 유저)
                if (res?.data?.status === 'already_logged_in') {
                    router.replace(ROUTES.HOME);
                } else {
                    // jwt로 로그인
                    // 쿠키 저장
                    saveAuth('USER', res.data);

                    setLoginSuccessSession(res.data.userLanguage);

                    const dlp = res.data?.defaultLandingPage;
                    router.replace(dlp === 'my-drive' ? ROUTES.DRIVE : `/${dlp}`);
                }
            }
        } catch (err) {
            const code = err?.response?.data?.code; // 428이면 mfa인증 필요
            const errorCode = err?.response?.data?.errorCode;

            const data = err?.response?.data?.data;
            const mfaChallengeType = data?.mfaChallengeType;
            const token = data?.token;
            console.log(err?.response);
            // MFA 활성화된 유저 -> OTP 인증 필요
            if (code === 428 && mfaChallengeType) {
                switch (mfaChallengeType) {
                    case 'setup':
                        saveMfaSetup(token);
                        router.replace(ROUTES.MFA_SETUP);
                        break;

                    case 'verify':
                        saveMfaVerify(token, data.challengeType);
                        router.replace(ROUTES.MFA_VERIFY);
                        break;
                    default:
                        console.log('jwt Login 실패 - 428');
                        if (settingInfo?.loginType === 'jwt' && settingInfo?.errorRedirectUrl) {
                            sessionStorage.setItem(
                                'loginErrorRedirectUrl',
                                settingInfo.errorRedirectUrl
                            );
                            router.replace(ROUTES.LOGIN_ERROR);
                        }
                }
            } else {
                // ex) SSO_018 - jwt 유저와 기존 드라이브 로그인 유저 불일치
                console.log('jwt Login 실패 - ' + errorCode);
                if (settingInfo?.errorRedirectUrl) {
                    sessionStorage.setItem('loginErrorRedirectUrl', settingInfo.errorRedirectUrl);
                    router.replace(ROUTES.LOGIN_ERROR);
                } else {
                    router.replace(ROUTES.ERROR_PAGE);
                }
            }
        }
    };

    useEffect(() => {
        if (!router.isReady) return;
        const init = async () => {
            const settingInfo = await getLoginSettingInfo();
            await authenticateWithJwt(settingInfo);
        };
        init();
    }, [router.isReady]);

    return null;
}
