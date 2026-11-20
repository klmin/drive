// 읽기 위주 부하 — 사용자가 드라이브를 둘러보는 흐름(목록·폴더 열기·상세·검색·최근·즐겨찾기·트리·용량)
//   docker compose --profile loadtest run --rm k6 run /scripts/read.js
//   짧은 확인:  ... run -e PROFILE=smoke /scripts/read.js
//
// VU(가상 사용자)마다 seed 로 만든 사용자 하나로 처음 한 번 로그인하고, 자기 폴더·파일 번호를 모은 뒤 반복한다.
// 결과는 Prometheus 로 가고(k6_*), Grafana "k6 부하 테스트" 대시보드에서 서버 지표와 같이 본다.
// 흐름 본체는 flows/browse.js — 여기서는 부하 모양(PROFILES)과 기준(thresholds)만 정한다.
import { browse } from './flows/browse.js';

const PROFILES = {
  // 스크립트 확인용 — 1명 30초
  smoke: { stages: [{ duration: '5s', target: 1 }, { duration: '30s', target: 1 }] },
  // 기본 — 10명까지 올려 유지 → 30명까지 올려 유지 → 내림 (약 4분)
  default: {
    stages: [
      { duration: '30s', target: 10 },
      { duration: '1m', target: 10 },
      { duration: '30s', target: 30 },
      { duration: '1m', target: 30 },
      { duration: '30s', target: 0 },
    ],
  },
  // 한계 찾기 — 계속 올린다(실패율·p95 가 꺾이는 지점을 Grafana 에서 본다)
  stress: {
    stages: [
      { duration: '1m', target: 20 },
      { duration: '1m', target: 50 },
      { duration: '1m', target: 100 },
      { duration: '1m', target: 150 },
      { duration: '30s', target: 0 },
    ],
  },
};

const profile = PROFILES[__ENV.PROFILE || 'default'];
const THINK = parseFloat(__ENV.THINK || '1'); // 동작 사이 쉬는 시간(초) — 0 이면 쉬지 않고 두드린다

export const options = {
  tags: { testid: __ENV.TESTID || `read-${__ENV.PROFILE || 'default'}` },
  // k6 는 반복마다 쿠키를 비운다 — 로그인 쿠키(DRIVE_AT_USER)를 VU 가 계속 쓰도록 유지
  noCookiesReset: true,
  scenarios: { read: Object.assign({ executor: 'ramping-vus', startVUs: 0, gracefulRampDown: '10s' }, profile) },
  thresholds: {
    http_req_failed: ['rate<0.01'], // 실패 1% 미만
    'http_req_duration{api:list}': ['p(95)<1000'], // 목록 p95 1초 미만
    checks: ['rate>0.99'],
  },
};

export default function () {
  browse(THINK);
}
