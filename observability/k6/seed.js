// 부하 테스트 데이터 만들기 — 한 번만 실행(다시 하려면 loadtest-db·storage 볼륨을 지우고 처음부터, k6/README.md)
//   docker compose --profile loadtest run --rm k6 run /scripts/seed.js
//
// 만드는 것(환경변수로 조절): 사용자 USERS(18)명, 사용자마다 루트 파일 ROOT_FILES(10)개 + 폴더 FOLDERS(10)개 × 폴더당 파일 FILES(20)개,
//   파일 STAR_EVERY(10)개마다 1개 즐겨찾기. 기본값이면 파일 3,780개·폴더 180개.
//   라이선스 없는 새 DB 는 좌석이 20개 남짓이라 그 이상 등록하면 USER_013(등록 실패)이 난다.
// 사용자 등록은 admin, 나머지는 각 사용자로 로그인해 실제 API 로 만든다(업로드는 loadtest-storage 로 간다).
import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { BASE_URL, USER_COUNT, PASSWORD, ADMIN_ID, userId, login, roleHeaders, data, multipart } from './lib/drive.js';

const FOLDERS = parseInt(__ENV.FOLDERS || '10', 10);
const FILES = parseInt(__ENV.FILES || '20', 10);
const ROOT_FILES = parseInt(__ENV.ROOT_FILES || '10', 10);
const STAR_EVERY = parseInt(__ENV.STAR_EVERY || '10', 10);
const WORDS = ['report', 'budget', 'plan', 'design', 'meeting', 'contract', 'memo', 'draft', 'invoice', 'roadmap'];

export const options = {
  tags: { testid: __ENV.TESTID || 'seed' },
  scenarios: {
    seed: { executor: 'shared-iterations', vus: Math.min(5, USER_COUNT), iterations: USER_COUNT, maxDuration: '30m' },
  },
};

// admin 으로 사용자 등록 — 이미 있으면(재실행) 건너뛴다
export function setup() {
  login('ADMIN', ADMIN_ID);
  let created = 0;
  for (let i = 1; i <= USER_COUNT; i++) {
    const form = multipart({ ui: userId(i), encPwd: PASSWORD, un: `부하테스트 ${i}`, roles: 'USER', tq: String(100 * 1024 ** 2) }); // 100MB — 테넌트 총 용량(기본 10GB) 안에서 나눠 준다
    const res = http.post(`${BASE_URL}/api/v1/admins/users`, form.body, {
      headers: roleHeaders('ADMIN', { 'Content-Type': form.contentType }),
      tags: { api: 'admin_create_user' },
    });
    if (res.status === 201) created++;
    else console.warn(`user ${userId(i)}: ${res.status} ${String(res.body).slice(0, 150)} (이미 있으면 정상)`);
  }
  console.log(`users created: ${created}/${USER_COUNT}`);
}

function upload(parentSeq, name) {
  const text = `${name}\n` + 'drive load test content. '.repeat(40 + Math.floor(Math.random() * 120));
  const body = { file: http.file(text, name, 'text/plain') };
  if (parentSeq) body.pfs = String(parentSeq);
  const res = http.post(`${BASE_URL}/api/v1/files`, body, { headers: roleHeaders('USER'), tags: { api: 'upload' } });
  check(res, { 'upload 201': (r) => r.status === 201 });
  const d = data(res);
  return d ? d.resourceSeq : null;
}

// 사용자 1명분 — 반복 번호가 곧 사용자 번호
export default function () {
  const index = exec.scenario.iterationInTest + 1;
  login('USER', userId(index));
  const fileSeqs = [];

  for (let f = 1; f <= ROOT_FILES; f++) {
    fileSeqs.push(upload(null, `${WORDS[f % WORDS.length]}-root-${f}.txt`));
  }
  for (let d = 1; d <= FOLDERS; d++) {
    const res = http.post(`${BASE_URL}/api/v1/folders`, JSON.stringify({ fn: `${WORDS[d % WORDS.length]}-folder-${d}` }), {
      headers: roleHeaders('USER', { 'Content-Type': 'application/json' }),
      tags: { api: 'create_folder' },
    });
    check(res, { 'folder 201': (r) => r.status === 201 });
    const folder = data(res);
    if (!folder) continue;
    for (let f = 1; f <= FILES; f++) {
      fileSeqs.push(upload(folder.resourceSeq, `${WORDS[(d + f) % WORDS.length]}-${d}-${f}.txt`));
    }
  }

  fileSeqs.filter(Boolean).forEach((seq, i) => {
    if (i % STAR_EVERY !== 0) return;
    const res = http.post(`${BASE_URL}/api/v1/starred`, JSON.stringify({ rs: seq }), {
      headers: roleHeaders('USER', { 'Content-Type': 'application/json' }),
      tags: { api: 'star' },
    });
    check(res, { 'star 2xx': (r) => r.status >= 200 && r.status < 300 });
  });
}
