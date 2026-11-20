# 업로드 세션 재설계 (인계 문서)

> 2026-11-18 1차 설계 → **2026-11-19 설계 확정**(질의응답으로 규칙·숫자·흐름 결정). 새 컨텍스트에서 이 문서만 보고 구현을 시작할 수 있게 정리.
> 범위: 파일 업로드·버전 저장·덮어쓰기·버전 복원 + 서버 내부 쓰기(웹오피스 저장·MCP 업로드·복사·템플릿)의 저장 경로, 용량, 동시성, 실패 정리, 대용량(5GB 초과), 이어 올리기.
> 상태: **단계 0~3 구현(2026-11-19) — PR #9·#19·#22·#27 머지. 단계 4 이후 미착수.** 이 문서는 확정 설계·결정 이유의 기록이다 — **지금 코드의 실제 동작은 [upload-session-as-built.md](upload-session-as-built.md), 남은 일은 [../todo/upload-session.md](../todo/upload-session.md)**. 구현 중 바뀐 결정: 규칙 8 의 사용자 행 잠금은 "루트면"이 아니라 이름 결정·영구 삭제 작업에서 **항상 먼저**(as-built §4).
> 전제: **기존 업로드 코드와의 호환·전환은 고려하지 않는다**(사용자 결정 — 기존 `FileWriteOrchestrator`/`*FileWriteService`/저장 마커/`cleanVersionIfExists` 는 교체 대상).
> 참고: 이 파일은 11-18 GitHub `main` force push(amend) 때 빠졌다가 고아 커밋 `81dd0d533` 에서 복원했다.

---

## 0. 한 줄 요약

**불변 스토리지 key(`{tenantSeq}/{세션 uuidv7}`) + 업로드 세션(DB 상태) + 짧은 커밋 tx에서 포인터 교체 + 상태를 끝내는 쪽이 같은 tx에서 정리 작업 등록(`tb_storage_deletion`).**
S3 작업은 항상 DB tx 밖. 용량 확정·이름 넘버링·버전 번호·덮어쓰기 충돌은 커밋 tx 에서만 판정. 대용량은 서버 경유 청크 멀티파트(추후 presigned 옵션).

---

## 1. 규칙 (구현 판단 기준 — 모든 코드는 이걸 따른다)

**저장**
1. key = `{tenantSeq}/{upload_session_id}` (uuidv7, 세션 PK와 같은 값). 테넌트 없음/시스템 데이터는 `0/`. **key 는 계산·파싱하지 않는다** — 조회·삭제는 DB 에 저장된 값으로만. prefix 로 소유자 판단·삭제·용량 계산 금지(사용자 간 이동 시 key 는 그대로라 prefix 에 사용자를 넣지 않는다).
2. **key 하나는 행 하나만 참조한다.** 버전 복원·파일 복사는 항상 새 key 로 서버측 복사(`CopyObject`, 5GB 초과는 `UploadPartCopy`).
3. Content-Type 은 클라이언트 값을 무시하고 서버가 확장자로 정한다. 내용은 매직바이트로 형식 계열만 검증(텍스트류는 판별 불가 — 한계로 수용).

**상태와 정리**
4. 세션 상태는 **`WHERE status = :expected` 조건부 UPDATE 로만** 바꾼다. 1행일 때만 후속 처리.
5. **상태를 끝(FAILED)으로 바꾸는 쪽이 같은 tx 에서 정리 작업(`tb_storage_deletion` 행)을 등록한다.** 클라이언트에게 abort 를 맡기지 않는다. 사전 SELECT 중복 확인 없음(조건부 UPDATE 로 1회만 등록되고, 중복돼도 삭제·abort 는 멱등).
6. 교체·밀려난 이전 key 삭제는 유예(`next_attempt_date = now() + 24h`). 편집 잠금 중인 파일은 잠금 해제 뒤.

