-- -------------------------------------------------------------------------------------
-- 업로드 세션 진행 중 용량을 "신고 크기"가 아니라 "커밋 때 늘어날 것으로 보이는 크기"로 센다.
--   덮어쓰기(OVERWRITE)는 새 크기 − 시작 시점 대상 크기(0 이상)만 사용량이 늘어나는데, 지금까지는 파일 전체 크기로
--   사전 필터·진행 중 합계를 계산해 용량이 거의 찬 사용자의 수정 저장을 시작 단계에서 잘못 거절했다.
--   NEW·VERSION·RESTORE 는 전체 크기(이전 파일이 버전으로 남으므로) — size_byte 와 같은 값.
-- 최종 판정은 여전히 커밋 tx(설계 규칙 11). 여기 값은 사전 필터와 화면(uploadingByte)용.
-- NULL 허용 + 합계는 COALESCE(reserve_byte, size_byte) — 배포 시점에 진행 중이던 세션(값 없음)은 예전처럼 전체 크기로 센다.
-- 컬럼 추가만이라 테이블 재작성·장시간 잠금 없음.
-- -------------------------------------------------------------------------------------

ALTER TABLE tb_upload_session ADD COLUMN reserve_byte bigint DEFAULT NULL;

COMMENT ON COLUMN tb_upload_session.reserve_byte IS '진행 중 용량으로 셀 크기(byte) — OVERWRITE 는 새 크기 − 시작 시 대상 크기(0 이상), 그 외 size_byte. NULL 이면 size_byte';
