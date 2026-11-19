# 관측성 동작 원리 — 무엇이 어디서 만들어져 어디로 가는가

`README.md` 가 "띄우고 쓰는 법"이라면, 이 문서는 **각 구성 요소의 역할, 설정 하나하나의 의미, 실제로 데이터가 흐르는 길**을 정리한다.
처음 보는 사람이 "왜 이 의존성이 있지?", "이 설정을 빼면 무엇이 사라지지?"에 답할 수 있게 쓰는 것이 목적이다.

## 1. 한눈에 보기

```
┌──────────────── Drive 앱 (Spring Boot) ────────────────┐        ┌─────────────── Docker: observability/ ───────────────┐
│                                                         │        │                                                       │
│ Micrometer 메트릭 ─▶ /actuator/prometheus (:8091) ◀────┼─pull───┤ Alloy ─ prometheus.scrape ─┐                         │
│                                                         │        │                             ├─push─▶ Prometheus ◀──┐  │
│ Observation ─▶ OTel span ─▶ OTLP exporter ─────────────┼─push──▶│ Alloy :4318 ─ tail 샘플링 ─┼─push─▶ Tempo ───────┼──┤ Grafana
│   (HTTP·SQL·S3·외부 HTTP·@Async)                        │        │                             │        └span 메트릭▶ │  │ (조회)
│                                                         │        │                             │                     │  │
│ log4j2 ─▶ C:/log/drive/backend/drive-server.log ◀──────┼─read───┤ Alloy ─ loki.source.file ──┴─push─▶ Loki ◀────────┘  │
│   (줄마다 [traceId,spanId])                             │        │                                                       │
└─────────────────────────────────────────────────────────┘        │ Alloy 내장 exporter: PostgreSQL·호스트(OS) ─▶ Prometheus │
                                                                   └───────────────────────────────────────────────────────┘
```

| 데이터 | 앱이 하는 일 | 가져가는/받는 쪽 | 저장 | 방향 |
|---|---|---|---|---|
| **메트릭** | Prometheus 형식으로 `/actuator/prometheus` 에 **내놓는다** | Alloy 가 15초마다 가져감 | Prometheus | pull |
| **트레이스** | OTLP 로 Alloy 에 **보낸다** | Alloy 가 받아 tail 샘플링 | Tempo | push |
| **로그** | 파일에 **쓴다** | Alloy 가 파일을 따라 읽음 | Loki | 파일 |

- OTel(OpenTelemetry)로 전송하는 것은 **트레이스뿐**이다. 메트릭·로그는 OTel 을 거치지 않는다.
- 앱이 직접 통신하는 관측 컴포넌트는 **Alloy 하나**다. Prometheus·Loki·Tempo 주소는 앱이 모른다.
- Grafana 는 세 저장소를 **조회만** 한다. 데이터를 직접 받지 않는다.

## 2. 앱 쪽 — 의존성(pom.xml)

| 의존성 | 하는 일 | 없으면 |
|---|---|---|
| `spring-boot-starter-actuator` | `/actuator/*` 엔드포인트, 관측 자동 설정. **Micrometer** 를 함께 가져온다 | 관측 전체가 없다 |
| Micrometer (actuator 에 포함) | 메트릭·Observation 을 기록하는 공통 API. HTTP 요청·SQL·S3 등은 모두 여기에 기록된다 | — |
| `micrometer-registry-prometheus` | Micrometer 메트릭을 **Prometheus 텍스트 형식으로 변환**해 `/actuator/prometheus` 로 내놓는다. 저장하지 않는다 | `/actuator/prometheus` 가 없어 Prometheus 가 빈다 |
| `micrometer-tracing-bridge-otel` | Observation 을 **OTel span 으로 만든다**. traceId·spanId 생성, 로그 MDC 에 traceId 넣기, 외부 호출에 `traceparent` 헤더 붙이기 | 트레이스가 없고 로그에 traceId 도 없다 |
| `opentelemetry-exporter-otlp` | 만들어진 span 을 **OTLP 로 Alloy 에 보낸다** (bridge 는 만들기만 하고 보내지 않는다) | span 은 생기지만 앱 밖으로 나가지 않는다 |
| `datasource-proxy` | DataSource 를 감싸 **SQL 실행을 가로챈다**(이벤트만 발생) | SQL 구간이 없다 |
| `datasource-micrometer` | 가로챈 SQL 이벤트를 **Micrometer Observation 으로 바꾼다** (`DataSourceObservationListener`) | 〃 |

