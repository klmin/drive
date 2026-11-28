# Drive 환경변수

`src/main/resources/application.yml` 의 `${...}` 59개를 기준으로 정리했다(필수 17 · 선택 42).
서버(운영·dev·설치본·k8s)는 OS 환경변수로 넣고, 로컬은 `DriveAPI/.env` 파일로 넣는다.

| 하려는 일 | 볼 곳 |
|---|---|
| dev·운영·docker·k8s 서버를 처음 띄우기 | 1-1(순서) → 1-2(환경별 값) |
| 서명·암호화 키 3개 만들기 | 1-3(스크립트) · 4번 아래 "서명·암호화 키"(뜻) |
| 기동이 안 될 때 | 1-4 |
| 설치본(DriveInstaller) 만들기·설치 | 2번 |
| 로컬에서 띄우기·테스트 | 3번 |
| 키 하나의 뜻·기본값 찾기 | 4번(필수)·5번(선택) |

## 1. 서버 (dev·운영·k8s)

### 1-1. 처음 띄우기 — 순서대로

설정 파일 하나(아래 예: `/etc/drive/drive.env`)에 값을 모아 두고, 기동할 때마다 그 파일을 읽는다. 프로필(`--spring.profiles.active`)은 필요 없다.

1. **설정 파일 작성** — 4번 필수 값 중 키 3개(`DRIVE_JWT_SECRET_KEY`·`DRIVE_SMTP_AES_SECRET_KEY`·`DRIVE_DOWNLOAD_SECRET_KEY`)를 뺀 14개 + 1-2 의 환경별 값. `$` 가 들어간 값은 작은따옴표로 감싼다.

   ```bash
   export DRIVE_DATABASE_HOST='db.example.local'
   export DRIVE_DATABASE_PORT='5432'
   export DRIVE_DATABASE_NAME='t_drive'
   export DRIVE_DATABASE_USERNAME='t_drive_user'
   export DRIVE_DATABASE_PASSWORD='…'
   export DRIVE_STORAGE_TYPE='ceph'            # ceph | s3 | rustfs
   export DRIVE_STORAGE_ENDPOINT='http://…'    # s3 면 대신 DRIVE_STORAGE_REGION
   export DRIVE_STORAGE_ACCESS_KEY='…'
   export DRIVE_STORAGE_SECRET_KEY='…'
   export DRIVE_STORAGE_BUCKET_NAME='…'
   export GOVERNANCE_SDK_ENDPOINT='http://…/v1'  # 감사 로그 SDK 4개(끄려면 GOVERNANCE_AUDIT_ENABLED='false')
   export GOVERNANCE_SDK_TENANT_ID='…'
   export GOVERNANCE_SDK_KEY_ID='…'
   export GOVERNANCE_SDK_BASE64_SECRET='…'
   ```

2. **키 3개 추가** — 같은 파일에 키를 만들어 넣는다(1-3). 기존 DB(운영·dev)에 붙는 서버는 `--existing-db` 필수.

   ```bash
   bash DriveInstaller/thinkfree/TFD/generate_secret_keys.sh /etc/drive/drive.env --existing-db
   ```

   `export DRIVE_JWT_SECRET_KEY='…'` 등 3줄이 한 곳에 순서대로 쓰이고 권한이 600 이 된다 — `# SecretKey` 주석이 있으면 그 아래, 없고 키 줄(빈 값 `''` 포함)이 있으면 그 자리, 둘 다 없으면 `DRIVE_DATABASE_PASSWORD` 줄 뒤(없으면 파일 끝)에 `# SecretKey` 와 함께. 흩어진 키 줄은 한 곳으로 모은다. 값이 있는 키는 그대로 두고, 다시 실행해도 파일은 바뀌지 않는다.
3. **기동** — 파일을 읽어(export) 띄운다. 로그는 `/log/drive/` 아래(앱 로그 `backend/`, 톰캣 접근 로그 `web/`)에 쌓이므로 그 폴더에 쓰기 권한이 있어야 한다 — 바꾸려면 `LOG_PATH`·`ERROR_LOG_PATH`·`DRIVE_WEB_LOG_DIRECTORY`(5번).

   ```bash
   source /etc/drive/drive.env
   java -jar Thinkfree-Drive-*.jar
   ```

4. **확인** — 로그에 `Started ThinkfreeDriveApplication`, `curl -s -o /dev/null -w '%{http_code}' localhost:8080/api/v1/common/ping` 이 `200`.

