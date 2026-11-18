# Drive HTTP Storage Provider

Drive는 HTTP Storage만 사용합니다. Forward 콜백과 Forward 어댑터 설정은 제거했습니다.
Office 연결은 테넌트 관리자 설정 API `GET/PUT /api/v1/admins/system-config/office`에서 관리합니다.
기존 `tb_system_config`의 테넌트별 키/값을 사용하므로 DB 컬럼 추가는 필요하지 않습니다.

## 연결 방식

| officeConnectionType | 동작 |
| --- | --- |
| onPremise | 테넌트에 지정한 TFO 서버와 HTTP Storage로 연결 |
| none | Office URL 발급 차단. FE는 Office 파일도 일반 파일처럼 처리 |
| cloud | 준비 중. FE 선택 불가, BE 저장 검증에서도 거절 |

기존 연결 방식 값이 없는 설치는 Office 주소가 있으면 onPremise, 없으면 none으로 읽습니다.
새 저장 요청에는 officeConnectionType을 명시해야 합니다.
none 저장 시 기존 주소와 인증 정보를 보존하고 새 문서 URL 발급만 차단합니다. 이미 열린 문서는 기존 세션의 유효기간과 권한 검증을 통과하면 저장할 수 있습니다. TFO 어댑터나 원본 파일은 삭제하지 않습니다.

## On-premise 설정

1. TFO 관리자 사이트에 로그인하고 **외부연동 → 어댑터 추가 → HTTP Storage**를 선택합니다.
2. 이름에는 Drive 화면의 HTTP Storage 어댑터 이름을 그대로 입력합니다.
3. Provider Base URL에는 해당 Drive 테넌트의 BE 주소에 `/api/v1`을 붙여 입력합니다. 예: `https://drive.example.com/t/acme/api/v1`. TFO는 `/tfo-http-storage/v1`과 작업 경로를 자동으로 붙입니다. 따라서 이 접미사는 Provider Base URL에 입력하지 않습니다. 테넌트 경로(`/t/acme`)는 생략하지 않습니다.
   테넌트 주소/경로 규칙을 유지하고, TFO 서버에서 접근할 수 있는 주소를 사용합니다.
4. TFO의 **안전한 시크릿 생성**으로 생성한 **요청 서명 시크릿**을 Drive의 HTTP Storage 요청 서명키에 동일하게 입력합니다.
   기존 어댑터는 기존 값을 사용합니다. TFO는 등록한 어댑터의 서명키 변경을 허용하지 않습니다.
5. Ticket 방식은 TFO 서버 운영자로부터 `TFO_DOCUMENT_OPEN_TICKET_ISSUE_SERVICE_TOKEN` 값을 받아 Drive에 입력합니다.
   TFO에서도 Ticket 인증을 활성화해야 합니다. Legacy 방식에는 이 토큰을 저장하지 않습니다.
6. Drive 설정을 먼저 저장하고 TFO에서 연결 테스트를 실행합니다. 초기 서명 검증을 통과한 세션 없는 루트 LIST에는 `501 LIST_NOT_SUPPORTED`를 반환합니다. TFO의 목록 미지원 안내를 확인하고 등록을 계속합니다. 파일은 공개하지 않습니다. 어댑터 실행 상태를 확인합니다. Drive 저장은 TFO 어댑터 자동 등록 기능이 아닙니다.

PUT 예시(값은 예시용 자리표시자):
```json
{
  "officeConnectionType": "onPremise",
  "officeDomain": "https://office.example.com",
  "officeAuthMode": "ticket",
  "officeHttpStorageAdapterName": "acme-storage",
  "officeTicketServiceToken": "<TFO 서버의 서비스 토큰>",
  "officeHttpStorageRequestJwtSecret": "<TFO 어댑터와 동일한 32바이트 이상 서명키>"
}
```

