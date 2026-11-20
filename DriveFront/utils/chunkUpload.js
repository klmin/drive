import api from '@/utils/api';

/**
 * 청크 업로드(서버 경유 멀티파트) — 백엔드 /api/v1/uploads (docs/design/upload-session-as-built.md §3.5).
 *
 * - 이 크기 이상만 청크로 보낸다. 서버는 이보다 작은 파일을 거절한다(단일 요청 POST /files 를 쓴다).
 * - 파트는 서버가 정한 계획(partSize·partCount)대로 잘라 세션당 동시 maxConcurrentParts 개 PUT 한다.
 * - 같은 파일(이름·크기·수정 시각·대상)을 다시 고르면 localStorage 에 남긴 세션으로 빠진 파트만 이어 올린다.
 *   (브라우저는 새로고침 뒤 파일을 다시 읽을 수 없으므로 사용자가 같은 파일을 다시 골라야 한다)
 * - 409(세션이 더 이상 PENDING 아님)를 받으면 재시도·취소 없이 멈춘다 — 서버가 이미 정리를 등록했다.
 * - 취소(signal abort)는 complete 전까지만 — DELETE 로 세션을 끝낸다. 새로고침·탭 닫기에는 보내지 않는다(이어 올리기 위해).
 */
export const CHUNK_UPLOAD_THRESHOLD = 32 * 1024 * 1024;

const DEFAULT_CONCURRENCY = 4;
const PART_ATTEMPTS = 4; // 첫 시도 + 재시도 3번
const RETRY_BASE_MS = 1000;
const COMPLETE_POLL_MS = 2000;
const COMPLETE_POLL_LIMIT = 150; // 2초 × 150 = 5분
const STORE_KEY = 'drive.chunkUploads';
const STORE_TTL_MS = 7 * 24 * 60 * 60 * 1000;

/** 업로드를 멈춘 이유 — 호출부가 상태 표시를 고른다 */
export class ChunkUploadError extends Error {
    constructor(status, errorCode, message) {
        super(message || errorCode || `chunk upload failed (${status})`);
        this.status = status;
        this.errorCode = errorCode;
    }
}

const CANCELLED = Symbol('CHUNK_UPLOAD_CANCELLED');

const sleep = (ms, signal) =>
    new Promise(resolve => {
        const timer = setTimeout(resolve, ms);
        signal?.addEventListener(
            'abort',
            () => {
                clearTimeout(timer);
                resolve();
            },
            { once: true }
        );
    });

// ------------------------------------------------------------------ 이어 올리기 저장소(세션 ID 만 — 토큰·파일 내용 없음)

const readStore = () => {
    try {
        const raw = window.localStorage.getItem(STORE_KEY);
        const parsed = raw ? JSON.parse(raw) : {};
        return parsed && typeof parsed === 'object' ? parsed : {};
    } catch {
        return {};
    }
};

const writeStore = store => {
    try {
        const now = Date.now();
        const pruned = Object.fromEntries(
            Object.entries(store).filter(([, v]) => v?.savedAt && now - v.savedAt < STORE_TTL_MS)
        );
        window.localStorage.setItem(STORE_KEY, JSON.stringify(pruned));
    } catch {
        // 저장소를 못 쓰면 이어 올리기만 안 될 뿐 업로드는 계속
    }
};

const rememberSession = (fingerprint, sessionId) => {
    const store = readStore();
    store[fingerprint] = { sessionId, savedAt: Date.now() };
    writeStore(store);
};

const forgetSession = fingerprint => {
    const store = readStore();
    if (store[fingerprint]) {
        delete store[fingerprint];
        writeStore(store);
    }
};

/** 같은 파일·같은 대상이면 같은 지문 — 다른 위치로 올리는 같은 파일은 다른 세션 */
const fingerprintOf = (file, target) =>
    [
        file.name,
        file.size,
        file.lastModified,
        target.parentFolderSeq ?? '',
        target.resourceSeq ?? '',
        target.saveType ?? '',
        target.sharedByUserSeq ?? '',
    ].join('|');

// ------------------------------------------------------------------ API

/** api 헬퍼는 실패 시 서버 오류 본문({code, errorCode, message})을, 네트워크 오류·중단이면 undefined 를 돌려준다 */
const toError = (res, signal) => {
    if (signal?.aborted) return CANCELLED;
    if (!res) return new ChunkUploadError(0, 'NETWORK', 'network error');
    // 오류 본문(ApiExceptionResponse)은 HTTP 상태를 code 에 담는다. 본문이 JSON 이 아니면(앞단 프록시 413 HTML 등) -1
    const status = typeof res === 'object' ? (res.code ?? res.status ?? -1) : -1;
    return new ChunkUploadError(status, res?.errorCode, res?.message);
};

