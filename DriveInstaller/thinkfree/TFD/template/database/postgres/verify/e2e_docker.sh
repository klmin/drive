#!/bin/bash
# =====================================================================================
# 실제 pgloader 로 migration/01→02→03→04 를 끝까지 돌리는 로컬 E2E (dev DB 무접촉, 전부 일회용 컨테이너)
#   throwaway MySQL 8.0(mysql_src.sql 구조 + 합성 데이터 + dev 잡 테이블 6개) → pgloader(ghcr.io/dimitri/pgloader) → throwaway PG 18
#   순서는 서버 ★ 절 명령과 동일: DROP SCHEMA → initialize.sql → 01 → pgloader 02 → 03 → 04 → e2e_post_checks.sql
#   실행:  bash e2e_docker.sh            (Git Bash, Docker Desktop 기동 상태)
#          KEEP=1 bash e2e_docker.sh     (끝나도 컨테이너 유지 — psql -h 127.0.0.1 -p 55432 -U t_drive_user t_drive, 비밀번호 verifypg)
#   02_drive.load 는 FROM/INTO 호스트만 컨테이너 이름으로 바꾼 사본을 쓴다(나머지 옵션·EXCLUDING·CAST·RENAME 은 리포 파일 그대로).
#   산출물: .e2e/ (out_pgloader.txt · out_counts.txt · out_post_checks.txt · count_diff.txt)
# =====================================================================================
set -u
V=$(cd "$(dirname "$0")" && pwd)
P=$(cd "$V/.." && pwd)
W="$V/.e2e"; mkdir -p "$W/run"   # 산출물은 매 실행 덮어씀
NET=pgmig-net; PGC=pgmig-verify; MYC=pgmig-mysql
PGIMG=postgres:18; MYIMG=mysql:8.0; LOADER=ghcr.io/dimitri/pgloader:latest
cleanup() { [ "${KEEP:-0}" = 1 ] && return; docker rm -f $PGC $MYC >/dev/null 2>&1; docker network rm $NET >/dev/null 2>&1; }
trap cleanup EXIT
step() { echo; echo "########## $*"; }
PSQL="docker exec -i -e PGPASSWORD=verifypg $PGC psql -h 127.0.0.1 -U t_drive_user -d t_drive -X"
MYSQL="docker exec -i $MYC mysql -uroot -pverify --default-character-set=utf8mb4"

step "containers"
docker rm -f $PGC $MYC >/dev/null 2>&1; docker network rm $NET >/dev/null 2>&1
docker network create $NET >/dev/null || exit 1
# 서버와 같은 initdb 옵션(C.UTF-8)·timezone
docker run -d --name $PGC --network $NET -p 55432:5432 -e POSTGRES_PASSWORD=verify -e TZ=Asia/Seoul \
  -e POSTGRES_INITDB_ARGS="--encoding=UTF8 --locale=C.UTF-8" $PGIMG -c timezone=Asia/Seoul >/dev/null || exit 1
# pgloader(qmynd)는 caching_sha2_password 미지원 → native password
docker run -d --name $MYC --network $NET -e MYSQL_ROOT_PASSWORD=verify -e MYSQL_DATABASE=t_drive -e MYSQL_USER=t_drive_user \
  -e MYSQL_PASSWORD=verifymy -e TZ=Asia/Seoul $MYIMG --default-authentication-plugin=mysql_native_password \
  --character-set-server=utf8mb4 --collation-server=utf8mb4_general_ci >/dev/null || exit 1
until $MYSQL -e "select 1" t_drive >/dev/null 2>&1; do sleep 2; done
until docker exec $PGC pg_isready -q 2>/dev/null; do sleep 1; done
docker exec $PGC psql -U postgres -q -c "CREATE ROLE t_drive_user LOGIN PASSWORD 'verifypg'" -c "CREATE DATABASE t_drive OWNER t_drive_user" || exit 1
docker exec $PGC psql -U postgres -tAc "select version()"

step "MySQL: mysql_src.sql + 합성 데이터"
$MYSQL t_drive < "$V/mysql_src.sql" 2>/dev/null || exit 1
$MYSQL -N -B -e "select table_name, column_name, data_type, ifnull(character_maximum_length,0), extra, is_nullable, column_type
                 from information_schema.columns where table_schema='t_drive' order by table_name, ordinal_position" 2>/dev/null > "$W/cols.tsv"