Legacy는 `officeAuthMode: legacy`로 저장하며 서비스 토큰을 보내지 않습니다.
연동 해제는 `{"officeConnectionType":"none"}`만 보냅니다.
테넌트 관리자는 `officeHttpStorageAdapterName`을 지정합니다(영문·숫자·밑줄·하이픈, 최대 128자). 저장값이 없으면 테넌트 번호가 기본값입니다. 기존 설정을 유지하려면 PUT에서 이 필드를 생략합니다. 이름을 변경하면 즉시 적용되므로 문서를 저장하고 닫은 뒤 TFO 어댑터 이름도 동일하게 변경해야 합니다.

Developers 공용 연동은 기존 Super Admin Office 설정 API가 `serviceManaged`로 관리합니다. Developers는 Storage ID를 어댑터 이름으로 전달합니다. 테넌트 관리자 화면은 어댑터 이름을 읽기 전용으로 보여주며, 공용 토큰·서명키는 반환하지 않습니다. 테넌트 관리자는 관리형 설정을 수정할 수 없습니다. 새 DB 컬럼 없이 기존 `office_http_storage_adapter_name` 설정 키를 사용합니다.
비밀값은 요청에 따라 평문으로 저장하고 관리자 응답에서만 반환합니다. 관리자 화면은 기본적으로 값을 가립니다.
일반 사용자용 `GET /api/v1/config/office`에는 토큰과 서명키를 반환하지 않습니다.

전역 환경변수는 수명 설정 `TFO_STORAGE_SESSION_TTL`(기본 24h), `TFO_TICKET_OPEN_TTL`(기본 60s)만 사용합니다.
주소·어댑터·서명키·서비스 토큰을 전역 환경변수로 대체하지 않습니다.

## 요청 흐름

- Drive FE는 로그인 쿠키와 `X-Drive-Role` 헤더로 `POST /api/v1/office/client-sessions`를 호출한다.
- Drive FE는 편집에 Editor, 읽기 전용 공유 문서와 미리보기에 Viewer를 사용한다. Watch URL 함수와 BE의 Watcher 모드 정의를 제거했다. `*_WATCHER`를 지정한 새 문서 열기 요청은 400으로 거절한다. PDF는 Viewer로 연다.
  예: `{"ownerUserSeq":4,"resourceSeq":123,"app":"WRITE_EDITOR","lang":"ko_KR","authMode":"ticket"}`.
  자신의 문서는 `ownerUserSeq`를 생략할 수 있다. 공유 문서는 문서 소유자의 번호를 보낸다.
- Drive BE는 로그인 사용자와 현재 테넌트를 확인하고 문서의 VIEW/EDIT/DOWNLOAD 권한을 검사한다.
  FE가 전달한 이름·다운로드 허용 여부·어댑터·TFO 주소를 신뢰하지 않는다.
- `authMode`는 TFO `DOCUMENT_OPEN_AUTH_MODE` / `EnvVar.AuthMode`의 용어를 따른다. 값은 `ticket` 또는 `legacy`다. 생략하면 테넌트가 저장한 방식을 사용한다.
  `legacy`이면 BE가 기존 `/api/{adapter}/{documentPath}/open` URL을 조합하며 티켓 API를 호출하지 않는다.
  Legacy 응답의 `expiresAt`은 Drive 문서 세션의 만료 시각이다.
- `ticket`이면 Drive BE는 문서 접근 정보에 검증용 서명을 붙이고 TFO의 `POST /api/open-tickets`를 호출한다.
  문서 경로, 실행 모드, metadata 및 principal을 TFO에 전달한다. principal의 tenantId/userId는
  Drive의 고정 번호를 사용하므로 표시 이름이 바뀌어도 사용자 식별자는 변하지 않는다.
- TFO가 티켓을 발급하면 Drive BE는 `data.url`과 `data.expiresAt`(최초 열기 마감 Unix 초)을 반환한다.
  FE는 반환된 `/cloud-office/api/open-document?ticket=...` URL을 연다. Drive는 티켓을 직접 서명하지 않는다.
  티켓 발급이 실패하면 BE는 502를 반환하며 예전 URL로 우회하지 않는다.
