#!/bin/bash

source ${HOME}/bin/env.sh
#source /root/bin/env.sh

if [ -n "$PID" ]; then
  echo "[$DEPLOY_JAR] is currently running with PID : [$PID]"
else
  echo "[$DEPLOY_JAR] process not found"
fi

exit 0
