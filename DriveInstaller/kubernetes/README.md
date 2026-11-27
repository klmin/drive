# Drive PostgreSQL 최초 초기화

Drive WAS는 initContainer가 DB 확인을 마친 뒤 시작합니다. 별도 Job이나 전용 이미지는 사용하지 않습니다.
이 문서는 로컬 구현을 설명하며 운영 배포 완료를 의미하지 않습니다.

## 파일과 저장소 간 연결

| 파일 | 역할 |
| --- | --- |
| DriveInstaller/thinkfree/TFD/template/database/postgres/init/initialize.sql | 최초 설치 DDL·초기 데이터 원본. 실행 구문과 BEGIN/COMMIT 유지 |
| DriveInstaller/kubernetes/bootstrap.sh | DB Secret으로 psql 실행 |
| DriveInstaller/kubernetes/bootstrap.sql | 같은 연결에서 잠금·테이블 검사·필요 시 원본 SQL 실행 |
| Developers: infra/kubernetes/helm/cloud-drive | Drive Deployment, initContainer, ConfigMap, ClusterIP Service |
| Developers: infra/kubernetes/scripts/sync-drive-bootstrap.py | 원본 세 파일을 chart/files로 복사하고 체크섬 기록 |

**Drive 스크립트만 수정하면 Developers 배포에 반영되지 않습니다.**
Helm은 다른 저장소의 최신 파일을 자동으로 가져오지 않습니다. 아래 명령으로 사본을 갱신하고 양쪽 변경을 검토하세요.

~~~sh
# Developers 저장소에서 실행
python3 infra/kubernetes/scripts/sync-drive-bootstrap.py --drive /path/to/drive
python3 infra/kubernetes/scripts/sync-drive-bootstrap.py --drive /path/to/drive --check
~~~

chart는 스크립트와 SQL을 각각 immutable ConfigMap으로 생성합니다.
이름에 내용 해시를 넣으므로 변경 시 Pod가 새 ConfigMap을 참조합니다.
DB 비밀번호와 Super Admin API Key는 ConfigMap에 넣지 않습니다.
원본 SQL의 기본 계정 해시는 그대로 포함됩니다. 초기 로그인 비밀번호는 별도로 변경해야 합니다.
기본 로그인 비밀번호 변경과 고정 Super Admin API Key 교체는 서로 다른 작업입니다.

## 초기화 판단

DB와 접속 계정은 사전에 준비합니다. initContainer는 CREATE DATABASE/ROLE을 실행하지 않습니다.
pg_advisory_lock(73104, 1)을 얻은 뒤 아래 순서로 검사합니다.

1. public 스키마가 비어 있으면 원본 initialize.sql을 실행합니다.
2. tb_tenant, tb_user, tb_user_role, tb_system_config가 모두 있으면 실행을 생략합니다.
3. 기준 테이블이 부족한데 스키마에 다른 객체가 있으면 오류로 종료합니다.
4. tenant_seq가 NULL이고 ACTIVE 상태인 Super Admin이 있어야 성공합니다.
5. 기존 계정의 비밀번호·역할·상태는 변경하지 않습니다.

기준 테이블 검사는 최초 설치 여부만 판단합니다. 모든 컬럼의 버전 호환성을 보장하지 않습니다.
이후 버전 업그레이드는 별도 마이그레이션으로 처리하세요.

## 동시 실행·재시작·실패

Pod 두 개가 시작하면 하나만 초기화합니다. 다른 Pod는 잠금을 얻은 뒤 다시 검사합니다.
세션 잠금은 원본 SQL의 COMMIT에도 유지되며 DB 연결 종료 시 해제됩니다.
네트워크 단절을 서버가 감지하기 전까지 잠금이 남을 수 있습니다.
대기 Pod는 120초 후 실패하고 Kubernetes가 재시도합니다. 잠금을 무시하고 초기화하지 않습니다.

