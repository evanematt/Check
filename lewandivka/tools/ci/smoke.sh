#!/usr/bin/env bash
# Dedicated server smoke test: boots the dev server with a fresh world, loads both custom dimensions through the
# normal data pack path, validates the generated structures with /lewandivka validate and stops the server.
# Usage (from the lewandivka directory):  tools/ci/smoke.sh ../logs/server.txt
set -u
LOG="${1:-server.log}"
RUN=run/server
mkdir -p "$RUN"
echo "eula=true" > "$RUN/eula.txt"
cat > "$RUN/server.properties" <<PROPS
online-mode=false
enable-rcon=true
rcon.port=25575
rcon.password=ci-smoke
broadcast-rcon-to-ops=false
view-distance=3
simulation-distance=3
max-tick-time=-1
spawn-protection=0
level-seed=1
motd=lewandivka smoke test
PROPS
./gradlew runServer --console=plain > "$LOG" 2>&1 &
GRADLE_PID=$!
for i in $(seq 1 420); do
  if grep -q "Done (" "$LOG" 2>/dev/null; then break; fi
  if ! kill -0 "$GRADLE_PID" 2>/dev/null; then echo "server process ended before it finished starting"; break; fi
  sleep 1
done
if grep -q "Done (" "$LOG"; then
  python3 tools/ci/rcon.py 127.0.0.1 25575 ci-smoke \
    "lewandivka status" \
    "execute in lewandivka:district run forceload add 0 0 31 31" \
    "execute in lewandivka:chromandivka run forceload add 0 0 31 31" \
    "lewandivka validate" \
    "lewandivka status" \
    "stop" | tee "$LOG.rcon"
else
  echo "SMOKE: the server did not finish starting"
  kill "$GRADLE_PID" 2>/dev/null
fi
wait "$GRADLE_PID" 2>/dev/null
echo "SMOKE-EXIT $?"
if grep -q "validate: OK" "$LOG.rcon" 2>/dev/null; then echo "SMOKE-RESULT OK"; else echo "SMOKE-RESULT FAILED"; exit 1; fi
