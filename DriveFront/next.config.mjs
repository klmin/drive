import createNextIntlPlugin from 'next-intl/plugin';
import { PHASE_DEVELOPMENT_SERVER } from 'next/constants.js';

const withNextIntl = createNextIntlPlugin();

/** @type {import('next').NextConfig} */
const nextConfig = {
    reactStrictMode: false,
    output: 'export',
    trailingSlash: true,
    images: {
        unoptimized: true,
    },
    compiler: {
        removeConsole: process.env.NODE_ENV === 'production',
    },
};

/**
 * PATH 테넌트 모드 개발용 rewrite (dev 서버 한정). URL 문법 = `/t/{slug}/...` (utils/tenantPrefix TENANT_PATH_MARKER).
 * 백엔드가 `/t/{slug}/oauth2-login/` 처럼 접두 붙은 페이지로 302 시키는데, 단일 jar 배포는 백엔드가 접두를 떼고
 * 정적 페이지를 서빙하지만 로컬은 Caddy 가 접두 붙은 페이지를 그대로 Next dev(3000)로 넘긴다.
 * afterFiles 라 실제 페이지·public 파일이 먼저 매치되고, 마커가 붙은 `/t/{slug}/...` 만 접두를 떼서 페이지로 보낸다
 * (마커 없는 1세그먼트 경로는 건드리지 않는다 — 종전 `/:slug` 전역 rewrite 의 부작용 제거).
 * 클라이언트는 `location.pathname` 에 접두가 남아 있어 utils/tenantPrefix 가 그대로 판별한다.
 * `output: 'export'` 빌드에는 rewrite 가 적용되지 않으므로 dev phase 에만 넣는다.
 */
const devTenantPathRewrites = async () => ({
    afterFiles: [
        { source: '/t/:slug', destination: '/' },
        { source: '/t/:slug/:path*', destination: '/:path*' },
    ],
});

export default phase =>
    withNextIntl(
        phase === PHASE_DEVELOPMENT_SERVER
            ? { ...nextConfig, rewrites: devTenantPathRewrites }
            : nextConfig
    );