docker·k8s 는 같은 내용을 `KEY=value` 형식(따옴표·`export` 없음)으로 쓴다 — 키는 `generate_secret_keys.sh <파일> --dotenv` 로 추가하고, `docker run --env-file <파일>` 또는 `kubectl create secret generic drive-env --from-env-file=<파일>` + Deployment `envFrom: [{secretRef: {name: drive-env}}]`. k8s readinessProbe 는 `httpGet /api/v1/common/ping :8080` 을 쓰면 된다. 재기동(컨테이너 재생성·`rollout restart`)해도 같은 파일·Secret 을 쓰는 한 키가 같아 로그인이 유지된다.

### 1-2. 환경별로 더 넣을 값

- **dev**: 키 3개는 로컬 `.env.example` 과 같은 값을 쓴다(같은 dev DB 라 SMTP AES 가 같아야 하고, 그대로 옮기면 가장 간단하다). 기존 dev 동작을 유지하려면 `DRIVE_FRONT_BASE_URL`(기존 `http://localhost:3000`), `DRIVE_TENANT_DEV_FALLBACK_ENABLED=true`, 외부 호출 본문 로그 `DRIVE_REST_CLIENT_TRACE_ENABLED=true`(기존 dev 는 켜짐), 감사 로그 인스턴스 이름 `GOVERNANCE_ORIGIN_INSTANCE_ID`(예: `tfd-dev`). Ceph 용량 조회를 쓰면 `DRIVE_STORAGE_ADMIN_*` 3개.
- **운영**: 키 3개 — SMTP AES 는 **예전 jar 내장값 그대로**(`--existing-db` 가 넣어 준다), JWT·다운로드는 새로 만든다. `GOVERNANCE_ORIGIN_INSTANCE_ID` 에 설치처 이름, `DRIVE_EMBED_ALLOWED_ORIGINS` 에 허용 origin 지정(기본 `*`), HTTPS 로 서비스하면 `DRIVE_AUTH_COOKIE_SECURE=true`. `DRIVE_TENANT_DEV_FALLBACK_ENABLED`·`DRIVE_REST_CLIENT_TRACE_ENABLED` 는 넣지 않는다(기본 `false`).
- **k8s**: 배치 담당이 아닌 파드는 `DRIVE_SCHEDULER_BATCH_ENABLED=false`.
- **앞단 프록시(Apache·nginx·LB)와 클라이언트 IP**: 앱은 `X-Forwarded-For` 를 직접 읽지 않고, Tomcat 이 **신뢰 프록시에서 온 요청만** 그 헤더로 클라이언트 IP 를 정한다(IP 접근 제한·감사 로그가 이 값을 쓴다). 기본 신뢰 범위는 사설 대역(10/8·172.16/12·192.168/16·127/8 등)이다.
  - 프록시가 공인 IP 면(CDN 등) `SERVER_TOMCAT_REMOTEIP_TRUSTEDPROXIES` 에 그 주소 정규식을 넣는다. 안 넣으면 모든 요청이 프록시 IP 로 보인다.
  - 사용자 PC 가 사설 대역에 있는 설치처는 `SERVER_TOMCAT_REMOTEIP_INTERNALPROXIES` 를 **프록시 주소만**으로 좁힌다(예: `10\.0\.1\.5`). 기본 범위 그대로면 사설 대역 사용자가 보낸 `X-Forwarded-For` 도 프록시가 붙인 것으로 보고 따라가, IP 접근 제한을 피할 수 있다.

### 1-3. 키 3개 만들기 — `DriveInstaller/thinkfree/TFD/generate_secret_keys.sh`

키는 **처음 한 번 만들어 파일에 저장하고, 이후 기동마다 같은 값을 쓴다**(기동할 때마다 새로 만들면 로그인 세션이 끊기고 저장된 SMTP 비밀번호를 못 푼다). 키 설명은 4번 아래 "서명·암호화 키".

| 하려는 일 | 실행 | 결과 |
|---|---|---|
| 파일에 만들어 두고 그 파일로 기동(VM) | `bash generate_secret_keys.sh <파일> [--existing-db]` | 파일에 `export KEY='…'` 3줄, 화면에도 출력 |
| docker·k8s 용 파일 | `bash generate_secret_keys.sh <파일> --dotenv [--existing-db]` | 파일에 `KEY=…` 3줄 |
| 만들면서 바로 이 셸에 export | `source generate_secret_keys.sh <파일> [--existing-db]` | 파일 저장 + 현재 셸 export(이어서 `java -jar`) |
| 파일 없이 값만 받아 직접 입력 | `bash generate_secret_keys.sh --print-only [--existing-db] [--dotenv]` | 화면 출력만. **어디에도 저장되지 않으니** 바로 보관(다시 실행하면 다른 값) |
| 설치본 | `setup.sh` 가 자동 실행(2번) | `env.sh` 의 빈 키 3줄을 채움 |

옵션: `--existing-db`(기존 DB — SMTP AES 를 새로 만들지 않고 예전 값), `--dotenv`(`KEY=value` 형식), `--quiet`(값을 화면에 출력하지 않음 — 로그에 남기지 않을 때), `--print-only`(파일 없이 출력만), `-h`(사용법).

