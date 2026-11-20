# 업로드 세션 — 남은 작업

> 기준: 2026-11-20. 끝난 것 = 단계 0~3(PR #9·#19·#22·#27), 단계 4(PR #38·#40·#43·#45·#49), B 보안 항목(PR #51·#53·#55·#57). **배포된 설치처 없음**(2026-11-20 확인).
> 새 세션은 [next-session.md](next-session.md) §3 "다음 할 일"부터 본다.
> 지금 구조는 [현재 구현 문서](../design/upload-session-as-built.md), 결정 이유는 [설계 문서](../design/upload-session-redesign.md)(§ 번호는 이 문서 기준).
> 우선순위: **A** 다음에 할 것 / **B** 운영 전 필요 / **C** 여유 있을 때.

---

## A. 단계 4 — 청크 업로드 API + 프론트 (설계 §5.3·§5.4·§6.2·§6.3·§10)

지금은 큰 파일도 서버가 한 요청(multipart/form-data → Tomcat 임시 파일)으로 받는다. 브라우저가 파트를 직접 보내고 이어 올릴 수 있게 한다.
세션·커밋·정리는 이미 있으므로 **API 계층과 complete 검증만** 새로 만든다.

- [x] **세션 API 컨트롤러**(PR #38) — `/api/v1/uploads` 6개. `UploadSessionWriter.open` 을 분리해 재사용, 권한·크기 정책은 웹 업로드와 같게, 32MB 미만은 400(기존 단일 요청 API 사용 — 이유는 [as-built §3.5](../design/upload-session-as-built.md)), 남의 세션·내부 쓰기 세션은 404
- [x] **파트 PUT** — 요청 스트림 그대로 `uploadPart`, 411·400·409·`touch`, `NO_SUCH_UPLOAD` 면 세션 FAILED
- [x] **complete** — 선점 0행 분기(이전 결과 / 409 / 410), ListParts 검증, 선점 뒤는 `UploadSessionWriter.finishClaimed` 를 내부 쓰기와 공유. 매직바이트는 아직(B)
- [x] **취소** — `fail(PENDING, CANCELED, BEFORE_ASSEMBLE)`, 이미 끝났어도 성공
- [ ] **청크 API 외부 공개 여부** — 지금은 내부 웹 API 만(`@ExternalApi` 없음). API Key·MCP 로 큰 파일을 올릴 필요가 생기면 `/api/public` 노출·scope(WRITE) 검토
- [x] **사용량 `uploadingByte`**(PR #40) — `GET /api/v1/users/quota` 에 진행 중 합계 별도 필드(용량 주인 기준, 공유 폴더에 남이 올리는 중인 크기 포함). 막대는 확정값만 — 이 값은 IS_FULL 안내용(설계 규칙 12, 2026-11-20 결정). 알림·지표·관리자 화면·로그인 응답은 확정값만(설계 규칙 12)
- [x] **덮어쓰기 사전 필터 증가분**(PR #43, V7 `reserve_byte`) — 덮어쓰기 세션은 시작 검사·진행 중 합계에 새 − 기존 크기(0 이상)만. 버전 저장·새 파일은 전체 크기 그대로
- [x] **프론트(DriveFront)**(PR #45) — Uppy 대신 직접 만든 업로더(`utils/chunkUpload.js`, 이유는 설계 §10), 세션당 동시 4, 409 받으면 중단(재시도·abort 안 함), 취소 버튼 → DELETE, 새로고침에는 abort 안 보냄, `localStorage` 지문으로 이어 올리기, 폴더 업로드 큐(기존 다이얼로그 동시 3), 세션 시작 IS_FULL 이면 내 진행 중 청크 업로드가 있을 때 "진행 중 업로드가 공간을 쓰고 있음" + 목록·취소 안내(막대는 확정값만, 공유 폴더 때문에 내 uploadingByte 로 판단하지 않음)
- [x] **앞단 프록시**(PR #49) — [DriveInstaller/thinkfree/TFD/template/httpd/README.md](../../DriveInstaller/thinkfree/TFD/template/httpd/README.md): 본문 상한 1GiB 권장(가장 큰 파트 이상), 응답 대기 300초, nginx·ingress-nginx 예시. 번들 Apache 는 `ProxyTimeout 300` 추가(20MB 파트 통과 확인). Developers 저장소의 k8s ingress 에는 아직 반영 안 함
- 완료 기준: 1GB 파일을 파트 업로드 → 중간에 끊고 `GET /uploads/{id}` 로 빠진 파트만 → complete. 동시 complete(중복 클릭)·complete 재요청 멱등·취소 후 파트 PUT 409·스케줄러 만료 후 409 통합 테스트
  - 통합 테스트는 `UploadChunkIntegrationTest`(PG + 메모리 멀티파트 가짜, 33MB), 업로더는 `tests/scripts/chunk-upload.test.mjs`(가짜 서버)로 끝남. 남은 것: 실제 MinIO 로 1GB 를 브라우저에서 끊었다 이어 올리기(로컬 백엔드 실행 필요). **조립(complete multipart) 시간도 잴 것**: COMPLETING 30분(`drive.upload.completing-timeout`)을 넘기면 정리 스케줄러가 FAILED + 객체 삭제를 하므로(`UploadSessionCleanupService`) 여유가 있는지 확인한다. 세션 TTL·COMPLETING 숫자는 이 측정 전에는 바꾸지 않는다

## B. 운영 전에 필요한 것

- [x] **커밋 tx 권한 재확인**(PR #51, 설계 규칙 14) — 청크 업로드 complete 는 커밋 tx 에서 행 잠금 뒤 `ResourcePermissionService.checkPermission` 을 다시(요청자 = 로그인 사용자, 청크 API 는 익명 불가라 컬럼 추가 없이 `RequestActor.user`). 실패 = `PERMISSION_DENIED` FAILED + DELETE. 서버 내부 쓰기는 호출부 검사만 유지(짧고, 익명 공유 링크 업로드는 세션에 맥락이 없어 재구성 불가) — 필요해지면 세션에 `request_anonymous` 컬럼 추가
- [x] **매직바이트 검사**(PR #53, 규칙 3) — 조립 뒤 커밋 전 앞 64KB 를 Tika 로 판별해 확장자와 형식 계열이 다르거나 실행 파일이면 FAILED(`CONTENT_MISMATCH`) + DELETE, 415 `UPLOAD_010`. 사용자 업로드(단일·청크·웹오피스 저장) 모두, 복사·복원은 제외. 실행 파일 차단은 `drive.upload.block-executable-content`(기본 true). 텍스트류는 판별 불가(통과)
- [x] **Content-Type 서버 결정 강제**(PR #55, 규칙 3) — 클라이언트 값은 버리고 항상 확장자(Tika 확장자 사전)로, 모르면 `application/octet-stream`. 버전 저장·덮어쓰기·복원은 대상 파일의 확장자. PR #19 에서 클라이언트 값을 남긴 건 값이 없을 때 NOT NULL 오류를 막으려던 것이라 의도된 결정이 아니었다
- [x] **업로드 중 대상 삭제 조기 실패**(PR #67) — 파트 PUT 때 대상이 살아 있는지 조회해 없으면 FAILED(`TARGET_DELETED`) + ABORT + 409 `UPLOAD_001`
  - 문제: `UploadChunkService.uploadPart` 는 세션 상태·파트 크기만 보고, 대상 파일(버전·덮어쓰기)·부모 폴더(신규)가 살아 있는지는 보지 않는다. 그 사이 삭제되면 수 GB 를 다 올린 뒤 complete 커밋 tx 에서야 실패한다(데이터는 안전: FAILED + DELETE).
  - 수정: 파트 PUT 때 가벼운 존재 조회를 하고, 없으면 `fail(PENDING, …, BEFORE_ASSEMBLE)` + 409.
  - 완료 기준: `UploadChunkIntegrationTest` 에 "업로드 중 대상·부모 삭제 → 다음 파트 409" 추가
- [ ] **감사 로그 발행 시점**(규칙 13) — 지금 `resourceAuditService.record` 는 호출부(`FileUseCase` 등)에서 커밋 뒤. 커밋 tx 안에서 발행 + `AFTER_COMMIT` 으로 옮길지 검토(감사 코드 자체는 수정하지 않음)
- [x] **지표·알림**(설계 §13) — Micrometer 대신 DB 지표(Alloy `observability/alloy/pg-queries.yaml`)로 냈다. 앱 인스턴스 수와 상관없이 DB 기준 한 벌만 나온다.
  - 업로드 세션(PR #50): `drive_upload_active_sessions{status}`·`drive_upload_active_reserved_bytes{status}`·`drive_upload_active_oldest_idle_seconds{status}`·`drive_upload_active_oldest_completing_seconds{status}`(PENDING·COMPLETING, 0 이어도 두 상태 모두 나옴)
  - 삭제 대기열(PR #56): `drive_storage_deletion_pending`·`deferred`·`overdue`·`overdue_max_seconds`·`exhausted`(`{action}`, `REFERENCED` 포함 action 별)
  - 알림(`observability/grafana/provisioning/alerting/drive-rules.yaml`): 업로드 세션 정리 안 됨(`drive-upload-session-stale`, PENDING 활동 없음이 TTL 을 넘김, PR #50), 삭제 대기 기한 지남(`drive-storage-deletion-overdue`)·시도 상한 도달(`drive-storage-deletion-exhausted`, 최종 실패 행, PR #56)
  - [x] **COMPLETING 30분 초과 알림**(PR #68) — 시간 초과 실패 사유를 `COMMIT_ERROR` 에서 `COMPLETING_TIMEOUT` 으로 분리해 `drive_upload_session_ended_total{reason="completing_timeout"}` 로 센다. 알림 두 개: 시간 초과 발생(`drive-upload-completing-timeout`, 15분 안에 1건 이상) · 시간 초과가 지났는데 정리 안 됨(`drive-upload-completing-stale`, `drive_upload_active_oldest_completing_seconds` > 45분). 시간 제한 30분 자체는 그대로 두고, 1GB 조립 시간 측정(next-session §3-3) 뒤에 다시 정한다
- [ ] **Ceph·RustFS 계약 테스트**(단계 1 나머지) — `S3StorageServiceMultipartContractTest` 를 이미지만 바꿔 실행. 확인할 것: abort 직후 동작, NoSuchUpload 코드, `AbortIncompleteMultipartUpload` lifecycle 지원(MinIO 는 미지원), 파트 체크섬·`If-None-Match`
- [x] **운영 버킷 lifecycle 확인**(PR #69) — 자동 병합 PUT 이 운영 정책과 부딪히는 경우 3가지를 고쳤다: 운영자의 버킷 전체 abort 규칙이 같거나 짧으면 겹치는 규칙을 더하지 않음, 예전 형식(Prefix) 규칙은 Filter 로 바꿔 형식 혼합 거절을 막음, 객체 만료 규칙은 경고(사용자 파일 삭제 위험). 권한 없는 설치처는 `DRIVE_UPLOAD_MANAGEBUCKETLIFECYCLE=false` + 수동 설정(`DriveInstaller/docs/storage-bucket-lifecycle.md`). 실제 AWS·Ceph 버킷에서는 아직 확인하지 않았다
- [x] **CodeQL 결과 업로드 실패**(PR #35) — private 저장소라 업로드 없이 분석만(`upload: never`), 결과는 실행 Summary·SARIF 아티팩트
- [x] **CodeQL 발견 검토** — #35 실행 기준 Java 31건·JS 2건(PR #35 의 Actions 실행 → Summary 표, 아티팩트 `codeql-sarif-<언어>`). 진짜 문제는 고치고 오탐은 이유를 남긴 뒤, `.github/workflows/codeql.yml` 의 `FAIL_ON_FINDINGS` 를 `'true'` 로 켜서 새 발견이 PR 체크를 실패시키게 한다(오탐 제외 수단이 없으므로 켜기 전에 0건으로 만들거나 쿼리·경로 제외를 정할 것) → PR #57 로 정리(실제 문제 28건 수정·오탐 4건은 규칙 제외·이름 변경), `FAIL_ON_FINDINGS` 켬
- [x] **`tb_resource_version.resource_save_name` 인덱스 생성 잠금**(V6) — 큰 설치처면 배포 시 쓰기 잠금. 이미 배포된 곳이 없으면 그대로, 있으면 `CREATE INDEX CONCURRENTLY` 별도 마이그레이션 검토 → 2026-11-20 배포된 곳이 없음을 확인해 그대로 둔다

## C. 정리·후속

- [x] **옛 흐름 호환 코드 제거**(PR #63) — 새 흐름은 PENDING 버전 행·저장 마커를 남기지 않는다. **배포된 설치처가 없으므로(2026-11-20) 기다리지 않고 지금 제거한다.** 남겨 두면 롤링 배포 중 옛 서버의 3분 만료 PENDING 이 좀비 시나리오(옛 서버가 원본 key 를 덮어씀)와 커밋 tx 안 S3 HEAD 를 다시 만든다. 대상:
  `DefaultResourceService.cleanVersionIfExists`, `DefaultResourceVersionService.cleanVersionIfExists/finalizeAfterStorageUpload/rollbackResourceVersionMetadata`, `StorageService.findSaveMarker`, `StorageConstants.SAVE_MARKER_METADATA_KEY`, `S3StorageServiceSaveMarkerTest`, `ResourceVersionStatus.PENDING·DELETE` 사용처, PENDING 만료 판정(`ResourceVersionRepository.existsAliveVersion/findExpiredVersion` 의 threshold)
  - 호출부: `DefaultResourceService` 의 `replaceWithVersioning`·`overwriteContent`·`deleteVersion`(커밋 tx 안). `existsAliveVersion` 이 3분 안 PENDING 이면 `VERSION_PROCESSING` 으로 거절하는 분기도 함께 정리
  - 완료 기준: 위 코드·테스트를 제거한 뒤 업로드·버전 관련 통합 테스트 통과, as-built 의 호환 코드 언급 정리
- [ ] **호출부 없는 코드 제거** — `ResourceRepository.revertOverwrite`(+JPQL·`ResourceRevertOverWriteQuery`·`OVERWRITE_REVERT_FAIL`·`DefaultResourceService.revertOverwrite`), `StorageService.uploadPartsFromStream`·`copyObjectAnySize`(계약 테스트만 사용 — 청크 API 에서 쓸지 보고 결정), `QuotaService.reserveQuota/reserveQuotaInTx`(다른 호출부 확인 후)
- [ ] **`FileWriteOrchestrator` 이름** — 지금은 크기 정책 확인 후 `UploadSessionWriter` 로 넘기기만 한다. 호출부 6곳을 `UploadSessionWriter`(또는 파사드)로 바꾸고 클래스 제거 검토
- [x] **설계 문서 규칙 8 갱신**(PR #31) — "사용자 행(루트면)" → "사용자 행 항상 먼저"(PR #27). [as-built §4](../design/upload-session-as-built.md) 에는 반영됨
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
