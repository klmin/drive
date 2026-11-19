# 업로드 세션 — 현재 구현 (as-built)

> 기준: 2026-11-19. 단계 0~3 의 PR #9·#19·#22 는 `main` 에 머지됨. **§4 잠금 순서는 PR #27(리뷰 중, 브랜치 `fix/name-lock-parent-folder`) 기준** — 머지 전이면 `main` 에는 아직 없다. 지금 코드가 **실제로 어떻게 동작하는지**를 정리한 문서.
> 설계 결정의 이유·검토한 대안은 [upload-session-redesign.md](upload-session-redesign.md), 남은 일은 [../todo/upload-session.md](../todo/upload-session.md).
> 코드가 바뀌면 이 문서를 같은 PR 에서 고친다.

---

## 1. 한눈에

모든 파일 쓰기(웹 업로드·MCP 업로드·파일 복사·템플릿 문서 생성·버전 저장·덮어쓰기·버전 복원·웹오피스 저장)가 **세션 → 저장 → 커밋** 한 경로를 탄다.

```
FileWriteOrchestrator.write          테넌트 파일 크기 정책 확인 후 넘김(진입점, 호출부 무변경)
  └─ UploadSessionWriter.write        트랜잭션 없음 — 아래 단계를 순서대로
       1. 세션 생성        UploadSessionTxService.create         (짧은 tx, REQUIRES_NEW)
       2. 스토리지 저장    StorageService                       (tx 밖)
       3. COMPLETING 선점  UploadSessionTxService.markCompleting (짧은 tx)
       4. (멀티파트면) 조립 → 저장 크기 확인
       5. 커밋             UploadCommitService.commit*           (짧은 tx, 일시적 DB 오류 3회 재시도)
       실패 시             UploadSessionTxService.fail           (FAILED + 정리 등록, 같은 tx)
```

- **불변 key**: 새 내용은 항상 새 key `{tenantSeq}/{세션 uuidv7}`(테넌트 없으면 `0/`). key 는 세션 생성 때 한 번 계산하고, 이후 조회·삭제는 DB 에 저장된 값으로만 한다. 버전·덮어쓰기는 S3 복사 없이 **DB 포인터만** 바꾼다.
- **사용량(`tb_user.use_quota_byte`)은 커밋 tx 에서만** 바뀐다. 실패하면 되돌릴 것이 없다(보상 로직 없음).
- **정리는 상태를 끝내는 쪽이 같은 tx 에서 등록**한다(`tb_storage_deletion`). 실제 S3 abort·delete 는 삭제 워커가 한다.
- 청크 업로드 API(브라우저가 파트를 직접 보내는 경로)는 **아직 없다** — 지금은 서버가 내용을 한 번에 받고 내부에서만 세션을 쓴다.

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
사용자 행 `FOR NO KEY UPDATE` → **용량 사전 필터**(확정 + 진행 중 합계 + 신고 크기 ≤ 한도, 아니면 `IS_FULL`) → 진행 중 세션 수 < 50(아니면 `UPLOAD_SESSION_LIMIT` 429) → INSERT(PENDING).
`IS_FULL` 이면 tx 밖에서 용량 초과 알림(`QuotaService.notifyQuotaExceeded`).

그 전에 `UploadSessionWriter` 가 하는 것:
- Content-Type: 클라이언트 값이 없으면 확장자로(`MediaTypeFactory`), 모르면 `application/octet-stream`.
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

공통 마무리: `markCompleted`(0행이면 예외 → 전체 롤백 — 스케줄러가 이미 FAILED 처리) → `FileUploadedEvent` 를 tx 안에서 발행(리스너가 `AFTER_COMMIT` 이라 커밋 성공 시에만 사용량 후처리).

### 3.4 실패 처리 (`UploadSessionWriter.failOrRecover`)
진행 위치에 따라 기대 상태·정리 방식이 정해진다.

| 위치 | 기대 상태 | 정리 등록(`UploadSessionStorageCleanup`) |
|---|---|---|
| 조립 전(PUT·파트·복사 중) | PENDING | 멀티파트 ABORT, 단일 요청이면 DELETE |
| 조립 중(complete 결과 불명확) | COMPLETING | ABORT + DELETE |
| 조립 후(크기 확인·커밋) | COMPLETING | DELETE |

- 조건부 UPDATE 가 0행이면 커밋이 실제로는 성공했을 수 있다(커밋 뒤 예외·응답 유실) — 세션을 다시 읽어 COMPLETED 면 저장된 결과를 돌려준다(**DB 상태가 정본**).
- 실패 기록조차 못 하면 세션은 PENDING/COMPLETING 으로 남고 스케줄러가 정리한다.
- `fail_reason`: `QUOTA_EXCEEDED`(IS_FULL), `VALIDATION`(그 밖의 업무 오류), `STORAGE_ERROR`(조립 전 스토리지 오류), `COMMIT_ERROR`(그 외).

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
| `max-concurrent-parts-per-session` | 4 (청크 API 용, 아직 미사용) |
| `max-active-sessions-per-user` | 50 |
| `cleanup-batch-size` | 500 |
| `manage-bucket-lifecycle` | true |
| `abort-incomplete-multipart-days` | 1 |
스케줄 끄기(테스트): `drive.scheduler.upload-session-cleanup.cron: "-"`.

---

## 7. 코드 위치
| 역할 | 위치 |
|---|---|
| 진입점 | `file/write/orchestrator/FileWriteOrchestrator` |
| 쓰기 흐름 | `upload/session/service/UploadSessionWriter` |
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
- `CopyAndDocumentWriteIntegrationTest`, `VersionRestoreConcurrencyIntegrationTest`, `WebOfficeSaveAuditIntegrationTest`
- `ResourceNamingLockConcurrencyIntegrationTest` — 동시 같은 이름 생성·이름 변경·이동, 교착 회귀(부모·자식 이름 변경, 루트 폴더 이름 변경 + 자식 루트 이동, 복원 + 영구 삭제, 이름 변경 + 직접 영구 삭제). 각 교착 테스트는 해당 선행 잠금을 끄면 `deadlock detected` 로 실패하는 것을 확인함
- `DefaultUploadSessionRepositoryTest` — 상태 전이·정리 배치(PG), `StorageDeletionProcessorTest`
- `S3StorageServiceMultipartContractTest` — MinIO 계약, `S3BucketInitializerTest`

---

## 8. 알려진 한계 (자세한 내용은 todo)
- 청크 업로드 API·이어 올리기·프론트 미구현 — 큰 파일도 서버가 한 요청으로 받는다.
- 커밋 tx 에서 권한 재확인 안 함(호출부 검사만), 매직바이트 검사 없음, 감사 로그 발행 시점은 호출부 그대로.
- 옛 흐름 호환 코드 남음: `cleanVersionIfExists`(옛 PENDING 버전 정리, 저장 마커 조회 = tx 안 S3 HEAD), `ResourceRepository.revertOverwrite`(호출부 없음), 미사용 `uploadPartsFromStream`·`copyObjectAnySize`.
- Ceph·RustFS 계약 테스트 미실행(MinIO 만).
- 지표(Micrometer) 없음.
