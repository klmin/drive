// 휴지통 비우기 — 사용자 U 의 루트 폴더 500개(각 파일 100개)를 하나씩 휴지통으로 옮긴 뒤 한 번에 비운다(약 5만 건).
// 그동안 같은 사용자의 쓰기(폴더 만들기·영구 삭제)와 다른 사용자의 읽기가 얼마나 기다리는지 본다.
// env: U(사용자 번호, 기본 18), OTHERS(다른 사용자 VU 수, 기본 14) — 기본값이면 VU 1~17 이라 U 와 겹치지 않는다
import { sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { login, request, data, userId } from '../lib/drive.js';
import { browse } from '../flows/browse.js';

const U = parseInt(__ENV.U || '18');
const IDS = JSON.parse(open(__ENV.IDS || './ids.json'));
const FOLDERS = IDS[userId(U)].volFolders;
const JSON_HEADERS = { 'Content-Type': 'application/json' };
const big = new Trend('big_op_ms', true);

export const options = {
  noCookiesReset: true,
  scenarios: {
    deleter: { executor: 'per-vu-iterations', vus: 1, iterations: 1, startTime: '15s', exec: 'deleter', maxDuration: '10m' },
    same_user: { executor: 'constant-vus', vus: 2, duration: '150s', exec: 'sameUser' },
    others: { executor: 'constant-vus', vus: parseInt(__ENV.OTHERS || '14'), duration: '150s', exec: 'others' },
  },
  thresholds: {
    'big_op_ms{op:trash_move}': ['max>=0'], 'big_op_ms{op:trash_empty}': ['max>=0'],
    'http_req_duration{scenario:same_user}': ['p(95)<100000'],
    'http_req_duration{scenario:others}': ['p(95)<100000'],
    'http_req_failed{scenario:same_user}': ['rate<=1'], 'http_req_failed{scenario:others}': ['rate<=1'],
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

let logged = false;

export function deleter() {
  // 루트 폴더 500개(각 파일 100개)를 하나씩 휴지통으로 옮긴 뒤 휴지통을 한 번에 비운다(약 5만 건)
  login('USER', userId(U));
  let t = Date.now();
  for (const seq of FOLDERS) {
    request('PATCH', `/api/v1/folders/${seq}/delete`, 'trash_move', '{}', { headers: JSON_HEADERS, timeout: '600s' });
  }
  big.add(Date.now() - t, { op: 'trash_move' });
  console.log(`trash_move x${FOLDERS.length} ms=${Date.now() - t}`);
  sleep(5);
  t = Date.now();
  const r2 = request('DELETE', '/api/v1/trash/empty', 'trash_empty', null, { timeout: '600s' });
  big.add(Date.now() - t, { op: 'trash_empty' });
  console.log(`trash_empty status=${r2.status} ms=${Date.now() - t}`);
}

export function sameUser() {
  if (!logged) { login('USER', userId(U)); logged = true; }
  const name = `ld-${__VU}-${__ITER}-${Date.now()}`;
  const f = data(request('POST', '/api/v1/folders', 'create_folder', JSON.stringify({ fn: name }), { expect: 201, headers: JSON_HEADERS, timeout: '600s' }));
  if (f) request('DELETE', `/api/v1/folders/${f.resourceSeq}`, 'delete_folder', null, { timeout: '600s' });
  sleep(0.5);
}

export function others() {
  // VU 번호 = 사용자 번호(lib 의 loginAsVuUser). 기본값(U=18, OTHERS=14)이면 VU 1~17 이라 U 와 겹치지 않는다
  browse(1);
}
