# 로컬 확인 가이드 — 업로드·다운로드·모니터링·k6 (2026-11-23)

새 세션에서 화면과 Grafana 를 보면서 직접 확인하기 위한 순서표. 확인을 끝내고 결과를 `docs/todo/next-session.md` 에 옮기면 지운다.
포트·컨테이너 이름은 작성한 PC(commerce-saga 가 함께 떠 있는 PC) 기준이다. 구조·설정 설명은 같은 폴더의 `NOTES.md`.
새 세션은 이 파일과 `docs/todo/next-session.md` §2-3·§3 을 같이 읽는다. 설정의 근거·자세한 설명은 끝의 "참고 문서".

## 0. 먼저 알 것

- **두 가지 모드는 동시에 못 띄운다** — 둘 다 관리 포트 8091 을 쓴다.

  | 모드 | 언제 | 백엔드 | DB·스토리지 |
  |---|---|---|---|
  | A. 화면 확인 | 웹 화면에서 업로드·다운로드를 손으로 해 볼 때 | IntelliJ **DriveAPI**(8080) + `DriveFront` `npm run dev`(3000) | `DriveAPI/.env` 가 가리키는 곳 |
  | B. 부하 측정 | k6 로 동시 다운로드·업로드 부하를 줄 때 | IntelliJ **DriveAPI (loadtest)**(8082) | 이 PC 의 `loadtest-db`(15440)·`loadtest-storage` RustFS(19010) |

- 2026-11-23 기준 이 PC 에 이미 떠 있는 컨테이너: 관측 스택(grafana·prometheus·loki·tempo·alloy), `loadtest-db`, `loadtest-storage`.
- 모드 A 용 `DriveAPI/.env` 를 2026-11-23 에 만들어 두었다(git 제외). `.env.example` 과 다른 점:
  - DB 포트 **15433** — 이 PC 는 5432 를 commerce-saga, 15432 를 drive-flyway-local 이 쓴다. 로컬 인프라는 포트 덮어쓰기 파일과 같이 띄운다:
    ```powershell
    cd C:\intelliJProject\klmin-drive\DriveAPI
    docker compose -f docker-compose.local.yml -f docker-compose.local.port.yml up -d
    ```
    (`docker-compose.local.port.yml` 은 로컬 전용, `.git/info/exclude` 에 넣어 git 에 안 보인다. RustFS 는 9000, 콘솔 9001)
  - `DRIVE_LICENSE_ENFORCEMENT_ENABLED=false`(라이선스 파일 없이 로그인), `GOVERNANCE_AUDIT_ENABLED=false`(로컬 Governance 없음), `app.front.port=3000`(공유 링크가 프론트 개발 서버로)
  - 관측 연결: `DRIVE_OTLP_TRACING_EXPORT_ENABLED=true`, `logging.config=classpath:log/log4j2-server.yml`(콘솔 + `C:/log/drive/backend/drive-server.log` → Loki)
  - 처음 기동하면 빈 DB 에 Flyway 가 V1 부터 만든다(기본 테넌트·superadmin·admin 시드, 비밀번호는 해시라 모른다).
    앱을 한 번 띄운 뒤 admin 비밀번호를 부하 테스트와 같은 테스트 값(`LoadTest!2026`)으로 맞춘다 — 로컬 DB 전용:
    ```powershell
    Get-Content C:\intelliJProject\klmin-drive\observability\k6\seed-admin.sql | docker exec -i drive-local-postgres-1 psql -U drive -d drive
    ```
    그 뒤 http://localhost:3000 에서 `admin` / `LoadTest!2026`.
- **`observability/.env` 의 `DRIVE_PG_DSN` 은 2026-11-23 부터 로컬 도커 PG(`host.docker.internal:15433/drive`)를 본다**(전 값인 공용 dev DB 는 그 파일에 주석으로 남김).
  Grafana 의 업로드 세션·정리 대기열 지표(`job="postgres"`)와 그 알림이 모드 A 에서 한 업로드를 보여 준다. 앱이 처음 기동해 Flyway 가 테이블을 만들기 전에는 이 지표가 없다.
  모드 B 의 DB 는 `job="postgres-loadtest"` 로 따로 모인다. 로컬 DB 라 §4 의 "알림 실제로 울리기" 도 여기서 해도 된다.
  - 모드 A 는 `DRIVE_SCHEDULER_BATCH_ENABLED=false` 라 삭제 대기열이 처리되지 않는다 → 파일을 지우면 15분 뒤 "스토리지 정리 밀림" 알림이 뜨는 게 정상. 정리까지 보려면 `.env` 에서 true 로.
