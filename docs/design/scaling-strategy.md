# 분산 환경·대용량 트래픽·정합성·캐싱 전략

> 작성: 2026-11-27. WAS 여러 대(로드밸런서 뒤) 운영을 전제로, 지금 코드가 어디까지 되어 있고 무엇을 어떤 방식으로 더할지 정한 문서.
> 할 일 목록은 [../todo/scaling.md](../todo/scaling.md). 여기에는 **결정과 이유**만 둔다.

## 1. 지금 상태 (2026-11-27 main `fa6d04f`)

| 영역 | 현재 | 비고 |
|---|---|---|
| 인증 | JWT(access 1h·refresh 7d), 상태 없음 | WAS 간 sticky session 불필요 |
| 세션 저장 | Spring Session JDBC — OAuth2 AS 의 authorize~코드 발급 구간만 | 일반 요청은 세션 안 씀 |
| 스케줄러 | ShedLock(JDBC) | WAS 여러 대에서 같은 작업 한 번만 |
| 동시성 | DB 행 잠금(사용자 행 → 부모 폴더 → 대상 → 하위 순서, as-built §4) | 교착 방지 순서 고정 |
| 로컬 캐시 | Caffeine `HostTenantCache`(TTL 30s), `LicenseService`(매번 DB 버전 비교), OIDC·JWK 캐시 | 무효화는 **그 WAS 만** |
| Redis | 의존성만 있고 코드에서 안 씀(`management.health.redis.enabled=false`) | |
| 요청 수 제한 | **없음** | |
| 업로드 동시 상한 | **없음** — 비정상 클라이언트가 Tomcat 스레드 198/200 점유(k6) | 다운로드는 `drive.download.stream.max-threads=100` |
| 파일 바이트 경로 | 업로드·다운로드 모두 WAS 경유. 복사는 S3 서버측(`CopyObject`·`UploadPartCopy`) | presigned·Range 없음 |

요청 하나가 인증 단계에서 DB 를 여러 번 조회한다: JWT 블랙리스트(`existsByTokenHash`), 사용자(`loadUserByUserSeq`), IP 정책(`findAccessRestrictionStatusConfig` + CIDR·국가 목록). API Key 요청은 매번 `last_used_date` UPDATE 까지 한다. 코드 전체에 `@Cacheable` 이 없다.

## 2. 원칙

### 2-1. 데이터 성격별로 저장 위치를 고른다
DB 는 캐시가 아니라 **원본**이다. 캐시 "몇 겹"이 아니라 값마다 아래 중 하나를 고른다. 로컬 + Redis + DB 3단은 쓰지 않는다(무효화를 두 겹으로 관리해야 하고, 지금 규모에서 얻는 것이 없다).

| 성격 | 위치 | 대상 |
|---|---|---|
| 매 요청 읽고 거의 안 바뀜, **최악 30초 늦어도 됨** | **Caffeine(TTL) + Redis pub/sub 무효화** | 테넌트, IP 정책, 시스템 설정, 사용자 인증 정보 |
| 여러 WAS 가 **같은 값을 정확히** 봐야 함, 자주 바뀜 | **Redis** (+ 필요하면 DB 원본) | JWT 블랙리스트, rate limit 카운터 |
| 계정 상태처럼 관리자 화면·감사 로그와 맞아야 함 | **DB** | 로그인 실패 잠금 |
| 그 WAS 자신의 자원 보호 | **JVM 메모리** | 업로드 동시 상한(세마포어) |

로컬 캐시를 쓰는 이유: 메모리 읽기는 수십 ns, Redis 는 왕복 0.2~1ms + 직렬화다. 요청마다 여러 번 읽는 설정을 Redis 에 두면 "요청 수 × 키 수" 만큼 왕복이 생기고, Redis 장애가 곧 전체 장애가 된다.

### 2-2. "서버 자원 보호"는 로컬, "클라이언트 사용량 제한"은 전역
- 업로드 세마포어는 WAS 의 스레드를 지킨다 → WAS 가 늘면 처리량도 같이 늘어야 맞다 → **JVM 단위**.
- rate limit 은 클라이언트 한 명의 사용량을 제한한다 → WAS 마다 버킷이면 실제 한도가 "설정값 × WAS 대수" 가 되고 오토스케일 때마다 바뀐다 → **Redis 공유**.

### 2-3. Redis 는 선택 구성요소로
설치형 제품이라 Redis 를 필수로 만들면 설치기·운영 부담이 는다. 기능마다 `redis | db | local` 스위치를 두고, Redis 가 없거나 죽어도 서비스가 멈추지 않게 한다.

