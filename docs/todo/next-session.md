# 다음 세션 시작점 (인계)

> 작성: 2026-11-20 저녁(같은 날 오전 판을 갱신), 2026-11-22 §2-0·§3 갱신(로컬 청크 이어 올리기 확인). 긴 세션을 끊고 새 컨텍스트에서 이어가기 위한 문서. 새 세션은 **이 파일부터** 읽는다.
> 다 읽고 이어갔으면 이 파일의 §2 "진행 상태"·§3 "다음 할 일"만 고쳐 두고, 오래되면 지운다(내용은 아래 링크 문서에 다 있음).

## 1. 읽는 순서
1. 이 파일의 **§3 다음 할 일**: 무엇부터 할지 정해 두었다
2. [upload-session.md](upload-session.md): **남은 작업 전체**(A 단계 4 / B 운영 전 / C 정리), 항목마다 왜·어디·완료 기준
3. [../design/upload-session-as-built.md](../design/upload-session-as-built.md): **지금 코드가 실제로 어떻게 동작하는지**(흐름·잠금 순서·정리·설정·코드 위치·테스트)
4. [../design/upload-session-redesign.md](../design/upload-session-redesign.md): 확정 설계·결정 이유(필요한 절만)
5. `AGENTS.md`: 리뷰 기준(한국어, P0~P2)

## 2. 진행 상태 (2026-11-20 기준, 모두 main 에 머지)
업로드 세션 재설계는 **단계 0~4, B 의 보안 항목, 지표·알림, 운영 버킷 lifecycle 점검까지 끝났다.** 기능은 다 만들었고 관련 테스트는 PR CI 에서 통과한다.
남은 것은 **운영형 스토리지(MinIO·Ceph·AWS)에서의 조립 시간 확인과 브라우저 수동 확인**, k8s ingress 설정, 선택적인 정리·개선이다.

### 2-0. 2026-11-22 로컬 청크 이어 올리기 확인 (코드 변경 없음)
로컬 compose(PostgreSQL + **RustFS 1.0.0**, Windows 11·Docker 29) + 로컬 백엔드(main `9bf92e7`)에 **청크 API 를 스크립트로 직접 호출**해 확인했다(브라우저 아님). 파트 16MB, 동시 4개. 파일 내용은 파트마다 다른 텍스트.

| 확인 | 결과 |
|---|---|
| 1GB: 파트 일부(26/64, 중간중간 빠짐)만 보낸 뒤 `GET /uploads/{id}` → 빠진 38개만 → complete | 올라간 파트 목록이 보낸 것과 정확히 같음. 전송 2.9s + 4.3s, **complete(조립+크기 확인+매직바이트+커밋) 0.38s**. DB 크기·사용량·저장소 객체 SHA-256 원본과 일치 |
| 파트 PUT 도중 연결 끊김(Content-Length 선언 후 절반만 보내고 소켓 종료) | 세션 PENDING 유지, 끊긴 파트는 목록에 없음 → 그 파트만 다시 보내고 complete 성공 |
| 동시 complete 2번(중복 클릭) | 하나 200, 하나 409. 파일 1개. 끝난 뒤 complete 재요청은 같은 결과 200(멱등) |
| 1GB 절반 전송 뒤 **백엔드 강제 종료(taskkill /F) → 재기동** → 다시 로그인 → 이어 올리기 | 세션 PENDING·올라간 32개 유지, 빠진 32개만 보내고 complete 0.32s 성공, 크기·사용량 일치 |
| 3GB(파트 188개) | 전송 14.9s, **complete 0.24s** |
| 공통 | 끝난 뒤 미완성 멀티파트·진행 중 세션 남지 않음 |

- 판단: 로컬 RustFS 에서는 조립이 1초 미만이라 COMPLETING 30분·세션 TTL 1시간과 차이가 크다. 다만 로컬 디스크 숫자라 MinIO·Ceph·AWS 에서는 더 걸릴 수 있어 숫자 변경 근거로는 쓰지 않는다(§3-1).
- 로컬에서 재현할 때: 테넌트 총 용량(10GB)이 테스트 사용자 할당량으로 차면 사용자 생성이 `QUOTA_007` 이다. 로컬 DB 에서 테스트 사용자 `total_quota_byte` 를 사용량만큼으로 줄이고 다시 한다.

### 2-1. 2026-11-20 이 세션에서 한 것

