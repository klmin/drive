# k6 부하 테스트 (읽기 위주)

k6 가 HTTP 로 Drive(WAS)를 두드리고, 관측성 스택으로 WAS·DB·호스트를 같이 본다.
DB 부하는 WAS 를 거쳐 걸린다(사용자 경로 그대로) — DB 만 따로 두드리는 건 pgbench 몫.

공용 dev DB·스토리지에 부하를 주지 않도록 **이 PC 의 테스트 전용 PG·RustFS** 를 쓴다(compose profile `loadtest`).

```
k6 컨테이너 ──HTTP──▶ Drive (IntelliJ "DriveAPI (loadtest)", :8082) ──▶ loadtest-db(PG 18.6) · loadtest-storage(RustFS)
    │                      └─ 메트릭(:8091)·트레이스·로그 ─▶ Alloy ─▶ Prometheus·Tempo·Loki
    └─ k6 결과(remote write) ─▶ Prometheus ─▶ Grafana "Drive › k6 부하 테스트"
```

## 파일

**흐름(`flows/`)** 과 **실행 파일**을 나눈다. 흐름은 "사용자가 무엇을 하나"(함수), 실행 파일은 "몇 명이 얼마 동안, 기준은 무엇"(`options`)만 정한다.
같은 흐름을 여러 실행 파일이 재사용하고, 나중에 `mixed.js` 처럼 `scenarios` 의 `exec` 로 섞을 수 있다.

| 파일 | 내용 |
|---|---|
| `lib/drive.js` | 로그인(쿠키 + `X-Drive-Role` 헤더)·VU 별 사용자 로그인·요청+체크(`request`)·응답 꺼내기·multipart |
| `flows/browse.js` | 흐름: 둘러보기 — 내 드라이브 목록 → 폴더 열기 → 파일 상세 → 검색 → 최근·즐겨찾기·트리·용량 |
| `flows/upload.js` | 흐름: 올리기 — 작은 파일(기존 업로드 API) / 큰 파일(청크 업로드 API, 일부는 중간 취소), 올린 뒤 영구 삭제 |
| `seed.js` | 데이터 만들기 — 사용자 18명 × (루트 파일 10 + 폴더 10 × 파일 20) = 파일 3,780·폴더 180, 10개마다 즐겨찾기 |
| `read.js` | 실행: 둘러보기 부하 |
| `upload.js` | 실행: 업로드 부하 — 작은 파일·큰 파일 시나리오를 동시에 |
| `flows/write.js` | 흐름: 정리하기 — 폴더 만들기 → 파일 복사 → 이름 바꾸기 → 다른 폴더로 옮기기 → 휴지통 → 복원 → 작업 폴더 영구 삭제 |
| `write.js` | 실행: 쓰기 부하 — `HOT_USER=1` 이면 모든 VU 가 같은 사용자로(잠금 경합) |
| `flows/download.js` | 흐름: 내려받기 — 다운로드 토큰 → 링크로 GET. 작은 파일 / 큰 파일(VU 마다 한 번 올려 두고 재사용) / 폴더 zip |
| `download.js` | 실행: 다운로드 부하 — 작은 파일·큰 파일·zip 시나리오를 동시에 |
| `download-concurrency.js` | 실행: 동시 다운로드 수 고정(VU = 전송 중인 다운로드) — S3 커넥션 풀·다운로드 스트림 상한 확인, 503 을 따로 센다 |
| `abuse-parts.js` | 실행: 비정상 업로드 클라이언트 — 프론트 안내값(파트 동시 4)을 무시하고 한 세션의 파트를 한꺼번에 PUT → 취소. 그동안 다른 사용자의 목록 조회 지연(피해)을 잰다 |
| `seed-admin.sql` | 부하 테스트 DB 의 admin 비밀번호를 테스트 값(`LoadTest!2026`)으로 |

## 처음 한 번

```bash
cd observability
docker compose up -d                                   # 관측성 스택
docker compose --profile loadtest up -d loadtest-db loadtest-storage
```

1. IntelliJ 에서 실행 구성 **DriveAPI (loadtest)** 실행 — 서비스 8082, 관리 8091.
   빈 DB 라 Flyway 가 스키마를 만든다. 감사 로그 전송(Governance)·배치 스케줄러·p6spy 는 꺼져 있고, 로그는 `C:/log/drive/backend` 파일로도 남는다.
   **평소 DriveAPI(8080)는 끈다** — 관리 포트 8091 이 겹치고, 같은 PC 에서 CPU 를 나눠 쓴다.
2. admin 비밀번호 맞추기
   ```bash
   docker compose exec -T loadtest-db psql -U drive_load -d drive_load < k6/seed-admin.sql
   ```
3. 데이터 만들기 (1분 남짓)
   ```bash
   docker compose --profile loadtest run --rm k6 run /scripts/seed.js
   ```

## 부하 주기

```bash
docker compose --profile loadtest run --rm k6 run /scripts/read.js                     # 기본: 10명→30명, 약 4분
docker compose --profile loadtest run --rm k6 run -e PROFILE=smoke /scripts/read.js    # 확인용: 1명 35초
docker compose --profile loadtest run --rm k6 run -e PROFILE=stress /scripts/read.js   # 한계 찾기: 150명까지
docker compose --profile loadtest run --rm k6 run -e THINK=0 /scripts/read.js          # 쉬지 않고 두드리기
```

- **Git Bash** 에서는 `/scripts/...` 를 Windows 경로로 바꿔 버린다 — 앞에 `MSYS_NO_PATHCONV=1` 을 붙인다(PowerShell 은 그대로).
- `-e TESTID=이름` 을 주면 Grafana 에서 실행을 골라 볼 수 있다(기본 `read-<PROFILE>`).
- 기준(thresholds): HTTP 실패 1% 미만, 목록 p95 1초 미만, 체크 99% 이상 — 넘으면 k6 가 실패로 끝난다.

### 업로드

```bash
docker compose --profile loadtest run --rm k6 run /scripts/upload.js                    # 기본: 작은 파일 4→8명 + 큰 파일 2→4명, 약 3분 30초
docker compose --profile loadtest run --rm k6 run -e PROFILE=smoke /scripts/upload.js   # 확인용: 각 1명 30초
docker compose --profile loadtest run --rm k6 run -e PROFILE=stress /scripts/upload.js  # 큰 파일 동시 12명까지
docker compose --profile loadtest run --rm k6 run -e CHUNK_MB=100 -e SMALL_KB=2048 -e CANCEL_RATE=0.2 /scripts/upload.js
```

| 환경변수 | 기본 | 뜻 |
|---|---|---|
| `SMALL_KB` | 512 | 작은 파일 크기 — `POST /api/v1/files` 한 요청 |
| `CHUNK_MB` | 40 | 큰 파일 크기 — 32MB(단일 요청 기준) 이상이어야 청크 API 가 받는다. 파트는 16MB, 동시 4개 |
| `CANCEL_RATE` | 0.1 | 큰 파일 중 절반쯤 보내다 취소하는 비율 — 멀티파트 abort 정리 경로 |
| `THINK` | 1 | 반복 사이 쉬는 시간(초) |