- **3000 포트 충돌**: commerce-saga 의 Grafana 가 떠 있으면 `localhost:3000` 이 그쪽으로 간다(`127.0.0.1:3000` 만 Drive). 그때는 `DriveAPI/.env` 의 `DRIVE_FRONT_BASE_URL`·`app.front.port`·`DRIVE_CORS_ALLOWED_ORIGINS` 를 3001 로 바꾸고 프론트를 `npx next dev -H 0.0.0.0 -p 3001 --turbopack` 로 띄운 뒤 http://localhost:3001 로 들어간다.
- Git Bash 에서 `docker compose ... /scripts/...` 를 쓰면 경로가 바뀐다 → 앞에 `MSYS_NO_PATHCONV=1`. PowerShell 은 그대로.

## 1. 관측 스택 확인

```powershell
cd C:\intelliJProject\klmin-drive\observability
docker compose ps
docker compose up -d
docker compose restart grafana alloy   # 저장소를 pull 해서 규칙·Alloy 설정이 바뀌었을 때(기동 때만 읽음)
```

| 주소 | 볼 것 |
|---|---|
| http://localhost:3300 (`admin` / `admin` — `observability/.env` 에 `GRAFANA_ADMIN_PASSWORD` 를 넣으면 그 값) | 대시보드 Drive › **Drive 개요**, Drive › **k6 부하 테스트**, Alerting › Alert rules |
| http://localhost:9090/targets | `drive-api`(앱 8091) UP 인지 |
| http://localhost:12345 | Alloy 파이프라인 |

- 앱을 아직 안 띄웠으면 "Drive 메트릭 수집 끊김" 알림이 뜨는 게 정상이다.
- 알림 규칙 14개가 Alerting 화면에 보이고 평가 오류(Error)가 없어야 한다. 받는 곳(contact point)은 아직 없어 알림이 어디로도 가지 않는다.

## 2. 모드 A — 화면에서 업로드·다운로드

띄우기: IntelliJ **DriveAPI** 실행 → `cd DriveFront; npm run dev` → http://localhost:3000.
Grafana 는 **Drive 개요** 를 열어 두고 시간 범위를 "Last 15 minutes", 자동 새로고침 10s.

### 2-1. 업로드

| # | 해 볼 것 | 기대 | Grafana / 확인 |
|---|---|---|---|
| U1 | 32MB 미만 파일 업로드 | 한 요청(`POST /api/v1/files`)으로 끝 | HTTP 요청 패널에 `/api/v1/files` |
| U2 | 32MB 이상(예: 200MB) 업로드 | 청크 API(`/api/v1/uploads`) 로 16MB 파트 4개씩 → complete | 업로드 세션 시작·종료 카운터, 진행 중 세션 수가 올랐다 0 으로 |
| U3 | 큰 파일(1GB) 올리다 **새로고침** → 같은 파일 다시 선택 | 새 세션 없이 빠진 파트만 보내고 끝(이어 올리기) | 브라우저 개발자도구 Network 에 `GET /uploads/{id}` 뒤 일부 파트만 PUT |
| U4 | 큰 파일 올리다 **취소** | 세션 취소, 진행 중 용량 반환 | 진행 중 세션 0, 사용자 용량 원래대로 |
| U5 | 큰 파일 올리는 중 **대상 폴더를 휴지통으로** | 다음 파트에서 409 `UPLOAD_001`, 세션 FAILED | 세션 종료 사유 `target_deleted` |
| U6 | 실행 파일(.exe 등, 내용이 실행 파일)을 확장자만 바꿔 업로드 | 415 `UPLOAD_010` 거절 | 오류 응답 |
| U7 | 용량보다 큰 파일 | 시작 단계에서 용량 초과 안내 | 사용량 막대는 확정값만, 진행 중은 `uploadingByte` |
| U8 | 잠긴 파일에 새 버전 업로드 | 423 잠금 안내(프론트 분기) | — |