- 연동이 해제된 테넌트의 새 URL 발급은 거절한다. 기존 세션의 HTTP Storage 콜백은 기존 권한·만료·서명을 계속 검증한다.

### 티켓 발급 설정

- TFO 주소는 관리자 Office 설정의 주소를 사용한다. 별도 티켓 서버 주소는 설정하지 않는다. 주소에 `/cloud-office`가 없으면 붙인 뒤 `/api/open-tickets`로 티켓을 요청한다. 문서 열기 URL에도 같은 주소를 사용한다.
- 티켓 발급 서비스 토큰은 테넌트 관리자 Office 설정에서 저장한 값을 사용한다.
- `TFO_TICKET_OPEN_TTL`: 최초 열기 제한 시간. 기본 `60s`, 최대 `12h`.
- 브라우저가 접근하는 TFO 주소는 기존 테넌트의 `office_domain` 설정을 사용한다.
- HTTP Storage 요청 서명키와 티켓 발급 서비스 토큰은 별개다. 일반 사용자 FE에는 서비스 토큰을 전달하지 않는다.

- TFO가 연결별로 보관한 metadata를 요청마다 `X-TFO-Storage-Request-JWT`의 `request.client_metadata`에 포함한다.
- Provider는 HS256/typ/issuer/audience/60초 이하 유효기간/adapter/method/raw path/content type/길이/SHA-256을 검증한다.
  Drive가 발급한 접근 정보의 proof를 검증한 뒤 테넌트·사용자·소유자·문서·실행 모드·만료 시각을 확인한다.
  활성 테넌트, 사용자 상태와 현재 문서 권한은 기존 Drive 서비스를 통해 매 요청에서 다시 확인한다.
- HTTP Storage는 Redis를 사용하지 않는다. 별도의 세션 테이블이나 연결별 잠금 소유자 테이블도 만들지 않는다.
- Drive는 `client_metadata`에 tenantSeq, userSeq, ownerUserSeq, resourceSeq, adapter, path, app, expiresAt,
  proof를 전달한다. proof는 테넌트의 요청 서명키로 HMAC-SHA256을 계산한 값이다.
  서명 입력에 `drive-http-storage-access-v1` 구분자를 넣으므로 TFO 요청 JWT와 용도가 다르다.
- TFO는 metadata를 연결에 보관하고 콜백에 그대로 포함한다. TFO의 요청 서명만으로는 브라우저가
  Legacy URL에 넣은 사용자 정보의 진위를 알 수 없으므로 Drive의 proof도 반드시 검사한다.
  이름·문서·권한·만료 시각을 바꾼 metadata는 TFO가 요청을 서명해도 거절한다.
- metadata는 암호문이 아니다. URL·metadata·JWT 원문을 로그에 기록하지 않는다.
  proof는 특정 문서와 실행 모드, 유효기간으로 제한된 접근 증명이며 장기 서비스 인증키가 아니다.
- 요청 JWT의 `jti`는 형식을 확인하지만 사용 이력을 저장하지 않는다. 동일한 서명 요청을 유효기간 안에
  재전송하면 다시 처리할 수 있으며, PUT 재전송은 새 버전·감사 기록을 만들 수 있다.
  서명·본문 해시·최대 60초 유효기간 검증은 유지하지만 중복 실행 방지를 보장하지 않는다.
  TLS를 사용하고, 결과를 알 수 없는 PUT을 자동 재전송하지 않는다.
- 접근 정보는 기존 `TFO_STORAGE_SESSION_TTL`(기본 24h)로 만료한다. 개별 접근 증명을 서버에서
  폐기하는 기능은 없다. 사용자 비활성화·권한 회수는 즉시 재검사하며 서명키를 변경하면 기존 증명이 무효가 된다.