node "$(cygpath -w "$V/e2e_gen_mysql_data.mjs" 2>/dev/null || echo "$V/e2e_gen_mysql_data.mjs")" "$(cygpath -w "$W/cols.tsv" 2>/dev/null || echo "$W/cols.tsv")" "$(cygpath -w "$W/mysql_data.sql" 2>/dev/null || echo "$W/mysql_data.sql")" || exit 1
$MYSQL t_drive < "$W/mysql_data.sql" 2>/dev/null || exit 1

step "0) DROP SCHEMA → initialize.sql"
cp "$P/init/initialize.sql" "$P"/migration/0[134]_*.sql "$W/run/"
sed -e 's#@172\.17\.163\.141:3306/#@'$MYC':3306/#' -e 's#@127\.0\.0\.1:5432/#@'$PGC':5432/#' "$P/migration/02_drive.load" > "$W/run/02_drive.load"
$PSQL -v ON_ERROR_STOP=1 -q -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;" 2>/dev/null || exit 1
$PSQL -v ON_ERROR_STOP=1 -q -f - < "$W/run/initialize.sql" > "$W/out_init.txt" 2>&1 || { tail -20 "$W/out_init.txt"; exit 1; }
$PSQL -tAc "SELECT 'partitioned tables = ' || count(*) || ' (expect 11)' FROM pg_partitioned_table"

step "1) 01_before_load.sql"
$PSQL -v ON_ERROR_STOP=1 -f - < "$W/run/01_before_load.sql" || exit 1

step "2) pgloader 02_drive.load"
MSYS_NO_PATHCONV=1 docker run --rm --network $NET -e MYSQL_PWD=verifymy -e PGPASSWORD=verifypg \
  -v "$(cygpath -w "$W/run" 2>/dev/null || echo "$W/run")":/w $LOADER pgloader /w/02_drive.load > "$W/out_pgloader.txt" 2>&1
echo "pgloader exit=$?"
grep -E 'WARNING|ERROR|failed to find|KABOOM|Skipping' "$W/out_pgloader.txt" | cut -c1-200
grep -E '^ *(fetch meta data|public\.tb_resource_stage|Create Check Constraints|Total import time)' "$W/out_pgloader.txt"

step "3) 03_after_load.sql"
$PSQL -v ON_ERROR_STOP=1 -f - < "$W/run/03_after_load.sql" || exit 1

step "4) 04_verify_counts.sql vs MySQL COUNT(*)"
$PSQL -f - < "$W/run/04_verify_counts.sql" > "$W/out_counts.txt" || exit 1
Q=$($MYSQL -N -B -e "SET SESSION group_concat_max_len=100000; SELECT GROUP_CONCAT(CONCAT('SELECT ''', table_name, ''' t, COUNT(*) c FROM \`', table_name, '\`') ORDER BY table_name SEPARATOR ' UNION ALL ') FROM information_schema.tables WHERE table_schema='t_drive' AND table_type='BASE TABLE'" 2>/dev/null)
$MYSQL -N -B t_drive -e "$Q" 2>/dev/null | tr -d '\r' | sort > "$W/my_counts.tsv"
tr -d '\r' < "$W/out_counts.txt" | awk -F'|' 'NF==2 && $2+0==$2 {gsub(/ /,"",$1); gsub(/ /,"",$2); print $1"\t"$2}' | sort > "$W/pg_counts.tsv"
echo "PG tables counted: $(wc -l < "$W/pg_counts.tsv") (expect 42 — tb_mcp_oauth_jwk 제외)"
echo "MySQL NULL shared_by_user_seq (03 이 삭제): $($MYSQL -N -B t_drive -e "SELECT CONCAT('tb_recent=', (SELECT COUNT(*) FROM tb_recent WHERE shared_by_user_seq IS NULL), ' tb_starred=', (SELECT COUNT(*) FROM tb_starred WHERE shared_by_user_seq IS NULL))" 2>/dev/null)"
echo "MySQL vs PG 차이 (테이블 MySQL PG) — 예상: 제외 5개 PG=0, 잡 테이블 6개·tb_mcp_oauth_jwk MISSING, tb_recent/tb_starred = MySQL - NULL 행:"
join -a1 -a2 -e MISSING -o 0,1.2,2.2 -t $'\t' "$W/my_counts.tsv" "$W/pg_counts.tsv" | awk -F'\t' '$2!=$3' | tee "$W/count_diff.txt"

step "5) e2e_post_checks.sql (IDENTITY·컬럼 순서·생성 컬럼·파티션·제약·타임존·새 INSERT)"
$PSQL -f - < "$V/e2e_post_checks.sql" > "$W/out_post_checks.txt" 2>&1
cat "$W/out_post_checks.txt"
