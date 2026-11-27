#!/bin/bash
source ${HOME}/.bash_profile

export LOG_DIR="/var/log/drive"
export LOG_PATH="$LOG_DIR/service"
export ERROR_LOG_PATH="$LOG_DIR/error/service-error"
export DRIVE_DIR="/root/service"
export DEPLOY_DIR="$DRIVE_DIR"
export PROFILE="prod"
export DRIVE_USER="thinkfree"
export DEPLOY_JAR=$(ls -t $DEPLOY_DIR/Thinkfree-Drive-*.jar | head -n 1)
export PID=$(pgrep -f "$DEPLOY_DIR/Thinkfree-Drive-.*\.jar")
# PostgreSQL (application.yml 의 DRIVE_DATABASE_*). 비밀번호는 template/database/postgres/.env 의 DRIVE_DB_PASSWORD 와 같게.
# setup.sh(S3)·configure_ceph.sh(Ceph) 가 DRIVE_DATABASE_PASSWORD 줄 뒤에 DRIVE_STORAGE_* 블록(application.yml storage.*)을 다시 쓴다.
export DRIVE_DATABASE_HOST="postgres"
export DRIVE_DATABASE_PORT="5432"
export DRIVE_DATABASE_NAME="t_drive"
export DRIVE_DATABASE_USERNAME="t_drive_user"
export DRIVE_DATABASE_PASSWORD='changeme'
# 테이블·기초 데이터는 앱 기동 시 Flyway 가 만든다(빈 DB 면 V1 실행, 이력 있는 DB 면 남은 V 만 실행).
# 테이블은 있는데 Flyway 이력이 없는 DB(예전 initialize.sql 로 설치한 곳)는 false 면 기동 실패한다.
#   그런 곳을 업그레이드할 때만 스키마가 V1 과 같은지 확인한 뒤 첫 기동에 "true" 로 주고, 이후 "false" 로 되돌린다.
export DRIVE_FLYWAY_BASELINE_ON_MIGRATE="false"

# 설치 직후 초기 설정(application.yml drive.initial-setup) — 첫 기동 때 DB 에 없을 때만 넣고, 이미 있으면 유지한다.
# 대상 테넌트 = 활성 자체관리 테넌트가 하나뿐일 때 그 테넌트(첫 설치면 1).
# 라이선스: docker-compose 가 template/license 를 /root/weboffice/license 로 마운트한다(thinkfree.public.key 도 같은 폴더).
#   파일이 없으면 건너뛰고 관리자 화면에서 업로드.
export DRIVE_INITIAL_LICENSE_FILE="/root/weboffice/license/license.tfl"
# office(TFO) 연결: TFO 에 HTTP Storage 어댑터를 만든 뒤 그 값을 넣는다. 주소가 비어 있으면 건너뛴다.
#   관리자 화면과 같은 검증(주소·인증 방식·서명키 32바이트 이상·ticket 이면 토큰)을 통과해야 저장한다 — 하나라도 빠지면 저장 안 함.
export DRIVE_INITIAL_OFFICE_DOMAIN=""
export DRIVE_INITIAL_OFFICE_ADAPTER_NAME=""
export DRIVE_INITIAL_OFFICE_AUTH_MODE="ticket"
export DRIVE_INITIAL_OFFICE_CONNECTION_TYPE="onPremise"
export DRIVE_INITIAL_OFFICE_TICKET_SERVICE_TOKEN=""
export DRIVE_INITIAL_OFFICE_HTTP_STORAGE_REQUEST_JWT_SECRET=""

# Redis(선택) — 기본은 쓰지 않는다. 설치기는 Redis 를 띄우지 않으므로, 쓰려면 Redis 를 따로 준비하고 아래를 켠다.
#   WAS 를 여러 대 둘 때 캐시 무효화·로그아웃 토큰·요청 수 제한을 서버끼리 맞추는 데 쓴다. Redis 가 죽어도 서비스는 계속된다.
#   Redis 는 maxmemory-policy noeviction 으로 둔다(아니면 기동 시 경고). 자세한 항목은 DriveAPI/ENV_VARIABLES.md §5
export DRIVE_REDIS_ENABLED="false"
#export DRIVE_REDIS_HOST="redis"
#export DRIVE_REDIS_PORT="6379"
#export DRIVE_REDIS_PASSWORD=''
#export DRIVE_REDIS_KEY_PREFIX="drive:"

# 서명·암호화 키 (application.yml jwt·smtp.aes·download — 기본값 없음, 빠지면 기동 실패).
# 비워 두면 setup.sh 가 generate_secret_keys.sh 로 설치처마다 새로 만든다(openssl rand -base64 24 → 32글자). 값이 있으면 그대로 둔다.
# SMTP AES 키는 DB 에 저장된 SMTP 비밀번호를 푸는 키라, 기존 DB 가 있으면 새로 만들지 않고 예전 jar 내장값을 넣는다.
# 직접 넣을 때: JWT 32글자 이상, SMTP AES 정확히 16·24·32글자, 다운로드 32글자 이상 권장.
export DRIVE_JWT_SECRET_KEY=''
export DRIVE_SMTP_AES_SECRET_KEY=''
export DRIVE_DOWNLOAD_SECRET_KEY=''

# 감사 로그 Governance 전송 (application.yml 기본 ON). 설치 시 SDK 4개를 반드시 채운다 — 비어 있으면 기동 실패.
# 끄려면 GOVERNANCE_AUDIT_ENABLED="false" (그때는 SDK 값 불필요).
export GOVERNANCE_SDK_ENDPOINT=""
export GOVERNANCE_SDK_TENANT_ID=""
export GOVERNANCE_SDK_KEY_ID=""
export GOVERNANCE_SDK_BASE64_SECRET=""
# 감사 로그에 찍힐 이 설치처 이름 — 설치처마다 구분되게 바꾼다(예: tfd-회사명). 빈 값("")으로 두면 이름 없이 전송된다.
export GOVERNANCE_ORIGIN_INSTANCE_ID="tfd"

echo "============================================="
echo "LOG_PATH=$LOG_PATH"
echo "ERROR_LOG_PATH=$ERROR_LOG_PATH"
echo "DOWNLOAD_DIR=$DOWNLOAD_DIR"
echo "DEPLOY_DIR=$DEPLOY_DIR"
echo "BACKUP_DIR=$BACKUP_DIR"
echo "PROFILE=$PROFILE"
echo "DRIVE_USER=$DRIVE_USER"
echo "DOWNLOAD_JAR=$DOWNLOAD_JAR"
echo "DEPLOY_JAR=$DEPLOY_JAR"
echo "============================================="