/**
 * api.Post 는 평소 응답 본문(ApiResponse)을 돌려주지만, 401 → 토큰 재발급 → 재시도가 일어나면 Axios 응답 전체를 돌려준다
 * (utils/api.js handleAuthError). 둘 다 본문으로 맞춘다 — 안 맞추면 성공한 세션 생성·complete 를 실패로 본다.
 */
const postBody = async (url, body, config) => {
    const res = await api.Post(url, body, null, config);
    const isAxiosResponse = res && typeof res === 'object' && 'config' in res && 'headers' in res;
    return isAxiosResponse ? res.data : res;
};

const startSession = async (file, target, signal) => {
    const res = await postBody(
        'uploads',
        {
            sharedByUserSeq: target.sharedByUserSeq ?? undefined,
            parentFolderSeq: target.parentFolderSeq ?? undefined,
            resourceSeq: target.resourceSeq ?? undefined,
            saveType: target.saveType ?? undefined,
            fileName: file.name,
            size: file.size,
        },
        { signal }
    );
    const data = res?.data;
    if (!data?.sessionId) {
        const err = toError(res, signal);
        if (err !== CANCELLED) err.atStart = true;
        throw err;
    }
    return {
        sessionId: data.sessionId,
        partSize: data.partSize,
        partCount: data.partCount,
        concurrency: data.maxConcurrentParts || DEFAULT_CONCURRENCY,
        uploadedParts: [],
    };
};

/**
 * 세션 상태. 없으면(404 — 남의 세션·내부 쓰기 세션 포함) null.
 * 조회 자체가 실패하면(네트워크·5xx) 던진다 — "세션이 끝났다"와 구분해야 이어 올릴 세션을 버리지 않는다.
 */
const getSession = async (sessionId, signal) => {
    const res = await api.Get(`uploads/${sessionId}`, { signal });
    const session = res?.data?.data;
    if (session) return session;
    const err = toError(res, signal);
    if (err === CANCELLED) throw CANCELLED;
    if (err.status === 404) return null;
    throw err;
};

/**
 * 남은 세션으로 이어 갈 수 있는지.
 * - PENDING 이고 이 파일과 맞으면 이어 올릴 계획
 * - COMPLETING·COMPLETED 면 { finishing: true } — complete 를 이미 보냈다(새로고침 등). 새로 올리면 같은 파일이 두 번 저장되므로 결과만 기다린다
 * - 끝났거나(FAILED·없음) 다른 파일이면 null(새로 시작). 조회 실패는 던진다
 */
const resumeSession = async (sessionId, file, signal) => {
    const s = await getSession(sessionId, signal);
    if (!s || s.size !== file.size || s.fileName !== file.name) return null;
    if (s.status === 'COMPLETING' || s.status === 'COMPLETED') return { finishing: true };
    if (s.status !== 'PENDING' || !s.partSize || !s.partCount) return null;
    return {
        sessionId,
        partSize: s.partSize,
        partCount: s.partCount,
        concurrency: DEFAULT_CONCURRENCY,
        uploadedParts: s.uploadedParts || [],
    };
};

/** 서버 오류 중 다시 보내면 될 수도 있는 것 — 네트워크 오류·5xx·429 */
const isRetryable = err => err.status === 0 || err.status >= 500 || err.status === 429;

const putPart = async (sessionId, partNumber, blob, signal, onPartProgress) => {
    for (let attempt = 1; ; attempt++) {
        const res = await api.Put(`uploads/${sessionId}/parts/${partNumber}`, blob, null, {
            signal,
            headers: { 'Content-Type': 'application/octet-stream' },
            onUploadProgress: e => onPartProgress(e?.loaded || 0),
        });
        if (res?.status === 200) return;
        const err = toError(res, signal);
        if (err === CANCELLED) throw CANCELLED;
        if (!isRetryable(err) || attempt >= PART_ATTEMPTS) throw err;
        onPartProgress(0);
        await sleep(RETRY_BASE_MS * 2 ** (attempt - 1), signal);
        if (signal?.aborted) throw CANCELLED;
    }
};

