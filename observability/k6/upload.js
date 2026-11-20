// 업로드 부하 — 작은 파일(기존 업로드 API)과 큰 파일(청크 업로드 API)을 동시에, 올린 뒤 바로 영구 삭제
//   docker compose --profile loadtest run --rm k6 run /scripts/upload.js
//   짧은 확인:  ... run -e PROFILE=smoke /scripts/upload.js
//   크기·비율:  ... run -e CHUNK_MB=100 -e SMALL_KB=2048 -e CANCEL_RATE=0.2 /scripts/upload.js
//
// 흐름 본체는 flows/upload.js — 여기서는 부하 모양(PROFILES)과 기준(thresholds)만 정한다.
// 서버 쪽은 Grafana "k6 부하 테스트" 의 업로드 행(업로드 세션 시작·종료·진행 중, S3 호출)에서 같이 본다.
//
// 용량: seed 사용자는 100MB. 큰 파일은 complete 전까지 진행 중 용량으로 잡히므로
//   (사용자 하나를 쓰는 VU 수) × CHUNK_MB 가 100MB 를 넘으면 세션 생성이 용량 초과로 실패한다.
//   VU 는 사용자 18명을 번호 순서대로 나눠 쓰고(small·chunk 시나리오 합산), 기본값은 겹치지 않게 잡았다.
import { sleep } from 'k6';
import { smallUpload, chunkUpload } from './flows/upload.js';

const SMALL_KB = parseFloat(__ENV.SMALL_KB || '512');
const CHUNK_MB = parseFloat(__ENV.CHUNK_MB || '40'); // 단일 요청 기준(32MB) 이상이어야 청크 API 를 받는다
const CANCEL_RATE = parseFloat(__ENV.CANCEL_RATE || '0.1');
const THINK = parseFloat(__ENV.THINK || '1');

// 시나리오별 VU 수와 시간
const PROFILES = {
  // 스크립트 확인용 — 각 1명 30초
  //   ramping-vus 는 target 까지 선형으로 올리므로 첫 단계를 짧게 — 30초 동안 0→1 이면 끝날 때야 VU 가 생긴다
  smoke: { small: [{ duration: '5s', target: 1 }, { duration: '30s', target: 1 }], chunk: [{ duration: '5s', target: 1 }, { duration: '30s', target: 1 }] },
  // 기본 — 작은 파일 4명→8명, 큰 파일 2명→4명 (약 3분 30초)
  default: {
    small: [{ duration: '30s', target: 4 }, { duration: '1m', target: 4 }, { duration: '30s', target: 8 }, { duration: '1m', target: 8 }, { duration: '30s', target: 0 }],
    chunk: [{ duration: '30s', target: 2 }, { duration: '1m', target: 2 }, { duration: '30s', target: 4 }, { duration: '1m', target: 4 }, { duration: '30s', target: 0 }],
  },
  // 한계 찾기 — 큰 파일 동시 업로드를 올려 S3·디스크·커넥션 중 어디가 먼저 차는지 본다(사용자 18명 안에서)
  stress: {
    small: [{ duration: '1m', target: 4 }, { duration: '2m', target: 6 }, { duration: '30s', target: 0 }],
    chunk: [{ duration: '1m', target: 4 }, { duration: '1m', target: 8 }, { duration: '1m', target: 12 }, { duration: '30s', target: 0 }],
  },
};

const profile = PROFILES[__ENV.PROFILE || 'default'];
const base = { executor: 'ramping-vus', startVUs: 0, gracefulRampDown: '60s' }; // 큰 파일은 끝까지 올리고 내려가게

export const options = {
  tags: { testid: __ENV.TESTID || `upload-${__ENV.PROFILE || 'default'}` },
  noCookiesReset: true,
  scenarios: {
    small: Object.assign({}, base, { stages: profile.small, exec: 'small' }),
    chunk: Object.assign({}, base, { stages: profile.chunk, exec: 'chunk' }),
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
    'http_req_duration{api:upload_small}': ['p(95)<2000'], // 작은 파일 한 요청 2초
    'http_req_duration{api:upload_complete}': ['p(95)<5000'], // complete(조립·커밋) 5초
  },
};

// 시나리오의 exec 이름
export function small() {
  smallUpload(SMALL_KB);
  sleep(THINK);
}

export function chunk() {
  chunkUpload(CHUNK_MB, CANCEL_RATE);
  sleep(THINK);
}
