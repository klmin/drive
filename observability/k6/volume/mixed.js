// 혼합 부하 + 메모리 — 둘러보기(읽기) + 업로드(작은 파일 512KB 70%, 40MB 청크 30%) + 다운로드(512KB 70%, 큰 파일 30%)를 동시에.
// 다운로드할 파일은 VU 가 처음에 직접 올려 둔다(seed-volume.sql 데이터는 DB 행만 있고 스토리지 객체가 없어서).
// 같은 부하를 WAS 힙(-Xmx)만 바꿔 돌려 힙 사용·GC·실패를 비교한다. 업로드한 만큼 스토리지 디스크를 쓰므로 여유 공간을 본다.
// env: READ, UP, DOWN(VU 수, 기본 20·4·6), LARGE_MB(큰 파일 크기, 기본 40), DUR(기본 180s)
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, loginAsVuUser, request, data } from '../lib/drive.js';
import { browse } from '../flows/browse.js';
import { smallUpload, chunkUpload, uploadKeptFile } from '../flows/upload.js';

const DUR = __ENV.DUR || '180s';
const LARGE_MB = parseFloat(__ENV.LARGE_MB || '40');
export const options = {
  noCookiesReset: true,
  scenarios: {
    read: { executor: 'constant-vus', vus: parseInt(__ENV.READ || '20'), duration: DUR, exec: 'read' },
    up: { executor: 'constant-vus', vus: parseInt(__ENV.UP || '4'), duration: DUR, exec: 'up' },
    down: { executor: 'constant-vus', vus: parseInt(__ENV.DOWN || '6'), duration: DUR, exec: 'down', gracefulStop: '120s' },
  },
  thresholds: {
    'http_req_duration{scenario:read}': ['p(95)<100000'],
    'http_req_failed{scenario:read}': ['rate<=1'], 'http_req_failed{scenario:up}': ['rate<=1'], 'http_req_failed{scenario:down}': ['rate<=1'],
    'upload_file_duration{kind:small}': ['p(95)>=0'], 'upload_file_duration{kind:chunk}': ['p(95)>=0'],
    'http_req_duration{api:download_small}': ['p(95)>=0'], 'http_req_duration{api:download_large}': ['p(95)>=0'],
    'http_reqs{api:download_large}': ['count>=0'],
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

export function read() { browse(1); }

export function up() {
  if (Math.random() < 0.7) smallUpload(512); else chunkUpload(40, 0);
  sleep(1);
}

let files = null;
export function down() {
  if (!files) {
    loginAsVuUser(__VU);
    // 32MB 미만은 청크 API 가 받지 않는다 — 기존 업로드 API(multipart)로 512KB 를 올려 둔다
    const file = http.file('x'.repeat(512 * 1024), `mixed-small-${__VU}-${Date.now()}.txt`, 'text/plain');
    const saved = data(request('POST', '/api/v1/files', 'upload_kept_small', { file }, { expect: 201 }));
    const small = saved && saved.resourceSeq;
    const large = uploadKeptFile(`mixed-large-${__VU}-${Date.now()}.txt`, LARGE_MB);
    if (!small || !large) { sleep(5); return; }
    files = { small, large };
  }
  const large = Math.random() < 0.3;
  const seq = large ? files.large : files.small;
  const link = data(request('GET', `/api/v1/files/${seq}/download-token`, 'download_token_file'));
  if (link && link.linkUrl) {
    const res = http.get(`${BASE_URL}/api/v1${link.linkUrl}`, { redirects: 0, responseType: 'none', timeout: '120s',
      tags: { api: large ? 'download_large' : 'download_small', name: large ? 'download_large' : 'download_small' } });
    check(res, { 'download 200': (r) => r.status === 200 });
  }
  sleep(1);
}