/** 이미 올라간 파트를 빼고 나머지를 동시 concurrency 개로 보낸다. 하나라도 재시도 불가 실패면 나머지를 멈추고 그 오류를 던진다 */
const uploadParts = async (file, plan, signal, onProgress) => {
    const done = new Set(plan.uploadedParts);
    const pending = [];
    for (let n = 1; n <= plan.partCount; n++) {
        if (!done.has(n)) pending.push(n);
    }

    const partBytes = n => Math.min(plan.partSize, file.size - (n - 1) * plan.partSize);
    let confirmed = [...done].reduce((sum, n) => sum + partBytes(n), 0);
    const inFlight = new Map(); // partNumber → 전송 중 바이트
    const report = () => {
        let sending = 0;
        inFlight.forEach(v => (sending += v));
        onProgress?.(Math.min(file.size, confirmed + sending), file.size);
    };
    report();

    // 한 파트가 실패하면 나머지 파트 전송도 끊는다(호출자의 signal 과 별개)
    const stop = new AbortController();
    const onAbort = () => stop.abort();
    signal?.addEventListener('abort', onAbort, { once: true });
    let failure = null;

    const worker = async () => {
        while (pending.length > 0 && !failure && !stop.signal.aborted) {
            const n = pending.shift();
            const start = (n - 1) * plan.partSize;
            const blob = file.slice(start, start + partBytes(n));
            try {
                await putPart(plan.sessionId, n, blob, stop.signal, loaded => {
                    inFlight.set(n, loaded);
                    report();
                });
                inFlight.delete(n);
                confirmed += partBytes(n);
                report();
            } catch (e) {
                inFlight.delete(n);
                if (!failure) failure = e;
                stop.abort();
            }
        }
    };

    try {
        const workers = Array.from({ length: Math.min(plan.concurrency, pending.length) }, worker);
        await Promise.all(workers);
    } finally {
        signal?.removeEventListener('abort', onAbort);
    }
    if (signal?.aborted) throw CANCELLED;
    if (failure) throw failure === CANCELLED ? CANCELLED : failure;
};

/** complete — 재요청해도 안전(완료된 세션은 같은 결과). 처리 중(409 UPLOAD_005)이면 상태를 읽어 끝날 때까지 기다린다 */
const completeSession = async sessionId => {
    for (let attempt = 1; attempt <= PART_ATTEMPTS; attempt++) {
        const res = await postBody(`uploads/${sessionId}/complete`, null);
        if (res?.data?.resourceSeq) return res.data;
        const err = toError(res);
        if (err.errorCode === 'UPLOAD_005') return waitForCompletion(sessionId);
        if (!isRetryable(err)) throw err;
        await sleep(RETRY_BASE_MS * 2 ** (attempt - 1));
    }
    // 응답을 못 받았지만 서버는 끝냈을 수 있다 — 상태로 확인
    return waitForCompletion(sessionId);
};

const waitForCompletion = async sessionId => {
    for (let i = 0; i < COMPLETE_POLL_LIMIT; i++) {
        let s;
        try {
            s = await getSession(sessionId);
        } catch (e) {
            // 일시적인 조회 실패는 다음 조회까지 기다린다
            if (!(e instanceof ChunkUploadError) || !isRetryable(e)) throw e;
            await sleep(COMPLETE_POLL_MS);
            continue;
        }
        if (s === null) throw new ChunkUploadError(404, 'UPLOAD_003', 'upload session not found');
        if (s?.status === 'COMPLETED') {
            return { resourceSeq: s.resourceSeq, resourceName: s.resourceName };
        }
        if (s?.status === 'FAILED') {
            throw new ChunkUploadError(410, 'UPLOAD_006', s.failReason);
        }
        await sleep(COMPLETE_POLL_MS);
    }
    throw new ChunkUploadError(0, 'NETWORK', 'complete timed out');
};

const cancelSession = sessionId => {
    // 취소 응답은 기다리지 않는다 — 실패해도 서버 스케줄러가 만료시킨다
    api.Delete(`uploads/${sessionId}`).catch(() => {});
};

/**
 * 이어 올리지 않기로 한 세션을 정리한다 — 아직 PENDING 이면 DELETE.
 * 409·410·400(UPLOAD_009)처럼 서버가 이미 끝낸 세션은 그대로 두고, 앞단 프록시 413·권한 오류처럼 서버 세션은 살아 있는데
 * 클라이언트가 포기하는 경우만 취소한다(안 하면 만료까지 예약 용량·세션 수를 잡는다). 조회가 안 되면 스케줄러에 맡긴다.
 */
