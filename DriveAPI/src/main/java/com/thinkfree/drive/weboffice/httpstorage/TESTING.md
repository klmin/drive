# 테넌트별 Office 연동 검증

검증일: 2026-11-04 (KST). 아래 브라우저 기록은 Redis 제거 이전 구현의 로컬 검증이다. Redis 제거 이후의 검증 범위는 마지막 절에 별도로 기록한다. 운영 데이터나 운영 서버는 사용하지 않았다.

## 이전 구현의 브라우저 검증 환경

- Drive: 이 변경의 BE와 프로덕션 빌드한 FE, PostgreSQL 및 Redis 테스트 인스턴스.
- TFO: `thinkfree/office-single-node-trial:17.1.1-20261103-008`.
- 서로 다른 두 PATH 테넌트, 사용자, HTTP Storage 어댑터와 서명키를 사용했다.
- 테넌트별 문서를 업로드하고 같은 Chromium 브라우저의 두 탭에서 각각 열었다.
- 로컬 TFO의 Apache는 기존 테스트 설정인 `ProxyPreserveHost On`을 사용했다. 이 변경은 TFO 소스나 운영 설정을 변경하지 않는다.

## 실제 연동 결과

| 검증 | 결과 |
|---|---|
| 테넌트별 관리자 Office 설정 저장 | 두 테넌트 모두 성공 |
| TFO 어댑터 연결 테스트·등록·활성화 | 두 테넌트 모두 성공 |
| Ticket 방식 문서 열기·편집·저장 | 두 테넌트 모두 성공. WebSocket 연결과 저장 응답 200 확인 |
| Ticket 저장 파일 재다운로드 | 각 DOCX의 `word/document.xml`에 자기 테넌트의 편집 내용만 존재 |
| Ticket 저장 문서를 Viewer로 다시 열기 | 각 테넌트의 저장 내용 표시. 다른 테넌트의 내용은 표시되지 않음 |
| Legacy 방식 문서 열기·편집·저장 | 별도 문서로 두 테넌트 모두 성공 |
| Legacy 저장 파일 재다운로드 | 자기 테넌트의 새 편집 내용만 존재 |
| 테넌트별 서명 GET·LIST | 성공. 상대 테넌트의 문서는 목록에 없음 |
| 상대 테넌트 소유자·문서로 URL 발급 | 403 |
| 자기 소유자 번호와 상대 문서 번호로 URL 발급 | 404 |
| 자기 로그인 쿠키로 상대 테넌트 URL 발급 | 401 |
| 상대 테넌트에 자기 문서 세션으로 GET·PUT | 403. 상대 서명키로 정상 서명한 요청도 세션 불일치로 거부 |
| 자기 세션으로 다른 문서 경로 접근 | 403 |
| 세션 ID는 유지하고 metadata의 테넌트·사용자만 변경 | 403 |
| 상대 테넌트 URL에 자기 서명키 사용 | 401 |
| Viewer 세션으로 PUT | 403 |

교차 접근 검증은 양방향으로 실행했다. 정상 GET·LIST를 포함한 22건이 모두 기대한 상태 코드를 반환했다.
이 결과는 위 경로와 조건의 검증이며 모든 보안 문제의 부재를 보증하지 않는다.

## 자동 테스트 및 UI

- BE 130건 통과, 실패·생략 0건. 실제 TFO adapter 클래스를 로드하는 계약 테스트 포함.
- FE Node 테스트 8건 통과.
- FE 프로덕션 빌드 및 정적 페이지 51개 생성 성공.
- Office 설정의 Ticket/Legacy/연동하지 않기, 비어 있는 입력과 저장된 입력을 브라우저에서 확인.
- 390px 모바일 화면에서 가로 넘침 없음. 화면 JavaScript 오류 없음.
- 테넌트 경로를 포함한 요청은 전체 raw path를 서명해야 한다. 접두사를 생략하거나 다른 테넌트 경로로 바꾼 서명은 거절하는 회귀 테스트를 추가했다.

## 확인 중 수정한 안내

TFO의 Provider Base URL에는 `https://drive.example.com/t/acme/api/v1`을 입력한다.
TFO가 `/tfo-http-storage/v1`과 작업 경로를 붙이므로 이 접미사를 설정값에 중복 입력하지 않는다.
Drive FE의 한국어·영어·스페인어 안내와 README를 이 규칙으로 수정했다.

## 재실행

BE에서 다음 테스트를 실행한다. TFO 계약 테스트를 생략하지 않으려면 로컬 TFO 클래스와 런타임 classpath 파일을 지정한다.

```text
mvn -o -Dfrontend.skip=true -Dmaven.compiler.proc=full \
  -Dtfo.http.storage.classes=<CloudOffice>/cloud-office-admin/target/classes \
  -Dtfo.http.storage.classpath=<CloudOffice>/cloud-office-admin/target/test-runtime-classpath.txt \
  -Dtest=OfficeConnectionSettingsTest,HttpStorageContractTest,HttpStorageListTest,HttpStorageConfigurationTest,OfficeTicketClientTest,IpAccessFilterTest test
```

FE: `node --test tests/scripts/office-settings.test.cjs tests/scripts/web-office-ticket.test.cjs` 및 `npm run build`.
실제 연동은 별도 테스트 테넌트와 문서로 위 표를 재실행하고, 저장 응답뿐 아니라 Drive에서 다운로드한 파일 내용까지 비교한다.
서명키, 서비스 토큰, 로그인 쿠키와 실제 문서 열기 URL은 테스트 보고서나 Git에 저장하지 않는다.

## Redis 제거 이후 검증 (2026-11-04)

최신 develop 기반 별도 작업 폴더에서 141건 통과, 실패·오류·생략 0건.
HTTP Storage 테스트 실행에 Redis 서버를 사용하지 않았다.

- HttpStorageContractTest 81건: 접근 증명 변조, 만료, 테넌트 경계, Viewer 저장 거부, 권한 회수,
  파일 본문 검증, 병렬 저장, 기존 잠금 서비스 호출, 다른 검증 인스턴스에서의 접근 증명 검증, 키 변경.
- HttpStorageListTest 26건: 목록 범위, 변조·만료 처리, 현재 권한, 경로/크기 제한, 저장 상태 없는 반복 요청.
- HttpStorageConfigurationTest 2건, OfficeConnectionSettingsTest 8건, OfficeTicketClientTest 5건.
- IpAccessFilterTest 19건.
- 실제 로컬 TFO HttpStorageAdapter 클래스를 로드하고 HTTP 서버를 통해
  LIST(루트·하위 폴더), INFO, LOCK, PUT, GET, UNLOCK을 왕복했다.
  PUT한 바이트와 GET으로 받은 바이트가 같음을 확인했다.
- 변경 후 브라우저 전체 시나리오 및 운영 배포 검증은 수행하지 않았다.
  위의 이전 브라우저 검증 결과를 새 코드의 실행 결과로 취급하지 않는다.

재실행에는 위 명령에 TFO 클래스·classpath를 함께 지정한다. HTTP Storage 관련 Java 코드에는
Redis 클라이언트 참조가 없다. 새 세션/잠금 테이블, DB 스키마 변경도 없다.
