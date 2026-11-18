# Drive 가입 자동 연동 로컬 검증 — 2026-11-04

## 결과

initContainer의 스크립트를 PostgreSQL 클라이언트 컨테이너에서 직접 실행한 뒤,
Developers 가입 → Drive 테넌트·계정·역할별 키 생성 → Office 설정 → TFO 어댑터 등록 → 브라우저 편집·저장을 검증했습니다.
Kubernetes 클러스터는 생성하지 않았고 운영 서비스나 운영 DB도 변경하지 않았습니다.

| 검증 | 결과 |
| --- | --- |
| 빈 DB 최초 초기화, 재실행, 동시 기동, SQL 실패·롤백·복구, 부분 스키마, 관리자 누락·비활성, 연결 종료 | 11개 검사 통과 |
| Developers 단위·통합 회귀 검사 | 10개 통과: 작업 생성·롤백·실패·동시 실행·중단·암호화·트랜잭션·PATCH |
| Drive Office 설정·티켓 검사 | 16개 통과 |
| 실제 OAuth 가입 서비스 → 실제 Drive·TFO | 서로 다른 2개 계정 준비 완료 |
| 실제 HTTP 이메일 가입 → Mailpit 수신 → 인증 완료 → 로그인 → Drive·TFO 준비 | H2와 별도 PostgreSQL DB에서 각각 통과 |
| Developers PostgreSQL 마이그레이션 | 빈 DB에 V1~V49 모두 적용, Hibernate validate 및 실제 이메일 가입 연동 통과 |
| API Key로 업로드·문서 열기·브라우저 편집·Drive 저장 | 서로 다른 두 테넌트에서 성공 |
| 저장 결과 검증 | 다운로드한 DOCX XML에 자신의 수정 문장 존재, 다른 테넌트 문장 없음 |
| 다른 테넌트의 API 경로·문서 ID·Office 열기 요청 | 모두 거절 |
| 공용 Office 비밀값과 설정 보호 | 테넌트 관리자 응답에서 비밀값 제외, 변경은 403, 일반 USER의 관리자 접근도 403 |
| 테넌트 ADMIN 키로 Super Admin 설정 API 호출 | 주소 형태에 따라 401 또는 403, 모두 차단 |
| Drive FE | production build 통과 |
| Helm chart | 원본 사본 일치, lint, template 검증 통과. 실제 클러스터 배포는 하지 않음 |

두 테넌트의 문서는 동일한 브라우저 컨텍스트의 별도 탭에서 동시에 열었습니다.
화면의 수정 표시만 확인하지 않고, 저장 후 다운로드한 실제 DOCX 내용을 비교했습니다.
이메일 가입 검증은 브라우저에서 폼을 클릭한 것이 아니라 실제 HTTP 클라이언트로 CSRF·세션·메일 인증 API를 호출했습니다.
TFO는 로컬 Docker 이미지 `thinkfree/office-single-node-trial:17.1.1-20261103-008`을 사용했습니다.

## 검증 중 수정한 문제

- Developers HTTP 클라이언트를 PATCH를 지원하는 구현으로 바꿨습니다.
- 자동 생성한 Drive 초기 비밀번호를 Drive의 8~64자 규칙에 맞췄습니다.
- Drive에 로컬 TFL의 검증용 공개키를 설치했습니다. 라이선스 검증을 우회하거나 개인 서명키를 가져오지 않았습니다.
- Drive 문서 열기 API에 외부 API Key용 매핑과 WRITE scope 검사를 추가했습니다.
- 서비스가 관리하는 Office 설정을 테넌트 관리자가 변경하려 할 때 500 대신 403을 반환하도록 수정했습니다.
- Developers 재시도 진입점에서도 다음 재시도 시각을 검사하도록 보완했습니다.
- Helm chart에 라이선스 검증용 공개키의 ConfigMap 마운트 옵션과 설명을 추가했습니다.

임시 라이선스는 OFFLINE/PERSEAT이며 Drive 조회 결과 종료일은 9999-12-31입니다.
동일한 파일을 여러 테스트 테넌트에 등록하는 데 성공했습니다. 이 결과를 운영 라이선스 발급 정책으로 해석하지 마세요.

## 테스트 환경과 증거

기존 로컬 DB와 분리한 Drive DB 및 Developers DB를 사용했습니다.
새 Drive DB의 테넌트 번호는 기존 로컬 TFO 테스트 어댑터와 충돌하지 않도록 10000부터 시작하게 한 테스트 fixture입니다.
제품의 initialize.sql 실행 구문은 변경하지 않았습니다.
Developers DB에서는 실제 Flyway 마이그레이션을 실행했습니다.
초기화 스크립트의 연결 종료 검사는 서버가 단절을 감지하는 상황을 `pg_terminate_backend`로 재현했습니다.

비밀값을 제외한 결과와 화면·저장 파일은 `/home/chan/.local/share/thinkfree-evidence/drive-signup-20261104/`에 보관합니다.
API Key와 설정 파일은 저장소에 포함하지 않았습니다.

## 아직 준비되지 않은 항목

`SYSTEM_MANAGED`, `SINGLE_ACCOUNT`만 남았다는 뜻은 아닙니다.
현재 검증한 범위는 가입 자동 준비와 Drive–TFO 문서 연동입니다.
다음 항목은 별도 구현 또는 검증이 필요합니다.

- Developers API Key를 해당 계정의 Drive ADMIN/USER 키로 교환하는 프록시.
- Playground의 Drive API 목록·iframe·postMessage 연결 화면.
- Drive API Key의 자동 갱신, 결과 불명확 상태를 운영자가 확인하고 안전하게 재개하는 관리자 기능.
- Pro 구독 변경에 따른 Drive 용량 갱신.
- 운영 이미지·Secret·네트워크 설정 및 실제 배포 확인.

테넌트 타입 두 개는 요청대로 미구현 상태를 유지했습니다.
서비스가 Office 설정을 관리한다는 별도 표시를 추가했지만, 이를 SYSTEM_MANAGED 테넌트 타입으로 취급하지 않습니다.
