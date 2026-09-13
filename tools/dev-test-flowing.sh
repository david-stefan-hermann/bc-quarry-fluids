#!/usr/bin/env bash
# Dev test: a single water source placed on a mined layer spreads flowing water; the quarry must take the source,
# ignore the flowing blocks and keep going. Assumes the prepared run/world (quarry at 0,-60,0, area x -4..4, z 2..10).
set -u
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot}"
export TEMP=C:/jtmp TMP=C:/jtmp
R() { "$JAVA_HOME/bin/java" tools/Rcon.java 127.0.0.1 25599 bcqf "$@"; }

( ./gradlew runServer --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 80); do grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break; sleep 3; done

echo "=== setup: mined pit down to y -61 with a stone floor at y -62"
R "forceload add -32 -32 32 32" \
  "fill -4 -62 2 4 -56 10 minecraft:air" \
  "fill -4 -62 2 4 -62 10 minecraft:stone" \
  "fill -4 -63 2 4 -63 10 minecraft:stone" \
  "data merge block 0 -60 0 {hasBoxIterator:0b,hasDrill:0b,currentTaskId:-1b}" | grep -v '^>'
echo "=== wait until the drill works on y -62"
for _ in $(seq 1 40); do
  y=$(R "data get block 0 -60 0 drillY" | grep -o '\-[0-9]*\.[0-9]*d' | head -1)
  echo "drillY=$y"
  case "$y" in -62*|-63*) break;; esac
  sleep 5
done
echo "=== one water source on the mined layer y -58 (it spreads and falls as flowing water)"
R "setblock 0 -58 6 minecraft:water" "data get block 0 -60 0 bcqf_tank" | grep -v '^>'
sleep 20
echo "flowing blocks after 20 s: $(R 'execute if blocks -4 -62 2 4 -56 10 -4 -62 2 all' >/dev/null; R 'fill -4 -62 2 4 -56 10 minecraft:air replace minecraft:water' | tail -1)"
echo "(that fill removed the water for counting; place the source again and wait for the quarry)"
R "setblock 0 -58 6 minecraft:water" | tail -1
sleep 70
echo "=== result"
R "execute if block 0 -58 6 minecraft:water" "data get block 0 -60 0 drillY" "data get block 0 -60 0 bcqf_tank" \
  "fill -4 -62 2 4 -56 10 minecraft:air replace minecraft:water" "fill -4 -62 2 4 -62 10 minecraft:air replace minecraft:stone"
R "stop" | tail -1
wait
