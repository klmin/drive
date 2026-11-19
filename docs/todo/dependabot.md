# Dependabot 경고 정리

> 기준: 2026-11-19. 새 세션에서 이 파일만 보고 처리할 수 있게 정리. 끝내면 항목을 `[x]` + PR 번호로 바꾸고, 모두 끝나면 이 파일을 지운다.

## 0. 설정 상태
- 저장소 Settings → Code security 에서 **Dependabot alerts·security updates 가 켜져 있다**. 취약 버전을 쓰면 경고가 뜨고, 고친 버전이 있으면 Dependabot 이 버전 올림 PR 을 자동으로 만든다.
- `.github/dependabot.yml`(정기 버전 업데이트)은 **두지 않았다** — "CI 도입 전까지 보류"(커밋 `66b172c`). 지금 PR 은 보안 업데이트로만 생긴다. CI(CodeQL)가 들어왔으니 정기 버전 업데이트를 켤지는 따로 결정(C 항목).
- 경고 확인: 저장소 Security 탭 → Dependabot, 또는 `gh api "repos/klmin/drive/dependabot/alerts?state=open"`.

## 1. 지금 열린 경고 (2건, 모두 백엔드·실행 시 사용)

| 경고 | 패키지 | 현재 → 고친 버전 | 심각도 | Dependabot PR |
|---|---|---|---|---|
| #1 | `org.springframework.retry:spring-retry` | 2.0.11 → **2.0.13** | medium — GHSA-2827-2mxx-j8pv(상태 저장 재시도의 캐시 고갈 → DoS) | **#29** |
| #2 | `org.apache.httpcomponents.client5:httpclient5` | 5.5 → **5.6.3** | medium — GHSA-hjcp-jmpx-g3qm(Content-Encoding 디코딩 시 연결 누수) | **#28** |

두 PR 모두 `DriveAPI/pom.xml` 의 버전 한 줄만 바꾼다(`spring-retry` 266행 근처, `httpclient5` 312행 근처 — 둘 다 Boot 관리 버전이 아니라 pom 에 직접 박혀 있다).
프론트 경고(`next`·`axios`·`sharp`·`postcss` 등 50여 건)는 2026-11-19 에 모두 fixed — 남은 것 없음.

### 어디서 쓰나 (테스트 범위 정할 때)
- **spring-retry**: `@ApiRetryable`(→ `@Retryable`) 기반 재시도 — `PostProcessingOrchestratorRetry`, `ResourceRetryService`, `QuotaService`, `NotificationRetryService`, `DefaultResourceShareTokenService`, 판정 `RetryUtil`. 모두 **상태 없는(stateless) 재시도**로 보여 이 취약점(stateful 재시도)에 실제로 걸리는지는 낮지만, 버전은 올린다.
- **httpclient5**: `httpclient/config/HttpClientConfig`, `restclient/config/RestClientFactory` — 외부 HTTP 호출(RestClient). 웹오피스 티켓 발급(`OfficeTicketClient`), 외부 API 연동 등. (S3 는 AWS SDK 자체 HTTP 클라이언트라 무관)

## 2. 처리 순서
- [ ] **#29 spring-retry 2.0.13**
  1. PR 에 `@dependabot rebase` 코멘트 — PR 이 2026-11-19 08:08 에 만들어져 **CodeQL 워크플로 변경(#35) 전 CI 결과**만 있다. main 기준으로 다시 돌린다
  2. 로컬: `gh pr checkout 29` → `mvn -o -Dfrontend.skip=true test -Dtest='*Retry*Test,VersionDeleteTransactionIntegrationTest,UploadWriteIntegrationTest,CopyAndDocumentWriteIntegrationTest,TrashConcurrencyIntegrationTest'`
     - `-o`(오프라인)면 새 버전이 로컬 저장소에 없어 실패한다 → 처음 한 번은 `-o` 없이
  3. CI(CodeQL 두 개) 통과 + Codex 리뷰 확인 → 머지 → Security 탭에서 경고 #1 fixed 확인
- [ ] **#28 httpclient5 5.6.3** (마이너 버전 5.5 → 5.6 — #29 보다 변경 폭이 크다)
  1. `@dependabot rebase`
  2. 로컬: `gh pr checkout 28` → `mvn -Dfrontend.skip=true test -Dtest='OfficeTicketClientTest,HttpStorageContractTest,*RestClient*Test,*HttpClient*Test'` + 앱을 띄워 웹오피스 문서 열기(티켓 발급) 한 번
  3. 5.6 변경점 확인: 커넥션 풀·타임아웃 설정 API 가 `HttpClientConfig`·`RestClientFactory` 에서 deprecated·동작 변경이 없는지(컴파일 경고 포함)
  4. CI + Codex 리뷰 → 머지 → 경고 #2 fixed 확인
- 두 PR 은 같은 `pom.xml` 의 다른 줄이라 순서대로 머지하면 충돌 없음(먼저 머지한 뒤 다른 쪽은 Dependabot 이 자동 rebase)

## 3. 선택(C)
- [ ] `.github/dependabot.yml` 로 정기 버전 업데이트(주 1회, maven·npm·github-actions) 켤지 결정 — PR 이 늘어나는 대신 큰 버전 점프를 피한다. 켠다면 `open-pull-requests-limit`, 그룹(`groups`)으로 소음 줄이기
- [ ] `spring-retry`·`httpclient5` 를 pom 에서 직접 버전 지정하지 말고 Spring Boot 관리 버전을 따를지 검토 — Boot 를 올릴 때 같이 올라간다. 단 Boot 관리 버전이 고친 버전보다 낮으면 지금처럼 직접 지정 유지

## 참고
- Codex 리뷰는 Dependabot PR 에도 자동으로 돈다(`codex-review-on-push.yml`).
- CodeQL 은 이제 결과를 업로드하지 않는다(#35) — 체크 통과/실패와 별개로 실행 Summary 에 발견 목록이 나온다.
