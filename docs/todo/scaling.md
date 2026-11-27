# 분산 환경·대용량 트래픽 남은 작업

설계·결정 이유는 [../design/scaling-strategy.md](../design/scaling-strategy.md). 여기는 할 일만. 위에서부터 한다.

## A. Redis 없이 할 수 있는 것

- [x] **업로드 동시 상한(세마포어)** — 설계 §3-3, PR #96
  - `UploadConcurrencyLimitFilter`: 파트 PUT·단일 업로드 POST(외부 API `/api/public/v1/files` 포함), WAS 당 `drive.upload.max-concurrent-bodies`(기본 50), 넘치면 본문 읽기 전 503 + `Retry-After` + `Connection: close`
  - WebOffice 저장(`PUT /api/v1/tfo-http-storage/**`)은 뺐다 — WebOffice 가 503 에 다시 보내는지 확인되지 않음
  - 프론트: 청크 파트는 503·429 에 약 1분까지(지터, `Retry-After` 이상), 단일 업로드는 1·2·4초 재시도. 본문을 다 보낸 뒤 끊긴 단일 업로드는 다시 보내지 않음(파일 중복)
  - k6 `abuse-parts.js` 재측정: Tomcat 바쁜 스레드 200 → 104, S3 연결 128 → 52, 정상 업로드 실패 0. **피해 사용자 목록 지연은 그대로**(같은 PC 의 네트워크 경로로 추정, D 에서 다른 머신으로 확인)
- [x] **로그인 잠금** — 설계 §3-5, PR #98
  - V10 `tb_user.login_failed_at`(기존 실패 행은 마이그레이션 시각으로 채움), 비교 전 조건부 원자 카운트(`UPDATE … RETURNING`, 별도 트랜잭션), 15분 뒤 자동 해제, 비밀번호가 맞으면 MFA 전에 바로 초기화
  - 인증 없이 부르는 재설정 링크 요청이 실패 횟수를 0 으로 만들던 우회를 없앰
  - 동시 20회 → 비교 5번(`LoginAttemptConcurrencyIntegrationTest`)
- [x] **IP 헤더 위조 차단** — 설계 §3-4 전제, PR #97
  - `IpUtil` 이 클라이언트가 보낸 `X-Forwarded-For` 등 헤더를 그대로 믿어 IP 접근 제한·감사 로그 IP 를 위조할 수 있었다 → `getRemoteAddr()` 만(Tomcat RemoteIpValve 가 신뢰 프록시의 XFF 로 채움)
  - 남은 것: 사용자 PC 도 사설망인 설치처는 `SERVER_TOMCAT_REMOTEIP_INTERNALPROXIES` 를 프록시 주소로 좁혀야 한다(`ENV_VARIABLES.md` §1-2). 설치기 기본값은 그대로
- [x] **API Key `last_used_date` 쓰기 줄이기** — PR #99
  - 인증 조회에서 읽은 값이 1분 안이면 건너뜀 + UPDATE 조건 `last_used_date < now-1분`. 같은 키 100번에 UPDATE 1번
- [x] **IP 정책·접근 제한 설정 Caffeine 캐시** — 설계 §3-1, PR #100
  - `AccessPolicyCache` 키 `tenantSeq + role`, TTL 30s, 정책·사용 여부·테넌트 삭제 시 로컬 무효화 + 커밋 뒤 한 번 더. 다른 WAS 는 B 의 무효화 전까지 최대 30초 늦음
- [x] **`CachedTenantNameResolver` 를 Caffeine TTL 로** — PR #101 (5분, 최대 1만 건)
- [ ] **Tomcat `connection-timeout`**
  - 왜: 설정이 없어 바이트를 조금씩 흘려 보내는 클라이언트가 업로드 허가·Tomcat 스레드를 오래 잡는다(세마포어 상한을 적은 연결로 채울 수 있음)
  - 어디: `application.yml` `server.tomcat.connection-timeout`, 앞단 Apache `Timeout` 과 같이. 느린 회선의 정상 16MB 파트가 끊기지 않는 값
  - 끝 기준: 바이트를 멈춘 연결이 설정 시간 뒤 끊기고 허가가 반납됨(테스트 또는 k6 느린 클라이언트)
- [ ] **MCP 로깅 필터 본문 무제한 읽기**
  - 왜: 인증 전에 요청 본문 전체를 크기 제한 없이 메모리로 읽는다 — 인증 없는 큰 요청으로 힙을 쓸 수 있음
  - 어디: `McpRequestLoggingFilter` — 상한(예: 64KB)까지만 담고 나머지는 흘려보내거나, 인증 뒤로 옮김
  - 끝 기준: 상한을 넘는 본문에서 메모리에 담는 양이 상한으로 묶임(단위 테스트)