const abandonSession = async sessionId => {
    try {
        const s = await getSession(sessionId);
        if (s?.status === 'PENDING') cancelSession(sessionId);
    } catch {
        // 만료 스케줄러가 정리
    }
};

/** 다시 시도해도 소용없는 서버 거절(네트워크·5xx·429 가 아님) */
const isFinalRejection = e => e instanceof ChunkUploadError && e.status !== 0 && !isRetryable(e);

// ------------------------------------------------------------------ 진입점

/**
 * 파일 하나를 청크로 올린다.
 *
 * @param {File} file
 * @param {{parentFolderSeq?: number, resourceSeq?: number, saveType?: 'DUPLICATE'|'VERSION'|'OVERWRITE', sharedByUserSeq?: number}} target
 * @param {{signal?: AbortSignal, onProgress?: (loaded:number, total:number) => void, onPartsSent?: () => void}} options
 *   onPartsSent: 모든 파트 전송이 끝나 complete 를 보내기 직전 — 이 뒤로는 취소할 수 없다
 * @returns {Promise<{resourceSeq:number, resourceName:string} | null>} 취소되면 null
 * @throws {ChunkUploadError}
 */
export async function uploadInChunks(file, target, { signal, onProgress, onPartsSent } = {}) {
    const fingerprint = fingerprintOf(file, target);
    let plan = null;
    // 이어 올리려던 세션 — 조회 도중 취소돼도(plan 이 아직 없다) 이 세션을 정리해야 한다
    let resumingId = null;
    // complete 를 이미 보낸 세션(새로고침 뒤 다시 고름) — 파트를 보내지 않고 결과만 기다린다
    let finishingOnly = false;
    try {
        const saved = readStore()[fingerprint];
        if (saved?.sessionId) {
            resumingId = saved.sessionId;
            const resumed = await resumeSession(saved.sessionId, file, signal);
            if (resumed?.finishing) {
                plan = { sessionId: saved.sessionId };
                finishingOnly = true;
            } else if (resumed) {
                plan = resumed;
            } else {
                forgetSession(fingerprint);
                resumingId = null;
            }
        }
        if (signal?.aborted) throw CANCELLED;
        if (!plan) {
            plan = await startSession(file, target, signal);
            rememberSession(fingerprint, plan.sessionId);
        }
        if (signal?.aborted) throw CANCELLED;

        if (!finishingOnly) await uploadParts(file, plan, signal, onProgress);
    } catch (e) {
        if (e === CANCELLED) {
            // PENDING 이 아니면(이미 complete 중·끝남) 서버가 DELETE 를 무시하고 현재 상태로 답한다
            const id = plan?.sessionId ?? resumingId;
            if (id) cancelSession(id);
            forgetSession(fingerprint);
            return null;
        }
        // 다시 시도해도 안 되는 거절 — 다음엔 새로 시작하고, 서버 세션이 아직 살아 있으면 취소.
        // 네트워크·5xx(조회 실패 포함)면 이어 올릴 수 있게 세션과 지문을 남긴다
        if (isFinalRejection(e)) {
            forgetSession(fingerprint);
            if (plan) await abandonSession(plan.sessionId);
        }
        throw e;
    }

    onPartsSent?.();
    try {
        const result = finishingOnly
            ? await waitForCompletion(plan.sessionId)
            : await completeSession(plan.sessionId);
        forgetSession(fingerprint);
        return result;
    } catch (e) {
        if (isFinalRejection(e)) {
            forgetSession(fingerprint);
            await abandonSession(plan.sessionId);
        }
        throw e;
    }
}

/**
 * 서버가 세션 생성에서 "단일 요청 기준 미만"(400 UPLOAD_008)으로 거절했는지 — 설치처가
 * `drive.upload.single-request-threshold` 를 CHUNK_UPLOAD_THRESHOLD 보다 크게 잡은 경우. 호출부는 단일 요청(POST /files)으로 보낸다.
 */
export const isBelowServerChunkThreshold = e =>
    e instanceof ChunkUploadError && e.atStart === true && e.errorCode === 'UPLOAD_008';

/** 내 진행 중(PENDING) 청크 업로드 목록 */
export async function listPendingUploads() {
    const res = await api.Get('uploads', { params: { status: 'PENDING' } });
    return Array.isArray(res?.data?.data) ? res.data.data : null;
}

/** 진행 중 업로드 취소 — 목록 화면용. 성공 여부를 돌려준다 */
export async function cancelPendingUpload(sessionId) {
    const res = await api.Delete(`uploads/${sessionId}`);
    return res?.status === 200;
}
