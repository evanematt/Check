#!/usr/bin/env bash
# Dedicated server smoke test on one fresh world, two sessions:
#  1. boots the dev server, loads both custom dimensions through the normal data pack path, validates the generated
#     structures with /lewandivka validate, moves the campaign to a late step, saves and stops;
#  2. boots the same world again: the campaign must still be at that step ("survives a server restart") and the
#     structures, now read back from the region files, must validate again.
# Usage (from the lewandivka directory):  tools/ci/smoke.sh ../logs/server.txt
set -u
LOG="${1:-server.log}"
LOG2="${LOG%.txt}-restart.txt"
RUN=run/server
STEP=rg_wings
rm -rf "$RUN/world"
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

# session <log> <commands...>: boots the server, runs the commands over rcon (the last one stops it), waits for it to end
session() {
  local log="$1"
  shift
  ./gradlew runServer --console=plain > "$log" 2>&1 &
  local pid=$!
  for i in $(seq 1 420); do
    if grep -q "Done (" "$log" 2>/dev/null; then break; fi
    if ! kill -0 "$pid" 2>/dev/null; then echo "server process ended before it finished starting"; break; fi
    sleep 1
  done
  if grep -q "Done (" "$log"; then
    python3 tools/ci/rcon.py 127.0.0.1 25575 ci-smoke "$@" | tee "$log.rcon"
  else
    echo "SMOKE: the server did not finish starting ($log)"
    kill "$pid" 2>/dev/null
  fi
  wait "$pid" 2>/dev/null
  echo "SMOKE-EXIT $? ($log)"
}

session "$LOG" \
  "lewandivka status" \
  "execute in lewandivka:district run forceload add 0 0 31 31" \
  "execute in lewandivka:chromandivka run forceload add 0 0 31 31" \
  "lewandivka validate" \
  "lewandivka step $STEP" \
  "lewandivka status" \
  "save-all flush" \
  "stop"

echo "=== restart on the same world ==="
session "$LOG2" \
  "lewandivka status" \
  "execute in lewandivka:district run forceload add 0 0 31 31" \
  "execute in lewandivka:chromandivka run forceload add 0 0 31 31" \
  "lewandivka validate" \
  "stop"

ok=1
grep -q "validate: OK" "$LOG.rcon" 2>/dev/null || { echo "SMOKE: first session did not validate"; ok=0; }
grep -q "validate: OK" "$LOG2.rcon" 2>/dev/null || { echo "SMOKE: the restarted server did not validate"; ok=0; }
# the first status line of the second session is the one printed right after the restart
if head -n 3 "$LOG2.rcon" 2>/dev/null | grep -q "step=$STEP"; then
  echo "SMOKE: the campaign step survived the restart ($STEP)"
else
  echo "SMOKE: the campaign step did NOT survive the restart"
  ok=0
fi
if [ "$ok" = 1 ]; then echo "SMOKE-RESULT OK"; else echo "SMOKE-RESULT FAILED"; exit 1; fi
