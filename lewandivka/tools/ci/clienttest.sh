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
# The furniture of the rooms is made of the blocks of the furniture mod of the pack when it is installed: the test installs it the way
# the pack does (its jar and its library in the mods folders of the development server and client), so that the pictures show it.
# LEWANDIVKA_CI_MODS=none runs the test with the game's own blocks only.
if [ "${LEWANDIVKA_CI_MODS:-furniture}" = "furniture" ]; then
  if python3 tools/ci/fetch_decor.py "$SRV/mods" "$CLI/mods" > "$OUT/clienttest-mods.txt" 2>&1; then
    echo "furniture mod installed: $(tr '\n' ' ' < "$OUT/clienttest-mods.txt")"
  else
    echo "the furniture mod could not be downloaded, the test runs without it:"; cat "$OUT/clienttest-mods.txt"
    rm -rf "$SRV/mods" "$CLI/mods"
  fi
fi
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

# A run takes half an hour: every few minutes what is known so far (the bot's result file, both logs, the screenshots as
# small jpg files) goes to the pre-release ci-latest, so a problem at the start can be read long before the end.
publish_partial() {
  command -v gh >/dev/null 2>&1 || return 0
  [ -n "${GH_TOKEN:-}" ] || return 0
  local dir="$OUT/partial"
  rm -rf "$dir"
  mkdir -p "$dir"
  cp "$CLI/autotest-result.txt" "$dir/autotest-result.txt" 2>/dev/null
  cp "$SERVER_LOG" "$dir/clienttest-server.txt" 2>/dev/null
  cp "$CLIENT_LOG" "$dir/clienttest-client.txt" 2>/dev/null
  python3 - "$CLI/screenshots" "$dir" <<'PY' 2>/dev/null
import sys
from pathlib import Path
try:
    from PIL import Image
except ImportError:
    sys.exit(0)
src, dst = Path(sys.argv[1]), Path(sys.argv[2])
for p in sorted(src.glob("*.png")):
    Image.open(p).convert("RGB").save(dst / (p.stem + ".jpg"), quality=75)
PY
  echo "run=${GITHUB_RUN_NUMBER:-?} sha=${GITHUB_SHA:-?} job-status=running state=partial at=$(date -u +%H:%M:%S)" > "$dir/info-client.txt"
  gh release upload ci-latest $(find "$dir" -type f ! -name 'info-*') --clobber > /dev/null 2>&1 || true
  gh release upload ci-latest "$dir/info-client.txt" --clobber > /dev/null 2>&1 || true
}
( while true; do sleep 240; publish_partial; done ) &
UPLOADER=$!

export LEWANDIVKA_AUTOTEST=1
export LIBGL_ALWAYS_SOFTWARE=1
export MESA_GL_VERSION_OVERRIDE=4.5
export MESA_GLSL_VERSION_OVERRIDE=450
timeout 2400 xvfb-run -a -s "-screen 0 1280x720x24" \
  ./gradlew runClient --console=plain --args="--username Tester --width 1280 --height 720 --quickPlayMultiplayer 127.0.0.1:25566" \
  > "$CLIENT_LOG" 2>&1
CLIENT_EXIT=$?
kill "$UPLOADER" 2>/dev/null
# an upload of the partial publisher that is in flight must be over before the final publication starts
for i in $(seq 1 30); do pgrep -f "gh release upload" >/dev/null || break; sleep 2; done
echo "client exit code $CLIENT_EXIT"
python3 tools/ci/rcon.py 127.0.0.1 25576 ci-client "lewandivka decor" > "$OUT/clienttest-decor.txt" 2>&1
python3 tools/ci/rcon.py 127.0.0.1 25576 ci-client "stop" > /dev/null 2>&1
wait "$SERVER_PID" 2>/dev/null
mkdir -p "$OUT/shots"
cp "$CLI"/screenshots/*.png "$OUT/shots/" 2>/dev/null
cp "$CLI/autotest-result.txt" "$OUT/autotest-result.txt" 2>/dev/null
echo "screenshots: $(ls "$OUT/shots" 2>/dev/null | wc -l)"
# Everything the game logged about the mod is a defect: entities that failed to load, models that could not be baked,
# flows that threw, missing sounds. (The sound device and the narrator do not exist on the CI machine.)
PROBLEMS="$OUT/clienttest-problems.txt"
# The furniture mod of the pack makes the game say "No data fixer registered for <mod>" at the level ERROR when it starts: the line
# is about the mod having no data fixer for old saves, not a defect of anything; only its mod ids are let through here.
NOISE="No data fixer registered for (handcrafted|resourcefullib)"
{
  grep -nE "/ERROR\]|Exception loading entity|Exception ticking|Ticking entity|/WARN\] \(lewandivka\)" "$SERVER_LOG" | grep -vE "$NOISE" | sed 's/^/server: /'
  grep -nE "/ERROR\]|/WARN\] \(lewandivka\)|(Unable to bake model|Exception evaluating model definition|Missing sound for event|Unable to load|Failed to load).*lewandivka" "$CLIENT_LOG" \
    | grep -v "Error starting SoundSystem\|Error while loading the narrator" | grep -vE "$NOISE" | sed 's/^/client: /'
} > "$PROBLEMS" 2>/dev/null
N_PROBLEMS=$(wc -l < "$PROBLEMS" 2>/dev/null | tr -d ' ')
N_PROBLEMS=${N_PROBLEMS:-0}
[ "$N_PROBLEMS" = "0" ] && echo "none: the logs of the server and the client contain no error or warning of the mod" > "$PROBLEMS"
echo "log problems: $N_PROBLEMS"
if [ "$N_PROBLEMS" != "0" ]; then head -40 "$PROBLEMS"; fi
if grep -q "AUTOTEST-RESULT OK" "$OUT/autotest-result.txt" 2>/dev/null && [ "$N_PROBLEMS" = "0" ]; then
  echo "CLIENTTEST-RESULT OK"
else
  echo "CLIENTTEST-RESULT FAILED"
  exit 1
fi