- 끝난 뒤 진행 중 세션·미완성 멀티파트가 남지 않아야 한다.
- 업로드 요청의 트레이스: Grafana › Explore › Tempo 에서 `{ span.http.url =~ ".*uploads.*" }`. 정상 요청은 10%만 남고 30초쯤 뒤에 보인다.

### 2-2. 다운로드

| # | 해 볼 것 | 기대 | Grafana / 확인 |
|---|---|---|---|
| D1 | 작은 파일 다운로드 | 바로 받아짐 | `/download` 처리 시간 |
| D2 | 큰 파일(1GB 이상) 다운로드 | **끝까지** 받아짐(#64 이전에는 중간에 멈춤) | "스트림 사용 중"(`executor_active_threads{name="driveStreamExecutor"}`)이 받는 동안 1 |
| D3 | 큰 파일 받다가 브라우저에서 취소 | 서버 스트림이 정리됨 | 스트림 사용 중이 0 으로 돌아옴, ERROR 로그 폭주 없음 |
| D4 | 폴더 zip 다운로드 | zip 이 열림 | — |
| D5 | 미리보기(view) | 열림 | `/view` |

## 3. 모드 B — k6 부하 측정

모드 A 의 DriveAPI(8080)를 **끄고** 시작한다.

### 3-1. 준비(처음 한 번, 이미 했으면 건너뜀)

```powershell
cd C:\intelliJProject\klmin-drive\observability
docker compose --profile loadtest up -d loadtest-db loadtest-storage
```

1. IntelliJ **DriveAPI (loadtest)** 실행 — 서비스 8082, 관리 8091. 빈 DB 면 Flyway 가 스키마를 만든다. 배치 스케줄러·감사 로그 전송은 꺼져 있다.
2. admin 비밀번호 맞추기
   ```powershell
   Get-Content k6\seed-admin.sql | docker compose exec -T loadtest-db psql -U drive_load -d drive_load
   ```
3. 데이터 만들기(1분 남짓) — 사용자 18명, 파일 3,780개
   ```powershell
   docker compose --profile loadtest run --rm k6 run /scripts/seed.js
   ```

Grafana 는 **k6 부하 테스트** 대시보드. 위는 k6 결과, 아래는 같은 시간대 서버(WAS p95·Hikari·DB·CPU·업로드·다운로드 행).

### 3-2. 먼저 smoke 로 동작만 확인

```powershell
docker compose --profile loadtest run --rm k6 run -e PROFILE=smoke /scripts/read.js
docker compose --profile loadtest run --rm k6 run -e PROFILE=smoke /scripts/upload.js
docker compose --profile loadtest run --rm k6 run -e PROFILE=smoke /scripts/download.js
```
세 개 모두 k6 기준(thresholds) 통과로 끝나야 한다.

### 3-3. 남은 측정 ①: 동시 다운로드 상한(#64·#77 확인) — `next-session.md` §3-3

단계마다 따로 돌린다(단계당 90초 + 준비, 128MB 파일).

```powershell
foreach ($v in 50,100,150,200) { docker compose --profile loadtest run --rm -e VUS=$v -e TESTID=dlc-after-vu$v k6 run /scripts/download-concurrency.js; Start-Sleep 15 }
```

| 보는 것 | 기대 |
|---|---|
| k6 요약 `dlc_failed` | 모든 단계에서 0 |
| k6 요약 `dlc_rejected_503` | 50·100 단계는 0, 150·200 단계는 초과분만큼 |
| "스트림 사용 중" / "스트림 최대" | 최대 100 에서 멈춤 |
| "스트림 거절/초"(`drive_download_stream_rejected_total`) | 100 초과 단계에서만 오름 |
| S3 풀 대기(`drive_s3_http_pending`)·대기 초과(`_acquire_timeout_total`) | 0 유지 |
| Tomcat 바쁜 스레드 | 낮게 유지(본문은 스트림 풀이 보냄) |

수정 전 값은 `observability/k6/results/2026-11-22-s3-pool/` 의 `k6-before-vu*.out`·`sample-before-*.log`. 결과는 k6 README "S3 풀" 절의 표에 "수정 후"로 채운다.
(그 폴더의 `run-dlc.sh` 는 다른 PC 의 `C:/loadtest-tools` k6.exe 용이다 — 여기서는 위의 docker 명령을 쓴다.)

### 3-4. 남은 측정 ②: 업로드 정상·비정상 클라이언트

```powershell
docker compose --profile loadtest run --rm -e TESTID=after-upload k6 run -e PROFILE=stress -e THINK=0 /scripts/upload.js
docker compose --profile loadtest run --rm -e TESTID=after-abuse k6 run /scripts/abuse-parts.js
```

| 보는 것 | 기대 |
|---|---|
| upload.js | HTTP 실패 1% 미만, complete p95 5초 미만 |
| abuse-parts.js 동안 Tomcat 바쁜 스레드 | **지금은 200 가까이 찰 것**(업로드 파트 동시 상한이 아직 없음 — 지난 측정 198/200) |
| abuse-parts.js 의 피해 사용자 목록 조회 p95 | 얼마나 느려지는지 기록 |

- 업로드 파트 동시 상한은 아직 만들지 않았다. 판단 메모: 정상 클라이언트(업로드당 파트 4개)는 동시 업로더 약 50명 전까지 문제없다.
  **SaaS·대규모·외부 공개 설치처를 열기 전에** WAS 단위 세마포어(50 안팎, 본문 읽기 전 503 + `Retry-After`)로 막는다.
- 부하 모드는 배치 스케줄러가 꺼져 있어 지운 객체·취소한 멀티파트가 `tb_storage_deletion` 에 쌓인다(정상). 스토리지 볼륨이 커지면 다시 처음부터.

### 3-5. (선택) 장애 주입 — DB 를 45초 멈췄을 때 트레이스·대시보드

k6 README "장애 주입" 절 그대로. 5xx 트레이스가 실제 5xx 수만큼 남는지 본다.

## 4. 모니터링 — 아직 실제로 본 적 없는 것

| 항목 | 할 수 있는지 |
|---|---|
| 알림 규칙 14개 로드·평가 | 됨(2026-11-22 확인) — §1 에서 다시 눈으로 확인 |
| 업로드 알림이 **실제로 울리는지**(`drive-upload-completing-stale` 등) | 오래된 COMPLETING 세션이 필요하다. 알림이 보는 DB 가 이제 로컬 도커 PG 라 거기서 해도 된다 — `tb_upload_session`(V6) 의 `status='COMPLETING'`, `completing_at` 을 45분 이전으로 둔 행을 만들고(스키마를 먼저 확인), 10분 뒤 알림 확인, 끝나면 지운다 |
| "Drive 메트릭 수집 끊김" | 앱을 끄면 2분 뒤 발생 → 켜면 해제. 가장 쉬운 실제 발생 확인 |
| 알림 받는 곳(Slack·메일) | 아직 없음 — 팀 채널이 정해지면 Grafana › Alerting › Contact points |
| 운영 버킷 lifecycle 병합(#69) | 실제 AWS·Ceph 버킷이 필요해 로컬에서는 못 함 |

## 5. 끝나고

- 모드 B 를 끝내면 **DriveAPI (loadtest)** 를 끈다. 평소 DriveAPI(8080)를 다시 쓸 때 `observability/.env` 의 `DRIVE_METRICS_TARGETS=host.docker.internal:8091` 은 그대로 둬도 된다(두 모드 모두 관리 포트 8091).
- 측정 결과는 `observability/k6/README.md` 의 해당 절에, 확인 결과는 `docs/todo/next-session.md` §2-3 에 적는다(worktree → PR → 리뷰 → merge).
- 저장소가 private 이고 이번 달 Actions 무료 시간을 다 써서 PR 의 CodeQL·테스트·push 후 Codex 리뷰 요청이 돌지 않는다. PR 을 처음 열 때의 Codex 리뷰는 돈다.

## 참고 문서

- `docs/todo/next-session.md` — 진행 상태·남은 일(§2-3 미확인 항목, §3 다음 할 일)
- `docs/design/upload-session-as-built.md` — 업로드가 실제로 어떻게 동작하는지(흐름·오류 코드·정리)
- `observability/README.md` — 관측 스택, 알림 규칙 표, 트레이스 샘플링
- `observability/HOW-IT-WORKS.md` — 지표·트레이스·로그가 흐르는 길
- `observability/k6/README.md` — k6 스크립트·옵션·대시보드 읽는 법·S3 풀 측정 기록
