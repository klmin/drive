// 프론트 안내값(maxConcurrentParts=4)을 무시하고 한 세션의 파트를 한꺼번에 보내는 클라이언트 — Tomcat 요청 스레드 고갈 확인용
//   abuser: 세션 하나를 열고 PARTS 개 파트를 동시에 PUT → 취소(complete 하지 않아 조립·용량 영향 없음)
//   victim: 다른 사용자가 가벼운 목록 조회를 계속 — 앞 30초(abuser 시작 전)가 기준, 그 뒤가 부하 중
//   k6 run -e BASE_URL=http://localhost:8082 abuse-parts.js   (기본: 사용자 12명 × 16MB 파트 24개 동시 = 이론상 288, 피해 사용자 6명, 약 3분 30초)
//   조절: -e ABUSERS=12 -e PARTS=24 -e VICTIMS=6. 결과·해석은 README 결과 7(S3 커넥션 풀)
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import exec from 'k6/execution';
import { BASE_URL, login, userId, roleHeaders, request, data } from './lib/drive.js';

const PARTS = parseInt(__ENV.PARTS || '24');
const ABUSERS = parseInt(__ENV.ABUSERS || '12');
const VICTIMS = parseInt(__ENV.VICTIMS || '6');
const MB = 1024 * 1024;
const PART = 16 * MB;

const victimLatency = new Trend('victim_list_ms', true);
const partLatency = new Trend('abuse_part_ms', true);

export const options = {
  tags: { testid: __ENV.TESTID || 'abuse-parts' },
  noCookiesReset: true,
  batch: PARTS,
  batchPerHost: PARTS,
  scenarios: {
    victim: { executor: 'constant-vus', vus: VICTIMS, duration: '3m30s', exec: 'victim' },
    abuser: {
      executor: 'ramping-vus', startVUs: 0, startTime: '30s', exec: 'abuser', gracefulRampDown: '120s',
      stages: [{ duration: '20s', target: ABUSERS }, { duration: '2m', target: ABUSERS }, { duration: '10s', target: 0 }],
    },
  },
  thresholds: { 'victim_list_ms{phase:baseline}': ['max>=0'], 'victim_list_ms{phase:load}': ['max>=0'], abuse_part_ms: ['max>=0'], 'http_req_failed{api:victim_list}': ['rate<=1'], 'http_req_failed{api:upload_part}': ['rate<=1'] },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max', 'count'],
};

let logged = false;
let buf = null;

function bytes(size) {
  const b = new Uint8Array(size);
  for (let i = 0; i < size; i++) b[i] = 97 + (i % 26);
  return b.buffer;
}

// abuser: 사용자 1..ABUSERS, victim: 그 뒤 사용자
export function abuser() {
  if (!logged) { login('USER', userId(((exec.vu.idInTest - 1) % ABUSERS) + 1)); logged = true; }
  if (!buf) buf = bytes(PART);
  const plan = data(request('POST', '/api/v1/uploads', 'upload_start',
    JSON.stringify({ fileName: `k6-abuse-${__VU}-${__ITER}-${Date.now()}.txt`, size: PART * PARTS }),
    { expect: 201, headers: { 'Content-Type': 'application/json' } }));
  if (!plan) { sleep(1); return; }
  const params = { headers: roleHeaders('USER', { 'Content-Type': 'application/octet-stream' }), tags: { api: 'upload_part', name: 'upload_part' }, timeout: '300s' };
  const batch = [];
  for (let n = 1; n <= plan.partCount; n++) batch.push({ method: 'PUT', url: `${BASE_URL}/api/v1/uploads/${plan.sessionId}/parts/${n}`, body: buf, params });
  for (const r of http.batch(batch)) {
    check(r, { 'upload_part 200': (x) => x.status === 200 });
    partLatency.add(r.timings.duration);
  }
  request('DELETE', `/api/v1/uploads/${plan.sessionId}`, 'upload_cancel');
}

export function victim() {
  if (!logged) { login('USER', userId(ABUSERS + ((exec.vu.idInTest - 1) % VICTIMS) + 1)); logged = true; }
  const phase = exec.instance.currentTestRunDuration < 30000 ? 'baseline' : 'load';
  const r = request('GET', '/api/v1/resources?st=MY&ps=20', 'victim_list', null, { timeout: '300s' });
  victimLatency.add(r.timings.duration, { phase });
  sleep(0.5);
}
