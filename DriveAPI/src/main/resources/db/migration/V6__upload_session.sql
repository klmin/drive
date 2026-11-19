-- -------------------------------------------------------------------------------------
-- 업로드 세션 재설계 2단계(docs/design/upload-session-redesign.md §5.2·§9.1)
--   1) tb_upload_session — 업로드 한 건의 상태(PENDING → COMPLETING → COMPLETED / FAILED)와 불변 스토리지 key({tenantSeq}/{세션 uuid}).
--      key 는 세션 PK 와 같은 uuidv7(PG 18 내장)로 만들고, 조회·삭제는 저장된 값으로만 한다(계산·파싱 금지).
--      진행 중 용량 = 이 테이블의 PENDING·COMPLETING size_byte 합계(tb_user 에 예약하지 않는다).
--      시각은 DB now() 하나로 통일(WAS 간 시계 차이 배제).
--   2) tb_storage_deletion 확장 — action(DELETE / ABORT_MULTIPART) + upload_id.
--      기존 등록 코드(리소스 삭제·휴지통 비우기 등)는 DEFAULT 'DELETE' 로 그대로 동작한다.
--      삭제 워커는 action 별로 따로 꺼낸다(같은 key 의 DELETE·ABORT 행이 key 묶음 처리에서 합쳐지지 않게).
--   3) tb_resource_version.resource_save_name 인덱스 — 삭제 워커의 "참조 중인 key 는 지우지 않는다" 방어 조회용
--      (tb_resource 는 V1 의 idx_resource_resource_save_name 이 이미 있다).
-- -------------------------------------------------------------------------------------

