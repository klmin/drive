# PostgreSQL 초기화 SQL 유지보수

> **2026-11 Flyway 도입 이후**
> - 스키마·기초 데이터의 정본은 `DriveAPI/src/main/resources/db/migration`(V1__init.sql, V2…)입니다. Drive 앱이 기동할 때 Flyway 가 실행합니다.
> - 이 initialize.sql 은 Kubernetes bootstrap 호환용으로 **고정**했습니다. 더 이상 고치지 않습니다. 스키마 변경은 새 V 파일로 합니다.
> - V1 과 다른 점은 office 빈 행 2개(office_domain·office_adapter_name)와 BEGIN/COMMIT 뿐입니다. 빈 행은 동작에 영향이 없습니다.
> - 설치기(docker compose)는 더 이상 이 파일을 실행하지 않습니다. `docker-init/01_init_drive_db.sh` 는 계정·DB 만 만듭니다.
> - 이 파일로 만든 DB(k8s bootstrap, MySQL 이관 등)는 Flyway 이력이 없습니다. 첫 기동에 `DRIVE_FLYWAY_BASELINE_ON_MIGRATE=true` 를 줘야 "V1 적용됨"으로 기록되고, 그 뒤 V2 부터 적용됩니다. false 이면 기동이 실패합니다.
> - 기존 DB 를 V1 상태로 맞추는 절차는 [migration/README.md](../migration/README.md) 를 봅니다.

아래는 Flyway 도입 전부터의 Kubernetes 안내입니다.

initialize.sql은 빈 Drive DB에 테이블과 초기 계정을 생성하는 원본입니다.
Kubernetes용으로 실행 구문이나 BEGIN/COMMIT을 변경하지 않았습니다.
2026-11-04 변경은 상단의 배포 안내 주석뿐입니다.

[초기화 가이드](../../../../../../kubernetes/README.md)의 bootstrap.sql이 잠금과 테이블 확인 후 필요한 경우에만 원본을 실행합니다.
Developers의 infra/kubernetes/helm/cloud-drive chart가 검증된 SQL과 스크립트를 ConfigMap으로 패키징하고,
Drive Pod의 initContainer에 마운트합니다. DB 접속 값은 기존 Secret에서 읽습니다.

**이 SQL이나 bootstrap 스크립트를 바꿨다면 Developers chart 파일도 동기화해야 합니다.**
Developers 저장소에서 infra/kubernetes/scripts/sync-drive-bootstrap.py --drive /path/to/drive를 실행하고,
원본/사본 일치 검사, 로컬 초기화 테스트, Helm lint/template 검증을 수행하세요.
SQL 수정만으로 운영 ConfigMap이 바뀌지 않으며, ConfigMap 갱신으로 기존 DB가 업그레이드되지도 않습니다.

이미 초기화된 DB는 계정과 비밀번호를 유지합니다.
Super Admin이 없거나 비활성 상태라면 운영자가 확인해야 하며 스크립트는 자동 복원하지 않습니다.
