-- =====================================================================================
-- 테넌트별 user_id 유니크 — 이미 PostgreSQL 로 운영 중인 DB 업데이트 (t_drive DB 에 t_drive_user 로 실행)
--   psql -h 127.0.0.1 -U t_drive_user -d t_drive -v ON_ERROR_STOP=1 -f TFDRIVE-1897_tenant_user_id.sql
-- 자동으로 실행되지 않는다 — Flyway 도입(2026-11) 전 DB 를 V1 상태로 맞추는 수동 스크립트. 실행 뒤 앱 첫 기동은 DRIVE_FLYWAY_BASELINE_ON_MIGRATE=true(../README.md).
-- 1) tb_user: user_id 유일 범위를 전체 → 테넌트 안으로 바꾼다(다른 테넌트에는 같은 이메일 허용). tenant_seq NULL(슈퍼관리자·익명)끼리는 계속 유일.
--    기존 데이터는 전체에서 유일하므로 새 인덱스도 그대로 만족한다 — 데이터 이관 없음.
-- 2) tb_user_sso_identity: tenant_seq 를 추가해 연결된 사용자의 테넌트로 채우고, (sso_provider_seq, subject) 유니크를 테넌트 안으로 바꾼다
--    (GLOBAL Provider 의 같은 IdP 계정을 테넌트마다 다른 계정에 연결 허용).
--    사용자 행이 없는 연결(user_seq 가 tb_user 에 없음)은 로그인 조회(사용자 INNER JOIN)에서도 쓰이지 않던 행이라 지운다 — NOT NULL 을 걸기 위해.
--    테넌트 없는 사용자(슈퍼관리자 등)에 연결된 행이 있으면 SET NOT NULL 에서 실패하고 전체가 롤백된다(SSO 는 테넌트 사용자만 연결하므로 정상이면 0건).
--    사전 확인: SELECT i.* FROM tb_user_sso_identity i JOIN tb_user u ON u.user_seq = i.user_seq WHERE u.tenant_seq IS NULL;
-- 새로 설치하는 DB 는 initialize.sql 이 같은 정의로 만든다.
-- 여러 번 실행해도 안전하다(IF EXISTS / IF NOT EXISTS, 채우기는 NULL 인 행만).
-- =====================================================================================
BEGIN;

DROP INDEX IF EXISTS uniq_active_user_id;
CREATE UNIQUE INDEX uniq_active_user_id ON tb_user ((COALESCE(tenant_seq, 0)), user_id) WHERE status <> 'DELETED';

ALTER TABLE tb_user_sso_identity ADD COLUMN IF NOT EXISTS tenant_seq integer;
UPDATE tb_user_sso_identity i SET tenant_seq = u.tenant_seq FROM tb_user u WHERE u.user_seq = i.user_seq AND i.tenant_seq IS NULL;
DELETE FROM tb_user_sso_identity i WHERE NOT EXISTS (SELECT 1 FROM tb_user u WHERE u.user_seq = i.user_seq);
ALTER TABLE tb_user_sso_identity ALTER COLUMN tenant_seq SET NOT NULL;
ALTER TABLE tb_user_sso_identity DROP CONSTRAINT IF EXISTS uniq_provider_subject;
ALTER TABLE tb_user_sso_identity DROP CONSTRAINT IF EXISTS uniq_provider_subject_tenant;
ALTER TABLE tb_user_sso_identity ADD CONSTRAINT uniq_provider_subject_tenant UNIQUE (sso_provider_seq, subject, tenant_seq);
COMMENT ON COLUMN tb_user_sso_identity.tenant_seq IS '연결된 유저의 테넌트 (tb_user.tenant_seq 와 같음). GLOBAL Provider 의 같은 IdP 계정을 테넌트마다 따로 연결하기 위한 유니크 범위';
COMMENT ON CONSTRAINT uniq_provider_subject_tenant ON tb_user_sso_identity IS '같은 테넌트 안에서 같은 IdP의 같은 사람 중복 방지 (다른 테넌트에는 같은 IdP 계정 연결 허용)';

COMMIT;

-- 확인: 아래 정의가 나오면 적용된 것
--   SELECT indexdef FROM pg_indexes WHERE tablename = 'tb_user' AND indexname = 'uniq_active_user_id';
--   → CREATE UNIQUE INDEX uniq_active_user_id ON public.tb_user USING btree (COALESCE(tenant_seq, 0), user_id) WHERE ((status)::text <> 'DELETED'::text)
--   SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'uniq_provider_subject_tenant';
--   → UNIQUE (sso_provider_seq, subject, tenant_seq)
