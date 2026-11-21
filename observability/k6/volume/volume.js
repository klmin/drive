// 데이터량 테스트 — API 12종을 API 별 태그로 고정 VU 동안 반복해 API 마다 p95 를 잰다(seed-volume.sql 데이터 기준).
// ids.json: { "<userId>": { folder, big, deep, file } } — 없으면(null) 그 API 는 건너뛴다
import { loginAsVuUser, request, userId } from '../lib/drive.js';
const IDS = JSON.parse(open(__ENV.IDS || './ids.json'));
const VUS = parseInt(__ENV.VUS || '10'); const DUR = __ENV.DUR || '60s';
const APIS = ['list_root','list_folder','list_big','list_big_p50','list_deep','tree','search_common','search_rare','recent','starred','quota','detail'];
const thresholds = {}; APIS.forEach((a) => { thresholds[`http_req_duration{api:${a}}`] = ['p(95)<100000']; thresholds[`http_reqs{api:${a}}`] = ['count>=0']; });
thresholds.http_req_failed = ['rate<=1'];
export const options = { noCookiesReset: true, scenarios: { v: { executor: 'constant-vus', vus: VUS, duration: DUR } }, thresholds, summaryTrendStats: ['avg','med','p(95)','p(99)','max'] };
let me = null;
const get = (p, a) => request('GET', p, a, null, { timeout: '120s' });
export default function () {
  if (!me) { const i = loginAsVuUser(__VU); me = IDS[userId(i)]; }
  get('/api/v1/resources?st=MY&ps=50&sb=NAME&so=ASC', 'list_root');
  get(`/api/v1/resources?st=MY&ps=50&pfs=${me.folder}&sb=UPDATE&so=DESC`, 'list_folder');
  if (me.big) { get(`/api/v1/resources?st=MY&ps=50&pfs=${me.big}&sb=NAME&so=ASC`, 'list_big');
                get(`/api/v1/resources?st=MY&ps=50&pi=50&pfs=${me.big}&sb=NAME&so=ASC`, 'list_big_p50'); }
  if (me.deep) get(`/api/v1/resources?st=MY&ps=50&pfs=${me.deep}`, 'list_deep');
  get('/api/v1/resources/tree', 'tree');
  get('/api/v1/resources?st=ALL&sk=plan&ps=30', 'search_common');
  get('/api/v1/resources?st=ALL&sk=zzqx&ps=30', 'search_rare');
  get('/api/v1/recent?ps=30', 'recent');
  get('/api/v1/starred?ps=30', 'starred');
  get('/api/v1/users/quota', 'quota');
  get(`/api/v1/resources/${me.file}`, 'detail');
}
