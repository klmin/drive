-- PostgreSQL 쪽 테이블별 행 수 (MySQL 쪽 결과와 대조)
--   예상 차이: 제외 5개(oauth2_authorization·oauth2_authorization_consent·spring_session·spring_session_attributes·shedlock) = 0,
--             tb_mcp_oauth_jwk = PG 에 테이블 없음(구 이름 잔존 테이블, 02 에서 제외)
--             tb_recent·tb_starred = MySQL 행수 - shared_by_user_seq IS NULL 행수 (03_after_load.sql 2-1 에서 삭제)
--             tb_user_role = MySQL 에 테이블 없음, PG 는 tb_user 행수와 같아야 함 (03_after_load.sql 2-2 에서 role_type 으로 채움)
--             tb_user_sso_identity = MySQL 행수 - 사용자 행이 없는 연결 수 (03_after_load.sql 2-3 에서 삭제)
--   MySQL 쪽 사용자 없는 SSO 연결 수: SELECT COUNT(*) FROM tb_user_sso_identity i LEFT JOIN tb_user u ON u.user_seq = i.user_seq WHERE u.user_seq IS NULL;
--   MySQL 쪽 NULL 행수: SELECT (SELECT COUNT(*) FROM tb_recent WHERE shared_by_user_seq IS NULL) r, (SELECT COUNT(*) FROM tb_starred WHERE shared_by_user_seq IS NULL) s;
SELECT table_name,
       (xpath('/row/c/text()', query_to_xml(format('SELECT count(*) AS c FROM %I', table_name), false, true, '')))[1]::text::bigint AS rows
  FROM information_schema.tables
 WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
   AND table_name NOT IN (SELECT c.relname FROM pg_class c WHERE c.relnamespace = 'public'::regnamespace AND c.relispartition)   -- 파티션 자식(_p0/_p1) 제외, 부모 count 가 전체
 ORDER BY table_name;

-- 역할 없는 계정 = 0 이어야 함 (있으면 로그인 실패 — 03_after_load.sql 2-2 가 빠졌는지 확인). 완전 삭제(DELETED) 계정은 역할이 없는 게 맞아 제외
SELECT count(*) AS users_without_role
  FROM tb_user u
 WHERE u.status <> 'DELETED'
   AND NOT EXISTS (SELECT 1 FROM tb_user_role r WHERE r.user_seq = u.user_seq);

-- SSO 연결 테넌트가 사용자 테넌트와 다른 행 = 0 이어야 함 (03_after_load.sql 2-3 이 빠졌거나 잘못 채워졌는지 확인)
SELECT count(*) AS sso_identity_tenant_mismatch
  FROM tb_user_sso_identity i
  JOIN tb_user u ON u.user_seq = i.user_seq
 WHERE i.tenant_seq IS DISTINCT FROM u.tenant_seq;
