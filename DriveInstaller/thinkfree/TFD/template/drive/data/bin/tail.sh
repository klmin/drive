#!/bin/bash

source ${HOME}/bin/env.sh
#source /root/bin/env.sh

LOG_FILE="${LOG_PATH}.log"
ERROR_LOG_FILE="${ERROR_LOG_PATH}.log"

all() {
  if [ -f "$LOG_FILE" ]; then
    tail -f $LOG_FILE
  else
    echo "Log file [$LOG_FILE] not found"
  fi
}

error_log() {
  if [ -f "$ERROR_LOG_FILE" ]; then
    tail -f $ERROR_LOG_FILE
  else
    echo "Error log file [$ERROR_LOG_FILE] not found"
  fi
}

if [ "$#" -eq 0 ]; then
  all
elif [ "$#" -eq 1 ]; then
  case "$1" in
    error)
      error_log
      ;;
    *)
      echo "Usage: $0 {error}"
      exit 1
      ;;
  esac
else
  echo "Usage: $0 {error}"
  exit 1
fi