**트랜잭션·잠금**
7. **S3 호출은 DB tx 밖에서만.** 행 잠금은 짧은 tx 안에서만.
8. 잠금 순서 고정: **사용자 행(`FOR NO KEY UPDATE`) → 대상 리소스(resource_seq 오름차순) → 부모·목적지 폴더 행**. 교착(`40P01`)은 tx 재시도. ※ 구현에서 바뀐 점(PR #27, as-built §4): 이름 결정 작업(업로드 커밋·폴더 생성·이름 변경·이동·복원)과 영구 삭제가 사용자 행을 **항상** 먼저 잡고, 그다음 **이름 정할 자리(부모 폴더)를 대상보다 먼저** 잡는다(Codex 리뷰의 교착 3건 때문). 교착 재시도는 두지 않았다(실패 감사 로그가 중복되므로 순서로 해결).
9. 이름을 정하는 모든 경로(업로드 커밋·폴더 생성·이름 변경·이동·복원)는 **부모 폴더 행을 `FOR NO KEY UPDATE`** 로 잠근다(부모가 루트면 사용자 행). 이름 유니크 인덱스는 두지 않는다 — 이 잠금이 유일한 중복 방지 장치.

**용량**
10. `tb_user.use_quota_byte` 는 **확정값이며 커밋 tx 에서만 증가**. 진행 중 크기는 세션 테이블 합계(쓰지 않고 읽기만). 실패·만료·취소 시 되돌릴 사용량 없음.
11. **최종 판정은 커밋 tx 에서만.** 세션 시작 검사는 사전 필터.
12. 화면의 사용량 막대는 **확정값만**. 업로드 중 값(`uploadingByte` 별도 필드)은 세션 시작이 IS_FULL 로 거절될 때 "진행 중 업로드 N개가 공간을 쓰고 있음 + 목록·취소" 안내에 쓴다(2026-11-20 변경 — 처음엔 막대 두 구간). 용량 알림·지표·관리자 화면·로그인 응답은 확정값만.

**후처리**
13. 감사 로그·알림 이벤트는 커밋 tx 에서 발행하고 `AFTER_COMMIT` 으로 처리(커밋 성공 시에만). 감사 로그 코드 자체는 수정하지 않고 호출 시점만 맞춘다.

**접근·멱등·경로 통일**
14. 모든 세션 API 는 `request_user_seq = 시큐리티 컨텍스트의 로그인 사용자`(웹·API Key·MCP OAuth 공통 principal) 조건으로 조회. 불일치는 404(존재 숨김). 요청 파라미터의 사용자 값은 쓰지 않는다. 공유 폴더 업로드는 소유자(`user_seq`, 용량 대상)와 요청자를 구분 저장, 권한은 커밋 tx 에서 재확인.
15. 재요청 결과 동일: 파트 PUT 같은 번호 재전송 = 덮어씀, complete 재요청 = `COMPLETED` 면 이전 결과 반환, 취소 재요청 = 이미 끝났어도 성공.
16. **작은 파일 단일 요청·서버 내부 쓰기(웹오피스·MCP·복사·복원·템플릿)도 같은 세션 → 저장 → 커밋 로직**을 탄다. 경로별 별도 구현 금지.

---

## 2. 정해진 숫자 (전부 서버 설정값)

| 항목 | 값 | 비고 |
|---|---|---|
| 세션 TTL | **1시간** | `last_activity` 기준 슬라이딩. 업로드 중엔 수 초마다 갱신되므로 업로드 시간과 무관. 이 안에 돌아오면 이어 올리기 가능 |
| COMPLETING 최대 | 30분 | complete 처리 중 서버 다운 대비 |
| 세션 정리 스케줄러 | 10분 | ShedLock. 상태 변경 + 정리 등록만(S3 호출 없음) |
| 삭제 워커 | 30초 | 기존 `processStorageDeletions`(`CRON_STORAGE_DELETION`) |
| 파트 크기 | `max(16MB, ceil(size / 10_000))` | 16MB 로 160GB 까지 기본 크기. 앞단 프록시 `client_max_body_size` > 파트 크기 |
| 단일 요청 기준 | 32MB 미만 | 0바이트 파일 포함(멀티파트는 파트 최소 1개라 빈 파일 불가) |
| 최대 크기 | 테넌트 최대 업로드 크기 정책, 절대 상한 5TB | 파트 10,000개·객체 5TB(S3) |
| 세션당 동시 파트 / 사용자당 동시 세션 | 4 / 50 | 폴더 업로드는 프론트가 큐로 동시 3~6 세션 |
| 이전 key 삭제 유예 | 24시간 | |
| 버킷 lifecycle `AbortIncompleteMultipartUpload` | 1일 | TTL 보다 길게 |
| 끝난 세션 행 보관 | 7일 | COMPLETED 는 멱등 응답용 |

---

## 3. 현재 구조와 문제 (교체 이유)

현재 흐름: `FileWriteOrchestrator.write` → `prepareMeta`(용량 예약 + 메타, 한 tx) → `doStorage`(S3) → `afterStorage`. `StorageException` 일 때만 `rollback`. 같은 key(`resource_save_name`)를 덮어쓰는 mutable key, 버전은 `copyAndUpload`(원본 key → `원본key/버전seq` copy 후 원본에 PUT).

| # | 문제 | 위치 |
|---|---|---|
| 1 | 버전 PENDING 만료 **3분 하드코딩** → 대용량 업로드 중 다음 저장이 끼면 이전 버전 유실·메타 불일치 | `ResourceVersionConverter`, `DefaultResourceVersionService.cleanVersionIfExists` |
| 2 | 애매한 실패(타임아웃인데 실제 성공) 시 백업 key 즉시 삭제 + 메타 롤백 → 이전 내용 유실, 고아 객체 | `S3StorageService.copyAndUpload/backUpAndRestore` |
| 3 | prepareMeta 커밋 ~ 스토리지 완료 사이 서버 다운 → 행만 있고 객체 없음 / 메타·내용 불일치, **올라간 사용량이 그대로 남음**(복구 수단 없음) | `SchedulerService`(PENDING 정리 없음) |
| 4 | Overwrite 업로드 중 보호 없음 → 동시 덮어쓰기 시 내용·크기 불일치 | `OverwriteFileWriteService` |
| 5 | 단일 `putObject`/`copyObject` → **5GB 초과 불가** | `S3StorageService.upload/copy` |
| 6 | `StorageException` 만 보상 | `FileWriteOrchestrator` |
| 7 | multipart/form-data → Tomcat 임시 파일(디스크 I/O 2배) | — |
| 8 | **이름 중복 방지 구멍**: `lockMaxCopyNumbers` 는 같은 이름의 *기존 행*만 `FOR UPDATE` → 같은 이름이 없으면 아무것도 안 잠김(PG 는 범위 잠금 없음). 업로드는 앞선 사용자 행 잠금 덕에 우연히 직렬화되지만 폴더 생성·이름 변경·이동·복원은 사용자 행을 안 잡음 | `DefaultResourceRepository.lockMaxCopyNumbers`, `DefaultResourceService` |
| 9 | **업로드가 부모 폴더를 잠그지 않음**(`validateParentFolderExists` = 일반 조회) → 폴더 휴지통 이동과 동시에 업로드하면 휴지통 폴더 아래 살아있는 파일 | `DefaultResourceService.validateParentFolderExists` |

참고(별건): 다운로드 Range 미지원·토큰 만료 1m·`nosniff` 없음.

---

## 4. 목표 구조

### 4.1 불변 key
- 새 내용은 항상 새 key(`{tenantSeq}/{세션 uuid}`). DB 컬럼은 기존 `resource_save_name` 을 그대로 storage key 로 사용.
- 버전 key 조합(`ResourceVersion.initResourceSaveName`, `원본key/PK`) 제거 → 버전 행은 이전 key 를 그대로 넘겨받음(S3 copy 없음).
- 사용자 간 이동은 지금처럼 key 를 바꾸지 않는다(복사 비용 없음, DB 가 소유자 정본).
- 실패 시 최악 = 고아 객체 1개(사용자에게 안 보임, 정리 작업이 삭제).

### 4.2 저장 유형별 커밋 동작
| 유형 | 커밋 tx |
|---|---|
| DUPLICATE(신규) | 부모 폴더 잠금(규칙 9) → 이름 넘버링 → resource INSERT(key = 세션 key) |
| VERSION | 리소스 행 `FOR NO KEY UPDATE` → 현재 key·크기·날짜로 version INSERT(DB 값 복사) → resource key·메타를 세션 key 로 UPDATE → 버전 번호·한도, **밀려난 버전 행 삭제 + 그 크기만큼 사용량 차감 + key DELETE 등록**(현재 `DefaultResourceVersionService` 의 버전 삭제 시 차감과 같은 정책) |
| OVERWRITE | `UPDATE … SET resource_save_name=:newKey … WHERE resource_seq=? AND resource_save_name=:baseKey`(낙관적 비교) → 0행이면 충돌 → **1차는 자동 버전 저장**. 이전 key DELETE 등록(유예) |
| RESTORE(버전 복원) | 버전 객체를 새 key 로 서버측 복사(tx 밖) → 커밋은 VERSION 과 동일(현재 내용이 새 버전 행으로, 복원 내용이 새 현재 버전) |

- 동시 요청: 업로드는 병렬, DB 반영은 complete 커밋 도착 순.
- 복원은 복원하는 파일 크기만큼 사용량이 늘어난다(같은 내용이 버전과 현재에 하나씩). 지금 코드도 같은 정책.
- 복원 후 모든 key 는 한 행만 참조. 복원을 되돌리려면 직전 버전을 다시 복원.

### 4.3 덮어쓰기 충돌
- 세션 시작 시 대상의 현재 key 를 `base_storage_key` 로 저장 → 커밋 시 비교(불변 key 라 key 같음 = 내용 안 바뀜).
- 1차: 충돌이면 자동 버전 저장. 추후: `CONFLICT` 상태 + resolve API.

---

## 5. 업로드 세션

### 5.1 상태 (4개 + 실패 사유)
```
PENDING ──complete 선점──▶ COMPLETING ──커밋 tx──▶ COMPLETED
   │                          │
   └──────── 취소·만료 ────────┴── 검증·커밋 실패 ──▶ FAILED (fail_reason)
```
- `fail_reason`: `VALIDATION`, `QUOTA_EXCEEDED`, `CONTENT_MISMATCH`, `EXPIRED`, `CANCELED`, `COMMIT_ERROR`, `NO_SUCH_UPLOAD` …
- 성공 아닌 끝 상태는 정리 방식이 같으므로 상태 하나 + 사유로 구분. `QUOTA_EXCEEDED`/`CONFLICT` 를 별도 상태로 두는 건 "객체 유지 후 재커밋"·resolve 기능을 만들 때.
- **`COMPLETED` 전환은 resource INSERT/UPDATE 와 같은 커밋 tx** → "COMPLETING = 미반영, COMPLETED = 반영" 이 항상 성립(장애 판정의 근거).

### 5.2 DDL (Flyway 다음 버전, PG 18)
```sql
CREATE TABLE tb_upload_session (
    upload_session_id   uuid         PRIMARY KEY DEFAULT uuidv7(),  -- 세션 ID 겸 key 의 uuid
    tenant_seq          int,
    user_seq            int          NOT NULL,   -- 소유자(용량 대상)
    request_user_seq    int          NOT NULL,   -- 요청자 = 시큐리티 컨텍스트 로그인 사용자
    save_type           varchar(20)  NOT NULL,   -- DUPLICATE / VERSION / OVERWRITE / RESTORE
    parent_folder_seq   bigint,
    resource_seq        bigint,                  -- VERSION/OVERWRITE/RESTORE 대상
    source_version_seq  bigint,                  -- RESTORE 원본 버전
    base_storage_key    text,                    -- OVERWRITE 충돌 감지
    file_name           text         NOT NULL,   -- 요청 이름(최종 이름은 커밋 때 확정)
    content_type        text         NOT NULL,   -- 서버가 확장자로 결정
    size_byte           bigint       NOT NULL,   -- 신고 크기(진행 중 합계에 사용)
    storage_key         text,                    -- S3 준비 후 채움
    upload_id           text,                    -- 멀티파트일 때만, S3 준비 후 채움
    part_size           bigint,
    part_count          int,
    status              varchar(20)  NOT NULL DEFAULT 'PENDING',
    fail_reason         varchar(40),
    result_resource_seq bigint,                  -- COMPLETED 후 멱등 응답
    result_name         text,                    -- 확정 이름
    last_error          text,
    last_activity       timestamptz  NOT NULL DEFAULT now(),
    completing_at       timestamptz,
    create_date         timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_upload_session_user_active ON tb_upload_session (user_seq, status);
CREATE INDEX idx_upload_session_cleanup     ON tb_upload_session (status, last_activity);
```
- 시각은 DB `now()` 하나로 통일(WAS 간 시계 차이 배제).
- 컬럼 COMMENT 는 구현 시 팀 규칙대로 추가.

### 5.3 API (서버 경유 청크)
```
POST   /uploads                               세션 생성 (JSON 메타, 바이트 없음)
       req : {fileName, size, parentFolderSeq, saveType, resourceSeq?}
       res : {sessionId, partSize, partCount}            ※ uploadId 비노출

PUT    /uploads/{sessionId}/parts/{partNumber}  파트 업로드
       Content-Type: application/octet-stream  (multipart/form-data 아님 → 임시 파일 없음)
       Content-Length: <파트 크기>             (없거나 chunked 면 411, 기대값과 다르면 400)
       body: 청크 바이트 스트림

GET    /uploads/{sessionId}                   상태 + 이미 올라간 파트 번호(S3 ListParts) — 이어 올리기용
GET    /uploads?status=PENDING                내 진행 중 업로드 목록
POST   /uploads/{sessionId}/complete          완료 → {resourceSeq, 확정 이름, …}
DELETE /uploads/{sessionId}                   취소 → FAILED(CANCELED) + ABORT 등록
```
- partNumber 는 path(업로드 대상 자체, PUT 멱등 의미, 로그·지표 구분). 길이는 표준 `Content-Length` 로 충분(별도 size 필드 없음).
- 컨트롤러는 `@RequestBody byte[]` 금지, `HttpServletRequest.getInputStream()`(또는 `InputStream`)을 S3 로 그대로 흘림.
- 세션이 `PENDING` 이 아니면 파트 PUT 은 409 `{code: UPLOAD_SESSION_FAILED, reason}`.
- 32MB 미만은 기존처럼 단일 요청으로 받되 서버 내부에서 같은 세션·커밋 로직(규칙 16).

### 5.4 파트 계획 (서버가 결정)
- `partSize = max(16MB, ceil(size / 10_000))`, `partCount = ceil(size / partSize)`, n번 기대 크기 = `n < count ? partSize : size - partSize*(count-1)`.
- S3 는 파트 크기를 기대값과 비교하지 않는다(5MB 미만 비마지막만 `EntityTooSmall`) → 서버가 파트 수신 시(Content-Length) + complete 시(ListParts) 직접 검증. 파트 크기 검증 덕분에 총 크기가 신고 크기를 넘을 수 없다 → **파트마다 용량 재검사 불필요**.
- 파트는 병렬·순서 무관, 같은 번호 재업로드는 덮어씀.

---

## 6. 흐름 상세

### 6.1 세션 생성 — INSERT 먼저, S3 준비, UPDATE
```
validator: 권한·대상 존재·휴지통·편집잠금·테넌트 기능·파일크기/확장자 정책·파일명 (읽기)
① tx(짧게): 사용자 행 FOR NO KEY UPDATE
            → 용량: use_quota_byte + 진행 중 합계 + size ≤ total_quota_byte (아니면 IS_FULL)
            → 세션 수: 진행 중 COUNT < 50 (아니면 429 UPLOAD_SESSION_LIMIT)
            → INSERT 세션(PENDING, upload_id NULL) RETURNING upload_session_id
② tx 밖:    S3 createMultipartUpload(key = {tenantSeq}/{id}, contentType)  → uploadId
③ tx(짧게): UPDATE … SET upload_id=:uploadId, storage_key=:key WHERE id=:id AND status='PENDING'
응답: {sessionId, partSize, partCount}

실패: ② 실패 → 세션 FAILED(정리 없음)
      ③ 0행/실패 → 세션 FAILED + ABORT 등록(uploadId 가 이미 생겼으므로)
      ②~③ 사이 다운 → upload_id NULL 인 PENDING → TTL 후 스케줄러가 FAILED, S3 는 버킷 lifecycle
```
- DB 기록이 항상 S3 보다 먼저 생긴다 → DB 에 흔적 없는 미완성 업로드가 생기지 않음.
- 진행 중 합계 쿼리(시간 조건 없음 — 만료되면 스케줄러가 FAILED 로 바꾸므로 PENDING = 진행 중):
  ```sql
  SELECT COALESCE(SUM(size_byte),0), COUNT(*) FROM tb_upload_session
   WHERE user_seq = :u AND status IN ('PENDING','COMPLETING');
  ```

### 6.2 파트 업로드
```
세션 조회(id + request_user_seq) → status = PENDING 아니면 409
→ Content-Length == 기대 크기 아니면 400
→ S3 uploadPart(stream 그대로)
   NoSuchUpload 인데 세션 PENDING → UPDATE FAILED(NO_SUCH_UPLOAD) WHERE status='PENDING' (1행이면 정리 없음 — 이미 없음) → 409
→ UPDATE last_activity = now() WHERE id=:id AND status='PENDING'   (0행 = 그 사이 끝남 → 409)
(선택) partNumber=1 이면 앞 64KB 매직바이트 검사 → 불일치면 FAILED(CONTENT_MISMATCH) + ABORT, 409
```
- 어떤 파트가 올라갔는지는 DB 에 기록하지 않는다(S3 ListParts 가 정본). PUT 핸들러는 S3 abort 를 직접 부르지 않는다.

### 6.3 완료 complete — 메서드에 @Transactional 없음
```
① 선점(짧은 tx, 즉시 커밋):
   UPDATE … SET status='COMPLETING', completing_at=now(), last_activity=now()
    WHERE id=:id AND request_user_seq=:u AND status='PENDING'
   0행 → COMPLETED 면 이전 결과 반환(멱등) / COMPLETING 이면 409 / 그 외 410
② ListParts(paginator) 검증: 개수 == partCount, 1..N 연속, 각 크기 == 기대값, 합계 == size
③ completeMultipartUpload(parts[partNumber, eTag])      ← 이 시점 객체 생성
④ headObject: contentLength == size
⑤ 범위 읽기(bytes=0-65535) → Tika 매직바이트: 실행 파일 차단, 확장자 계열 불일치 차단
⑥ 커밋 tx(별도 빈, @Transactional):
   사용자 행 FOR NO KEY UPDATE → 대상 리소스 → 부모 폴더 FOR NO KEY UPDATE (규칙 8·9)
   → 권한·휴지통·편집잠금 재확인
   → 유형별 반영(§4.2) → 용량 최종 검사 + use_quota_byte 증가 (넘으면 FAILED(QUOTA_EXCEEDED))
   → 교체·밀려난 key DELETE 등록(유예)
   → 감사·알림 이벤트 발행(AFTER_COMMIT)
   → UPDATE status='COMPLETED', result_* WHERE status='COMPLETING' (0행이면 예외 → 전체 롤백)
   일시적 DB 오류는 커밋 tx 만 2~3회 재시도(객체는 이미 있음)
⑦ 실패 처리(REQUIRES_NEW):
   UPDATE status='FAILED', fail_reason=… WHERE status='COMPLETING'
   1행일 때만 정리 등록: ③ 전 실패 = ABORT, ③ 후 실패 = DELETE, ③ 결과 불명확(타임아웃) = ABORT + DELETE
   0행 = 실제로는 커밋 성공(커밋 후 예외·ack 유실) → 아무것도 안 함
```
- S3 를 다시 확인하지 않는다 — **DB 상태가 정본**. 커밋 tx 결과가 애매해도 ⑦의 조건부 UPDATE 0행/1행으로 판정. ⑦조차 못 하면 COMPLETING 으로 남고 30분 뒤 스케줄러가 FAILED + ABORT·DELETE(COMPLETING = 미반영이 보장되므로 안전).
- 클라이언트 complete 재요청: COMPLETED → 이전 결과, FAILED → 오류(처음부터 다시).

### 6.4 서버 내부 쓰기 (웹오피스 저장·MCP 업로드·복사·복원·템플릿)
- 외부 인터페이스는 stream 1회 수신 유지, 내부만 같은 세션·커밋 로직(규칙 16), uploadId 비노출.
- 길이를 앎(`HttpStorageService.put(..., InputStream, long length)`): `< 32MB` 단일 PUT, 이상이면 16MB 씩 읽어 즉시 uploadPart(메모리·임시파일에 통째로 담지 않음).
- 복사·복원: 새 key 로 `CopyObject`, 5GB 초과는 `UploadPartCopy`. **대용량(예: 수 GB 이상) 복원·복사는 비동기** — 세션 ID 를 바로 돌려주고 `GET /uploads/{id}` 로 상태 확인(HTTP 타임아웃 회피). 기준 크기는 구현 시 결정.

### 6.5 S3StorageService 추가 메서드 (SDK v2)
| 메서드 | 용도 |
|---|---|
| `createMultipartUpload(key, contentType, metadata)` → uploadId | 시작 |
| `uploadPart(key, uploadId, n, in, len)` → eTag | `RequestBody.fromInputStream(in, len)` |
| `listParts(key, uploadId)` | `listPartsPaginator`, `NoSuchUploadException` → NO_SUCH_UPLOAD |
| `completeMultipartUpload(key, uploadId, parts)` | 조립 |
| `abortMultipartUpload(key, uploadId)` | NoSuchUpload 는 성공 간주(멱등) |
| `headContentLength(key)` / `readRange(key, range)` | 크기 확인 / 매직바이트용 소범위 |
| `copyObject` / `uploadPartCopy` | 복원·복사 |
| `uploadMultipartFromStream` / `putSingle` | 서버 내부 쓰기·작은 파일 |
- 멀티파트 ETag 는 전체 MD5 아님(`…-N`). 파트 체크섬(CRC32/SHA256)은 선택(Ceph/RustFS 지원 확인 후).

---

## 7. 용량 정책

| 용도 | 쓰는 값 |
|---|---|
| 세션 시작 검사 | 확정 + 진행 중 합계 + 이번 세션의 늘어날 양 ≤ 한도 (사용자 행 잠금 안). 늘어날 양 = `reserve_byte`: OVERWRITE 는 새 크기 − 시작 시 대상 크기(0 이상), 그 외 신고 크기 |
| 파트 업로드 | **용량 안 봄**(세션 PENDING + 파트 크기만) |
| complete 커밋 | 확정 + 실제 증가분 ≤ 한도 — **최종 판정** |
| 사용량 조회 API | `useQuotaByte`(확정) + **`uploadingByte`(진행 중 합계, 새 필드)** |
| 용량 알림·지표·관리자 화면·로그인 응답 | 확정만 |

- 증가분: 신규·버전·복원 = 실제 크기, 덮어쓰기 = 새 크기 − 이전 크기, 밀려난 버전 = −크기(같은 tx).
- 떠난 사용자의 업로드는 최대 TTL + 스케줄러 주기(약 1시간 10분) 동안 진행 중 합계에 잡히다 풀린다.
- **관리자 용량 축소**: 지금처럼 허용(`QuotaService.ensureCanUpdateQuota` 는 축소를 검사 없이 허용). 진행 중 업로드는 커밋 때 새 한도로 판정.
  - 후속(선택): 축소 tx 에서 `확정 + 진행 중 > 새 한도` 면 넘치는 PENDING 세션(최근 시작 순)을 `FAILED(QUOTA_EXCEEDED)` + ABORT 등록 → 클라이언트는 다음 파트에서 409 로 즉시 중단(낭비 방지). COMPLETING 은 건드리지 않음.
- 테넌트 총 용량은 현재 **사용자 할당 합계** 기준(`TenantQuotaPolicy`)이라 업로드 커밋에서 테넌트 행을 잡지 않는다.
- 용량 알림: 지금도 업로드가 끝난 뒤 `FileUploadedEvent` → `FileUploadedListener`(`@TransactionalEventListener` + `@Async`)로 처리. 새 구조에선 커밋 tx 안에서 발행해 커밋 성공 시에만.

---

## 8. 잠금 정책

### 8.1 PG 행 잠금 요약
모두 일반 SELECT 는 막지 않고, 잠기는 건 조회된 그 행뿐.

| 보유 ↓ / 요청 → | KEY SHARE | SHARE | NO KEY UPDATE | UPDATE |
|---|---|---|---|---|
| FOR KEY SHARE | ✅ | ✅ | ✅ | ⏳ |
| FOR SHARE | ✅ | ✅ | ⏳ | ⏳ |
| FOR NO KEY UPDATE | ✅ | ⏳ | ⏳ | ⏳ |
| FOR UPDATE | ⏳ | ⏳ | ⏳ | ⏳ |

- `FOR NO KEY UPDATE`: 읽고 그 값으로 이 행을 고칠 때(일반 UPDATE 가 거는 잠금과 같음). `FOR UPDATE` 와 달리 FK 참조 INSERT(`KEY SHARE`)를 막지 않는다 — `tb_user` 는 `tb_user_log`·`tb_system_config` 가 FK 로 참조하므로 이쪽을 쓴다.
- `FOR SHARE`: 이 행을 고치지 않고 판단 근거로만 쓸 때(근거 확인끼리는 병렬, 고치려는 쪽만 대기). 같은 tx 에서 나중에 그 행을 고칠 거면 쓰지 않는다(잠금 승격 교착).

### 8.2 작업별
| 작업 | 사용자 행 | 대상 | 부모·목적지 폴더 |
|---|---|---|---|
| 세션 생성 | NO KEY UPDATE | — | — |
| 업로드 커밋(신규) | NO KEY UPDATE | — | NO KEY UPDATE (이름 넘버링 직렬화 + 휴지통 경합 차단) |
| 버전·복원 커밋 | NO KEY UPDATE | NO KEY UPDATE | — |
| 덮어쓰기 커밋 | NO KEY UPDATE | 낙관적 비교 UPDATE | — |
| 폴더 생성 | (루트면) NO KEY UPDATE | — | NO KEY UPDATE |
| 이름 변경 | (루트면) | 대상 잠금(현재와 같음) | NO KEY UPDATE |
| 이동 | (루트면) | 대상 + 하위 잠금(현재와 같음, 경로 치환 때문에 필요) | 목적지 NO KEY UPDATE |
| 휴지통·삭제 | — | 대상 + 하위 잠금(현재와 같음) | — |

- 위 "현재와 같음" 외의 이름 결정 경로 변경(규칙 9)은 업로드 재설계와 별개로 지금 코드의 구멍(§3 #8·#9)도 막는다.
- 하위가 큰 폴더의 하위 전체 잠금 비용은 경로 구조상 필요한 비용으로 수용.

---

## 9. 정리 (스케줄러·삭제 워커)

### 9.1 `tb_storage_deletion` 확장 (기존 테이블 재사용)
```sql
ALTER TABLE tb_storage_deletion
  ADD COLUMN action    varchar(20) NOT NULL DEFAULT 'DELETE',  -- DELETE / ABORT_MULTIPART
  ADD COLUMN upload_id varchar(1024);                          -- ABORT_MULTIPART 전용
DROP INDEX idx_storage_deletion_next_attempt;
CREATE INDEX idx_storage_deletion_due ON tb_storage_deletion (action, next_attempt_date, storage_deletion_seq);
```
- 기존 등록 코드(리소스 삭제·휴지통 비우기 등)는 DEFAULT 로 그대로 동작.

| 상황 | 등록 |
|---|---|
| complete 전 실패·취소·만료·관리자 축소 | ABORT_MULTIPART |
| complete 후 실패 | DELETE |
| complete 결과 불명확 | ABORT_MULTIPART + DELETE |
| 버전·덮어쓰기로 밀려난 이전 key | DELETE, `next_attempt_date = now() + 24h` |

### 9.2 삭제 워커 (기존 `StorageDeletionProcessor`, 30초, 스케줄러 하나)
- **같은 ShedLock 작업 안에서 action 별로 경로 분리**:
  - `processDueDeletes()` — `WHERE action='DELETE'`, 기존 `DeleteObjects` 일괄 삭제·key 별 묶음 로직 그대로.
  - `processDueAborts()` — `WHERE action='ABORT_MULTIPART'`, 행마다 abort(일괄 API 없음), NoSuchUpload = 성공.
- 섞으면 안 되는 이유: 기존 `processBatch` 가 `storage_key` 별로 묶으므로 같은 key 의 DELETE·ABORT 행이 합쳐져 abort 가 빠진다.
- 실행 시간 예산을 둘이 나눠 씀(한쪽 적체가 다른 쪽을 굶기지 않게). 실패 행은 기존 백오프로 뒤로 밀려 다른 행을 막지 않는다 → 별도 스케줄러 불필요.
- 삭제 직전 방어: 그 key 를 `tb_resource`·`tb_resource_version` 이 참조하면 건너뛰고 경고 로그.
- 최대 재시도 도달 행 수에 알림.

### 9.3 세션 정리 스케줄러 (신규, 10분, ShedLock)
| 대상 | 조건 | 처리(한 tx) |
|---|---|---|
| PENDING | `last_activity < now() - 1h` | FAILED(EXPIRED) + ABORT 등록(upload_id 있을 때) |
| COMPLETING | `completing_at < now() - 30m` | FAILED(COMMIT_ERROR) + ABORT + DELETE 등록 |
| COMPLETED / FAILED | `create_date < now() - 7d` | 행 삭제 |
- S3 호출 없음. 정리 등록이 실패하면 상태 변경도 같이 롤백 → 세션은 그대로 PENDING, 다음 실행에서 재시도. 반쪽 상태 없음.
- 최종 안전망: 버킷 lifecycle `AbortIncompleteMultipartUpload` 1일.

---

## 10. 프론트

- **직접 만든 청크 업로더**(`DriveFront/utils/chunkUpload.js`)를 기존 업로드 다이얼로그에 연결(2026-11-20 변경 — 처음엔 Uppy `@uppy/aws-s3`). Uppy 를 쓰지 않은 이유: ① 파트 PUT 응답의 ETag 헤더를 요구해 백엔드 헤더·CORS 노출 추가가 필요, ② 자체 XHR 이라 api 유틸의 401 → 토큰 재발급 → 재시도를 거치지 않아 긴 업로드 중 토큰 만료에 실패, ③ 기존 다이얼로그(폴더 업로드·중복 이름·취소·재시도)와 Uppy 상태가 이중. 필요한 것은 전송 로직(분할·동시·재시도·재개)뿐이라 약 300줄로 충분. presigned 로 바꾸면 파트 PUT 대상만 바꾼다.
- 파트 PUT 은 `application/octet-stream` 원본 바이트, 세션당 동시 4.
- **409(`UPLOAD_SESSION_FAILED`)** 받으면 남은 파트 전송 중지, 재시도 안 함, 사유별 메시지. abort 요청은 보내지 않는다(서버가 이미 등록).
- 취소 버튼 → `DELETE /uploads/{id}`. 새로고침·탭 닫기에는 abort 를 보내지 않는다(1시간 안에 이어 올리기 가능, 아니면 TTL 정리).
- 재개: `localStorage` 에 `{sessionId, fileName, size, lastModified, partSize}` → 복귀 시 같은 파일 재선택 → 지문 비교 → `GET /uploads/{id}` 로 빠진 파트만.
- 폴더 업로드: 파일 큐 + 동시 3~6 세션(사용자당 상한 50).
- 사용량 막대: 확정값만. 세션 시작이 IS_FULL 이면 사용량 API 를 다시 읽어 `uploadingByte > 0` 일 때 "진행 중 업로드가 공간을 쓰고 있음" + 업로드 목록·취소 버튼(막대 두 구간은 낯설고 업로드가 끝나면 어차피 확정값으로 넘어가므로 안내로 대신).

---

## 11. 보안·검증
- 세션 접근은 `request_user_seq` = 시큐리티 principal(규칙 14).
- 확장자는 `fileName` 에서 서버가 추출(확장자 정책 적용), Content-Type 은 서버 결정.
- 매직바이트로 형식 계열 검증, 정밀 검사(바이러스)는 커밋 후 비동기.
- 다운로드: DB Content-Type + `Content-Disposition: attachment` + `X-Content-Type-Options: nosniff`.

---

## 12. 단계별 계획

| 단계 | 내용 |
|---|---|
| 0 | `nosniff` 헤더 |
| 1 | **스토리지 계약 테스트**(Testcontainers: MinIO / Ceph demo / RustFS — create·part·list·complete·abort·range·copy·uploadPartCopy) |
| 2 | Flyway: `tb_upload_session` 생성 + `tb_storage_deletion` 확장, S3StorageService 멀티파트 메서드, 삭제 워커 action 분리, 세션 정리 스케줄러, 버킷 lifecycle |
| 3 | 세션·불변 key·커밋 tx 를 **서버 내부에 먼저**: 기존 단일 업로드 API·웹오피스·MCP·복사·복원이 내부에서 세션 → 저장 → 커밋. 기존 `FileWriteOrchestrator`/`*FileWriteService`/저장 마커/`cleanVersionIfExists`/`initResourceSaveName` 제거. 이름 결정 경로 잠금 통일(규칙 9) |
| 4 | 청크 API(§5.3) + 프론트 청크 업로더·이어 올리기, `uploadingByte`(IS_FULL 안내용) |
| 5 | (선택) 관리자 축소 시 진행 중 세션 즉시 중단, presigned 옵션(`upload.mode = PROXY \| PRESIGNED`, 버킷 CORS `ExposeHeaders: ETag`), resolve API, 버킷↔DB 대조 잡, 파트 체크섬 |
| 별건 | 다운로드 Range(206, `If-Range`, ETag) + 토큰 만료 연장 |

### 12.1 구현 현황 (2026-11-19)
- **단계 0**: 별도 코드 없음 — 모든 `/api/**` 체인이 Spring Security 기본 헤더(`X-Content-Type-Options: nosniff`)를 쓰고(`.headers()` 커스터마이즈 없음), 다운로드 경로는 `web.ignoring()` 밖.
- **단계 1**: `S3StorageServiceMultipartContractTest`(MinIO) — create·part(역순·재업로드 덮어쓰기)·list·complete·abort 멱등·NoSuchUpload 매핑·range·0바이트 PUT·copy·UploadPartCopy.
- **단계 2**:
  - `V6__upload_session.sql` — `tb_upload_session`, `tb_storage_deletion.action/upload_id` + CHECK, `idx_storage_deletion_due`, `idx_resource_version_resource_save_name`(삭제 직전 참조 방어용). jOOQ 재생성(enum forcedType 4개).
  - `StorageService` 멀티파트 메서드(§6.5) — `S3StorageService` 구현. 파트 스트림은 길이 제한·close 차단 래퍼로 흘린다.
  - 삭제 워커: `StorageDeletionProcessor` 가 DELETE(앞 절반 예산)·ABORT(남은 예산)·DELETE(남으면) 순서. DELETE 직전 `tb_resource`·`tb_resource_version` 참조 key 는 `REFERENCED` 실패로 남김(시도 상한 → 수동 확인). `StorageDeletionService.registerDeferred/registerAbort` 추가.
  - 세션 도메인(`com.thinkfree.drive.upload.session`): enum·`UploadSessionProperty`(`drive.upload.*`, §2 값이 기본값)·`UploadPartPlan`·`UploadSessionRepository`(조건부 전이·진행 중 합계·정리용 SKIP LOCKED 배치)·`UploadSessionStorageCleanup`(조립 전/후/불명확 정리 등록).
  - 세션 정리 스케줄러: `SchedulerService.cleanupUploadSessions`(10분, ShedLock `upload_session_cleanup`) → `UploadSessionCleanupProcessor`.
  - 버킷 lifecycle: `S3BucketInitializer` 가 기존 규칙에 병합(위 §14 MinIO 메모).
  - `fail_reason` 에 `STORAGE_ERROR`(세션 생성 중 스토리지 준비 실패, §6.1 ②) 추가.
- **단계 3-1 (신규 DUPLICATE)** — 브랜치 `feat/upload-session-commit`:
  - `FileWriteOrchestrator.write` 가 DUPLICATE 를 `UploadSessionWriter.writeDuplicate` 로 보낸다 → 웹 업로드·MCP 업로드·파일 복사·템플릿 문서 생성이 모두 세션 경로. `DuplicateFileWriteService` 제거.
  - `UploadSessionWriter`(tx 없음): 세션 생성 → key `{tenantSeq}/{세션 ID}` → 32MB 미만 단일 PUT / 이상 멀티파트(16MB) / 복사는 `copyObjectAnySize` → COMPLETING → (조립) → 크기 확인 → 커밋(일시적 DB 오류 3회 재시도). 실패는 진행 위치에 맞춰 FAILED + 정리 등록, uploadId 기록 실패는 `failWithAbort`, 커밋 뒤 예외는 세션이 COMPLETED 면 저장 결과 반환.
  - `UploadSessionTxService`(각 단계 REQUIRES_NEW), `UploadCommitService.commitDuplicate`(사용자 행 → 부모 폴더 행 `FOR NO KEY UPDATE`, 용량 최종 판정·증가, `saveUploadedFile`, COMPLETED, `FileUploadedEvent` 를 tx 안에서 발행), `UploadLockRepository`.
  - 용량 사전 필터는 확정 + 진행 중 + 신고 크기(덮어쓰기는 증가분 — 단계 4-3). IS_FULL 이면 트랜잭션 밖에서 알림(`QuotaService.notifyQuotaExceeded`).
  - 아직 아님: 커밋 tx 의 권한 재확인(호출부 검사만), 확장자 기반 Content-Type 은 클라이언트 값이 없을 때만(Codex 리뷰 반영)·매직바이트 검사(규칙 3), 감사 로그 발행 시점(호출부 그대로).
- **단계 3-2 (VERSION·OVERWRITE·RESTORE)** — 브랜치 `feat/upload-session-version`:
  - 모든 저장 유형이 `UploadSessionWriter.write` 하나로. `FileWriteOrchestrator` 는 크기 정책 확인 후 넘기기만 한다. `Version/OverwriteFileWriteService`·`FileWriteService`·`ResourceVersionRetryService`·`S3StorageService.copyAndUpload/backUpAndRestore`·`ResourceService.updateWithVersioning/updateWithOverwrite/rollbackVersion/saveWithCopyCheck` 제거.
  - VERSION·RESTORE 커밋(`commitVersion`): 사용자 행 → 리소스 행 → 버전 행 잠금, 현재 key·크기·등록일을 SUCCESS 버전 행으로 그대로 넘김(S3 복사 없음, `pushCurrentAsVersion`), 한도 초과 시 가장 오래된 버전 행 삭제 + 사용량 차감 + key 24h 유예 삭제 등록, 리소스는 세션 key.
  - OVERWRITE 커밋(`commitOverwrite`): 세션 시작 때 저장한 base key 와 잠근 뒤 현재 key 를 비교해 같을 때만 교체(JPQL `overwrite` 에 `resource_save_name = :baseKey` 조건), 이전 key 24h 유예 삭제. 다르면(그 사이 다른 저장) 버전 저장으로.
  - 복원은 출처가 스토리지 객체인 VERSION = 세션 RESTORE. 잠긴 파일은 세션 시작 전에 미리 거절(최종 판정은 커밋 tx).
  - 남겨 둔 것(전환 기간): `cleanVersionIfExists`(옛 흐름이 남긴 PENDING·DELETE 버전 정리 — 저장 마커 `findSaveMarker`, `finalizeAfterStorageUpload`, `rollbackResourceVersionMetadata` 포함). 옛 PENDING 행이 없다고 확인된 뒤 제거. `ResourceRepository.revertOverwrite` 는 호출부 없음(다음 정리 때 제거).
- **이름 결정 경로 잠금 통일** — 브랜치 `fix/name-lock-parent-folder`: `DefaultResourceService.lockNamingScope` 가 폴더 생성·파일/폴더 이름 변경·이동(소유자 변경 시 받는 사용자 기준)·휴지통 복원에서 대상 행 잠금 뒤 부모 폴더 행(루트면 사용자 행)을 `FOR NO KEY UPDATE`(`ResourceNamingLockRepository`). 업로드 커밋은 3-1 에서 이미 같은 잠금. 동시 같은 이름 생성·이름 변경·이동 테스트(잠금을 끄면 4건 모두 이름 중복으로 실패하는 것 확인).
- **단계 4-1 (청크 API 백엔드)** — 브랜치 `feat/upload-chunk-api`:
  - `/api/v1/uploads` — 세션 생성·파트 PUT(요청 스트림 그대로)·상태(ListParts 로 올라간 파트)·목록·complete·취소(`UploadSessionController` → `UploadSessionUseCase` → `UploadChunkService`).
  - `UploadSessionWriter` 를 `open`(세션 생성)·`finishClaimed`(선점 뒤 조립→크기 확인→커밋→실패 처리)로 나눠 내부 쓰기와 complete 가 같은 코드를 쓴다.
  - 정한 것: 32MB 미만은 받지 않는다(기존 단일 요청 API — 단일 객체는 complete 뒤 늦은 PUT 이 커밋된 객체를 덮어쓸 수 있다). 청크 API 세션 = `part_count` 가 있는 세션(내부 쓰기 세션은 이 API 로 조회·complete·취소 불가). complete 의 파트 검증 실패는 설계대로 세션 FAILED + ABORT. 권한은 세션 생성과 complete 선점 전에 확인(커밋 tx 안 재확인은 todo B). 익명 사용자 불가.
- **단계 4-2 (`uploadingByte`)** — 브랜치 `feat/usage-uploading-byte`: `GET /api/v1/users/quota` 에 진행 중 세션 합계 `uploadingByte` 별도 필드(용량 주인 기준, 확정값 `useQuotaByte` 와 합치지 않음). 알림·지표·관리자 화면은 그대로 확정값.
- **단계 4-3 (덮어쓰기 사전 필터)** — 브랜치 `fix/overwrite-quota-precheck`, V7 `reserve_byte`: 덮어쓰기 세션은 시작 검사·진행 중 합계에 증가분(새 − 시작 시 대상 크기, 0 이상)만. 전체 크기로 보던 탓에 용량이 거의 찬 사용자의 수정 저장이 커밋에선 통과할 텐데 시작에서 거절됐다. 그 사이 다른 저장이 끼어 버전 저장으로 바뀌면 커밋 tx 가 전체 크기로 다시 판정.
- **단계 4-4 (프론트)** — 브랜치 `feat/front-chunk-upload`: `utils/chunkUpload.js`(분할·세션당 동시 4·5xx·네트워크 재시도·409 중단·`localStorage` 지문 이어 올리기·취소 DELETE·complete 409 이면 상태 조회) + `UploadDialog` 에서 32MB 이상만 청크로, 진행률 표시, IS_FULL 때 `uploadingByte > 0` 이면 진행 중 업로드 목록(`PendingUploadsDialog`, 취소 가능).
- **미착수**: 단계 4 나머지(앞단 프록시 문서, 실제 MinIO 1GB 브라우저 확인), 지표(§13), 커밋 tx 권한 재확인·매직바이트 검사, 전환 기간 코드(`cleanVersionIfExists`·`revertOverwrite`) 제거.

---

## 13. 테스트·운영
- 동시성: 동시 complete(중복 클릭), 동시 버전 저장, 동시 덮어쓰기, 스케줄러 vs complete, 같은 폴더 동시 폴더 생성(같은 이름), 업로드 vs 부모 폴더 휴지통, 관리자 축소 vs 진행 중 업로드.
- 장애 주입: complete ②~⑥ 각 단계 강제 예외, complete 타임아웃(실제 성공), 커밋 후 예외(⑦ 0행 경로), 세션 생성 ②~③ 사이 다운, 프로세스 다운 후 스케줄러 복구.
- 경계: 0바이트, 정확히 32MB, 파트 크기 경계, 마지막 파트, 5GB 초과 복사.
- 지표(Micrometer): 상태별 세션 수, 가장 오래된 PENDING/COMPLETING 나이, 삭제 대기 적체·최고령·최종 실패 수. 알림: COMPLETING 30분 초과, 최종 실패 행 발생.
- WAS 여러 대: 서버 전체 파트 동시 수 세마포어는 인스턴스 단위 — 전체 제한은 WAS 수 × 상한 또는 게이트웨이.

---

## 14. 미결 / 구현 시 결정
- [ ] 대용량 복원·복사를 비동기로 돌리는 기준 크기
- [ ] Ceph·RustFS 의 멀티파트·abort 직후 동작·체크섬·조건부 쓰기(`If-None-Match`) 지원(단계 1 결과로 확정) — MinIO 는 `S3StorageServiceMultipartContractTest` 통과. 같은 테스트를 Ceph demo·RustFS 이미지로 돌리는 건 남음
- [x] 버킷 lifecycle `AbortIncompleteMultipartUpload`: **MinIO 는 미지원**(단독 규칙은 400 MalformedXML, 다른 동작과 섞으면 조용히 버림 — MinIO 는 `stale_uploads_expiry`(기본 24h)로 자체 정리). `S3BucketInitializer` 는 기존 규칙을 읽어 병합 후 PUT, 실패하면 경고만(`drive.upload.manage-bucket-lifecycle=false` 로 끔). RustFS 도 MinIO 계열이라 같을 가능성 — 단계 1 에서 확인
- [ ] `tb_upload_session` 컬럼 COMMENT·오류 코드 이름(`UPLOAD_SESSION_FAILED`, `UPLOAD_SESSION_LIMIT`) 최종 확정
- [ ] 앞단 프록시 `client_max_body_size`·타임아웃 값
- [ ] 이력서 반영 문구는 구현 후(`C:\intelljProject\drive\RESUME_DRIVE_REVIEW.md` §5 참고)

---

## 15. 검토했지만 택하지 않은 것 (이유)
| 대안 | 이유 |
|---|---|
| 이름 유니크 인덱스 | 사용자 결정으로 안 둠 → 부모 폴더 잠금(규칙 9)으로 대체 |
| key 에 userSeq(`{userSeq}/…`, `{tenant}/{user}/…`) | 사용자 간 이동 시 key 를 안 바꾸므로 prefix 가 실제 소유자와 어긋남 |
| 버전 key 에 버전 번호 붙이기(`uuid/v3`) | key 계산 규칙이 다시 생기고 S3 복사 필요 |
| 복원 시 버전 key 를 포인터로 공유 | 한 key 를 두 행이 참조 → 버전 삭제 시 현재 내용 유실 |
| 진행 중 용량을 `tb_user` reserved 컬럼에 예약 | 떠난 업로드가 묶임·보상 로직 필요 → 세션 합계로 계산 |
| 마지막(complete)에서만 용량 검사 | 시작 검사 비용은 쿼리 하나, 없으면 수 GB 업로드 후 실패 |
| 파트마다 용량 검사 | 파트 크기 검증으로 총량 초과 불가, 관리자 축소는 세션 상태로 처리 → 불필요한 부하 |
| 사용량 화면에 확정 + 진행 중 합산 | 실패·취소 시 숫자가 튐, 알림 오발송 → 막대는 확정값만, 진행 중은 IS_FULL 안내에서 |
| 진행 중은 안 보고 커밋에서만 용량 판정 | 꽉 찬 사용자도 complete 없이 만료까지 S3 를 한도 없이 채울 수 있고, 수 GB 를 올린 뒤에야 실패 → 시작 검사 유지 |
| 이어 올리기 TTL 과 진행 중 판정을 두 값(30분/6시간)으로 | 1차엔 개념 하나(1시간)로 충분, 필요해지면 쿼리 조건만 추가 |
| 파트 PUT 에서 S3 abort 직접 호출 / 클라이언트가 abort | 병렬 파트가 중복 호출, 브라우저 종료 시 누락 → 상태 바꾸는 쪽이 정리 등록 |
| 삭제 워커에 action 섞어 한 쿼리 처리 / 스케줄러 분리 | 기존 key 묶음 로직과 충돌 / 백오프·경로 분리로 격리 충분 |
| tus 프로토콜(tus-java-server) | 청크를 로컬 디스크에 모으는 구조라 S3 멀티파트와 안 맞음 |
| presigned 직접 업로드를 1차로 | 고객사 내부망 스토리지는 브라우저 접근 불가 → 단계 5 옵션 |