- 올린 파일은 바로 `DELETE /api/v1/files/{id}`(영구 삭제)로 지운다 — 사용자 용량(100MB)이 차지 않게. 다만 complete 전까지는 진행 중 용량으로 잡히므로
  **사용자 하나를 쓰는 VU 수 × `CHUNK_MB` 가 100MB 를 넘으면 세션 생성이 용량 초과로 실패한다.** VU 는 사용자 18명을 번호 순서대로 나눠 쓴다(두 시나리오 합산, 기본·stress 는 겹치지 않음).
- 부하 테스트 실행 구성은 배치 스케줄러가 꺼져 있어(`DRIVE_SCHEDULER_BATCH_ENABLED=false`) 지운 객체·취소한 멀티파트가 스토리지에서 실제로 정리되지 않고
  `tb_storage_deletion` 에 쌓인다(대시보드 "정리 대기열"). 스토리지 볼륨이 커지면 "다시 처음부터".
- 기준: HTTP 실패 1% 미만, 체크 99% 이상, 작은 파일 p95 2초, complete p95 5초.

### 쓰기

```bash
docker compose --profile loadtest run --rm k6 run /scripts/write.js                          # 기본: 5명→15명, 약 4분
docker compose --profile loadtest run --rm k6 run -e PROFILE=smoke /scripts/write.js         # 확인용: 1명 35초
docker compose --profile loadtest run --rm k6 run -e HOT_USER=1 -e THINK=0 /scripts/write.js # 모든 VU 가 사용자 1 로 — 잠금 경합
```

- 반복마다 만든 폴더·복사본은 반복 끝에 영구 삭제한다 — 오래 돌려도 seed 데이터·용량이 그대로(스토리지 객체는 정리 대기열에 쌓인다).
- 복사는 사용자 행을 잠그고(용량) 스토리지 서버측 복사를 한다. `HOT_USER=1` 이면 그 행 하나를 VU 전부가 기다린다 — "DB 잠금·느린 쿼리" 행에서 잠금 대기와 `tb_user … for no key update` 가 위로 올라온다.
- 상위 쿼리 패널은 누적값이라, 실행 전에 비우면 그 실행만 보인다:
  ```bash
  docker compose exec -T loadtest-db psql -U drive_load -d drive_load -c "SELECT pg_stat_statements_reset()"
  ```
- 기준: HTTP 실패 1% 미만, 체크 99% 이상, 이동 p95 1초, 복사 p95 2초.

### 다운로드

```bash
docker compose --profile loadtest run --rm k6 run /scripts/download.js                   # 기본: 작은 파일 4→8명 + 큰 파일 2→4명 + zip 1→2명, 약 3분 30초
docker compose --profile loadtest run --rm k6 run -e PROFILE=smoke /scripts/download.js  # 확인용: 각 1명 35초
docker compose --profile loadtest run --rm k6 run -e LARGE_MB=80 /scripts/download.js    # 큰 파일 크기(32MB 이상)
```

- 프론트(`DriveFront/hooks/useDriveDownload.js`)와 같은 순서 — 다운로드 토큰 발급 → 받은 `linkUrl` 로 GET. `linkUrl` 은 API 기준 상대경로(`/files/download?dt=…`)라 `/api/v1` 뒤에 붙인다.
- 작은 파일은 seed 파일, zip 은 seed 폴더(파일 20개). 큰 파일은 VU 마다 처음 한 번 `k6-download-large-<MB>mb.txt` 를 청크 업로드로 올려 두고 재사용한다(크기를 바꾸면 이전 것은 지운다 — 사용자 용량 100MB).
- 본문은 k6 메모리에 담지 않고(`responseType: none`) `Content-Length` 로 크기만 확인한다. 받은 양은 `data_received`.
- 리다이렉트는 따라가지 않는다 — 잘못된 경로·만료 토큰은 302 → 에러 페이지(200)라 따라가면 실패가 성공으로 보인다.
- `DOWNLOAD_TIMEOUT`(기본 120s) — 스트림이 중간에 멈추면 상태는 이미 200 이라 이 시간이 지나야 실패로 잡힌다. 너무 길면 멈춘 VU 가 끝까지 묶여 아무 결과도 남기지 않는다.
- 기준: HTTP 실패 1% 미만, 체크 99% 이상, 작은 파일 다운로드 p95 1초, 토큰 발급 p95 0.5초.

## 보는 곳

Grafana http://localhost:3300 › Drive › **k6 부하 테스트**

- 위: k6 가 본 결과 — VU 수, API 별 초당 요청·p95, 실패율, 상태 코드
- 아래: 같은 시간대 서버 — WAS p95(점 → 트레이스), Hikari 커넥션 사용·대기, 부하 테스트 DB 세션·트랜잭션(`job="postgres-loadtest"`), CPU, 힙·GC, WARN·ERROR 로그
- 읽는 법: VU 를 올려도 초당 요청이 더 안 오르고 p95 만 오르면 한계. 그때 Hikari 대기가 0 보다 크면 DB 풀·쿼리, CPU 가 차면 WAS, DB 세션 active 가 많으면 DB 쪽.
  느린 요청은 WAS p95 그래프의 점을 눌러 트레이스에서 어느 SQL 인지 본다.
- 업로드 행: 파일 하나 올리는 시간(k6) · 세션 시작·종료(앱) · 진행 중 세션(DB) · S3 호출 p95·오류(앱) · 정리 대기열(DB).
  업로드가 느려질 때 S3 p95 가 같이 오르면 스토리지, 그대로면 WAS·DB(complete 의 커밋 tx). 시작보다 completed 가 적고 진행 중 PENDING 이 계속 늘면 클라이언트가 끝내지 못하는 세션이 쌓이는 것.
- 다운로드 행: 다운로드 시간·받은 양(k6) · `/download` 경로별 서버 처리 시간(본문을 다 보낼 때까지) · 스레드.
  다운로드 본문은 Tomcat 요청 스레드가 아니라 다운로드 스트림 풀(`DriveStream-N`, `executor_*{name="driveStreamExecutor"}`)이 흘려보내므로, 동시 다운로드는 "스트림 사용 중" 으로 본다. "스트림 최대" 에 닿으면 새 다운로드는 503 으로 거절된다("스트림 거절/초").
- DB 잠금·느린 쿼리 행: 잠금 대기 세션·교착 · DB 시간을 가장 많이 쓰는 쿼리 · 1회 평균 시간 · 초당 호출 수(부하 테스트 DB 의 `pg_stat_statements`).
  잠금 대기 시간도 실행 시간에 들어간다 — 평균은 짧은데 DB 시간 상위면 잠금을 기다리는 쿼리. 초당 호출 수가 k6 초당 요청과 비슷하면 "요청마다 한 번" 쿼리(캐시 후보), 몇 배면 N+1.

