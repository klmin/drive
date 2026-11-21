// 데이터량 테스트 — API 12종을 API 별 태그로 고정 VU 동안 반복해 API 마다 p95 를 잰다(seed-volume.sql 데이터 기준).
// ids.json: { "<userId>": { folder, big, deep, file } } — 없으면(null) 그 API 는 건너뛴다
// 목록은 커서 페이징이다(cv·ci·ct). 50페이지는 VU 마다 처음 한 번 49페이지를 따라가 커서를 구해 두고 그 커서로 잰다(cursor_walk 태그는 준비용).
import { loginAsVuUser, request, userId } from '../lib/drive.js';
const IDS = JSON.parse(open(__ENV.IDS || './ids.json'));
const VUS = parseInt(__ENV.VUS || '10'); const DUR = __ENV.DUR || '60s';
const APIS = ['list_root','list_folder','list_big','list_big_p50','list_deep','tree','search_common','search_rare','recent','starred','quota','detail'];
const thresholds = {}; APIS.forEach((a) => { thresholds[`http_req_duration{api:${a}}`] = ['p(95)<100000']; thresholds[`http_reqs{api:${a}}`] = ['count>=0']; });
thresholds.http_req_failed = ['rate<=1'];
export const options = {
  tags: { testid: __ENV.TESTID || 'volume' },
  noCookiesReset: true,
  scenarios: { v: { executor: 'constant-vus', vus: VUS, duration: DUR } },
  thresholds,
  summaryTrendStats: ['avg','med','p(95)','p(99)','max'],
};
let me = null;
let p50 = null; // big-folder 50페이지 커서 쿼리 문자열
const get = (p, a) => request('GET', p, a, null, { timeout: '120s' });
const BIG_LIST = (pfs) => `/api/v1/resources?st=MY&ps=50&pfs=${pfs}&sb=NAME&so=ASC`;

// page 번째 페이지를 여는 커서(&cv=…&ci=…&ct=…). 그 전에 목록이 끝나면 null
function cursorAt(pfs, page) {
  let q = '';
  for (let i = 1; i < page; i++) {
    let body;
    try { body = get(BIG_LIST(pfs) + q, 'cursor_walk').json(); } catch (e) { return null; }
    if (!body || !body.hasNext) return null;
    q = `&cv=${encodeURIComponent(body.nextCursorValue)}&ci=${body.nextCursor}&ct=${body.nextCursorType}`;
  }
  return q;
}

export default function () {
  if (!me) {
    const i = loginAsVuUser(__VU); me = IDS[userId(i)];
    if (me.big) p50 = cursorAt(me.big, 50);
  }
  get('/api/v1/resources?st=MY&ps=50&sb=NAME&so=ASC', 'list_root');
  get(`/api/v1/resources?st=MY&ps=50&pfs=${me.folder}&sb=UPDATE&so=DESC`, 'list_folder');
  if (me.big) get(BIG_LIST(me.big), 'list_big');
  if (p50) get(BIG_LIST(me.big) + p50, 'list_big_p50');
  if (me.deep) get(`/api/v1/resources?st=MY&ps=50&pfs=${me.deep}`, 'list_deep');
  get('/api/v1/resources/tree', 'tree');
  get('/api/v1/resources?st=ALL&sk=plan&ps=30', 'search_common');
  get('/api/v1/resources?st=ALL&sk=zzqx&ps=30', 'search_rare');
  get('/api/v1/recent?ps=30', 'recent');
  get('/api/v1/starred?ps=30', 'starred');
  get('/api/v1/users/quota', 'quota');
  get(`/api/v1/resources/${me.file}`, 'detail');
}
