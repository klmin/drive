// 공유 폴더 동시 작업: 사용자 1이 폴더를 공유(편집·업로드 권한)하고, 사용자 2~16(15명)이 그 안에서 동시에
// 폴더 만들기 → 이름 바꾸기를 반복한다. MODE=own 이면 같은 15명이 각자 자기 드라이브(자기 작업 폴더)에서 같은 일을 한다(비교용).
// env: MODE(shared|own), SPLIT=1(공유 폴더 안에 사람마다 하위 폴더 — 부모 폴더 잠금을 빼고 사용자 행 잠금만 남긴다), THINK(쉬는 초, 기본 0.2), VUS(기본 15), DUR
// 만든 폴더는 모두 실행용 작업 폴더 아래에 두고, 끝나면(teardown) 작업 폴더를 영구 삭제한다 — 폴더가 쌓여 트리 등 다른 테스트가 느려지지 않게.
import { sleep } from 'k6';
import { login, request, data, userId } from '../lib/drive.js';

const MODE = __ENV.MODE || 'shared';
const VUS = parseInt(__ENV.VUS || '15');
const IDS = JSON.parse(open(__ENV.IDS || './ids.json'));
const JSON_HEADERS = { 'Content-Type': 'application/json' };
const OWNER = 1;

export const options = {
  tags: { testid: __ENV.TESTID || 'shared-folder' },
  noCookiesReset: true,
  setupTimeout: '120s',
  teardownTimeout: '600s',
  scenarios: { work: { executor: 'constant-vus', vus: VUS, duration: __ENV.DUR || '60s' } },
  thresholds: {
    'http_req_duration{api:create_folder}': ['p(95)<100000'],
    'http_req_duration{api:rename_folder}': ['p(95)<100000'],
    'http_reqs{api:create_folder}': ['count>=0'],
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

function createFolder(body) {
  return data(request('POST', '/api/v1/folders', 'setup', JSON.stringify(body), { expect: 201, headers: JSON_HEADERS })).resourceSeq;
}

export function setup() {
  const run = Date.now();
  if (MODE !== 'shared') {
    // 사용자마다 작업 폴더 하나(VU n → 사용자 n+1)
    const own = {};
    for (let i = 2; i <= VUS + 1; i++) {
      login('USER', userId(i));
      own[i] = createFolder({ fn: `own-hot-${run}` });
    }
    return { own, cleanup: Object.keys(own).map((i) => ({ user: Number(i), seq: own[i] })) };
  }
  login('USER', userId(OWNER));
  const folder = createFolder({ fn: `shared-hot-${run}` });
  const ts = [];
  for (let i = 2; i <= VUS + 1; i++) ts.push({ us: IDS[userId(i)].seq, cv: true, ce: true, cd: true, cu: true, cs: false, se: false });
  request('POST', '/api/v1/shares', 'setup', JSON.stringify({ rs: folder, st: 'SPECIFIC_USERS', ts }), { expect: 201, headers: JSON_HEADERS });
  const subs = [];
  for (let i = 1; i <= VUS; i++) subs.push(createFolder({ fn: `sub-${i}`, pfs: folder }));
  return { folder, subs, ownerSeq: IDS[userId(OWNER)].seq, cleanup: [{ user: OWNER, seq: folder }] };
}

let logged = false;

export default function (s) {
  if (!logged) { login('USER', userId(__VU + 1)); logged = true; }
  const name = `hot-${__VU}-${__ITER}-${Date.now()}`;
  const body = MODE === 'shared'
    ? { fn: name, pfs: (__ENV.SPLIT ? s.subs[__VU - 1] : s.folder), ss: s.ownerSeq }
    : { fn: name, pfs: s.own[__VU + 1] };
  const f = data(request('POST', '/api/v1/folders', 'create_folder', JSON.stringify(body), { expect: 201, headers: JSON_HEADERS }));
  if (f) {
    const rb = MODE === 'shared' ? { rn: `${name}-r`, ss: s.ownerSeq } : { rn: `${name}-r` };
    request('PATCH', `/api/v1/folders/${f.resourceSeq}/rename`, 'rename_folder', JSON.stringify(rb), { headers: JSON_HEADERS });
  }
  sleep(parseFloat(__ENV.THINK || '0.2'));
}

// 작업 폴더 영구 삭제(공유도 같이 사라진다)
export function teardown(s) {
  for (const c of s.cleanup) {
    login('USER', userId(c.user));
    request('DELETE', `/api/v1/folders/${c.seq}`, 'cleanup', null, { timeout: '600s' });
  }
}