`bridge-otel` + `exporter-otlp` 는 둘 다 있어야 트레이스가 Tempo 까지 간다.
`datasource-proxy` + `datasource-micrometer` 도 한 쌍이다. proxy 가 가로채고, micrometer 가 관측으로 만든다.

## 3. 앱 쪽 — 설정(application.yml)

값의 우선순위: **명령줄 인자 > OS 환경변수 > `DriveAPI/.env` > `application.yml` 기본값**.
yml 의 `${이름:기본값}` 은 "그 이름의 값이 있으면 그것, 없으면 기본값"이다.
`DriveAPI/.env` 는 실행 구성의 `-Dspring.config.import=optional:file:.env[.properties]` 로 읽히며, yml 과 같은 키(예: `logging.config`)를 적으면 yml 값을 덮어쓴다.

| 설정 | 기본값 | 의미 |
|---|---|---|
| `logging.config` | `log4j2-server.yml` | 로그 설정 파일. server = 콘솔 + 파일, local = 콘솔만. **Loki 에 로그를 넣으려면 server** (Alloy 는 파일을 읽는다) |
| `management.server.port` | `8091` (`DRIVE_MANAGEMENT_PORT`) | Actuator 전용 포트. 서비스 포트와 분리해 외부에 열지 않는다. Alloy 가 여기서 메트릭을 가져간다 |
| `management.endpoints.web.exposure.include` | `health,info,prometheus` | HTTP 로 공개할 Actuator 엔드포인트. 안 쓰면 Boot 기본은 health 만 |
| `management.health.redis.enabled` | `false` | Redis 는 쓰지 않으므로(starter 만 남음) health 가 DOWN 이 되지 않게 끈다 |
| `management.metrics.tags.application` | `drive-api` | 모든 메트릭에 붙는 공통 라벨 `application="drive-api"` |
| `management.metrics.distribution.percentiles-histogram.http.server.requests` | `true` | 응답 시간을 구간별 개수(histogram)로 기록. 이게 있어야 **p95·p99** 계산과 그래프 점(exemplar) → 트레이스 이동이 된다 |
| `management.observations.enable.spring.security` | `false` | 보안 **필터 동작은 그대로**, 필터 단계마다 생기는 trace 구간만 끈다(요청마다 10여 개라 트레이스를 가린다) |
| `management.tracing.sampling.probability` | `1.0` (`DRIVE_TRACING_SAMPLING_PROBABILITY`) | 앱이 트레이스를 만들어 보낼 요청 비율. 1.0 = 전부 보내고 남길지는 Alloy 가 정한다. 안 쓰면 Boot 기본 0.1 |
| `management.otlp.tracing.endpoint` | `http://localhost:4318/v1/traces` | 트레이스를 보낼 주소(OTLP HTTP). 지금은 Alloy. Tempo·Jaeger·SaaS 로 바꿔도 키는 같다 |
| `management.otlp.tracing.export.enabled` | `false` (`DRIVE_OTLP_TRACING_EXPORT_ENABLED`) | 트레이스 전송 스위치. 받을 곳(Alloy)이 없는데 켜면 앱은 정상이지만 전송 실패 WARN 이 반복되고 span 은 버려진다. 꺼도 traceId 는 생성돼 로그에 찍힌다 |
| `management.opentelemetry.resource-attributes.service.name` | `drive-api` | Tempo 에 보이는 서비스 이름 |
| `management.opentelemetry.resource-attributes.deployment.environment` | `local` (`DRIVE_ENV`) | 트레이스에 붙는 환경 이름(local·dev·prod) |
| `decorator.datasource.enabled` | `false` | p6spy SQL 콘솔 로그. 관측(SQL 구간)과 별개 |
| `drive.observability.jdbc-tracing.enabled` | `true` (코드 기본) | SQL 구간 생성. 끌 때만 `false` |
| `drive.observability.s3-tracing.enabled` | `true` (코드 기본) | S3 호출 구간·메트릭 생성. 끌 때만 `false` |

