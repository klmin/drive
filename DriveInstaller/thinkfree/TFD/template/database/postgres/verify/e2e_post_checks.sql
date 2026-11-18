\echo '--- A) IDENTITY: 다음 값(last_value, is_called=false) = MAX+1 이어야 함 (불일치만 출력, 0행이면 OK)'
WITH ids AS (
  SELECT col.table_name t, col.column_name c, pg_get_serial_sequence(col.table_name, col.column_name) s
    FROM information_schema.columns col
    JOIN pg_class k ON k.relname = col.table_name AND k.relnamespace = 'public'::regnamespace
   WHERE col.table_schema = 'public' AND col.is_identity = 'YES' AND NOT k.relispartition)
SELECT t, c, s,
       (xpath('/row/m/text()', query_to_xml(format('SELECT COALESCE(MAX(%I),0)+1 AS m FROM %I', c, t), false, true, '')))[1]::text::bigint AS expect_next,
       (xpath('/row/v/text()', query_to_xml(format('SELECT CASE WHEN is_called THEN last_value+1 ELSE last_value END AS v FROM %s', s), false, true, '')))[1]::text::bigint AS seq_next
  FROM ids
EXCEPT
SELECT t, c, s, x, x FROM (SELECT t, c, s,
       (xpath('/row/m/text()', query_to_xml(format('SELECT COALESCE(MAX(%I),0)+1 AS m FROM %I', c, t), false, true, '')))[1]::text::bigint x FROM ids) z;
SELECT count(*) AS identity_columns_checked FROM information_schema.columns col JOIN pg_class k ON k.relname = col.table_name AND k.relnamespace='public'::regnamespace WHERE col.is_identity='YES' AND NOT k.relispartition;

\echo '--- B) tb_resource 컬럼 순서·생성 컬럼 위치 (9, 10 이어야 함)'
SELECT string_agg(column_name || '@' || ordinal_position, ', ' ORDER BY ordinal_position) FROM information_schema.columns WHERE table_name='tb_resource';
SELECT column_name, ordinal_position FROM information_schema.columns WHERE table_name='tb_resource' AND is_generated='ALWAYS' ORDER BY 2;
\echo '--- C) 생성 컬럼 재계산 값'
SELECT resource_seq, resource_name, resource_name_numeric_part n, resource_name_text_part t FROM tb_resource WHERE resource_seq IN (1,2,3) ORDER BY 1;
SELECT count(*) FILTER (WHERE resource_name ~ '^[0-9]' AND resource_name_numeric_part IS NULL) AS bad_numeric, count(*) FILTER (WHERE resource_name_text_part IS NULL) AS null_text FROM tb_resource;
\echo '--- D) 파티션 분포 (11테이블, 부모=자식 합)'
SELECT p.partrelid::regclass AS parent,
       string_agg(i.inhrelid::regclass || '=' || (xpath('/row/c/text()', query_to_xml(format('SELECT count(*) c FROM ONLY %s', i.inhrelid::regclass), false, true, '')))[1]::text, ', ' ORDER BY i.inhrelid::regclass::text) AS parts
  FROM pg_partitioned_table p JOIN pg_inherits i ON i.inhparent = p.partrelid GROUP BY 1 ORDER BY 1::text;
\echo '--- E) 스테이징 제거·FK·CHECK·NOT NULL·콜레이션 인덱스'
SELECT to_regclass('tb_resource_stage') AS stage_should_be_null;
SELECT conrelid::regclass, conname, contype FROM pg_constraint WHERE contype IN ('f','c') AND connamespace='public'::regnamespace AND conislocal ORDER BY 1::text, 2;
SELECT c.relname, a.attname, a.attnotnull FROM pg_attribute a JOIN pg_class c ON c.oid=a.attrelid WHERE a.attname='shared_by_user_seq' AND c.relname ~ '^tb_(recent|starred)' ORDER BY 1;
SELECT indexdef FROM pg_indexes WHERE indexname='idx_resource_sort_resource_name';
\echo '--- F) 타임존: MySQL 서울 벽시계가 +09 로'
SELECT user_seq, register_date FROM tb_user WHERE user_seq IN (1,2) ORDER BY 1;
\echo '--- G) 새 INSERT 가 MAX+1 (롤백)'
BEGIN;
INSERT INTO tb_user(user_id,user_name,register_user_seq) VALUES ('new@x','n',1) RETURNING user_seq;
INSERT INTO tb_resource(user_seq,resource_type,resource_name,register_user_seq) VALUES (3,'file','7z',1) RETURNING resource_seq, resource_name_numeric_part, resource_name_text_part, tableoid::regclass;
INSERT INTO tb_recent(user_seq,resource_seq,shared_by_user_seq,register_user_seq) VALUES (3,1,3,1) RETURNING recent_seq;
ROLLBACK;
