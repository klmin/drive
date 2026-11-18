-- 초기화 진입점: 원본 initialize.sql은 빈 DB에서만 실행합니다.
-- 이 파일을 수정하면 Developers chart의 ConfigMap용 사본도 동기화해야 합니다.
-- 명령과 배포 순서는 같은 폴더의 README.md를 확인하세요.
\set ON_ERROR_STOP on
SET search_path = public, pg_catalog;
SET lock_timeout = '120s';
SET statement_timeout = '10min';
SELECT pg_advisory_lock(73104, 1);
-- Recheck AFTER obtaining the lock: another Pod may have initialized the DB.
SELECT count(*) = 4 AS ready
FROM (VALUES ('tb_tenant'), ('tb_user'), ('tb_user_role'), ('tb_system_config')) AS expected(name)
JOIN pg_class c ON c.relname = expected.name
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
\gset
SELECT NOT EXISTS (
 SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = 'public'
 UNION ALL
 SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace WHERE n.nspname = 'public'
 UNION ALL
 SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid = t.typnamespace WHERE n.nspname = 'public'
 UNION ALL
 SELECT 1 FROM pg_collation c JOIN pg_namespace n ON n.oid = c.collnamespace WHERE n.nspname = 'public'
) AS empty
\gset
\if :ready
 \echo '[drive-db-init] Existing tables found; preserving all accounts and data.'
\else
 \if :empty
  \echo '[drive-db-init] Initializing empty database with original SQL.'
  \ir /sql/initialize.sql
 \else
  DO $$ BEGIN
   RAISE EXCEPTION 'DRIVE_DB_INIT_PARTIAL: public schema is not empty and required tables are missing. Inspect the database; no objects were removed.';
  END $$;
 \endif
\endif
DO $$
BEGIN
 IF NOT EXISTS (SELECT 1 FROM tb_user_role WHERE role_type = 'SUPER_ADMIN') THEN
  RAISE EXCEPTION 'DRIVE_DB_INIT_ADMIN_MISSING: Super Admin role is missing. Restore the intended account manually.';
 END IF;
 IF NOT EXISTS (
  SELECT 1 FROM tb_user u JOIN tb_user_role r ON r.user_seq = u.user_seq
  WHERE r.role_type = 'SUPER_ADMIN' AND u.status = 'ACTIVE' AND u.tenant_seq IS NULL
 ) THEN
  RAISE EXCEPTION 'DRIVE_DB_INIT_ADMIN_INACTIVE: No active global Super Admin account. Review account status; no account was changed.';
 END IF;
END $$;
SELECT pg_advisory_unlock(73104, 1);
\echo '[drive-db-init] Initialization check passed.'
