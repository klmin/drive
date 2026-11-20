# Drive 관측성 스택

Alloy(수집) → Prometheus(메트릭) · Loki(로그) · Tempo(트레이스) → Grafana(화면)

각 의존성·설정의 의미와 데이터가 실제로 흐르는 길은 [HOW-IT-WORKS.md](HOW-IT-WORKS.md) 에 정리했다.

## 구성 요소와 역할

| 구성요소 | 위치 | 역할 |
|---|---|---|
| Micrometer | 앱 안 (라이브러리) | 요청 수·응답 시간·JVM·DB 커넥션 풀 등을 측정. 트레이스(traceId·span)도 만든다 |
| Actuator | 앱 안 (라이브러리) | 측정값을 관리 포트의 `/actuator/prometheus` 로 노출, `/actuator/health` |
| OpenTelemetry SDK | 앱 안 (라이브러리) | Micrometer 가 만든 트레이스를 OTLP 로 Alloy 에 보낸다 |
| Alloy | 컨테이너 | 수집기 — 메트릭 가져오기(scrape)·OTLP 받기·로그 파일 읽기, 가공, 저장소별 전송 |
| Prometheus | 컨테이너 | 메트릭 저장(자체 시계열 DB) |
| Loki | 컨테이너 | 로그 저장(라벨 인덱스 + 로그 덩어리 파일) |
| Tempo | 컨테이너 | 트레이스 저장(traceId 기준 블록 파일) |
| Grafana | 컨테이너 | 세 저장소 조회·대시보드·알림. 메트릭 ↔ 트레이스 ↔ 로그를 서로 링크 |

## 데이터 흐름

```
[Drive 앱]
 ├ Micrometer → Actuator  :8091/actuator/prometheus   ◀── Alloy 가 15초마다 가져감(pull)
 ├ Micrometer Tracing → OTel SDK ── OTLP(push) ──────▶ Alloy :4318
 └ log4j2 → drive-server.log  [traceId,spanId] 포함    ◀── Alloy 가 따라 읽음(tail)

[Alloy] ── remote_write ─▶ Prometheus :9090
        ── push API ─────▶ Loki :3100
        ── OTLP ─────────▶ Tempo :3200 ── (트레이스로 만든 메트릭) ─▶ Prometheus

[Grafana :3300] ── 조회 ─▶ Prometheus · Loki · Tempo
```

OS 지표(CPU·메모리·네트워크)는 Alloy 내장 `prometheus.exporter.unix` 가 수집한다(job="node").
Docker Desktop(Windows·macOS)에서는 도커 VM 의 값이 나온다 — 실제 호스트 값은 리눅스 서버에서.

## 띄우기

```bash
cd observability
cp .env.example .env      # 필요하면 앱 주소·로그 폴더 수정
docker compose up -d
```

| 주소 | 용도 |
|---|---|
| http://localhost:3300 | Grafana (admin / admin) — 대시보드 Drive › Drive 개요 |
| http://localhost:12345 | Alloy 파이프라인 상태 |
| http://localhost:9090 | Prometheus |

## 앱 쪽 설정 (환경변수)

| 변수 | 기본값 | 설명 |
|---|---|---|
| `DRIVE_MANAGEMENT_PORT` | 8091 | Actuator 관리 포트. 서비스 포트와 분리 — 외부에 열지 않는다 |
| `DRIVE_OTLP_TRACING_EXPORT_ENABLED` | false | 트레이스를 Alloy 로 보낼지. 스택이 있는 곳만 true |
| `DRIVE_OTLP_TRACING_ENDPOINT` | http://localhost:4318/v1/traces | Alloy OTLP HTTP 주소 |
| `DRIVE_TRACING_SAMPLING_PROBABILITY` | 1.0 | 앱이 트레이스를 보낼 요청 비율. 전부 보내고 남길지는 Alloy 가 정한다(아래 트레이스 샘플링) |
| `DRIVE_ENV` | local | 트레이스 resource 의 deployment.environment |
| `drive.observability.jdbc-tracing.enabled` | true | SQL 실행 구간(span) 기록 |
| `drive.observability.s3-tracing.enabled` | true | S3 API 호출 구간(span)·`aws.s3.client` 메트릭 기록 |

### 트레이스에 잡히는 구간

요청 1건 = 트레이스 1개(traceId). 아래는 그 요청의 자식 구간으로 남는다.

| 구간 | 이름 예 | 만드는 곳 |
|---|---|---|
| HTTP 요청 | `http post /api/v1/files` | Spring MVC 관측 (`/actuator` 는 `ObservationConfig` 가 제외) |
| SQL | `query` (`jdbc.query[0]` 에 SQL, 바인딩 값 없음) | `JdbcObservationConfig` |
| S3 호출 | `s3 put-object`·`s3 get-object` (버킷·상태코드, 객체 키 없음) | `S3ObservationInterceptor` — 404 는 "객체 없음" 정상 결과라 오류로 표시하지 않는다 |
| 외부 HTTP | `http post` (상대에게 `traceparent` 전달) | `RestClientFactory`·`OfficeTicketClient` 의 `observationRegistry` |
| `@Async` 작업 안의 SQL·S3 | 요청 트레이스 안에 이어진다 | `AsyncConfig` 의 `ContextPropagatingTaskDecorator` (로그 traceId 도 이어진다) |