| 기능 | Redis 장애 시 |
|---|---|
| 블랙리스트 | DB 로 조회(fail-closed 쪽, 보안) |
| rate limit | 통과(fail-open) 또는 로컬 버킷 |
| 캐시 무효화 pub | 로그만 남김 — TTL 30초 안에 반영 |

### 2-4. Kafka 는 쓰지 않는다
설치처마다 Kafka 클러스터를 같이 깔아야 한다. Kafka 가 주는 것(작업 저장·한 번만 처리·장애 시 이어받기)은 **DB 작업 테이블 + ShedLock 스케줄러 + `SKIP LOCKED`** 로 충분하고, 업로드 세션·삭제 대기열이 이미 그 구조다. Kafka 가 이미 있는 환경이면 비동기 작업만 Kafka 로 바꿀 수 있다(블랙리스트·rate limit 은 키-값 저장소가 필요해 대체 불가).

## 3. 결정

### 3-1. 로컬 캐시 + Redis pub/sub 무효화
**구조**
```
CacheInvalidator.invalidate("ip-policy", "3")
  ├─ 내 WAS Caffeine 에서 즉시 삭제           ← 수정한 관리자가 바로 결과를 본다
  └─ 커밋 후(afterCommit) PUBLISH drive:cache-invalidate "ip-policy:3"

기동 시 구독(모든 WAS, RedisMessageListenerContainer)
  └─ 메시지 수신 → payload "종류:키" 를 보고 해당 캐시에서 삭제
```
- **채널은 하나**(`drive:cache-invalidate`). payload 의 "종류" 로 어느 캐시를 비울지 정한다. 캐시 종류마다 채널을 만들지 않는다.
- 구독은 WAS 당 **연결 1개를 계속 열어 두고** Redis 가 메시지를 밀어 준다(push, 폴링 아님). 메시지는 관리자 설정 변경 때만 나가 양이 매우 적다.
- **반드시 커밋 후 발행**한다. 커밋 전에 보내면 다른 WAS 가 아직 옛 값을 읽어 다시 캐시한다(`HostTenantCache.invalidateAll()` 이 `afterCompletion` 에서 한 번 더 비우는 것과 같은 이유).
- pub/sub 는 저장하지 않는다 — 그 순간 끊겨 있던 WAS 는 메시지를 못 받는다. 그래서 **TTL(30초)을 안전망**으로 둔다. 놓치면 안 되는 메시지라면 Redis Streams(`XADD`/`XREADGROUP`) 를 쓰지만 캐시 무효화에는 필요 없다.
- 캐시 키에 **반드시 tenantSeq** 를 넣는다(AGENTS.md P0).
- Redis 가 없는 설치처: 무효화 신호 없이 TTL 만으로 동작(지금 `HostTenantCache` 와 같다). PostgreSQL `LISTEN/NOTIFY` 도 같은 역할을 할 수 있다.

**대상**

| 캐시 | 처리 |
|---|---|
| `HostTenantCache` | 그대로 두고 `invalidateAll()` 호출부를 `CacheInvalidator` 로 |
| IP 정책·접근 제한 설정(신규) | Caffeine TTL 30s, 키 `tenantSeq + role` |
| 사용자 인증 정보 `loadUserByUserSeq`(신규) | 짧은 TTL. 비활성화·권한 변경·삭제 시 무효화 필수 — 보안 영향이 있어 마지막에 |
| `CachedTenantNameResolver` | 크기 제한·무효화 없음 → Caffeine TTL 로 교체 |
| `LicenseService`, OIDC·JWK | 그대로(이미 일관됨 / 외부 값 주기 갱신) |

### 3-2. JWT 블랙리스트 — DB 원본 + Redis 조회
- 로그아웃: DB INSERT(원본) → 커밋 후 Redis `SET drive:bl:{tokenHash} 1 EX <토큰 남은 만료>`.
- 조회: Redis → 오류면 DB. 필터는 그대로, `BlacklistTokenFindService` 구현만 바꾼다.
- Redis 에 "없음" 을 "블랙리스트 아님" 으로 믿으려면 Redis 가 DB 와 맞아야 한다. 어긋나는 경우와 대응:

| 상황 | 결과 | 대응 |
|---|---|---|
| Redis 전체 장애 | 조회 오류 → DB → 안전 | fallback |
| Redis 재시작으로 데이터 유실 | 조회는 성공, "없음" → **로그아웃 토큰 통과** | 기동·재연결 시 DB 의 미만료 항목을 Redis 로 재적재 |
| SET 한 번 실패(순간 끊김) | 위와 같음 | 재시도 + 1분 주기(ShedLock) "최근 N분 DB 항목 → Redis" 동기화 |

- Redis 만 두는 안은 버린다: 설치처마다 AOF·Sentinel 수준을 보장할 수 없다.

