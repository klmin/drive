import { useClientConfigContext } from '@/provider/ClientConfigProvider';

export default function useClientConfig() {
    const {
        officeEnabled,
        officeHttps,
        shareEnabled,
        intellectEnabled,
        isMultiTenant,
        tenantType,
        userMode,
    } = useClientConfigContext();

    return {
        officeEnabled,
        // TFO 접속 주소가 https 인지(응답 전에는 null)
        officeHttps,
        shareEnabled,
        intellectEnabled,
        isMultiTenant,
        tenantType,
        userMode,
        // 외부 시스템이 관리하는 테넌트 — 설정 저장 등이 막혀 있어 화면에서 미리 비활성화한다(응답 전에는 false)
        isSystemManaged: tenantType === 'SYSTEM_MANAGED',
        // 계정이 보호 계정 하나뿐인 SYSTEM_MANAGED 테넌트 — 계정 추가·API Key 발급이 막힌다(응답 전에는 false)
        isSingleUser: tenantType === 'SYSTEM_MANAGED' && userMode === 'SINGLE',
    };
}