## 내 파일과 하위 폴더 LIST

Drive FE는 문서 열기에 `POST /api/v1/office/client-sessions`만 사용한다. 별도의 목록 세션 발급 API는 없다.
TFO가 HTTP Storage 어댑터의 LIST를 호출하면 문서를 열 때 받은 `__tfo_adapter_http_storage_client_metadata`를 그대로 전달한다.
Drive는 서명과 문서 세션의 유효성, 테넌트·사용자 일치를 확인하고 해당 사용자의 내 파일 목록만 반환한다.
익명 사용자의 목록 조회는 허용하지 않는다. Drive 화면의 일반 파일 목록은 기존 Drive 파일 API로 조회한다.

TFO는 LIST 요청 JWT의 `request.client_metadata`로 이를 전달한다.
Provider URL은 루트가 `GET /api/v1/tfo-http-storage/v1/list`, 하위 폴더가
`GET /api/v1/tfo-http-storage/v1/{folderPath}/list`다. 각 경로 segment를 UTF-8 percent-encoding한다.
`folderPath`는 사용자 '내 파일' 기준 상대 경로이며, 응답의 directory `path`를 다음 LIST에 그대로 사용한다.

```json
{"entries":[{"path":"내 문서","name":"내 문서","type":"directory","size":0,"readable":true,"writable":false,"locked":false}]}
```

- 루트/폴더의 바로 아래 항목만 반환한다. 사용자의 소유 파티션 및 `isDelete=false`로 조회하므로 휴지통과 다른 사용자의 파일은 포함하지 않는다.
  '공유받은 파일' 등 가상 목록은 이번 범위에 포함하지 않는다.
- 모든 상위 폴더를 동일 사용자 범위에서 순서대로 확인하고, 활성 테넌트/계정·테넌트 소속·VIEW 권한을 매 요청마다 검사한다.
- 빈 폴더는 `200 {"entries":[]}`, 존재하지 않거나 삭제된 폴더 및 파일 경로에 대한 LIST는 404다.
- 응답은 정확한 `Content-Length`와 `Cache-Control: no-store`를 가진 JSON이다. TFO 규격상 파일 크기가 300 MiB를 넘는 파일은 목록에서 제외한다.
- 한 폴더에 10,000개를 초과하는 항목이 있거나 응답 JSON이 5 MiB를 넘으면 413을 반환한다. 규격에 페이지네이션이 없어 조용히 잘라 반환하지 않는다.
  동일 이름의 파일/폴더가 중복 경로를 만들면 409, 잘못된 이름이나 64단계를 넘는 폴더 경로는 400이다.
- HTTP Storage 접근 정보는 서버에 저장하지 않으며 서명된 `expiresAt`까지 유효하다. 접근 증명은 로그/고정 설정에 저장하지 않는다.
  만료 후 재발급하고, 익명 전환·계정/테넌트 비활성화·테넌트 변경 시 기존 세션도 거부한다.

목록 entry의 `writable`은 false다. 목록 조회 결과만으로 다른 파일의 저장 권한을 부여하지 않는다.
파일 내용을 열거나 편집할 때는 기존 `POST /api/v1/office/client-sessions`로 해당 파일의 문서 세션을 별도로 발급한다.
목록의 이름 기반 경로와 문서 세션의 `documents/{ownerUserSeq}-{resourceSeq}/{fileName}` 경로는 각 용도에 맞게 사용한다.
이 구현은 Provider와 세션 발급 API를 제공한다. TFO/Developers의 파일 선택 UI 및 선택 결과를 문서 열기에 연결하는 코드는 포함하지 않는다.

## 지원 범위

경로는 `documents/{ownerUserSeq}-{resourceSeq}/{fileName}`이다. `path`와 마지막 segment인 `name`의 일치를 보장하며,
파일명이 바뀌어도 이미 열린 세션의 경로는 유지한다. 저장 응답 `docId`는 `{ownerUserSeq}-{resourceSeq}`로 안정적이다.

