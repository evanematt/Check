#!/usr/bin/env bash
# Boots the finished server pack zip (third-party server mods + lewandivka.jar + the Fabric launcher) and checks over rcon
# that the campaign and both dimensions work. Usage: serverpack-smoke.sh <zip> <log file> [path to rcon.py]
set -u
ZIP="$(readlink -f "$1")"
LOG="$(readlink -f "$2")"
RCON="$(readlink -f "${3:-tools/ci/rcon.py}")"
DIR="$(mktemp -d)"
unzip -q "$ZIP" -d "$DIR" || { echo "SERVERPACK-RESULT FAILED: cannot unzip"; exit 1; }
cd "$DIR"/*/ || exit 1
echo "mods in the pack:"; ls mods
echo "eula=true" > eula.txt
sed -i 's/^enable-rcon=false/enable-rcon=true\nrcon.port=25577\nrcon.password=ci-pack/' server.properties
echo "level-seed=1" >> server.properties
java -Xmx2G -jar fabric-server-launch.jar nogui > "$LOG" 2>&1 &
PID=$!
for i in $(seq 1 600); do
  grep -q "Done (" "$LOG" 2>/dev/null && break
  kill -0 "$PID" 2>/dev/null || { echo "server ended early"; break; }
  sleep 1
done
if grep -q "Done (" "$LOG"; then
  python3 "$RCON" 127.0.0.1 25577 ci-pack \
    "lewandivka status" \
    "execute in lewandivka:district run forceload add 0 0 31 31" \
    "execute in lewandivka:chromandivka run forceload add 0 0 31 31" \
    "lewandivka validate" \
    "stop" | tee "$LOG.rcon"
else
  echo "SERVERPACK: the server did not finish starting"
  kill "$PID" 2>/dev/null
fi
wait "$PID" 2>/dev/null
if grep -q "validate: OK" "$LOG.rcon" 2>/dev/null; then echo "SERVERPACK-RESULT OK"; else echo "SERVERPACK-RESULT FAILED"; exit 1; fi
