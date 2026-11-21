#!/bin/bash
# k6 스크립트 하나를 돌리면서 2초마다 서버 지표를 샘플링한다(기록용 원본, 로컬 설치 환경 기준).
#   사용: run-k6.sh <라벨> <스크립트> [k6 -e 인자...]   예) run-k6.sh after-upload upload.js -e PROFILE=stress -e THINK=0
# 출력: k6-<라벨>.out(k6 요약), sample-<라벨>.log(Tomcat busy·스트림·S3 풀·CPU·힙)
OUT=${OUT:-/c/drive/observability/k6/results/2026-11-22-s3-pool}
LABEL=$1; SCRIPT=$2; shift 2
K6=/c/loadtest-tools/x_k6-v1.2.0-windows-amd64/k6-v1.2.0-windows-amd64/k6.exe
mkdir -p $OUT
( while true; do
    m=$(curl -s -m 2 localhost:8091/actuator/prometheus)
    g() { echo "$m" | awk -v p="$1" -v f="$2" '(index($0,p"{")==1 || index($0,p" ")==1) && (f=="" || index($0,f)) {s+=$NF; n++} END{if(n) printf "%.4g", s; else printf "-"}'; }  # g <지표 이름> [라벨 조각]
    echo "$(date +%T) busy=$(g tomcat_threads_busy_threads) cur=$(g tomcat_threads_current_threads) stream=$(g executor_active_threads 'name="driveStreamExecutor"') hikari=$(g hikaricp_connections_active)/$(g hikaricp_connections_pending) leased=$(g drive_s3_http_leased_max) pending=$(g drive_s3_http_pending_max) acqto=$(g drive_s3_http_acquire_timeout_total) cpu=$(g process_cpu_usage) heap=$(echo "$m" | awk '/^jvm_memory_used_bytes\{.*area="heap"/ {s+=$NF} END{printf "%.0f", s/1048576}')"
    sleep 2
  done ) > $OUT/sample-$LABEL.log 2>&1 &
SP=$!
cd /c/drive/observability/k6
MSYS_NO_PATHCONV=1 $K6 run --quiet --no-color -e BASE_URL=http://localhost:8082 -e TESTID=$LABEL "$@" $SCRIPT > $OUT/k6-$LABEL.out 2>&1
kill $SP 2>/dev/null
echo "$LABEL done $(date +%T)"