### 3-3. 업로드 동시 상한 — JVM 세마포어
- **서블릿 Filter** 하나로 `PUT /api/v1/uploads/{id}/parts/{n}`(octet-stream)·`POST /api/v1/files`(multipart) 두 경로를 막는다. multipart 는 `DispatcherServlet.checkMultipart` 가 컨트롤러·인터셉터보다 먼저 본문을 다 읽으므로 Filter 여야 "본문 읽기 전 거절" 이 된다.
- `tryAcquire()` 로 즉시 판단 → 실패 시 **503 + `Retry-After`**, 성공 시 `finally` 에서 `release()`. **기다리게 하지 않는다**(대기도 Tomcat 스레드를 잡는다).
- 상한 기본 50 안팎(WAS 당). 전체 상한 = 상한 × WAS 대수. `storage.http.max-connections`(200)·Tomcat 스레드(200)와 같이 정한다.
- 프론트 청크 업로더가 503 을 받으면 잠시 뒤 그 파트만 다시 보내야 한다(확인 필요).
- 사용자 한 명이 여러 WAS 에 걸쳐 독점하는 것까지 막으려면 Redis 카운터 — 나중.

### 3-4. rate limit — Bucket4j + Redis
- **토큰 버킷**: 용량 N, 초당 r 개 다시 채움, 요청 하나가 토큰 하나를 쓴다. 다 쓰면 429 + `Retry-After`. 순간 N 번, 평균 초당 r 번.
- Bucket4j 는 계산 라이브러리이고 상태 저장소는 모듈로 고른다(`bucket4j-redis` — Lettuce/Redisson). Redis 는 그냥 키로 저장할 뿐 Bucket4j 를 모른다. JDBC 백엔드는 요청마다 DB 쓰기라 쓰지 않는다.
- 위치: 인증 필터 다음 Filter. 클라이언트 IP 는 `IpAccessFilter` 의 추출 로직을 재사용(신뢰 프록시의 `X-Forwarded-For` 만).
- 층: 앞단(nginx `limit_req`·WAF)은 IP 당 거친 상한, 앱은 업무 키별.

| 대상 | 키(모두 tenant 포함) | 시작값(조정 대상) |
|---|---|---|
| 로그인(사용자·관리자·슈퍼관리자 화면 모두) | IP, 입력한 아이디 | IP 분당 20, 아이디 분당 5 |
| API Key·MCP | API Key | 분당 N |
| 공유 링크 | IP | 추측 시도 방지 |
| 무거운 API(zip 다운로드, 검색) | 사용자 | |
| 테넌트 전체 | tenantSeq | 독점 방지 |

- 브라우저(쿠키·핑거프린트) 기준은 쓰지 않는다 — 클라이언트가 마음대로 바꾼다.

### 3-5. 로그인 실패 잠금 — DB 유지, 고칠 점 3가지
지금 동작([AuthService.java](../../DriveAPI/src/main/java/com/thinkfree/drive/auth/service/AuthService.java) `validateAccountNotLocked`·`validatePassword`):

1. **관리자·슈퍼관리자 로그인 화면은 잠금이 없다**(`RoleType.USER != loginRole` 이면 횟수를 안 셈) → 관리자 계정 무제한 시도. 잠금을 걸면 공격자가 관리자를 잠글 수 있으므로 **rate limit(3-4)으로 막는다.**
2. **사용자 계정은 한 번 잠기면 시간으로 풀리지 않는다**(비밀번호 변경 때만 초기화) → 아이디만 알면 5번 틀려서 영구 잠금(계정 잠금 DoS). → 마지막 실패 시각을 저장하고 로그인 때 비교해 15분 뒤 자동 해제. **스케줄러 불필요.**
3. **동시 요청 경합**: 요청 시작 때 읽은 횟수로 판단하고 증가는 비밀번호 비교 뒤 → 동시에 50개를 보내면 모두 "0회" 로 통과해 50번 비교까지 간다. → 비교 **전에** 조건부 원자 증가:
   `login_failed_at` 은 지금 없는 컬럼이라 새 Flyway V 파일로 더한다(`login_failure_count` 만 V1 에 있음).
   ```sql
   UPDATE tb_user
   SET login_failure_count = CASE WHEN login_failed_at < now() - interval '15 minutes' THEN 1 ELSE login_failure_count + 1 END,
       login_failed_at = now()
   WHERE user_seq = ? AND (login_failure_count < 5 OR login_failed_at < now() - interval '15 minutes')
   RETURNING login_failure_count;
   -- 0행 = 잠김(비교 안 함), 비밀번호 맞으면 0 으로 초기화
   ```
   `SELECT ... FOR UPDATE` 는 bcrypt 비교(~100ms) 동안 행 잠금을 잡아 쓰지 않는다.

