-- =====================================================================================
-- MySQL -> PostgreSQL 데이터 이전: 적재 전 준비 (t_drive DB 에 t_drive_user 로 실행)
--   psql -h 127.0.0.1 -U t_drive_user -d t_drive -v ON_ERROR_STOP=1 -f 01_before_load.sql
-- 전제: initialize.sql 로 스키마가 이미 생성되어 있음
-- =====================================================================================
BEGIN;

-- 1) initialize.sql 이 넣은 초기 데이터 제거 (MySQL 데이터로 대체됨)
--    tb_user_role 은 FK 가 없어 tb_user CASCADE 로 비워지지 않는다 — 시드 역할(1 SUPER_ADMIN·2 ADMIN·3 ANONYMOUS)이 남으면
--    03_after_load.sql 2-2 가 그 위에 역할을 더해 같은 user_seq 의 이관 계정이 시드 역할까지 갖게 되므로 직접 비운다
TRUNCATE tb_user_log, tb_system_config, tb_share_setting, tb_email_template, tb_user_role, tb_user, tb_tenant
  RESTART IDENTITY CASCADE;

-- 2) FK 잠시 제거 (pgloader 는 테이블 적재 순서를 보장하지 않음) -> 03_after_load.sql 에서 복원
ALTER TABLE tb_system_config
  DROP CONSTRAINT fk_tb_syscfg_reg_user,
  DROP CONSTRAINT fk_tb_syscfg_upd_user;
ALTER TABLE tb_user_log
  DROP CONSTRAINT fk_user_log_user_seq;

-- 3) tb_resource 는 스테이징 테이블로 받는다 (tb_resource 본 테이블은 손대지 않음 → 컬럼 순서·생성 컬럼·인덱스 그대로)
--    이유: pgloader data only 는 MySQL VIRTUAL 컬럼(resource_name_numeric_part/text_part) 값도 넘기는데
--         PG 생성 컬럼에는 값을 넣을 수 없고, 컬럼을 떼었다 붙이면 테이블 끝으로 밀려 MySQL 과 순서가 달라진다.
--    02_drive.load 의 ALTER TABLE NAMES MATCHING 'tb_resource' RENAME TO 'tb_resource_stage' 가 여기로 적재하고,
--    03_after_load.sql 이 생성 컬럼 2개를 뺀 나머지 컬럼만 tb_resource 로 옮긴 뒤 스테이징을 버린다.
--    LIKE 는 INCLUDING GENERATED 를 안 주면 생성 컬럼을 일반 컬럼으로 복사하므로 MySQL 값이 그대로 들어간다.
--    (DEFAULT 만 복사, PK·인덱스·파티션 없음 = 적재 빠름)
DROP TABLE IF EXISTS tb_resource_stage;
CREATE TABLE tb_resource_stage (LIKE tb_resource INCLUDING DEFAULTS);
ALTER TABLE tb_resource_stage ALTER COLUMN resource_name_numeric_part TYPE bigint;   -- MySQL int unsigned 캐스트(bigint) 경고 제거용

-- 4) tb_recent·tb_starred 의 shared_by_user_seq NOT NULL 잠시 해제 (MySQL 은 NULL 허용이라 NULL 행이 있으면 적재가 실패함)
--    -> 03_after_load.sql 에서 NULL 행 삭제 후 복원
ALTER TABLE tb_recent  ALTER COLUMN shared_by_user_seq DROP NOT NULL;
ALTER TABLE tb_starred ALTER COLUMN shared_by_user_seq DROP NOT NULL;

-- 5) tb_user 에 MySQL 에만 있는 컬럼(role_type·is_active)을 잠시 붙인다 (TFDRIVE-1896 에서 PG 스키마에서 뺀 컬럼)
--    pgloader data only 는 MySQL 컬럼을 이름으로 그대로 넣으므로 대상에 없으면 tb_user 적재가 실패한다.
--    03_after_load.sql 2-2 가 role_type 으로 tb_user_role 을 채운 뒤 두 컬럼을 지운다(테이블 끝에 붙였다 떼므로 나머지 컬럼 순서는 그대로).
ALTER TABLE tb_user
  ADD COLUMN role_type varchar(20),
  ADD COLUMN is_active varchar(1);

-- 6) tb_user_sso_identity.tenant_seq NOT NULL 잠시 해제 (TFDRIVE-1897 에서 PG 에만 추가한 컬럼 — MySQL 에는 없어 NULL 로 적재됨)
--    -> 03_after_load.sql 2-3 에서 사용자 테넌트로 채운 뒤 복원
ALTER TABLE tb_user_sso_identity ALTER COLUMN tenant_seq DROP NOT NULL;

COMMIT;
