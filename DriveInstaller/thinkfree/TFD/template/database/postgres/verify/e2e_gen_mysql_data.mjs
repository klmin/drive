// e2e_docker.sh 용: mysql_src.sql 로 만든 throwaway MySQL 에 넣을 합성 데이터 생성 (information_schema 컬럼 TSV → INSERT)
// 규칙: 행 i 의 정수 컬럼 = i (FK 대상 tb_user 는 USERS 행이라 *_user_seq 가 전부 존재), 문자열은 길이 안에서 유일, 날짜는 서울 벽시계
import fs from 'node:fs';
const [inTsv, outSql] = process.argv.slice(2);  // e2e_docker.sh 가 .e2e/cols.tsv, .e2e/mysql_data.sql 을 넘김
const rows = fs.readFileSync(inTsv, 'utf8').trim().split('\n').map(l => l.replace(/\r$/, '').split('\t'));
const tables = {};
for (const [t, c, dt, len, extra, nullable, ctype] of rows) (tables[t] ??= []).push({ c, dt, len: +len, extra, nullable, ctype });

const USERS = 120;
const N = (t, idx) => t === 'tb_user' ? USERS : ['tb_base_domain', 'tb_super_admin_login_setting'].includes(t) ? 1 : 20 + idx; // 테이블마다 다른 행수
const esc = s => "'" + String(s).replace(/\\/g, '\\\\').replace(/'/g, "''") + "'";
const ts = i => `2026-${String(1 + (i % 9)).padStart(2, '0')}-${String(10 + (i % 18)).padStart(2, '0')} ${String(8 + (i % 12)).padStart(2, '0')}:${String(i % 60).padStart(2, '0')}:${String((i * 7) % 60).padStart(2, '0')}`;
const str = (col, len, i) => {
  if (len && len <= 3) return i.toString(36).slice(-len);
  const s = `${col.replace(/_/g, '').slice(0, 8).toLowerCase()}${i}`;
  return len ? s.slice(-len) : s;
};

const special = (t, c, i) => {
  if (t === 'tb_resource' && c === 'resource_name') return esc(i % 3 === 0 ? `${i}새 문서.docx` : i % 3 === 1 ? `name${i}.txt` : `000${i}abc`);
  if ((t === 'tb_recent' || t === 'tb_starred') && c === 'shared_by_user_seq') return i % 10 === 0 ? 'NULL' : String(i); // NULL 행 → 03 이 삭제해야 함
  if (t === 'tb_sso_provider' && c === 'scope') return "'TENANT'";
  if (t === 'tb_sso_jwt_config' && c === 'key_source_type') return "'PUBLIC_KEY'";
  if (t === 'tb_sso_jwt_config' && c === 'jwks_uri') return 'NULL';
  if (t === 'tb_base_domain' && c === 'base_domain_seq') return '1';
  if (t === 'tb_super_admin_login_setting' && c === 'super_admin_login_setting_seq') return '1';
  if (t === 'tb_share_setting' && c === 'is_enforced') return "'N'";
  if (t === 'tb_user' && c === 'status') return i % 7 === 0 ? "'DELETED'" : "'ACTIVE'";
  // 03 이 role_type 으로 tb_user_role 을 채운다 — 실제 역할 값이어야 백필이 검증된다(합성 문자열이면 0건)
  if (t === 'tb_user' && c === 'role_type') return i === 1 ? "'SUPER_ADMIN'" : i % 10 === 0 ? "'ADMIN'" : "'USER'";
  if (t === 'tb_user' && c === 'is_active') return i % 7 === 0 ? "'N'" : "'Y'";
  return undefined;
};
const val = (t, col, i) => {
  const sp = special(t, col.c, i); if (sp !== undefined) return sp;
  switch (col.dt) {
    case 'int': case 'bigint': case 'smallint': case 'tinyint': case 'mediumint': return String(col.ctype.includes('tinyint') ? i % 100 : i);
    case 'char': return col.len === 1 ? (i % 2 ? "'Y'" : "'N'") : esc(str(col.c, col.len, i));
    case 'varchar': case 'text': case 'mediumtext': case 'longtext': return esc(str(col.c, col.len, i));
    case 'datetime': case 'timestamp': return esc(ts(i));
    case 'json': return esc(JSON.stringify({ k: i, s: `v${i}` }));
    case 'blob': case 'mediumblob': case 'longblob': return `X'${Buffer.from('blob' + i).toString('hex')}'`;
    default: throw new Error(`unhandled type ${t}.${col.c} ${col.dt}`);
  }
};

let out = 'SET FOREIGN_KEY_CHECKS=0;\nSET NAMES utf8mb4;\n';
Object.keys(tables).forEach((t, idx) => {
  const cols = tables[t].filter(c => !/VIRTUAL GENERATED|STORED GENERATED/i.test(c.extra));
  const n = N(t, idx);
  const vals = [];
  for (let i = 1; i <= n; i++) vals.push('(' + cols.map(c => val(t, c, i)).join(',') + ')');
  out += `INSERT INTO \`${t}\` (${cols.map(c => '`' + c.c + '`').join(',')}) VALUES\n${vals.join(',\n')};\n`;
});
// dev MySQL 에만 있는 잡 테이블 6개 (02 의 EXCLUDING 대상) — 있어도 pgloader 가 건너뛰는지 확인용
for (const [j, src] of [['tb_license_1', 'tb_license'], ['tb_pinned_folder_test', 'tb_pinned_folder'], ['tb_recent_test', 'tb_recent'],
  ['tb_share_target_test', 'tb_share_target'], ['tb_share_test', 'tb_share'], ['tb_starred_test', 'tb_starred']])
  out += `CREATE TABLE \`${j}\` LIKE \`${src}\`;\nINSERT INTO \`${j}\` SELECT * FROM \`${src}\`;\n`;
fs.writeFileSync(outSql, out);
console.log('tables', Object.keys(tables).length, 'bytes', out.length);