- 비즈니스 메서드 단위 구간은 없다 — 필요한 곳만 `@Observed` 로 더한다.
- `@Async` 작업 자체는 구간을 만들지 않아, 그 안의 SQL·S3 가 요청 구간 바로 아래에 붙는다(응답 뒤 시각일 수 있다).
- GetObject 구간은 응답 헤더를 받은 시점까지다. 본문 스트림을 읽는 시간은 요청 구간에 포함된다.
- 메트릭: S3 호출은 `aws_s3_client_seconds_*`(operation·status 별), 외부 HTTP 는 `http_client_requests_seconds_*`.

로그를 Loki 로 보내려면 앱이 **파일로** 로그를 남겨야 한다(`log4j2-server.yml`, `LOG_PATH`).
로컬 `.env` 는 콘솔 전용(`log4j2-local.yml`)이라, 쓰려면 `DriveAPI/.env.example` 의 관측성 절을 참고.
`observability/.env` 의 `DRIVE_LOG_DIR` 은 `LOG_PATH` 의 상위 폴더로 맞춘다(그 아래 `**/drive-server.log` 를 읽는다).

## 트레이스 샘플링

앱은 모든 요청의 트레이스를 Alloy 로 보내고(`DRIVE_TRACING_SAMPLING_PROBABILITY=1.0`),
Alloy 의 `otelcol.processor.tail_sampling` 이 요청이 끝난 뒤 **요청(trace) 단위로** 남길지 정한다.

| 정책 | 남기는 요청 |
|---|---|
| errors | span 상태가 ERROR (처리 안 된 예외) |
| server_errors | 응답 5xx (`outcome=SERVER_ERROR`) |
| slow | 1초 이상 걸린 요청 |
| sample | 위에 해당하지 않는 요청 중 10% (traceId 기준) |

- 앱에서 비율을 낮추면(head 샘플링) 요청 시작 때 결과를 모른 채 버리므로 에러 트레이스도 같이 사라진다.
- 버린 요청도 로그(Loki)와 메트릭(Prometheus)은 전부 남는다. 로그의 traceId 로 Tempo 를 열었을 때 트레이스가 없을 수는 있다.
- Alloy 가 트레이스를 `decision_wait`(30초) 동안 메모리에 모았다가 보내므로 Tempo 에 늦게 보인다.
  요청이 이 시간보다 오래 걸리면 결정이 먼저 나서 뒤에 온 에러 span 을 놓칠 수 있다 — DB 커넥션 대기(Hikari 30초)를 고려한 값이다.
- Tempo 가 트레이스로 만드는 span 메트릭은 남은 트레이스 기준이라 치우친다. 요청 수·응답 시간은 Micrometer 메트릭을 본다.
- 비율·기준은 `alloy/config.alloy` 의 `tail_sampling` 정책에서 바꾼다.

## 서버에 둘 때

- WAS 가 여러 대면 **서버마다 Alloy 하나**(로그 파일·OS 지표는 그 서버에서만 읽을 수 있다), 저장소와 Grafana 는 한 곳.
  각 Alloy 의 `prometheus.remote_write`·`loki.write`·`otelcol.exporter.otlp` 주소를 중앙 저장소로 바꾼다.
- 리눅스 서버에서 OS 지표를 정확히 보려면 Alloy 컨테이너에 `/proc`·`/sys`·`/` 를 읽기 전용으로 마운트하고
  `prometheus.exporter.unix` 에 `procfs_path`·`sysfs_path`·`rootfs_path` 를 지정한다.
- 보관 기간: Prometheus 15일(`PROMETHEUS_RETENTION`), Loki·Tempo 14일(각 config). 데이터가 커지면 Loki·Tempo 를 S3(RustFS·Ceph)로.
- Grafana 관리자 비밀번호는 `.env` 로 바꾼다.

## 트레이스에 남는 것 / 빼는 것

- **남긴다**: HTTP 요청(루트), 그 안의 **SQL 실행**(`query` 구간, `jdbc.query[0]` 태그에 SQL 문), 스케줄 작업(`task …`).
  SQL 은 `JdbcObservationConfig` 가 DataSource 를 datasource-proxy 로 감싸서 만든다. **바인딩 값은 남기지 않는다**(SQL 은 `?` 그대로).
  끄기: `drive.observability.jdbc-tracing.enabled=false`
- **뺀다**: `/actuator` 요청(Alloy 수집 요청 — `ObservationConfig`), Spring Security 필터 체인 구간
  (`management.observations.enable.spring.security=false` — 요청마다 여러 개라 트레이스를 가리고, ignoring 경로에서 "unsecured request" 루트를 만든다)