로그 패턴(`log4j2-*.yml`)의 `[%X{traceId},%X{spanId}]` 는 **트레이스를 만드는 데 필요하지 않다.**
로그 ↔ 트레이스를 잇는 데 쓴다(Loki 로그 줄 → Tempo, Tempo → 같은 traceId 로그). 값은 bridge-otel 이 MDC 에 자동으로 넣는다.

## 4. 앱 쪽 — 관측 코드

| 클래스 | 하는 일 |
|---|---|
| `global/config/ObservationConfig` | `/actuator/**` 요청은 관측하지 않는다(15초마다 오는 수집 요청이 트레이스를 덮지 않게) |
| `global/config/JdbcObservationConfig` | DataSource 를 proxy 로 감싸 **SQL 실행 구간**을 만든다. 쿼리 실행만, 바인딩 값은 남기지 않음(`?` 그대로) |
| `storage/observation/S3ObservationInterceptor` | AWS SDK 호출마다 **`s3 put-object` 같은 구간**과 `aws.s3.client` 메트릭. 객체 키는 남기지 않고 404 는 오류로 보지 않음 |
| `async/config/AsyncConfig` 등 executor | `ContextPropagatingTaskDecorator` — `@Async`·스레드풀 작업이 요청 트레이스 안에 남고 로그 traceId 가 이어진다 |
| `restclient/config/RestClientFactory`, `OfficeTicketClient` | Boot 의 `RestClient.Builder` 를 `clone()` 해서 쓴다 → 외부 HTTP 호출 구간·메트릭, 상대에게 `traceparent` 전달 |

### 요청 하나의 트레이스 예 (업로드)

```
http post /api/v1/files            ← HTTP 서버 관측(Boot 자동)
  ├ query × 11                     ← JdbcObservationConfig
  ├ s3 put-object  PutObject 200   ← S3ObservationInterceptor
  └ query × 3                      ← @Async 후처리(AsyncConfig 의 컨텍스트 전파)
```

## 5. DB 정보는 세 갈래

| # | 무엇 | 누가 만드나 | 저장 | 답하는 질문 |
|---|---|---|---|---|
| ① | **요청별 SQL 구간** | 앱(`datasource-micrometer` + proxy) | Tempo | 이 요청은 어떤 쿼리 때문에 느렸나? |
| ② | **SQL 실행 시간 메트릭** `jdbc_query_seconds_*` | 앱(① 의 Observation 이 타이머도 만든다) | Prometheus | 앱 전체 쿼리 수·시간 추세는? |
| ③ | **커넥션 풀** `hikaricp_connections_*`, `jdbc_connections_*` | 앱(Micrometer 가 Hikari 자동 수집) | Prometheus | 앱이 커넥션을 기다리고 있나? |
| ④ | **DB 서버 지표** `pg_*` (`pg_up`, 연결·락·트랜잭션·캐시 적중률·테이블) | **Alloy 내장 postgres exporter** 가 DB 에 직접 접속(`DRIVE_PG_DSN`) | Prometheus | DB 서버 자체가 바쁜가, 막혔나? |

- ② 는 라벨이 `jdbc_datasource_name`·`error` 뿐이라 SQL 문장별로 나뉘지 않는다(시계열 폭증 없음). 쿼리별로는 ① 트레이스에서 본다.
- Prometheus 는 DB 를 직접 수집하지 않는다. 저장소일 뿐이고 ②③④ 가 Alloy 를 거쳐 들어온다.
- `up{job="drive-api"}` 는 **앱**이 살아 있는지, `pg_up` 은 **DB** 에 접속되는지다.
- 장애 때: ① 에서 느린 쿼리 → 같은 시간 ③ 풀 대기 → ④ DB 락·활성 연결을 함께 본다.

## 6. Docker 쪽 — 구성 요소와 설정

`docker compose` 가 `observability/` 아래 설정 파일을 각 컨테이너에 마운트해서 읽게 한다. `observability/.env` 는 compose 가 자동으로 읽는 변수 파일이다.

