-- -------------------------------------------------------------------------------------
-- 내 파일 검색·목록의 정렬 인덱스에 user_seq 를 앞에 둔다.
--   기존 정렬 인덱스(idx_resource_sort_*)는 (resource_type DESC, 정렬 컬럼) 이라 사용자 구분이 없다.
--   tb_resource 는 HASH(user_seq) 파티션 2개라 한 파티션에 여러 사용자의 행이 섞여 있다.
--   앱의 prepared statement 가 generic plan 으로 바뀌면 PostgreSQL 이 이 정렬 인덱스를 순서대로 훑으며
--   user_seq·is_delete·LIKE 를 행마다 거르는 계획을 고른다. 결과가 31건에 못 미치는 검색(드문 키워드)은
--   파티션 전체를 읽는다.
--   측정(부하 테스트 DB, 파일 약 110만 건, 사용자 18명, 결과 0건 키워드, generic plan):
--     이름순  628ms → 35ms (버린 행 732,612 → 61,051)
--     크기순  205ms →  9ms
--     수정일순 326ms → 14ms
--   (user_seq, 정렬 컬럼) 인덱스는 이 사용자 구간만 정렬 순서대로 읽고 31건이 모이면 멈춘다.
-- 확장자 정렬은 API 에 없어서(ResourceSortField: NAME, SIZE, UPDATE, OPEN) 추가하지 않는다.
-- 기존 정렬 인덱스는 이번에는 그대로 둔다(사용자 구분 없이 쓰는 쿼리가 없는지 확인한 뒤 따로 정리).
-- 주의: 파티션 테이블에는 CREATE INDEX CONCURRENTLY 를 쓸 수 없어서, 인덱스를 만드는 동안 tb_resource 쓰기가 막힌다.
--   부하 테스트 DB(약 110만 건)에서 세 개 합쳐 수 초 걸렸다. 행이 매우 많은 설치본은 점검 시간에 배포한다.
-- -------------------------------------------------------------------------------------

-- 이름 정렬·커서 튜플과 같은 COLLATE 로 만들어야 인덱스를 탄다 (V1 의 idx_resource_sort_resource_name 과 같은 식)
CREATE INDEX idx_resource_user_sort_resource_name ON tb_resource (user_seq, resource_type DESC, resource_name_numeric_part, resource_name_text_part COLLATE drive_name_sort, resource_name COLLATE drive_name_sort);
CREATE INDEX idx_resource_user_sort_size_byte     ON tb_resource (user_seq, resource_type DESC, size_byte);
CREATE INDEX idx_resource_user_sort_update_date   ON tb_resource (user_seq, resource_type DESC, update_date);
