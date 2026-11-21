// 동시 다운로드 수 측정 — 큰 파일을 VU 수만큼 "끊임없이" 받아 동시 전송 수를 VU 수로 고정한다.
//   S3 커넥션 풀(storage.http.max-connections)·다운로드 스트림 상한(drive.download.stream.max-threads, 기본 200)이
//   설계대로 동작하는지(상한까지 받고 넘치면 503), 동시 수를 올릴 때 1인당 속도·전체 처리량이 어떻게 변하는지 본다.
//
//   k6 run -e BASE_URL=http://localhost:8082 -e VUS=100 -e DUR=90s download-concurrency.js
//   파일 크기: -e LARGE_MB=128 (기본). 한 건이 오래 걸릴수록 동시 수가 VU 수에 가깝게 유지된다
//
// download.js 의 large 시나리오와 다른 점
//   - 큰 파일을 setup() 에서 사용자마다 한 번만 준비한다 — VU 가 사용자 수(18)보다 많으면 같은 사용자의 VU 들이
//     동시에 올리고 서로의 파일을 지우는 경합이 있어서(download.js ensureLarge)
//   - 503(상한 초과 거절)을 실패가 아니라 따로 센다 — 상한을 넘기는 단계에서는 기대한 결과다
//   - 생각 시간 없음(THINK=0) — VU 하나 = 전송 중인 다운로드 하나
import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { BASE_URL, USER_COUNT, data, login, loginAsVuUser, request, userId } from './lib/drive.js';
import { uploadKeptFile } from './flows/upload.js';

const MB = 1024 * 1024;
const LARGE_MB = parseFloat(__ENV.LARGE_MB || '128');
const SIZE = Math.round(LARGE_MB * MB);
const NAME = `k6-dlc-${LARGE_MB}mb.txt`;
const VUS = parseInt(__ENV.VUS || '50', 10);
const DUR = __ENV.DUR || '90s';
const DOWNLOAD_TIMEOUT = __ENV.DOWNLOAD_TIMEOUT || '600s';

const downloadSeconds = new Trend('dlc_download_seconds'); // 토큰 발급 ~ 마지막 바이트(성공 건)
const downloadMbps = new Trend('dlc_download_mbps'); // 한 건의 속도 MB/s(성공 건) — 1인당 체감 속도
const rejected = new Counter('dlc_rejected_503'); // 상한 초과로 거절(503)
const failed = new Counter('dlc_failed'); // 503 이 아닌 실패(500, 타임아웃, 크기 불일치 등)
const okCount = new Counter('dlc_ok');

export const options = {
  tags: { testid: __ENV.TESTID || `dlc-vu${VUS}` },
  noCookiesReset: true,
  setupTimeout: '15m',
  scenarios: {
    dl: { executor: 'constant-vus', vus: VUS, duration: DUR, gracefulStop: __ENV.GRACEFUL || '10m', exec: 'dl' },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'max', 'count'],
};

// 사용자마다 큰 파일 하나 — 있으면 재사용, 없으면 올린다. 다른 크기의 이전 파일은 지우지 않는다(다른 측정이 쓸 수 있음)
export function setup() {
  const files = {};
  for (let i = 1; i <= USER_COUNT; i++) {
    login('USER', userId(i));
    const root = data(request('GET', '/api/v1/resources?st=MY&ps=100', 'list')) || [];
    const found = root.find((r) => r.resourceType === 'FILE' && r.resourceName === NAME && r.sizeByte === SIZE);
    const seq = found ? found.resourceSeq : uploadKeptFile(NAME, LARGE_MB);
    if (!seq) throw new Error(`${userId(i)}: ${NAME} 준비 실패`);
    files[i] = seq;
  }
  return { files };
}

let myIndex = null;

export function dl(setupData) {
  if (myIndex === null) myIndex = loginAsVuUser(__VU);
  const seq = setupData.files[myIndex];
  const started = Date.now();
  const link = data(request('GET', `/api/v1/files/${seq}/download-token`, 'download_token_file'));
  if (!link || !link.linkUrl) {
    failed.add(1, { stage: 'token' });
    return;
  }
  const res = http.get(`${BASE_URL}/api/v1${link.linkUrl}`, {
    redirects: 0,
    responseType: 'none',
    timeout: DOWNLOAD_TIMEOUT,
    tags: { api: 'download_large', name: 'download_large' },
    responseCallback: http.expectedStatuses(200, 503),
  });
  if (res.status === 503) {
    rejected.add(1);
    return;
  }
  const ok = check(res, {
    'download 200': (r) => r.status === 200,
    'download 크기': (r) => Number(r.headers['Content-Length']) === SIZE,
  });
  if (!ok || res.error) {
    failed.add(1, { status: String(res.status) });
    return;
  }
  const sec = (Date.now() - started) / 1000;
  okCount.add(1);
  downloadSeconds.add(sec);
  downloadMbps.add(LARGE_MB / sec);
}
