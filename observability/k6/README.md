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

## 보는 곳

Grafana http://localhost:3300 › Drive › **k6 부하 테스트**

- 위: k6 가 본 결과 — VU 수, API 별 초당 요청·p95, 실패율, 상태 코드
- 아래: 같은 시간대 서버 — WAS p95(점 → 트레이스), Hikari 커넥션 사용·대기, 부하 테스트 DB 세션·트랜잭션(`job="postgres-loadtest"`), CPU, 힙·GC, WARN·ERROR 로그
- 읽는 법: VU 를 올려도 초당 요청이 더 안 오르고 p95 만 오르면 한계. 그때 Hikari 대기가 0 보다 크면 DB 풀·쿼리, CPU 가 차면 WAS, DB 세션 active 가 많으면 DB 쪽.
  느린 요청은 WAS p95 그래프의 점을 눌러 트레이스에서 어느 SQL 인지 본다.
- 업로드 행: 파일 하나 올리는 시간(k6) · 세션 시작·종료(앱) · 진행 중 세션(DB) · S3 호출 p95·오류(앱) · 정리 대기열(DB).
  업로드가 느려질 때 S3 p95 가 같이 오르면 스토리지, 그대로면 WAS·DB(complete 의 커밋 tx). 시작보다 completed 가 적고 진행 중 PENDING 이 계속 늘면 클라이언트가 끝내지 못하는 세션이 쌓이는 것.

### 지표가 어디서 오나

| 패널 지표 | 만드는 곳 | 가는 곳 |
|---|---|---|
| `k6_*` (예: `k6_upload_file_duration_p95`) | k6 — 내장 지표 + `flows/upload.js` 의 `Trend` | k6 → Prometheus(remote write) |
| `http_server_requests_*`, `hikaricp_*`, `jvm_*` | Spring Boot Actuator 자동 | Alloy 가 `/actuator/prometheus` 를 가져감 → Prometheus |
| `aws_s3_client_seconds_*` | 앱 `S3ObservationInterceptor`(Observation) | Prometheus(지표) + Tempo(구간) 둘 다 |
| `drive_upload_session_*` | 앱 `UploadSessionMetrics`(Counter·DistributionSummary) | Prometheus 만 |
| `drive_upload_active_*`, `drive_storage_deletion_*` | Alloy postgres exporter 커스텀 쿼리(`../alloy/pg-queries.yaml`) | Prometheus — "지금 DB 상태" 라 앱이 아니라 exporter 가 센다 |

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
| 1초 이하 요청 수 | `sum(increase(http_server_requests_seconds_bucket{job="drive-api",le="0.984263336"}[6m]))` — 전체에서 빼면 1초 초과 |

- histogram 에 `le="1.0"` 구간이 없어 가장 가까운 `0.984263336` 을 쓴다.
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

같은 방법(DB 45초 중단, `decision_wait` 10초)으로 잰 값. 지금 설정은 `decision_wait` 30초다.

| | 앱 10% head 샘플링 | 앱 100% + tail 샘플링 |
|---|---|---|
| 5xx 트레이스 남음 | 31건 중 2 | 31건 중 26 |
| 1초 초과 트레이스 남음 | 53건 중 4 | 53건 중 53 |
| 앱 → Alloy span | 2,539 | 25,779 |
| p95 | 34.1ms | 37.0ms |

놓친 5xx 5건은 `decision_wait`(당시 10초)보다 오래 걸린 요청이다 — 첫 span 이 들어오고 결정이 먼저 나서 뒤늦게 온 에러 span 이 버려졌다. 그래서 30초로 늘렸고, 30초에서의 재측정은 아직 하지 않았다.

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
- 앱은 트레이스를 전부 보내고(실행 구성 1.0), Alloy 가 에러·5xx·1초 이상 요청은 전부, 나머지는 10%만 Tempo 에 남긴다(`../README.md` 트레이스 샘플링).