### 지표가 어디서 오나

| 패널 지표 | 만드는 곳 | 가는 곳 |
|---|---|---|
| `k6_*` (예: `k6_upload_file_duration_p95`) | k6 — 내장 지표 + `flows/upload.js` 의 `Trend` | k6 → Prometheus(remote write) |
| `http_server_requests_*`, `hikaricp_*`, `jvm_*` | Spring Boot Actuator 자동 | Alloy 가 `/actuator/prometheus` 를 가져감 → Prometheus |
| `aws_s3_client_seconds_*` | 앱 `S3ObservationInterceptor`(Observation) | Prometheus(지표) + Tempo(구간) 둘 다 |
| `drive_upload_session_*` | 앱 `UploadSessionMetrics`(Counter·DistributionSummary) | Prometheus 만 |
| `drive_upload_active_*`, `drive_storage_deletion_*`, `drive_pg_lock_wait_*` | Alloy postgres exporter 커스텀 쿼리(`../alloy/pg-queries.yaml`) | Prometheus — "지금 DB 상태" 라 앱이 아니라 exporter 가 센다 |
| `drive_pg_statements_*{query}` | 부하 테스트 DB 전용 exporter 의 커스텀 쿼리(`../alloy/pg-statements.yaml`, `pg_stat_statements` 상위 20) | Prometheus |

## 장애 주입 — 부하 중에 DB 멈추기

에러·느린 요청을 일부러 만들어 **tail 샘플링이 그 트레이스를 남기는지**, 장애 때 대시보드·알림이 어떻게 보이는지 확인한다.

### 순서

1. "처음 한 번" 이 끝난 상태에서 **DriveAPI (loadtest)** 를 띄운다(트레이스 전송 켜짐, 샘플링 1.0).
2. 터미널 1 — 부하(약 4분). 실행 이름을 붙여 Grafana 에서 고를 수 있게 한다.
   ```powershell
   docker compose --profile loadtest run --rm -e TESTID=db-outage k6 run /scripts/read.js
   ```
3. 터미널 2 — 시작 90초 뒤 DB 를 **45초** 멈췄다가 다시 켠다.
   ```powershell
   Start-Sleep 90; docker compose --profile loadtest stop loadtest-db; Start-Sleep 45; docker compose --profile loadtest start loadtest-db
   ```
4. k6 가 끝나고 **40초 이상** 기다린 뒤 본다 — Alloy 가 `decision_wait`(30초) 동안 모았다가 Tempo 로 보낸다.

- 멈추는 시간은 **Hikari 커넥션 대기(30초)보다 길게**. 20초쯤이면 요청이 커넥션을 기다렸다가 DB 가 돌아와 성공하므로 5xx 가 생기지 않고 느린 요청만 생긴다.
- 멈춘 동안 Alloy 의 `postgres-loadtest` 수집이 접속 오류를 남긴다(무해). 운영 DB 알림(`job="postgres"`)과는 무관하다.
- k6 는 실패율 기준(1%)을 넘겨 실패로 끝나는 것이 정상이다.

### 확인 — 실제 요청 수와 남은 트레이스 수 비교

