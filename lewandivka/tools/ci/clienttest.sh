#!/usr/bin/env bash
# Real client test: a dedicated server (fresh world, rcon) and a software-rendered client under Xvfb that joins it with
# --quickPlayMultiplayer. The in-game AutoTest (LEWANDIVKA_AUTOTEST=1) tours both dimensions, shows every entity and block,
# opens the screens and takes screenshots into run/client/screenshots.
# Usage (from the lewandivka directory):  tools/ci/clienttest.sh ../logs
set -u
OUT="${1:-../logs}"
mkdir -p "$OUT"
SERVER_LOG="$OUT/clienttest-server.txt"
CLIENT_LOG="$OUT/clienttest-client.txt"
SRV=run/server
CLI=run/client
rm -rf "$SRV/world" "$CLI/screenshots" "$CLI/autotest-result.txt"
mkdir -p "$SRV" "$CLI"
echo "eula=true" > "$SRV/eula.txt"
UUID=$(python3 - <<'PY'
import hashlib, uuid
d = bytearray(hashlib.md5(b"OfflinePlayer:Tester").digest())
d[6] = (d[6] & 0x0f) | 0x30
d[8] = (d[8] & 0x3f) | 0x80
print(uuid.UUID(bytes=bytes(d)))
PY
)
echo "[{\"uuid\":\"$UUID\",\"name\":\"Tester\",\"level\":4,\"bypassesPlayerLimit\":true}]" > "$SRV/ops.json"
cat > "$SRV/server.properties" <<PROPS
online-mode=false
enable-rcon=true
rcon.port=25576
rcon.password=ci-client
broadcast-rcon-to-ops=false
view-distance=6
simulation-distance=5
max-tick-time=-1
spawn-protection=0
level-seed=1
gamemode=creative
difficulty=normal
server-port=25566
motd=lewandivka client test
PROPS
cat > "$CLI/options.txt" <<OPTS
version:3465
renderDistance:6
simulationDistance:5
maxFps:30
guiScale:2
lang:en_us
onboardAccessibility:false
tutorialStep:none
soundCategory_master:0.0
fullscreen:false
enableVsync:false
graphicsMode:1
particles:1
OPTS
./gradlew runServer --console=plain > "$SERVER_LOG" 2>&1 &
SERVER_PID=$!
for i in $(seq 1 420); do
  grep -q "Done (" "$SERVER_LOG" 2>/dev/null && break
  kill -0 "$SERVER_PID" 2>/dev/null || { echo "server ended early"; break; }
  sleep 1
done
if ! grep -q "Done (" "$SERVER_LOG"; then
  echo "CLIENTTEST-RESULT FAILED: the server did not start"
  kill "$SERVER_PID" 2>/dev/null
  exit 1
fi
python3 tools/ci/gallery.py > "$OUT/gallery-commands.txt"
mapfile -t CMDS < "$OUT/gallery-commands.txt"
python3 tools/ci/rcon.py 127.0.0.1 25576 ci-client "${CMDS[@]}" > "$OUT/gallery-rcon.txt" 2>&1
echo "gallery built: $(grep -c . "$OUT/gallery-rcon.txt") rcon lines"

export LEWANDIVKA_AUTOTEST=1
export LIBGL_ALWAYS_SOFTWARE=1
export MESA_GL_VERSION_OVERRIDE=4.5
export MESA_GLSL_VERSION_OVERRIDE=450
timeout 900 xvfb-run -a -s "-screen 0 1280x720x24" \
  ./gradlew runClient --console=plain --args="--username Tester --width 1280 --height 720 --quickPlayMultiplayer 127.0.0.1:25566" \
  > "$CLIENT_LOG" 2>&1
CLIENT_EXIT=$?
echo "client exit code $CLIENT_EXIT"
python3 tools/ci/rcon.py 127.0.0.1 25576 ci-client "stop" > /dev/null 2>&1
wait "$SERVER_PID" 2>/dev/null
mkdir -p "$OUT/shots"
cp "$CLI"/screenshots/*.png "$OUT/shots/" 2>/dev/null
cp "$CLI/autotest-result.txt" "$OUT/autotest-result.txt" 2>/dev/null
echo "screenshots: $(ls "$OUT/shots" 2>/dev/null | wc -l)"
if grep -q "AUTOTEST-RESULT OK" "$OUT/autotest-result.txt" 2>/dev/null; then echo "CLIENTTEST-RESULT OK"; else echo "CLIENTTEST-RESULT FAILED"; exit 1; fi
