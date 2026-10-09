#!/usr/bin/env bash
# The second run of the client test: the cars of Trep's Cars. The mod cannot run on a dedicated server (its main class touches a class
# that only the client has), so the cars exist only in a game that plays a world of its own: the client starts a world made from
# the level.dat of the dedicated server (no chunks: the plan is generated again, with the mod in the game) with --quickPlaySingleplayer
# and the bot (LEWANDIVKA_AUTOTEST=cars) checks and photographs the cars.
# Usage (from the lewandivka directory, after the dedicated server of the test has been stopped):  tools/ci/carstest.sh ../logs
set -u
OUT="${1:-../logs}"
SRV=run/server
CLI=run/client
LOG="$OUT/clienttest-cars-client.txt"
RESULT="$OUT/autotest-cars-result.txt"
mkdir -p "$OUT" "$CLI/saves"
rm -rf "$CLI/saves/ciworld" "$CLI/screenshots" "$CLI/autotest-result.txt" "$RESULT"
if [ ! -f "$SRV/world/level.dat" ]; then
  echo "CARSTEST-RESULT FAILED: no level.dat to start a world from"
  exit 1
fi
mkdir -p "$CLI/saves/ciworld"
cp "$SRV/world/level.dat" "$CLI/saves/ciworld/level.dat"
export LEWANDIVKA_AUTOTEST=cars
export LIBGL_ALWAYS_SOFTWARE=1
export MESA_GL_VERSION_OVERRIDE=4.5
export MESA_GLSL_VERSION_OVERRIDE=450
timeout 1500 xvfb-run -a -s "-screen 0 1280x720x24" \
  ./gradlew runClient --console=plain --args="--username Tester --width 1280 --height 720 --quickPlaySingleplayer ciworld" \
  > "$LOG" 2>&1
CODE=$?
echo "cars client exit code $CODE"
cp "$CLI/autotest-result.txt" "$RESULT" 2>/dev/null
mkdir -p "$OUT/shots"
cp "$CLI"/screenshots/*.png "$OUT/shots/" 2>/dev/null
echo "cars screenshots: $(ls "$CLI"/screenshots 2>/dev/null | wc -l)"
# what the game logged as an error or as a warning of the mod is a defect (a mod of the pack that complains about itself is shown as well)
PROBLEMS="$OUT/clienttest-cars-problems.txt"
NOISE="No data fixer registered for [A-Za-z0-9_-]+"
grep -nE "/ERROR\]|/WARN\] \(lewandivka\)|Exception ticking|Ticking entity|Exception loading entity|(Unable to bake model|Exception evaluating model definition|Missing sound for event|Unable to load|Failed to load).*(lewandivka|trepscars)" "$LOG" \
  | grep -v "Error starting SoundSystem\|Error while loading the narrator" | grep -vE "$NOISE" > "$PROBLEMS" 2>/dev/null
N=$(wc -l < "$PROBLEMS" | tr -d ' ')
[ "$N" = "0" ] && echo "none: the log of the cars run contains no error or warning" > "$PROBLEMS"
echo "cars log problems: $N"
[ "$N" != "0" ] && head -30 "$PROBLEMS"
grep -h "SOFT CHECK\|^cars: \|CHECK FAILED\|cars known" "$RESULT" 2>/dev/null | head -40
if grep -q "AUTOTEST-RESULT OK" "$RESULT" 2>/dev/null && [ "$N" = "0" ]; then
  echo "CARSTEST-RESULT OK"
  exit 0
fi
echo "CARSTEST-RESULT FAILED"
exit 1