잠금을 DB 에 두는 이유: 계정 상태라 관리자 화면·감사 로그·관리자 해제와 맞아야 한다. Redis 로 옮기면 재시작 때 잠금이 모두 풀린다. 잠금(계정 하나 보호)과 rate limit(IP·아이디별 속도 제한)은 역할이 달라 둘 다 둔다.

### 3-6. 비동기 작업 — 큰 복사·복원만, DB 작업 테이블로
- **업로드는 비동기로 하지 않는다.** 바이트를 보내는 쪽이 브라우저다. 청크·이어 올리기로 이미 쪼개져 있고 complete 는 0.3초 수준(로컬 RustFS). 비동기 대상은 나중에 생길 **후처리**(썸네일·바이러스 검사·색인).
- **복사·복원은 기준 크기 이상만**(upload-session 설계 §6.4). S3 서버측 복사라 바이트는 WAS 를 지나지 않지만, Ceph 에서 수 GB 는 수십 초~분이 걸려 요청 스레드를 잡고 앞단 타임아웃(ALB idle, Apache 300s)에 걸린다.
- 방식: 세션을 PENDING 으로 만들고 **202 + sessionId** → 워커가 `SELECT ... FOR UPDATE SKIP LOCKED` 로 집어 처리 → 죽으면 기존 정리 스케줄러가 FAILED + 객체 정리. `@Async` 단독은 재시작 때 작업이 사라져 쓰지 않는다.
- 프론트: 목록에 "복사 중..." 상태 칩/토스트, `GET /uploads/{id}` 를 1~2초 간격 폴링 → COMPLETED 면 목록 새로고침. WebSocket·SSE 는 WAS 간 연결 위치 관리가 필요해 지금은 쓰지 않는다.

## 4. 이 문서 밖에서 같이 봐야 할 것 (대용량·분산에서 남은 빈칸)

| 항목 | 왜 | 판단 |
|---|---|---|
| **DB 커넥션 수** | Hikari 최대 50 × WAS 대수가 PostgreSQL `max_connections` 를 금방 넘는다 | WAS 3대 이상이면 PgBouncer(transaction 모드) 검토. 읽기 복제본은 그다음 |
| **파일 바이트가 모두 WAS 경유** | 트래픽 확장의 가장 큰 지렛대 | presigned 직접 업로드·다운로드, 다운로드 Range(upload-session todo C) |
| **인증 단계 DB 조회** | k6 300명부터 Hikari 대기 | 3-1 캐시 + API Key `last_used_date` 를 분 단위로만 쓰기 |
| **한 사용자 공간에 쓰기 몰림** | 사용자 행 잠금으로 초당 약 170건 상한(k6 결과 4) | 용량이 안 바뀌는 작업에서 사용자 행 잠금 제외 검토 — 잠금 순서 규칙과 같이 |
| **쓰기 API 재시도 중복** | 앞단·클라이언트가 타임아웃 뒤 재시도하면 복사·폴더 생성이 두 번 된다 | 이름 충돌 처리로 대부분 막히지만, 비동기 복사 도입 때 `Idempotency-Key` 검토 |
| **롤링 배포 중 긴 전송** | Spring Boot 기본 graceful shutdown 은 단계당 30초 — 큰 다운로드·파트 PUT 이 끊긴다 | `spring.lifecycle.timeout-per-shutdown-phase`·LB deregistration delay 를 같이 정함. 청크는 이어 올리기로 복구됨 |
| **Redis 이중화** | Redis 를 쓰기 시작하면 장애점이 하나 는다 | 2-3 의 fallback 으로 Redis 없이도 동작하게 두면 단일 인스턴스로 시작 가능 |

## 5. 성능 테스트(k6)에서 비어 있는 것
도구는 k6 로 충분하다. 측정 범위가 비어 있다([../../observability/k6/README.md](../../observability/k6/README.md)).
- **WAS 2대 + LB** 로 한 번은 돌린다 — ShedLock·DB 잠금·로컬 캐시 불일치는 여기서만 드러난다. 3-1 의 무효화도 이것으로 확인.
- k6·WAS·DB 가 한 PC — 최소한 k6 는 다른 머신.
- **장시간(1~2시간)**: 커넥션·메모리 누수, 삭제 대기열·업로드 세션 누적. **스파이크**(순간 급증).
- 사용자 18명·테넌트 1개 → 테넌트 여러 개·사용자 수백 명으로 캐시 적중률·잠금 경합을 현실에 가깝게.
- 실제 스토리지(Ceph·S3) 지연, 느린 클라이언트.
- #77 수정 후 재측정(인계 문서 §3-3).
