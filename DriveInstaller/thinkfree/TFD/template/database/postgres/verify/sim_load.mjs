import { PGlite } from '@electric-sql/pglite';
import fs from 'node:fs';
const R='C:/intelliJProject/gerrit/drive/DriveInstaller/thinkfree/TFD/template/database/postgres/';
const rd=(p)=>fs.readFileSync(R+p,'utf8').replace(/\r\n/g,'\n');

// pgloader data only 흉내: 자식 먼저, 명시 PK, MySQL datetime 문자열,
// ★ MySQL VIRTUAL 컬럼(resource_name_numeric_part/text_part) 값도 같이 넘어온다 (09-29 실서버에서 확인된 실제 동작)
// ★ tb_resource 는 02 의 ALTER TABLE NAMES MATCHING 으로 tb_resource_stage 에 적재된다 (본 테이블 무접촉)
const RESOURCE_ROWS = `(123456,1000013,'file','12새 문서.docx',12,'새 문서.docx',1000013,'2026-06-01 10:00:00'),
          (123457,1000013,'folder','abc',NULL,'abc',1000013,'2026-06-01 10:00:01'),
          (99,1,'folder','root-of-user1',NULL,'root-of-user1',1,'2026-01-01 00:00:00'),
          (100,2,'folder','root-of-user2',NULL,'root-of-user2',2,'2026-01-01 00:00:00'),
          (101,3,'folder','u3',NULL,'u3',3,'2026-01-01 00:00:00'),(102,4,'folder','u4',NULL,'u4',4,'2026-01-01 00:00:00'),
          (103,5,'folder','u5',NULL,'u5',5,'2026-01-01 00:00:00'),(104,6,'folder','u6',NULL,'u6',6,'2026-01-01 00:00:00')`;
const RESOURCE_COLS = `(resource_seq,user_seq,resource_type,resource_name,resource_name_numeric_part,resource_name_text_part,register_user_seq,register_date)`;
const FAKE_PGLOADER = `
 insert into tb_user_log(user_log_seq,user_seq,region,ip_address,register_date,activity_type,actor_type) values (500,1000013,'Unknown','127.0.0.1','2026-09-28 15:00:00','LOGIN','USER');
 insert into tb_system_config(config_seq,tenant_seq,setting_key,value_text,register_date,register_user_seq,update_date,update_user_seq) values (7,1,'office_domain','x','2026-01-01 09:00:00',1,'2026-01-01 09:00:00',1);
 insert into tb_tenant(tenant_seq,tenant_name,register_user_seq,register_date) values (1,'기본 테넌트',1,'2026-03-05 10:17:01');
 insert into tb_user(user_seq,user_id,user_name,register_user_seq,register_date) values (1,'superadmin','슈퍼어드민',1,'2026-01-01 00:00:00'),(1000013,'aa@thinkfree.com','임경민',1,'2026-05-01 12:34:56');
 insert into tb_resource_stage${RESOURCE_COLS} values ${RESOURCE_ROWS};
 insert into tb_email_template(email_template_seq,template_key,locale_tag,subject_template,body_template,register_user_seq) values (21,'X','ko-KR','s','b',1);
 insert into tb_recent(recent_seq,user_seq,resource_seq,shared_by_user_seq,register_user_seq) values (1,1,99,1,1),(2,1,100,NULL,1);
 insert into tb_starred(starred_seq,user_seq,resource_seq,shared_by_user_seq,register_user_seq) values (1,1,99,1,1),(2,2,100,NULL,2);
`;

const pg=new PGlite(); const q=async s=>(await pg.query(s)).rows;
await pg.exec("SET timezone TO 'Asia/Seoul'");
await pg.exec(rd('init/initialize.sql'));                 console.log('initialize OK');
const colsBefore=(await q("select column_name from information_schema.columns where table_name='tb_resource' order by ordinal_position")).map(r=>r.column_name);
console.log(' partitioned tables:', (await q("select count(*)::int c from pg_partitioned_table"))[0].c, '(expect 11) partitions:', (await q("select count(*)::int c from pg_class where relispartition and relkind='r'"))[0].c, '(expect 22)');

