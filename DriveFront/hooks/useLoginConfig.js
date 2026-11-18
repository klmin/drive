import { useLoginConfigContext } from '@/provider/LoginConfigProvider';

export default function useLoginConfig() {
    const { loginConfig, refetchLoginConfig } = useLoginConfigContext();
    return {
        loginConfig,
        isLocal: loginConfig?.loginType === 'local',
        isOidc: loginConfig?.loginType === 'oidc',
        isJwt: loginConfig?.loginType === 'jwt',
        errorRedirectUrl: loginConfig?.errorRedirectUrl,
        // SYSTEM_MANAGED 는 재설정 메일 발송이 막혀 있다(TENANT_011) — 값이 없으면 허용으로 본다
        passwordResetEnabled: loginConfig?.passwordResetEnabled !== false,
        refetchLoginConfig,
    };
}
