#!/usr/bin/env bash
# Dev test: blocks placed on an already mined layer must be mined before the drill goes deeper.
# Then takes the GUI screenshot. Assumes the prepared run/world (quarry at 0,-60,0, area x -4..4, z 2..10).
set -u
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot}"
export TEMP=C:/jtmp TMP=C:/jtmp
R() { "$JAVA_HOME/bin/java" tools/Rcon.java 127.0.0.1 25599 bcqf "$@"; }

rm -f run/screenshots/bcqf-quarry.png
( ./gradlew runServer -PdevScreenshot --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 80); do grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break; sleep 3; done

echo "=== setup"
R "forceload add -32 -32 32 32" \
  "fill -4 -61 2 4 -56 10 minecraft:air" \
  "fill -4 -61 2 4 -61 10 minecraft:stone" \
  "fill -4 -62 2 4 -62 10 minecraft:dirt" \
  "data merge block 0 -60 0 {hasBoxIterator:0b,hasDrill:0b,currentTaskId:-1b}" | grep -v '^>'
echo "=== wait until the drill works on y -61 or below"
for _ in $(seq 1 40); do
  y=$(R "data get block 0 -60 0 drillY" | grep -o '\-[0-9]*\.[0-9]*d' | head -1)
  echo "drillY=$y"
  case "$y" in -61*|-62*|-63*) break;; esac
  sleep 5
done
echo "=== place three cobblestone blocks on the mined layer y -58"
R "setblock -4 -58 2 minecraft:cobblestone" "setblock 0 -58 6 minecraft:cobblestone" "setblock 4 -58 10 minecraft:cobblestone" | grep -v '^>'
sleep 75
echo "=== result (expect: Test failed = block is gone)"
R "execute if block -4 -58 2 minecraft:cobblestone" "execute if block 0 -58 6 minecraft:cobblestone" "execute if block 4 -58 10 minecraft:cobblestone" "data get block 0 -60 0 drillY" "data get block 0 -60 0 bcqf_items"
echo "=== screenshot"
R "time set day" "weather clear" | tail -1
./gradlew runClient -PdevScreenshot --no-daemon -q > run/runClient.out 2>&1
echo "client exit $?"
ls -la run/screenshots
R "stop" | tail -1
wait
