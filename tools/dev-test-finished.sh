#!/usr/bin/env bash
# Dev test: a finished quarry (nothing left to mine) must still pump a pool placed into the pit.
set -u
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot}"
export TEMP=C:/jtmp TMP=C:/jtmp
R() { "$JAVA_HOME/bin/java" tools/Rcon.java 127.0.0.1 25599 bcqf "$@"; }

( ./gradlew runServer -PdevDebug --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 80); do grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break; sleep 3; done

echo "=== setup: pit completely empty (air down to bedrock), fresh iterator, empty tank"
R "forceload add -32 -32 32 32" \
  "fill -4 -63 2 4 -56 10 minecraft:air" \
  "data merge block 0 -60 0 {hasBoxIterator:0b,hasDrill:0b,currentTaskId:-1b,bcqf_tank:${TANK:-[{amount:0L,variant:{fluid:\"minecraft:empty\"}},{amount:0L,variant:{fluid:\"minecraft:empty\"}},{amount:0L,variant:{fluid:\"minecraft:empty\"}},{amount:0L,variant:{fluid:\"minecraft:empty\"}}]}}" | grep -v "^>"
sleep 15
echo "=== state after 15 s (iterator should be exhausted)"
R "data get block 0 -60 0 hasBoxIterator" "data get block 0 -60 0 currentTaskId" "data get block 0 -60 0 hasDrill" "data get block 0 -60 0 drillY" | grep -v '^>'
echo "=== 3 x 3 pool at y -58"
R "fill -1 -58 5 1 -58 7 minecraft:water" | tail -1
for i in 1 2 3 4 5 6; do
  sleep 10
  echo "--- t=$((i*10))s"
  R "data get block 0 -60 0 currentTaskId" "data get block 0 -60 0 drillY" "data get block 0 -60 0 bcqf_tank" "execute if block 0 -58 6 minecraft:water" | grep -v '^>'
done
echo "=== log lines"
grep '\[bcqf\]' run/logs/latest.log | tail -12
R "stop" | tail -1
wait