| 컨테이너 | 설정 파일 | 하는 일 | 핵심 설정 |
|---|---|---|---|
| **Alloy** | `alloy/config.alloy` | 수집·가공·전송 창구 | 가져오기(앱 메트릭·로그 파일·DB·호스트), 받기(OTLP 4317/4318), tail 샘플링, 보내기(세 저장소) |
| **Prometheus** | `prometheus/prometheus.yml` + compose `command` | 메트릭 저장·조회 | `--web.enable-remote-write-receiver`(Alloy push 수신), `--enable-feature=exemplar-storage`(메트릭 → 트레이스 링크), 보관 `PROMETHEUS_RETENTION`(기본 15d) |
| **Loki** | `loki/config.yaml` | 로그 저장·조회 | `allow_structured_metadata: true`(trace_id 저장), 보관 14일 |
| **Tempo** | `tempo/config.yaml` | 트레이스 저장·조회 | OTLP 수신(4317/4318, 도커 내부), 보관 14일, `metrics_generator` 가 span 메트릭을 Prometheus 로 push |
| **Grafana** | `grafana/provisioning/` | 조회 화면 | 데이터소스 3개와 서로 간 링크, 대시보드 2개, 알림 7개 |
| loadtest-db·storage·k6 | `k6/` | 부하 테스트 전용 | `--profile loadtest` 로만 뜬다 |

### Alloy 파이프라인 (`config.alloy`)

| 구성 요소 | 역할 |
|---|---|
| `prometheus.scrape "drive"` | `DRIVE_METRICS_TARGETS`(기본 `host.docker.internal:8091`)의 `/actuator/prometheus` 를 15초마다 가져감 |
| `prometheus.exporter.unix "host"` + scrape | OS 지표(CPU·메모리·네트워크). Docker Desktop 에서는 도커 VM 값 |
| `prometheus.exporter.postgres "drive"` + scrape | `DRIVE_PG_DSN` 으로 DB 에 접속해 `pg_*` 지표, 30초마다 |
| `prometheus.remote_write "default"` | 위 메트릭을 `http://prometheus:9090/api/v1/write` 로 push |
| `otelcol.receiver.otlp "default"` | 앱 트레이스 수신 `0.0.0.0:4317`(gRPC)·`4318`(HTTP) |
| `otelcol.processor.tail_sampling "default"` | 요청 단위로 남길지 결정(아래) |
| `otelcol.processor.batch` → `otelcol.exporter.otlp "tempo"` | 모아서 `tempo:4317` 로 전송 |
| `local.file_match` → `loki.source.file` → `loki.process` → `loki.write` | `DRIVE_LOG_DIR` 아래 `**/drive-server.log` 를 따라 읽고, 여러 줄 스택을 한 건으로 묶고, `level` 라벨과 `trace_id`·`span_id` 메타데이터를 붙여 Loki 로 push |

### tail 샘플링

앱은 모든 요청의 트레이스를 보내고, Alloy 가 trace 의 첫 span 이후 `decision_wait`(30초) 동안 모은 뒤 하나라도 맞으면 그 요청 전체를 남긴다.

| 정책 | 남기는 요청 |
|---|---|
| `errors` | span 상태 ERROR |
| `server_errors` | 응답 5xx(`outcome=SERVER_ERROR`) |
| `slow` | 1초 이상 |
| `sample` | 나머지 중 traceId 기준 10% |

- 정상 요청은 10% 만 남고, Tempo 에 **30초 넘게 지나서** 보인다.
- 로그·메트릭은 샘플링과 무관하게 전부 남는다. 로그의 traceId 로 Tempo 를 열었는데 없을 수 있다.
- Tempo 자체에는 tail 샘플링이 없다. Alloy(또는 OTel Collector) 같은 수집기가 있어야 한다.
- Alloy 를 여러 대 두면 같은 trace 가 한 대로 모이도록 traceId 기준 분배 단계가 추가로 필요하다.

### 주소와 포트

| 경로 | 주소 | 설명 |
|---|---|---|
| 앱 → Alloy (트레이스) | `localhost:4318` | compose 가 Alloy 컨테이너의 4318 을 PC 에 연다 |
| Alloy → 앱 (메트릭) | `host.docker.internal:8091` | 컨테이너에서 PC 의 앱 접근 |
| Alloy → Tempo | `tempo:4317` | 도커 내부 네트워크, 컨테이너 이름으로. PC 에는 열려 있지 않다 |
| Alloy → Prometheus | `prometheus:9090/api/v1/write` | 〃 |
| Alloy → Loki | `loki:3100/loki/api/v1/push` | 〃 |
| Grafana → 세 저장소 | `prometheus:9090`, `loki:3100`, `tempo:3200` | 〃 |

