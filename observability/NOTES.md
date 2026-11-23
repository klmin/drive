# Drive 관측 스택 정리 노트 (2026-11-23)

관측 스택(Alloy·Prometheus·Loki·Tempo·Grafana·k6)과 앱 쪽 Spring 설정을 한곳에 모은 정리. 자세한 동작은 같은 폴더의 `README.md`·`HOW-IT-WORKS.md`·`k6/README.md`.
같은 내용의 웹 페이지(그림 포함, 작성자 계정 전용): https://claude.ai/artifact/9i5YhBntuacPiwREQueFMJ

- 출처: `observability/` 전체, `DriveAPI/src/main/resources/application.yml`, `log/log4j2-server.yml`, DriveAPI Java 소스, `observability/k6/README.md`, `LOCAL-TEST-GUIDE.md`

---

## 0. 오늘 어디까지 했나 (이어서 할 일)

| 항목 | 상태 |
|---|---|
| IntelliJ 실행 구성 "module not specified" | 원인: `DriveAPI/pom.xml` 이 Maven 프로젝트로 연결 안 됨(루트에 빌드 파일이 없는 모노레포 + `.idea` 는 git 제외). `pom.xml` 우클릭 › **Add as Maven Project** 로 해결 |
| 첫 기동 실패 `BuildProperties` bean 없음 | `target/classes/META-INF/build-info.properties` 가 Maven 빌드(`spring-boot:build-info`)로만 생김. Maven import 후 생겨서 다시 실행하니 기동됨. 또 나면 Maven 창 › Plugins › spring-boot › `build-info` 실행 |
| DriveAPI(모드 A) 기동 | 17:02 기동 확인. health UP, `up{job="drive-api"}`=1, Flyway V1~V9 |
| admin 비밀번호 | 로컬 도커 PG(15433)에 `seed-admin.sql` 적용 → `admin` / `LoadTest!2026` |
| 업로드 U1~U8 · 다운로드 D1~D5 | **아직 안 함** — 다음에 이어서 |
| `docs/todo/next-session.md` §2-3 반영 | 확인 끝난 뒤 |
| 주의 | 이 PC 의 **8082 는 commerce-saga inventory-service 가 사용 중** → 모드 B(DriveAPI loadtest, 8082) 전에 꺼야 함 |

---

## 1. 전체 흐름

```
DriveAPI (IntelliJ, 서비스 :8080 / 관리 :8091)
 ├ Micrometer → /actuator/prometheus (:8091) ◀── Alloy 가 15초마다 가져감(pull) ──▶ Prometheus (remote_write)
 ├ Micrometer Tracing(OTel) ── OTLP HTTP ──▶ Alloy :4318 ── tail 샘플링 ──▶ Tempo (OTLP gRPC :4317)
 └ log4j2 → C:/log/drive/backend/drive-server.log ◀── Alloy 가 따라 읽음(tail) ──▶ Loki (push API)
PostgreSQL ◀── Alloy 내장 postgres exporter (SQL) ──▶ Prometheus
k6 ── HTTP 부하 ──▶ DriveAPI(:8082, 부하 모드) , k6 결과 ── remote write ──▶ Prometheus
Grafana(:3300) ── Prometheus · Tempo · Loki 를 서로 링크해서 보여 줌
```

