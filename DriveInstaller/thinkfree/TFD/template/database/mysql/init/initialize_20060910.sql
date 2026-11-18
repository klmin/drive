-- db create
CREATE DATABASE IF NOT EXISTS t_drive CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
-- user create
CREATE USER IF NOT EXISTS 't_drive_user'@'%' IDENTIFIED BY 'changeme';
-- grant user permissions
GRANT ALL PRIVILEGES ON t_drive.* TO 't_drive_user'@'%';
-- use tdrive
USE t_drive;

-- t_drive.oauth2_authorization definition

CREATE TABLE `oauth2_authorization` (
  `id` varchar(100) NOT NULL,
  `registered_client_id` varchar(100) NOT NULL,
  `principal_name` varchar(200) NOT NULL,
  `authorization_grant_type` varchar(100) NOT NULL,
  `authorized_scopes` varchar(1000) DEFAULT NULL,
  `attributes` blob,
  `state` varchar(500) DEFAULT NULL,
  `authorization_code_value` blob,
  `authorization_code_issued_at` timestamp NULL DEFAULT NULL,
  `authorization_code_expires_at` timestamp NULL DEFAULT NULL,
  `authorization_code_metadata` blob,
  `access_token_value` blob,
  `access_token_issued_at` timestamp NULL DEFAULT NULL,
  `access_token_expires_at` timestamp NULL DEFAULT NULL,
  `access_token_metadata` blob,
  `access_token_type` varchar(100) DEFAULT NULL,
  `access_token_scopes` varchar(1000) DEFAULT NULL,
  `oidc_id_token_value` blob,
  `oidc_id_token_issued_at` timestamp NULL DEFAULT NULL,
  `oidc_id_token_expires_at` timestamp NULL DEFAULT NULL,
  `oidc_id_token_metadata` blob,
  `refresh_token_value` blob,
  `refresh_token_issued_at` timestamp NULL DEFAULT NULL,
  `refresh_token_expires_at` timestamp NULL DEFAULT NULL,
  `refresh_token_metadata` blob,
  `user_code_value` blob,
  `user_code_issued_at` timestamp NULL DEFAULT NULL,
  `user_code_expires_at` timestamp NULL DEFAULT NULL,
  `user_code_metadata` blob,
  `device_code_value` blob,
  `device_code_issued_at` timestamp NULL DEFAULT NULL,
  `device_code_expires_at` timestamp NULL DEFAULT NULL,
  `device_code_metadata` blob,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- t_drive.oauth2_authorization_consent definition

CREATE TABLE `oauth2_authorization_consent` (
  `registered_client_id` varchar(100) NOT NULL,
  `principal_name` varchar(200) NOT NULL,
  `authorities` varchar(1000) NOT NULL,
  PRIMARY KEY (`registered_client_id`,`principal_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- t_drive.oauth2_registered_client definition

CREATE TABLE `oauth2_registered_client` (
  `id` varchar(100) NOT NULL,
  `client_id` varchar(100) NOT NULL,
  `client_id_issued_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `client_secret` varchar(200) DEFAULT NULL,
  `client_secret_expires_at` timestamp NULL DEFAULT NULL,
  `client_name` varchar(200) NOT NULL,
  `client_authentication_methods` varchar(1000) NOT NULL,
  `authorization_grant_types` varchar(1000) NOT NULL,
  `redirect_uris` varchar(1000) DEFAULT NULL,
  `post_logout_redirect_uris` varchar(1000) DEFAULT NULL,
  `scopes` varchar(1000) NOT NULL,
  `client_settings` varchar(2000) NOT NULL,
  `token_settings` varchar(2000) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- t_drive.spring_session definition

CREATE TABLE `spring_session` (
  `PRIMARY_ID` char(36) NOT NULL,
  `SESSION_ID` char(36) NOT NULL,
  `CREATION_TIME` bigint NOT NULL,
  `LAST_ACCESS_TIME` bigint NOT NULL,
  `MAX_INACTIVE_INTERVAL` int NOT NULL,
  `EXPIRY_TIME` bigint NOT NULL,
  `PRINCIPAL_NAME` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`PRIMARY_ID`),
  UNIQUE KEY `SPRING_SESSION_IX1` (`SESSION_ID`),
  KEY `SPRING_SESSION_IX2` (`EXPIRY_TIME`),
  KEY `SPRING_SESSION_IX3` (`PRINCIPAL_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

-- t_drive.spring_session_attributes definition

CREATE TABLE `spring_session_attributes` (
  `SESSION_PRIMARY_ID` char(36) NOT NULL,
  `ATTRIBUTE_NAME` varchar(200) NOT NULL,
  `ATTRIBUTE_BYTES` blob NOT NULL,
  PRIMARY KEY (`SESSION_PRIMARY_ID`,`ATTRIBUTE_NAME`),
  CONSTRAINT `SPRING_SESSION_ATTRIBUTES_FK` FOREIGN KEY (`SESSION_PRIMARY_ID`) REFERENCES `spring_session` (`PRIMARY_ID`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

-- t_drive.shedlock definition

CREATE TABLE `shedlock` (
  `name` varchar(64) NOT NULL,
  `lock_until` timestamp(3) NOT NULL,
  `locked_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `locked_by` varchar(255) NOT NULL,
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- t_drive.tb_access_allowed_country definition

CREATE TABLE `tb_access_allowed_country` (
  `country_seq` int NOT NULL AUTO_INCREMENT,
  `tenant_seq` int NOT NULL COMMENT '소속 테넌트',
  `country_iso_code` char(2) NOT NULL COMMENT '국가 ISO 코드',
  `register_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `register_user_seq` int NOT NULL,
  PRIMARY KEY (`country_seq`),
  UNIQUE KEY `uniq_tenant_country` (`tenant_seq`,`country_iso_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- t_drive.tb_access_allowed_ip definition

CREATE TABLE `tb_access_allowed_ip` (
  `ip_seq` int NOT NULL AUTO_INCREMENT,
  `tenant_seq` int NOT NULL COMMENT '소속 테넌트',
  `role_type` varchar(20) NOT NULL COMMENT '권한 타입',
  `ip_address` varchar(15) NOT NULL COMMENT 'IPv4 주소 또는 네트워크 주소',
  `cidr_prefix` int NOT NULL DEFAULT '32' COMMENT 'CIDR 프리픽스 길이',
  `register_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록 일시',
  `register_user_seq` int NOT NULL COMMENT '사용자 seq',
  PRIMARY KEY (`ip_seq`),
  UNIQUE KEY `uniq_tenant_role_type_ip` (`tenant_seq`,`role_type`,`ip_address`,`cidr_prefix`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- t_drive.tb_api_key definition

CREATE TABLE `tb_api_key` (
  `api_key_seq` int NOT NULL AUTO_INCREMENT COMMENT 'API Key seq',
  `tenant_seq` int DEFAULT NULL COMMENT '소속 테넌트. 슈퍼관리자 키는 NULL',
  `key_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Key 이름',
  `key_purpose` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '사용 용도',
  `api_key` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'API Key 값',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ACTIVE' COMMENT 'Key 상태 (ACTIVE, INACTIVE)',
  `last_used_date` datetime DEFAULT NULL COMMENT '마지막 사용일',
  `register_user_seq` int NOT NULL COMMENT '등록자 seq',
  `register_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일',
  `update_user_seq` int DEFAULT NULL COMMENT '수정자 seq',
  `update_date` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
  `user_seq` int NOT NULL COMMENT '키 대상 사용자 seq (tb_user)',
  `role_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'USER' COMMENT '키 권한 타입 (USER, ADMIN, SUPER_ADMIN)',
  `scope` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '키에 부여된 scope. 공백 구분. 모든 role 의 키가 값을 가짐',
  `expire_date` datetime NOT NULL COMMENT '만료일',
  PRIMARY KEY (`api_key_seq`),
  UNIQUE KEY `uniq_api_key` (`api_key`),
  KEY `idx_api_key_status` (`status`),
  KEY `idx_api_key_user_seq` (`user_seq`),
  KEY `idx_api_key_register_user` (`register_user_seq`,`api_key_seq`),
  KEY `idx_api_key_tenant` (`tenant_seq`,`api_key_seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='API Key 테이블';


-- t_drive.tb_base_domain definition

CREATE TABLE `tb_base_domain` (
  `base_domain_seq` int NOT NULL,
  `base_domain` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `register_user_seq` int NOT NULL,
  `register_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_user_seq` int DEFAULT NULL,
  `update_date` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`base_domain_seq`),
  UNIQUE KEY `uniq_base_domain` (`base_domain`),
  CONSTRAINT `chk_base_domain_lowercase` CHECK ((`base_domain` = lower(`base_domain`))),
  CONSTRAINT `chk_base_domain_singleton` CHECK ((`base_domain_seq` = 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;


-- t_drive.tb_blacklist_token definition

CREATE TABLE `tb_blacklist_token` (
  `blacklist_token_seq` bigint NOT NULL AUTO_INCREMENT,
  `user_seq` int NOT NULL,
  `token_hash` char(64) NOT NULL,
  `issued_date` datetime NOT NULL,
  `expiration_date` datetime NOT NULL,
  `register_date` datetime NOT NULL,
  PRIMARY KEY (`blacklist_token_seq`),
  UNIQUE KEY `uniq_token_hash` (`token_hash`),
  KEY `idx_expiration_date` (`expiration_date`),
  KEY `idx_user_seq` (`user_seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- t_drive.tb_branding definition

CREATE TABLE `tb_branding` (
  `branding_seq` int NOT NULL AUTO_INCREMENT,
  `tenant_seq` int NOT NULL COMMENT '소속 테넌트',
  `tab_title` varchar(30) NOT NULL COMMENT '웹 브라우저 타이틀',
  `service_name` varchar(30) NOT NULL COMMENT '서비스 명',
  `show_powered_by` char(1) NOT NULL DEFAULT 'Y' COMMENT 'Powered By 표시 여부',
  `use_default` char(1) NOT NULL DEFAULT 'Y' COMMENT '로고 기본값 사용 여부',
  `favicon_data` mediumblob COMMENT 'favicon blob',
  `favicon_content_type` varchar(100) DEFAULT NULL COMMENT 'favicon content_type',
  `logo_icon_data` mediumblob COMMENT 'logo icon blob',
  `logo_icon_content_type` varchar(100) DEFAULT NULL COMMENT 'logo icon content_type',
  `logo_image_data` mediumblob COMMENT 'logo image blob',
  `logo_image_content_type` varchar(100) DEFAULT NULL COMMENT 'logo image content_type',
  `email_logo_data` mediumblob COMMENT 'email logo blob',
  `email_logo_content_type` varchar(100) DEFAULT NULL COMMENT 'email logo content_type',
  `register_user_seq` int NOT NULL COMMENT '등록자 seq',
  `register_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록시간',
  `update_user_seq` int DEFAULT NULL COMMENT '수정자 seq',
  `update_date` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '수정시간',
  PRIMARY KEY (`branding_seq`),
  UNIQUE KEY `uniq_tenant` (`tenant_seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- t_drive.tb_email_template definition

CREATE TABLE `tb_email_template` (
  `email_template_seq` int NOT NULL AUTO_INCREMENT COMMENT '이메일 템플릿 seq',
  `template_key` varchar(50) NOT NULL COMMENT '이메일 템플릿 템플릿 키',
  `locale_tag` varchar(7) NOT NULL COMMENT 'BCP-47 언어-지역 태그 (예: ko-KR, en-US)',
  `subject_template` text NOT NULL COMMENT '제목',
  `body_template` text NOT NULL COMMENT '본문',
  `register_user_seq` int NOT NULL COMMENT '등록자 seq',
  `register_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록시간',
  `update_user_seq` int DEFAULT NULL COMMENT '수정자 seq',
  `update_date` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '수정시간',
  PRIMARY KEY (`email_template_seq`),
  UNIQUE KEY `uniq_email_template_key_locale_language` (`template_key`,`locale_tag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- t_drive.tb_license definition

CREATE TABLE `tb_license` (
  `license_seq` int NOT NULL AUTO_INCREMENT,
  `tenant_seq` int NOT NULL COMMENT '소속 테넌트',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '값을 license로 고정하여 사용',
  `data` longblob NOT NULL COMMENT '라이선스 바이너리',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '생성시간',
  `updated_at` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '수정시간',
  PRIMARY KEY (`license_seq`),
  UNIQUE KEY `uniq_tenant` (`tenant_seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='라이선스 테이블';

-- t_drive.tb_mcp_oauth_jwk definition

CREATE TABLE `tb_mcp_oauth_jwk` (
  `kid` varchar(64) NOT NULL COMMENT 'JWK kid (UUID, jwk_json 내 kid와 동일)',
  `jwk_json` text NOT NULL COMMENT '개인키 포함 JWK JSON (RSAKey.toJSONString)',
  `register_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
  `expiration_date` datetime DEFAULT NULL COMMENT 'NULL=현역(서명+검증). rotate 시 now+유예(access TTL 1h에 여유를 둔 24h~) → 그때까지 검증만',
  PRIMARY KEY (`kid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MCP OAuth RS256 서명 키 (McpOAuthKeyManager)';


-- t_drive.tb_notification definition

CREATE TABLE `tb_notification` (
  `notification_seq` bigint NOT NULL AUTO_INCREMENT COMMENT '알림 seq',
  `user_seq` int NOT NULL COMMENT '유저 seq',
  `notification_event_seq` bigint NOT NULL COMMENT '알림 이벤트 seq',
  `is_read` char(1) NOT NULL COMMENT '읽음 여부',
  `read_datetime` datetime DEFAULT NULL COMMENT '읽음 시간',
  `register_user_seq` int NOT NULL COMMENT '등록자 seq',
  `register_date` datetime NOT NULL COMMENT '등록시간',
  `update_user_seq` int DEFAULT NULL COMMENT '수정자 seq',
  `update_date` datetime DEFAULT NULL COMMENT '수정시간',
  PRIMARY KEY (`notification_seq`,`user_seq`),
  UNIQUE KEY `idx_notification` (`user_seq`,`notification_event_seq`),
  KEY `idx_notification_time_sort` (`user_seq`,`register_date` DESC),
  KEY `idx_notification_event_read_time_sort` (`user_seq`,`is_read`,`register_date` DESC),
  KEY `idx_notification_event_sort` (`user_seq`,`notification_event_seq`,`register_date` DESC),
  KEY `idx_notification_cursor_sort` (`user_seq`,`notification_seq` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
/*!50100 PARTITION BY HASH (`user_seq`)
PARTITIONS 2 */;


-- t_drive.tb_notification_event definition

CREATE TABLE `tb_notification_event` (
  `notification_event_seq` bigint NOT NULL AUTO_INCREMENT COMMENT '알림 이벤트 seq',
  `resource_seq` bigint DEFAULT NULL COMMENT '파일/폴더 seq',
  `shared_by_user_seq` int DEFAULT NULL COMMENT '공유 유저 seq',
  `action_type` varchar(20) NOT NULL COMMENT '알람 유형',
  `description` json NOT NULL COMMENT '내용',
  `actor_user_seq` int NOT NULL COMMENT '이벤트 발생 유저 seq',
  `occurred_datetime` datetime NOT NULL COMMENT '이벤트 발생 시각',
  `register_user_seq` int NOT NULL COMMENT '등록자 seq',
  `register_date` datetime NOT NULL COMMENT '등록시간',
  PRIMARY KEY (`notification_event_seq`),
  KEY `idx_notification_event_action_type` (`action_type`),
  KEY `idx_notification_share_resource` (`shared_by_user_seq`,`resource_seq`),
  KEY `idx_notification_event_sort` (`occurred_datetime` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- t_drive.tb_oauth2_client_connection definition

CREATE TABLE `tb_oauth2_client_connection` (
  `connection_seq` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_seq` int NOT NULL,
  `client_id` varchar(100) NOT NULL COMMENT 'oauth2_registered_client.client_id (공개 식별자 - JWT aud 클레임과 매칭)',
  `scopes` varchar(1000) NOT NULL COMMENT '최근 승인 scope 스냅샷 (공백 구분)',
  `register_date` datetime NOT NULL COMMENT '최초 연결(첫 토큰 발급)',
  `token_issued_date` datetime NOT NULL COMMENT '마지막 토큰 발급/갱신(refresh rotation 포함)',
  `last_used_date` datetime DEFAULT NULL COMMENT '마지막 /mcp 요청 (5분 스로틀)',
  PRIMARY KEY (`connection_seq`),
  UNIQUE KEY `uk_oauth2_client_connection_user_client` (`user_seq`,`client_id`)
) ENGINE=InnoDB AUTO_INCREMENT=176 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='OAuth2 클라이언트별 사용자 연결 상태';

-- t_drive.tb_oauth2_jwk definition

CREATE TABLE `tb_oauth2_jwk` (
  `kid` varchar(64) NOT NULL COMMENT 'JWK kid (UUID, jwk_json 내 kid와 동일)',
  `jwk_json` text NOT NULL COMMENT '개인키 포함 JWK JSON (RSAKey.toJSONString)',
  `register_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
  `expiration_date` datetime DEFAULT NULL COMMENT 'NULL=현역(서명+검증). rotate 시 now+유예(access TTL 1h에 여유를 둔 24h~) → 그때까지 검증만',
  PRIMARY KEY (`kid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='OAuth RS256 서명 키';

-- t_drive.tb_password_reset_token definition

CREATE TABLE `tb_password_reset_token` (
  `password_reset_token_seq` int NOT NULL AUTO_INCREMENT,
  `user_seq` int NOT NULL,
  `token_hash` char(64) NOT NULL,
  `issued_date` datetime NOT NULL,
  `expiration_date` datetime NOT NULL,
  `register_date` datetime NOT NULL,
  PRIMARY KEY (`password_reset_token_seq`),
  UNIQUE KEY `token_hash` (`token_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- t_drive.tb_pinned_folder definition

CREATE TABLE `tb_pinned_folder` (
  `pinned_folder_seq` bigint NOT NULL AUTO_INCREMENT COMMENT '고정 폴더 seq',
  `user_seq` int NOT NULL COMMENT '유저 seq',
  `resource_seq` bigint NOT NULL COMMENT '파일/폴더 seq',
  `shared_by_user_seq` int DEFAULT NULL COMMENT '공유 유저 seq',
  `register_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록시간',
  PRIMARY KEY (`pinned_folder_seq`,`user_seq`),
  UNIQUE KEY `uniq_pinned_folder` (`user_seq`,`resource_seq`,`shared_by_user_seq`),
  KEY `idx_pinned_folder_sort` (`user_seq`,`register_date`,`pinned_folder_seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='고정 폴더 테이블'
/*!50100 PARTITION BY HASH ((`user_seq` % 2))
PARTITIONS 2 */;

-- t_drive.tb_recent definition

CREATE TABLE `tb_recent` (
  `recent_seq` bigint NOT NULL AUTO_INCREMENT COMMENT '최근 문서함 seq',
  `user_seq` int NOT NULL COMMENT '유저 seq',
  `resource_seq` bigint NOT NULL COMMENT '파일/폴더 seq',
  `shared_by_user_seq` int DEFAULT NULL COMMENT '공유 유저 seq',
  `register_user_seq` int NOT NULL COMMENT '등록자 seq',
  `register_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록시간',
  `update_date` datetime DEFAULT NULL COMMENT '최근 열어본 일시',
  PRIMARY KEY (`user_seq`,`recent_seq`),
  UNIQUE KEY `uniq_recent` (`user_seq`,`resource_seq`,`shared_by_user_seq`),
  KEY `idx_recent_recent_seq` (`recent_seq`),
  KEY `idx_recent_resource_seq` (`resource_seq`),
  KEY `idx_recent_update_date` (`update_date` DESC),
  KEY `idx_recent_shared_by_user_seq` (`shared_by_user_seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='중요문서함 테이블'
/*!50100 PARTITION BY HASH ((`user_seq` % 2))
PARTITIONS 2 */;

-- t_drive.tb_refresh_token definition

CREATE TABLE `tb_refresh_token` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_seq` int NOT NULL,
  `session_id` varchar(36) NOT NULL,
  `refresh_token` varchar(512) NOT NULL,
  `expiration_date` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refresh_token_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- t_drive.tb_resource definition

CREATE TABLE `tb_resource` (
  `resource_seq` bigint NOT NULL AUTO_INCREMENT COMMENT '파일/폴더 seq',
  `user_seq` int NOT NULL COMMENT '유저 seq',
  `owner_seq` int DEFAULT NULL,
  `latest_owner_seq` int DEFAULT NULL,
  `resource_type` varchar(6) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '타입 : file, folder',
  `parent_folder_seq` bigint DEFAULT NULL COMMENT '상위 폴더 seq',
  `delete_parent_folder_seq` bigint DEFAULT NULL COMMENT '삭제가 시작된 폴더의 seq',
  `resource_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '폴더/파일명',
  `resource_name_numeric_part` int unsigned GENERATED ALWAYS AS (cast(regexp_substr(`resource_name`,_utf8mb4'^[0-9]{1,9}') as unsigned)) VIRTUAL COMMENT '선행 숫자 1~9자리 추출, 없으면 NULL',
  `resource_name_text_part` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci GENERATED ALWAYS AS (regexp_replace(`resource_name`,_utf8mb4'^[0-9]{0,9}',_utf8mb4'')) VIRTUAL COMMENT '선행 숫자 0~9자리 제거, 나머지 문자열',
  `original_file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '(원본 이름)',
  `resource_save_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '저장소 파일명 (*파일일 경우 - user_seq / resource_seq)',
  `full_path_seq` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '전체 경로 seq(*폴더일 경우)',
  `full_path` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
  `current_version_number` int NOT NULL DEFAULT '1' COMMENT '(버전 번호)',
  `version_register_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '현재 버전의 생성 시간',
  `copy_number` int NOT NULL DEFAULT '0' COMMENT '(원본 이름 중복 번호)',
  `is_delete` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'N' COMMENT '삭제여부',
  `is_locked` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'N' COMMENT '파일 잠금',
  `locked_date` datetime DEFAULT NULL COMMENT '잠금시간',
  `ext` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '확장자 (*파일일 경우)',
  `content_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'content 타입 (*파일일 경우)',
  `size_byte` bigint DEFAULT NULL COMMENT '크기 (*파일일 경우, 바이트)',
  `share_token` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '공유 링크 식별용 Base62 토큰(16자)',
  `register_user_seq` int NOT NULL COMMENT '등록자 seq',
  `register_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록시간',
  `update_user_seq` int DEFAULT NULL COMMENT '수정자 seq',
  `update_date` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '수정시간',
  PRIMARY KEY (`user_seq`,`resource_seq`),
  KEY `idx_resource_resource_seq` (`resource_seq`),
  KEY `idx_resource_resource_name` (`resource_name`),
  KEY `idx_resource_original_file_name` (`original_file_name`),
  KEY `idx_resource_resource_save_name` (`resource_save_name`),
  KEY `idx_resource_user_seq_is_delete` (`user_seq`,`is_delete`),
  KEY `idx_resource_user_seq_parent_folder_seq` (`user_seq`,`parent_folder_seq`),
  KEY `idx_resource_full_path_seq` (`full_path_seq`),
  KEY `idx_resource_delete_parent_folder_seq` (`delete_parent_folder_seq`),
  KEY `idx_resource_resource_type` (`resource_type`),
  KEY `idx_resource_sort_size_byte` (`resource_type` DESC,`size_byte`),
  KEY `idx_resource_sort_size_ext` (`resource_type` DESC,`ext`),
  KEY `idx_resource_sort_size_update_date` (`resource_type` DESC,`update_date`),
  KEY `idx_resource_owner_seq` (`owner_seq`),
  KEY `idx_resource_is_locked_date` (`is_locked`,`locked_date`),
  KEY `idx_resource_share_token` (`share_token`),
  KEY `idx_resource_sort_resource_name` (`resource_type` DESC,`resource_name_numeric_part`,`resource_name_text_part`,`resource_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='파일/폴더 테이블'
/*!50100 PARTITION BY HASH ((`user_seq` % 2))
PARTITIONS 2 */;


-- t_drive.tb_resource_tag definition

CREATE TABLE `tb_resource_tag` (
  `resource_tag_seq` bigint NOT NULL AUTO_INCREMENT COMMENT '태그 seq',
  `user_seq` int NOT NULL COMMENT '유저 seq',
  `resource_seq` bigint NOT NULL COMMENT '파일/폴더 seq',
  `resource_tag_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '태그명',
  `register_user_seq` int NOT NULL COMMENT '등록자 seq',
  `register_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록시간',
  PRIMARY KEY (`user_seq`,`resource_tag_seq`),
  KEY `idx_resourcE_tag_seq` (`resource_tag_seq`),
  KEY `idx_resource_tag_resource_seq` (`resource_seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='리소스 태그 테이블'
/*!50100 PARTITION BY HASH ((`user_seq` % 2))
PARTITIONS 2 */;


-- t_drive.tb_resource_version definition

CREATE TABLE `tb_resource_version` (
  `resource_version_seq` bigint NOT NULL AUTO_INCREMENT COMMENT '리소스버전 seq',
  `user_seq` int NOT NULL COMMENT '유저 seq',
  `resource_seq` bigint NOT NULL COMMENT '리소스 seq',
  `owner_seq` int DEFAULT NULL,
  `resource_save_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '저장소 파일명 ( user_seq / resource_seq / version_number)',
  `version_number` int NOT NULL DEFAULT '1' COMMENT '(버전 번호)',
  `size_byte` bigint NOT NULL COMMENT '크기 (*바이트)',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PENDING' COMMENT '상태 (PENDING, DELETE, SUCCESS)',
  `version_register_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '버전 등록시간',
  `register_user_seq` int NOT NULL COMMENT '등록자 seq',
  `register_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록시간',
  `update_user_seq` int DEFAULT NULL COMMENT '수정자 seq',
  `update_date` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '수정시간',
  PRIMARY KEY (`user_seq`,`resource_version_seq`),
  UNIQUE KEY `uniq_resource_version` (`user_seq`,`resource_seq`,`version_number`,`status`),
  KEY `idx_resource_version_seq` (`resource_version_seq`),
  KEY `idx_resource_version_user_seq` (`user_seq`),
  KEY `idx_resource_version_register_date` (`register_date` DESC),
  KEY `idx_tb_resource_version_status` (`status`),
  KEY `idx_resource_version_owner_seq` (`owner_seq`),
  KEY `idx_resource_version_user_resource` (`user_seq`,`resource_seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='리소스 버전 테이블'
/*!50100 PARTITION BY HASH ((`user_seq` % 2))
PARTITIONS 2 */;


-- t_drive.tb_share definition

CREATE TABLE `tb_share` (
  `share_seq` bigint NOT NULL AUTO_INCREMENT COMMENT '공유 seq',
  `user_seq` int NOT NULL COMMENT '유저 seq',
  `resource_seq` bigint NOT NULL COMMENT '폴더/파일 seq',
  `share_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '공유 유형',
  `can_view` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '보기 권한 (Y/N)',
  `can_edit` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '보기 권한 (Y/N)',
  `can_download` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '보기 권한 (Y/N)',
  `can_upload` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '보기 권한 (Y/N)',
  `can_delete` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '보기 권한 (Y/N)',
  `can_share` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '보기 권한 (Y/N)',
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '비밀번호',
  `is_password_enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'N' COMMENT '비밀번호 입력여부',
  `expire_date` datetime DEFAULT NULL COMMENT '만료날짜',
  `register_user_seq` int NOT NULL COMMENT '등록자 seq',
  `register_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록시간',
  `update_user_seq` int DEFAULT NULL COMMENT '수정자 seq',
  `update_date` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '수정시간',
  PRIMARY KEY (`user_seq`,`share_seq`),
  UNIQUE KEY `uniq_share` (`user_seq`,`resource_seq`),
  KEY `idx_share_share_seq` (`share_seq`),
  KEY `idx_share_resource_seq` (`resource_seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='공유 테이블'
/*!50100 PARTITION BY HASH ((`user_seq` % 2))
PARTITIONS 2 */;


-- t_drive.tb_share_setting definition

CREATE TABLE `tb_share_setting` (
  `share_setting_seq` int NOT NULL AUTO_INCREMENT,
  `tenant_seq` int DEFAULT NULL COMMENT 'NULL=슈퍼어드민(글로벌) 설정, 그 외=테넌트별 설정',
  `is_enforced` char(1) NOT NULL DEFAULT 'N' COMMENT '적용범위(글로벌 row 전용): Y=전체적용 / N=개별적용. 테넌트 row는 항상 N',
  `is_external_share_allowed` char(1) NOT NULL DEFAULT 'N' COMMENT '외부 공유 허용 여부 (Y/N)',
  `is_specific_users_allowed` char(1) NOT NULL DEFAULT 'Y' COMMENT '접근범위-제한됨(직접 추가한 사용자만) 허용 여부 (Y/N)',
  `is_org_link_allowed` char(1) NOT NULL DEFAULT 'Y' COMMENT '접근범위-조직 내부 공개 허용 여부 (Y/N)',
  `is_public_allowed` char(1) NOT NULL DEFAULT 'N' COMMENT '접근범위-전체 공개 허용 여부 (Y/N)',
  `register_user_seq` int NOT NULL COMMENT '등록자 user_seq',
  `register_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
  `update_user_seq` int DEFAULT NULL COMMENT '수정자 user_seq',
  `update_date` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
  PRIMARY KEY (`share_setting_seq`),
  UNIQUE KEY `tb_share_setting_uk_tenant` (`tenant_seq`),
  UNIQUE KEY `tb_share_setting_uk_global` (((case when (`tenant_seq` is null) then 1 else NULL end))),
  CONSTRAINT `tb_share_setting_chk_enforce` CHECK (((`tenant_seq` is null) or (`is_enforced` = _utf8mb4'N')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='공유 설정 (슈퍼어드민 글로벌 1행 + 테넌트별 1행)';


-- t_drive.tb_share_target definition

CREATE TABLE `tb_share_target` (
  `share_target_seq` bigint NOT NULL AUTO_INCREMENT COMMENT '공유 대상 seq',
  `user_seq` int NOT NULL COMMENT '공유받은유저 seq',
  `share_seq` bigint NOT NULL COMMENT '공유 seq',
  `resource_seq` bigint NOT NULL COMMENT '폴더/파일 seq',
  `shared_by_user_seq` int DEFAULT NULL COMMENT '공유 유저 seq',
  `share_date` datetime NOT NULL COMMENT '공유시간',
  `can_view` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'N' COMMENT '보기 권한 (Y/N)',
  `can_edit` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'N' COMMENT '편집 권한 (Y/N)',
  `can_download` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'N' COMMENT '다운로드 권한 (Y/N)',
  `can_upload` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'N' COMMENT '업로드 권한 (Y/N)',
  `can_delete` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'N' COMMENT '삭제 권한 (Y/N)',
  `can_share` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'N' COMMENT '공유 권한 (Y/N)',
  `register_user_seq` int NOT NULL COMMENT '등록자 seq',
  `register_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록시간',
  `update_user_seq` int DEFAULT NULL COMMENT '수정자 seq',
  `update_date` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '수정시간',
  PRIMARY KEY (`user_seq`,`share_target_seq`),
  UNIQUE KEY `uniq_share_target` (`user_seq`,`resource_seq`,`shared_by_user_seq`),
  KEY `idx_share_target_seq` (`share_target_seq`),
  KEY `idx_share_target_resource_seq` (`resource_seq`),
  KEY `idx_share_target_share_seq` (`share_seq`),
  KEY `idx_share_target_shared_by_user_seq_resource_seq` (`shared_by_user_seq`,`resource_seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='공유대상 테이블'
/*!50100 PARTITION BY HASH ((`user_seq` % 2))
PARTITIONS 2 */;


-- t_drive.tb_smtp_config definition

CREATE TABLE `tb_smtp_config` (
  `smtp_config_seq` int NOT NULL AUTO_INCREMENT COMMENT 'SMTP 설정 seq',
  `tenant_seq` int NOT NULL COMMENT '소속 테넌트',
  `client_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '고객사명',
  `host` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'SMTP 서버 주소',
  `port` int NOT NULL COMMENT 'SMTP 포트 번호',
  `encryption_type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '보안 방식 (SSL, TLS, NONE)',
  `auth_type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '인증 방식 (IDPW, OAuth)',
  `username` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'SMTP 사용자 계정',
  `enc_pwd` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'SMTP 비밀번호 (암호화 저장)',
  `oauth_info` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT 'OAuth 인증 정보 (선택)',
  `from_address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '발신자 이메일',
  `test_receiver_email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '테스트용 수신자 이메일',
  `register_user_seq` int NOT NULL COMMENT '생성자 seq',
  `register_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일자',
  `update_user_seq` int DEFAULT NULL COMMENT '수정자 seq',
  `update_date` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일자',
  PRIMARY KEY (`smtp_config_seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;


-- t_drive.tb_sso_jwt_config definition

CREATE TABLE `tb_sso_jwt_config` (
  `sso_provider_seq` int NOT NULL COMMENT 'PK이자 tb_sso_provider FK (1:1)',
  `issuer` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'issuer 주소',
  `audience` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '원하면 설정, NULL이면 aud 검증 skip',
  `key_source_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '검증 키 방식 - PUBLIC_KEY, JWKS',
  `public_key` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT 'RS256 Public Key',
  `jwks_uri` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'RS256 + JWKS URI일 때',
  `email_claim_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'email 대신 사용할 claim',
  `error_redirect_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'JWT 오류시 이동할 URL',
  `register_user_seq` int NOT NULL COMMENT '등록자 seq',
  `register_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록시간',
  `update_user_seq` int DEFAULT NULL COMMENT '수정자 seq',
  `update_date` datetime DEFAULT NULL COMMENT '최종 수정일시',
  PRIMARY KEY (`sso_provider_seq`),
  UNIQUE KEY `uniq_sso_jwt_issuer` (`issuer`),
  CONSTRAINT `check_key_source_type` CHECK ((((`key_source_type` = _utf8mb4'PUBLIC_KEY') and (`public_key` is not null) and (`jwks_uri` is null)) or ((`key_source_type` = _utf8mb4'JWKS') and (`jwks_uri` is not null) and (`public_key` is null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='jwt 설정 테이블';


-- t_drive.tb_sso_oidc_config definition

CREATE TABLE `tb_sso_oidc_config` (
  `sso_provider_seq` int NOT NULL COMMENT 'PK이자 tb_sso_provider FK (1:1)',
  `issuer_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'IdP 주소. {issuer_url}/.well-known/openid-configuration 으로 endpoint 자동 Discovery. ex) https://accounts.google.com',
  `client_id` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'IdP에서 발급한 앱 식별자. 공개값',
  `client_secret` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'IdP에서 발급한 앱 시크릿. 앱 레벨 AES 암호화 후 저장 필수',
  `scopes` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'OIDC scope override. null이면 서버 기본값 사용',
  `allow_idp_initiated` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'N' COMMENT 'IdP 포털에서 앱 클릭으로 바로 진입 허용 여부. N=SP-Initiated만 허용(권장), Y=IdP-Initiated도 허용',
  `register_user_seq` int NOT NULL COMMENT '등록자 user_seq',
  `register_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
  `update_user_seq` int DEFAULT NULL COMMENT '최종 수정자 user_seq',
  `update_date` datetime DEFAULT NULL COMMENT '최종 수정일시',
  PRIMARY KEY (`sso_provider_seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OIDC 전용 설정. tb_sso_provider.protocol_type=OIDC 일 때 JOIN';


-- t_drive.tb_sso_provider definition

CREATE TABLE `tb_sso_provider` (
  `sso_provider_seq` int NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `tenant_seq` int DEFAULT NULL,
  `display_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '로그인 버튼 텍스트.',
  `icon_image_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'Ceph/S3 스토리지 키. profile_image_key와 동일 패턴',
  `protocol_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'SSO 프로토콜 종류. OIDC | SAML | JWT',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '상태',
  `scope` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'TENANT' COMMENT 'TENANT | GLOBAL',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '로그인 화면 버튼 노출 순서. 낮을수록 위',
  `register_user_seq` int NOT NULL COMMENT '등록자 user_seq',
  `register_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
  `update_user_seq` int DEFAULT NULL COMMENT '최종 수정자 user_seq',
  `update_date` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '최종 수정일시',
  PRIMARY KEY (`sso_provider_seq`),
  UNIQUE KEY `uniq_sso_provider_scope_tenant_name` (`scope`,(coalesce(`tenant_seq`,0)),`display_name`),
  CONSTRAINT `check_scope_tenant` CHECK ((((`scope` = _utf8mb4'TENANT') and (`tenant_seq` is not null)) or ((`scope` = _utf8mb4'GLOBAL') and (`tenant_seq` is null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='SSO Provider 공통 메타. protocol_type으로 하위 config 테이블 분기';


-- t_drive.tb_starred definition

CREATE TABLE `tb_starred` (
  `starred_seq` bigint NOT NULL AUTO_INCREMENT COMMENT '중요 문서함 seq',
  `user_seq` int NOT NULL COMMENT '유저 seq',
  `resource_seq` bigint NOT NULL COMMENT '파일/폴더 seq',
  `shared_by_user_seq` int DEFAULT NULL,
  `register_user_seq` int NOT NULL COMMENT '등록자 seq',
  `register_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록시간',
  PRIMARY KEY (`user_seq`,`starred_seq`),
  UNIQUE KEY `uniq_starred` (`user_seq`,`resource_seq`,`shared_by_user_seq`),
  KEY `idx_starred_starred_seq` (`starred_seq`),
  KEY `idx_starred_resource_seq` (`resource_seq`),
  KEY `idx_starred_user_seq` (`user_seq`),
  KEY `idx_starred_register_date` (`register_date` DESC),
  KEY `idx_starred_shared_by_user_seq` (`shared_by_user_seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='중요문서함 테이블'
/*!50100 PARTITION BY HASH ((`user_seq` % 2))
PARTITIONS 2 */;



-- t_drive.tb_super_admin_login_setting definition

CREATE TABLE `tb_super_admin_login_setting` (
  `super_admin_login_setting_seq` int NOT NULL DEFAULT '1' COMMENT 'PK이자 단일행 보장 (항상 1)',
  `login_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `is_enforced` char(1) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'N' COMMENT 'Y=모든 테넌트에 전체 적용',
  `register_user_seq` int NOT NULL COMMENT '최초 등록자 user_seq',
  `register_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_user_seq` int DEFAULT NULL,
  `update_date` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`super_admin_login_setting_seq`),
  CONSTRAINT `check_super_admin_login_setting_singleton` CHECK ((`super_admin_login_setting_seq` = 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='슈퍼어드민 로그인 설정 (단일행, PK=1 고정)';


-- t_drive.tb_tenant definition

CREATE TABLE `tb_tenant` (
  `tenant_seq` int NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `tenant_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '내부 관리명. 어드민 화면 식별용',
  `tenant_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ORGANIZATION' COMMENT '테넌트 유형',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '테넌트 상태. ACTIVE | INACTIVE',
  `login_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'LOCAL' COMMENT '로그인 유형 (LOCAL, OIDC, JWT)',
  `domain` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '풀 도메인 (예: drive.example.com)',
  `subdomain` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `base_domain_seq` int DEFAULT NULL,
  `register_user_seq` int NOT NULL COMMENT '등록자 user_seq',
  `register_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
  `update_user_seq` int DEFAULT NULL COMMENT '최종 수정자 user_seq',
  `update_date` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '최종 수정일시',
  PRIMARY KEY (`tenant_seq`),
  UNIQUE KEY `tenant_name` (`tenant_name`),
  UNIQUE KEY `uniq_tenant_domain` (`domain`),
  KEY `idx_tenant_base_domain` (`base_domain_seq`),
  CONSTRAINT `chk_tenant_domain_lowercase` CHECK ((`domain` = lower(`domain`)))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='테넌트 기본 정보';


-- t_drive.tb_tenant_access_token definition

CREATE TABLE `tb_tenant_access_token` (
  `tenant_access_token_seq` int NOT NULL AUTO_INCREMENT,
  `super_admin_user_seq` int NOT NULL,
  `tenant_seq` int NOT NULL,
  `token_hash` char(64) NOT NULL,
  `issued_date` datetime NOT NULL,
  `expiration_date` datetime NOT NULL,
  `register_date` datetime NOT NULL,
  PRIMARY KEY (`tenant_access_token_seq`),
  UNIQUE KEY `uk_tenant_access_token_hash` (`token_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- t_drive.tb_trash definition

CREATE TABLE `tb_trash` (
  `trash_seq` bigint NOT NULL AUTO_INCREMENT COMMENT '휴지통 seq',
  `user_seq` int NOT NULL COMMENT '유저 seq',
  `resource_seq` bigint NOT NULL COMMENT '파일/폴더 seq',
  `register_user_seq` int NOT NULL COMMENT '등록자 seq',
  `register_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록시간',
  PRIMARY KEY (`user_seq`,`trash_seq`),
  UNIQUE KEY `uniq_trash` (`user_seq`,`resource_seq`),
  KEY `idx_trash_trash_seq` (`trash_seq`),
  KEY `idx_trash_resource_seq` (`resource_seq`),
  KEY `idx_trash_register_date` (`register_date` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='휴지통 테이블'
/*!50100 PARTITION BY HASH ((`user_seq` % 2))
PARTITIONS 2 */;


-- t_drive.tb_user definition

CREATE TABLE `tb_user` (
  `user_seq` int NOT NULL AUTO_INCREMENT COMMENT '유저 seq',
  `tenant_seq` int DEFAULT NULL COMMENT '소속 테넌트',
  `user_id` varchar(254) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `enc_pwd` varchar(70) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `signup_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'LOCAL' COMMENT '인증 방식. LOCAL=Local 가입, SSO_OIDC=oidc 가입, SSO_SAML=smal 가입. sso는(enc_pwd NULL)',
  `is_active` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'Y' COMMENT '유저 활성화여부',
  `user_name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '유저 이름',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ACTIVE',
  `total_quota_byte` bigint DEFAULT '0',
  `use_quota_byte` bigint NOT NULL DEFAULT '0' COMMENT '현재 사용한 드라이브 용량',
  `role_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'USER' COMMENT '권한 타입',
  `mfa_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DISABLED' COMMENT 'MFA 상태(DISABLED, PENDING, ENABLED)',
  `mfa_method` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'MFA 방식 (TOTP, EMAIL_CODE)',
  `mfa_secret_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'MFA secret key',
  `mfa_setup_date` datetime DEFAULT NULL COMMENT 'MFA 등록 시작 시간',
  `mfa_confirm_date` datetime DEFAULT NULL COMMENT 'mfa 등록 완료 시간',
  `register_user_seq` int NOT NULL COMMENT '생성자 id',
  `register_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성시간',
  `update_user_seq` int DEFAULT NULL COMMENT '수정자',
  `update_date` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `login_failure_count` int NOT NULL DEFAULT '0' COMMENT '로그인 실패 횟수',
  `pending_delete_date` datetime DEFAULT NULL,
  `delete_schedule_time` datetime DEFAULT NULL,
  `delete_date` datetime DEFAULT NULL COMMENT '탈퇴 일자',
  `is_delete` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'N' COMMENT '탈퇴 처리 여부 (Y,N)',
  `country_code` varchar(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '국가 코드 (국제전화 국가번호)',
  `phone_number` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '전화번호',
  `user_language` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'auto',
  `profile_image_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '프로필 이미지 스토리지 키',
  `pinned_folder_list` json DEFAULT NULL,
  `default_landing_page` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'home' COMMENT '로그인 후 진입할 기본 페이지',
  PRIMARY KEY (`user_seq`),
  UNIQUE KEY `uniq_active_user_id` (((case when (`status` <> _utf8mb4'DELETED') then `user_id` end))),
  KEY `idx_user_user_name` (`user_name`),
  KEY `idx_user_pending_delete_sort` (`status`,`pending_delete_date` DESC,`user_seq` DESC),
  KEY `idx_user_delete_schedule_time` (`delete_schedule_time`),
  KEY `idx_user_status` (`status`),
  KEY `idx_user_tenant` (`tenant_seq`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;


-- t_drive.tb_user_quota_alert_state definition

CREATE TABLE `tb_user_quota_alert_state` (
  `user_seq` bigint NOT NULL COMMENT '사용자 seq',
  `last_threshold_percent` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '최근 알림 임계치 %(0, 80/90/100)',
  `register_date` datetime NOT NULL COMMENT '등록시간',
  `update_user_seq` int DEFAULT NULL COMMENT '수정자 seq',
  `update_date` datetime DEFAULT NULL COMMENT '수정시간',
  PRIMARY KEY (`user_seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
/*!50100 PARTITION BY HASH (`user_seq`)
PARTITIONS 2 */;


-- t_drive.tb_user_sso_identity definition

CREATE TABLE `tb_user_sso_identity` (
  `sso_identity_seq` bigint NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `user_seq` int NOT NULL COMMENT '우리 서비스 유저 (tb_user FK)',
  `sso_provider_seq` int NOT NULL COMMENT '연결된 SSO Provider (tb_sso_provider FK)',
  `subject` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'IdP 발급 사용자 고유 식별자. OIDC=sub claim, SAML=NameID. email과 달리 절대 변경되지 않음',
  `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'IdP에서 받은 email. 참조/표시용. 실제 식별은 subject 사용 (email은 변경 가능)',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '이름',
  `linked_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최초 SSO 연결 또는 계정 생성 시각',
  `last_callback_at` datetime DEFAULT NULL COMMENT '가입되어 있을 경우 마지막 callback 시각',
  PRIMARY KEY (`sso_identity_seq`),
  UNIQUE KEY `uniq_provider_subject` (`sso_provider_seq`,`subject`) COMMENT '같은 IdP의 같은 사람 중복 방지',
  UNIQUE KEY `uniq_user_provider` (`user_seq`,`sso_provider_seq`),
  KEY `idx_user_seq` (`user_seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='내부 User와 외부 IdP Identity 매핑. 프로토콜 무관 통합 관리';


-- t_drive.tb_system_config definition

CREATE TABLE `tb_system_config` (
  `config_seq` int NOT NULL AUTO_INCREMENT COMMENT '설정 seq',
  `tenant_seq` int DEFAULT NULL COMMENT '소속 테넌트 (NULL=슈퍼어드민/글로벌)',
  `setting_key` varchar(64) NOT NULL COMMENT '설정 키',
  `value_text` text NOT NULL COMMENT '설정 값',
  `register_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록 일시',
  `register_user_seq` int NOT NULL COMMENT '등록자 사용자 SEQ',
  `update_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
  `update_user_seq` int NOT NULL COMMENT '수정자 사용자 SEQ',
  PRIMARY KEY (`config_seq`),
  UNIQUE KEY `uk_tb_tenant_setting_key` ((coalesce(`tenant_seq`,0)),`setting_key`),
  KEY `idx_tb_syscfg_reg_user` (`register_user_seq`),
  KEY `idx_tb_syscfg_upd_user` (`update_user_seq`),
  CONSTRAINT `fk_tb_syscfg_reg_user` FOREIGN KEY (`register_user_seq`) REFERENCES `tb_user` (`user_seq`),
  CONSTRAINT `fk_tb_syscfg_upd_user` FOREIGN KEY (`update_user_seq`) REFERENCES `tb_user` (`user_seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='시스템 전역 설정(서버 IP/주소, 오피스 연동)';


-- t_drive.tb_user_log definition

CREATE TABLE `tb_user_log` (
  `user_log_seq` bigint NOT NULL AUTO_INCREMENT COMMENT '유저 로그 seq',
  `user_seq` int NOT NULL COMMENT '유저 seq',
  `region` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '접속 지역',
  `ip_address` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'IP 주소',
  `register_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '로그 생성 시각',
  `activity_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '활동 종류 (예: LOGIN, LOGOUT, CHANGE_PASSWORD)',
  `actor_type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'USER' COMMENT '로그 행위 주체 (USER | ADMIN | SYSTEM)',
  PRIMARY KEY (`user_log_seq`),
  KEY `idx_register_date` (`register_date` DESC),
  KEY `fk_user_log_user_seq` (`user_seq`),
  CONSTRAINT `fk_user_log_user_seq` FOREIGN KEY (`user_seq`) REFERENCES `tb_user` (`user_seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;


INSERT INTO t_drive.tb_tenant
(tenant_seq, tenant_name, tenant_type, status, login_type, `domain`, register_user_seq, register_date, update_user_seq, update_date)
VALUES(1, '기본 테넌트', 'ORGANIZATION', 'ACTIVE', 'LOCAL', null, 1, now(), null, null);

INSERT INTO t_drive.tb_user
(user_seq, tenant_seq, user_id, enc_pwd, signup_type, is_active, user_name, status, total_quota_byte, use_quota_byte, role_type, mfa_status, mfa_method, mfa_secret_key, mfa_setup_date, mfa_confirm_date, register_user_seq, register_date, update_user_seq, update_date, login_failure_count, pending_delete_date, delete_schedule_time, delete_date, is_delete, country_code, phone_number, user_language, profile_image_key, pinned_folder_list, default_landing_page)
VALUES(1, NULL, 'superadmin', '{bcrypt}$2a$10$kjByez33RcCAZqNjQrrVtOn.oroWcuh1AaQEr3F6KMKQNRc9oS8hS', 'LOCAL', 'Y', '슈퍼어드민', 'ACTIVE', 0, 0, 'SUPER_ADMIN', 'DISABLED', NULL, NULL, NULL, NULL, 1, now(), null, now(), 0, NULL, NULL, NULL, 'N', NULL, NULL, 'auto', null, NULL, 'home');

INSERT INTO t_drive.tb_user
(user_seq, tenant_seq, user_id, enc_pwd, signup_type, is_active, user_name, status, total_quota_byte, use_quota_byte, role_type, mfa_status, mfa_method, mfa_secret_key, mfa_setup_date, mfa_confirm_date, register_user_seq, register_date, update_user_seq, update_date, login_failure_count, pending_delete_date, delete_schedule_time, delete_date, is_delete, country_code, phone_number, user_language, profile_image_key, pinned_folder_list, default_landing_page)
VALUES(2, 1, 'admin', '{bcrypt}$2a$10$P89fI46sL7nNBtbUk2.hn.2P.qgSlHcVYMVA4L7B.b5y2jhshj8w6', 'LOCAL', 'Y', '관리자', 'ACTIVE', 0, 0, 'ADMIN', 'DISABLED', NULL, NULL, NULL, NULL, 1, now(), NULL, now(), 0, NULL, NULL, NULL, 'N', NULL, NULL, 'auto', NULL, NULL, 'home');

INSERT INTO t_drive.tb_user
(user_seq, tenant_seq, user_id, enc_pwd, signup_type, is_active, user_name, status, total_quota_byte, use_quota_byte, role_type, mfa_status, mfa_method, mfa_secret_key, mfa_setup_date, mfa_confirm_date, register_user_seq, register_date, update_user_seq, update_date, login_failure_count, pending_delete_date, delete_schedule_time, delete_date, is_delete, country_code, phone_number, user_language, profile_image_key, pinned_folder_list, default_landing_page)
VALUES(3, NULL, 'anonymous', NULL, 'LOCAL', 'N', '익명 사용자', 'ACTIVE', 0, 0, 'ANONYMOUS', 'DISABLED', NULL, NULL, NULL, NULL, 1, now(), NULL, now(), 0, NULL, NULL, NULL, 'N', NULL, NULL, 'auto', NULL, NULL, 'home');

INSERT INTO t_drive.tb_system_config
    (setting_key, tenant_seq, value_text, register_date, register_user_seq, update_date, update_user_seq)
VALUES
    ('office_domain', 1, '', NOW(), 1, NOW(), 1),
    ('office_adapter_name', 1, '', NOW(), 1, NOW(), 1),
    ('intellect_status', 1, 'true', NOW(), 1, NOW(), 1),
    ('mfa_status', 1, 'false', NOW(), 1, NOW(), 1),
    ('share_status', 1, 'true', NOW(), 1, NOW(), 1);

INSERT INTO t_drive.tb_share_setting
(tenant_seq, is_enforced, is_external_share_allowed, is_specific_users_allowed, is_org_link_allowed, is_public_allowed, register_user_seq, register_date, update_user_seq, update_date)
VALUES(1, 'N', 'N', 'Y', 'Y', 'N', 1, now(), 1, now());

INSERT INTO t_drive.tb_email_template
(template_key, locale_tag, subject_template, body_template, register_user_seq, register_date, update_user_seq, update_date)
VALUES('SHARE_FOLDER', 'ko-KR', '"${resourceName}" 폴더가 공유되었습니다', '${requestUserName}(${requestUserId})님이 아래 리소스를 공유했습니다.

공유된 항목: ${resourceName}', 1, '2025-07-18 17:34:25', NULL, '2025-07-18 18:23:55');
INSERT INTO t_drive.tb_email_template
(template_key, locale_tag, subject_template, body_template, register_user_seq, register_date, update_user_seq, update_date)
VALUES('SHARE_FILE', 'ko-KR', '"${resourceName}" 파일이 공유되었습니다', '${requestUserName}(${requestUserId})님이 아래 리소스를 공유했습니다.

공유된 항목: ${resourceName}', 1, '2025-07-18 17:35:11', NULL, '2025-07-21 13:46:25');
INSERT INTO t_drive.tb_email_template
(template_key, locale_tag, subject_template, body_template, register_user_seq, register_date, update_user_seq, update_date)
VALUES('SHARE_FOLDER', 'en-US', 'Folder shared with you: "${resourceName}"', '${requestUserName} (${requestUserId}) has shared the following item with you.

Shared item: ${resourceName}', 1, '2025-07-18 17:36:25', NULL, '2025-07-18 18:23:55');
INSERT INTO t_drive.tb_email_template
(template_key, locale_tag, subject_template, body_template, register_user_seq, register_date, update_user_seq, update_date)
VALUES('SHARE_FILE', 'en-US', 'File shared with you: "${resourceName}"', '${requestUserName} (${requestUserId}) has shared the following item with you.

Shared item: ${resourceName}', 1, '2025-07-18 17:36:46', NULL, '2025-07-21 14:01:31');
INSERT INTO t_drive.tb_email_template
(template_key, locale_tag, subject_template, body_template, register_user_seq, register_date, update_user_seq, update_date)
VALUES('ADMIN_USER_CSV_REGISTER', 'ko-KR', '[${serviceName}] 계정이 생성되었습니다.', '${baseUrl} 에서 계정을 생성했습니다.

아이디 : ${userId}
임시 비밀번호 : ${pw}

로그인 Url : ${loginUrl}
보안을 위해 최초 로그인 후 반드시 비밀번호를 변경해 주세요.', 1, '2025-10-13 17:00:40', NULL, '2026-04-07 08:37:25');
INSERT INTO t_drive.tb_email_template
(template_key, locale_tag, subject_template, body_template, register_user_seq, register_date, update_user_seq, update_date)
VALUES('ADMIN_USER_CSV_REGISTER', 'en-US', '[${serviceName}] Your account has been created.', 'Your account has been created for ${baseUrl}

ID : ${userId}
Temporary password : ${pw}

Sign in Url : ${loginUrl}
For security, please change your password after your first sign-in.', 1, '2025-10-13 17:16:13', NULL, '2026-04-07 08:37:53');
INSERT INTO t_drive.tb_email_template
(template_key, locale_tag, subject_template, body_template, register_user_seq, register_date, update_user_seq, update_date)
VALUES('PASSWORD_RESET', 'ko-KR', '[${serviceName}] 비밀번호 재설정 안내', '<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
</head>
<body style="margin:0;padding:0;background-color:#ffffff;">

  <div style="max-width:600px;margin:0 auto;padding:48px 40px;box-sizing:border-box;border:1px solid rgba(58,61,73,0.12);font-family:''Pretendard'',''Apple SD Gothic Neo'',''Malgun Gothic'',Arial,sans-serif;">

    <!-- 본문 영역 -->
    <div style="padding-bottom:64px;">

      <!-- 로고 -->
      <div style="padding-bottom:32px;">
        <img src="${logoUrl}" alt="${serviceName}" width="140" height="20" style="display:block;border:0;" />
      </div>

      <!-- 텍스트 -->
      <div style="margin-bottom:28px;color:#3A3D49;font-size:14px;font-weight:400;line-height:20px;word-break:break-word;">
        안녕하세요, ${userName}님.<br/>
        고객님의 계정에 대한 비밀번호 재설정 요청을 접수하였습니다. 아래 버튼을 클릭하여
        새 비밀번호를 설정해 주세요. 본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다.<br/>
        <br/>
        이 링크는 발송 후 1시간 동안만 유효합니다.
      </div>

      <!-- 버튼 -->
      <table cellpadding="0" cellspacing="0" border="0">
                  <tr>
                    <td
                      style="
                        background-color:#151A2A;
                        border-radius:4px;
                        text-align:center;
                      "
                    >
                      <a
                        href="${resetLink}"
                        style="
                          display:block;
                          padding:8px 16px;
                          color:#FFFFFF;
                          font-size:14px;
                          font-family:''Pretendard'',''Apple SD Gothic Neo'',''Malgun Gothic'',Arial,sans-serif;
                          font-weight:400;
                          line-height:20px;
                          text-decoration:none;
                          text-align:center;
                          white-space:nowrap;
                        "
                      >
                        비밀번호 재설정
                      </a>
                    </td>
                  </tr>
                </table>

    </div>

    <!-- 푸터 -->
    <div style="padding-top:28px;border-top:1px solid rgba(58,61,73,0.08);">
      <p style="margin:0;color:rgba(58,61,73,0.60);font-size:12px;font-weight:400;line-height:16px;word-break:break-word;">
        본 이메일은 <strong>기밀 정보를 포함</strong>하고 있으며, 지정된 수신인에게만 전달되었습니다.
        만약 귀하가 의도된 수신인이 아니라면, 이 이메일의 내용을 열람, 사용 또는 배포하는 것을 <strong>엄격히 금지</strong>합니다.
        만일 이 이메일을 잘못 수신하셨다면, 즉시 삭제해 주시고 발신자에게 알려주시기 바랍니다.<br/>
      </p>
    </div>

  </div>

</body>
</html>
', 1, '2026-02-23 14:33:27', NULL, '2026-04-13 10:10:10');
INSERT INTO t_drive.tb_email_template
(template_key, locale_tag, subject_template, body_template, register_user_seq, register_date, update_user_seq, update_date)
VALUES('PASSWORD_RESET', 'en-US', '[${serviceName}] Password Reset Instructions', '<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
</head>
<body style="margin:0;padding:0;background-color:#ffffff;">

  <div style="max-width:600px;margin:0 auto;padding:48px 40px;box-sizing:border-box;border:1px solid rgba(58,61,73,0.12);font-family:''Pretendard'',''Apple SD Gothic Neo'',''Malgun Gothic'',Arial,sans-serif;">

    <!-- Body -->
    <div style="padding-bottom:64px;">

      <!-- Logo -->
      <div style="padding-bottom:32px;">
        <img src="${logoUrl}" alt="${serviceName}" width="140" height="20" style="display:block;border:0;" />
      </div>

      <!-- Text -->
      <div style="margin-bottom:28px;color:#3A3D49;font-size:14px;font-weight:400;line-height:20px;word-break:break-word;">
        Hello, ${userName}.<br/>
        We received a request to reset your password.
        Please click the button below to set a new password.
        If you did not request this, please ignore this email.<br/>
        <br/>
        This link is valid for 1 hour after sending.
      </div>

      <!-- Button -->
        <table cellpadding="0" cellspacing="0" border="0">
          <tr>
            <td
              style="
                background-color:#151A2A;
                border-radius:4px;
                text-align:center;
              "
            >
              <a
                href="${resetLink}"
                style="
                  display:block;
                  padding:8px 16px;
                  color:#FFFFFF;
                  font-size:14px;
                  font-family:''Pretendard'',''Apple SD Gothic Neo'',''Malgun Gothic'',Arial,sans-serif;
                  font-weight:400;
                  line-height:20px;
                  text-decoration:none;
                  text-align:center;
                  white-space:nowrap;
                "
              >
                Reset Password
              </a>
            </td>
          </tr>
        </table>

    </div>

    <!-- Footer -->
    <div style="padding-top:28px;border-top:1px solid rgba(58,61,73,0.08);">
      <p style="margin:0;color:rgba(58,61,73,0.60);font-size:12px;font-weight:400;line-height:16px;word-break:break-word;">
        This email contains <strong>confidential information</strong> and is intended solely for the designated recipient(s).
        If you are not the intended recipient, you are hereby notified that any disclosure, copying, distribution,
        or use of the contents of this email is <strong>strictly prohibited</strong>.
        If you have received this email in error, please delete it immediately and notify the sender.<br/>
      </p>
    </div>

  </div>

</body>
</html>
', 1, '2026-02-23 14:33:32', NULL, '2026-04-13 10:11:04');
INSERT INTO t_drive.tb_email_template
(template_key, locale_tag, subject_template, body_template, register_user_seq, register_date, update_user_seq, update_date)
VALUES('PASSWORD_RESET', 'es-ES', '[${serviceName}] Instrucciones para restablecer la contraseña', '<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
</head>
<body style="margin:0;padding:0;background-color:#ffffff;">

  <div style="max-width:600px;margin:0 auto;padding:48px 40px;box-sizing:border-box;border:1px solid rgba(58,61,73,0.12);font-family:''Pretendard'',''Apple SD Gothic Neo'',''Malgun Gothic'',Arial,sans-serif;">

    <!-- Cuerpo -->
    <div style="padding-bottom:64px;">

      <!-- Logo -->
      <div style="padding-bottom:32px;">
        <img src="${logoUrl}" alt="${serviceName}" width="140" height="20" style="display:block;border:0;" />
      </div>

      <!-- Texto -->
      <div style="margin-bottom:28px;color:#3A3D49;font-size:14px;font-weight:400;line-height:20px;word-break:break-word;">
        Hola, ${userName}.<br/>
        Hemos recibido una solicitud para restablecer tu contraseña.
        Haz clic en el siguiente botón para establecer una nueva contraseña.
        Si no realizaste esta solicitud, ignora este correo.<br/>
        <br/>
        Este enlace es válido durante 1 hora después del envío.
      </div>

      <!-- Botón -->
        <table cellpadding="0" cellspacing="0" border="0">
          <tr>
            <td
              style="
                background-color:#151A2A;
                border-radius:4px;
                text-align:center;
              "
            >
              <a
                href="${resetLink}"
                style="
                  display:block;
                  padding:8px 16px;
                  color:#FFFFFF;
                  font-size:14px;
                  font-family:''Pretendard'',''Apple SD Gothic Neo'',''Malgun Gothic'',Arial,sans-serif;
                  font-weight:400;
                  line-height:20px;
                  text-decoration:none;
                  text-align:center;
                  white-space:nowrap;
                "
              >
                Restablecer contraseña
              </a>
            </td>
          </tr>
        </table>

    </div>

    <!-- Pie de página -->
    <div style="padding-top:28px;border-top:1px solid rgba(58,61,73,0.08);">
      <p style="margin:0;color:rgba(58,61,73,0.60);font-size:12px;font-weight:400;line-height:16px;word-break:break-word;">
        Este mensaje contiene <strong>información confidencial</strong> y está dirigido exclusivamente al destinatario(s) indicado(s).
        Si usted no es el destinatario previsto, se le informa que cualquier visualización, uso, copia o distribución
        de los contenidos de este mensaje está <strong>estrictamente prohibida</strong>.
        Si ha recibido este mensaje por error, por favor elimínelo inmediatamente y notifique al remitente.<br/>
      </p>
    </div>

  </div>

</body>
</html>
', 1, '2026-04-13 09:30:54', NULL, '2026-04-13 10:11:55');
INSERT INTO t_drive.tb_email_template
(template_key, locale_tag, subject_template, body_template, register_user_seq, register_date, update_user_seq, update_date)
VALUES('SHARE_FOLDER', 'es-ES', 'Carpeta compartida contigo: "${resourceName}"', '${requestUserName} (${requestUserId}) ha compartido el siguiente elemento contigo.

Elemento compartido: ${resourceName}', 1, '2026-04-13 09:34:51', NULL, '2026-04-13 09:41:45');
INSERT INTO t_drive.tb_email_template
(template_key, locale_tag, subject_template, body_template, register_user_seq, register_date, update_user_seq, update_date)
VALUES('SHARE_FILE', 'es-ES', 'Archivo compartido contigo: "${resourceName}"', '${requestUserName} (${requestUserId}) ha compartido el siguiente elemento contigo.

Elemento compartido: ${resourceName}', 1, '2026-04-13 09:35:27', NULL, '2026-04-13 09:42:08');
INSERT INTO t_drive.tb_email_template
(template_key, locale_tag, subject_template, body_template, register_user_seq, register_date, update_user_seq, update_date)
VALUES('ADMIN_USER_CSV_REGISTER', 'es-ES', '[${serviceName}] Tu cuenta ha sido creada.', 'Tu cuenta ha sido creada en ${baseUrl}

ID : ${userId}
Contraseña temporal : ${pw}

URL de inicio de sesión : ${loginUrl}
Por seguridad, cambia tu contraseña después de tu primer inicio de sesión.', 1, '2026-04-13 09:36:04', NULL, '2026-04-13 09:41:21')