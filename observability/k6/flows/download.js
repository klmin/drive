// 흐름: 내려받기 — 프론트(DriveFront hooks/useDriveDownload.js)와 같은 순서: 다운로드 토큰 발급 → 받은 링크(linkUrl)로 GET
//   작은 파일: seed 파일(수 KB) — GET /api/v1/files/{id}/download-token → /api/v1/files/download?dt=…
//   큰 파일:  VU 마다 처음 한 번 청크 API 로 올려 두고(k6-download-large-*) 계속 재사용 — 지우지 않는다
//   폴더 zip: seed 폴더(파일 20개) — GET /api/v1/folders/{id}/download-token → /api/v1/folders/download?dt=… (서버가 zip 으로 묶어 스트리밍)
// 본문은 k6 메모리에 담지 않는다(responseType none) — 받은 양은 k6 data_received 로 센다. 크기는 Content-Length 로 확인한다.
import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { Trend } from 'k6/metrics';
import { BASE_URL, loginAsVuUser, request, data, userId } from '../lib/drive.js';
import { uploadKeptFile } from './upload.js';

const MB = 1024 * 1024;
const LARGE_PREFIX = 'k6-download-large-';
// 다운로드 한 건의 제한 시간 — 스트림이 중간에 멈추면(서버가 연결을 닫지 않고 끊김) 상태는 이미 200 이라 이 시간이 지나야 실패로 잡힌다.
//   길게 두면 멈춘 VU 가 테스트 끝까지 묶여 아무 결과도 남기지 않는다(반복이 끝나지 않아 체크도 기록되지 않음)
const DOWNLOAD_TIMEOUT = __ENV.DOWNLOAD_TIMEOUT || '120s';

// 토큰 발급부터 마지막 바이트까지 — 사용자가 "다운로드" 를 누르고 파일을 다 받을 때까지(kind: small / large / zip)
const downloadDuration = new Trend('download_file_duration', true);

let loggedInAs = null;
let me = null; // { smallFiles: [{seq, size}], folders: [seq], large: {seq, size} | null }

function listRoot() {
  return data(request('GET', '/api/v1/resources?st=MY&ps=100', 'list'));
}

// 큰 파일 준비 — 같은 크기의 것이 있으면 재사용, 크기가 다른 이전 것은 지운다(사용자 용량 100MB 안에 두려고)
function ensureLarge(root, largeMb) {
  const size = Math.round(largeMb * MB);
  let large = null;
  for (const r of root.filter((x) => x.resourceType === 'FILE' && String(x.resourceName).startsWith(LARGE_PREFIX))) {
    if (!large && r.sizeByte === size) large = { seq: r.resourceSeq, size };
    else request('DELETE', `/api/v1/files/${r.resourceSeq}`, 'delete_file');
  }
  if (large) return large;
  const seq = uploadKeptFile(`${LARGE_PREFIX}${largeMb}mb.txt`, largeMb);
  return seq ? { seq, size } : null;
}

// 처음 한 번 — 실패하면 false(다음 반복에서 다시). seed 파일이 없으면 테스트를 멈춘다
function prepare(largeMb) {
  if (loggedInAs === null) loggedInAs = loginAsVuUser(__VU);
  const root = listRoot();
  if (root === null) return false;
  const smallFiles = root
    .filter((r) => r.resourceType === 'FILE' && !String(r.resourceName).startsWith(LARGE_PREFIX))
    .map((r) => ({ seq: r.resourceSeq, size: r.sizeByte }));
  const folders = root.filter((r) => r.resourceType === 'FOLDER').map((r) => r.resourceSeq);
  if (smallFiles.length === 0 || folders.length === 0) exec.test.abort(`${userId(loggedInAs)}: seed 파일·폴더가 없다 — seed.js 를 먼저 실행`);
  let large = null;
  if (largeMb > 0) {
    large = ensureLarge(root, largeMb);
    if (!large) return false;
  }
  me = { smallFiles, folders, large };
  return true;
}

function pick(list) {
  return list[Math.floor(Math.random() * list.length)];
}

// 토큰 발급 → 링크로 받기. expectSize 가 있으면 Content-Length 를 확인한다
function downloadVia(tokenPath, kind, expectSize) {
  const started = Date.now();
  const link = data(request('GET', tokenPath, `download_token_${kind === 'zip' ? 'folder' : 'file'}`));
  if (!link || !link.linkUrl) return;
  // linkUrl 은 API 기준 상대경로(/files/download?dt=…) — 프론트처럼 /api/v1 뒤에 붙인다(DriveFront getBaseURL() = …/api/v1).
  // 링크는 서명 토큰(dt)으로 인증한다 — 프론트도 쿠키·헤더 없이 window.location 으로 연다.
  // 리다이렉트는 따라가지 않는다 — 잘못된 경로·만료 토큰은 302 → 에러 페이지(200)라, 따라가면 실패가 성공으로 보인다
  const res = http.get(`${BASE_URL}/api/v1${link.linkUrl}`, {
    redirects: 0,
    responseType: 'none',
    timeout: DOWNLOAD_TIMEOUT,
    tags: { api: `download_${kind}`, name: `download_${kind}` },
  });
  const ok = check(res, {
    [`download_${kind} 200`]: (r) => r.status === 200,
    [`download_${kind} 크기`]: (r) => expectSize === undefined || Number(r.headers['Content-Length']) === expectSize,
  });
  if (ok) downloadDuration.add(Date.now() - started, { kind });
}

function ready(largeMb) {
  return me !== null || prepare(largeMb);
}

export function downloadSmall(largeMb = 0) {
  if (!ready(largeMb)) return;
  const f = pick(me.smallFiles);
  downloadVia(`/api/v1/files/${f.seq}/download-token`, 'small', f.size);
}

export function downloadLarge(largeMb) {
  if (!ready(largeMb) || !me.large) return;
  downloadVia(`/api/v1/files/${me.large.seq}/download-token`, 'large', me.large.size);
}

export function downloadZip(largeMb = 0) {
  if (!ready(largeMb)) return;
  downloadVia(`/api/v1/folders/${pick(me.folders)}/download-token`, 'zip'); // zip 크기는 서버가 만들 때 정해진다
}
