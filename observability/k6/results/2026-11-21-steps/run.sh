#!/bin/bash
# 이 실행에 쓴 스크립트 원본(기록용). 로컬 설치 환경(C:/loadtest-tools, Docker 없음) 기준이라 그대로는 다른 PC 에서 돌지 않는다.
#   pin.ps1 <명령줄 일부|postgres> <마스크>: 해당 프로세스의 ProcessorAffinity 를 바꾼다(127 = 코어 0~6, 896 = 코어 7~9)
#   cpu.ps1: java·postgres 프로세스의 누적 TotalProcessorTime 합(초)을 "java postgres" 로 출력
# 출력: k6-vu<N>.out(k6 요약), sample-vu<N>.log(5초마다 /actuator/prometheus), cpu.txt("VU java전 pg전 java후 pg후 걸린초")
# 단계마다 따로 k6 를 돌려 단계별 k6 요약 + WAS/PG CPU(초) + 5초 샘플(힙, hikari, busy, process cpu)을 남긴다
# k6 는 코어 7~9, WAS·PG 는 코어 0~6 에 고정
OUT=/c/loadtest-tools/steps-$(date +%m%d-%H%M)
mkdir -p $OUT
T=/c/loadtest-tools
K6=$T/x_k6-v1.2.0-windows-amd64/k6-v1.2.0-windows-amd64/k6.exe
SCRIPT=C:/drive/observability/k6/volume/steps.js
powershell -NoProfile -ExecutionPolicy Bypass -File $T/pin.ps1 app-te6.jar 127 > $OUT/pin.txt
powershell -NoProfile -ExecutionPolicy Bypass -File $T/pin.ps1 postgres 127 >> $OUT/pin.txt
BASE_URL=http://localhost:8082 LEVELS=30 DUR=60 TESTID=steps-warmup $K6 run --quiet --no-color $SCRIPT > $OUT/k6-warmup.out 2>&1; sleep 10
for v in ${LEVELS:-10 25 50 100 150 200 300 400}; do
  ( while true; do
      m=$(curl -s -m 3 localhost:8091/actuator/prometheus)
      h=$(echo "$m" | awk '/^jvm_memory_used_bytes\{.*area="heap"/ {s+=$NF} END{printf "%.0f", s/1048576}')
      g=$(echo "$m" | awk '/^jvm_gc_pause_seconds_sum/ {s+=$NF} END{printf "%.3f", s}')
      b=$(echo "$m" | awk '/^tomcat_threads_busy/ {print $NF}')
      a=$(echo "$m" | awk '/^hikaricp_connections_active/ {print $NF}')
      p=$(echo "$m" | awk '/^hikaricp_connections_pending/ {print $NF}')
      c=$(echo "$m" | awk '/^process_cpu_usage/ {print $NF}')
      echo "$(date +%T) heap=$h gc=$g busy=$b active=$a pending=$p cpu=$c"; sleep 5
    done ) > $OUT/sample-vu$v.log 2>&1 &
  SP=$!
  a=$(powershell -NoProfile -ExecutionPolicy Bypass -File $T/cpu.ps1); t0=$(date +%s)
  BASE_URL=http://localhost:8082 LEVELS=$v DUR=60 TESTID=steps-vu$v $K6 run --quiet --no-color $SCRIPT > $OUT/k6-vu$v.out 2>&1 &
  KP=$!; sleep 2
  powershell -NoProfile -ExecutionPolicy Bypass -File $T/pin.ps1 steps.js 896 >> $OUT/pin.txt
  wait $KP
  b=$(powershell -NoProfile -ExecutionPolicy Bypass -File $T/cpu.ps1); t1=$(date +%s)
  kill $SP 2>/dev/null
  echo "$v $a $b $((t1-t0))" >> $OUT/cpu.txt
  sleep 10
done
powershell -NoProfile -Command "Get-Process java,postgres | % { \$_.ProcessorAffinity = [IntPtr]1023 }"
echo DONE $OUT
