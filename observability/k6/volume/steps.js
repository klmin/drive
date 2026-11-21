// 단계별 부하(사양 산정용) — 사용자 수 단계(LEVELS)마다 고정 VU 로 둘러보기를 돌려 단계마다 p95·처리량을 따로 본다
import { browse } from '../flows/browse.js';
const LEVELS = (__ENV.LEVELS || '10,25,50,100,150,200').split(',').map(Number);
const DUR = parseInt(__ENV.DUR || '60'); const THINK = parseFloat(__ENV.THINK || '1');
const scenarios = {}; const thresholds = {};
LEVELS.forEach((v, i) => {
  scenarios[`vu${v}`] = { executor: 'constant-vus', vus: v, duration: `${DUR}s`, startTime: `${i * (DUR + 5)}s`, gracefulStop: '5s' };
  thresholds[`http_req_duration{scenario:vu${v}}`] = ['p(95)<100000'];
  thresholds[`http_req_duration{scenario:vu${v},api:list}`] = ['p(95)<100000'];
  thresholds[`http_reqs{scenario:vu${v}}`] = ['count>=0'];
  thresholds[`http_req_failed{scenario:vu${v}}`] = ['rate<=1'];
});
export const options = { noCookiesReset: true, scenarios, thresholds, summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'] };
export default function () { browse(THINK); }