**실제 요청**(Prometheus, http://localhost:9090) — 샘플링과 무관하게 전부 센다. 범위 `[6m]` 은 실행 시간에 맞춘다.

| 보는 것 | 쿼리 |
|---|---|
| 결과별 요청 수 | `sum by (outcome) (increase(http_server_requests_seconds_count{job="drive-api"}[6m]))` |
| 1초 이하 요청 수 | `sum(increase(http_server_requests_seconds_bucket{job="drive-api",le="1.0"}[6m]))` — 전체에서 빼면 1초 초과 |

- `le="1.0"` 구간은 SLO 경계(`management.metrics.distribution.slo`)로 만든다. 그 설정 전 앱이면 가장 가까운 `le="0.984263336"` 을 쓴다.
- `increase()` 는 새로 생긴 시계열의 첫 값을 세지 못한다. 실행 중 처음 생긴 5xx 는 조금 적게 나올 수 있으니, 앱을 새로 띄운 경우 `http_server_requests_seconds_count{outcome="SERVER_ERROR"}` 의 끝값(누적)을 그대로 본다.

**남은 트레이스**(Grafana › Explore › Tempo, TraceQL) — 시간 범위를 실행 구간으로.

| 보는 것 | TraceQL | 기대 |
|---|---|---|
| 5xx 요청 | `{ span.outcome = "SERVER_ERROR" }` | 실제 5xx 와 (거의) 같은 수 |
| 1초 초과 요청 | `{ span.http.url != nil && duration > 1s }` | 실제 1초 초과와 같은 수 |
| 전체 HTTP 요청 | `{ span.http.url != nil }` | 실제 요청의 10% 남짓 + 에러·느린 요청 |

**수집기**(http://localhost:12345/metrics 또는 Alloy UI)

- `otelcol_receiver_accepted_spans_total` — 앱에서 받은 span(전부)
- `otelcol_exporter_sent_spans_total` — Tempo 로 보낸 span(샘플링 뒤)
- `otelcol_processor_tail_sampling_*` — 정책별 결정 수, 늦게 온 span(`dropped_too_early`)

### 지난 결과 (참고)

같은 방법(DB 45초 중단)으로 잰 값.

| | 앱 10% head 샘플링 | 앱 100% + tail(`decision_wait` 10초) | 앱 100% + tail(`decision_wait` 30초, 지금 설정) |
|---|---|---|---|
| 5xx 트레이스 남음 | 31건 중 2 | 31건 중 26 | **27건 중 27** |
| 1초 초과 트레이스 남음 | 53건 중 4 | 53건 중 53 | **197건 중 197** |
| 앱 → Alloy span | 2,539 | 25,779 | 21,985 |
| Alloy → Tempo span | | | 2,899 (87% 줄임) |
| p95 | 34.1ms | 37.0ms | |

- 10초에서 놓친 5xx 5건은 `decision_wait` 보다 오래 걸린 요청이다 — 첫 span 이 들어오고 결정이 먼저 나서 뒤늦게 온 에러 span 이 버려졌다. 30초로 늘린 뒤 재측정(2026-11-21)에서는 전부 남았다.
- 30초 측정의 "실제" 값: 5xx 는 앱 `http_server_requests_seconds_count{outcome="SERVER_ERROR"}` 누적(앱을 새로 띄운 뒤라 끝값 그대로), 1초 초과는 k6 `--out csv` 의 `http_req_duration` > 1000ms 건수.
  k6 가 본 5xx 는 2건뿐이었다 — 브라우저 경로 요청의 에러는 에러 페이지로 리다이렉트되어 k6 에는 3xx 로 보인다. 5xx 는 앱 쪽 수치로 센다.
- 30초 측정은 아래 "데이터량·삭제·공유 폴더 테스트" 와 같은 로컬 환경(Docker 없이 PC 에 직접 설치)에서 했다.

## 데이터량·삭제·공유 폴더 테스트 (`volume/`)

seed 데이터(사용자당 파일 210개)로는 보이지 않는 병목을 찾는다. 2026-11-21 에 한 번 돌린 결과를 아래에 남긴다.

| 파일 | 내용 |
|---|---|
| `volume/seed-volume.sql` | 대량 데이터 — 사용자 18명 × (루트 폴더 500 × 파일 100, big-folder 파일 1만, 깊이 30 폴더) ≈ 110만 건. **DB 행만** 만든다(스토리지 객체 없음) |
| `volume/ids.sql` | 스크립트가 쓰는 사용자별 번호(사용자 seq, big-folder, deep-30, 루트 폴더 500개 등)를 `volume/ids.json` 으로(생성 파일, 커밋 안 함) |
| `volume/volume.js` | 데이터량: API 12종(목록·큰 폴더·50페이지·깊은 폴더·트리·검색 2종·최근·즐겨찾기·용량·상세)을 API 별 p95 로 |
| `volume/steps.js` | 단계별 부하: `LEVELS`(예: `20,60,100,150`) 단계마다 고정 VU 로 둘러보기, 단계별 p95·처리량 |
| `volume/big-delete.js` | 큰 폴더(파일 1만) 휴지통 이동 → 비우기, 그동안 같은 사용자 쓰기·다른 사용자 읽기 |
| `volume/trash-empty.js` | 루트 폴더 500개(파일 5만)를 휴지통으로 → 한 번에 비우기, 위와 같이 같은 사용자·다른 사용자 |
| `volume/shared-folder.js` | 공유 폴더 동시 쓰기: 15명이 한 공유 폴더에서 폴더 만들기·이름 바꾸기. `MODE=own` 이면 각자 자기 드라이브(비교용) |
| `volume/mixed.js` | 혼합 부하 + 메모리: 읽기 20명 + 업로드 4명 + 다운로드 6명. 같은 부하를 WAS 힙만 바꿔 비교 |

### 실행

```bash
# seed.js 다음에 한 번
docker compose exec -T loadtest-db psql -U drive_load -d drive_load < k6/volume/seed-volume.sql
docker compose exec -T loadtest-db psql -U drive_load -d drive_load -At < k6/volume/ids.sql > k6/volume/ids.json

docker compose --profile loadtest run --rm k6 run /scripts/volume/volume.js
docker compose --profile loadtest run --rm k6 run -e LEVELS=20,60,100,150 -e DUR=60 /scripts/volume/steps.js
docker compose --profile loadtest run --rm k6 run /scripts/volume/big-delete.js       # 사용자 18 의 big-folder 를 지운다
docker compose --profile loadtest run --rm k6 run /scripts/volume/trash-empty.js      # 사용자 18 의 루트 폴더 500개를 지운다
docker compose --profile loadtest run --rm k6 run -e MODE=own -e THINK=0 /scripts/volume/shared-folder.js
docker compose --profile loadtest run --rm k6 run -e MODE=shared -e THINK=0 /scripts/volume/shared-folder.js
docker compose --profile loadtest run --rm k6 run /scripts/volume/mixed.js            # WAS 힙(-Xmx)을 바꿔 가며 반복
```

- 삭제 테스트는 데이터를 실제로 지운다. 다시 하려면 "다시 처음부터" 로 DB 를 새로 만든다.
- `mixed.js` 전에 업로드 한도를 올린다 — seed 사용자 한도(100MiB)를 `seed-volume.sql` 이 약 60MB 채워서 40MB 업로드가 용량 검사에 걸린다:
  `docker compose exec -T loadtest-db psql -U drive_load -d drive_load -c "update tb_user set total_quota_byte = 10737418240 where user_id like 'lt-user-%'"`
- 실행 이름은 `-e TESTID=…` 로 바꾼다(기본: 스크립트 이름) — Grafana "k6 부하 테스트" 에서 실행별로 고른다. `shared-folder.js` 는 `MODE` 마다 다른 `TESTID` 를 준다.
- 휴지통 비우기는 삭제 대기열(`tb_storage_deletion`)에 쌓기만 한다. 대기열 처리까지 보려면 배치 스케줄러를 켠다(`DRIVE_SCHEDULER_BATCH_ENABLED=true`).
- 잠금 대기는 테스트 중에 1초마다 본다: `select count(*) filter (where wait_event_type='Lock'), max(now()-query_start) filter (where wait_event_type='Lock') from pg_stat_activity where datname='drive_load'`

### 측정 환경과 읽는 법

- PC 한 대(Intel Core Ultra 5 225F 10코어, 메모리 32GB, Windows 11)에 k6·WAS·PG 18.6·RustFS 를 Docker 없이 직접 띄웠다. 다른 프로세스도 돌고 있었다.
- 그래서 **절대 수치(최대 처리량, 사양)는 쓰지 않는다.** 같은 환경에서 조건 하나만 바꾼 **전후 비교**와 **병목 위치**만 근거로 쓴다.

### 결과 1 — 데이터량 (10명, 60초, API 별 p95)

| API | seed 데이터 | 110만 건 | 인덱스 추가 후(#72) |
|---|---|---|---|
| 루트 목록 | 30ms | 214ms | 35ms |
| 폴더 목록 | 27ms | 22ms | 16ms |
| 파일 1만 개 폴더 첫 페이지 | — | 52ms | 43ms |
| 깊이 30 폴더 | — | 22ms | 18ms |
| 트리 | 22ms | 138ms | 92ms |
| **검색(흔한 키워드)** | 40ms | **1.42s** | **236ms** |
| **검색(없는 키워드)** | 27ms | **2.01s** | **128ms** |
| 최근 / 즐겨찾기 / 용량 / 상세 | 40 / 41 / 23 / 42ms | 21 / 56 / 9 / 17ms | 27 / 39 / 15 / 22ms |

- 검색만 SLO(1초)를 넘었다. 원인: 정렬 인덱스에 `user_seq` 가 없어 generic plan 이 파티션 전체(73만 행)를 정렬 순서로 훑으며 걸렀다. 해시 파티션 2개라 파티션 하나에 여러 사용자가 섞여 있다.
- 루트 목록·트리가 오른 것은 느린 검색이 CPU 를 차지해서다 — 검색만 고쳤는데 같이 내려왔다.
- 인덱스 추가 후 열은 이름 정렬 인덱스만 넣고 잰 값이다. 쿼리 단위 비교는 #72(V8).
- 50페이지: 목록은 커서 페이징(`cv`·`ci`·`ct`)이라 페이지 번호(`pi`)는 무시된다. VU 마다 49페이지를 따라가 구한 커서로 다시 잰 값(#72 V8·V9 적용 후, 같은 실행): 첫 페이지 104ms, **50페이지 105ms** — 깊이와 무관하다. 같은 실행에서 트리는 33ms(V9 전 138ms).

### 결과 2 — 단계별 부하 중 폴더 트리 (WAS 2코어로 제한)

- 120명 단계에서 `pg_stat_statements` 로 보니 폴더 트리 재귀 쿼리가 **DB 시간의 44%**(평균 295ms)였다. 재귀 단계가 하위 파일까지 모두 읽고 폴더만 남겼다.
- `(user_seq, parent_folder_seq, resource_type)` 인덱스(#72 V9) 후 같은 부하에서 평균 **5ms**, 전체 DB 시간 약 670초 → 185초.
- 이 측정에서 WAS(2코어)는 약 0.4코어만 썼고 DB 가 먼저 찼다. 사양 수치는 환경 때문에 쓰지 않는다.

### 결과 3 — 큰 폴더 삭제, 휴지통 비우기

처음 측정(비우기가 한 트랜잭션이던 빌드). 개선 후는 아래 "개선: 나눠서 비우기".

| | 파일 1만 개 폴더 | 루트 폴더 500개(파일 5만) |
|---|---|---|
| 휴지통으로 | 2.0s (요청 1번) | 21.6s (요청 500번) |
| 휴지통 비우기 | 1.6s | **8.8s (요청 1번)** |
| 그동안 같은 사용자 쓰기(2명) | p95 53ms, 최대 1.17s | p95 37ms, **최대 4.18s** |
| 그동안 다른 사용자 읽기(13~14명) | p95 76ms | p95 67ms |
| 실패 / 교착 | 0 / 0 | 0 / 0 |

- 삭제 대기열: 스케줄러를 켜자 첫 실행에서 5만 건을 약 25초에 처리했다(1,000건씩 DeleteObjects).
- 한계(처음 빌드): 비우기가 한 트랜잭션이라, 처음에 사용자 용량(사용자 행)을 줄인 뒤 그 행 잠금을 커밋까지 쥐고 있었다. 그동안 같은 사용자의 쓰기가 모두 기다렸다(최대 4.18s).

#### 개선: 나눠서 비우기 (#74, #75)

- #74: 요청 시작 시 비울 대상(`trash_seq`)을 고정하고, 루트 50개씩 묶음마다 따로 커밋한다 — 사용자 행 잠금이 묶음 하나 동안만 잡힌다.
- #75: 묶음이 복원과 겹치면 방금 복원한 파일을 지울 수 있던 경합을 막는다. 무거운 대상 조회는 잠금 밖에서 하고, 사용자 행을 잠근 뒤 그 묶음의 휴지통 행 수를 다시 센다. 줄었으면(그사이 복원이나 영구 삭제) 잠금 안에서 다시 조회한다.

같은 측정(`volume/trash-empty.js`, 루트 폴더 500개·5만 건, 같은 사용자 쓰기 2명 + 다른 사용자 읽기 10명):

| | 한 트랜잭션(처음) | 묶음 커밋(#74) | 잠금 후 행 수 확인(#75, 지금 main) |
|---|---|---|---|
| 비우기 요청 시간 | 4.6~8.8s | 5.8s | 5.1s |
| 비우는 동안 잠금 대기 최대(1초 샘플) | **2.9~4.2s** | 0.20s | 0.37s |
| 같은 사용자 쓰기 최대 | **3.2~4.2s** | 1.6s* | 0.68s |
| 같은 사용자 쓰기 p95 | 32~37ms | 68ms | 46ms |
| 실패 | 0 | 0 | 0 |

- 처음 열은 여러 번 잰 범위(같은 PC, 실행마다 편차). 1초 샘플이라 0.2~0.4초는 같은 수준으로 본다.
- \* #74 측정 때는 같은 순간 다른 사용자 읽기도 1.6s 였다 — 잠금 대기가 아니라 시스템 지연.
- 남은 한계
  - 전체 요청 시간은 여전히 건수에 비례한다(50만 건이면 수십 초). 중간에 실패하면 앞 묶음은 이미 지워진 채 남고, 다시 비우면 이어서 지운다.
  - 묶음은 휴지통 항목(루트) 50개 기준이라, 하위가 아주 많은 폴더 하나(예: 파일 1만 개)는 한 묶음에서 다 지운다. 그 묶음 동안은 잠금이 길다.
  - 30일 자동 정리 스케줄러(`cleanUpResourcesByDate`)는 여전히 한 트랜잭션이다. #75 는 대상 사용자를 user_seq 순으로 먼저 잠그고 그 사용자 것만 지우게 했을 뿐이다.

### 결과 4 — 공유 폴더 동시 쓰기 (15명, 60초, 폴더 만들기 + 이름 바꾸기)

| | 각자 자기 드라이브 | 한 공유 폴더 | 공유 폴더 안 사람마다 하위 폴더(`SPLIT=1`) |
|---|---|---|---|
| 0.2초 쉬며 | p95 21~24ms, 초당 129건 | p95 44~47ms, 초당 120건 | |
| 쉬지 않고 | p95 36ms, **초당 약 720건** | p95 142ms, **초당 약 137건** | p95 123ms, 초당 175건 |
| 잠금 대기 세션(평균) | 0 | 12.7 | 12.7 |
| 실패 / 교착 | 0 / 0 | 0 / 0 | 0 / 0 |

- 쉬지 않고 행의 앞 두 칸은 지금 스크립트(사용자마다 작업 폴더 아래에서 하고 끝나면 정리)로 다시 잰 값이다(만들기+이름 바꾸기 건수 ÷ 60초). 처음 잰 값(작업 폴더 없이 루트에서, 정리 없이)은 초당 811건 대 159건으로 비율이 같았다. 0.2초 행과 하위 폴더 칸은 처음 잰 값.

- 공유 폴더의 리소스는 모두 소유자의 것이라, 쓰기마다 **소유자 사용자 행**을 잠근다(잠금 순서: 사용자 → 부모 → 대상). 하위 폴더를 나눠도 처리량이 그대로라 병목은 부모 폴더가 아니라 사용자 행이다.
- 평소 속도(0.2초 쉼)에서는 p95 두 배 정도로 문제가 안 되지만, 한 사람의 공간에 쓰기가 몰리면 초당 약 170건이 상한이다. 용량이 바뀌지 않는 작업(폴더 만들기, 이름 바꾸기)에서 사용자 행 잠금을 빼는 것이 개선 후보 — 교착 방지 설계(잠금 순서)와 같이 봐야 한다.

### 결과 5 — 혼합 부하와 WAS 힙 (30명, 3분)

읽기 20명 + 업로드 4명(512KB 70%, 40MB 청크 30%) + 다운로드 6명(512KB 70%, 40MB 30%). 같은 부하를 `-Xmx` 만 바꿔 돌렸다. 3분에 약 12~14GB 를 내려받았다.

| WAS 힙(`-Xmx`) | 힙 최대 사용 | GC 멈춤 합(3분) | 프로세스 메모리(RSS) | 실패 | p95: 40MB 다운로드 / 40MB 업로드 / 읽기 |
|---|---|---|---|---|---|
| 4GB | 336MB | 0.16s | 927MB | 0 | 0.68s / 2.4s / 1.1s |
| 512MB | 312MB | 0.26s | 895MB | 0 | 0.91s / 3.9s / 1.8s |
| 256MB | 239MB | 0.35s | 810MB | 0 | 0.47s / 3.0s / 0.92s |
| 128MB | — | — | — | **기동 실패**(`OutOfMemoryError: Java heap space`) | — |

- 40MB 파일을 수백 번 주고받아도 힙은 300MB 대에 머문다 — 업로드·다운로드가 파일을 메모리에 올리지 않고 스트리밍한다. 힙을 256MB 로 줄여도 실패 없이 GC 멈춤만 조금 늘었다.
- p95 차이는 힙이 아니라 환경 탓이다(GC 멈춤은 3분에 0.4초 미만). 스토리지가 네트워크 디스크(iSCSI)라 업로드·다운로드가 몰리면 읽기까지 느려졌고, 실행마다 편차가 컸다.
- 힙 외 메모리(스레드 스택, 메타스페이스, 네이티브 버퍼)가 약 500~600MB 라, 컨테이너 메모리는 힙 + 600MB 이상으로 잡는다.

### 결과 6 — 단계별 부하: 둘러보기는 어디서 꺾이나 (10~400명, 단계마다 60초)

`volume/steps.js` 를 단계마다 따로 실행했다(`LEVELS=<VU> DUR=60`, 쉬는 시간 1초, 110만 건 데이터). 2026-11-21, main(#75) 빌드, 실행 전 30명 1분 워밍업.
k6 는 코어 7~9, WAS·PG 는 코어 0~6 에 고정했다(WAS 힙 4GB, Tomcat 요청 스레드 기본 200, Hikari 풀 50).

| VU | RPS | 이상적 대비 | p95 | p99 | 목록 p95 | 실패 | WAS CPU | PG CPU | 힙 최대 | Tomcat 바쁜 스레드 최대 | Hikari 대기 최대 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 10 | 18.0 | 100% | 57ms | 102ms | 24ms | 0 | 0.04코어 | 0.03코어 | 313MB | 2 | 0 |
| 25 | 44.5 | 100% | 46ms | 343ms | 96ms | 0 | 0.07 | 0.07 | 320MB | 1 | 0 |
| 50 | 88.7 | 100% | 51ms | 363ms | 77ms | 0 | 0.14 | 0.16 | 328MB | 2 | 0 |
| 100 | 168 | 95% | 83ms | 819ms | 275ms | 0 | 0.33 | 0.38 | 319MB | 6 | 0 |
| 150 | 237 | 89% | 139ms | 1.49s | 434ms | 0 | 0.59 | 0.60 | 334MB | 58 | 0 |
| 200 | 340 | 96% | 91ms | 772ms | 317ms | 0 | 0.63 | (0.31) | 314MB | 8 | 0 |
| **300** | **430** | **81%** | **627ms** | 1.84s | **1.13s** | 0 | 1.28 | 1.60 | 365MB | **200(상한)** | **18** |
| **400** | **524** | **74%** | **828ms** | 2.11s | **1.14s** | 0 | 1.67 | 2.14 | 403MB | 111 | **43** |

- RPS 는 k6 요약의 `http_reqs` 초당 값(요청 수 ÷ 실행 전체 시간 — 로그인과 60초 뒤 `gracefulStop` 5초 동안 끝난 반복까지 포함, 단계마다 약 62~65초). **이상적 대비** = RPS ÷ (VU × 1.78) — 쉬는 시간만 있고 서버가 막히지 않을 때의 처리량(10~50명에서 잰 VU 당 1.78건/s) 대비 비율이다.
- CPU 는 단계 전후 `java`·`postgres` 프로세스 누적 CPU 시간 차 ÷ 단계 시간. PG 는 그사이 끝난 백엔드 프로세스의 시간이 빠져 작게 나온다(200명 칸이 특히) — 대략값.
  힙·Tomcat·Hikari 는 `/actuator/prometheus` 를 5초마다 읽은 최대값.
- 원본: [`results/2026-11-21-steps/`](results/2026-11-21-steps/) — 단계별 k6 요약(`k6-vu<N>.out`), 5초 샘플(`sample-vu<N>.log`), CPU 누적값(`cpu.txt`), 실행 스크립트(`run.sh`).
- **200명(약 340 RPS)까지는 처리량이 VU 에 비례하고 p95 가 100ms 안팎** — 쉬는 시간이 처리량을 정하고 서버는 여유가 있다.
- **300명부터 꺾인다**: RPS 가 이상적 값의 81%(400명 74%), p95 91ms → 627ms, Tomcat 요청 스레드가 상한(200)에 닿고 Hikari 커넥션 대기가 생긴다 — DB 가 늦어지고 그걸 기다리는 요청 스레드가 쌓이는 모양.
  k6 목록 기준(p95 1초)은 300명에서 넘는다. 운영 SLO(99% 가 1초 안)로 보면 p99 가 100명부터 1초 근처라 더 이르다.
- 150명이 200명보다 나쁜 것은 단계마다 한 번만 재서 생긴 편차(같은 PC 의 다른 프로세스)로 본다. 정확히 보려면 단계마다 3번씩 잰다.
- 사용자 18명을 VU 가 나눠 쓰고, k6·WAS·DB 가 한 PC 에 있다 — 이 표는 운영 사양이 아니라 **이 환경에서 병목이 어디서 생기는지**의 근거다.
  다음에 볼 것: 300명 단계에서 `pg_stat_statements` 상위 쿼리(목록·검색), Tomcat 스레드·Hikari 풀 크기를 바꿨을 때 꺾이는 지점이 움직이는지.

### 결과 7 — S3 커넥션 풀과 동시 다운로드 (2026-11-22, 수정 전만 측정)

업로드·다운로드·삭제가 같은 동기 `S3Client` 를 쓰는데 HTTP 클라이언트 설정이 없어 **SDK 기본 커넥션 풀(동시 50, 대기 10초 × 재시도 3회)** 에 묶여 있었다.
다운로드 스트림 상한(200)·Tomcat(200)보다 S3 풀이 작아, 입구에서 받아 준 요청이 S3 연결을 기다리다 실패한다.

`download-concurrency.js` 를 단계마다 따로 실행했다(128MB 파일, 생각 시간 0 — VU = 전송 중인 다운로드, 단계마다 90초). 수정 전 jar(`app-te6.jar`), k6·WAS·PG·RustFS 가 한 PC.

| 동시 다운로드 | 성공 | 실패 | 전체 받은 양 | 1건 속도 중간값 | 1건 시간 p95 | Tomcat 바쁜 스레드 최대 |
|---|---|---|---|---|---|---|
| 50 | 744 | 0 | 924MB/s | 21MB/s | 9.8s | 10 |
| 100 | 804 | 1 (0.05%) | 961MB/s | 12MB/s | 21.9s | 58 |
| **200** | 832 | **545 (17.7%)** | 963MB/s | 10MB/s | 27.5s | **154** |

- **50 을 넘으면 S3 풀을 기다린다**: 다운로드 요청 스레드는 S3 연결을 빌린 뒤 바로 풀려나야 하는데, 풀(50)이 차면 연결을 기다리며 붙잡힌다 — Tomcat 바쁜 스레드가 58·154 로 오른다.
  200명에서는 대기(10초 × 3회)를 넘긴 요청이 실패했다(17.7%). 업로드 비정상 클라이언트 측정(2026-11-21)의 파트 500 도 같은 원인(`Timeout waiting for connection from pool (SDK Attempt Count: 3)`).
- **전체 받은 양은 50명에서 이미 다 찼다**(924 → 963MB/s). 동시 수를 늘리면 같은 대역폭을 나눠 쓰며 1건 속도만 떨어진다.
  → 다운로드 상한은 **회선 대역폭 ÷ 1인당 최소 속도**로 정한다(CPU 코어 수와는 관계가 약하다 — 스레드는 대부분 I/O 대기). 1Gbps(약 125MB/s)·1인당 1MB/s 면 약 100.
- 200명 단계 중에는 같은 PC 에서 다른 프로그램이 3코어 넘게 썼다 — 경향만 본다. 250명 단계는 중단해 결과가 없다.
- 원본: [`results/2026-11-22-s3-pool/`](results/2026-11-22-s3-pool/) — 단계별 k6 요약(`k6-before-vu<N>.out`), 2초 샘플(`sample-before-vu<N>.log`), 실행 스크립트(`run-dlc.sh`, 일반 스크립트용 `run-k6.sh`).
  수정 전 샘플의 `stream=` 은 라벨 순서 문제로 비어 있다(스크립트는 고쳤다).

**바꾼 것** (S3 커넥션 풀 PR)

| 설정 | 전 | 후 |
|---|---|---|
| S3 동기 클라이언트 풀 `storage.http.max-connections` | 50(SDK 기본) | **200** = 다운로드 100 + 업로드 파트 50 + 제어용 20 에 여유 |
| 풀 대기 `storage.http.connection-acquisition-timeout` | 10초 × 재시도 3회 | **2초, 재시도 없이 503**(`STORAGE_009`) — 다른 오류 재시도(STANDARD 3회)는 그대로 |
| 연결·소켓 시간 제한 | SDK 기본 | 2초·30초(SDK 기본과 같은 값을 명시) |
| 다운로드 스트림 상한 `drive.download.stream.max-threads` | 200 | **100**(환경변수 `DRIVE_DOWNLOAD_STREAM_MAX_THREADS`) |
| 지표 | 없음 | `drive_s3_http_acquire_seconds`(빌리는 시간)·`_acquire_timeout_total`(503 수)·`_leased`·`_pending`·`_max_connections` |
| Grafana | — | "k6 부하 테스트"·"Drive 개요" 에 S3 커넥션 풀·다운로드 스트림 패널, 알림 "S3 커넥션 대기 발생"(5분 지속) |

**남은 일**
1. 수정 후 jar 로 재측정 — 다운로드 50·100·150·200명(100명까지 대기 없이 받고 넘치면 503 인지, Tomcat 바쁜 스레드가 낮게 유지되는지),
   업로드 비정상 클라이언트(파트 500 → 503, 피해 사용자 p95), 업로드 정상(`upload.js -e PROFILE=stress -e THINK=0`, 실패 0 유지). 이 표에 전후로 채운다.
2. ~~업로드 파트 동시 상한~~ → 2026-11-27 `UploadConcurrencyLimitFilter`(`drive.upload.max-concurrent-bodies`, 기본 50)로 넣고 전후를 쟀다 — 아래 "결과 8".
3. 다운로드 상한을 설치처 회선·스토리지 사양으로 정한다 — 이 PC 는 내부 전송(약 960MB/s)이라 회선 병목이 재현되지 않는다. 운영과 같은 구성(WAS·스토리지 분리)에서 1인당 속도를 확인한다.
4. 범위 밖으로 둔 것: 전송용·제어용(complete·abort·삭제) S3 클라이언트 분리, `S3AsyncClient` 풀, WAS 여러 대일 때 스토리지 동시 연결(풀 × WAS 대수) 한도 확인.

**재측정 방법** (k6·WAS·스토리지가 한 PC — 측정 중에는 다른 무거운 프로그램을 끈다)

```bash
# WAS(수정 후 = main 빌드 jar) — C:/loadtest-tools/run-app.sh
cd /c/loadtest-tools && APP_JAR=<jar> bash run-app.sh --management.otlp.tracing.export.enabled=false
# 동시 다운로드 단계(단계마다 90초, 128MB 파일 — 사용자마다 setup 에서 한 번 올려 두고 재사용)
bash observability/k6/results/2026-11-22-s3-pool/run-dlc.sh after 50 100 150 200
# 업로드 정상 부하
bash observability/k6/results/2026-11-22-s3-pool/run-k6.sh after-upload upload.js -e PROFILE=stress -e THINK=0
# 업로드 비정상 클라이언트(파트 500 → 503, 피해 사용자 목록 조회 p95)
bash observability/k6/results/2026-11-22-s3-pool/run-k6.sh after-abuse abuse-parts.js
```

볼 것: 다운로드 100명까지 실패 0·S3 풀 대기 없음, 100명 초과분은 즉시 503(`drive_download_stream_rejected_total`), Tomcat 바쁜 스레드가 낮게 유지되는지, 업로드 실패 0.

#### 설계 메모 — 다운로드 스트림 풀은 "MVC 비동기 실행기"다

- `StreamingAsyncConfig` 는 `WebMvcConfigurer.configureAsyncSupport` 에 실행기를 넣는다 — **다운로드 전용 풀이 아니라 MVC 기본 비동기 실행기**다.
  컨트롤러가 `StreamingResponseBody` 를 반환하면 Spring 이 본문 쓰기(`writeTo`)를 `Callable` 로 감싸 이 실행기에 넘기고, 요청 스레드는 풀려난다.

  | 반환 타입 | 이 실행기 사용 |
  |---|---|
  | `StreamingResponseBody`, `Callable`, 실행기를 지정하지 않은 `WebAsyncTask`, MVC 의 `Flux` 스트리밍 | 씀 |
  | `DeferredResult`, `CompletableFuture`, `ResponseBodyEmitter`·`SseEmitter` | 안 씀(결과를 채우는 쪽 스레드가 따로 있다) |

  이 앱의 비동기 반환은 모두 `StreamingResponseBody`(파일·보기·폴더 zip·여러 항목·버전 다운로드, 프로필 이미지, 관리자 CSV 샘플)라 **사실상 다운로드 풀**이다.
  감사 로그 CSV 내보내기는 이 풀을 나눠 쓰지 않으려고 요청 스레드에서 직접 쓴다(`AuditLogController`).
- **설정하지 않으면**: `AsyncConfig` 의 @Primary 실행기 때문에 Boot 의 `applicationTaskExecutor` 가 없어 `SimpleAsyncTaskExecutor` — 요청마다 새 스레드, 상한·거절·지표가 없다.
  다운로드가 몰려도 거절하지 못하고 모두 동시에 전송하며 느려진다. 지금은 S3 풀(200)이 사실상 막지만, 그때는 2초 대기 뒤 `STORAGE_009` 503 이고 **업로드·삭제까지 연결을 못 받아 같이 실패**한다.
  상한(100)·대기열 0·즉시 503 을 두는 이유: 시간 제한(`request-timeout: -1`)을 푼 대신 오래 걸리는 전송을 묶고, 대역폭은 정해져 있어 더 받아도 모두 느려질 뿐이며, 다운로드가 S3 연결을 다 쓰지 못하게 해 업로드를 지킨다.
- **지표**: 실행기가 빈(`driveStreamExecutor`)이라 Boot 가 `executor_active_threads{name="driveStreamExecutor"}`·`executor_pool_max_threads` 등을 자동으로 낸다. 거절은 직접 등록한 `drive.download.stream.rejected` 카운터.
  실행기 단위 지표라 **다운로드가 아닌 비동기 API 가 생기면 숫자가 섞인다**(Spring 의 `http_server_requests_active` 는 진행 중 요청의 경로가 `UNKNOWN` 이라 종류를 못 나눈다).
- **나중에 다운로드를 전용으로 나눌 때** — 지금은 필요 없다. 다운로드가 아닌 비동기 API 를 추가할 때 고른다.
  - A(가장 간단): 새 비동기 API 가 자기 실행기를 지정한다 — `new WebAsyncTask<>(timeout, 자기 실행기, callable)`, `CompletableFuture.supplyAsync(..., 자기 실행기)`.
  - 작은 응답(프로필 이미지·CSV 샘플)을 `byte[]`·`Resource` 동기 반환으로 바꾸면 지금 구조로도 실행기를 쓰는 게 파일 다운로드뿐이 된다.
  - B: `DownloadBody`(별도 인터페이스 — `StreamingResponseBody` 를 상속하면 Spring 기본 처리기가 먼저 잡는다) + 전용 `HandlerMethodReturnValueHandler`(기본 처리기보다 앞에 등록) + `downloadExecutor` 빈.
    처리기가 `WebAsyncTask(-1, downloadExecutor, writeTo 작업)` 로 시작한다. 풀은 같은 `ThreadPoolTaskExecutor` 를 하나 더 만드는 것이지 상속이 아니다.
  - 종류별 동시 수가 필요하면 `writeTo` 시작·끝에서 올리고 내리는 게이지를 `kind`(file·zip·version·profile) 라벨로 둔다.
  - 스트리밍 기능별로 상한을 따로 두려면 실행기 하나(크기 = 합계) + 기능별 세마포어(넘치면 그 기능만 503).

#### 설계 메모 — 업로드 파트 동시 상한(남은 일 2, 구현: `global/filter/UploadConcurrencyLimitFilter`)

- 대상은 본문을 받는 두 경로 — 청크 파트 `PUT .../parts/{n}`(octet-stream)과 단일 업로드 `POST /api/v1/files`(multipart). multipart 는 DispatcherServlet 이 컨트롤러·인터셉터보다 먼저 본문을 다 읽으므로 서블릿 필터(테넌트 필터 다음, 시큐리티 앞)에 둔다.
- 거절 응답에는 `Connection: close` 를 붙인다 — 읽지 않은 본문이 같은 연결의 다음 요청으로 읽히지 않게. 단일 업로드도 프론트(`UploadDialog`)가 503·429 를 1·2·4초 간격으로 재시도한다(응답 없이 끊긴 경우는 본문을 다 보내기 전일 때만 — 다 보낸 뒤면 이미 저장됐을 수 있다).
- 지표 이름은 `drive_upload_body_active`·`drive_upload_body_max`·`drive_upload_body_rejected_total`.
- 업로드 파트(`PUT /api/v1/uploads/{sessionId}/parts/{n}`)는 동기 요청이라 Tomcat 요청 스레드가 파트 전송(16MB) 동안 S3 연결과 함께 점유한다. 별도 스레드 풀을 만들지 않고 **개수만 센다**.
- WAS 인스턴스 단위 `java.util.concurrent.Semaphore`(JVM 안의 카운터, OS 세마포어 아님) 50 안팎:
  `tryAcquire()`(기다리지 않음) 실패 → 본문을 읽기 전에 503 + `Retry-After: 1`, 성공하면 처리 후 `finally` 에서 반납. 업로드가 없을 때는 Tomcat 200 을 다른 API 가 다 쓴다 — 미리 떼어 두는 게 아니라 최대치만 묶는다.
- 세션 단위·Redis 로 세지 않는 이유: 한 세션의 파트가 여러 WAS 로 흩어지고, Redis 카운터는 WAS 가 죽으면 반납되지 않는다. 지키려는 것은 그 WAS 의 Tomcat 스레드와 S3 풀이다. WAS 가 여러 대면 전체 상한 = 50 × 대수.
- 프론트(`DriveFront/utils/chunkUpload.js`)는 파트 5xx·429·네트워크 오류를 1·2·4초 간격으로 3번 재시도한다 —
  본문을 다 읽지 않고 거절하면 Tomcat 이 연결을 끊어 네트워크 오류로 보일 수 있는데 이것도 재시도 대상이다. 7초 넘게 붐비면 그 파트는 실패하고, 올린 파트는 남아 이어 올릴 수 있다.
- 이 상한 없이 S3 풀만으로도 업로드는 결국 막히지만(2초 대기 뒤 503), 그동안 Tomcat 스레드가 200 까지 찰 수 있고 업로드·다운로드가 S3 풀을 서로 빼앗는다.

## 다시 처음부터

```bash
docker compose --profile loadtest rm -sf loadtest-db loadtest-storage
docker volume rm drive-observability_loadtest-db-data drive-observability_loadtest-storage-data
```
그 뒤 "처음 한 번" 부터.

## 주의

- 라이선스 없는 새 DB 는 좌석이 20개 남짓(admin 포함) — 사용자를 더 만들면 `USER_013`. 늘리려면 관리자 화면에서 라이선스를 넣고 `-e USERS=` 를 올린다.
- 틀린 비밀번호 5번이면 계정이 잠긴다 — 비밀번호를 바꾸면 처음부터.
- 같은 PC 에서 k6·WAS·DB 가 CPU 를 나눠 쓴다. 이 결과는 **병목 위치 찾기** 용이고, 처리량 수치는 k6 를 다른 머신에 두고 서버에서 잰다.
- 앱은 트레이스를 전부 보내고(실행 구성 1.0), Alloy 가 에러·5xx·1초 이상 요청(파일 전송 요청 제외)은 전부, 나머지는 10%만 Tempo 에 남긴다(`../README.md` 트레이스 샘플링).