- 메트릭은 **pull**, 트레이스는 **push**, 로그는 **파일 tail**.
- Prometheus 는 앱을 직접 긁지 않는다 → `localhost:9090/targets` 에는 `prometheus` 하나만 보인다. 앱 수집 여부는 `up{job="drive-api"}` 나 Alloy UI(http://localhost:12345)로 본다.

---

## 2. 구성요소 역할

| 구성요소 | 역할 | 포트 |
|---|---|---|
| **Micrometer** | 앱 안의 계측 라이브러리. HTTP·JVM·Hikari·Tomcat 지표 자동 + 프로젝트 커스텀 지표. Micrometer Tracing(OTel 브리지)이 traceId 를 만들어 로그 MDC·OTLP 로 넘김 | — |
| **Spring Actuator** | 지표·상태를 HTTP 로 내놓는 엔드포인트 | 8091 |
| **Alloy** | Grafana 의 수집기. 세 신호를 받아 가공 후 저장소별 전송. tail 샘플링·로그 파싱·PG/OS exporter | UI 12345, OTLP 4317/4318 |
| **Prometheus** | 메트릭 시계열 저장소. remote write 로 받기만 함. exemplar 저장 | 9090 |
| **Tempo** | 트레이스 저장소. 서비스 그래프를 만들어 Prometheus 로 | 3200 |
| **Loki** | 로그 저장소. 라벨(`service_name`·`level`·`env`) + 메타데이터(`trace_id`) | 3100 |
| **Grafana** | 화면. 데이터소스 3개·대시보드 2개·알림 14개 자동 등록 | 3300 |
| **k6** | 부하 생성기. 한 번 돌고 끝나는 컨테이너, 결과를 Prometheus 로 | (웹 대시보드 옵션 5665) |

---

## 3. Docker Compose (`observability/docker-compose.yml`)

파일 하나에 두 묶음.

| 묶음 | 서비스 | 띄우는 법 |
|---|---|---|
| 항상 | alloy, prometheus, loki, tempo, grafana | `docker compose up -d` |
| profile `loadtest` | loadtest-db, loadtest-storage, k6 | `--profile loadtest` 붙일 때만 |

### 서비스별 의미

| 서비스 | 이미지 | 핵심 설정 | 뜻 |
|---|---|---|---|
| alloy | grafana/alloy:v1.10.1 | env `DRIVE_METRICS_TARGETS`(기본 `host.docker.internal:8091`), `DRIVE_ENV`, `DRIVE_PG_DSN` · 볼륨 `config.alloy`, `pg-queries.yaml`, `pg-statements.yaml`, `${DRIVE_LOG_DIR}:/var/log/drive:ro` · 포트 12345/4317/4318 · `extra_hosts: host.docker.internal:host-gateway` | 컨테이너에서 PC 의 앱에 `host.docker.internal` 로 닿게 함. 로그 폴더를 읽기 전용으로 넣음 |
| prometheus | prom/prometheus:v3.5.0 | `--web.enable-remote-write-receiver` · `--enable-feature=exemplar-storage` · `--storage.tsdb.retention.time=15d` / `.size=20GB` | 밀어 넣는 지표 받기, 그래프 점→트레이스 링크, 15일 또는 20GB 먼저 닿는 쪽 기준 삭제 |
| loki | grafana/loki:3.5.3 | `loki/config.yaml` | |
| tempo | grafana/tempo:2.8.2 | `tempo/config.yaml`, `user: "0"` | 새 볼륨 쓰기 권한 때문에 root 로 |
| grafana | grafana/grafana:12.1.1 | `GF_SECURITY_ADMIN_USER/PASSWORD`(기본 admin/admin), `GF_USERS_ALLOW_SIGN_UP=false`, `./grafana/provisioning` 마운트, 포트 **3300**:3000 | 3000 은 프론트 개발 서버가 써서 3300 |
| loadtest-db | postgres:18.6-alpine | `drive_load`/`drive_load_pw`, 포트 15440, `shared_preload_libraries=pg_stat_statements` | 부하 전용 DB. 쿼리별 누적 시간 수집 |
| loadtest-storage | rustfs/rustfs:1.0.0 | `drive_load`/`drive_load_secret`, 포트 19010 | 부하 전용 S3 호환 스토리지 |
| k6 | grafana/k6:1.2.0 | `BASE_URL=${K6_BASE_URL:-http://host.docker.internal:8082}`, `K6_OUT=experimental-prometheus-rw`, `K6_PROMETHEUS_RW_SERVER_URL=http://prometheus:9090/api/v1/write`, `K6_PROMETHEUS_RW_TREND_STATS=p(95),p(99),avg,max`, 볼륨 **`./k6:/scripts:ro`** | k6 결과를 Prometheus 로. `observability/k6` 폴더가 컨테이너 `/scripts` |

### `observability/.env` (compose 가 자동으로 읽음)

| 키 | 지금 값 | 뜻 |
|---|---|---|
| `DRIVE_METRICS_TARGETS` | `host.docker.internal:8091` | Alloy 가 지표 가져갈 앱 관리 포트 |
| `DRIVE_LOG_DIR` | `C:/log/drive` | 앱 로그 상위 폴더 → 컨테이너 `/var/log/drive` |
| `DRIVE_ENV` | `local` | 지표·로그에 붙는 환경 라벨 |
| `DRIVE_PG_DSN` | `postgresql://…@host.docker.internal:15433/drive` | Alloy 가 DB 지표 읽을 곳(2026-11-23 부터 로컬 도커 PG). 비밀번호 특수문자는 URL 인코딩 |
| `GRAFANA_ADMIN_PASSWORD` | (없음 → `admin`) | |

---

## 4. 스프링 설정 (`application.yml` 의 `management.*`)

| 설정 | 값 | 뜻 · 원래 옵션인가 |
|---|---|---|
| `management.server.port` | `${DRIVE_MANAGEMENT_PORT:8091}` | **Actuator 전용 포트.** 서비스 포트와 다르면 Actuator 는 8091 에서만 열림(8080 `/actuator/*` 은 302 → `/error-page`). 외부에 열지 않음 |
| `endpoints.web.exposure.include` | `health,info,prometheus` | HTTP 로 열 엔드포인트 목록. env·heapdump·beans 등은 안 열림(404) |
| `health.redis.enabled` | `false` | Redis 헬스체크 끔. starter 의존성만 남아 있어 Redis 서버가 없으면 health 전체가 DOWN 되는 걸 막음 |
| `metrics.tags.application` | `drive-api` | **모든 지표에 붙는 공통 라벨** `application="drive-api"`(서비스명 개념이 아니라 라벨) |
| `distribution.percentiles-histogram` | `http.server.requests`, `aws.s3.client` | Spring Boot 표준 옵션. 응답 시간을 구간별 개수(`_bucket{le=…}`)로도 내보냄 → Grafana `histogram_quantile` 로 p95·p99, exemplar(점→트레이스). 키는 지표 이름이며 **`aws.s3.client` 는 프로젝트가 만든 지표** |
| `minimum/maximum-expected-value.aws.s3.client` | `1ms` / `2m` | 히스토그램 구간을 이 범위로만 → 시계열 수 줄임(S3 지표는 라벨 조합이 많음) |
| `distribution.slo.http.server.requests` | `300ms,1s,3s` | **프로젝트가 정한 값.** 히스토그램 경계에 정확히 0.3·1·3초를 추가할 뿐(앱 동작·타임아웃 영향 없음). 기본 구간엔 1초가 없어(0.984초) "1초 안 비율"을 정확히 셀 수 없기 때문. SLO 알림이 `le="1.0"` 사용 |
| `observations.enable.spring.security` | `false` | **Security 는 그대로 동작.** 필터 체인 단계별 span 기록만 끔(요청마다 10여 개라 트레이스를 가림) |
| `tracing.sampling.probability` | `${DRIVE_TRACING_SAMPLING_PROBABILITY:1.0}` | 앱은 트레이스 **100% 전송**, 남길지는 Alloy tail 샘플링이 결정. 앱에서 낮추면 에러 트레이스도 같이 버려짐 |
| `otlp.tracing.endpoint` | `http://localhost:4318/v1/traces` | **Tempo 가 아니라 Alloy 주소.** 앱 → Alloy(4318) → 샘플링 → Tempo |
| `otlp.tracing.export.enabled` | `${DRIVE_OTLP_TRACING_EXPORT_ENABLED:false}` | 기본 꺼짐: 관측 스택 없는 곳에서 연결 오류가 계속 나지 않게. 이 PC 는 `DriveAPI/.env` 에서 `true`. 꺼도 로그의 traceId 는 찍힘 |
| `opentelemetry.resource-attributes.service.name` | `drive-api` | **트레이스**의 서비스 이름(Tempo 검색·서비스 그래프) |
| `opentelemetry.resource-attributes.deployment.environment` | `${DRIVE_ENV:local}` | 트레이스의 환경 이름 — 여러 환경 트레이스가 한 Tempo 에 모일 때 구분 |

### `drive-api` 가 쓰이는 세 군데

| 어디 | 붙는 곳 | 보는 곳 |
|---|---|---|
| `management.metrics.tags.application` | 메트릭 라벨 `application` | Prometheus |
| `opentelemetry.resource-attributes.service.name` | 트레이스 서비스명 | Tempo |
| Alloy `job_name = "drive-api"` | 메트릭 라벨 `job` | 대시보드·알림 쿼리 |

### Actuator URL (8091 만)

| URL | 응답 |
|---|---|
| `/actuator` | 열린 엔드포인트 목록 |
| `/actuator/health` | `{"status":"UP"}` |
| `/actuator/info` | 빌드 정보(`build-info.properties`) |
| `/actuator/prometheus` | Alloy 가 가져가는 지표 텍스트 |

### `DriveAPI/.env` 의 관측 관련 값 (모드 A)

`DRIVE_MANAGEMENT_PORT=8091`, `DRIVE_OTLP_TRACING_EXPORT_ENABLED=true`, `logging.config=classpath:log/log4j2-server.yml`. 실행 구성의 `-Dspring.config.import=optional:file:.env[.properties]` 로 읽힘(Working directory = `DriveAPI/`).

### 로그 (`log/log4j2-server.yml`)

- 패턴에 `[%X{traceId},%X{spanId}]` → Alloy 가 정규식으로 뽑아 Loki 메타데이터로.
- 파일: `${LOG_PATH:-/log/drive/backend/drive-server}.log`(전체), `error/drive-error.log`(ERROR 만). 200MB 또는 하루마다 굴림, 10개.
- Windows 에서 `/log/...` 는 `C:/log/...` → `DRIVE_LOG_DIR=C:/log/drive`.
- Alloy 는 `drive-server.log` 만 읽음(굴린 파일은 중복, error 는 부분집합).

---

## 5. Alloy (`alloy/config.alloy`)

| 컴포넌트 | 신호 | 하는 일 |
|---|---|---|
| `prometheus.scrape "drive"` | 메트릭 | `DRIVE_METRICS_TARGETS` 의 `/actuator/prometheus` 15초마다, `job="drive-api"`, 라벨 `env` |
| `prometheus.exporter.unix` + relabel | 메트릭 | OS 지표(Docker Desktop 에서는 도커 VM 값), `job="node"` |
| `prometheus.exporter.postgres "drive"` | 메트릭 | `DRIVE_PG_DSN`. 연결·트랜잭션·캐시·잠금. `wal` 은 권한 문제로 제외. `pg-queries.yaml` 실행. 30초, `job="postgres"` |
| `prometheus.exporter.postgres "loadtest"`, `"loadtest_statements"` | 메트릭 | 부하 DB. 두 번째는 `pg-statements.yaml` 상위 20 쿼리. `job="postgres-loadtest"` |
| `otelcol.receiver.otlp` | 트레이스 | 4317/4318 수신 → 트레이스는 tail_sampling, 메트릭·로그는 batch |
| `otelcol.processor.tail_sampling` | 트레이스 | `decision_wait=30s` 동안 한 요청의 span 을 모아 하나라도 맞으면 남김: `errors`(span ERROR), `server_errors`(`outcome=SERVER_ERROR`), `slow`(1초 이상이면서 `/parts/`·`/download`·`/view` 아님), `sample`(나머지 10%) |
| `otelcol.processor.batch` → `exporter.otlp "tempo"` | 트레이스 | `tempo:4317` |
| `local.file_match` + `loki.source.file` | 로그 | `/var/log/drive/**/drive-server.log`, 라벨 `service_name="drive-api"`, `env` |
| `loki.process "drive"` | 로그 | multiline(날짜로 시작하는 줄이 새 로그), level → 라벨, trace_id·span_id → 구조화 메타데이터 |
| `prometheus.remote_write`, `loki.write` | 출구 | `prometheus:9090/api/v1/write`(exemplar 포함), `loki:3100/loki/api/v1/push` |

### `alloy/pg-queries.yaml` — 앱 테이블에서 읽는 지표

| 지표 | 뜻 |
|---|---|
| `drive_upload_active_sessions{status}` | PENDING·COMPLETING 세션 수 |
| `drive_upload_active_reserved_bytes{status}` | 진행 중 세션이 잡은 용량 |
| `drive_upload_active_oldest_idle_seconds{status}` | 가장 오래 활동 없는 세션 경과(TTL 1시간) |
| `drive_upload_active_oldest_completing_seconds{status}` | 가장 오래된 COMPLETING 경과(제한 30분) |
| `drive_pg_lock_wait_sessions`, `_max_seconds` | 잠금 대기 세션 수·최대 |
| `drive_storage_deletion_{pending,deferred,overdue,overdue_max_seconds,exhausted}{action}` | `tb_storage_deletion` 대기열(DELETE·ABORT_MULTIPART). overdue = 15분 넘김, exhausted = 10회 소진 |
| `drive_pg_statements_{calls,seconds,mean_seconds,rows}` | (부하 DB 만) pg_stat_statements 상위 20 |

---

## 6. Prometheus · Loki · Tempo

| 파일 | 설정 | 뜻 |
|---|---|---|
| `prometheus/prometheus.yml` | job `prometheus` 하나 | 자기 자신만 직접 수집 |
| `loki/config.yaml` | 단일 실행, filesystem, tsdb v13 | 라벨 인덱스 + chunks 파일 |
| | `retention_period: 336h` + compactor | 14일 보관 |
| | `allow_structured_metadata: true` | trace_id 를 라벨이 아닌 메타데이터로 |
| | `pattern_ingester.enabled` | Logs Drilldown 패턴 보기 |
| `tempo/config.yaml` | `distributor.receivers.otlp` 4317/4318 | Alloy 트레이스 수신 |
| | `ingester.max_block_duration: 5m` | 5분마다 파일로 |
| | `compactor.block_retention: 336h` | 14일 |
| | `metrics_generator` processors `service-graphs`, `local-blocks` | 서비스 그래프 → Prometheus, TraceQL 메트릭. span-metrics 는 샘플링 탓에 치우쳐 제외(요청 수·응답 시간은 Micrometer 값을 본다) |

---

## 7. Grafana (`grafana/provisioning/`, 바꾸면 `docker compose restart grafana`)

- **데이터소스 링크**: Prometheus `exemplarTraceIdDestinations` → Tempo(그래프 점 클릭 → 트레이스) · Loki `derivedFields` trace_id → Tempo · Tempo `tracesToLogsV2` → Loki(±5분), `serviceMap`, `nodeGraph`.
- **대시보드**(폴더 Drive): **Drive 개요**(요청 수·p95·5xx·JVM·CPU·Hikari·로그·PG·S3 풀·다운로드 스트림), **k6 부하 테스트**(위 k6 결과, 아래 같은 시간대 서버, `$testid` 로 실행 선택).
- **알림 14개**(1분 평가, contact point 아직 없음):

| 규칙 | for | 조건 |
|---|---|---|
| Drive 메트릭 수집 끊김 (critical) | 2m | `up{job="drive-api"}`=0 |
| PostgreSQL 접속 불가 (critical) | 2m | `pg_up=0` |
| 5xx 비율 5% 초과 | 5m | |
| 응답 시간 p95 3초 초과 | 10m | |
| SLO: 1초 안 성공 비율 99% 미만 | 15m | 30분, `le="1.0"` 이면서 5xx 아님 |
| DB 커넥션 대기 | 5m | `hikaricp_connections_pending>0` |
| S3 커넥션 대기 | 5m | 풀 대기 > 0 (초과 시 503 STORAGE_009) |
| JVM 힙 부족(GC 후 90%) | 10m | |
| 호스트 메모리 90% | 10m | |
| 스토리지 정리 밀림 | 15m | `drive_storage_deletion_overdue>0` |
| 스토리지 정리 포기 행 | 5m | `exhausted>0` |
| 업로드 세션 정리 안 됨 | 10m | PENDING TTL 초과 |
| 업로드 완료 처리 시간 초과 | 0s | 최근 15분 completing_timeout 실패 |
| 업로드 완료 처리 정리 안 됨 | 10m | COMPLETING 30분 초과 |

### 보는 법

1. http://localhost:3300 — `admin` / `admin`(`.env` 에 `GRAFANA_ADMIN_PASSWORD` 없음).
2. Dashboards › Drive › **Drive 개요**, Last 15 minutes, 새로고침 10s.
3. p95 그래프의 **점** → Tempo 트레이스 → "Logs for this span" 으로 같은 요청 로그.
4. Explore 쿼리 예:

| 보고 싶은 것 | 데이터소스 | 쿼리 |
|---|---|---|
| 앱 수집 여부 | Prometheus | `up{job="drive-api"}` |
| 업로드 API 요청 | Prometheus | `sum by (uri,status) (rate(http_server_requests_seconds_count{uri=~"/api/v1/(files\|uploads).*"}[1m]))` |
| 진행 중 업로드 세션 | Prometheus | `drive_upload_active_sessions{job="postgres"}` |
| 다운로드 스트림 사용 중 | Prometheus | `executor_active_threads{name="driveStreamExecutor"}` |
| 업로드 트레이스 | Tempo | `{ span.http.url =~ ".*uploads.*" }` (정상은 10%, 30초 뒤) |
| 에러 로그 | Loki | `{service_name="drive-api", level="ERROR"}` |
| 특정 요청 로그 | Loki | `{service_name="drive-api"} \| trace_id="<32자리>"` |

---

## 8. Micrometer 에 프로젝트가 직접 넣은 것

### 직접 등록한 지표

| Prometheus 이름 | 종류·태그 | 뜻 | 코드 |
|---|---|---|---|
| `drive_upload_session_size_bytes_{count,sum,max}` | Summary · `kind`=chunk\|server | 세션 시작 시 선언 크기(커밋 후). count rate = 세션 시작/초 | `upload/session/metrics/UploadSessionMetrics.java:29` |
| `drive_upload_session_ended_total` | Counter · `result`, `reason` | completed/none 또는 failed + 사유(validation, quota_exceeded, content_mismatch, expired, canceled, commit_error, no_such_upload, storage_error, permission_denied, target_deleted, completing_timeout). 조합 전부 0 으로 미리 등록 | `UploadSessionMetrics.java:30`, 호출 `DefaultUploadSessionRepository.java:142/158/194` |
| `drive_download_stream_rejected_total` | Counter | `driveStreamExecutor`(최대 100, 큐 0) 가득 → 503 | `global/config/StreamingAsyncConfig.java:36` |
| `drive_s3_http_acquire_seconds_*` | Timer(히스토그램, 최대 30s) | 동기 S3Client 풀에서 연결 빌리는 시간 | `storage/observation/S3HttpPoolMetrics.java` |
| `drive_s3_http_acquire_timeout_total` | Counter | 풀 대기 초과 = 503(STORAGE_009) 수 | 〃 |
| `drive_s3_http_leased_*` | Summary | 빌린 연결 수 | 〃 |
| `drive_s3_http_pending_*` | Summary | 연결 대기 요청 수(패널은 `max_over_time(…_max)`) | 〃 |
| `drive_s3_http_max_connections` | Gauge | `storage.http.max-connections` 설정값 | 〃 (등록 `S3ClientConfig.java:89-115`, 동기 클라이언트만, CEPH·S3·RUSTFS) |
| `aws_s3_client_seconds_*`, `aws_s3_client_active_seconds_*` | Observation → Timer + LongTaskTimer · `rpc.method`, `aws.s3.bucket`, `http.status_code`, `error` | S3 API 1회(재시도 포함). 404 는 오류 아님, GetObject 는 헤더까지. span("s3 put-object" 등)도 생성, object key 미기록 | `storage/observation/S3ObservationInterceptor.java:33`, `drive.observability.s3-tracing.enabled`(기본 true) |

### 자동이지만 프로젝트 설정으로 의미가 생기는 것

- `executor_*{name="driveStreamExecutor"}` — Boot 가 `ThreadPoolTaskExecutor` 빈마다 자동 생성. 다운로드 본문 전송 풀이라 "스트림 사용 중/최대". 다른 풀: `getAsyncExecutor`, `governanceAuditExecutor`, `cephTokenExecutor`. 모두 `ContextPropagatingTaskDecorator` 로 traceId·MDC 전달.
- `jdbc_query_*` 와 SQL span — `global/config/JdbcObservationConfig.java:27-47` 이 DataSource 를 datasource-proxy 로 감쌈. 쿼리 span 만, 바인드 값 미기록(`drive.observability.jdbc-tracing.enabled`).

### 계측 동작을 바꾼 곳

- `global/config/ObservationConfig.java:20-28` — `/actuator` 요청은 관찰 안 함(scrape 가 지표·트레이스에 안 섞임).
- `global/filter/TenantFilter.java:156,165` — 요청 span 에 `tenant.seq`(트레이스만, 메트릭 태그 아님).
- 안 쓰는 것: `@Timed`/`@Observed`, MeterFilter, 커스텀 Sampler. 진행 중 세션 수·정리 대기열은 앱이 아니라 Alloy `pg-queries.yaml`.

---

## 9. k6

### 실행 형태

```
docker compose --profile loadtest run --rm  [-e 컨테이너환경변수]  k6  run  [-e 스크립트변수]  /scripts/<파일>.js
```

- 스크립트 경로는 **반드시** 마지막 인자. `observability/k6/read.js` → `/scripts/read.js`, `k6/volume/mixed.js` → `/scripts/volume/mixed.js`.
- k6 는 명령줄 도구. 결과는 터미널 요약 + Grafana › Drive › **k6 부하 테스트**.
- 실시간 웹 화면(선택): `-p 5665:5665 -e K6_WEB_DASHBOARD=true -e K6_WEB_DASHBOARD_HOST=0.0.0.0` 를 `run --rm` 뒤에 붙이면 http://localhost:5665 (테스트 끝나면 닫힘).
- Git Bash 에서는 앞에 `MSYS_NO_PATHCONV=1`.

### 혼자 돌리는 순서 (PowerShell, `observability` 폴더)

```powershell
cd C:\intelliJProject\klmin-drive\observability
docker compose up -d
docker compose --profile loadtest up -d loadtest-db loadtest-storage
# IntelliJ "DriveAPI (loadtest)" 실행 — 서비스 8082·관리 8091. 평소 DriveAPI(8080)와 8082 쓰는 다른 프로그램은 끈다
# 처음 한 번
Get-Content k6\seed-admin.sql | docker compose exec -T loadtest-db psql -U drive_load -d drive_load
docker compose --profile loadtest run --rm k6 run /scripts/seed.js        # 사용자 18명, 파일 3,780개
# 부하
docker compose --profile loadtest run --rm k6 run -e PROFILE=smoke /scripts/read.js
docker compose --profile loadtest run --rm k6 run -e PROFILE=smoke /scripts/upload.js
docker compose --profile loadtest run --rm k6 run -e PROFILE=smoke /scripts/download.js
```

### 스크립트

| 파일 | 내용 |
|---|---|
| `seed.js` | 데이터 만들기 |
| `read.js` | 둘러보기(목록·폴더·상세·검색·최근·즐겨찾기·트리·용량) |
| `upload.js` | 작은 파일(`POST /api/v1/files`) + 큰 파일(청크, 16MB 파트 동시 4, 일부 취소) |
| `write.js` | 폴더 만들기·복사·이름 변경·이동·휴지통·복원. `HOT_USER=1` 잠금 경합 |
| `download.js` | 작은 파일·큰 파일·zip |
| `download-concurrency.js` | 동시 다운로드 수 고정(`VUS`) — 스트림 상한 100·S3 풀, 503 따로 셈 |
| `abuse-parts.js` | 파트를 한꺼번에 PUT 하는 비정상 클라이언트, 피해 사용자 p95 |
| `volume/*` | 대량 데이터·큰 폴더 삭제·휴지통 비우기·공유 폴더 동시 쓰기·혼합 부하 |

### 옵션

| 옵션 | 뜻 |
|---|---|
| `-e PROFILE=smoke\|stress` | 단계 선택(기본·smoke·stress) |
| `-e THINK=0` | 생각 시간 없이 |
| `-e TESTID=이름` | Grafana `$testid` 로 실행 선택 |
| `SMALL_KB` / `CHUNK_MB` / `CANCEL_RATE` | upload.js 크기(512KB·40MB)·취소 비율(0.1) |
| `-e` 위치 | `k6 run` 뒤 = 스크립트 변수(`__ENV`), `run --rm` 바로 뒤 = 컨테이너 환경변수. 둘 다 스크립트에서 읽힘 |

기준(thresholds): HTTP 실패 1% 미만, 목록 p95 1초 미만, 체크 99% 이상.

### 남은 측정 (LOCAL-TEST-GUIDE §3-3·3-4)

```powershell
foreach ($v in 50,100,150,200) { docker compose --profile loadtest run --rm -e VUS=$v -e TESTID=dlc-after-vu$v k6 run /scripts/download-concurrency.js; Start-Sleep 15 }
docker compose --profile loadtest run --rm -e TESTID=after-upload k6 run -e PROFILE=stress -e THINK=0 /scripts/upload.js
docker compose --profile loadtest run --rm -e TESTID=after-abuse k6 run /scripts/abuse-parts.js
```

기대: `dlc_failed` 0, 100 초과 단계만 503(`drive_download_stream_rejected_total`), 스트림 최대 100, S3 풀 대기 0, Tomcat 바쁜 스레드 낮게. abuse-parts 중 Tomcat 스레드는 아직 200 가까이 찰 것(파트 동시 상한 미구현).

---

## 10. 화면 확인 체크리스트 (모드 A — 다음에 이어서)

준비: IntelliJ **DriveAPI**(8080) + `DriveFront` `npm run dev`(3000), http://localhost:3000 `admin` / `LoadTest!2026`. 다른 PC 에서 새로 하면 앱 첫 기동 후:

```powershell
Get-Content C:\intelliJProject\klmin-drive\observability\k6\seed-admin.sql | docker exec -i drive-local-postgres-1 psql -U drive -d drive
```

### 업로드

| # | 해 볼 것 | 기대 | 확인 |
|---|---|---|---|
| U1 | 32MB 미만 | `POST /api/v1/files` 한 요청 | 요청 수 패널, `drive_upload_session_size_bytes_count{kind="server"}` |
| U2 | 32MB 이상(200MB) | 청크 API, 16MB 파트 4개씩 → complete | `kind="chunk"` 증가, `ended_total{result="completed"}`, 진행 중 세션 올랐다 0 |
| U3 | 1GB 올리다 새로고침 → 같은 파일 | 빠진 파트만(이어 올리기) | Network: `GET /uploads/{id}` 뒤 일부 PUT |
| U4 | 큰 파일 취소 | 세션 취소, 용량 반환 | `reason="canceled"`, 진행 중 0, `tb_storage_deletion` ABORT_MULTIPART |
| U5 | 올리는 중 대상 폴더 휴지통 | 409 `UPLOAD_001`, FAILED | `reason="target_deleted"` |
| U6 | 실행 파일 확장자만 변경 | 415 `UPLOAD_010` | 오류 응답 |
| U7 | 용량보다 큰 파일 | 시작 단계 용량 초과 안내 | 사용량 막대 확정값, 진행 중 `uploadingByte` |
| U8 | 잠긴 파일에 새 버전 | 423 잠금 안내 | — |

모드 A 는 배치 스케줄러 꺼짐 → 정리 대기열 미처리, 15분 뒤 "스토리지 정리 밀림" 알림이 정상.

### 다운로드

| # | 해 볼 것 | 기대 | 확인 |
|---|---|---|---|
| D1 | 작은 파일 | 바로 받아짐 | `/download` 처리 시간 |
| D2 | 1GB 이상 | 끝까지(#64 이전엔 멈춤) | `executor_active_threads{name="driveStreamExecutor"}` 받는 동안 1 |
| D3 | 받다가 브라우저 취소 | 서버 스트림 정리 | 0 으로 복귀, ERROR 폭주 없음 |
| D4 | 폴더 zip | 열림 | — |
| D5 | 미리보기 | 열림 | `/view` |

시작 전 기준값(2026-11-23 17:0x): 세션 시작 chunk 0 / server 0, 종료 0, 진행 중 0, 스트림 0.

확인 결과는 `docs/todo/next-session.md` §2-3 에 반영(worktree → PR → 리뷰 → merge).

---

## 11. k6 지난 결과 요약 (README 원본 참고)

한 PC(Core Ultra 5 225F, 32GB)에 전부 → 절대 수치 말고 **전후 비교·병목 위치**만.

- **장애 주입(DB 45초 중단)**: tail 샘플링 30초에서 5xx 27/27, 1초 초과 197/197 남음, Tempo 로 가는 span 87% 감소.
- **결과 1 데이터량**: 110만 건에서 검색 1.42s·2.01s → 인덱스(#72) 후 236ms·128ms. 커서 페이징이라 50페이지도 105ms.
- **결과 2 트리**: 재귀 쿼리가 DB 시간 44%(295ms) → V9 인덱스 후 5ms.
- **결과 3 휴지통 비우기(5만 건)**: 잠금 대기 최대 2.9~4.2s → 묶음 커밋(#74) 0.20s, #75 0.37s, 같은 사용자 쓰기 최대 0.68s.
- **결과 4 공유 폴더 동시 쓰기**: 자기 드라이브 초당 약 720 vs 한 공유 폴더 약 137(소유자 사용자 행 잠금이 병목).
- **결과 5 힙**: 4GB·512MB·256MB 모두 실패 0, 힙 최대 300MB 대(스트리밍). 128MB 기동 실패. 컨테이너 메모리 = 힙 + 600MB 이상.
- **결과 6 단계별 부하**: 200명(340 RPS)까지 비례, 300명부터 꺾임(Tomcat 200 상한, Hikari 대기 18).
- **결과 7 S3 풀(수정 전)**: 동시 200 다운로드에서 17.7% 실패(SDK 기본 풀 50). 수정: 풀 200, 대기 2초 후 503, 스트림 상한 100, `drive_s3_http_*` 지표. **수정 후 재측정이 남은 일.**
