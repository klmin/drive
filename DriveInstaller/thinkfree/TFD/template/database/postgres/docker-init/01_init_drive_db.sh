#!/bin/bash
# =====================================================================================
# PostgreSQL 공식 이미지 최초 기동 훅(/docker-entrypoint-initdb.d) — 데이터 디렉터리가 비어 있을 때 한 번만 실행된다.
#   슈퍼유저(POSTGRES_USER)로 앱 계정·DB 만 만든다.
#   테이블·기초 데이터는 Drive 앱이 기동할 때 Flyway(DriveAPI db/migration)가 만든다 — 여기서 initialize.sql 을 실행하지 않는다.
#   라이선스·office 연결 값은 앱의 InitialSetupRunner 가 넣는다(env.sh 의 DRIVE_INITIAL_*).
# 변수는 template/database/postgres/.env (DRIVE_DB_NAME·DRIVE_DB_USER·DRIVE_DB_PASSWORD).
# =====================================================================================
set -euo pipefail

: "${DRIVE_DB_NAME:?DRIVE_DB_NAME 이 비어 있다}"
: "${DRIVE_DB_USER:?DRIVE_DB_USER 이 비어 있다}"
: "${DRIVE_DB_PASSWORD:?DRIVE_DB_PASSWORD 이 비어 있다}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
     -v drive_db="$DRIVE_DB_NAME" -v drive_user="$DRIVE_DB_USER" -v drive_password="$DRIVE_DB_PASSWORD" <<'SQL'
CREATE ROLE :"drive_user" LOGIN PASSWORD :'drive_password';
CREATE DATABASE :"drive_db" OWNER :"drive_user" ENCODING 'UTF8';
SQL
