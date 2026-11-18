import { PGlite } from '@electric-sql/pglite';
import fs from 'node:fs';
const sql = fs.readFileSync('../init/initialize.sql', 'utf8').replace(/\r\n/g, '\n');
const pg = new PGlite();
await pg.exec("SET timezone TO 'Asia/Seoul'");
console.log((await pg.query('select version()')).rows[0].version);
try { await pg.exec(sql); console.log('SCRIPT OK'); }
catch (e) { console.log('SCRIPT FAILED:', e.message); process.exit(1); }
const q = async (s) => (await pg.query(s)).rows;
console.log('tables', (await q("select count(*)::int c from pg_tables where schemaname='public'"))[0].c);
console.log('indexes', (await q("select count(*)::int c from pg_indexes where schemaname='public'"))[0].c);
console.log('users', await q('select user_seq,user_id,tenant_seq from tb_user order by 1'));
console.log('templates', (await q('select count(*)::int c, min(email_template_seq) mn, max(email_template_seq) mx from tb_email_template'))[0]);
// identity continues after explicit seeds
const t = await q("insert into tb_tenant(tenant_name,register_user_seq) values('t2',1) returning tenant_seq");
const u = await q("insert into tb_user(user_id,user_name,register_user_seq) values('x@y','x',1) returning user_seq");
console.log('next tenant_seq', t[0].tenant_seq, 'next user_seq', u[0].user_seq);
// generated columns + sort index
await pg.exec("insert into tb_resource(user_seq,resource_type,resource_name,register_user_seq) values (1,'folder','12abc',1),(1,'file','abc',1),(1,'file','0007.txt',1),(1,'file','1234567890x',1)");
console.log('generated', await q('select resource_name, resource_name_numeric_part n, resource_name_text_part t from tb_resource order by resource_seq'));
// partial unique per tenant: deleted user may reuse id, NULL tenants (super admin) still unique via COALESCE, other tenant may reuse id
await pg.exec("insert into tb_user(user_id,user_name,status,register_user_seq) values('dup@y','d','DELETED',1)");
await pg.exec("insert into tb_user(user_id,user_name,status,register_user_seq) values('dup@y','d','ACTIVE',1)");
try { await pg.exec("insert into tb_user(user_id,user_name,status,register_user_seq) values('dup@y','d','ACTIVE',1)"); console.log('NULL-tenant unique NOT enforced'); } catch (e) { console.log('NULL-tenant unique enforced:', e.message.split('\n')[0]); }
await pg.exec(`insert into tb_user(user_id,user_name,status,tenant_seq,register_user_seq) values('dup@y','d','ACTIVE',${t[0].tenant_seq},1)`);
console.log('other tenant same id allowed');
try { await pg.exec(`insert into tb_user(user_id,user_name,status,tenant_seq,register_user_seq) values('dup@y','d','ACTIVE',${t[0].tenant_seq},1)`); console.log('per-tenant unique NOT enforced'); } catch (e) { console.log('per-tenant unique enforced:', e.message.split('\n')[0]); }
// sso identity unique per tenant: same IdP subject may link in another tenant, not twice in the same tenant
await pg.exec("insert into tb_user_sso_identity(user_seq,tenant_seq,sso_provider_seq,subject) values (2,1,7,'sub-x')");
await pg.exec(`insert into tb_user_sso_identity(user_seq,tenant_seq,sso_provider_seq,subject) values (${u[0].user_seq},${t[0].tenant_seq},7,'sub-x')`);
console.log('sso identity other tenant same subject allowed');
try { await pg.exec("insert into tb_user_sso_identity(user_seq,tenant_seq,sso_provider_seq,subject) values (3,1,7,'sub-x')"); console.log('sso identity per-tenant unique NOT enforced'); } catch (e) { console.log('sso identity per-tenant unique enforced:', e.message.split('\n')[0]); }
// global share setting singleton
await pg.exec("insert into tb_share_setting(tenant_seq,register_user_seq) values (NULL,1)");
try { await pg.exec("insert into tb_share_setting(tenant_seq,register_user_seq) values (NULL,1)"); console.log('global singleton NOT enforced'); } catch (e) { console.log('global singleton enforced:', e.message.split('\n')[0]); }
// check constraints
try { await pg.exec("insert into tb_share_setting(tenant_seq,is_enforced,register_user_seq) values (1,'Y',1)"); console.log('chk_enforce NOT enforced'); } catch (e) { console.log('chk_enforce enforced:', e.message.split('\n')[0]); }
try { await pg.exec("insert into tb_sso_provider(tenant_seq,protocol_type,scope,register_user_seq) values (NULL,'OIDC','TENANT',1)"); console.log('check_scope_tenant NOT enforced'); } catch (e) { console.log('check_scope_tenant enforced:', e.message.split('\n')[0]); }
// coalesce unique
await pg.exec("insert into tb_system_config(tenant_seq,setting_key,value_text,register_user_seq,update_user_seq) values (NULL,'k','v',1,1)");
try { await pg.exec("insert into tb_system_config(tenant_seq,setting_key,value_text,register_user_seq,update_user_seq) values (NULL,'k','v',1,1)"); console.log('coalesce unique NOT enforced'); } catch (e) { console.log('coalesce unique enforced:', e.message.split('\n')[0]); }
// case sensitivity + tz
console.log('ci', await q("select 'a.txt'='A.txt' ci, current_setting('timezone') tz"));
console.log('comments', (await q("select count(*)::int c from pg_description d join pg_class c on c.oid=d.objoid where c.relname like 'tb_%'"))[0].c);
