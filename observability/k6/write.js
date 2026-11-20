// 쓰기 부하 — 폴더 만들기·복사·이름 바꾸기·옮기기·휴지통·복원·영구 삭제(반복마다 만든 것은 모두 지운다)
//   docker compose --profile loadtest run --rm k6 run /scripts/write.js
//   짧은 확인:  ... run -e PROFILE=smoke /scripts/write.js
//   경합 보기:  ... run -e HOT_USER=1 /scripts/write.js   ← 모든 VU 가 같은 사용자로 — 사용자 행·폴더 잠금 경합
//
// 흐름 본체는 flows/write.js — 여기서는 부하 모양(PROFILES)과 기준(thresholds)만 정한다.
// 서버 쪽은 Grafana "k6 부하 테스트" 의 서버 행(WAS p95·Hikari) + "DB 잠금·느린 쿼리" 행에서 같이 본다.
import { organize } from './flows/write.js';

const PROFILES = {
  smoke: { stages: [{ duration: '5s', target: 1 }, { duration: '30s', target: 1 }] },
  // 기본 — 5명 → 15명 (약 4분)
  default: {
    stages: [
      { duration: '30s', target: 5 },
      { duration: '1m', target: 5 },
      { duration: '30s', target: 15 },
      { duration: '1m30s', target: 15 },
      { duration: '30s', target: 0 },
    ],
  },
  // 한계 찾기 — 사용자 18명을 넘으면 같은 사용자를 둘 이상의 VU 가 나눠 쓴다(경합이 섞인다)
  stress: {
    stages: [
      { duration: '1m', target: 10 },
      { duration: '1m', target: 30 },
      { duration: '1m', target: 60 },
      { duration: '30s', target: 0 },
    ],
  },
};

const profile = PROFILES[__ENV.PROFILE || 'default'];
const THINK = parseFloat(__ENV.THINK || '1');
const HOT_USER = __ENV.HOT_USER === '1';

export const options = {
  tags: { testid: __ENV.TESTID || `write-${__ENV.PROFILE || 'default'}${HOT_USER ? '-hot' : ''}` },
  noCookiesReset: true,
  scenarios: { write: Object.assign({ executor: 'ramping-vus', startVUs: 0, gracefulRampDown: '30s' }, profile) },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
    'http_req_duration{api:move_file}': ['p(95)<1000'],
    'http_req_duration{api:copy_file}': ['p(95)<2000'],
  },
};

export default function () {
  organize(THINK, HOT_USER ? 1 : __VU);
}
