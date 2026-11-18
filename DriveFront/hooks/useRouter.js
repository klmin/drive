import { useRouter as useNextRouter } from 'next/router';
import { useMemo } from 'react';
import { stripTenantPrefix, tenantHref } from '@/utils/tenantPrefix';

/**
 * 테넌트 접두(/t/{slug})를 반영한 next/router 래퍼.
 * push·replace 는 표시 URL(as)에 접두를 붙이고, asPath 는 접두를 뗀 앱 내부 경로를 반환한다.
 * 정적 라우트끼리는 url·as 불일치가 허용돼 SPA 이동이 유지된다(09-18 `/default/` 하이드레이션 실측).
 */
export function useRouter() {
    const router = useNextRouter();

    return useMemo(
        () => ({
            ...router,
            asPath: stripTenantPrefix(router.asPath),
            push: (url, as, options) => router.push(url, as ?? tenantHref(url), options),
            replace: (url, as, options) => router.replace(url, as ?? tenantHref(url), options),
        }),
        [router]
    );
}
