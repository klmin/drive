# 다음 세션 시작점 (인계)

> 작성: 2026-11-20(11-19 판을 갱신). 긴 세션을 끊고 새 컨텍스트에서 이어가기 위한 문서. 새 세션은 **이 파일부터** 읽는다.
> 다 읽고 이어갔으면 이 파일의 "진행 상태"·"다음 할 일"만 고쳐 두고, 오래되면 지운다(내용은 아래 링크 문서에 다 있음).

## 1. 읽는 순서
1. 이 파일의 **§3 다음 할 일**: 무엇부터 할지 이미 정해 두었다
2. [upload-session.md](upload-session.md): **남은 작업 전체**(A 단계 4 / B 운영 전 / C 정리), 항목마다 왜·어디·완료 기준
3. [../design/upload-session-as-built.md](../design/upload-session-as-built.md): **지금 코드가 실제로 어떻게 동작하는지**(흐름·잠금 순서·정리·설정·코드 위치·테스트)
4. [../design/upload-session-redesign.md](../design/upload-session-redesign.md): 확정 설계·결정 이유(필요한 절만)
5. `AGENTS.md`: 리뷰 기준(한국어, P0~P2)

## 2. 진행 상태 (2026-11-20 기준, 모두 main 에 머지)
업로드 세션 재설계 **단계 0~4 와 B 의 보안 항목까지 끝났다.** 기능은 다 만들었고 관련 테스트는 통과한다.
남은 것은 옛 코드 정리, 작은 개선 하나, 실제 환경 확인, 운영 대비다.

| PR | 내용 |
|---|---|
| #9 | 단계 0~2: 세션 테이블(V6)·S3 멀티파트·삭제 워커 action 분리·세션 정리 스케줄러·버킷 lifecycle |
| #19 | 단계 3-1: 신규 저장(웹·MCP·복사·템플릿)을 세션 경로로 |
| #22 | 단계 3-2: 버전 저장·덮어쓰기·복원·웹오피스를 세션 경로로, 옛 핸들러·보상 롤백 제거 |
| #27 | 이름 결정 경로 잠금(부모 폴더/사용자 행) + 교착 3건 수정(사용자 행 항상 먼저) |
| #31·#36 | 문서: as-built·todo·인계 |
| #35 | CI: CodeQL 업로드 없이 분석만(private 저장소) |
| #38 | 단계 4 백엔드: 청크 업로드 API `/api/v1/uploads`(시작·파트 PUT·상태·complete·취소·목록) |
| #40 | `GET /users/quota` 에 `uploadingByte`(진행 중 합계, IS_FULL 안내용. 막대는 확정값만) |
| #43 | 덮어쓰기 세션은 사전 용량 검사에 증가분만(V7 `reserve_byte`) |
| #45 | 프론트 청크 업로더(직접 구현 `utils/chunkUpload.js`, 32MB 이상만 청크, 이어 올리기·취소·진행 중 목록) |
| #49 | 앞단 프록시 문서(본문 상한·타임아웃), 번들 Apache `ProxyTimeout 300` |
| #51 | 커밋 tx 권한 재확인(청크 complete 만, 실패 = `PERMISSION_DENIED`) |
| #53 | 매직바이트 검사(Tika, 실행 파일 차단, 415 `UPLOAD_010`) |
| #55 | Content-Type 은 서버가 확장자로 결정 |
| #57 | CodeQL 발견 32건 정리(`LogSafe` 로 로그 줄바꿈 제거 등) + `FAIL_ON_FINDINGS: 'true'` |
| #60 | 프론트 423 잠금·QUOTA_003 분기 수정(오류 응답에 `status` 가 없음), MCP 요청·응답 본문 로그는 DEBUG 에서만 |
| #63 | 옛 흐름 호환 코드 제거(`cleanVersionIfExists`·저장 마커·PENDING 만료 판정·`VERSION_PROCESSING`·버전 롤백 쿼리) |

**업로드 경로 정리**
- 웹 화면의 신규·덮어쓰기·새 버전은 모두 `UploadDialog` 한 곳에서 처리한다. 32MB 이상은 청크 API, 미만은 기존 단일 요청(`POST /files`)이다.
- 두 방식 모두 서버에서는 같은 세션 흐름(`UploadSessionWriter`)을 거친다. 그래서 커밋 시 용량 확정·매직바이트·Content-Type·실패 정리가 똑같이 적용된다.
- 웹오피스 저장·MCP·API Key 업로드·복사·복원은 청크 대상이 아니다(서버 내부 처리, 또는 청크 API 를 외부에 공개하지 않음).

## 3. 다음 할 일 (2026-11-20 결정)
**배포된 설치처가 아직 없다.** 이 전제로 다른 세션의 검토 결과(옛 3분 만료·대상 삭제·타임아웃 등)를 코드와 대조해 정했다.