| 동작 | 처리 |
| --- | --- |
| INFO | JSON metadata, 실제 권한, DB 잠금 상태 |
| GET | 고정 Content-Length로 스트리밍, 문서 열기/미리보기 감사 로그 |
| PUT | 300 MiB 이하 임시 파일에서 길이·해시 검증 후 기존 Drive 버전/쿼터/저장/감사 로직 호출 |
| LOCK / UNLOCK | 서명된 connection과 JSON owner 일치 확인, 기존 ResourceLockUnlockService로 파일 잠금·해제 처리 |
| LIST | 문서 세션에 연결된 요청자를 기준으로 사용자 '내 파일' 루트·하위 폴더의 바로 아래 항목 조회 |
| MKDIR / RENAME / DELETE | 서명·본문 검증 후 정확한 `501 {"code":"<OPERATION>_NOT_SUPPORTED"}` |

잠금 상태는 기존 `TB_RESOURCE.is_locked`, `locked_date`를 재사용한다. 추가 컬럼/테이블은 없다.
기존 Forward와 동일하게 ResourceLockUnlockService의 파일 단위 잠금·해제를 사용한다.
연결별 잠금 소유자를 별도로 기록하지 않으며, 다른 연결의 잠금이라는 이유로 409를 반환하지 않는다.
TFO가 편집 연결을 조정하고 Drive는 기존 파일 잠금과 만료 작업을 유지한다.
기존 Drive 서비스 외에 HTTP Storage가 DB 행 잠금이나 저장 전체 트랜잭션을 추가하지 않는다.
PUT의 autosave도 실제 저장해야 하므로 기존 서비스에서 저장을 생략하는 타입은 일반 SAVE로 매핑한다.
HTTP Storage open URL의 `upgid`로 다른 이름으로 저장은 숨기며, Provider는 `saveas`로 기존 파일을 덮어쓰지 않는다.
신규 문서는 기존 Drive 문서 생성 API가 파일을 준비한 뒤 연다.

Forward XML의 `profileImage`, `cursorColor`, `userCursorName`을 HTTP Storage INFO JSON에 추가하지 않는다.
현재 TFO 규격은 이 필드를 허용하지 않는다. 해당 표시 기능의 동등성 및 실제 Office 공동편집 UI 동작은
TFO 배포 환경에서 별도로 확인해야 한다.

## 검증

Backend:

```sh
mvn -Dfrontend.skip=true -Dmaven.compiler.proc=full \
  -Dtest=HttpStorageListTest,HttpStorageContractTest,HttpStorageConfigurationTest,OfficeTicketClientTest,IpAccessFilterTest test
```

계약 테스트는 실제 서명/검증, MVC 요청 처리, 파일 staging과 기존 서비스 호출을 검증한다.
테넌트·사용자·문서·모드 변조, 만료, 권한 회수, 본문 변조, 반복 요청과 병렬 저장을 확인한다.
파일 잠금은 기존 서비스 호출을 검증하며 연결별 독점 잠금은 테스트 대상이 아니다.
사용자·문서 조회와 파일 저장 서비스는 테스트 대역이다. HTTP Storage 테스트에는 Redis 서버가 필요 없다.

로컬 CloudOffice의 빌드 결과가 있으면 실제 `HttpStorageAdapter`를 별도 classloader로 불러
HTTP 루트/하위 폴더 LIST와 INFO→LOCK→PUT→GET→UNLOCK 왕복을 추가 검증할 수 있다. 해당 바이너리를 Drive에 포함하지 않는다.

```sh
mvn -Dfrontend.skip=true -Dmaven.compiler.proc=full -Dtest=HttpStorageContractTest \
  -Dtfo.http.storage.classes=/path/to/CloudOffice/cloud-office-admin/target/classes \
  -Dtfo.http.storage.classpath=/path/to/CloudOffice/cloud-office-admin/target/test-runtime-classpath.txt test
```