CREATE TABLE tb_upload_session (
  upload_session_id   uuid         NOT NULL DEFAULT uuidv7(),
  tenant_seq          integer      DEFAULT NULL,
  user_seq            integer      NOT NULL,
  request_user_seq    integer      NOT NULL,
  save_type           varchar(20)  NOT NULL,
  parent_folder_seq   bigint       DEFAULT NULL,
  resource_seq        bigint       DEFAULT NULL,
  source_version_seq  bigint       DEFAULT NULL,
  base_storage_key    varchar(255) DEFAULT NULL,
  file_name           varchar(255) NOT NULL,
  content_type        varchar(255) NOT NULL,
  size_byte           bigint       NOT NULL,
  storage_key         varchar(255) DEFAULT NULL,
  upload_id           varchar(1024) DEFAULT NULL,
  part_size           bigint       DEFAULT NULL,
  part_count          integer      DEFAULT NULL,
  status              varchar(20)  NOT NULL DEFAULT 'PENDING',
  fail_reason         varchar(40)  DEFAULT NULL,
  result_resource_seq bigint       DEFAULT NULL,
  result_name         varchar(255) DEFAULT NULL,
  last_error          varchar(1000) DEFAULT NULL,
  last_activity       timestamptz  NOT NULL DEFAULT now(),
  completing_at       timestamptz  DEFAULT NULL,
  create_date         timestamptz  NOT NULL DEFAULT now(),
  PRIMARY KEY (upload_session_id),
  CONSTRAINT chk_upload_session_status    CHECK (status IN ('PENDING', 'COMPLETING', 'COMPLETED', 'FAILED')),
  CONSTRAINT chk_upload_session_save_type CHECK (save_type IN ('DUPLICATE', 'VERSION', 'OVERWRITE', 'RESTORE')),
  CONSTRAINT chk_upload_session_size      CHECK (size_byte >= 0)
);
CREATE INDEX idx_upload_session_user_active ON tb_upload_session (user_seq, status);
CREATE INDEX idx_upload_session_cleanup     ON tb_upload_session (status, last_activity);
COMMENT ON TABLE  tb_upload_session                     IS '업로드 세션(파일 저장 한 건의 진행 상태)';
COMMENT ON COLUMN tb_upload_session.upload_session_id   IS '업로드 세션 ID(uuidv7) — 스토리지 key 의 uuid 와 같은 값';
COMMENT ON COLUMN tb_upload_session.tenant_seq          IS '테넌트 seq(없으면 NULL — key 접두 0)';
COMMENT ON COLUMN tb_upload_session.user_seq            IS '소유자 seq(용량 대상)';
COMMENT ON COLUMN tb_upload_session.request_user_seq    IS '요청자 seq(시큐리티 컨텍스트 로그인 사용자) — 세션 API 접근 조건';
COMMENT ON COLUMN tb_upload_session.save_type           IS '저장 유형(DUPLICATE / VERSION / OVERWRITE / RESTORE)';
COMMENT ON COLUMN tb_upload_session.parent_folder_seq   IS '부모 폴더 seq(DUPLICATE, 루트면 NULL)';
COMMENT ON COLUMN tb_upload_session.resource_seq        IS '대상 리소스 seq(VERSION / OVERWRITE / RESTORE)';
COMMENT ON COLUMN tb_upload_session.source_version_seq  IS '복원 원본 버전 seq(RESTORE)';
COMMENT ON COLUMN tb_upload_session.base_storage_key    IS '세션 시작 시 대상의 스토리지 key(OVERWRITE 충돌 감지)';
COMMENT ON COLUMN tb_upload_session.file_name           IS '요청 파일명(최종 이름은 커밋 때 확정)';
COMMENT ON COLUMN tb_upload_session.content_type        IS 'Content-Type(서버가 확장자로 결정)';
COMMENT ON COLUMN tb_upload_session.size_byte           IS '신고 크기(byte) — 진행 중 용량 합계에 쓰인다';
COMMENT ON COLUMN tb_upload_session.storage_key         IS '스토리지 key({tenantSeq}/{upload_session_id}) — 스토리지 준비 후 채움';
COMMENT ON COLUMN tb_upload_session.upload_id           IS '멀티파트 업로드 ID(멀티파트일 때만) — 스토리지 준비 후 채움';
COMMENT ON COLUMN tb_upload_session.part_size           IS '파트 크기(byte, 멀티파트일 때만)';
COMMENT ON COLUMN tb_upload_session.part_count          IS '파트 수(멀티파트일 때만)';
COMMENT ON COLUMN tb_upload_session.status              IS '상태(PENDING / COMPLETING / COMPLETED / FAILED)';
COMMENT ON COLUMN tb_upload_session.fail_reason         IS '실패 사유(FAILED 일 때)';
COMMENT ON COLUMN tb_upload_session.result_resource_seq IS '반영된 리소스 seq(COMPLETED — 멱등 응답용)';
COMMENT ON COLUMN tb_upload_session.result_name         IS '확정 파일명(COMPLETED — 멱등 응답용)';
COMMENT ON COLUMN tb_upload_session.last_error          IS '마지막 오류 내용';
COMMENT ON COLUMN tb_upload_session.last_activity       IS '마지막 활동 시각(TTL 슬라이딩 기준)';
COMMENT ON COLUMN tb_upload_session.completing_at       IS 'COMPLETING 전환 시각';
COMMENT ON COLUMN tb_upload_session.create_date         IS '생성 시각';

ALTER TABLE tb_storage_deletion ADD COLUMN action    varchar(20)   NOT NULL DEFAULT 'DELETE';
ALTER TABLE tb_storage_deletion ADD COLUMN upload_id varchar(1024) DEFAULT NULL;
ALTER TABLE tb_storage_deletion ADD CONSTRAINT chk_storage_deletion_action CHECK (action IN ('DELETE', 'ABORT_MULTIPART'));
ALTER TABLE tb_storage_deletion ADD CONSTRAINT chk_storage_deletion_abort_upload_id CHECK (action <> 'ABORT_MULTIPART' OR upload_id IS NOT NULL);
DROP INDEX idx_storage_deletion_next_attempt;
CREATE INDEX idx_storage_deletion_due ON tb_storage_deletion (action, next_attempt_date, storage_deletion_seq);
COMMENT ON COLUMN tb_storage_deletion.action    IS '정리 동작(DELETE: 객체 삭제 / ABORT_MULTIPART: 미완성 멀티파트 업로드 중단)';
COMMENT ON COLUMN tb_storage_deletion.upload_id IS '멀티파트 업로드 ID(ABORT_MULTIPART 전용)';

CREATE INDEX idx_resource_version_resource_save_name ON tb_resource_version (resource_save_name);