| PR | 내용 |
|---|---|
| #64 | **다운로드 멈춤 수정**: 스트림 전용 풀 `DriveStream-`(대기열 없음, 포화 시 503), `spring.mvc.async.request-timeout` 해제, 커밋 후 실패 시 연결 끊기(`CommittedResponseExceptionResolver`). 리뷰 반영: 거절 시 이미 연 S3 응답을 `abort()` 후 닫기(`PendingDownloadStreams`), 503 전에 다운로드 헤더(Content-Length 등) 제거, 로그 값 `LogSafe`(CodeQL 2건) |
| #66 | 문서: 지표·알림 항목을 실제 구현과 맞춤, #59·#64·#65 반영 |
| #67 | **업로드 중 대상 삭제 조기 실패**: 파트 PUT 때 대상(신규는 부모 폴더, 버전·덮어쓰기는 대상 파일)이 휴지통·삭제면 FAILED(`TARGET_DELETED`) + ABORT + 409 `UPLOAD_001` |
| #68 | **COMPLETING 시간 초과 알림**: 시간 초과 사유를 `COMMIT_ERROR` 에서 `COMPLETING_TIMEOUT` 으로 분리. 알림 `drive-upload-completing-timeout`(15분 안에 시간 초과 발생), `drive-upload-completing-stale`(45분 넘게 남음 = 정리 스케줄러 정지). PG 지표 `drive_upload_active_oldest_completing_seconds` |
| #69 | **운영 버킷 lifecycle 점검**: 운영자의 버킷 전체 abort 규칙이 같거나 짧으면 겹치는 규칙을 더하지 않음, 예전 형식(Prefix) 규칙은 Filter 로 바꿔 다시 씀, 객체 만료 규칙은 경고. 수동 설정 문서 `DriveInstaller/docs/storage-bucket-lifecycle.md` |

같은 날 다른 세션에서 머지된 것: #59(통합 테스트 test 프로필 고정 + 백엔드 테스트 CI `backend-test.yml`), #65(`DriveFixtures` 로 꺼 둔 16건 다시 켬).

### 2-2. 그 전까지 한 것

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
| #50·#56 | 업로드 세션·삭제 대기열 DB 지표(`drive_upload_active_*`, `drive_storage_deletion_*`)와 정리 알림 |
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

### 2-3. 만들었지만 실제 환경에서 확인하지 못한 것
이 PC 에 Docker·Grafana·PostgreSQL·실제 버킷이 없어서, 단위 테스트와 PR CI 까지만 확인했다.
- **#68 알림 규칙·PG 쿼리**: Grafana 에 실제로 로드되는지, `drive_upload_active_oldest_completing_seconds` 가 나오는지. 관측 스택을 띄울 때 Alerting 화면에서 두 규칙(업로드 완료 처리 시간 초과·정리 안 됨)을 확인한다.
- **#69 lifecycle 병합**: 실제 AWS S3·Ceph RGW 버킷에서 기존 규칙과 합쳐 PUT 되는지. 특히 예전 형식(Prefix) 규칙 변환.
- **#64 다운로드 풀 포화**: 503·S3 응답 abort 를 실제 부하(k6 다운로드 시나리오)로 본 적은 없다.

## 3. 다음 할 일
**배포된 설치처는 아직 없다.** 위에서부터 한다.

1. **이어 올리기 확인 나머지**(배포 전 필수) — 로컬 RustFS + API 스크립트 확인은 끝남(§2-0)
   - 남은 것 ①: **운영형 스토리지에서 조립 시간**. MinIO·Ceph·AWS 중 설치처가 쓸 스토리지에서 1GB~수 GB complete 시간을 잰다. COMPLETING 이 30분(`drive.upload.completing-timeout`)을 넘기면 정리 스케줄러가 FAILED(`COMPLETING_TIMEOUT`) + 객체 삭제를 한다.
   - 남은 것 ②: **브라우저 수동 1회**. 1GB 를 올리다 새로고침 → 같은 파일 다시 선택 → 빠진 파트만 올라가는지(업로더 로직은 가짜 서버 테스트 22건으로 확인됨, 실제 서버와는 아직).
   - 측정 결과로 정할 것: COMPLETING 30분과 세션 TTL 1시간을 그대로 둘지. 바꾸면 #68 알림 기준(45분)과 as-built 의 정리 표도 같이 고친다.
   - 하는 김에 2-3 의 #68 알림 로드도 같은 관측 스택에서 확인한다.
