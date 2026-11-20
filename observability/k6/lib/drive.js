// Drive 내부 API(/api/v1) 공용 함수 — 로그인·요청 헤더·응답 검사
//
// 인증: 로그인하면 토큰이 쿠키(DRIVE_AT_USER·DRIVE_AT_ADMIN)로 온다. 이후 요청은 쿠키 + "X-Drive-Role" 헤더가 있어야
//   서버가 그 역할의 쿠키를 읽는다(헤더가 없으면 401). k6 는 VU 마다 쿠키 저장소를 따로 가져 자동으로 보낸다.
// 주의: 틀린 비밀번호 5번이면 계정이 잠긴다 — 비밀번호를 바꾸면 seed 부터 다시.
import http from 'k6/http';
import { check, fail } from 'k6';

export const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8082';
export const PASSWORD = __ENV.PASSWORD || 'LoadTest!2026';
export const ADMIN_ID = __ENV.ADMIN_ID || 'admin';
// 라이선스 없는 새 DB 는 좌석이 20개 남짓(admin 포함) — 그 이상은 관리자 화면에서 라이선스를 넣은 뒤 USERS 를 올린다
export const USER_COUNT = parseInt(__ENV.USERS || '18', 10);

export function userId(index) {
  return `lt-user-${String(index).padStart(3, '0')}@loadtest.local`;
}

export function roleHeaders(role, extra = {}) {
  return Object.assign({ 'X-Drive-Role': role }, extra);
}

export function login(role, id) {
  const path = role === 'ADMIN' ? '/api/v1/auth/admin/login' : '/api/v1/auth/login';
  const res = http.post(`${BASE_URL}${path}`, JSON.stringify({ userId: id, encPwd: PASSWORD }), {
    headers: { 'Content-Type': 'application/json' },
    tags: { api: role === 'ADMIN' ? 'admin_login' : 'login', name: 'login' },
  });
  if (!check(res, { 'login 200': (r) => r.status === 200 })) {
    fail(`login failed for ${id}: ${res.status} ${String(res.body).slice(0, 200)}`);
  }
}

// ApiResponse 의 data — 실패면 null
export function data(res) {
  try {
    const body = res.json();
    return body && body.result ? body.data : null;
  } catch (e) {
    return null;
  }
}

// 사용자(USER) 요청 하나 + 상태 코드 체크.
// api 태그로 Grafana·thresholds 에서 API 별로 나눠 본다. name 도 api 로 고정 — 기본값은 전체 URL 이라 id·검색어마다 Prometheus 시계열이 따로 생긴다
export function request(method, path, api, body = null, { expect = 200, headers = {}, timeout = '60s' } = {}) {
  const res = http.request(method, `${BASE_URL}${path}`, body, {
    headers: roleHeaders('USER', headers),
    tags: { api, name: api },
    timeout,
  });
  check(res, { [`${api} ${expect}`]: (r) => r.status === expect });
  return res;
}

// VU 마다 seed 사용자 하나로 로그인(VU 번호 순서대로 돌려 쓴다). 시나리오가 여럿이어도 VU 번호는 테스트 전체에서 겹치지 않는다
export function loginAsVuUser(vu) {
  const index = ((vu - 1) % USER_COUNT) + 1;
  login('USER', userId(index));
  return index;
}

// k6 는 파일이 없는 객체 본문을 form-urlencoded 로 보낸다 — multipart 가 필요한 API(사용자 등록)용으로 직접 만든다
export function multipart(fields) {
  const boundary = `----k6drive${Date.now()}${Math.floor(Math.random() * 1e6)}`;
  let body = '';
  for (const [name, value] of Object.entries(fields)) {
    body += `--${boundary}\r\nContent-Disposition: form-data; name="${name}"\r\n\r\n${value}\r\n`;
  }
  body += `--${boundary}--\r\n`;
  return { body, contentType: `multipart/form-data; boundary=${boundary}` };
}
