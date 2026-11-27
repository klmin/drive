# 분산 환경·대용량 트래픽 남은 작업

설계·결정 이유는 [../design/scaling-strategy.md](../design/scaling-strategy.md). 여기는 할 일만. 위에서부터 한다.

## A. Redis 없이 할 수 있는 것

- [ ] **업로드 동시 상한(세마포어)** — 설계 §3-3
  - 왜: 비정상 클라이언트가 Tomcat 스레드 198/200 점유(k6 `abuse-parts.js`). 목록·로그인까지 막힌다
  - 어디: 새 서블릿 Filter — `PUT /api/v1/uploads/*/parts/*`, `POST /api/v1/files`. `tryAcquire()` 실패 시 본문 읽기 전 503 + `Retry-After`, `finally` 에서 `release()`. 상한은 설정값(`drive.upload.max-concurrent-bodies` 등, 기본 50 안팎)
  - 프론트: `DriveFront` `utils/chunkUpload.js` 가 503 이면 잠시 뒤 그 파트만 재전송하는지 확인, 없으면 추가
  - 끝 기준: 상한 초과 요청이 503 이고 본문을 읽지 않음(단위 테스트), 연결 끊김에도 허가 반납, `abuse-parts.js` 재측정에서 Tomcat 바쁜 스레드가 상한 근처에 머묾
- [ ] **로그인 잠금 3가지** — 설계 §3-5
  - 왜: 동시 요청이면 5회 넘게 비교, 잠금이 시간으로 안 풀림(계정 잠금 DoS)
  - 어디: `AuthService.validateAccountNotLocked`·`validatePassword`, `UserJpaRepository.increaseLoginFailureCount` → 비교 전 조건부 원자 증가(`RETURNING`), 새 V 파일로 `tb_user.login_failed_at`
  - 끝 기준: 동시 20개 틀린 요청에서 비밀번호 비교가 5번만 일어남(동시성 테스트, `ResourceNamingLockConcurrencyIntegrationTest` 의 `race()` 패턴), 15분 뒤 자동 해제, 성공 시 초기화
- [ ] **API Key `last_used_date` 쓰기 줄이기**
  - 왜: `ApiKeyAuthenticationFilter` 가 요청마다 UPDATE — MCP 연속 호출 시 같은 행에 쓰기 집중
  - 어디: 값이 초 단위 절삭이므로 "이미 1분 안에 쓴 키는 건너뜀"(로컬 Caffeine 에 마지막 기록 시각) 또는 `WHERE last_used_date < now() - 1분`
  - 끝 기준: 같은 키로 1분에 100번 호출해도 UPDATE 1번
- [ ] **IP 정책·접근 제한 설정 Caffeine 캐시** — 설계 §3-1(무효화 신호는 B 에서)
  - 어디: `IpAccessControlService` — 키 `tenantSeq + role`, TTL 30s, 정책 저장·삭제 시 로컬 invalidate
  - 끝 기준: 같은 테넌트 요청이 TTL 안에 DB 를 다시 조회하지 않음, 수정 직후 같은 WAS 에서 반영
- [ ] **`CachedTenantNameResolver` 를 Caffeine TTL 로** — 크기 제한·무효화가 없어 테넌트 이름 변경이 감사 로그에 반영되지 않음

## B. Redis 도입 (선택 구성요소, `redis | db | local` 스위치)

- [ ] **Redis 연결·스위치 골격** — 설계 §2-3. `management.health.redis` 는 Redis 를 켠 설치처만. 설치기(`DriveInstaller`)·`ENV_VARIABLES.md` 에 항목 추가
- [ ] **캐시 무효화 pub/sub** — 설계 §3-1
  - 어디: 새 `CacheInvalidator`(로컬 즉시 삭제 + afterCommit `PUBLISH drive:cache-invalidate "종류:키"`), 기동 시 구독 리스너가 payload 로 분기. `HostTenantCache.invalidateAll()`·IP 정책 무효화 호출부를 이것으로
  - 끝 기준: WAS 2대에서 한쪽 수정이 다른 쪽에 1초 안에 반영, Redis 꺼도 TTL 로 반영
- [ ] **JWT 블랙리스트 Redis 조회** — 설계 §3-2
  - 어디: `BlacklistTokenFindService` 구현(Redis → 오류 시 DB), 로그아웃 시 DB + afterCommit Redis SET(TTL = 토큰 남은 만료), 기동·재연결 재적재, 1분 주기 동기화(ShedLock)
  - 끝 기준: Redis 정지·재시작·SET 실패 각각에서 로그아웃 토큰이 거절됨
- [ ] **rate limit(Bucket4j + Redis)** — 설계 §3-4
  - 우선: 로그인 전 화면(특히 관리자·슈퍼관리자 — 잠금 없음) → API Key → 공유 링크 → 무거운 API → 테넌트
  - 끝 기준: 초과 시 429 + `Retry-After`, WAS 2대에서 한도가 합산으로 지켜짐, Redis 장애 시 통과

## C. 비동기·확장

- [ ] **큰 복사·복원 비동기** — 설계 §3-6, upload-session todo 의 같은 항목과 하나로 처리. 기준 크기 결정, 202 + sessionId, 워커 `SKIP LOCKED`, 프론트 상태 칩 + 폴링
- [ ] **DB 커넥션 계획** — WAS 3대 이상 배포 전에 Hikari × 대수 vs `max_connections`, PgBouncer 여부
- [ ] **롤링 배포 종료 시간** — `spring.lifecycle.timeout-per-shutdown-phase`·LB deregistration delay
- [ ] **사용자 인증 정보 캐시** — 비활성화·권한 변경·삭제 무효화가 빠지면 보안 문제라 B 의 pub/sub 이 안정된 뒤

## D. 측정

- [ ] #77 수정 후 재측정(인계 §3-3) — A 의 세마포어와 같이
- [ ] WAS 2대 + LB 측정 — B 의 무효화·rate limit 확인 포함
- [ ] 장시간(1~2h)·스파이크, k6 를 다른 머신에서, 테넌트 여러 개