SQL 오류는 ON_ERROR_STOP으로 중단합니다. 연결이 종료되면 미완료 트랜잭션이 롤백됩니다.
커밋 직후 응답을 잃어도 다음 실행에서 테이블을 확인하여 중복 초기화를 생략합니다.
부분 스키마·인증 오류·Super Admin 누락/비활성은 운영자가 확인해야 합니다.
기존 DB를 삭제하거나 관리자 계정을 자동 복구하지 않습니다.

## 로컬 검증

~~~sh
python3 DriveInstaller/kubernetes/test-bootstrap.py
~~~

테스트는 무작위 이름의 일회용 Docker DB만 만들고 마지막에 제거합니다.
빈 DB, 반복 실행, 동시 기동, SQL 실패와 롤백, 수정 후 재시도, 부분 스키마,
Super Admin 누락/비활성, 연결 종료를 확인합니다.
결과는 /tmp/drive-bootstrap-test-report.json에 저장합니다.

## Developers·TFO 연동은 별도 단계

초기화 성공은 DB 준비만 의미합니다. Drive WAS에는 스토리지·라이선스 설정도 필요합니다.
Redis는 선택입니다(`DRIVE_REDIS_ENABLED`, 기본 `false`). WAS를 여러 대 둘 때 캐시 무효화·로그아웃 토큰·요청 수 제한을 맞추는 데 쓰며, 켜면 `maxmemory-policy noeviction`으로 둡니다. Redis 상태는 health에 넣지 않으므로 프로브에 영향이 없습니다(DriveAPI/ENV_VARIABLES.md §5).
Drive의 DRIVE_SUPER_ADMIN_EXTERNAL_API_KEY와 Developers의 비밀 설정에 같은 키를 제공합니다.
initContainer에는 이 키가 필요 없습니다. API Key 호출 경로는 /api/public/v1/super-admin/...입니다.

Developers 가입 준비 작업은 테넌트, 한 계정의 ADMIN/USER 역할, 역할별 API Key,
서비스가 관리하는 Office 연결, TFO HTTP Storage 어댑터를 단계별로 준비합니다.
TFO 티켓 발급용 서비스 토큰과 TFO 관리자 로그인은 서로 다른 인증정보입니다.
Provider Base URL은 TFO가 접근할 수 있는 Drive BE 주소 + /t/{slug}/api/v1 입니다.
TFO가 /tfo-http-storage/v1을 추가하므로 Base URL에 이 경로를 중복 입력하지 마세요.

## 변경 사항을 배포에 반영하는 순서

1. Drive 담당자는 이 저장소의 원본 SQL 또는 bootstrap 스크립트를 수정합니다.
2. Developers 담당자는 `sync-drive-bootstrap.py`로 Helm chart의 `files/` 사본과 체크섬을 갱신합니다.
3. 담당자는 로컬 초기화 테스트와 `--check`, Helm lint 및 template 검증을 수행합니다.
4. 담당자는 Drive 원본 변경과 Developers chart 변경을 함께 검토하고 각 저장소에 반영합니다.
5. 배포 담당자가 갱신한 chart를 배포하면 Helm이 새 ConfigMap을 만들고 Deployment가 해당 이름을 참조합니다.
6. Kubernetes는 새 Pod의 initContainer를 먼저 실행합니다. 초기화 확인에 성공한 경우에만 Drive WAS를 시작합니다.

ConfigMap은 실행할 파일을 제공할 뿐, DB 초기화를 직접 실행하지 않습니다.
initContainer가 실패하면 해당 Pod의 WAS는 시작하지 않습니다. 운영자는 initContainer 로그에서 원인을 확인해야 합니다.
이미 초기화된 DB에서는 새 SQL을 넣어 배포해도 기존 테이블과 데이터를 갱신하지 않습니다.
기존 DB의 스키마 변경에는 별도의 마이그레이션이 필요합니다.

## 실제 연동 검증 결과

[2026-11-04 로컬 검증](LOCAL-VERIFICATION-20261104.md)에 가입·키 발급·TFO 등록·문서 편집·저장 결과와 남은 범위를 기록했습니다.
