-- =====================================================================================
-- MySQL -> PostgreSQL 데이터 이전: 적재 후 복원·보정 (t_drive DB 에 t_drive_user 로 실행)
--   psql -h 127.0.0.1 -U t_drive_user -d t_drive -v ON_ERROR_STOP=1 -f 03_after_load.sql
-- =====================================================================================
BEGIN;

-- 1) 스테이징 -> tb_resource 본 테이블 (생성 컬럼 2개는 제외하고 넣으면 PG 가 다시 계산)
--    컬럼 목록은 카탈로그에서 뽑아 향후 컬럼 추가에도 그대로 동작. tb_resource 의 컬럼 순서·인덱스·파티션은 initialize.sql 그대로 유지된다.
TRUNCATE tb_resource;
DO $$
DECLARE cols text;
BEGIN
  SELECT string_agg(quote_ident(column_name), ', ' ORDER BY ordinal_position) INTO cols
    FROM information_schema.columns
   WHERE table_schema = 'public' AND table_name = 'tb_resource' AND is_generated = 'NEVER';
  EXECUTE format('INSERT INTO tb_resource (%s) SELECT %s FROM tb_resource_stage', cols, cols);
END $$;
DROP TABLE tb_resource_stage;

-- 2) FK 복원 (여기서 실패하면 MySQL 데이터에 고아 행이 있다는 뜻)
ALTER TABLE tb_system_config
  ADD CONSTRAINT fk_tb_syscfg_reg_user FOREIGN KEY (register_user_seq) REFERENCES tb_user (user_seq),
  ADD CONSTRAINT fk_tb_syscfg_upd_user FOREIGN KEY (update_user_seq)   REFERENCES tb_user (user_seq);
ALTER TABLE tb_user_log
  ADD CONSTRAINT fk_user_log_user_seq FOREIGN KEY (user_seq) REFERENCES tb_user (user_seq);

-- 2-1) tb_recent·tb_starred 의 shared_by_user_seq NOT NULL 복원
--      NULL 행은 목록 조회(INNER JOIN tb_resource ON shared_by_user_seq = user_seq)에 안 잡히고 유니크 제약도 못 거는 고아 행이라 삭제한다.
DELETE FROM tb_recent  WHERE shared_by_user_seq IS NULL;
DELETE FROM tb_starred WHERE shared_by_user_seq IS NULL;
ALTER TABLE tb_recent  ALTER COLUMN shared_by_user_seq SET NOT NULL;
ALTER TABLE tb_starred ALTER COLUMN shared_by_user_seq SET NOT NULL;

-- 2-2) 계정 역할(tb_user_role) 채우기 + 임시 컬럼 제거
--      MySQL 에는 tb_user_role 이 없고, initialize.sql 의 시드 역할은 01_before_load.sql 1) 이 tb_user_role 을 직접 TRUNCATE 해 지운다(FK 없음).
--      MySQL 시절 계정은 역할이 하나(tb_user.role_type)뿐이라 그 값을 그대로 부여한다. 역할이 없으면 로그인이 실패한다.
--      role_type·is_active 는 01_before_load.sql 5) 가 적재용으로 붙인 임시 컬럼 — 역할을 옮긴 뒤 지운다(PG 스키마에는 없음).
INSERT INTO tb_user_role (user_seq, role_type, register_user_seq, register_date)
SELECT user_seq, role_type, 1, now()
  FROM tb_user
 WHERE role_type IN ('SUPER_ADMIN', 'ADMIN', 'USER', 'ANONYMOUS')   -- 그 밖의 값은 부여하지 않음(역할 없는 계정 확인으로 드러남)
   AND status <> 'DELETED'                                           -- 완전 삭제 계정은 역할 없음(애플리케이션도 완전 삭제 시 역할 행을 지운다)
ON CONFLICT (user_seq, role_type) DO NOTHING;
ALTER TABLE tb_user
  DROP COLUMN role_type,
  DROP COLUMN is_active;

-- 2-3) SSO 연결(tb_user_sso_identity)의 tenant_seq 채우기 + NOT NULL 복원 (TFDRIVE-1897, upgrade SQL 과 같은 규칙)
--      MySQL 에는 컬럼이 없어 NULL 로 적재됐다 — 연결된 사용자의 테넌트로 채운다.
--      사용자 행이 없는 연결은 로그인 조회(사용자 INNER JOIN)에서도 쓰이지 않던 행이라 지운다.
--      테넌트 없는 사용자(슈퍼관리자 등)에 연결된 행이 있으면 SET NOT NULL 에서 실패하고 전체가 롤백된다(SSO 는 테넌트 사용자만 연결하므로 정상이면 0건).
UPDATE tb_user_sso_identity i SET tenant_seq = u.tenant_seq FROM tb_user u WHERE u.user_seq = i.user_seq AND i.tenant_seq IS NULL;
DELETE FROM tb_user_sso_identity i WHERE NOT EXISTS (SELECT 1 FROM tb_user u WHERE u.user_seq = i.user_seq);
ALTER TABLE tb_user_sso_identity ALTER COLUMN tenant_seq SET NOT NULL;

-- 2-4) 테넌트 유형 ORGANIZATION → SELF_MANAGED (SYSTEM_MANAGED 테넌트, upgrade SQL 과 같은 규칙)
--      MySQL 값이 그대로 적재된다. user_mode·is_protected 는 MySQL 에 없어 컬럼 기본값(MULTI·'N')으로 채워진다.
UPDATE tb_tenant SET tenant_type = 'SELF_MANAGED' WHERE tenant_type = 'ORGANIZATION';
--      설치 시드 계정(superadmin·admin·anonymous)은 보호 계정 — initialize.sql 시드와 같은 값
UPDATE tb_user SET is_protected = 'Y'
 WHERE (user_id = 'superadmin' AND tenant_seq IS NULL)
    OR (user_id = 'admin'      AND tenant_seq = 1)
    OR (user_id = 'anonymous'  AND tenant_seq IS NULL);

-- 3) 모든 IDENTITY 시퀀스를 MAX+1 로 보정 (빈 테이블은 1)
--    파티션 자식({table}_p0/_p1)은 제외 — 부모와 시퀀스를 공유하므로 자식의 MAX 로 setval 하면 실제 MAX 보다 낮게 잡힐 수 있음
DO $$
DECLARE r record;
BEGIN
  FOR r IN
    SELECT col.table_name, col.column_name
      FROM information_schema.columns col
      JOIN pg_class c ON c.relname = col.table_name AND c.relnamespace = 'public'::regnamespace
     WHERE col.table_schema = 'public' AND col.is_identity = 'YES' AND NOT c.relispartition
  LOOP
    EXECUTE format(
      'SELECT setval(pg_get_serial_sequence(%L, %L), COALESCE((SELECT MAX(%I) FROM %I), 0) + 1, false)',
      r.table_name, r.column_name, r.column_name, r.table_name);
  END LOOP;
END $$;

COMMIT;

-- 4) 통계 갱신 (public 테이블만 — 인자 없는 ANALYZE 는 시스템 카탈로그까지 돌아 permission denied WARNING 을 찍음)
DO $$
DECLARE r record;
BEGIN
  FOR r IN SELECT relname FROM pg_class WHERE relnamespace = 'public'::regnamespace AND relkind IN ('r','p') AND NOT relispartition LOOP
    EXECUTE format('ANALYZE %I', r.relname);
  END LOOP;
END $$;