- **다시 만들지 않는 기준은 넘긴 파일 안의 값**이다. 키마다 확인해 비어 있는 키만 만든다(`kept (already in file)` 가 나오면 유지). 서버마다 경로를 하나 정해 항상 그 경로로 실행한다 — 다른 경로를 주면 새 키가 만들어진다. 기본 저장 위치는 없다(경로를 안 주면 사용법만 출력).
- 키마다 판정 순서: 파일에 값 있음 → 유지 / 현재 환경변수에 있음 → 그 값을 옮겨 적음(이미 주입된 서버) / SMTP AES + `--existing-db` → 예전 jar 내장값 / 그 밖 → `openssl rand -base64 24` 로 생성.
- 파일의 다른 줄은 건드리지 않는다. 파일·폴더가 없으면 만들고(600·700), 새로 만든 키가 있으면 백업하라는 경고가 나온다.
- **기존 DB(운영·dev)에 붙일 서버는 반드시 `--existing-db`**. 빼먹고 SMTP AES 를 새로 만들면 경고가 나온다 — 파일에서 그 줄을 지우고 `--existing-db` 로 다시 실행.
- `openssl` 이 필요하다(없으면 `dnf -y install openssl`).

### 1-4. 기동이 안 될 때

| 로그에 보이는 것 | 원인 · 조치 |
|---|---|
| `Could not resolve placeholder 'DRIVE_…'` | 그 필수 값이 주입되지 않음 — 설정 파일을 `source` 했는지, 키 이름 오타 확인 |
| `jwt.secret.key 가 필요합니다 — 환경변수 DRIVE_JWT_SECRET_KEY 를 주입하세요` | 키 3개가 없음 — 1-3 으로 만들고 다시 `source` |
| `tenantId must not be blank` | 감사 로그 SDK 4개가 비어 있음 — 채우거나 `GOVERNANCE_AUDIT_ENABLED=false` |
| `Access key ID cannot be blank` | 스토리지 access key 가 비어 있음 |
| `The connection attempt failed` · `password authentication failed` | DB 주소·포트 또는 계정·비밀번호 확인(`$` 가 든 비밀번호는 작은따옴표) |
| 기동은 되는데 메일 발송 실패 | SMTP AES 키가 기존 값과 다름 — 기존 DB 면 `--existing-db` 값으로(또는 관리자가 SMTP 비밀번호를 다시 입력) |
| 재기동 후 모두 로그아웃됨 | JWT 키가 바뀜 — 매번 같은 파일을 읽는지 확인 |

## 2. 설치본 체크리스트

설치본의 설정 파일은 `DriveInstaller/thinkfree/TFD/template/drive/data/bin/env.sh` 하나다(1-1 의 설정 파일 역할). 이미지 빌드 때 `/root/bin/env.sh` 로 들어가고, 기동 때 `docker-entrypoint.sh`·`drive.sh` 가 `source` 한 뒤 `java -jar` 한다. DB·감사 로그 항목은 템플릿에 이미 들어 있고, 스토리지는 `setup.sh`(S3)·`configure_ceph.sh`(Ceph) 가, 키 3개는 `setup.sh` 가 써 넣는다.

- **설치 시 직접 채울 것은 `GOVERNANCE_SDK_*` 4개뿐**(감사 로그를 안 쓰면 `GOVERNANCE_AUDIT_ENABLED="false"`).
- **키 3개는 `setup.sh` 가 자동으로 만든다**(`[[ Secret Keys ]]` 단계에서 `generate_secret_keys.sh` 실행, 값은 로그에 남기지 않음). 템플릿 `env.sh` 의 세 줄은 빈 값이다.

  | 상황 | JWT·다운로드 | SMTP AES |
  |---|---|---|
  | 새 설치(`template/database/postgres/data` 없음·비어 있음) | 새로 생성 | 새로 생성 |
  | 기존 DB 가 있는데 키가 빈 값(새 패키지로 업그레이드) | 새로 생성 | 예전 jar 내장값을 넣음 — 저장된 SMTP 비밀번호를 그대로 푼다 |
  | 이미 값이 있음(재실행·직접 넣음) | 그대로 둠 | 그대로 둠 |

  - 키를 직접 정하고 싶으면 `setup.sh` 실행 전에 `env.sh` 에 넣는다(이후 `setup.sh` 는 손대지 않는다). 키만 다시 확인·보충하려면 `bash generate_secret_keys.sh template/drive/data/bin/env.sh`(값이 있으면 유지).
  - `openssl` 이 없으면 이 단계에서 멈춘다 — `dnf -y install openssl` 후 다시 실행.
  - 새로 만든 SMTP AES 키는 그 설치처의 SMTP 비밀번호를 푸는 유일한 키다. `env.sh` 를 백업해 두고, 재설치·이전 때 같은 값을 넣는다.
