#!/usr/bin/env bash
# Starts the dev server, joins it with the dev client, lets the mod open the quarry menu and save
# run/screenshots/bcqf-quarry.png, then stops both. Needs the world prepared in run/world (quarry at 0,-60,0).
set -u
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot}"
export TEMP=C:/jtmp TMP=C:/jtmp
RCON="$JAVA_HOME/bin/java tools/Rcon.java 127.0.0.1 25599 bcqf"

rm -f run/screenshots/bcqf-quarry.png
( ./gradlew runServer -PdevScreenshot --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 80); do
  grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break
  sleep 3
done
$RCON "time set day" "weather clear" | tail -1
./gradlew runClient -PdevScreenshot --no-daemon -q > run/runClient.out 2>&1
echo "client exit $?"
ls -la run/screenshots
$RCON "stop" | tail -1
wait
