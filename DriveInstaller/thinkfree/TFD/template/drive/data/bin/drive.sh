#!/bin/bash

source ${HOME}/.bash_profile
source ./env.sh
#source /root/.bash_profile
#source /root/bin/env.sh

log() {
  echo "[$(date +'%Y-%m-%d %H:%M:%S')] - $1"
}

#if [ "$(whoami)" != "$DRIVE_USER" ]; then
#  echo "This script must be run as [$DRIVE_USER] user!"
#  exit 1
#fi

stop_drive() {

  if [ -n "$PID" ]; then
    kill -15 $PID
    echo "Stopped DRIVE with PID : [$PID]"
  else
    echo "Already Stopped DRIVE"
  fi

}

start(){
  if [ "$RUN_MODE" = "container" ]; then
    java -jar "$DEPLOY_JAR" --spring.profiles.active=$PROFILE
  else
    nohup java -jar "$DEPLOY_JAR" --spring.profiles.active=$PROFILE > /dev/null 2>&1 &
    echo "Started [$DEPLOY_JAR], profile : [$PROFILE], LOG_PATH : [$LOG_PATH], ERROR_LOG_PATH : [$ERROR_LOG_PATH], DB : [$DRIVE_DATABASE_HOST:$DRIVE_DATABASE_PORT/$DRIVE_DATABASE_NAME]"
  fi
}  

  #nohup java -jar $DEPLOY_JAR --spring.profiles.active=$PROFILE > /dev/null 2>&1 &
  #java -jar $DEPLOY_JAR --spring.profiles.active=$PROFILE
  #echo "Started [$DEPLOY_JAR], profile : [$PROFILE], LOG_PATH : [$LOG_PATH], ERROR_LOG_PATH : [$ERROR_LOG_PATH]"

#}

deploy_drive() {

  if [ -z "$DOWNLOAD_JAR" ]; then
    echo "No JAR file found in $DOWNLOAD_DIR"
    exit 1
  fi

  stop_drive

  sleep 5

  if [ -n "$DEPLOY_JAR" ]; then
    echo "Backing up current jar [$DEPLOY_JAR] to [$BACKUP_DIR]"
    mv $DEPLOY_JAR $BACKUP_DIR/
  else
    echo "No JAR file found in [$DEPLOY_DIR] to backup"
  fi

  echo "Deploying new jar from [$DOWNLOAD_JAR] to [$DEPLOY_DIR]"
  cp $DOWNLOAD_JAR $DEPLOY_DIR

  DEPLOY_JAR=$(ls -t $DEPLOY_DIR/Thinkfree-Drive-*.jar | head -n 1)

  start

}

start_drive() {

  if [ -n "$PID" ]; then
    echo "Application is already running with PID : [$PID]"
    exit 1
  fi

  if [ -z "$DEPLOY_JAR" ]; then
    echo "No JAR file found in [$DEPLOY_DIR]"
    exit 1
  fi

  start

}

case "$1" in
  deploy)
    log "Deploying Drive"
    deploy_drive
    ;;
  start)
    log "Starting Drive"
    start_drive
    ;;
  stop)
    log "Stopping Drive"
    stop_drive
    ;;
  *)
    log "Usage: $0 {deploy|start|stop}"
    exit 1
esac

exit 0

