# 다음 세션 시작점 (인계)

> 작성: 2026-11-19. 긴 세션을 끊고 새 컨텍스트에서 이어가기 위한 문서. 새 세션은 **이 파일부터** 읽는다.
> 다 읽고 이어갔으면 이 파일의 "진행 상태"만 고쳐 두고, 오래되면 지운다(내용은 아래 링크 문서에 다 있음).

## 1. 읽는 순서
1. [upload-session.md](upload-session.md) — **남은 작업**(A 단계 4 / B 운영 전 / C 정리). 다음 할 일은 여기서 고른다
2. [../design/upload-session-as-built.md](../design/upload-session-as-built.md) — **지금 코드가 실제로 어떻게 동작하는지**(흐름·잠금 순서·정리·설정·코드 위치·테스트)
3. [../design/upload-session-redesign.md](../design/upload-session-redesign.md) — 확정 설계·결정 이유(필요한 절만)
4. `AGENTS.md` — 리뷰 기준(한국어, P0~P2)

## 2. 진행 상태 (2026-11-19 기준)
| PR | 내용 | 상태 |
|---|---|---|
| #9 | 단계 0~2 — 세션 테이블(V6)·S3 멀티파트·삭제 워커 action 분리·세션 정리 스케줄러·버킷 lifecycle | 머지 |
| #19 | 단계 3-1 — 신규 저장(웹·MCP·복사·템플릿)을 세션 경로로 | 머지 |
| #22 | 단계 3-2 — 버전 저장·덮어쓰기·복원·웹오피스를 세션 경로로, 옛 핸들러·보상 롤백 제거 | 머지 |
| #27 | 이름 결정 경로 잠금(부모 폴더/사용자 행) + 교착 3건 수정(사용자 행 항상 먼저) | 머지 |
| #31 | 문서 — as-built·todo | 머지 |
| #35 | CI — CodeQL 업로드 없이 분석만(private 저장소) | 머지 |

그 밖의 열린 일: **Dependabot 경고 2건(백엔드)** — [dependabot.md](dependabot.md). Dependabot PR #28·#29 가 이미 있다.

**업로드 세션 단계 0~3 완료.** 다음 큰 작업 = 단계 4(청크 업로드 API + 프론트 Uppy·이어 올리기) — todo A.

## 3. 바로 알아야 할 것
- **CodeQL**: 저장소가 private 이 되면서 Code scanning 업로드가 막혔다. #35 로 "분석만 하고 결과는 실행 Summary·SARIF 아티팩트로" 바꿨다. 발견이 있어도 체크를 실패시키지 않는 상태(`FAIL_ON_FINDINGS: 'false'`) — 기존 발견 확인 후 켤지 결정. #35 실행 기준 발견 = **Java 31건, JS 2건**(PR #35 의 Actions 실행 → Summary 에 목록) — 아직 검토 안 함, upload-session todo B 참고.
- **Codex 리뷰**: PR 을 열거나 push 하면 자동(`codex-review-on-push.yml`). 끝났는지는 PR 의 `codex-review-state` 코멘트(`"status":"completed"`, `has_any_findings`)로 본다. `@codex address that feedback`(자동 수정)은 환경 미설정이라 안 된다 — 지적은 직접 고친다.
- **잠금 순서 규칙**(쓰기 경로를 새로 만들면 반드시): 소유자 사용자 행 → 이름 정할 자리(부모 폴더) → 대상 → 하위. 리소스 행 뒤에 사용자 행을 잡으면 교착(PR #27 에서 3번 나옴). as-built §4.
- **로컬 stash**: `stash@{0}` = 이미 main 에 머지된 PR #8(관측) 작업본 사본. 필요 없으면 `git stash drop`.

## 4. 로컬 환경 메모 (Windows)
- Maven: PATH 에 없다. `~/.m2/wrapper/dists/apache-maven-3.9.11/<hash>/bin/mvn` 사용(리포에 `.mvn/wrapper` 없음). 빌드·테스트는 `-o -Dfrontend.skip=true`
  - 예: `mvn -o -Dfrontend.skip=true test -Dtest='UploadWriteIntegrationTest,UploadSessionWriterTest'`
- 테스트는 Testcontainers(PG 18·MinIO) — Docker 필요
- **전체 테스트 실패의 기존 원인**(코드 문제 아님): test 프로필 없는 MVC 테스트의 `${DRIVE_DATABASE_HOST}`, 커밋형 테스트가 남기는 `quota-test` 테넌트 이름, 컨텍스트 누적 `too many clients`, IntelliJ 자동 빌드와 겹침(`NoClassDefFoundError`). 관련 테스트만 골라 돌린다. 자세히는 todo 맨 아래
- jOOQ 재생성: `docker-maven-plugin` 이 Docker 29 와 안 맞음 → PG 18 컨테이너를 직접 띄우고 `-Pjooq-codegen -Ddocker.skip=true -Djooq-codegen.pg-port=<port>`, 생성물의 `"this-escape"` 토큰은 지워 diff 최소화
- 동시성(교착) 회귀 테스트 패턴: `ResourceNamingLockConcurrencyIntegrationTest` 의 `race()` — 수정을 잠시 끄고 실패하는지 확인한 뒤 원복하는 식으로 검증해 왔다

## 5. 작업 방식(지금까지)
- 단계마다 브랜치 → 관련 테스트 → 커밋(작업 파일만) → PR → Codex 리뷰 확인·반영 → CI 확인 → 머지
- 문서는 코드와 같은 PR 에서 as-built·todo 를 고친다(끝난 todo 는 `[x]` + PR 번호)