1. ~~**옛 흐름 호환 코드 제거**~~ — **완료(PR #63)**. 아래는 기록용.
   - 함께 지운 것: 저장 마커(`findSaveMarker`·`SAVE_MARKER_METADATA_KEY`), `existsAliveVersion`·`findExpiredVersion`(3분 판정), `finalizeAfterStorageUpload`·`rollbackResourceVersionMetadata`, `ResourceRepository.rollbackResourceVersionData`·`ResourceVersionRollbackQuery`, `VERSION_PROCESSING`(RESOURCE_VERSION_004). `ResourceVersionStatus.PENDING` 은 `pushCurrentAsVersion` 이 같은 tx 안에서 INSERT 할 때 쓰므로(DB 기본값) 남겼고, `DELETE` 값도 enum 에 남겼다(쓰는 곳 없음).
   - 배포된 곳이 없으니 옛 흐름이 만든 PENDING·DELETE 버전 행이 운영 DB 에 없다. 기다릴 이유가 없으므로 지금 지운다.
   - 지우면 아래 두 문제가 함께 사라진다.
     - 롤링 배포 중 옛 서버의 3분 만료 좀비 시나리오
     - 커밋 tx 의 행 락 안에서 S3 HEAD 를 부르는 문제
   - 현재 위치: `DefaultResourceService.java` 의 `replaceWithVersioning`·`overwriteContent`·`deleteVersion` 이 `cleanVersionIfExists` 를 부른다.
   - 완료 기준: 관련 통합 테스트 통과, `existsAliveVersion`(VERSION_PROCESSING 거절)도 함께 정리
2. **업로드 중 대상 삭제 조기 실패**([upload-session.md](upload-session.md) B 새 항목)
   - 문제: `UploadChunkService.uploadPart` 는 세션 상태·파트 크기만 본다. 그래서 그 사이 대상 파일·부모 폴더가 삭제돼도 수 GB 를 다 올린 뒤 complete 에서야 실패한다(데이터는 안전).
   - 수정: 파트 PUT 때 대상이 살아 있는지 가볍게 조회하고, 없으면 세션을 FAILED 로 끝내고 409 를 준다.
   - 완료 기준: `UploadChunkIntegrationTest` 에 "업로드 중 대상 삭제 → 다음 파트 409" 1~2건
3. **실제 MinIO 로 1GB 이어 올리기 확인**(배포 전 필수)
   - 브라우저에서 1GB 를 올리다 끊고 이어 올린 뒤 complete 한다.
   - **조립(complete multipart) 시간도 잰다.** COMPLETING 30분을 넘기면 정리 스케줄러가 FAILED + 객체 삭제를 하므로(`UploadSessionCleanupService.java:35`), 여유가 있는지 숫자로 확인한다.
   - 로컬 백엔드 실행 설정(`.env`)이 필요하다. 이전 PC 에는 없었다.

**하지 않기로 한 것**
- 세션 TTL 1h·COMPLETING 30m 숫자 변경: 3번 측정 전에는 근거가 없다. 파트마다 `touch` 로 연장되고 동시 4개라, 16MB 파트 하나가 1시간을 넘기려면 초당 약 4.5KB 수준의 회선이어야 한다.
- 단일 요청·내부 쓰기의 커밋 tx 권한 재확인: 의도한 설계다. 요청 안에서 확인과 커밋이 바로 이어지고, 오래 걸리는 청크만 다시 확인한다.
- V6 인덱스 `CONCURRENTLY`: 배포된 곳이 없으니 그대로 둔다.

그 다음 후보(운영 전): 지표·알림, 운영 버킷 lifecycle 확인, Developers 저장소 k8s ingress 본문 크기·타임아웃. Ceph·RustFS 계약 테스트는 그 스토리지를 쓰는 설치처가 생길 때 한다.

## 4. 바로 알아야 할 것
- **작업 폴더**: `C:\intelliJProject\klmin-drive` 는 다른 세션들도 같이 쓴다. 브랜치가 중간에 바뀐 적이 있다. 작업은 `git worktree add ../wt-<이름> -b <브랜치> origin/main` 으로 따로 만들고, 끝나면 `git worktree remove` 로 지운다.
- **Codex 리뷰**: PR 을 열거나 push 하면 자동이다(`codex-review-on-push.yml`). **`@codex review` 를 직접 달지 않는다**(중복이 생긴다). Codex 가 "We couldn't complete this review" 라고 했을 때만 직접 단다. 끝났는지는 PR 의 `codex-review-state` 코멘트(`"status":"completed"`, `has_any_findings`)로 본다. 지적은 직접 고친다(`@codex address that feedback` 은 환경 미설정이라 안 된다).
- **머지**: 사용자가 그 PR 에 대해 머지를 승인했을 때만 한다(PR 마다 따로 승인).
- **CodeQL**: 이제 발견이 1건이라도 있으면 PR 체크가 실패한다. 사용자 입력(헤더·URI·요청 값)을 로그에 넣을 때는 `com.thinkfree.drive.util.LogSafe.of(...)` 로 감싼다. CSRF 규칙은 오탐이라 제외했다(쿠키 인증은 `X-Drive-Role` 헤더가 있어야 동작).
- **프론트 오류 응답 모양**: `api.js` 는 오류 때 서버 본문(`ApiExceptionResponse`: `result, code, errorCode, message`)을 돌려준다. HTTP 상태는 `res.code` 로 보고, **`res.status` 는 없다**. 예외로 로그인 페이지는 reject 된 axios 오류를 받는다.
- **잠금 순서 규칙**(쓰기 경로를 새로 만들면 반드시): 소유자 사용자 행 → 이름 정할 자리(부모 폴더) → 대상 → 하위. 리소스 행 뒤에 사용자 행을 잡으면 교착이 생긴다(PR #27). as-built §4.
- **스토리지 호출은 트랜잭션 밖**(AGENTS·as-built 규칙). 예외로 남아 있던 `cleanVersionIfExists`(커밋 tx 안 S3 HEAD)는 PR #63 에서 지웠다.
- **커밋·PR 서식**: 커밋 메시지 끝에 `Co-Authored-By` 줄, PR 본문 끝에 Claude Code 표시. 응답·리뷰는 한국어로 한다.
- **로컬 stash**: `stash@{0}` 은 이미 main 에 머지된 PR #8(관측) 작업본 사본이다. 필요 없으면 `git stash drop`.

## 5. 로컬 환경 메모 (Windows)
- Maven: PATH 에 없다. `~/.m2/wrapper/dists/apache-maven-3.9.11/<hash>/bin/mvn` 을 쓴다(리포에 `.mvn/wrapper` 없음). 빌드·테스트는 `-o -Dfrontend.skip=true`
  - 예: `M=$(find ~/.m2/wrapper/dists/apache-maven-3.9.11* -name mvn -path "*/bin/*" | head -1); "$M" -q -o test -Dfrontend.skip=true -Dtest='UploadChunkIntegrationTest'`
- 업로드 관련 주요 테스트:
  - 백엔드: `UploadChunkIntegrationTest`, `UploadWriteIntegrationTest`, `UploadSessionWriterTest`, `UploadContentInspectorTest`
  - 프론트: `DriveFront` 에서 `node --test tests/scripts/chunk-upload.test.mjs`(가짜 서버 22건)
- 테스트는 Testcontainers(PG 18·MinIO)를 쓰므로 Docker 가 필요하다.
- **전체 테스트(2026-11-20, 3200f01)**: 2585건 중 오류 140건, 실패 1건이고, 업로드 관련 실패는 없다.
  - 오류 140건: test 프로필 없는 35개 클래스의 컨텍스트 실패. 다른 세션이 `test/integration-test-profile` 브랜치에서 작업 중이다.
  - 실패 1건: `StorageDeletionSchedulerTimingIntegrationTest`. 단독 실행은 통과하고 전체 실행에서만 흔들린다.
  - 그 밖의 기존 원인은 [upload-session.md](upload-session.md) 맨 아래에 있다. 관련 테스트만 골라 돌린다.
- jOOQ 재생성: `docker-maven-plugin` 이 Docker 29 와 맞지 않는다. PG 18 컨테이너를 직접 띄우고 `-Pjooq-codegen -Ddocker.skip=true -Djooq-codegen.pg-port=<port>` 로 돌린다. 생성물의 `"this-escape"` 토큰은 지워 diff 를 줄인다.
- 동시성(교착) 회귀 테스트 패턴: `ResourceNamingLockConcurrencyIntegrationTest` 의 `race()`. 수정을 잠시 끄고 실패하는지 확인한 뒤 원복하는 식으로 검증해 왔다.
- Windows CRLF 파일은 `sed` 여러 줄 치환이 잘 안 된다. Edit 도구나 node 스크립트로 고친다.

## 6. 작업 방식(지금까지)
- 단계마다 worktree·브랜치 → 관련 테스트 → 커밋(작업 파일만) → PR → Codex 리뷰·CodeQL 확인·반영 → 사용자 승인 후 머지 → worktree 제거
- 문서는 코드와 같은 PR 에서 as-built·todo 를 고친다(끝난 todo 는 `[x]` + PR 번호). 이 인계 파일의 §2·§3 도 함께 고친다.
