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

| 파일 | 내용 |
|---|---|
| `lib/drive.js` | 로그인(쿠키 + `X-Drive-Role` 헤더)·응답 꺼내기·multipart |
| `seed.js` | 데이터 만들기 — 사용자 18명 × (루트 파일 10 + 폴더 10 × 파일 20) = 파일 3,780·폴더 180, 10개마다 즐겨찾기 |
| `read.js` | 읽기 시나리오 — 내 드라이브 목록 → 폴더 열기 → 파일 상세 → 검색 → 최근·즐겨찾기·트리·용량 |
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

## 보는 곳

Grafana http://localhost:3300 › Drive › **k6 부하 테스트**

- 위: k6 가 본 결과 — VU 수, API 별 초당 요청·p95, 실패율, 상태 코드
- 아래: 같은 시간대 서버 — WAS p95(점 → 트레이스), Hikari 커넥션 사용·대기, 부하 테스트 DB 세션·트랜잭션(`job="postgres-loadtest"`), CPU, 힙·GC, WARN·ERROR 로그
- 읽는 법: VU 를 올려도 초당 요청이 더 안 오르고 p95 만 오르면 한계. 그때 Hikari 대기가 0 보다 크면 DB 풀·쿼리, CPU 가 차면 WAS, DB 세션 active 가 많으면 DB 쪽.
  느린 요청은 WAS p95 그래프의 점을 눌러 트레이스에서 어느 SQL 인지 본다.

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
- 트레이스 샘플링은 실행 구성에서 0.1(10%) — 1.0 이면 Tempo 디스크가 금방 찬다.
