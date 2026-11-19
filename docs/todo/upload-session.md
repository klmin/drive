# 업로드 세션 — 남은 작업

> 기준: 2026-11-19. 끝난 것 = 단계 0·1(MinIO)·2·3(PR #9·#19·#22 머지). **진행 중 = PR #27(이름 결정 경로 잠금, 브랜치 `fix/name-lock-parent-folder`) — Codex 리뷰 P1 3건 반영 후 재리뷰 대기.** 이 PR 이 머지되면 아래 "진행 중" 항목을 지운다.
> 지금 구조는 [현재 구현 문서](../design/upload-session-as-built.md), 결정 이유는 [설계 문서](../design/upload-session-redesign.md)(§ 번호는 이 문서 기준).
> 우선순위: **A** 다음에 할 것 / **B** 운영 전 필요 / **C** 여유 있을 때.

---

## 진행 중

- [ ] **PR #27 머지** — 이름 결정 경로(폴더 생성·이름 변경·이동·복원)가 소유자 사용자 행 → 이름 자리(부모 폴더) → 대상 순으로 잠금, 영구 삭제도 사용자 행 먼저. 남은 Codex 지적이 있으면 반영 후 머지

## A. 단계 4 — 청크 업로드 API + 프론트 (설계 §5.3·§5.4·§6.2·§6.3·§10)

지금은 큰 파일도 서버가 한 요청(multipart/form-data → Tomcat 임시 파일)으로 받는다. 브라우저가 파트를 직접 보내고 이어 올릴 수 있게 한다.
세션·커밋·정리는 이미 있으므로 **API 계층과 complete 검증만** 새로 만든다.

- [ ] **세션 API 컨트롤러** — `POST /uploads`, `PUT /uploads/{id}/parts/{n}`, `GET /uploads/{id}`, `GET /uploads?status=PENDING`, `POST /uploads/{id}/complete`, `DELETE /uploads/{id}`
  - 세션 생성: 지금 `UploadSessionWriter.write` 앞부분(Content-Type·base key·잠김 사전 확인 + `UploadSessionTxService.create`)을 분리해 재사용. 권한 검사(UPLOAD/EDIT)·크기 정책(`validateFileSizeLimit`)은 현재 `FileUseCase.upload`·`FileWriteOrchestrator` 와 같게
  - 32MB 이상만 멀티파트로 시작(`createMultipartUpload` → `attachStorage`, 실패는 `failWithAbort`), 응답 `{sessionId, partSize, partCount}`(uploadId 비노출). 파트 계획 = `UploadPartPlan`
  - 모든 조회는 `request_user_seq = 로그인 사용자`(`findByIdAndRequester`), 불일치 404
- [ ] **파트 PUT** — `@RequestBody byte[]` 금지, `HttpServletRequest.getInputStream()` 을 `StorageService.uploadPart` 로 그대로. `Content-Length` 없으면 411, 기대 크기(`UploadPartPlan.expectedPartSize`)와 다르면 400. PENDING 아니면 409 `UPLOAD_SESSION_FAILED`. 성공 후 `touch`. `NO_SUCH_UPLOAD` 면 `fail(PENDING, NO_SUCH_UPLOAD)`(정리 없음 — 이미 없음)
- [ ] **complete** — `markCompleting`(0행: COMPLETED 면 이전 결과 반환, COMPLETING 409, 그 외 410) → `listParts` 검증(개수·1..N 연속·각 크기·합계) → `completeMultipartUpload` → `headContentLength` → (매직바이트, 아래 B) → 커밋은 `UploadCommitService.commit*` 재사용. 실패 처리는 `UploadSessionWriter.failOrRecover` 와 같은 표(조립 전/중/후)
  - 리팩터링 후보: `UploadSessionWriter` 의 3·4·5단계(선점→조립→크기 확인→커밋→실패 처리)를 "내부 쓰기"와 "complete API"가 공유하도록 분리
- [ ] **취소** — `fail(PENDING, CANCELED, BEFORE_ASSEMBLE)`, 이미 끝났어도 성공(멱등)
- [ ] **사용량 화면 `uploadingByte`** — 사용량 조회 API 에 진행 중 합계(`UploadSessionRepository.sumActive`) 별도 필드. 알림·지표·관리자 화면·로그인 응답은 확정값만(설계 규칙 12)
- [ ] **프론트(DriveFront)** — Uppy `@uppy/aws-s3` 멀티파트 플러그인을 우리 API 에 연결, 세션당 동시 4, 409 받으면 중단(재시도·abort 안 함), 취소 버튼 → DELETE, 새로고침에는 abort 안 보냄, `localStorage` 지문으로 이어 올리기, 폴더 업로드 큐(동시 3~6), 사용량 막대 두 구간
- [ ] **앞단 프록시** `client_max_body_size` > 파트 크기(16MB+), 타임아웃 — 설치 문서(DriveInstaller)
- 완료 기준: 1GB 파일을 파트 업로드 → 중간에 끊고 `GET /uploads/{id}` 로 빠진 파트만 → complete. 동시 complete(중복 클릭)·complete 재요청 멱등·취소 후 파트 PUT 409·스케줄러 만료 후 409 통합 테스트

## B. 운영 전에 필요한 것

- [ ] **커밋 tx 권한 재확인**(설계 규칙 14, §6.3 ⑥) — 지금은 호출부(컨트롤러 aspect·MCP 툴)에서 한 번만. 청크 API 는 업로드가 길어 그 사이 공유 해제될 수 있으므로 커밋 tx 에서 `ResourcePermissionService.checkPermission` 을 다시. 세션에 요청자·익명 여부가 있어야 함(`RequestActor`) → 컬럼 추가 검토
- [ ] **매직바이트 검사**(규칙 3, §6.3 ⑤) — `StorageService.readRange(key, 0, 65535)` → Tika 로 실행 파일 차단·확장자 계열 불일치 차단 → `CONTENT_MISMATCH`. 내부 쓰기 경로에도 넣을지(웹오피스 저장은 신뢰 가능) 결정. 텍스트류는 판별 불가(한계 수용)
- [ ] **Content-Type 서버 결정 강제** — 지금은 클라이언트 값이 없을 때만 확장자로(Codex 리뷰 반영, PR #19). 규칙 3 대로 항상 서버가 정할지 결정(다운로드 `Content-Disposition: attachment`·`nosniff` 는 이미 적용)
- [ ] **감사 로그 발행 시점**(규칙 13) — 지금 `resourceAuditService.record` 는 호출부(`FileUseCase` 등)에서 커밋 뒤. 커밋 tx 안에서 발행 + `AFTER_COMMIT` 으로 옮길지 검토(감사 코드 자체는 수정하지 않음)
- [ ] **지표·알림**(설계 §13) — Micrometer: 상태별 세션 수, 가장 오래된 PENDING/COMPLETING 나이, 삭제 대기 적체·최고령·최종 실패 수. 알림: COMPLETING 30분 초과(`UploadSessionCleanupProcessor` 가 지금 WARN 로그만), 삭제 최종 실패 행(`REFERENCED` 포함)
- [ ] **Ceph·RustFS 계약 테스트**(단계 1 나머지) — `S3StorageServiceMultipartContractTest` 를 이미지만 바꿔 실행. 확인할 것: abort 직후 동작, NoSuchUpload 코드, `AbortIncompleteMultipartUpload` lifecycle 지원(MinIO 는 미지원), 파트 체크섬·`If-None-Match`
- [ ] **운영 버킷 lifecycle 확인** — 기동 시 자동 병합 PUT 이 운영 버킷 정책과 충돌하지 않는지, 권한 없는 설치처는 `drive.upload.manage-bucket-lifecycle=false` + 수동 설정 안내(DriveInstaller 문서)
- [ ] **`tb_resource_version.resource_save_name` 인덱스 생성 잠금**(V6) — 큰 설치처면 배포 시 쓰기 잠금. 이미 배포된 곳이 없으면 그대로, 있으면 `CREATE INDEX CONCURRENTLY` 별도 마이그레이션 검토

## C. 정리·후속

- [ ] **옛 흐름 호환 코드 제거** — 새 흐름은 PENDING 버전 행·저장 마커를 남기지 않는다. 배포 후 `SELECT count(*) FROM tb_resource_version WHERE status <> 'SUCCESS'` 가 0 이면 제거:
  `DefaultResourceService.cleanVersionIfExists`, `DefaultResourceVersionService.cleanVersionIfExists/finalizeAfterStorageUpload/rollbackResourceVersionMetadata`, `StorageService.findSaveMarker`, `StorageConstants.SAVE_MARKER_METADATA_KEY`, `S3StorageServiceSaveMarkerTest`, `ResourceVersionStatus.PENDING·DELETE` 사용처, PENDING 만료 판정(`ResourceVersionRepository.existsAliveVersion/findExpiredVersion` 의 threshold)
  - 주의: `cleanVersionIfExists` 는 커밋 tx 안에서 S3 HEAD(저장 마커 조회)를 한다 — 옛 PENDING 행이 있을 때만
- [ ] **호출부 없는 코드 제거** — `ResourceRepository.revertOverwrite`(+JPQL·`ResourceRevertOverWriteQuery`·`OVERWRITE_REVERT_FAIL`·`DefaultResourceService.revertOverwrite`), `StorageService.uploadPartsFromStream`·`copyObjectAnySize`(계약 테스트만 사용 — 청크 API 에서 쓸지 보고 결정), `QuotaService.reserveQuota/reserveQuotaInTx`(다른 호출부 확인 후)
- [ ] **`FileWriteOrchestrator` 이름** — 지금은 크기 정책 확인 후 `UploadSessionWriter` 로 넘기기만 한다. 호출부 6곳을 `UploadSessionWriter`(또는 파사드)로 바꾸고 클래스 제거 검토
- [ ] **설계 문서 규칙 8 갱신** — "사용자 행(루트면)" → "사용자 행 항상 먼저"(PR #27). [as-built §4](../design/upload-session-as-built.md) 에는 반영됨
- [ ] **교착 남은 조합 점검** — 사용자 행을 먼저 잡지 않으면서 리소스 행 뒤에 사용자 행을 잡는 경로가 새로 생기면 교착이 된다(PR #27 리뷰에서 3건). 새 쓰기 경로를 만들 때 as-built §4 순서를 지키고, 관리자 사용자 삭제(`UserPermanentDeletionService`)·테넌트 정리처럼 아직 검증 안 한 경로는 동시성 테스트로 확인
- [ ] **관리자 용량 축소 시 진행 중 세션 중단**(단계 5 선택) — 축소 tx 에서 `확정 + 진행 중 > 새 한도` 면 PENDING 세션을 최근 순으로 `FAILED(QUOTA_EXCEEDED)` + ABORT
- [ ] **presigned 직접 업로드 옵션**(단계 5 선택) — `upload.mode = PROXY | PRESIGNED`, 버킷 CORS `ExposeHeaders: ETag`
- [ ] **대용량 복원·복사 비동기**(설계 §6.4) — 수 GB 이상은 세션 ID 를 바로 돌려주고 `GET /uploads/{id}` 로 확인. 기준 크기 결정
- [ ] **다운로드 Range**(별건) — 206·`If-Range`·ETag, 다운로드 토큰 만료 연장

## 테스트 환경 메모 (전체 테스트 돌릴 때)
- 전체 실행 시 실패가 많이 보이는 기존 원인 — 이번 작업과 무관:
  - test 프로필 없는 MVC 테스트(`AbstractMvcTest` 계열)가 `${DRIVE_DATABASE_HOST}` 를 찾아 컨텍스트 실패
  - 커밋형 테스트(`ManagedSingleQuotaIntegrationTest` 등)가 테넌트 1 이름을 `quota-test` 로 남겨 저장소 테스트 단언 실패(실행 순서 의존)
  - 컨텍스트가 많이 쌓이면 Testcontainers PG `too many clients`
  - IntelliJ 자동 빌드가 `target/classes` 를 다시 쓰면 `NoClassDefFoundError` — 전체 실행 중엔 IDE 빌드 중지
- jOOQ 재생성: `docker-maven-plugin` 이 Docker 29 와 안 맞음 → PG 18 컨테이너를 직접 띄우고 `-Ddocker.skip=true -Djooq-codegen.pg-port=<port>`, 결과의 `"this-escape"` 경고 토큰은 지워 diff 최소화
