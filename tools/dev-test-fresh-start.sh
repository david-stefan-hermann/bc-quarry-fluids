#!/usr/bin/env bash
# Dev test: a quarry that has not mined anything yet (fresh boxIterator) must not chase a phantom
# "blocker" at the untouched far corner of the mining box (the 1.0.5 fix).
# Assumes the prepared run/world (quarry at 0,-60,0, area x -4..4, z 2..10).
set -u
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot}"
export TEMP=C:/jtmp TMP=C:/jtmp
R() { "$JAVA_HOME/bin/java" tools/Rcon.java 127.0.0.1 25599 bcqf "$@"; }

( ./gradlew runServer -PdevDebug --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 80); do grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break; sleep 3; done

echo "=== box bounds"
R "data get block 0 -60 0 box_minX" "data get block 0 -60 0 box_minY" "data get block 0 -60 0 box_minZ" \
  "data get block 0 -60 0 box_maxX" "data get block 0 -60 0 box_maxY" "data get block 0 -60 0 box_maxZ" | grep -v '^>'

echo "=== setup: fill the whole box with stone (nothing mined) and reset the iterator/drill like a fresh placement"
R "forceload add -32 -32 32 32" \
  "fill -3 -80 3 3 -56 9 minecraft:stone" \
  "data merge block 0 -60 0 {hasBoxIterator:0b,hasDrill:0b,currentTaskId:-1b}" | grep -v '^>'

echo "=== watch drillX/Y/Z for 30s (should stay near 0,-60,0-ish, never jump to the far box corner)"
for i in $(seq 1 10); do
  sleep 3
  x=$(R "data get block 0 -60 0 drillX" | grep -o '\-\?[0-9]*\.[0-9]*d' | head -1)
  y=$(R "data get block 0 -60 0 drillY" | grep -o '\-\?[0-9]*\.[0-9]*d' | head -1)
  z=$(R "data get block 0 -60 0 drillZ" | grep -o '\-\?[0-9]*\.[0-9]*d' | head -1)
  echo "t=${i}: drill=($x, $y, $z)"
done

echo "=== last 40 debug lines (look for 'blocker found' far from 0,-60,0)"
grep -a 'bcqf' run/runServer.out | tail -40

R "stop" | tail -1
wait
