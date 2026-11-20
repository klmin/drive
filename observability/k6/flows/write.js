// 흐름: 정리하기(쓰기) — 폴더 만들기 → 파일 복사 → 이름 바꾸기 → 다른 폴더로 옮기기 → 휴지통 → 복원 → 정리
// 한 반복이 만든 것은 반복 끝에 모두 영구 삭제한다 — 반복을 오래 돌려도 seed 데이터·사용자 용량이 그대로.
//
// 서버에서 보는 것: 폴더·리소스 행 잠금(이동·이름 변경), 이름 중복 확인, 사용자 용량 행 잠금(복사),
//   스토리지 서버측 복사(CopyObject), 삭제 대기열 등록. 같은 사용자의 VU 가 여럿이면 같은 사용자 행을 두고 경합한다(HOT_USER).
import { group, sleep } from 'k6';
import { loginAsVuUser, request, data, userId } from '../lib/drive.js';

const JSON_HEADERS = { 'Content-Type': 'application/json' };

let sources = null; // VU 마다 복사 원본으로 쓸 seed 파일 번호들

function post(path, api, body, expect = 200) {
  return request('POST', path, api, JSON.stringify(body), { expect, headers: JSON_HEADERS });
}

function patch(path, api, body = {}) {
  return request('PATCH', path, api, JSON.stringify(body), { headers: JSON_HEADERS });
}

function prepare(userIndex) {
  const index = loginAsVuUser(userIndex);
  const root = data(request('GET', '/api/v1/resources?st=MY&ps=100', 'list')) || [];
  sources = root.filter((r) => r.resourceType === 'FILE').map((r) => r.resourceSeq);
  if (sources.length === 0) console.warn(`${userId(index)}: 복사할 파일이 없다 — seed.js 를 먼저 실행`);
}

function createFolder(name, parentSeq) {
  const body = { fn: name };
  if (parentSeq) body.pfs = parentSeq;
  const folder = data(post('/api/v1/folders', 'create_folder', body, 201));
  return folder ? folder.resourceSeq : null;
}

// userIndex: 로그인할 seed 사용자 번호 쪽 VU 번호(기본 자기 VU). think: 동작 사이 쉬는 시간(초)
export function organize(think = 1, userIndex = __VU) {
  if (!sources) prepare(userIndex);
  if (sources.length === 0) return;

  const tag = `${__VU}-${__ITER}-${Date.now()}`;
  let work = null;
  let archive = null;

  group('폴더 만들기', () => {
    work = createFolder(`k6-work-${tag}`);
    archive = work && createFolder(`k6-archive-${tag}`, work);
  });
  if (!work || !archive) return;
  sleep(think);

  let copy = null;
  group('파일 복사·이름 바꾸기', () => {
    const source = sources[Math.floor(Math.random() * sources.length)];
    const copied = data(post(`/api/v1/files/${source}/copy`, 'copy_file', { pfs: work }, 201));
    copy = copied && copied.resourceSeq;
    if (copy) patch(`/api/v1/files/${copy}/rename`, 'rename_file', { rn: `k6-renamed-${tag}.txt`, ncp: 'AUTO' });
  });
  sleep(think);

  if (copy) {
    group('옮기기', () => {
      post(`/api/v1/files/${copy}/move`, 'move_file', { pfs: archive });
      patch(`/api/v1/folders/${archive}/rename`, 'rename_folder', { rn: `k6-archive-done-${tag}` });
    });
    sleep(think);

    group('휴지통·복원', () => {
      patch(`/api/v1/files/${copy}/delete`, 'trash_file');
      patch(`/api/v1/trash/files/${copy}/restore`, 'restore_file', { pe: true }); // pe: 원래 폴더로(false 면 루트)
    });
    sleep(think);
  }

  // 정리 — 작업 폴더를 통째로 영구 삭제(안의 폴더·파일 포함, 스토리지 객체는 삭제 대기열로)
  group('정리', () => {
    request('DELETE', `/api/v1/folders/${work}`, 'delete_folder');
  });
  sleep(think);
}
