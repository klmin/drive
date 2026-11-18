-- =====================================================================================
-- TFDRIVE-1896 계정 다중 역할 — 이미 PostgreSQL 로 운영 중인 DB 업데이트 (t_drive DB 에 t_drive_user 로 실행)
--   psql -h 127.0.0.1 -U t_drive_user -d t_drive -v ON_ERROR_STOP=1 -f TFDRIVE-1896_user_role.sql
-- 자동으로 실행되지 않는다 — Flyway 도입(2026-11) 전 DB 를 V1 상태로 맞추는 수동 스크립트. 실행 뒤 앱 첫 기동은 DRIVE_FLYWAY_BASELINE_ON_MIGRATE=true(../README.md).
--   실행하지 않으면 tb_user_role 이 없거나 비어 있어 모든 계정의 로그인·API Key·MCP 인증이 실패한다.
-- tb_user.role_type·is_active 컬럼을 지운다 — 되돌릴 수 없으므로 실행 전에 반드시 DB 를 백업한다.
--   지운 뒤에는 이전 버전 애플리케이션이 tb_user 를 읽지 못한다(새 버전과 함께 배포).
-- 새로 설치하는 DB 는 initialize.sql 이, MySQL 에서 옮기는 DB 는 01·03 이관 스크립트가 같은 일을 하므로 필요 없다.
-- 여러 번 실행해도 안전하다(IF NOT EXISTS·역할 채우기는 role_type 컬럼이 남아 있을 때만·DROP COLUMN IF EXISTS).
-- =====================================================================================
BEGIN;

-- 1) 계정 역할 테이블 (initialize.sql 과 동일)
CREATE TABLE IF NOT EXISTS tb_user_role (
  user_seq          integer     NOT NULL,
  role_type         varchar(20) NOT NULL,
  register_user_seq integer     NOT NULL,
  register_date     timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_seq, role_type)
);
-- 이전 버전 스크립트로 만든 FK 제거(initialize.sql 과 맞춤 — 사용자 행은 상태값으로만 삭제되어 CASCADE 가 쓰이지 않는다)
ALTER TABLE tb_user_role DROP CONSTRAINT IF EXISTS fk_user_role_user;
CREATE INDEX IF NOT EXISTS idx_user_role_role ON tb_user_role (role_type, user_seq);
COMMENT ON COLUMN tb_user_role.user_seq          IS '유저 seq';
COMMENT ON COLUMN tb_user_role.role_type         IS '권한 타입 (SUPER_ADMIN, ADMIN, USER, ANONYMOUS)';
COMMENT ON COLUMN tb_user_role.register_date     IS '부여 일시';
COMMENT ON COLUMN tb_user_role.register_user_seq IS '부여한 사용자 seq';

-- 2) 기존 계정 역할 채우기 — 지금까지 계정은 역할이 하나(tb_user.role_type)뿐이라 그 값을 그대로 부여한다
--    role_type 컬럼이 아직 있을 때만(두 번째 실행부터는 3) 에서 지워져 건너뛴다). 동적 SQL 이라 컬럼이 없어도 파싱 오류가 나지 않는다
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns
              WHERE table_schema = 'public' AND table_name = 'tb_user' AND column_name = 'role_type') THEN
    EXECUTE $sql$
      INSERT INTO tb_user_role (user_seq, role_type, register_user_seq, register_date)
      SELECT user_seq, role_type, 1, now()
        FROM tb_user
       WHERE role_type IN ('SUPER_ADMIN', 'ADMIN', 'USER', 'ANONYMOUS')   -- 그 밖의 값은 부여하지 않음(역할 없는 계정 확인으로 드러남)
         AND status <> 'DELETED'                                           -- 완전 삭제 계정은 역할 없음
      ON CONFLICT (user_seq, role_type) DO NOTHING
    $sql$;
  END IF;
END $$;

-- 2-1) 완전 삭제(DELETED) 계정의 역할 행 제거 — 애플리케이션은 완전 삭제 시 역할 행을 지운다(FK·CASCADE 없음).
--      이 스크립트의 이전 버전으로 DELETED 계정에도 역할을 채운 DB 정리용(이미 깨끗하면 0행)
DELETE FROM tb_user_role r
 USING tb_user u
 WHERE u.user_seq = r.user_seq
   AND u.status = 'DELETED';

-- 3) 역할은 tb_user_role 이 정본 — tb_user 의 단일 역할·활성 여부 컬럼 제거(활성 여부는 status 로 판정)
ALTER TABLE tb_user
  DROP COLUMN IF EXISTS role_type,
  DROP COLUMN IF EXISTS is_active;

COMMIT;

-- 4) 확인 — 역할 없는 계정 = 0 이어야 함(DELETED 제외)
SELECT count(*) AS users_without_role
  FROM tb_user u
 WHERE u.status <> 'DELETED'
   AND NOT EXISTS (SELECT 1 FROM tb_user_role r WHERE r.user_seq = u.user_seq);
