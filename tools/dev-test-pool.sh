#!/usr/bin/env bash
# Dev test: an "infinite water" pool (7 x 7 sources) on a mined layer must be pumped away in one go.
# Assumes the prepared run/world (quarry at 0,-60,0, area x -4..4, z 2..10).
set -u
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot}"
export TEMP=C:/jtmp TMP=C:/jtmp
R() { "$JAVA_HOME/bin/java" tools/Rcon.java 127.0.0.1 25599 bcqf "$@"; }

( ./gradlew runServer --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 80); do grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break; sleep 3; done

echo "=== setup: empty tank, mined pit down to y -61, stone floor at -62/-63"
R "forceload add -32 -32 32 32" \
  "fill -4 -62 2 4 -56 10 minecraft:air" \
  "fill -4 -62 2 4 -62 10 minecraft:stone" \
  "fill -4 -63 2 4 -63 10 minecraft:stone" \
  'data merge block 0 -60 0 {hasBoxIterator:0b,hasDrill:0b,currentTaskId:-1b,bcqf_tank:[{amount:0L,variant:{fluid:"minecraft:empty"}},{amount:0L,variant:{fluid:"minecraft:empty"}},{amount:0L,variant:{fluid:"minecraft:empty"}},{amount:0L,variant:{fluid:"minecraft:empty"}}]}' | grep -v '^>'
echo "=== wait until the drill works on y -62"
for _ in $(seq 1 40); do
  y=$(R "data get block 0 -60 0 drillY" | grep -o '\-[0-9]*\.[0-9]*d' | head -1)
  echo "drillY=$y"
  case "$y" in -62*|-63*) break;; esac
  sleep 5
done
echo "=== 7 x 7 water sources on the mined layer y -58 (49 sources, all connected)"
R "fill -3 -58 3 3 -58 9 minecraft:water" | tail -1
sleep 60
echo "=== result (expect: no water left, 49 buckets = 3969000 droplets in the tank)"
R "fill -4 -62 2 4 -56 10 minecraft:air replace minecraft:water" "data get block 0 -60 0 bcqf_tank" "data get block 0 -60 0 drillY"
echo "=== second pool with a nearly full tank: only 15 buckets of room"
R 'data merge block 0 -60 0 {bcqf_tank:[{amount:1215000L,variant:{fluid:"minecraft:water"}},{amount:1296000L,variant:{fluid:"minecraft:water"}},{amount:1296000L,variant:{fluid:"minecraft:water"}},{amount:1296000L,variant:{fluid:"minecraft:water"}}]}' "fill -3 -58 3 3 -58 9 minecraft:water" | tail -1
sleep 45
echo "=== result (expect: 15 removed then waiting; pool refilled itself, tank full = 5184000)"
R "data get block 0 -60 0 bcqf_tank" "data get block 0 -60 0 currentTaskId" "fill -4 -62 2 4 -56 10 minecraft:air replace minecraft:water"
R "stop" | tail -1
wait
