# PostgreSQL 스키마 검증 도구 (PGlite = PostgreSQL 18 WASM, 서버 불필요)

> 2026-11 Flyway 도입 뒤 스키마 정본은 `DriveAPI/src/main/resources/db/migration` 입니다. 이 도구들은 MySQL 이관 검증용이고 고정된 `init/initialize.sql`(= V1 스키마)을 그대로 씁니다.
> V2 이후 변경은 여기서 검증하지 않습니다. 이관한 DB 의 첫 앱 기동 절차는 [migration/README.md](../migration/README.md) 를 봅니다.

```bash
cd DriveInstaller/thinkfree/TFD/template/database/postgres/verify
npm init -y && npm i @electric-sql/pglite
node run.mjs                     # init/initialize.sql 전체 실행 + 제약·IDENTITY·생성컬럼 동작 확인
node compare.mjs                 # mysql_src.sql(MySQL DDL, 코멘트 제거본) vs init/initialize.sql 카탈로그 전수 대조
node compare.mjs <pg_dump.sql>   # 서버에서 뽑은 PG DDL(DBeaver 등) vs MySQL 원본 대조
node sim_load.mjs                # A(initialize→01→tb_resource_stage 적재 흉내(VIRTUAL 값 포함)→03: 컬럼 순서 불변·파티션·upsert·시퀀스) · C(생성 컬럼 직접 INSERT 실패 확인)
```

## 실제 pgloader E2E (Docker, 10-07 추가)

PGlite 는 pgloader 를 흉내만 낸다. 실제 pgloader 동작(경고·Check Constraints 단계·RENAME·EXCLUDING)까지 보려면:

```bash
bash e2e_docker.sh          # Docker Desktop 기동 상태, Git Bash. KEEP=1 이면 끝나도 컨테이너 유지(localhost:55432)
```

- 일회용 컨테이너 3종(`pgmig-verify` postgres:18 C.UTF-8·Asia/Seoul / `pgmig-mysql` mysql:8.0 native password / `ghcr.io/dimitri/pgloader`)만 쓴다. dev DB 무접촉.
- MySQL 에 `mysql_src.sql` 구조 + `e2e_gen_mysql_data.mjs` 합성 데이터(테이블마다 다른 행수, `tb_recent`/`tb_starred` 일부 `shared_by_user_seq` NULL, 숫자로 시작하는 `resource_name`) + dev 잡 테이블 6개를 넣는다.
- 서버 ★ 명령과 같은 순서(DROP SCHEMA → initialize → 01 → pgloader 02(호스트만 치환) → 03 → 04) 뒤 MySQL `COUNT(*)` 와 자동 대조하고 `e2e_post_checks.sql`(IDENTITY = MAX+1, 컬럼 순서, 생성 컬럼, 파티션 분포, 제약, 타임존, 새 INSERT 롤백)을 돌린다. 산출물은 `.e2e/`(gitignore).
- 알려진 정상 출력: pgloader 3.6.10 의 `Create Check Constraints` errors 7 (initialize.sql 이 이미 만든 CHECK 를 다시 만들려다 "already exists"/`_utf8mb4\'` syntax error — 적재와 무관, 제약은 그대로 존재).

- `mysql_src.sql` 은 dev MySQL 덤프(2026-09-28, 43테이블)에서 COMMENT 만 뗀 구조 정본. MySQL 스키마가 바뀌면 여기부터 갱신.
- compare.mjs 의 유일한 잔여 보고 `SPRING_SESSION_IX1 renamed ... spring_session_ix1` 은 대소문자 폴딩(동일 인덱스)이라 무시.
- run.mjs / sim_load.mjs 는 절대경로 `C:/intelliJProject/gerrit/drive/...` 를 참조 → 다른 경로에서는 상단 경로 수정.
