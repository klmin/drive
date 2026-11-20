// chunk-upload.test.mjs 전용 모듈 로더 — '@/utils/api' 를 가짜 서버로, 나머지 '@/...' 를 DriveFront 소스로 잇는다
const FRONT_ROOT = new URL('../../../', import.meta.url);
const FAKE_API = new URL('./fakeApi.mjs', import.meta.url);

export async function resolve(spec, ctx, next) {
    if (spec === '@/utils/api') return { url: FAKE_API.href, shortCircuit: true };
    if (spec.startsWith('@/')) {
        return { url: new URL(`${spec.slice(2)}.js`, FRONT_ROOT).href, shortCircuit: true };
    }
    return next(spec, ctx);
}

export async function load(url, ctx, next) {
    // 소스는 package.json type 지정 없이 ESM 문법(.js) — 모듈로 읽는다
    if (url.startsWith(FRONT_ROOT.href) && url.endsWith('.js')) {
        const r = await next(url, { ...ctx, format: 'module' });
        return { ...r, format: 'module', shortCircuit: true };
    }
    return next(url, ctx);
}
