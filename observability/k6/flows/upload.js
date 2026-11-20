// 흐름: 파일 올리기 — 작은 파일(한 요청)과 큰 파일(청크 업로드 API), 올린 뒤 바로 영구 삭제
//
// 청크 업로드(docs/design/upload-session-redesign.md §5.3):
//   POST /api/v1/uploads(세션·파트 계획) → PUT .../parts/{n}(파트 바이트 그대로, 동시에 maxConcurrentParts 개) → POST .../complete
//   취소는 DELETE /api/v1/uploads/{id} — 서버가 멀티파트 abort 를 정리 대기열에 등록한다.
// 올린 파일은 DELETE /api/v1/files/{id}(휴지통을 거치지 않는 영구 삭제)로 지운다 — 사용자 용량(seed 100MB)이 차지 않게, 삭제 대기열도 같이 지나가게.
//   부하 테스트 실행 구성은 배치 스케줄러가 꺼져 있어 스토리지 객체는 실제로 지워지지 않고 tb_storage_deletion 에 쌓인다.
import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';
import { BASE_URL, loginAsVuUser, request, roleHeaders, data } from '../lib/drive.js';

const MB = 1024 * 1024;

// k6 쪽 커스텀 지표 — 파일 하나를 올리는 데 걸린 전체 시간(첫 요청~마지막 응답). 사용자가 체감하는 시간
//   Prometheus 에는 k6_upload_file_duration_p95 등으로 간다(kind 태그: small / chunk)
const uploadFileDuration = new Trend('upload_file_duration', true);

let loggedIn = false;
let partBuffer = null; // VU 마다 파트 크기만큼 한 번 만든다(반복마다 수십 MB 를 새로 만들지 않게)

function ensureLogin() {
  if (!loggedIn) {
    loginAsVuUser(__VU);
    loggedIn = true;
  }
}

// 텍스트 바이트(.txt 로 올린다) — 서버는 내용이 아니라 크기·파트 수를 본다
function textBytes(size) {
  const line = new Uint8Array(64);
  for (let i = 0; i < 63; i++) line[i] = 97 + (i % 26);
  line[63] = 10;
  const buf = new Uint8Array(size);
  for (let i = 0; i < size; i += 64) buf.set(line.subarray(0, Math.min(64, size - i)), i);
  return buf.buffer;
}

function fileName(kind) {
  return `k6-${kind}-${__VU}-${__ITER}-${Date.now()}.txt`;
}

function deletePermanently(resourceSeq) {
  if (resourceSeq) request('DELETE', `/api/v1/files/${resourceSeq}`, 'delete_file');
}

function cancel(sessionId) {
  request('DELETE', `/api/v1/uploads/${sessionId}`, 'upload_cancel');
}

// 작은 파일 — 기존 업로드 API(multipart 한 요청). 서버 안에서는 같은 업로드 세션 코드(kind=server)를 탄다
export function smallUpload(sizeKb = 512) {
  ensureLogin();
  const started = Date.now();
  const file = http.file(textBytes(Math.round(sizeKb * 1024)), fileName('small'), 'text/plain');
  const res = request('POST', '/api/v1/files', 'upload_small', { file }, { expect: 201 });
  const saved = data(res);
  if (saved) uploadFileDuration.add(Date.now() - started, { kind: 'small' });
  deletePermanently(saved && saved.resourceSeq);
}

// 큰 파일 — 청크 업로드 API. cancelRate 비율만큼은 절반쯤 보내다가 취소한다(abort 정리 경로)
export function chunkUpload(sizeMb = 40, cancelRate = 0.1) {
  ensureLogin();
  const size = Math.round(sizeMb * MB);
  const started = Date.now();

  const plan = data(request('POST', '/api/v1/uploads', 'upload_start', JSON.stringify({ fileName: fileName('chunk'), size }), {
    expect: 201,
    headers: { 'Content-Type': 'application/json' },
  }));
  if (!plan) return;
  const { sessionId, partSize, partCount, maxConcurrentParts } = plan;

  if (!partBuffer || partBuffer.byteLength !== partSize) partBuffer = textBytes(partSize);
  const lastSize = size - partSize * (partCount - 1);
  const lastBuffer = lastSize === partSize ? partBuffer : partBuffer.slice(0, lastSize);

  const canceling = Math.random() < cancelRate;
  const sendUpTo = canceling ? Math.max(1, Math.floor(partCount / 2)) : partCount;
  const params = {
    headers: roleHeaders('USER', { 'Content-Type': 'application/octet-stream' }),
    tags: { api: 'upload_part', name: 'upload_part' },
    timeout: '120s',
  };

  // 브라우저처럼 maxConcurrentParts 개씩 동시에
  for (let first = 1; first <= sendUpTo; first += maxConcurrentParts) {
    const batch = [];
    for (let n = first; n < Math.min(first + maxConcurrentParts, sendUpTo + 1); n++) {
      batch.push({ method: 'PUT', url: `${BASE_URL}/api/v1/uploads/${sessionId}/parts/${n}`, body: n === partCount ? lastBuffer : partBuffer, params });
    }
    let ok = true;
    for (const res of http.batch(batch)) {
      ok = check(res, { 'upload_part 200': (r) => r.status === 200 }) && ok;
    }
    if (!ok) {
      cancel(sessionId); // 실패한 세션을 남기지 않는다(브라우저도 포기하면 취소)
      return;
    }
  }

  if (canceling) {
    cancel(sessionId);
    return;
  }

  const done = data(request('POST', `/api/v1/uploads/${sessionId}/complete`, 'upload_complete', null, { timeout: '120s' }));
  if (done) uploadFileDuration.add(Date.now() - started, { kind: 'chunk' });
  deletePermanently(done && done.resourceSeq);
}