- datasource-micrometer 는 `-spring-boot` 자동 설정을 쓰지 않는다 — p6spy 데코레이터와 빈 이름이 겹쳐 기동 실패.
  또 datasource-proxy 가 클래스패스에 있으면 p6spy 데코레이터가 자기 datasource-proxy 연동도 켜는데(로컬 `decorator.datasource.enabled=true`),
  그쪽이 1.11 API 를 써서 `datasource-proxy` 를 1.11.0 으로 고정했다.

## DB·호스트 지표

Alloy 내장 exporter — 컨테이너 추가 없음. DB 접속 정보는 `observability/.env` 의 `DRIVE_PG_DSN`.
흐름: Alloy 가 PostgreSQL 에 접속해 `pg_stat_*` 를 읽음 → Prometheus 로 보냄 → Grafana 가 Prometheus 를 조회.
(Drive 는 Redis 를 쓰지 않아 Redis 수집은 넣지 않았다)

| job | 내용 |
|---|---|
| `postgres` | 세션 수(상태별)·트랜잭션·캐시 적중률·잠금·DB 크기 (`pg_stat_*` 읽기만) — 운영은 `pg_monitor` 역할만 준 전용 계정 |
| `node` | 호스트 CPU·메모리·부하·네트워크 |

내장 exporter 는 `job="integrations/…"` 를 스스로 붙여서 `discovery.relabel` 로 위 이름으로 바꾼다.

> ⚠️ **사내 172.17.x 서버(공용 dev DB 등)는 도커 기본 브리지 대역(172.17.0.0/16)과 겹친다** — 그대로면 컨테이너가 그 주소를
> 자기 내부(docker0)로 보내 접속이 안 된다(connection timed out).
> - 로컬(Docker Desktop): Settings › Docker Engine 에 `"bip": "10.200.0.1/24"` 추가 후 Apply & restart (11-16 적용·확인).
>   재시작 시 restart 정책 없는 컨테이너는 꺼진 채로 남으니 다시 켠다
> - 서버(리눅스): Alloy 를 `network_mode: host` 로 띄우거나 같은 bip 설정

PostgreSQL 수집기는 앱 계정으로 되는 것만 켠다(`enabled_collectors`) — `wal` 은 `pg_monitor` 권한이 필요하다.

앱 테이블에서 읽는 "지금 상태" 지표는 커스텀 쿼리로 같이 모은다(`alloy/pg-queries.yaml`, 두 PG exporter 모두):
`drive_upload_active_*{status}`(진행 중 업로드 세션 수·잡아 둔 용량·가장 오래 멈춘 시간), `drive_storage_deletion_*{action}`(스토리지 정리 대기열·재시도 중·가장 오래된 행).
앱이 DB 를 조회해 Gauge 로 내면 수집 때마다 SQL 트레이스가 생기고 WAS 가 여럿이면 같은 값이 겹치므로, DB 상태는 exporter 가 센다.
전용 계정이면 `tb_upload_session`·`tb_storage_deletion` SELECT 권한이 필요하다(없으면 이 쿼리만 실패하고 나머지 PG 지표는 나온다).

## 알림 (Grafana Alerting)

`grafana/provisioning/alerting/drive-rules.yaml` — 파일로 관리(화면에서 수정 불가, 파일을 고치고 Grafana 재시작).

| 규칙 | 조건 |
|---|---|
| Drive 메트릭 수집 끊김 | `up{job="drive-api"}` < 1 이 2분 (대상이 사라져도 발생) |
| PostgreSQL 접속 불가 | `pg_up` < 1 이 2분 |
| Drive 5xx 비율 5% 초과 | 5분 비율 > 5% 가 5분 |
| 응답 시간 p95 3초 초과 | 전체 p95 > 3s 가 10분 (파일 업로드·다운로드가 많으면 기준 조정) |
| DB 커넥션 대기 발생 | `hikaricp_connections_pending` > 0 이 5분 |
| JVM 힙 부족 | GC 후 남은 객체 / 최대 힙 > 90% 가 10분 |
| 호스트 메모리 90% 초과 | 10분 |

**받는 곳(contact point)은 아직 없다** — 기본 이메일(SMTP 미설정)이라 아무 데도 안 간다.
Grafana › Alerting › Contact points 에서 Slack·메일을 만들고 Notification policies 기본 정책에 연결한다.

## 부하 테스트 (k6)

`k6/README.md` — 테스트 전용 PG·RustFS·k6 를 compose profile `loadtest` 로 띄우고, IntelliJ 실행 구성 "DriveAPI (loadtest)" 로 앱을 붙인다.
결과는 Grafana "k6 부하 테스트" 대시보드에서 서버 지표와 같이 본다.

## 아직 없는 것

- 알림 받는 곳(contact point) 설정 — 팀 채널이 정해지면
- 트레이스 루트가 `query` 하나뿐인 경우가 가끔 있다 — 요청·스케줄 작업 밖(관측 안 되는 스레드)에서 실행된 SQL