- 라이선스는 `template/license/license.tfl`(+ 공개키 `thinkfree.public.key`)에 두면 첫 기동 때 등록된다(`DRIVE_INITIAL_LICENSE_FILE`, docker-compose 가 `/root/weboffice/license` 로 마운트). office 연결은 TFO 에 HTTP Storage 어댑터를 만든 뒤 `DRIVE_INITIAL_OFFICE_*` 를 채운다 — 주소·서명키(ticket 이면 토큰도)가 다 있어야 저장되고, 둘 다 비워 두면 관리자 화면에서 한다(5번).
- `GOVERNANCE_ORIGIN_INSTANCE_ID="tfd"` 는 설치처 이름으로 바꾸는 것을 권장한다(빈 값 `""` 으로 두면 기본값 대신 빈 이름으로 전송된다).
- 셸 `export` 라 `$` 가 들어간 값(비밀번호 등)은 작은따옴표로 감싼다.

## 3. 로컬 실행 (`.env`)

`DriveAPI/.env.example` 을 `DriveAPI/.env` 로 복사해 쓴다(값이 다 들어 있음, `.env` 는 git 제외).
`application.yml` 은 `.env` 를 읽지 않는다. 서버가 실수로 로컬 값을 읽지 않도록, `.env` 는 **실행할 때만** 붙인다. 방법은 둘 중 하나를 고른다(기본은 A).

### 3-1. 방법 A — 공유 실행 구성 (기본)

| 실행 | 할 일 |
|---|---|
| IntelliJ | 실행 구성 목록에서 `DriveAPI`(리포의 `.run/DriveAPI.run.xml`)를 고른다. VM 옵션·작업 디렉터리가 이미 들어 있다 |
| 다른 IDE·터미널 | 작업 디렉터리를 `DriveAPI` 로 두고 VM 옵션 `-Dspring.config.import=optional:file:.env[.properties]` 를 넣는다 |
| 프로필 없는 테스트(`AbstractMvcTest` 계열 등) | 따로 할 일 없음 — 테스트 전용 `src/test/resources/config/application.yml` 이 같은 방식으로 `.env` 를 읽는다 |
| test 프로필 테스트(Testcontainers 등) | `.env` 를 읽지 않는다(`application-test.yml` 만) |

- `.env` 는 **properties 형식**(`KEY=VALUE`)이다. 따옴표를 붙이면 따옴표까지 값이 된다 — 특수문자(`@`·`$`·`#`)가 있는 비밀번호도 따옴표 없이 그대로 쓴다.
- 줄 맨 앞의 `#` 만 주석이고, 값 중간의 `#` 은 값으로 읽힌다.
- `DRIVE_*` 처럼 이 문서의 키는 그 값을 채우고, 점 표기 키(`logging.config` 등)는 그 설정을 직접 덮는다.
- 예전 실행 구성(VM 옵션 없음)으로 띄우면 `${DRIVE_DATABASE_HOST}` 같은 필수값이 없어 기동이 실패한다(의도) — 실행 구성을 `DriveAPI` 로 바꾸면 된다.

### 3-2. 방법 B — IntelliJ Edit Configurations 의 환경변수 파일

`.env` 를 IntelliJ 가 환경변수로 넣어 띄운다(VM 옵션 없음).

