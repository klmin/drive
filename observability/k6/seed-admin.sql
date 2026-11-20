-- 부하 테스트 DB(loadtest-db) 전용 — 기본 테넌트 관리자(admin) 비밀번호를 테스트 값 'LoadTest!2026' 으로 맞춘다.
-- 운영·dev DB 에 실행하지 않는다. 앱이 한 번 기동해 Flyway 로 스키마를 만든 뒤 실행:
--   docker compose exec -T loadtest-db psql -U drive_load -d drive_load < k6/seed-admin.sql
UPDATE tb_user
   SET enc_pwd = '{bcrypt}$2a$10$5OowjmKjmx501v0bYtTsjOe6RGu/59s3J94gPcWMbGR/SRqxUInVW',
       login_failure_count = 0
 WHERE user_id = 'admin' AND tenant_seq = 1;

-- 쿼리별 누적 시간 수집(Grafana "DB 잠금·느린 쿼리") — compose 의 shared_preload_libraries 와 짝. 이미 있으면 그대로
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
