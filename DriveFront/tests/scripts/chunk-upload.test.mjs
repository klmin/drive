// 청크 업로더(utils/chunkUpload.js) — 가짜 서버로 분할·이어 올리기·재시도·409 중단·취소·complete 대기를 확인한다.
// 실행: node --test tests/scripts/chunk-upload.test.mjs
import test, { beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { register } from 'node:module';

register('./chunk-upload/hooks.mjs', import.meta.url);

const store = new Map();
globalThis.window = {
    localStorage: { getItem: k => store.get(k) ?? null, setItem: (k, v) => store.set(k, v) },
};
const { server } = await import('./chunk-upload/fakeApi.mjs');
const { uploadInChunks, ChunkUploadError } = await import('@/utils/chunkUpload');

const MB = 1024 * 1024;
const mkFile = (mb, name) => new File([new Uint8Array(mb * MB)], name, { lastModified: 1 });
const target = { parentFolderSeq: 10 };
const puts = () => server.calls.filter(c => c[0] === 'PUT').map(c => c[1]);
const starts = () => server.calls.filter(c => c[0] === 'POST' && c[1] === 'uploads').length;
const saved = () => Object.keys(JSON.parse(store.get('drive.chunkUploads') || '{}')).length;

beforeEach(() => {
    server.calls = [];
    server.failPut.clear();
    server.conflictOnPart = null;
    server.completeConflictTimes = 0;
    server.putDelay = 0;
});

test('전체 업로드: 70MB = 16MB 파트 5개, 진행률 100%, onPartsSent 1번, 저장소 정리', async () => {
    let last = 0;
    let sent = 0;
    const r = await uploadInChunks(mkFile(70, 'big.bin'), target, {
        onProgress: (l, tot) => {
            last = l / tot;
        },
        onPartsSent: () => sent++,
    });
    assert.equal(r.resourceSeq, 99);
    assert.deepEqual(
        [...puts()].sort((a, b) => a - b),
        [1, 2, 3, 4, 5]
    );
    assert.equal(last, 1);
    assert.equal(sent, 1);
    assert.equal(saved(), 0);
});

test('네트워크 오류로 끊김 → 같은 파일을 다시 고르면 같은 세션으로 빠진 파트만', async () => {
    const f = mkFile(70, 'resume.bin');
    server.failPut.set(3, 10); // 3번 파트 계속 네트워크 실패 → 재시도 소진
    await assert.rejects(
        uploadInChunks(f, target),
        e => e instanceof ChunkUploadError && e.status === 0
    );
    assert.equal(starts(), 1);
    assert.equal(saved(), 1, '네트워크 오류면 이어 올리기용으로 남긴다');
    const sentBefore = new Set(puts().filter(n => n !== 3));
    server.calls = [];
    server.failPut.clear(); // 연결 복구

    const r = await uploadInChunks(f, target);
    assert.equal(r.resourceSeq, 99);
    assert.equal(starts(), 0, '새 세션을 만들지 않는다');
    assert.ok(puts().includes(3));
    assert.deepEqual(
        puts().filter(n => sentBefore.has(n)),
        [],
        '이미 올라간 파트는 다시 보내지 않는다'
    );
});

test('5xx 는 재시도해서 성공', async () => {
    server.failPut.set(2, 99); // 503 한 번
    const r = await uploadInChunks(mkFile(40, 'retry.bin'), target);
    assert.equal(r.resourceSeq, 99);
    assert.equal(puts().filter(n => n === 2).length, 2);
});

test('409 를 받으면 재시도·취소 없이 멈추고 저장소에서 지운다', async () => {
    server.conflictOnPart = 2;
    await assert.rejects(
        uploadInChunks(mkFile(70, 'conflict.bin'), target),
        e => e.status === 409 && e.errorCode === 'UPLOAD_001'
    );
    assert.equal(puts().filter(n => n === 2).length, 1);
    assert.equal(server.calls.filter(c => c[0] === 'DELETE').length, 0);
    assert.equal(saved(), 0);
});

test('취소 → null, DELETE 1번, complete 안 보냄, 저장소에서 지움', async () => {
    server.putDelay = 30;
    const ac = new AbortController();
    const p = uploadInChunks(mkFile(100, 'cancel.bin'), target, { signal: ac.signal });
    setTimeout(() => ac.abort(), 10);
    assert.equal(await p, null);
    assert.equal(server.calls.filter(c => c[0] === 'DELETE').length, 1);
    assert.equal(
        server.calls.filter(c => c[0] === 'POST' && String(c[1]).endsWith('complete')).length,
        0
    );
    assert.equal(saved(), 0);
});

test('complete 409(처리 중) → 상태 조회로 완료 결과', async () => {
    server.completeConflictTimes = 1;
    const r = await uploadInChunks(mkFile(40, 'dup.bin'), target);
    assert.equal(r.resourceSeq, 99);
    assert.ok(server.calls.some(c => c[0] === 'GET'));
});

test('남은 세션이 만료(FAILED)됐으면 새로 시작', async () => {
    const f = mkFile(40, 'stale.bin');
    server.failPut.set(1, 10);
    await assert.rejects(uploadInChunks(f, target));
    for (const s of server.sessions.values()) if (s.fileName === 'stale.bin') s.status = 'FAILED';
    server.calls = [];
    server.failPut.clear();

    const r = await uploadInChunks(f, target);
    assert.equal(r.resourceSeq, 99);
    assert.equal(starts(), 1);
});

test('다른 위치로 올리는 같은 파일은 다른 세션', async () => {
    const f = mkFile(40, 'where.bin');
    server.failPut.set(1, 10);
    await assert.rejects(uploadInChunks(f, { parentFolderSeq: 1 }));
    server.calls = [];
    server.failPut.clear();
    await uploadInChunks(f, { parentFolderSeq: 2 });
    assert.equal(starts(), 1);
});

test('오류 본문이 JSON 이 아니면(앞단 프록시 413 HTML 등) 재시도하지 않는 실패', async () => {
    const api = (await import('./chunk-upload/fakeApi.mjs')).default;
    const original = api.Put;
    api.Put = async () => '<html>413 Request Entity Too Large</html>';
    try {
        await assert.rejects(uploadInChunks(mkFile(40, 'proxy.bin'), target), e => e.status === -1);
    } finally {
        api.Put = original;
    }
});

test('토큰 재발급 뒤 api.Post 가 Axios 응답 전체를 돌려줘도 세션 생성·complete 성공', async () => {
    const api = (await import('./chunk-upload/fakeApi.mjs')).default;
    const original = api.Post;
    // utils/api.js handleAuthError: 401 → 재발급 → axios(originalRequest) 결과(응답 전체)를 그대로 돌려준다
    api.Post = async (...args) => {
        const body = await original(...args);
        return { status: body.code, data: body, headers: {}, config: {} };
    };
    try {
        const r = await uploadInChunks(mkFile(40, 'reissue.bin'), target);
        assert.equal(r.resourceSeq, 99);
        assert.equal(starts(), 1);
    } finally {
        api.Post = original;
    }
});
