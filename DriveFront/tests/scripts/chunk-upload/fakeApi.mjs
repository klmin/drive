// 가짜 서버 — 백엔드 /api/v1/uploads 흉내(응답 모양은 UploadSession*Response·ApiExceptionResponse 와 같게). 테스트가 server.* 로 상태·실패를 주입한다
export const server = {
    sessions: new Map(),
    calls: [],
    failPut: new Map(), // partNumber → 남은 실패 횟수(99 = 503 한 번, 그 외 = 네트워크 오류)
    conflictOnPart: null,
    completeConflictTimes: 0,
    putDelay: 0,
};

const PART = 16 * 1024 * 1024;
let seq = 0;
const err = (code, errorCode) => ({ result: false, code, errorCode, message: errorCode });

const api = {
    Post: async (url, body) => {
        server.calls.push(['POST', url]);
        if (url === 'uploads') {
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

    Put: async (url, blob, _t, cfg) => {
        const [, id, n] = url.match(/^uploads\/(\w+)\/parts\/(\d+)$/);
        const part = Number(n);
        server.calls.push(['PUT', part]);
        const s = server.sessions.get(id);
        if (server.putDelay) await new Promise(r => setTimeout(r, server.putDelay));
        if (cfg.signal?.aborted) return undefined;
        if (cfg.headers?.['Content-Type'] !== 'application/octet-stream')
            return err(415, 'BAD_TYPE');
        if (server.conflictOnPart === part) {
            s.status = 'FAILED';
            return err(409, 'UPLOAD_001');
        }
        const f = server.failPut.get(part) || 0;
        if (f > 0) {
            server.failPut.set(part, f === 99 ? 0 : f - 1);
            return f === 99 ? err(503, 'X') : undefined;
        }
        if (s.status !== 'PENDING') return err(409, 'UPLOAD_001');
        cfg.onUploadProgress?.({ loaded: blob.size, total: blob.size });
        s.parts.set(part, blob.size);
        return { status: 200 };
    },

    Get: async url => {
        server.calls.push(['GET', url]);
        const id = url.split('/')[1];
        const s = server.sessions.get(id);
        if (!s) return err(404, 'UPLOAD_003');
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