2. **Developers 저장소 k8s ingress 본문 크기·타임아웃**
   - 청크 파트(최소 16MB)가 ingress 본문 상한에 걸리지 않게, 파트 PUT·다운로드가 ingress 타임아웃에 끊기지 않게 한다. 번들 Apache 쪽 기준은 #49 문서(앞단 프록시)에 있다.
3. 그 다음 후보(선택, [upload-session.md](upload-session.md) 에 항목별 설명)
   - B: 감사 로그 발행 시점(커밋 tx 안 + `AFTER_COMMIT`) 검토
   - B: Ceph·RustFS 계약 테스트 — 그 스토리지를 쓰는 설치처가 생길 때
   - A: 청크 API 외부 공개(API Key·MCP 로 큰 파일) — 필요해질 때
   - C: 호출부 없는 코드 제거, `FileWriteOrchestrator` 정리, 교착 남은 조합 점검(관리자 사용자 삭제·테넌트 정리)
   - C(단계 5 선택): 관리자 용량 축소 시 진행 중 세션 중단, presigned 직접 업로드, 대용량 복원·복사 비동기, 다운로드 Range

**하지 않기로 한 것**
- 세션 TTL 1h·COMPLETING 30m 숫자 변경: 운영형 스토리지 측정(1번 ①) 전에는 근거가 없다(로컬 RustFS 조립은 1초 미만, §2-0). 파트마다 `touch` 로 연장되고 동시 4개라, 16MB 파트 하나가 1시간을 넘기려면 초당 약 4.5KB 수준의 회선이어야 한다.
- 단일 요청·내부 쓰기의 커밋 tx 권한 재확인: 의도한 설계다. 요청 안에서 확인과 커밋이 바로 이어지고, 오래 걸리는 청크만 다시 확인한다.
- V6 인덱스 `CONCURRENTLY`: 배포된 곳이 없으니 그대로 둔다.