배포 환경 최종 확인은 테넌트별 TFO 연결, 로그인/익명 공유 문서 열기, 읽기 전용 저장 거부,
한글 파일명, 저장 후 재열기, 공동편집·잠금 해제, 권한 회수와 세션 만료를 포함한다.

기존 전체 애플리케이션 기반 테스트는 별도의 환경 준비가 필요하다. 현재 테스트 profile의
logging 설정은 존재하지 않는 `log/log4j2-test.yml`을 참조하며, 이를
`-Dlogging.config=classpath:log4j2-test.yml`로 보정해도 S3 테스트 서버가 연결되어 있어야 부팅된다.
기존 `TenantBoundaryFilterTest`의 `superadmin/login` fixture도 실제 `super-admin/login` 경로와 다르다.
이 연동 작업에서는 해당 기존 설정/fixture를 변경하지 않았다.

## 2026-11-04 범위 제한 및 롤백

저장 전체에 DB 연결을 유지하던 HttpStorageSaveMutex와 인스턴스당 1건 제한을 제거했다.
기존 DefaultResourceLockUnlockService를 원래 코드로 복원하여 잠금·자동 만료 동작을 변경하지 않는다.
HTTP PUT은 현재 권한과 본문을 확인한 다음 기존 저장 로직을 호출한다. HTTP Storage가 저장 전체를 감싸는 DB 연결/트랜잭션은 없다.
Viewer LOCK/UNLOCK 거부와 PUT 본문 수신 전 권한 검사는 유지한다.
서로 다른 문서의 저장은 병렬 진입할 수 있다. 단, 검사 이후 잠금 변경을 완전히 차단한다는 보장은 철회한다.
기존 저장 로직 내부의 트랜잭션, 일반 파일 쓰기, 자동 만료 코드는 이번 범위에서 변경하지 않는다.


## 로컬 개발 설정

로컬에서도 테넌트 관리자 Office 설정에 주소·방식·토큰·서명키를 저장합니다.
로컬 TFO에 해당 테넌트 ID 이름의 HTTP Storage 어댑터를 등록하고 동일 서명키를 지정합니다.
운영 인증 값을 로컬 테스트에 재사용하지 않습니다.

## 서비스 구성

`HttpStorageController`는 요청 검증과 HTTP 응답 작성을 담당한다. `HttpStorageService`는 목록 조회, 문서 정보 조회, 다운로드, 저장, 잠금·해제 처리를 담당한다. 목록 조회만 읽기 전용 트랜잭션으로 실행하며, 파일 전송·저장 전체를 서비스 트랜잭션으로 감싸지 않는다. 목록 조회는 익명 계정을 허용하지 않고, 문서 접근은 기존 공유 권한 규칙을 유지한다.

이전 버전의 테넌트별 브라우저 연동 검증 기록과 최신 변경의 검증 범위는 [TESTING.md](TESTING.md)를 참고합니다.

## Redis 의존성 제거 후 배포

이 변경은 HTTP Storage에서 추가한 Redis 세션·요청 jti·연결별 잠금 소유자 기록만 제거한다.
Drive의 다른 기능이 사용하는 Redis 설정이나 의존성은 변경하지 않는다.
TFO는 기존대로 metadata를 보관·전달하므로 TFO API 변경은 필요 없다.
기존 `sessionId`만 담긴 열기 링크와 이미 열린 연결은 새 접근 증명이 없으므로 거절한다.
배포 전 편집 중인 문서를 저장하고 닫은 뒤, 배포 후 Drive에서 새 URL을 받아 다시 열어야 한다.
롤링 배포 중에는 구·신 버전이 서로 다른 metadata를 사용하므로 이 변경을 혼용 배포하지 않는다.
예전 Redis 키는 기존 TTL에 따라 만료되며, 새 코드는 이 키를 조회하지 않는다.
