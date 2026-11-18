import { isDevelopersEmbed } from '@/utils/apiKeySession';
import { inferRoleFromPath } from '@/utils/authTokens';
import { getTenantPrefix } from '@/utils/tenantPrefix';

/**
 * 인텔릭트 열기 주소 — 화면은 `/intellect` 고정, API 용 테넌트 접두(tp)·쿠키 역할(r: USER|ANONYMOUS)을 넘긴다.
 * developers iframe 안이면 `em=1` — 인텔릭트가 쿠키 대신 developers API Key 로 호출
 */
export const getIntellectUrl = async (item, user, mode) => {
    if (!item) {
        console.warn('No item or user available for edit');
        return;
    }

    try {
        const rs = item?.resourceSeq;
        const ss = item?.sharedByUserSeq || item?.userSeq || undefined;
        const lang = user.userLanguage;
        const tp = getTenantPrefix();
        const r = inferRoleFromPath() === 'ANONYMOUS' ? 'ANONYMOUS' : 'USER';

        let intellectUrl = `/intellect?rs=${rs}&ss=${ss}&lang=${lang}&r=${r}`;
        intellectUrl = tp ? intellectUrl + `&tp=${encodeURIComponent(tp)}` : intellectUrl;
        intellectUrl = mode === 'ro' ? intellectUrl + `&mode=${mode}` : intellectUrl;
        intellectUrl = isDevelopersEmbed() ? intellectUrl + '&em=1' : intellectUrl;
        console.log('intellectUrl', intellectUrl);
        return intellectUrl;
    } catch (error) {
        console.error('getIntellectEditUrl: Failed to generate URL', error);
    }
};
