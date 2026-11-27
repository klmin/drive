#!/bin/bash
# k6(docker run, compose 없이) + 2초 서버 지표 샘플링. 요약은 stdout 과 --summary-export JSON 둘 다 남긴다
OUT=${OUT:?OUT}
LABEL=$1; SCRIPT=$2; shift 2
mkdir -p $OUT
( while true; do
    m=$(curl -s -m 2 localhost:8191/actuator/prometheus)
    g() { echo "$m" | awk -v p="$1" -v f="$2" '(index($0,p"{")==1 || index($0,p" ")==1) && (f=="" || index($0,f)) {s+=$NF; n++} END{if(n) printf "%.4g", s; else printf "-"}'; }
    echo "$(date +%T) busy=$(g tomcat_threads_busy_threads) cur=$(g tomcat_threads_current_threads) upload=$(g drive_upload_body_active) uprej=$(g drive_upload_body_rejected_total) hikari=$(g hikaricp_connections_active)/$(g hikaricp_connections_pending) leased=$(g drive_s3_http_leased_max) pending=$(g drive_s3_http_pending_max) acqto=$(g drive_s3_http_acquire_timeout_total) cpu=$(g process_cpu_usage) heap=$(echo "$m" | awk '/^jvm_memory_used_bytes\{.*area="heap"/ {s+=$NF} END{printf "%.0f", s/1048576}')"
    sleep 2
  done ) > $OUT/sample-$LABEL.log 2>&1 &
SP=$!
W=$(cygpath -w /c/intelliJProject/wt-upload-limit/observability/k6); O=$(cygpath -w $OUT)
MSYS_NO_PATHCONV=1 timeout 900 docker run --rm --name k6-$LABEL --add-host host.docker.internal:host-gateway -v "$W:/scripts:ro" -v "$O:/out" grafana/k6:1.2.0 run --quiet --no-color -e BASE_URL=http://host.docker.internal:8182 -e TESTID=$LABEL --summary-export /out/summary-$LABEL.json "$@" /scripts/$SCRIPT > $OUT/k6-$LABEL.out 2>&1
echo "exit=$?" >> $OUT/k6-$LABEL.out
kill $SP 2>/dev/null
echo "$LABEL done $(date +%T)"
