// 다운로드 부하 — 작은 파일·큰 파일·폴더 zip 을 동시에(프론트와 같은 "토큰 발급 → 링크로 받기")
//   docker compose --profile loadtest run --rm k6 run /scripts/download.js
//   짧은 확인:  ... run -e PROFILE=smoke /scripts/download.js
//   큰 파일 크기: ... run -e LARGE_MB=80 /scripts/download.js   ← 32MB 이상(청크 업로드로 올려 둔다)
//
// 흐름 본체는 flows/download.js — 여기서는 부하 모양(PROFILES)과 기준(thresholds)만 정한다.
// 큰 파일 시나리오 VU 는 처음 한 번 자기 사용자에 k6-download-large-<MB>mb.txt 를 올려 두고 재사용한다(지우지 않음, 크기를 바꾸면 이전 것은 지운다).
// 서버 쪽은 Grafana "k6 부하 테스트" 의 다운로드 행(받은 양·스트리밍 스레드·S3 GetObject)에서 같이 본다.
import { sleep } from 'k6';
import { downloadSmall, downloadLarge, downloadZip } from './flows/download.js';

const LARGE_MB = parseFloat(__ENV.LARGE_MB || '40');
const THINK = parseFloat(__ENV.THINK || '1');

// 시나리오별 VU 수 — 세 시나리오 합이 seed 사용자 수(18) 안이면 VU 마다 다른 사용자
const PROFILES = {
  smoke: {
    small: [{ duration: '5s', target: 1 }, { duration: '30s', target: 1 }],
    large: [{ duration: '5s', target: 1 }, { duration: '30s', target: 1 }],
    zip: [{ duration: '5s', target: 1 }, { duration: '30s', target: 1 }],
  },
  // 기본 — 작은 파일 4→8명, 큰 파일 2→4명, zip 1→2명 (약 3분 30초)
  default: {
    small: [{ duration: '30s', target: 4 }, { duration: '1m', target: 4 }, { duration: '30s', target: 8 }, { duration: '1m', target: 8 }, { duration: '30s', target: 0 }],
    large: [{ duration: '30s', target: 2 }, { duration: '1m', target: 2 }, { duration: '30s', target: 4 }, { duration: '1m', target: 4 }, { duration: '30s', target: 0 }],
    zip: [{ duration: '30s', target: 1 }, { duration: '1m', target: 1 }, { duration: '30s', target: 2 }, { duration: '1m', target: 2 }, { duration: '30s', target: 0 }],
  },
  // 한계 찾기 — 큰 파일 동시 다운로드를 올려 스트리밍 스레드·네트워크·S3 중 어디가 먼저 차는지 본다
  stress: {
    small: [{ duration: '1m', target: 4 }, { duration: '2m', target: 4 }, { duration: '30s', target: 0 }],
    large: [{ duration: '1m', target: 4 }, { duration: '1m', target: 8 }, { duration: '1m', target: 12 }, { duration: '30s', target: 0 }],
    zip: [{ duration: '1m', target: 2 }, { duration: '2m', target: 2 }, { duration: '30s', target: 0 }],
  },
};

const profile = PROFILES[__ENV.PROFILE || 'default'];
const base = { executor: 'ramping-vus', startVUs: 0, gracefulRampDown: '60s' };

export const options = {
  tags: { testid: __ENV.TESTID || `download-${__ENV.PROFILE || 'default'}` },
  noCookiesReset: true,
  scenarios: {
    small: Object.assign({}, base, { stages: profile.small, exec: 'small' }),
    large: Object.assign({}, base, { stages: profile.large, exec: 'large' }),
    zip: Object.assign({}, base, { stages: profile.zip, exec: 'zip' }),
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
    'http_req_duration{api:download_small}': ['p(95)<1000'], // 작은 파일 1초(SLO 와 같은 기준)
    'http_req_duration{api:download_token_file}': ['p(95)<500'],
  },
};

// 시나리오의 exec 이름
export function small() {
  downloadSmall();
  sleep(THINK);
}

export function large() {
  downloadLarge(LARGE_MB);
  sleep(THINK);
}

export function zip() {
  downloadZip();
  sleep(THINK);
}
