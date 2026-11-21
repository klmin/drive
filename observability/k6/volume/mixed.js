// 혼합 부하 + 메모리 — 둘러보기(읽기) + 업로드(작은 파일 512KB 70%, 40MB 청크 30%) + 다운로드(512KB 70%, 큰 파일 30%)를 동시에.
// 다운로드할 파일은 VU 가 처음에 직접 올려 둔다(seed-volume.sql 데이터는 DB 행만 있고 스토리지 객체가 없어서).
// 같은 부하를 WAS 힙(-Xmx)만 바꿔 돌려 힙 사용·GC·실패를 비교한다. 업로드한 만큼 스토리지 디스크를 쓰므로 여유 공간을 본다.
// env: READ, UP, DOWN(VU 수, 기본 20·4·6), LARGE_MB(큰 파일 크기, 기본 40), DUR(기본 180s)
// 용량: seed 사용자 한도(100MiB)는 seed-volume.sql 이 이미 약 60MB 를 채워서 40MB 업로드가 걸린다 — 실행 전에 README 의 한도 올리기 SQL 을 먼저 실행한다.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, loginAsVuUser, request, data } from '../lib/drive.js';
import { browse } from '../flows/browse.js';
import { smallUpload, chunkUpload, uploadKeptFile } from '../flows/upload.js';

const DUR = __ENV.DUR || '180s';
const LARGE_MB = parseFloat(__ENV.LARGE_MB || '40');
export const options = {
  tags: { testid: __ENV.TESTID || 'mixed' },
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

const SMALL_BYTES = 512 * 1024;
const LARGE_BYTES = Math.round(LARGE_MB * 1024 * 1024);

// 이전 실행이 남긴 이 VU 의 다운로드용 파일을 찾는다 — 크기가 맞으면 재사용, 아니면 지운다.
// 힙만 바꿔 같은 부하를 반복할 때 파일이 쌓여 용량을 채우거나 부하가 달라지지 않게.
// 이름에 VU 번호를 넣어(mixed-small-3-…) 같은 사용자를 쓰는 다른 VU 의 파일과 섞이지 않게 한다.
function reuseOrClean(prefix, size) {
  const found = data(request('GET', `/api/v1/resources?st=MY&ps=100&sk=${encodeURIComponent(prefix)}`, 'list_kept')) || [];
  let keep = null;
  for (const r of found.filter((x) => x.resourceType === 'FILE' && String(x.resourceName).startsWith(prefix))) {
    if (!keep && r.sizeByte === size) keep = r.resourceSeq;
    else request('DELETE', `/api/v1/files/${r.resourceSeq}`, 'delete_file');
  }
  return keep;
}

let files = null;
export function down() {
  if (!files) {
    loginAsVuUser(__VU);
    let small = reuseOrClean(`mixed-small-${__VU}-`, SMALL_BYTES);
    if (!small) {
      // 32MB 미만은 청크 API 가 받지 않는다 — 기존 업로드 API(multipart)로 512KB 를 올려 둔다
      const file = http.file('x'.repeat(SMALL_BYTES), `mixed-small-${__VU}-${Date.now()}.txt`, 'text/plain');
      const saved = data(request('POST', '/api/v1/files', 'upload_kept_small', { file }, { expect: 201 }));
      small = saved && saved.resourceSeq;
    }
    const large = reuseOrClean(`mixed-large-${__VU}-`, LARGE_BYTES) || uploadKeptFile(`mixed-large-${__VU}-${Date.now()}.txt`, LARGE_MB);
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
