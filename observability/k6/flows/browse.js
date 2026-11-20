// 흐름: 드라이브 둘러보기 — 내 드라이브 목록 → 폴더 열기 → 파일 상세 → 검색 → 최근·즐겨찾기·트리·용량
// 실행 파일(read.js·이후 mixed.js)이 import 해서 시나리오의 exec 로 쓴다.
import { group, sleep } from 'k6';
import { loginAsVuUser, request, data, userId } from '../lib/drive.js';

const KEYWORDS = ['report', 'budget', 'plan', 'design', 'meeting', 'contract', 'memo', 'draft', 'invoice', 'roadmap'];

let me = null; // VU 마다 { folders: [...], files: [...] }

const get = (path, api) => request('GET', path, api);

function pick(list) {
  return list[Math.floor(Math.random() * list.length)];
}

// 처음 한 번 — 로그인하고 자기 폴더·파일 번호를 모은다
function prepare() {
  const index = loginAsVuUser(__VU);
  const root = data(get('/api/v1/resources?st=MY&ps=100', 'list')) || [];
  const folders = root.filter((r) => r.resourceType === 'FOLDER').map((r) => r.resourceSeq);
  const files = root.filter((r) => r.resourceType === 'FILE').map((r) => r.resourceSeq);
  if (folders.length > 0) {
    const inner = data(get(`/api/v1/resources?st=MY&ps=100&pfs=${folders[0]}`, 'list')) || [];
    inner.filter((r) => r.resourceType === 'FILE').forEach((r) => files.push(r.resourceSeq));
  }
  if (files.length === 0) console.warn(`${userId(index)}: 파일이 없다 — seed.js 를 먼저 실행`);
  me = { folders, files };
}

// think: 동작 사이 쉬는 시간(초)
export function browse(think = 1) {
  if (!me) prepare();

  group('홈·내 드라이브', () => {
    get('/api/v1/users', 'me');
    get('/api/v1/resources?st=MY&ps=50&sb=NAME&so=ASC', 'list');
  });
  sleep(think);

  if (me.folders.length > 0) {
    group('폴더 열기', () => {
      get(`/api/v1/resources?st=MY&ps=50&pfs=${pick(me.folders)}&sb=UPDATE&so=DESC`, 'list');
    });
    sleep(think);
  }

  if (me.files.length > 0) {
    group('파일 상세', () => {
      get(`/api/v1/resources/${pick(me.files)}`, 'detail');
    });
    sleep(think);
  }

  group('검색', () => {
    get(`/api/v1/resources?st=ALL&sk=${pick(KEYWORDS)}&ps=30`, 'search');
  });
  sleep(think);

  group('사이드 메뉴', () => {
    get('/api/v1/recent?ps=30', 'recent');
    get('/api/v1/starred?ps=30', 'starred');
    get('/api/v1/resources/tree', 'tree');
    get('/api/v1/users/quota', 'quota');
  });
  sleep(think);
}
