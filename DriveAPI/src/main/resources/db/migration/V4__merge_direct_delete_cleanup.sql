-- -------------------------------------------------------------------------------------
-- 직접 영구 삭제 API 의 물리 삭제를 tb_storage_deletion 으로 통합 — 남은 대기 행을 "옮기고"(원본에서 삭제) 전용 큐를 지운다.
--   점유(lease) 중이던 행도 옮긴다: 구 WAS 가 이미 지웠더라도 스토리지 삭제는 멱등이라 다시 지워도 무해.
--   attempt_count 는 0 으로 새로 시작(구 큐는 상한이 없었으므로 기존 횟수를 가져오면 바로 데드레터가 될 수 있다).
--   Flyway 는 PG 에서 이 파일을 한 트랜잭션으로 실행한다 — 이관과 DROP 이 함께 성공하거나 함께 실패한다.
--   V2 는 이미 다른 DB 에 적용됐을 수 있으므로 수정하지 않고 여기서 정리한다.
-- -------------------------------------------------------------------------------------

WITH moved AS (
    DELETE FROM tb_direct_delete_cleanup
    RETURNING object_key, created_at
)
INSERT INTO tb_storage_deletion (storage_key, attempt_count, next_attempt_date, register_date)
SELECT object_key, 0, now(), created_at FROM moved;

DROP TABLE tb_direct_delete_cleanup;