- [ ] **OIDC 캐시 만료**
  - 왜: `OidcAuthHandler` 가 디스커버리 문서·`RestClient`·`JwtDecoder` 를 `ConcurrentHashMap` 에 만료 없이 보관한다(설계 §1 표). IdP 의 엔드포인트·`jwks_uri` 변경은 재기동 전까지 반영되지 않는다. JWK 키 회전이 디코더 안에서 갱신되는지는 확인 필요
  - 끝 기준: TTL(Caffeine) 또는 검증 실패 시 다시 받기, 크기 제한

## B. Redis 도입 (선택 구성요소, `redis | db | local` 스위치)

- [x] **Redis 연결·스위치 골격** — 설계 §2-3, PR #102. `drive.redis.enabled`(기본 false)·`key-prefix`, `@ConditionalOnDriveRedis`, 기동 시 PING·`maxmemory-policy` 경고, 지표 `drive_redis_up`
  - `management.health.redis` 는 Redis 를 켠 설치처도 **끈다** — 켜면 Redis 장애 때 health DOWN → LB·k8s 가 WAS 를 빼서 전체 장애가 된다. 경보는 `drive_redis_up` 으로
- [x] **캐시 무효화 pub/sub** — 설계 §3-1, PR #103
  - `CacheInvalidationBroadcaster`(커밋 뒤 PUBLISH `<prefix>cache-invalidate`, payload `보낸WAS:캐시이름:키`), 캐시가 `LocallyInvalidatable` 구현 — 호출부는 그대로
  - 구독 컨테이너는 기동 때 바로 시작하지 않는다 — Redis 에 닿지 않으면 첫 구독 예외로 앱 기동이 실패했다. 뒤에서 5초마다 시도
  - 확인: Testcontainers Redis 로 컨텍스트 2개, 1초 안 반영. Redis 없이도 기동
- [ ] **JWT 블랙리스트 Redis 조회** — 설계 §3-2
  - 어디: `BlacklistTokenFindService` 구현(Redis → 오류 시 DB), 로그아웃 시 DB + afterCommit Redis SET(TTL = 토큰 남은 만료), 재적재 완료 표시 키(없으면 DB 조회), 1분 주기 동기화(ShedLock)
  - Redis `maxmemory-policy noeviction` 을 설치 문서·설치기에 고정 — 밀려나면 로그아웃 토큰이 통과
  - 끝 기준: Redis 정지·재시작(데이터 유실)·SET 실패 각각에서 로그아웃 토큰이 거절됨
- [ ] **rate limit(Bucket4j + Redis)** — 설계 §3-4
  - 우선: 로그인 전 화면(특히 관리자·슈퍼관리자 — 잠금 없음) → API Key → 공유 링크 → 무거운 API → 테넌트
  - 끝 기준: 초과 시 429 + `Retry-After`, WAS 2대에서 한도가 합산으로 지켜짐, Redis 장애 시 통과

## C. 비동기·확장

- [ ] **큰 복사·복원 비동기** — 설계 §3-6, upload-session todo 의 같은 항목과 하나로 처리. 기준 크기 결정, 202 + sessionId, 워커 `SKIP LOCKED`, 프론트 상태 칩 + 폴링
  - 주의: 정리 스케줄러가 COMPLETING 30분(`completing-timeout`)을 넘은 세션을 FAILED 로 바꾸고 ABORT·DELETE 를 등록한다 — 수 GB 복사가 30분을 넘으면 작업 도중에 지워진다. 진행 중 갱신 시각(heartbeat) 또는 종류별 기준 시간
- [ ] **DB 커넥션 계획** — WAS 3대 이상 배포 전에 Hikari × 대수 vs `max_connections`, PgBouncer 여부
- [ ] **롤링 배포 종료 시간** — `spring.lifecycle.timeout-per-shutdown-phase`·LB deregistration delay
- [ ] **사용자 인증 정보 캐시** — 키 `userSeq + role`. 비활성화·권한 변경·삭제 무효화가 빠지면 보안 문제라 B 의 pub/sub 이 안정된 뒤
- [ ] **API Key 인증 조회 캐시** — 요청마다 SELECT 2번. 비활성화·재발급·만료일 변경 시 무효화 필수라 사용자 인증 정보 캐시와 같이

## D. 측정

- [x] 세마포어 재측정 — PR #96(k6 README "업로드 동시 상한"). 피해 사용자 지연은 다른 머신에서 다시
- [ ] "k6 300명부터 Hikari 대기가 인증 조회 때문" 은 추정 — A 의 캐시 반영 뒤 같은 시나리오로 확인
- [ ] WAS 2대 + LB 측정 — B 의 무효화·rate limit 확인 포함
- [ ] 장시간(1~2h)·스파이크, k6 를 다른 머신에서, 테넌트 여러 개