1. Run → Edit Configurations → `ThinkfreeDriveApplication` 실행 구성(Spring Boot 또는 Application)을 연다.
2. Working directory 를 `DriveAPI` 모듈 폴더로 둔다(`logging.config` 등 상대 경로 설정 때문).
3. Environment variables 칸 오른쪽의 파일 아이콘으로 `DriveAPI/.env` 를 지정한다(칸이 안 보이면 Modify options → Environment variables).
4. VM options 에 `-Dspring.config.import=…` 를 넣지 않는다.
5. `.env` 에서 특수문자(`#`·`$`·공백 등)가 들어간 값을 **작은따옴표**로 감싼다 — `DRIVE_DATABASE_PASSWORD='…'`. dotenv 관례상 작은따옴표 안은 글자 그대로라 `$` 를 변수로 풀지 않는다(큰따옴표는 `$`·`\` 를 해석할 수 있어 권하지 않음).

주의:

- IntelliJ 는 이 파일을 **dotenv 형식**으로 읽는다. 따옴표가 없으면 값 중간의 `#` 뒤를 주석으로 잘라 버려 DB 비밀번호가 틀어진다(위 5단계가 필요한 이유).
- 반대로 따옴표를 넣은 `.env` 를 **properties 형식으로 읽는 곳**에서는 따옴표까지 값이 되어 DB 접속이 실패한다 — 방법 A 의 공유 실행 구성, 프로필 없는 테스트, 터미널 `mvn test`.
  - IntelliJ 에서 프로필 없는 테스트를 돌릴 때는 JUnit 실행 구성(또는 Edit Configurations → Edit configuration templates → JUnit)에도 같은 `.env` 를 환경변수 파일로 지정한다. 환경변수가 `.env` import 보다 우선이라 올바른 값으로 덮인다.
  - 터미널 `mvn test` 는 이렇게 덮을 수 없다 — 방법 B 를 쓰는 동안은 IntelliJ 에서 테스트를 돌린다.
- `.env` 를 새로 복사하거나 `.env.example` 에서 값을 옮길 때마다 따옴표를 다시 확인한다(`.env.example` 은 방법 A 기준이라 따옴표가 없다).

### 3-3. 우선순위

실행 인자 > `-D` 시스템 프로퍼티 > OS 환경변수(방법 B·서버 주입) > `.env`(방법 A) > `application.yml` 기본값.
그래서 값 몇 개만 바꿔 띄우고 싶으면, 실행 구성의 Environment variables 칸에 그 키만 적으면 된다(파일이 아니라 칸에 직접 `KEY=VALUE`).

## 4. 필수 — 없으면 기동 실패 (17개)

| 키 | 설명 |
|---|---|
| `DRIVE_DATABASE_HOST` | PostgreSQL 호스트 |
| `DRIVE_DATABASE_PORT` | PostgreSQL 포트 |
| `DRIVE_DATABASE_NAME` | DB 이름 |
| `DRIVE_DATABASE_USERNAME` | DB 계정 |
| `DRIVE_DATABASE_PASSWORD` | DB 비밀번호 (설치본 `env.sh`·셸 `export` 에서 `$` 가 있으면 작은따옴표로 감쌀 것. 로컬 `.env` 는 3번 참고) |
| `DRIVE_STORAGE_ENDPOINT` | 스토리지 주소 — **ceph·rustfs 일 때만** 필수 |
| `DRIVE_STORAGE_REGION` | 리전 — **s3 일 때만** 필수 |
| `DRIVE_STORAGE_ACCESS_KEY` | 스토리지 access key |
| `DRIVE_STORAGE_SECRET_KEY` | 스토리지 secret key |
| `DRIVE_STORAGE_BUCKET_NAME` | 버킷 이름 |
| `DRIVE_JWT_SECRET_KEY` | 로그인 토큰 서명 키(HS256). **32글자 이상** — 아래 "서명·암호화 키" |
| `DRIVE_SMTP_AES_SECRET_KEY` | SMTP 비밀번호를 DB 에 암호화해 저장하는 AES 키. **정확히 16·24·32글자**. **기존 DB 는 원래 값 유지** — 아래 "서명·암호화 키" |
| `DRIVE_DOWNLOAD_SECRET_KEY` | 다운로드 링크 토큰 서명 키(HMAC-SHA256). 32글자 이상 권장 — 아래 "서명·암호화 키" |
| `GOVERNANCE_SDK_ENDPOINT` | 감사 로그 Governance 서버 주소 (예: `http://host:8081/v1`) |
| `GOVERNANCE_SDK_TENANT_ID` | Governance 테넌트 ID |
| `GOVERNANCE_SDK_KEY_ID` | Governance 키 ID |
| `GOVERNANCE_SDK_BASE64_SECRET` | Governance HMAC 시크릿(base64) |

> - `GOVERNANCE_SDK_*` 4개는 감사 로그가 켜져 있을 때(기본) 필수. `GOVERNANCE_AUDIT_ENABLED=false` 로 끄면 넣지 않아도 된다.

### 서명·암호화 키 (만드는 법은 1-3)

| 키 | 쓰임 | 조건 | 바꾸면 |
|---|---|---|---|
| `DRIVE_JWT_SECRET_KEY` | 로그인 토큰 HS256 서명 | 32글자 이상 | 로그인 세션이 모두 끊김(다시 로그인) |
| `DRIVE_SMTP_AES_SECRET_KEY` | 관리자가 입력한 SMTP 비밀번호(구글 앱 비밀번호 등)를 DB 에 AES 로 암호화해 저장 — 구글 값이 아니라 우리가 정하는 값 | 정확히 16·24·32글자 | **DB 에 저장된 SMTP 비밀번호를 못 풀어 메일 발송 실패**(관리자가 SMTP 비밀번호를 다시 입력해야 복구) |
| `DRIVE_DOWNLOAD_SECRET_KEY` | 다운로드 링크 토큰 HMAC-SHA256 서명 | 32글자 이상 권장 | 이미 발급된 다운로드 링크가 무효 |

- 새 값은 세 키 모두 `openssl rand -base64 24`(무작위 24바이트 → 32글자) 하나로 만든다 — 세 조건을 다 만족한다. 키마다 다른 값으로.
- 서버가 스스로 서명·검증(대칭)하므로 외부와 맞출 필요 없다. 같은 DB 를 쓰는 WAS 끼리만 같은 값이면 된다.
- 기존 서버(운영·dev)는 지금까지 jar 내장값으로 돌았다 — SMTP AES 는 그 값을 계속 쓴다(`generate_secret_keys.sh --existing-db` 가 넣는 값, `LEGACY` 상수, 로컬 `.env.example` 과 같은 값).

## 5. 선택 — 안 넣으면 기본값 (60개)

| 키 | 기본값 | 설명 |
|---|---|---|
| `GOVERNANCE_AUDIT_ENABLED` | `true` | 감사 로그 Governance 전송 |
| `GOVERNANCE_ORIGIN_INSTANCE_ID` | `tfd` | 감사 로그에 찍히는 설치 인스턴스 이름(origin.instanceId). **서버마다 설치처 이름을 넣기 권장**(안 넣으면 모든 서버가 `tfd` 로 찍힘). 로컬 `.env` 는 `tfd-local` |
| `DRIVE_DATABASE_OPTIONS` | (빈 값) | JDBC URL 뒤에 붙일 옵션 (예: `?sslmode=require`) |
| `DRIVE_STORAGE_TYPE` | (빈 값 → 코드에서 `ceph`) | `ceph` / `s3` / `rustfs` |
| `DRIVE_STORAGE_ADMIN_URL` | (빈 값) | Ceph 대시보드 주소 — 스토리지 용량 조회용 |
| `DRIVE_STORAGE_ADMIN_USERNAME` | (빈 값) | Ceph 대시보드 계정 |
| `DRIVE_STORAGE_ADMIN_PASSWORD` | (빈 값) | Ceph 대시보드 비밀번호 |
| `DRIVE_FRONT_BASE_URL` | (빈 값 → 요청 host 사용) | FE 주소. FE·BE 주소가 다를 때 |
| `DRIVE_SHARE_CONTEXT_PATH` | `/s/` | 공유 링크 경로 |
| `DRIVE_CORS_ALLOWED_ORIGINS` | (빈 값 → 같은 origin 만) | 쿠키 인증 내부 API 를 부를 수 있는 다른 origin(쉼표 구분) |
| `DRIVE_EMBED_ALLOWED_ORIGINS` | `*` | Drive 를 iframe 으로 넣을 수 있는 origin(쉼표 구분). 운영은 지정 권장 |
| `DRIVE_AUTH_COOKIE_SECURE` | `false` | 로그인 쿠키 Secure. HTTPS 로 서비스하면 `true` |
| `DRIVE_AUTH_COOKIE_SAME_SITE` | `Lax` | FE·BE 가 다른 사이트면 `None`(+Secure) |
| `DRIVE_TENANT_DEV_FALLBACK_ENABLED` | `false` | 테넌트 판별 실패 시 기본 테넌트로 대체. 운영은 반드시 `false` |
| `DRIVE_SCHEDULER_BATCH_ENABLED` | `true` | 공유 DB 배치 스케줄러. k8s 에서 배치 담당이 아닌 파드는 `false` |
| `DRIVE_REDIS_ENABLED` | `false` | Redis 사용(선택 구성요소). `false` 면 Redis 에 연결하지 않고 Redis 를 쓰는 기능은 DB·로컬 방식으로 동작. `true` 여도 Redis 장애가 서비스 중단이 되지 않는다(기능마다 DB 조회·통과·TTL 반영). 상태는 지표 `drive_redis_up`, health 에는 넣지 않는다 |
| `DRIVE_REDIS_HOST` | `localhost` | Redis 주소 — `DRIVE_REDIS_ENABLED=true` 일 때만 쓴다 |
| `DRIVE_REDIS_PORT` | `6379` | Redis 포트 |
| `DRIVE_REDIS_PASSWORD` | (빈 값 → 인증 없음) | Redis 비밀번호(`requirepass`). ACL 사용자가 필요하면 `SPRING_DATA_REDIS_USERNAME` |
| `DRIVE_REDIS_DATABASE` | `0` | Redis DB 번호 |
| `DRIVE_REDIS_SSL_ENABLED` | `false` | TLS 연결(관리형 Redis 등) |
| `DRIVE_REDIS_TIMEOUT` | `500ms` | 명령 응답 대기. 짧게 둬야 Redis 가 느릴 때 요청이 붙잡히지 않고 장애 시 동작으로 넘어간다 |
| `DRIVE_REDIS_CONNECT_TIMEOUT` | `1s` | 연결 대기 |
| `DRIVE_REDIS_KEY_PREFIX` | `drive:` | 모든 키의 접두어. 여러 설치가 Redis 하나를 같이 쓰면 설치마다 다르게. Redis 는 `maxmemory-policy noeviction` 으로 둔다 — 아니면 기동 시 경고(키가 밀려나면 로그아웃 토큰이 통과할 수 있음) |
| `DRIVE_RATE_LIMIT_ENABLED` | `true` | 로그인 전 화면(로그인·MFA·재설정 링크 요청) 요청 수 제한. 넘으면 429 + `Retry-After`. `DRIVE_REDIS_ENABLED=true` 면 WAS 끼리 합산, 아니면 WAS 마다(실제 한도 = 값 × WAS 대수) |
| `DRIVE_RATE_LIMIT_LOGIN_PER_ACCOUNT` | `10` | 로그인 — 같은 아이디 분당 횟수(테넌트·화면별). 관리자·슈퍼관리자 화면은 계정 잠금이 없어 이것이 무차별 대입을 막는다 |
| `DRIVE_RATE_LIMIT_LOGIN_PER_IP` | `100` | 로그인 — 같은 IP 분당 횟수(테넌트별). 회사 NAT 뒤 사용자가 많으면 올린다. IP 는 신뢰 프록시 기준(§1-2) |
| `DRIVE_RATE_LIMIT_ANONYMOUS_LOGIN_PER_IP` | `30` | 공유 링크 익명 로그인 — 같은 IP 분당 횟수(링크 토큰 추측 방지) |
| `DRIVE_RATE_LIMIT_MFA_PER_USER` | `5` | MFA 코드 확인(로그인 2단계·첫 설정) — 같은 사용자 분당 횟수. 틀린 MFA 코드는 계정 잠금에 세지 않아 이것이 6자리 코드 추측을 막는다 |
| `DRIVE_RATE_LIMIT_PASSWORD_RESET_LINK_PER_ACCOUNT` | `3` | 비밀번호 재설정 링크 메일 요청 — 같은 계정 **15분당** 횟수(남의 메일함 폭주 방지) |
| `DRIVE_RATE_LIMIT_PASSWORD_RESET_LINK_PER_IP` | `20` | 비밀번호 재설정 링크 메일 요청 — 같은 IP 분당 횟수(테넌트별) |
| `DRIVE_REST_CLIENT_TRACE_ENABLED` | `false` | 외부 호출 요청·응답 본문까지 로그(본문 버퍼링). 운영은 `false`, dev·로컬은 `true` |
| `DRIVE_SUPER_ADMIN_ALLOWED_IPS` | (빈 값 → 제한 없음) | 슈퍼어드민 접근 허용 IP·CIDR(쉼표 구분) |
| `DRIVE_SUPER_ADMIN_EXTERNAL_API_KEY` | (빈 값) | 슈퍼어드민 외부 API 고정 키 |
| `DRIVE_TIME_ZONE` | `Asia/Seoul` | 날짜 해석·표시 시간대 |
| `DRIVE_WEB_LOG_ENABLED` | `true` | 톰캣 액세스 로그 |
| `DRIVE_WEB_LOG_DIRECTORY` | `/log/drive/web` | 액세스 로그 폴더 |
| `DRIVE_WEB_LOG_PATTERN` | `[%{yyyy-MM-dd HH:mm:ss}t] \| [%I] \| %h \| "%r" \| %s \| %b \| ...` | 액세스 로그 형식 |
| `DRIVE_WEB_LOG_MAX_DAYS` | `14` | 액세스 로그 보관 일수 |
| `DRIVE_FILE_CONTENT_READ_MAX_SIZE` | `10MB` | MCP 파일 읽기 상한 |
| `DRIVE_FILE_CONTENT_READ_MAX_CHARS` | `50000` | MCP 파일 읽기 글자 상한 |
| `DRIVE_FILE_CONTENT_DOWNLOAD_MAX_SIZE` | `10MB` | MCP 다운로드 상한 |
| `DRIVE_FILE_CONTENT_WRITE_MAX_SIZE` | `512KB` | MCP 업로드 상한 |
| `DRIVE_FILE_CONTENT_CHARSET_CONFIDENCE_THRESHOLD` | `80` | 평문 인코딩 감지 신뢰도 문턱 |
| `DRIVE_SSO_CALLBACK_PATH` | `/sso/callback` | SSO 콜백 경로 |
| `DRIVE_BRANDING_TAB_TITLE` | `Drive` | 브라우저 탭 제목 기본값 |
| `DRIVE_BRANDING_SERVICE_NAME` | `Drive` | 서비스 이름 기본값 |
| `DRIVE_MFA_TOTP_ISSUER` | `Drive` | OTP 앱에 표시되는 발급자 |
| `TFO_TICKET_OPEN_TTL` | `60s` | 오피스 문서 열기 티켓 유효 시간 |
| `TFO_STORAGE_SESSION_TTL` | `24h` | 오피스 저장 세션 유효 시간 |
| `DRIVE_FLYWAY_ENABLED` | `true` | 기동 시 Flyway 로 테이블·기초 데이터 마이그레이션(빈 DB 면 V1 실행, 이력 있는 DB 면 남은 V 만 실행). DB·계정은 미리 만들어 둬야 한다 |
| `DRIVE_FLYWAY_BASELINE_ON_MIGRATE` | `false` | 테이블은 있는데 Flyway 이력이 없는 DB(예전 `initialize.sql` 로 만든 DB)를 만났을 때: `false` = 기동 실패, `true` = V1 을 실행하지 않고 "V1 적용됨"(baseline)만 기록한 뒤 V2 부터 적용. 스키마가 V1 과 같은지는 검사하지 않는다. 로컬 `.env.example` 은 `true`(공용 dev DB), 설치기·서버는 `false`(예전 설치 업그레이드 첫 기동만 `true`) |
| `DRIVE_INITIAL_LICENSE_FILE` | (빈 값 → 건너뜀) | 첫 기동 때 등록할 라이선스 파일 경로(설치기 `/root/weboffice/license/license.tfl`). 대상 테넌트(활성 자체관리 테넌트가 하나뿐일 때 그 테넌트, 첫 설치면 1 — 0개·여러 개면 라이선스·office 둘 다 건너뜀)에 라이선스가 없을 때만 업로드와 같은 검증으로 등록. 파일이 없거나 실패하면 건너뛰고 관리자 화면에서 업로드 |
| `DRIVE_LICENSE_ENFORCEMENT_ENABLED` | `true` | 라이선스 검사. **운영은 반드시 `true`.** `false` 면 `GET /api/v1/config/license-status` 가 등록 여부와 상관없이 사용 가능(`state:true`)을 돌려줘 사용자 로그인 차단(프론트)이 풀린다 — 라이선스 파일 없는 로컬 개발 전용, 기동 시 WARN. 관리자 미등록 배너·좌석 검사·화이트라벨은 그대로 |
| `DRIVE_INITIAL_OFFICE_DOMAIN` | (빈 값 → 건너뜀) | 첫 기동 때 저장할 office(TFO) 주소(http/https). **이 값이 있어야 office 초기 설정을 시도한다.** 대상 테넌트에 office 설정이 없을 때만(주소가 비어 있고, 연결 방식을 저장한 적 없고, 서비스 관리 테넌트가 아닐 때) 관리자 화면 저장과 같은 검증을 통과하면 아래 값과 함께 통째로 저장. 하나라도 검증에 걸리면 아무것도 저장하지 않고 경고 로그 — 관리자 화면에서 설정 |
| `DRIVE_INITIAL_OFFICE_ADAPTER_NAME` | (빈 값 → 테넌트 번호) | TFO HTTP Storage 어댑터 이름(영숫자·`_`·`-`, 128자 이내). 비우면 관리자 화면과 같이 테넌트 번호(예: `1`)로 저장 — TFO 에 만든 이름과 다르면 화면에서 고친다 |
| `DRIVE_INITIAL_OFFICE_AUTH_MODE` | `ticket` | office 인증 방식 `ticket` \| `legacy`(관리자 화면 기본값과 같음). 빈 값 `""` 으로 선언하면 기본값이 적용되지 않아 검증에 걸린다 |
| `DRIVE_INITIAL_OFFICE_CONNECTION_TYPE` | `onPremise` | office 연결 방식 `onPremise` \| `none` — 값은 그대로 넘기고 허용값 검증·저장 규칙은 관리자 화면 저장과 같다. `none` 이면 연결 방식만 저장되고(주소·토큰·서명키는 저장 안 됨) 이후 기동에서는 이미 설정된 것으로 보고 건너뛴다. 빈 값 `""` 으로 선언하면 검증에 걸린다 |
| `DRIVE_INITIAL_OFFICE_TICKET_SERVICE_TOKEN` | (빈 값) | 티켓 발급용 서비스 토큰 — 인증 방식이 `ticket` 이면 필수 |
| `DRIVE_INITIAL_OFFICE_HTTP_STORAGE_REQUEST_JWT_SECRET` | (빈 값) | HTTP Storage 요청 서명 키 — 필수, UTF-8 32바이트 이상 |

> 로그 파일 경로는 `log/log4j2-server.yml` 에서 읽는다: `LOG_PATH`(기본 `/log/drive/backend/drive-server`), `ERROR_LOG_PATH`(기본 `/log/drive/backend/error/drive-error`).
