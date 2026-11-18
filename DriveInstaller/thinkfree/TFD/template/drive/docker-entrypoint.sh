#!/usr/bin/env bash
set -e

BIN_DIR="/root/bin"
SERVICE_DIR="/root/service"
LOG_DIR="/var/log/drive"

mkdir -p "${LOG_DIR}/error"

# 환경 변수 로드
source "${BIN_DIR}/env.sh"

# 런모드 설정
export RUN_MODE=container
BACKEND_PORT="${BACKEND_PORT:-8080}"

echo "[INFO] Starting Thinkfree Drive service on port ${BACKEND_PORT}..."

# 단일 서비스 실행 (jar 또는 drive.sh 통해 실행)
"${BIN_DIR}/drive.sh" start \
  >> "${LOG_DIR}/service.log" \
  2>> "${LOG_DIR}/error/service-error.log" &

APP_PID=$!

# 종료 신호 처리
term() {
  echo "[INFO] Stopping Thinkfree Drive service (PID ${APP_PID})..."
  kill -TERM "${APP_PID}" 2>/dev/null || true
  wait "${APP_PID}" 2>/dev/null || true
}
trap term SIGTERM SIGINT

# 메인 프로세스 대기
wait "${APP_PID}"
