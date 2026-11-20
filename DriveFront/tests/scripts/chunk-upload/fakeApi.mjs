// 가짜 서버 — 백엔드 /api/v1/uploads 흉내(응답 모양은 UploadSession*Response·ApiExceptionResponse 와 같게). 테스트가 server.* 로 상태·실패를 주입한다
export const server = {
    sessions: new Map(),
    calls: [],
    failPut: new Map(), // partNumber → 남은 실패 횟수(99 = 503 한 번, 그 외 = 네트워크 오류)
    conflictOnPart: null,
    completeConflictTimes: 0,
    putDelay: 0,
    failGet: 0, // 남은 상태 조회 실패 횟수(네트워크 오류)
    getDelay: 0,
    startResponseDelay: 0,
    singleRequestThreshold: 0, // 이보다 작은 파일은 세션 생성 거절(400 UPLOAD_008)
};

const PART = 16 * 1024 * 1024;
let seq = 0;
const err = (code, errorCode) => ({ result: false, code, errorCode, message: errorCode });
// 실제 api 유틸: validateStatus 로 오류도 받으면 응답 전체({status, data}), 아니면 오류 본문만
const fail = (cfg, code, errorCode) =>
    cfg?.validateStatus?.(code)
        ? { status: code, data: err(code, errorCode) }
        : err(code, errorCode);

const api = {
    Post: async (url, body) => {
        server.calls.push(['POST', url]);
        if (url === 'uploads') {
            if (body.size < server.singleRequestThreshold) return err(400, 'UPLOAD_008');
            const id = `s${++seq}`;
            const partCount = Math.ceil(body.size / PART);
            server.sessions.set(id, {
                id,
                status: 'PENDING',
                fileName: body.fileName,
                sizeByte: body.size,
                partSize: PART,
                partCount,
                parts: new Map(),
                body,
            });
            // 세션은 만들어졌고 응답만 늦게 온다(그 사이 사용자 취소)
            if (server.startResponseDelay)
                await new Promise(r => setTimeout(r, server.startResponseDelay));
            return {
                code: 201,
                data: { sessionId: id, partSize: PART, partCount, maxConcurrentParts: 4 },
            };
        }
        const id = url.match(/^uploads\/(\w+)\/complete$/)[1];
        const s = server.sessions.get(id);
        if (server.completeConflictTimes > 0) {
            server.completeConflictTimes--;
            s.status = 'COMPLETING';
            setTimeout(() => {
                s.status = 'COMPLETED';
            }, 50);
            return err(409, 'UPLOAD_005');
        }
        if (s.status === 'COMPLETED')
            return { code: 200, data: { resourceSeq: 99, resourceName: s.fileName } };
        for (let n = 1; n <= s.partCount; n++) {
            const exp = Math.min(PART, s.sizeByte - (n - 1) * PART);
            if (s.parts.get(n) !== exp) {
                s.status = 'FAILED';
                return err(400, 'UPLOAD_009');
            }
        }
        s.status = 'COMPLETED';
        return { code: 200, data: { resourceSeq: 99, resourceName: s.fileName } };
    },

    // 실제 api.PostRaw: 응답 전체. validateStatus 가 받는 오류도 응답 전체, 아니면(재발급 재시도 실패 등) 오류 본문
    PostRaw: async (url, body, _t, cfg) => {
        const r = await api.Post(url, body);
        if (r.result === false && !cfg?.validateStatus?.(r.code)) return r;
        return { status: r.code, data: r };
    },

    Put: async (url, blob, _t, cfg) => {
        const cfgOf = cfg;
        const [, id, n] = url.match(/^uploads\/(\w+)\/parts\/(\d+)$/);
        const part = Number(n);
        server.calls.push(['PUT', part]);
        const s = server.sessions.get(id);
        if (server.putDelay) await new Promise(r => setTimeout(r, server.putDelay));
        if (cfg.signal?.aborted) return undefined;
        if (cfg.headers?.['Content-Type'] !== 'application/octet-stream')
            return fail(cfgOf, 415, 'BAD_TYPE');
        if (server.conflictOnPart === part) {
            s.status = 'FAILED';
            return fail(cfgOf, 409, 'UPLOAD_001');
        }
        const f = server.failPut.get(part) || 0;
        if (f > 0) {
            server.failPut.set(part, f === 99 ? 0 : f - 1);
            return f === 99 ? fail(cfgOf, 503, 'X') : undefined;
        }
        if (s.status !== 'PENDING') return fail(cfgOf, 409, 'UPLOAD_001');
        cfg.onUploadProgress?.({ loaded: blob.size, total: blob.size });
        s.parts.set(part, blob.size);
        return { status: 200 };
    },

    Get: async (url, data) => {
        const cfgOf = data;
        if (server.getDelay) await new Promise(r => setTimeout(r, server.getDelay));
        if (data?.signal?.aborted) return undefined;
        server.calls.push(['GET', url]);
        if (server.failGet > 0) {
            server.failGet--;
            return undefined;
        }
        const id = url.split('/')[1];
        const s = server.sessions.get(id);
        if (!s) return fail(cfgOf, 404, 'UPLOAD_003');
        return {
            status: 200,
            data: {
                data: {
                    sessionId: id,
                    status: s.status,
                    fileName: s.fileName,
                    size: s.sizeByte,
                    partSize: s.partSize,
                    partCount: s.partCount,
                    uploadedParts: [...s.parts.keys()].sort((a, b) => a - b),
                    resourceSeq: 99,
                    resourceName: s.fileName,
                },
            },
        };
    },

    Delete: async url => {
        server.calls.push(['DELETE', url]);
        const s = server.sessions.get(url.split('/')[1]);
        if (s && s.status === 'PENDING') s.status = 'FAILED';
        return { status: 200 };
    },
};

export default api;