console.log('\n===== A: initialize -> 01 -> pgloader(stage) -> 03 =====');
await pg.exec(rd('migration/01_before_load.sql'));        console.log('before_load OK');
console.log(' seeds gone:', await q("select (select count(*) from tb_user) u,(select count(*) from tb_tenant) t,(select count(*) from tb_email_template) e"));
console.log(' fks now:', (await q("select count(*)::int c from pg_constraint where contype='f' and conrelid::regclass::text in ('tb_system_config','tb_user_log')"))[0].c);
console.log(' stage cols (generated must be NEVER):', await q("select column_name, is_generated, data_type from information_schema.columns where table_name='tb_resource_stage' and column_name like 'resource_name_%_part' order by 1"));
console.log(' tb_resource untouched:', (await q("select count(*)::int c from information_schema.columns where table_name='tb_resource' and is_generated='ALWAYS'"))[0].c, 'generated cols,', (await q("select count(*)::int c from pg_indexes where tablename='tb_resource'"))[0].c, 'indexes');
await pg.exec(FAKE_PGLOADER); console.log('fake pgloader insert OK (child-before-parent, explicit ids, VIRTUAL col values into stage)');
console.log(' stage rows:', (await q("select count(*)::int c from tb_resource_stage"))[0].c);
await pg.exec(rd('migration/03_after_load.sql'));        console.log('after_load OK');
console.log(' stage dropped:', (await q("select count(*)::int c from pg_tables where tablename='tb_resource_stage'"))[0].c === 0);
const colsAfter=(await q("select column_name from information_schema.columns where table_name='tb_resource' order by ordinal_position")).map(r=>r.column_name);
console.log(' ★ column order unchanged (= initialize.sql = MySQL):', JSON.stringify(colsBefore)===JSON.stringify(colsAfter), '| generated positions:', (await q("select column_name||'@'||ordinal_position p from information_schema.columns where table_name='tb_resource' and is_generated='ALWAYS' order by ordinal_position")).map(r=>r.p), '(expect @9, @10)');
console.log(' rows per partition:', await q("select tableoid::regclass::text p, count(*)::int c from tb_resource group by 1 order by 1"));
console.log(' gen cols recomputed by PG:', await q("select resource_name, resource_name_numeric_part n, resource_name_text_part t from tb_resource where resource_seq in (123456,123457) order by 1"));
console.log(' fks back:', (await q("select conname from pg_constraint where contype='f' and conislocal order by 1")).map(r=>r.conname));
const nu=await q("insert into tb_user(user_id,user_name,register_user_seq) values('new@x','n',1) returning user_seq");
const nt=await q("insert into tb_email_template(template_key,locale_tag,subject_template,body_template,register_user_seq) values('Y','ko-KR','s','b',1) returning email_template_seq");
const nr=await q("insert into tb_resource(user_seq,resource_type,resource_name,register_user_seq) values(1,'file','7z',1) returning resource_seq, resource_name_numeric_part, resource_name_text_part, tableoid::regclass::text p");
const na=await q("insert into tb_api_key(key_name,key_purpose,api_key,register_user_seq,user_seq,scope,expire_date) values('k','p','raw',1,1,'read',now()) returning api_key_seq");
console.log(' next ids -> user', nu[0].user_seq, '(expect 1000014) template', nt[0].email_template_seq, '(expect 22) resource', nr[0].resource_seq, '(expect 123458) gen', nr[0].resource_name_numeric_part, nr[0].resource_name_text_part, 'in', nr[0].p, '| api_key(empty)', na[0].api_key_seq, '(expect 1)');
await pg.exec("insert into tb_user_quota_alert_state(user_seq,last_threshold_percent,register_date) values (1000013,80,now()) on conflict (user_seq) do update set last_threshold_percent=excluded.last_threshold_percent, update_date=now()");
await pg.exec("insert into tb_user_quota_alert_state(user_seq,last_threshold_percent,register_date) values (1000013,90,now()) on conflict (user_seq) do update set last_threshold_percent=excluded.last_threshold_percent, update_date=now()");
console.log(' partitioned upsert OK, rows:', (await q("select count(*)::int c from tb_user_quota_alert_state"))[0].c, '(expect 1)');
console.log(' shared_by_user_seq NULL rows deleted + NOT NULL restored (10-07):', await q("select (select count(*)::int from tb_recent) recent, (select count(*)::int from tb_starred) starred, (select string_agg(attnotnull::text, ',') from pg_attribute where attname='shared_by_user_seq' and attrelid in ('tb_recent'::regclass,'tb_starred'::regclass)) not_null_flags"), '(expect 1, 1, true,true)');
const nrc=await q("insert into tb_recent(user_seq,resource_seq,shared_by_user_seq,register_user_seq) values(1,101,1,1) returning recent_seq");
console.log(' next recent_seq', nrc[0].recent_seq, '(expect 2 — 03 의 setval 은 NULL 행 삭제 뒤 MAX+1)');
console.log(' tz roundtrip:', await q("select register_date::text d from tb_user where user_seq=1000013"));
try{ await pg.exec("insert into tb_user_log(user_seq,region,activity_type) values (99999999,'x','LOGIN')"); console.log(' FK NOT enforced'); }catch(e){ console.log(' FK enforced again:', e.message.split('\n')[0]); }
const plan=(await q("explain select * from tb_resource where user_seq=1000013 and resource_seq=123456")).map(r=>r['QUERY PLAN']);
console.log(' pruning check (1 partition scanned):', plan.filter(l=>/tb_resource/.test(l)).map(l=>l.trim()));

console.log('\n===== C: why staging is needed — VIRTUAL values straight into tb_resource must FAIL =====');
try{ await pg.exec(`insert into tb_resource${RESOURCE_COLS} values (999,1,'file','9x',9,'x',1,'2026-01-01 00:00:00')`); console.log(' UNEXPECTED: insert succeeded'); }
catch(e){ console.log(' expected failure:', e.message.split('\n')[0]); }
await pg.close();