## 4. 바로 알아야 할 것
- **작업 폴더**: `C:\intelliJProject\klmin-drive` 는 다른 세션들도 같이 쓴다. 작업은 `git worktree add ../wt-<이름> -b <브랜치> origin/main` 으로 따로 만들고, 끝나면 `git worktree remove` 로 지운다.
- **Codex 리뷰**: PR 을 열거나 push 하면 자동이다(`codex-review-on-push.yml`). **`@codex review` 를 직접 달지 않는다**(중복이 생긴다). Codex 가 "We couldn't complete this review" 라고 했을 때만 직접 단다.
  - 끝났는지는 PR 의 `codex-review-state` 코멘트로 본다: `code_review.commit_sha` 가 최신 커밋이고 `"status":"completed"`, `has_any_findings:false` 면 통과.
  - push 할 때마다 새 커밋을 다시 리뷰하고, 새 지적을 낼 수 있다(#64 는 수정 커밋에서 abort 지적이 하나 더 나왔다). 지적은 직접 고친다(`@codex address that feedback` 은 환경 미설정이라 안 된다).
  - 문서에 "PR #N 완료"라고 쓰면, #N 이 아직 main 에 없을 때 Codex 가 지적한다(#66). 코드 PR 을 먼저 머지하고 문서 브랜치를 그 위로 올린다.
- **머지**: 사용자가 그 PR 에 대해 머지를 승인했을 때만 한다(PR 마다 따로 승인). "리뷰 통과하면 머지"로 승인받았으면 Codex 지적 0건 + CI 전부 성공일 때만 머지한다.
- **CI 체크**: `mvn test`(백엔드 전체, `DriveAPI/**` 바꿀 때만), `Analyze (java-kotlin)`·`Analyze (javascript-typescript)`(CodeQL), `request-review`(Codex 요청).
- **CodeQL**: 발견이 1건이라도 있으면 PR 체크가 실패한다. 결과는 Actions 실행의 SARIF 아티팩트(`codeql-sarif-java-kotlin`)로 본다(`gh run download <run> -D <dir>`). 사용자 입력(헤더·URI·요청 값·예외 문자열)을 로그에 넣을 때는 `com.thinkfree.drive.util.LogSafe.of(...)` 로 감싼다. CSRF 규칙은 오탐이라 제외했다.
- **프론트 오류 응답 모양**: `api.js` 는 오류 때 서버 본문(`ApiExceptionResponse`: `result, code, errorCode, message`)을 돌려준다. HTTP 상태는 `res.code` 로 보고, **`res.status` 는 없다**.
- **잠금 순서 규칙**(쓰기 경로를 새로 만들면 반드시): 소유자 사용자 행 → 이름 정할 자리(부모 폴더) → 대상 → 하위. 리소스 행 뒤에 사용자 행을 잡으면 교착이 생긴다(PR #27). as-built §4.
- **스토리지 호출은 트랜잭션 밖**(AGENTS·as-built 규칙).
- **업로드 실패 사유**(`fail_reason`, varchar 라 값 추가에 마이그레이션 불필요): 지표 `drive_upload_session_ended_total{reason}` 은 enum 값을 모두 미리 만든다. 새 사유를 넣으면 as-built 의 목록도 고친다.
- **커밋·PR 서식**: 커밋 메시지 끝에 `Co-Authored-By` 줄, PR 본문 끝에 Claude Code 표시. 응답·리뷰는 한국어로 한다.
- **로컬 stash**: `stash@{0}` 은 이미 main 에 머지된 PR #8(관측) 작업본 사본이다. 필요 없으면 `git stash drop`.

## 5. 로컬 환경 메모 (Windows)
**PC 마다 다르다.**
- 두 번째 PC(2026-11-20 저녁 작업)
  - 기본 `java` 가 25 라서 `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot` 로 21 을 지정한다.
  - Maven 3.9.11 은 PATH 에 있다. 처음에 `-o` 로 돌리면 surefire 플러그인이 없어 실패하니 한 번은 온라인으로 돌린다. 그 뒤로는 `-o` 가 된다.
  - 예: `JAVA_HOME=... mvn -q -o test -Dfrontend.skip=true -Dtest='S3BucketInitializerTest'`
  - Docker·Python 이 없다. 통합 테스트는 PR CI 로, YAML 은 눈으로 확인했다.
- 첫 번째 PC: Maven 이 PATH 에 없어 `~/.m2/wrapper/dists/apache-maven-3.9.11/<hash>/bin/mvn` 을 썼다(리포에 `.mvn/wrapper` 없음).
  - 예: `M=$(find ~/.m2/wrapper/dists/apache-maven-3.9.11* -name mvn -path "*/bin/*" | head -1); "$M" -q -o test -Dfrontend.skip=true -Dtest='UploadChunkIntegrationTest'`
- 업로드 관련 주요 테스트:
  - 백엔드: `UploadChunkIntegrationTest`, `UploadWriteIntegrationTest`, `UploadSessionWriterTest`, `UploadContentInspectorTest`, `DefaultUploadSessionRepositoryTest`
  - 다운로드: `ApiGlobalExceptionHandlerTaskRejectedTest`, `CommittedResponseExceptionResolverTest`, `StreamingAsyncConfigTest`(Docker 없이 돈다)
  - 스토리지 초기화: `S3BucketInitializerTest`(Docker 없이 돈다)
  - 프론트: `DriveFront` 에서 `node --test tests/scripts/chunk-upload.test.mjs`(가짜 서버 22건)
- 통합 테스트는 Testcontainers(PG 18·MinIO)를 쓰므로 Docker 가 필요하다. 전체 테스트는 PR CI(`backend-test.yml`, 리눅스)가 돌린다.
  - 예전에 전체 실행에서만 흔들리던 `StorageDeletionSchedulerTimingIntegrationTest` 는 CI 결과로 계속 지켜본다.
- jOOQ 재생성: `docker-maven-plugin` 이 Docker 29 와 맞지 않는다. PG 18 컨테이너를 직접 띄우고 `-Pjooq-codegen -Ddocker.skip=true -Djooq-codegen.pg-port=<port>` 로 돌린다. 생성물의 `"this-escape"` 토큰은 지워 diff 를 줄인다.
- 동시성(교착) 회귀 테스트 패턴: `ResourceNamingLockConcurrencyIntegrationTest` 의 `race()`. 수정을 잠시 끄고 실패하는지 확인한 뒤 원복하는 식으로 검증해 왔다.
- Windows CRLF 파일은 `sed` 여러 줄 치환이 잘 안 된다. Edit 도구나 node 스크립트로 고친다(node 에서는 줄바꿈을 `String.fromCharCode(13,10)` 로 맞춘다).

## 6. 작업 방식(지금까지)
- 단계마다 worktree·브랜치 → 관련 테스트 → 커밋(작업 파일만) → PR → Codex 리뷰·CodeQL·CI 확인·반영 → 사용자 승인 후 머지 → worktree·브랜치 제거
- 문서는 코드와 같은 PR 에서 as-built·todo 를 고친다(끝난 todo 는 `[x]` + PR 번호). 이 인계 파일의 §2·§3 도 함께 고친다.