Alloy 와 Tempo 가 둘 다 4317/4318 인 것은 OTLP 표준 포트라서다. 각자 자기 컨테이너 안에서 듣기 때문에 부딪히지 않는다.

## 7. 실제 구동

### 7.1 스택 띄우기

```powershell
cd observability
copy .env.example .env      # 처음 한 번. DRIVE_PG_DSN 등 수정
docker compose up -d
```

### 7.2 앱 설정 (`DriveAPI/.env`, git 에 올라가지 않음)

```properties
# 트레이스를 Alloy 로 보낸다 (필수)
DRIVE_OTLP_TRACING_EXPORT_ENABLED=true
# 로그를 Loki 로 보내려면 파일로도 남긴다 (콘솔 + C:/log/drive/backend/drive-server.log)
logging.config=classpath:log/log4j2-server.yml
```

`observability/.env` 의 `DRIVE_LOG_DIR` 은 로그 파일의 상위 폴더(`C:/log/drive`)와 맞춘다. 관리 포트(8091)·트레이스 주소는 기본값 그대로면 된다.

### 7.3 앱 실행 후 확인

| 확인 | 방법 |
|---|---|
| 메트릭이 나오는가 | http://localhost:8091/actuator/prometheus 를 연다 |
| Alloy 가 가져가는가 | http://localhost:12345 (Alloy UI, 컴포넌트 상태·연결 그래프) |
| Prometheus 에 들어갔는가 | http://localhost:9090 에서 `up{job="drive-api"}` 가 1 |
| 트레이스 | Grafana → Explore → Tempo (정상 요청은 10%, 30초 후) |
| 로그 | Grafana → Explore → Loki, `{service_name="drive-api"}` |

### 7.4 접속 주소

| 화면 | 주소 | 비고 |
|---|---|---|
| **Grafana** | http://localhost:3300 | `admin` / `observability/.env` 의 `GRAFANA_ADMIN_PASSWORD`(없으면 `admin`). 대시보드 "Drive 개요", "k6 부하 테스트" |
| Prometheus | http://localhost:9090 | 메트릭 직접 조회 |
| Alloy UI | http://localhost:12345 | 파이프라인 상태 |
| Tempo API | http://localhost:3200 | Grafana 를 통해 보는 것이 보통 |
| Loki API | http://localhost:3100 | 〃 |
| 앱 Actuator | http://localhost:8091/actuator/prometheus, `/actuator/health` | |

## 8. 자주 헷갈리는 점

- **메트릭도 OTLP 로 보내는가?** 아니다. Prometheus 형식으로 내놓고 Alloy 가 가져간다. OTLP 로 보내려면 `micrometer-registry-otlp` + `management.otlp.metrics.export.url` 이 필요하지만, Prometheus 를 저장소로 쓰는 한 pull 이 표준이다(앱 생존을 `up` 으로 자동 감지, 브라우저로 바로 확인 가능).
- **pom 의 prometheus 는 저장소인가?** 아니다. 형식 변환 라이브러리다. 저장은 compose 의 Prometheus 서버가 한다.
- **Alloy 없이도 되는가?** 된다. 트레이스는 `endpoint` 를 Tempo 로(Tempo 의 4318 을 다른 PC 포트로 열어야 함), 메트릭은 `prometheus.yml` 에 앱을 수집 대상으로 추가, 로그는 별도 수집기나 앱 appender 가 필요하다. 대신 tail 샘플링과 DB·호스트 내장 exporter 를 잃고, 저장소를 바꿀 때 앱 설정을 고쳐야 한다.
- **Alloy 의 단점?** 관리할 구성 요소가 하나 늘고(멈추면 세 데이터가 함께 끊김, 앱 동작은 무관), tail 샘플링에 메모리를 쓰고(30초 보관), 여러 대로 늘릴 때 분배 단계가 필요하다.
- **로그 파일은 지워지는가?** 하루 또는 200MB 마다 새 파일로 바뀌지만 오래된 파일 자동 삭제는 없다. 로컬에서는 `C:\log\drive` 를 가끔 비운다.
- **trace 가 안 보인다.** `DRIVE_OTLP_TRACING_EXPORT_ENABLED=true` 인지, 30초가 지났는지, 정상 요청이라 10% 에 들지 못했는지 확인한다. 에러·1초 이상 요청은 항상 남는다.
