# 기존 DB 를 Flyway 체계로 옮기기

2026-11 부터 Drive 앱이 기동할 때 Flyway(`DriveAPI/src/main/resources/db/migration`)가 스키마를 관리합니다.
Flyway 이력 테이블(`flyway_schema_history`)이 없는 기존 DB 는 **V1 상태로 맞춘 뒤, 첫 기동만 baseline** 으로 띄웁니다.

| 설정 | 동작 |
|---|---|
| `DRIVE_FLYWAY_BASELINE_ON_MIGRATE=false`(기본) | 테이블은 있는데 이력이 없으면 기동 실패 |
| `DRIVE_FLYWAY_BASELINE_ON_MIGRATE=true` | V1 을 실행하지 않고 "V1 적용됨"만 기록한 뒤 V2 부터 적용. 스키마가 V1 과 같은지는 **검사하지 않는다** |

baseline 이 한 번 기록되면 이 값은 더는 의미가 없습니다. 첫 기동 뒤 `false` 로 되돌립니다.

## 1. 이미 PostgreSQL 로 운영 중인 DB

1. DB 를 백업합니다.
2. 아직 적용하지 않은 `upgrade/` 스크립트를 이름의 이슈 번호 순서대로 실행합니다. 이미 적용한 스크립트는 건너뜁니다.
   1. `TFDRIVE-1896_user_role.sql`
   2. `TFDRIVE-1897_tenant_user_id.sql`
   3. `TFDRIVE-1904_system_managed_tenant.sql`

   ```bash
   psql -h <host> -U t_drive_user -d t_drive -v ON_ERROR_STOP=1 -f upgrade/<파일>
   ```
3. 새 버전 앱을 `DRIVE_FLYWAY_BASELINE_ON_MIGRATE=true` 로 한 번 기동합니다.
4. 기동이 끝나면 `false` 로 되돌립니다.

`tb_license.name` 컬럼이 남아 있어도 V1 과 다를 뿐 동작에는 영향이 없습니다. 코드가 읽지도 쓰지도 않습니다.

## 2. MySQL 에서 옮기는 DB

`01_before_load.sql` → `02_drive.load`(pgloader) → `03_after_load.sql` → `04_verify_counts.sql` 순서는 그대로입니다.
이 절차는 `init/initialize.sql` 로 스키마를 먼저 만든 DB 를 전제로 합니다. initialize.sql 은 V1 과 같은 스키마로 고정돼 있습니다.

이관이 끝난 DB 는 Flyway 이력이 없습니다. 앱 첫 기동만 `DRIVE_FLYWAY_BASELINE_ON_MIGRATE=true` 로 띄우고, 그 뒤 `false` 로 되돌립니다.
`upgrade/` 스크립트는 필요 없습니다. 01·03 이 같은 일을 합니다.

## 3. 새로 설치하는 DB

할 일이 없습니다. 빈 DB 에 앱이 기동하면 Flyway 가 V1 부터 실행합니다. `DRIVE_FLYWAY_BASELINE_ON_MIGRATE` 는 기본값(false) 그대로 둡니다.

## 이후 스키마 변경

`upgrade/` 에 수동 스크립트를 더 만들지 않습니다. `DriveAPI/src/main/resources/db/migration` 에 새 V 파일을 추가하면 앱 기동 때 자동으로 적용됩니다.
