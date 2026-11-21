-- -------------------------------------------------------------------------------------
-- 폴더 트리(/api/v1/resources/tree) 재귀 쿼리용 인덱스.
--   재귀 단계는 "부모 폴더의 하위 중 폴더만" 찾는데(parent_folder_seq = ? AND resource_type = 'FOLDER'),
--   기존 (user_seq, parent_folder_seq) 인덱스로는 하위 파일까지 모두 읽고 나서 폴더만 남긴다.
--   계획에 따라서는 모든 사용자의 폴더를 해시 조인으로 단계마다 다시 읽는다.
--   측정(부하 테스트 DB, 파일 약 110만 건, 폴더 541개·깊이 30인 사용자, generic plan): 41ms → 2ms.
--   동시 사용자 120명 부하에서 이 쿼리가 DB 시간의 44%(평균 295ms)를 차지했다.
-- resource_type, is_delete 는 앱에서 바인드 변수로 넘어오므로 부분 인덱스(WHERE resource_type = 'FOLDER')는 generic plan 에서 못 쓴다.
--   그래서 컬럼에 resource_type 을 포함한다.
-- 주의: V8 과 같이 파티션 테이블이라 CONCURRENTLY 를 못 쓰고, 만드는 동안 tb_resource 쓰기가 막힌다.
-- -------------------------------------------------------------------------------------

CREATE INDEX idx_resource_user_parent_type ON tb_resource (user_seq, parent_folder_seq, resource_type);
