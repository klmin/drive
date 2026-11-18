-- -------------------------------------------------------------------------------------
-- tb_base_domain.scheme · port — 베이스 도메인 입력값 [scheme://]host[:port] 의 스킴·포트
--   입력은 문자열 하나로 받고 서버가 나눠 저장한다(BaseDomainAddress). base_domain 은 지금처럼 호스트만 —
--   테넌트 도메인 조합(subdomain.base_domain)과 SUBDOMAIN 모드 인바운드 판별 키(포트 없는 Host)의 재료라 포트·스킴을 섞지 않는다.
--   scheme·port 는 아웃바운드 URL(메일·공유 링크·OIDC redirect 등) 조립에만 쓴다. NULL 이면 요청 스킴(요청 없으면 https)·호출부 포트 규칙.
--   스킴과 짝이 맞는 기본 포트(http:80, https:443)는 저장하지 않는다(NULL).
-- -------------------------------------------------------------------------------------

ALTER TABLE tb_base_domain ADD COLUMN scheme varchar(5) DEFAULT NULL;
ALTER TABLE tb_base_domain ADD COLUMN port   integer    DEFAULT NULL;
ALTER TABLE tb_base_domain ADD CONSTRAINT chk_base_domain_scheme CHECK (scheme IN ('http', 'https'));
ALTER TABLE tb_base_domain ADD CONSTRAINT chk_base_domain_port   CHECK (port BETWEEN 1 AND 65535);
COMMENT ON COLUMN tb_base_domain.scheme IS '아웃바운드 스킴(http|https). NULL 이면 요청 스킴, 요청이 없으면 https';
COMMENT ON COLUMN tb_base_domain.port   IS '아웃바운드 포트(1~65535). NULL 이면 생략(스킴 기본 포트)';
