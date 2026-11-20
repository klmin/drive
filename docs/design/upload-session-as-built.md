# 업로드 세션 — 현재 구현 (as-built)

> 기준: 2026-11-19. 단계 0~3(PR #9·#19·#22·#27)이 모두 `main` 에 머지된 상태. 지금 코드가 **실제로 어떻게 동작하는지**를 정리한 문서.
> 설계 결정의 이유·검토한 대안은 [upload-session-redesign.md](upload-session-redesign.md), 남은 일은 [../todo/upload-session.md](../todo/upload-session.md).
> 코드가 바뀌면 이 문서를 같은 PR 에서 고친다.

---

## 1. 한눈에

모든 파일 쓰기(웹 업로드·MCP 업로드·파일 복사·템플릿 문서 생성·버전 저장·덮어쓰기·버전 복원·웹오피스 저장)가 **세션 → 저장 → 커밋** 한 경로를 탄다.

```
FileWriteOrchestrator.write          테넌트 파일 크기 정책 확인 후 넘김(진입점, 호출부 무변경)
  └─ UploadSessionWriter.write        트랜잭션 없음 — 아래 단계를 순서대로
       1. 세션 생성        open → UploadSessionTxService.create  (짧은 tx, REQUIRES_NEW)
       2. 스토리지 저장    StorageService                       (tx 밖)
       3. COMPLETING 선점  UploadSessionTxService.markCompleting (짧은 tx)
       4. (멀티파트면) 조립 → 저장 크기 확인 → 매직바이트 ┐ finishClaimed
       5. 커밋             UploadCommitService.commit*           │ (짧은 tx, 일시적 DB 오류 3회 재시도)
       실패 시             UploadSessionTxService.fail           ┘ (FAILED + 정리 등록, 같은 tx)
```
청크 업로드 API(`/api/v1/uploads`, §3.5)는 1(`open`)과 4~5(`finishClaimed`)를 같은 코드로 쓰고, 2·3 만 요청 단위로 나눈다(파트 PUT, complete 선점).

- **불변 key**: 새 내용은 항상 새 key `{tenantSeq}/{세션 uuidv7}`(테넌트 없으면 `0/`). key 는 세션 생성 때 한 번 계산하고, 이후 조회·삭제는 DB 에 저장된 값으로만 한다. 버전·덮어쓰기는 S3 복사 없이 **DB 포인터만** 바꾼다.
- **사용량(`tb_user.use_quota_byte`)은 커밋 tx 에서만** 바뀐다. 실패하면 되돌릴 것이 없다(보상 로직 없음).
- **정리는 상태를 끝내는 쪽이 같은 tx 에서 등록**한다(`tb_storage_deletion`). 실제 S3 abort·delete 는 삭제 워커가 한다.
- 큰 파일은 청크 업로드 API 로 브라우저가 파트를 하나씩 보내고 이어 올릴 수 있다(백엔드만 — 프론트 연결은 아직, todo A). 32MB 미만과 다른 진입점(MCP·복사·웹오피스 등)은 서버가 내용을 한 번에 받는다.

---

## 2. 데이터

### 2.1 `tb_upload_session` (V6)
| 컬럼 | 의미 |
|---|---|
| `upload_session_id` | uuidv7, 세션 ID 겸 key 의 uuid |
| `tenant_seq`, `user_seq`, `request_user_seq` | 테넌트 / 소유자(용량 대상) / 요청자 |
| `save_type` | `DUPLICATE` / `VERSION` / `OVERWRITE` / `RESTORE` |
| `parent_folder_seq`, `resource_seq` | 신규면 부모 폴더, 그 외 대상 리소스 |
| `base_storage_key` | OVERWRITE 충돌 감지 기준(세션 시작 때 대상의 key) |
| `file_name`, `content_type`, `size_byte` | 요청 이름 / 서버가 정한 Content-Type / 신고 크기 |
| `reserve_byte` (V7) | 진행 중 용량으로 셀 크기 — OVERWRITE 는 새 크기 − 시작 시 대상 크기(0 이상), 그 외 `size_byte`. NULL(V7 이전 행)이면 `size_byte` |
| `storage_key`, `upload_id` | 스토리지 준비 후 채움. `upload_id` 는 멀티파트일 때만 |
| `status`, `fail_reason`, `last_error` | 상태·실패 사유 |
| `result_resource_seq`, `result_name` | COMPLETED 결과(커밋 뒤 예외 복구·멱등 응답용) |
| `last_activity`, `completing_at`, `create_date` | DB `now()` 기준 시각 |

상태: `PENDING → COMPLETING → COMPLETED`, 어디서든 `→ FAILED(fail_reason)`. 상태는 `WHERE status = :expected` 조건부 UPDATE 로만 바꾼다(`UploadSessionRepository`).
**COMPLETED 전환은 리소스 반영과 같은 커밋 tx** — `COMPLETING = 미반영`, `COMPLETED = 반영` 이 항상 성립한다.

### 2.2 `tb_storage_deletion` 확장 (V6)
`action`(`DELETE` / `ABORT_MULTIPART`) + `upload_id`. 기존 등록 코드는 DEFAULT `DELETE` 로 그대로 동작.

### 2.3 버전 행
`tb_resource_version` 행은 이제 **SUCCESS 로만** 생긴다(같은 tx 에서 INSERT 후 상태 UPDATE — 다른 tx 에 PENDING 이 보이지 않음). 버전 행의 key = 넘겨받은 이전 현재 key.

---

## 3. 흐름

### 3.1 세션 생성 (`UploadSessionTxService.create`)
사용자 행 `FOR NO KEY UPDATE` → **용량 사전 필터**(확정 + 진행 중 합계 + 이번 `reserve_byte` ≤ 한도, 아니면 `IS_FULL`. 진행 중 합계도 `reserve_byte` 기준) → 진행 중 세션 수 < 50(아니면 `UPLOAD_SESSION_LIMIT` 429) → INSERT(PENDING).
`IS_FULL` 이면 tx 밖에서 용량 초과 알림(`QuotaService.notifyQuotaExceeded`).

그 전에 `UploadSessionWriter` 가 하는 것:
- Content-Type: **클라이언트 값은 버리고 서버가 확장자로**(Tika 확장자 사전 — MCP 업로드·매직바이트 검사와 같은 사전), 모르면 `application/octet-stream`(규칙 3). 버전 저장·덮어쓰기·복원은 대상 파일의 확장자(이름·확장자가 대상 것으로 유지되므로). MCP 가 확장자 없는 평문에 붙이던 `text/plain` 도 이제 `application/octet-stream` — 미리보기·읽기는 확장자 기준이라 영향 없음.
- OVERWRITE: 대상의 현재 key 를 `base_storage_key` 로(대상이 없으면 `NOT_FOUND`).
- 복원(`unlockedRequired`): 잠긴 파일이면 스토리지 복사 전에 미리 `RESOURCE_LOCKED`(최종 판정은 커밋 tx).

### 3.2 스토리지 저장 (`UploadSessionWriter.store`)
| 출처 | 방식 |
|---|---|
| 업로드 파일·스트림, 32MB 미만(0바이트 포함) | key 기록 → `putObject` |
| 업로드 파일·스트림, 32MB 이상 | `createMultipartUpload` → uploadId 기록 → 파트(`max(16MB, ceil(size/10000))`)마다 `uploadPart` + `touch`(last_activity 갱신) |
| 스토리지 객체(복사·복원), 5GiB 이하 | key 기록 → `copy`(CopyObject) |
| 스토리지 객체, 5GiB 초과 | 멀티파트 시작 → uploadId 기록 → 파트(≥512MiB)마다 `uploadPartCopy` + `touch` |

- uploadId 기록이 실패하면 세션 행에 uploadId 가 없어 일반 실패 처리로는 abort 가 안 걸리므로 `failWithAbort` 가 받은 uploadId 로 직접 ABORT 등록.
- `touch` 가 0행(그 사이 스케줄러 만료·취소)이면 남은 파트를 올리지 않고 `UPLOAD_SESSION_FAILED`.
- 신고 크기와 실제 길이가 다르면 스토리지를 부르지 않고 `UPLOAD_SIZE_MISMATCH`.

### 3.3 커밋 (`UploadCommitService`)
| 유형 | 커밋 tx |
|---|---|
| DUPLICATE | 사용자 행 → **부모 폴더 행**(루트면 사용자 행이 대신) 잠금 → 용량 최종 판정 → 이름 넘버링 + 파일 행 INSERT(key = 세션 key, `saveUploadedFile`) → 사용량 +크기 |
| VERSION·RESTORE | 사용자 행 → 리소스 행 → 버전 행 잠금 → 현재 key·크기·등록일을 SUCCESS 버전 행으로(`pushCurrentAsVersion`) → 리소스를 세션 key·새 크기로(`replaceWithVersioning`). 버전 한도 초과 시 가장 오래된 버전 행 삭제·번호 당김 → 사용량 +새 크기 −밀려난 크기, 밀려난 key **24h 유예 삭제** |
| OVERWRITE | 사용자 행 → 리소스 행 잠금 → 현재 key == base key 일 때만 key·크기 교체(JPQL `overwrite` 낙관적 조건) → 사용량 +(새−이전), 이전 key **24h 유예 삭제**. 다르면(그 사이 다른 저장) **버전 저장으로** 반영 |

**매직바이트(규칙 3)** — `finishClaimed` 가 조립·크기 확인 뒤, 커밋 전에 객체 앞 64KB 를 `readRange` 로 읽어 Tika 로 내용만 판별(`UploadContentInspector`). 확장자 사전 조회 결과와 형식 계열(미디어·PDF·컨테이너·실행 파일)이 다르면, 또는 실행 파일이면(`block-executable-content`. 끄면 확장자가 없거나 형식을 단정하지 않는 이름(`ls`·`.bin`)의 실행 파일은 받고, `.pdf`·`.txt` 처럼 다른 형식을 말하는 이름이면 위장으로 거절) `UPLOAD_CONTENT_MISMATCH`(415 `UPLOAD_010`) → FAILED(`CONTENT_MISMATCH`) + DELETE. 계열을 모르는 쪽(텍스트·HTML·SVG·알 수 없는 바이너리)은 통과, 같은 계열 안의 차이(docx↔xlsx, mp4↔mov)는 보지 않는다. 확장자는 신규면 업로드한 파일명, **버전 저장·덮어쓰기면 대상 파일의 확장자**(`UploadLockRepository.findFileExt`) — 대상의 이름·확장자는 그대로 두고 내용만 바꾸므로. 복사·복원(`StorageCopy`)은 이미 저장소에 있던 내용이라 건너뛴다. 단일 요청·청크·웹오피스 저장 모두 같은 경로.

**권한 재확인(규칙 14)** — 청크 업로드 complete 는 행을 잠근 직후 요청자 권한을 다시 본다(`recheckActor` = 요청자, 신규는 부모 폴더 UPLOAD, 버전·덮어쓰기는 대상 EDIT — 세션 생성과 같은 기준). 업로드가 길어 그 사이 공유가 해제될 수 있어서다. 실패하면 `PERMISSION_DENIED` 로 FAILED + 조립된 객체 DELETE 등록, 403. 서버 내부 쓰기(`recheckActor` = null)는 호출부(aspect·툴)가 직전에 확인하므로 건너뛴다 — 세션에 익명 여부·공유 링크 맥락을 남기지 않아 커밋 tx 에서 재구성할 수 없다.

공통 마무리: `markCompleted`(0행이면 예외 → 전체 롤백 — 스케줄러가 이미 FAILED 처리) → `FileUploadedEvent` 를 tx 안에서 발행(리스너가 `AFTER_COMMIT` 이라 커밋 성공 시에만 사용량 후처리).

### 3.4 실패 처리 (`UploadSessionWriter.failOrRecover`)
진행 위치에 따라 기대 상태·정리 방식이 정해진다.

| 위치 | 기대 상태 | 정리 등록(`UploadSessionStorageCleanup`) |
|---|---|---|
| 조립 전(PUT·파트·복사 중) | PENDING | 멀티파트 ABORT, 단일 요청이면 DELETE |
| 조립 전(complete 의 파트 검증) | COMPLETING | ABORT |
| 조립 중(complete 결과 불명확) | COMPLETING | ABORT + DELETE |
| 조립 후(크기 확인·매직바이트·커밋) | COMPLETING | DELETE |

- 조건부 UPDATE 가 0행이면 커밋이 실제로는 성공했을 수 있다(커밋 뒤 예외·응답 유실) — 세션을 다시 읽어 COMPLETED 면 저장된 결과를 돌려준다(**DB 상태가 정본**).
- 실패 기록조차 못 하면 세션은 PENDING/COMPLETING 으로 남고 스케줄러가 정리한다.
- `fail_reason`: `QUOTA_EXCEEDED`(IS_FULL), `VALIDATION`(그 밖의 업무 오류), `STORAGE_ERROR`(조립 전 스토리지 오류), `COMMIT_ERROR`(그 외), `CONTENT_MISMATCH`(매직바이트 — 확장자 위장·실행 파일), 청크 API 에서 `CANCELED`(취소)·`NO_SUCH_UPLOAD`(파트 PUT 중 스토리지 업로드 사라짐)·`PERMISSION_DENIED`(커밋 tx 권한 재확인 실패).

### 3.5 청크 업로드 API (`/api/v1/uploads`)
`UploadSessionController` → `UploadSessionUseCase`(권한·감사) → `UploadChunkService`(트랜잭션 없음). 로그인 사용자(USER)만, 익명 불가.

| 요청 | 동작 |
|---|---|
| `POST /api/v1/uploads` `{sharedByUserSeq?, parentFolderSeq?, resourceSeq?, fileName, size, saveType?}` | 권한(신규 = 부모 폴더 UPLOAD, 그 외 = 대상 EDIT) → 32MB 미만 400 `UPLOAD_008` → 절대 상한·테넌트 크기 정책 → `open`(용량 사전 필터·세션 수, 파트 계획을 `part_size`·`part_count` 에 저장) → `createMultipartUpload` → uploadId 기록. 응답 `{sessionId, partSize, partCount, maxConcurrentParts}` |
| `PUT /{id}/parts/{n}` (octet-stream) | PENDING 아니면 409 `UPLOAD_001`, `Content-Length` 없으면 411, 번호 범위 밖·크기 다르면 400 `UPLOAD_008` → 요청 스트림을 `uploadPart` 로 그대로 → `touch`(0행 409). `NoSuchUpload` 면 FAILED(NO_SUCH_UPLOAD) + 409 |
| `GET /{id}` | 상태 + PENDING 이면 ListParts 에서 기대 크기와 같은 파트 번호(`uploadedParts`) |
| `GET /?status=PENDING` | 내 청크 세션 목록(최근 순, 최대 50) — 스토리지 호출 없음 |
| `POST /{id}/complete` | (PENDING 이면) 권한 재확인 → 선점. 0행이면 COMPLETED = 이전 결과(감사 기록 안 함), COMPLETING = 409 `UPLOAD_005`, FAILED = 410 `UPLOAD_006` → ListParts 검증(개수·1..N·각 크기, 실패 시 400 `UPLOAD_009` + FAILED(VALIDATION) + ABORT) → `finishClaimed` → 감사(FILE_UPLOAD, 덮어쓰기면 FILE_EDIT) |
| `DELETE /{id}` | PENDING 이면 FAILED(CANCELED) + ABORT. 이미 끝났으면 그 상태로 성공(멱등) |

- 모든 조회는 `request_user_seq = 로그인 사용자`, 남의 세션은 404 `UPLOAD_003`.
- **청크 API 세션 = `part_count` 가 있는 세션.** 서버 내부 쓰기 세션은 같은 요청자라도 이 API 로 조회·complete·취소할 수 없다(진행 중인 내부 쓰기를 가로채지 못하게).
- 32MB 미만을 받지 않는 이유: 단일 객체 PUT 은 complete 뒤 늦게 도착한 PUT 이 커밋된 객체를 덮어쓸 수 있다(멀티파트는 complete 뒤 파트 PUT 이 NoSuchUpload 로 실패). 작은 파일은 기존 `POST /api/v1/files`.
- 파트 계획은 세션에 저장된 값을 쓴다 — 설정(`min-part-size`)이 바뀌어도 진행 중 세션은 시작 때 계획 그대로.
- **사용량 `uploadingByte`** — `GET /api/v1/users/quota` 응답에 진행 중(PENDING·COMPLETING) 세션의 `reserve_byte` 합계를 `useQuotaByte` 와 별도 필드로(`UploadSessionTxService.sumUploadingByte` → `sumActive`). 기준은 용량 주인(`user_seq`)이라 공유 폴더에 남이 올리는 중인 크기도 포함된다(용량 사전 필터와 같은 기준). 사용량 막대는 확정값만 그린다(설계 규칙 12). IS_FULL 안내는 이 값이 아니라 내 진행 중 청크 업로드 목록으로 판단한다(공유 폴더에 올릴 때 검사 대상은 폴더 주인이라 내 값으로는 못 맞춘다). 지금 프론트는 이 값을 쓰지 않는다. 알림·지표·관리자 화면·로그인 응답은 확정값만. 청크 API 세션뿐 아니라 내부 쓰기 세션도 포함.

---

## 4. 잠금 순서 (교착 방지 — 모든 쓰기 공통)

```
소유자 사용자 행  →  이름 정할 자리(부모 폴더 행)  →  대상 행  →  하위·버전 행
(FOR NO KEY UPDATE)    (FOR NO KEY UPDATE)             (FOR UPDATE, 기존)
```
- **사용자 행이 맨 먼저**: 업로드 커밋, 폴더 생성, 파일·폴더 이름 변경, 파일·폴더 이동, 휴지통 복원, 휴지통 영구 삭제·직접 영구 삭제(`TrashUseCase`), 버전 삭제, 이동 사용량 이전, 휴지통 비우기 스케줄러(사용량부터 줄임) 모두. 같은 소유자의 이름 결정은 이 행에서 줄을 서 순환 대기가 없다. 소유자가 바뀌는 이동은 두 사용자 행을 번호 순.
  - 설계 문서 규칙 8 은 "사용자 행(루트면)"이었으나, PR #27 Codex 리뷰에서 나온 교착 3건(부모·자식 동시 이름 변경, 루트 폴더 이름 변경 + 자식 루트 이동, 복원·이름 변경 + 영구 삭제) 때문에 **항상 먼저**로 바꿨다. 교착 시 tx 재시도는 두지 않았다 — `@AuditLog` 가 실패마다 FAILURE 감사 이벤트를 즉시 보내 재시도하면 중복된다.
  - 휴지통 보내기(영구 삭제 아님)는 사용자 행을 안 잡지만 대상·하위만 잠그고 사용자 행을 기다리지 않아 순환하지 않는다.
- **이름 정할 자리 잠금**(`DefaultResourceService.lockNamingScope`, `ResourceNamingLockRepository`): 이름 유니크 인덱스가 없어 같은 위치 동시 이름 결정을 막는 유일한 장치. 부모 폴더 행을 잠그므로 그 폴더의 휴지통 이동과도 직렬화된다.
- 이름 변경·복원·파일 이동은 대상 잠금 **전에** 자리를 먼저 잡는다(`lockCurrentScopeBeforeTarget`), 대상 잠금 뒤 한 번 더(그 사이 이동 대비).
- 폴더 이동의 목적지·대상은 기존대로 resource_seq 작은 쪽 먼저(교차 이동 교착 방지) — 사용자 행을 먼저 잡으므로 다른 이름 결정과는 순환하지 않는다.
- S3 호출은 어떤 잠금도 쥐지 않은 상태(tx 밖)에서만.

---

## 5. 정리(백그라운드)

### 5.1 세션 정리 스케줄러 — `SchedulerService.cleanupUploadSessions`
10분, ShedLock `upload_session_cleanup`, S3 호출 없음. 묶음(500)마다 한 tx(상태 변경 + 정리 등록).
| 대상 | 조건 | 처리 |
|---|---|---|
| PENDING | `last_activity < now() - 1h` | FAILED(EXPIRED) + ABORT(멀티파트) / DELETE(단일 요청) |
| COMPLETING | `completing_at < now() - 30m` | FAILED(COMMIT_ERROR) + ABORT + DELETE |
| COMPLETED·FAILED | `create_date < now() - 7d` | 행 삭제 |

### 5.2 삭제 워커 — `StorageDeletionProcessor` (기존 `processStorageDeletions`, 30초)
- DELETE: 1000개씩, key 별로 묶어 `DeleteObjects`. **삭제 직전 `tb_resource`·`tb_resource_version` 이 참조하는 key 는 지우지 않고 `REFERENCED` 실패로 남긴다**(10회 후 수동 확인).
- ABORT_MULTIPART: 100개씩 행마다 abort, NoSuchUpload = 성공.
- 실행 예산 4분을 DELETE(앞 절반) → ABORT(남은 시간) → DELETE(남으면) 순으로 나눠 쓴다.
- 실패는 2^n 분 백오프(상한 60분), 10회면 그대로 남김.

### 5.3 버킷 lifecycle — `S3BucketInitializer`
기동 시 `AbortIncompleteMultipartUpload` 1일 규칙을 기존 규칙에 병합 PUT. **MinIO 는 미지원**(400, 경고만 남기고 기동 계속 — MinIO 는 자체 24h 정리). 끄려면 `drive.upload.manage-bucket-lifecycle=false`.

---

## 6. 설정 (`drive.upload.*`, `UploadSessionProperty`)
| 키 | 기본 |
|---|---|
| `session-ttl` | 1h |
| `completing-timeout` | 30m |
| `finished-retention` | 7d |
| `replaced-key-delete-delay` | 24h |
| `min-part-size` | 16MB |
| `single-request-threshold` | 32MB |
| `absolute-max-size` | 5TB |
| `max-concurrent-parts-per-session` | 4 (청크 API 세션 생성 응답 `maxConcurrentParts` 로 안내만 — 서버가 막지는 않는다) |
| `max-active-sessions-per-user` | 50 |
| `cleanup-batch-size` | 500 |
| `manage-bucket-lifecycle` | true |
| `abort-incomplete-multipart-days` | 1 |
| `block-executable-content` | true — 실행 파일(PE·ELF·Mach-O) 내용 업로드 차단. 설치 파일을 공유해야 하면 false(그래도 다른 확장자로 위장한 실행 파일은 막힌다). `UploadContentInspector` 의 `@Value` |
스케줄 끄기(테스트): `drive.scheduler.upload-session-cleanup.cron: "-"`.

---

## 7. 코드 위치
| 역할 | 위치 |
|---|---|
| 진입점 | `file/write/orchestrator/FileWriteOrchestrator` |
| 쓰기 흐름 | `upload/session/service/UploadSessionWriter`(`open`·`finishClaimed` 는 청크 API 와 공유) |
| 매직바이트 검사 | `upload/session/service/UploadContentInspector` |
| 청크 업로드 API | `api/v1/upload/controller/UploadSessionController`, `application/upload/UploadSessionUseCase`, `upload/session/service/UploadChunkService` |
| 청크 업로드 프론트 | `DriveFront/utils/chunkUpload.js`(업로더), `components/common/dialog/UploadDialog.js`(32MB 이상만 청크), `PendingUploadsDialog.js`(IS_FULL 때 진행 중 업로드 목록·취소) |
| 사용량 `uploadingByte` | `api/v1/user/usecase/UserUseCase.getCurrentQuota` → `UploadSessionTxService.sumUploadingByte` |
| 세션 상태 tx | `upload/session/service/UploadSessionTxService` |
| 커밋 tx | `upload/session/service/UploadCommitService` |
| 정리 등록 | `upload/session/service/UploadSessionStorageCleanup` |
| 세션 정리 스케줄러 | `upload/session/service/UploadSessionCleanupProcessor`·`UploadSessionCleanupService`, `scheduler/service/SchedulerService` |
| 세션 저장소 | `upload/session/repository/DefaultUploadSessionRepository` |
| 업로드 잠금 | `upload/session/repository/UploadLockRepository` |
| 이름 결정 잠금 | `resource/repository/ResourceNamingLockRepository`, `DefaultResourceService.lockOwners/lockNamingScope/lockCurrentScopeBeforeTarget` |
| 리소스 반영 | `DefaultResourceService.saveUploadedFile/replaceWithVersioning/overwriteContent`, `DefaultResourceVersionService.pushCurrentAsVersion` |
| 스토리지 | `storage/service/StorageService`·`S3StorageService`(멀티파트·`uploadPartCopy`·`readRange` 등) |
| 삭제 워커 | `cleanup/storage/service/StorageDeletionProcessor`·`StorageDeletionService` |
| 마이그레이션 | `db/migration/V6__upload_session.sql` |

### 주요 테스트
- `UploadSessionWriterTest` — 단계 순서·실패 분기·유형별 커밋 선택(단위)
- `UploadWriteIntegrationTest` — DUPLICATE·VERSION·한도·복원·OVERWRITE(충돌 포함)·용량·동시 업로드(PG)
- `UploadChunkIntegrationTest` — 청크 API: 끊긴 뒤 빠진 파트만 다시 보내고 complete, complete 재요청 멱등, 동시 complete, 취소(후 파트 PUT 409·재취소 성공), 스케줄러 만료 후 409·410, 빠진 파트 complete, 파트 검증(411·400), NoSuchUpload, 다른 사용자 404, 32MB 미만 거절(PG + 메모리 멀티파트 가짜)
- `CopyAndDocumentWriteIntegrationTest`, `VersionRestoreConcurrencyIntegrationTest`, `WebOfficeSaveAuditIntegrationTest`
- `ResourceNamingLockConcurrencyIntegrationTest` — 동시 같은 이름 생성·이름 변경·이동, 교착 회귀(부모·자식 이름 변경, 루트 폴더 이름 변경 + 자식 루트 이동, 복원 + 영구 삭제, 이름 변경 + 직접 영구 삭제). 각 교착 테스트는 해당 선행 잠금을 끄면 `deadlock detected` 로 실패하는 것을 확인함
- `DefaultUploadSessionRepositoryTest` — 상태 전이·정리 배치(PG), `StorageDeletionProcessorTest`
- `S3StorageServiceMultipartContractTest` — MinIO 계약, `S3BucketInitializerTest`
- `DriveFront/tests/scripts/chunk-upload.test.mjs` — 업로더: 분할·이어 올리기·재시도·409 중단·취소·complete 대기(가짜 서버, `node --test`)

---

## 8. 알려진 한계 (자세한 내용은 todo)
- 앞단 프록시: 설정 안내는 `DriveInstaller/thinkfree/TFD/template/httpd/README.md`(본문 상한 1GiB·응답 대기 300초). 번들 Apache 외 프록시(nginx 기본 1MB 등)는 설치처가 맞춰야 하고, 안 맞추면 파트 PUT 이 413 으로 막힌다. Developers 저장소 k8s ingress 는 아직. 실제 MinIO·브라우저로 1GB 끊었다 이어 올리기는 아직 확인 안 함(업로더 로직은 가짜 서버 테스트만).
- 커밋 tx 권한 재확인은 청크 업로드만(서버 내부 쓰기는 호출부 검사만 — 업로드가 짧고 세션에 익명·공유 링크 맥락이 없다), 매직바이트는 형식 계열만(텍스트·HTML·SVG 등은 내용으로 판별 불가 — 통과, 다운로드는 `attachment`·`nosniff` 로 막는다), 감사 로그 발행 시점은 호출부 그대로.
- 청크 API 는 외부 API(`/api/public`, API Key)로 내보내지 않았다(내부 웹 API 만).
- 호출부 없는 코드 남음: `ResourceRepository.revertOverwrite`, 미사용 `uploadPartsFromStream`·`copyObjectAnySize`. 옛 흐름 호환 코드(`cleanVersionIfExists`·저장 마커·PENDING 만료 판정·`VERSION_PROCESSING`)는 PR #__PR__ 에서 제거했다 — 버전 작업 커밋 tx 안에서 스토리지를 부르지 않는다.
- Ceph·RustFS 계약 테스트 미실행(MinIO 만).
- 지표(Micrometer) 없음.
