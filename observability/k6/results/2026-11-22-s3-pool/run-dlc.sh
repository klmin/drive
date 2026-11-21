#!/bin/bash
# 동시 다운로드 단계 측정(기록용 원본). 로컬 설치 환경(C:/loadtest-tools, Docker 없음) 기준.
#   사용: run-dlc.sh <라벨> <VU 단계...>     예) run-dlc.sh before 40 60 100
# 단계마다 k6 를 따로 돌려 k6 요약(k6-<라벨>-vu<N>.out)과 2초 샘플(sample-<라벨>-vu<N>.log)을 남긴다.
# 샘플: Tomcat busy, 다운로드 스트림 사용 중·거절 누계, S3 GetObject 누계·초 합(응답 헤더까지),
#       S3 풀 빌린 연결 최대·대기 최대·대기 초과 누계(수정 후 jar 만), 프로세스 CPU, 힙(MB), 받은 바이트(호스트 NIC 아님 — k6 요약 참고)
OUT=${OUT:-/c/drive/observability/k6/results/2026-11-22-s3-pool}
LABEL=$1; shift
K6=/c/loadtest-tools/x_k6-v1.2.0-windows-amd64/k6-v1.2.0-windows-amd64/k6.exe
SCRIPT=C:/drive/observability/k6/download-concurrency.js
DUR=${DUR:-90s}
mkdir -p $OUT
for v in "$@"; do
  ( while true; do
      m=$(curl -s -m 2 localhost:8091/actuator/prometheus)
      g() { echo "$m" | awk -v p="$1" -v f="$2" '(index($0,p"{")==1 || index($0,p" ")==1) && (f=="" || index($0,f)) {s+=$NF; n++} END{if(n) printf "%.4g", s; else printf "-"}'; }  # g <지표 이름> [라벨 조각]
      echo "$(date +%T) busy=$(g tomcat_threads_busy_threads) stream=$(g executor_active_threads 'name="driveStreamExecutor"') rej=$(g drive_download_stream_rejected_total) s3get=$(g aws_s3_client_seconds_count 'rpc_method="GetObject"') leased=$(g drive_s3_http_leased_max) pending=$(g drive_s3_http_pending_max) acqto=$(g drive_s3_http_acquire_timeout_total) cpu=$(g process_cpu_usage) heap=$(echo "$m" | awk '/^jvm_memory_used_bytes\{.*area="heap"/ {s+=$NF} END{printf "%.0f", s/1048576}')"
      sleep 2
    done ) > $OUT/sample-$LABEL-vu$v.log 2>&1 &
  SP=$!
  MSYS_NO_PATHCONV=1 $K6 run --quiet --no-color -e BASE_URL=http://localhost:8082 -e VUS=$v -e DUR=$DUR -e TESTID=dlc-$LABEL-vu$v $SCRIPT > $OUT/k6-$LABEL-vu$v.out 2>&1
  kill $SP 2>/dev/null
  echo "$LABEL vu$v done $(date +%T)"
  sleep 15
done
