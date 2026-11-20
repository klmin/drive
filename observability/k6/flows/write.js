// 흐름: 정리하기(쓰기) — 폴더 만들기 → 파일 복사 → 이름 바꾸기 → 다른 폴더로 옮기기 → 휴지통 → 복원 → 정리
// 한 반복이 만든 것은 반복 끝에 모두 영구 삭제한다 — 반복을 오래 돌려도 seed 데이터·사용자 용량이 그대로.
//
// 서버에서 보는 것: 폴더·리소스 행 잠금(이동·이름 변경), 이름 중복 확인, 사용자 용량 행 잠금(복사),
//   스토리지 서버측 복사(CopyObject), 삭제 대기열 등록. 같은 사용자의 VU 가 여럿이면 같은 사용자 행을 두고 경합한다(HOT_USER).
import { group, sleep } from 'k6';
import exec from 'k6/execution';
import { loginAsVuUser, request, data, userId } from '../lib/drive.js';

const JSON_HEADERS = { 'Content-Type': 'application/json' };

let loggedInAs = null; // 로그인한 seed 사용자 번호
let sources = null; // VU 마다 복사 원본으로 쓸 seed 파일 번호들 — 목록 조회에 성공해야 채운다

function post(path, api, body, expect = 200) {
  return request('POST', path, api, JSON.stringify(body), { expect, headers: JSON_HEADERS });
}

function patch(path, api, body = {}) {
  return request('PATCH', path, api, JSON.stringify(body), { headers: JSON_HEADERS });
}

// 원본 목록 준비. 조회가 실패하면(일시 오류) false — 다음 반복에서 다시 한다(그 VU 가 남은 테스트 내내 쉬지 않게).
// 조회는 됐는데 파일이 없으면 seed 를 안 한 것이라 테스트를 멈춘다(부하가 빠진 채 결과가 나오지 않게)
function prepare(userIndex) {
  if (loggedInAs === null) loggedInAs = loginAsVuUser(userIndex);
  const root = data(request('GET', '/api/v1/resources?st=MY&ps=100', 'list'));
  if (root === null) return false;
  const files = root.filter((r) => r.resourceType === 'FILE').map((r) => r.resourceSeq);
  if (files.length === 0) exec.test.abort(`${userId(loggedInAs)}: 복사할 파일이 없다 — seed.js 를 먼저 실행`);
  sources = files;
  return true;
}

function createFolder(name, parentSeq) {
  const body = { fn: name };
  if (parentSeq) body.pfs = parentSeq;
  const folder = data(post('/api/v1/folders', 'create_folder', body, 201));
  return folder ? folder.resourceSeq : null;
}

// userIndex: 로그인할 seed 사용자 번호 쪽 VU 번호(기본 자기 VU). think: 동작 사이 쉬는 시간(초)
export function organize(think = 1, userIndex = __VU) {
  if (!sources && !prepare(userIndex)) {
    sleep(1); // 목록 조회 실패 — 다음 반복에서 다시 준비
    return;
  }

  const tag = `${__VU}-${__ITER}-${Date.now()}`;
  let work = null;
  group('폴더 만들기', () => {
    work = createFolder(`k6-work-${tag}`);
  });
  if (!work) return;

  // 작업 폴더가 생긴 뒤에는 중간에 무엇이 실패해도 정리한다 — 루트에 k6-work-* 가 쌓이지 않게
  let trashedCopy = null; // 휴지통에 들어간 채 복원되지 않은 복사본(작업 폴더를 지워도 따라 지워지지 않는다)
  try {
    let archive = null;
    group('하위 폴더 만들기', () => {
      archive = createFolder(`k6-archive-${tag}`, work);
    });
    if (!archive) return;
    sleep(think);

    let copy = null;
    group('파일 복사·이름 바꾸기', () => {
      const source = sources[Math.floor(Math.random() * sources.length)];
      const copied = data(post(`/api/v1/files/${source}/copy`, 'copy_file', { pfs: work }, 201));
      copy = copied && copied.resourceSeq;
      if (copy) patch(`/api/v1/files/${copy}/rename`, 'rename_file', { rn: `k6-renamed-${tag}.txt`, ncp: 'AUTO' });
    });
    if (!copy) return;
    sleep(think);

    group('옮기기', () => {
      post(`/api/v1/files/${copy}/move`, 'move_file', { pfs: archive });
      patch(`/api/v1/folders/${archive}/rename`, 'rename_folder', { rn: `k6-archive-done-${tag}` });
    });
    sleep(think);

    group('휴지통·복원', () => {
      if (patch(`/api/v1/files/${copy}/delete`, 'trash_file').status !== 200) return;
      trashedCopy = copy;
      if (patch(`/api/v1/trash/files/${copy}/restore`, 'restore_file', { pe: true }).status === 200) trashedCopy = null; // pe: 원래 폴더로
    });
    sleep(think);
  } finally {
    // 정리 — 작업 폴더를 통째로 영구 삭제(안의 폴더·파일 포함, 스토리지 객체는 삭제 대기열로)
    group('정리', () => {
      if (trashedCopy) request('DELETE', `/api/v1/trash/files/${trashedCopy}`, 'delete_trashed_file');
      request('DELETE', `/api/v1/folders/${work}`, 'delete_folder');
    });
    sleep(think);
  }
}
