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

# every encounter is created, rebuilt, reset and wiped once (unknown markers and gates are logged as warnings, which fail
# this test below)
FLOWS="garage13 tram_stop base rainbow_garage shelter aquapark sky_ascent sky_depot tower_approach tower garage0 district"
RESETS=()
for n in $FLOWS; do
  RESETS+=("lewandivka reset encounter $n" "lewandivka reset encounter $n full")
done

# the join replay (see JoinReplay and Lifecycle): two players join through the real join (the arrival in the district
# comes a few ticks later, never inside the join event), a third one is moved around like the client test does; the
# packets each of them would have been sent are decoded and compared with the worlds of the server
session "$LOG" \
  "lewandivka status" \
  "execute in lewandivka:district run forceload add 0 0 31 31" \
  "execute in lewandivka:chromandivka run forceload add 0 0 31 31" \
  "lewandivka validate" \
  "lewandivka decor" \
  "lewandivka populace" \
  "lewandivka loot" \
  "lewandivka cars" \
  "lewandivka selftest" \
  "execute in lewandivka:district run lewandivka probe 2 60 72 -3" \
  "lewandivka joinreplay start A -1" \
  "lewandivka joinreplay start B -1" \
  "lewandivka joinreplay torture" \
  "!until 150 DONE lewandivka joinreplay result" \
  "lewandivka joinreplay report" \
  "execute in lewandivka:district run forceload add -72 -72 72 72" \
  "execute in lewandivka:district run forceload add -72 308 72 452" \
  "execute in lewandivka:district run forceload add -522 -172 -378 -28" \
  "execute in lewandivka:district run forceload add 448 228 592 372" \
  "!until 300 chunks=81 lewandivka survey 0 0 4" \
  "!until 300 chunks=81 lewandivka survey 0 380 4" \
  "!until 300 chunks=81 lewandivka survey -450 -100 4" \
  "!until 300 chunks=81 lewandivka survey 520 300 4" \
  "lewandivka sweep start 24 2 190" \
  "!until 600 done=true lewandivka sweep status" \
  "lewandivka sweep end" \
  "lewandivka sweep structures 100" \
  "!until 900 done=true lewandivka sweep status" \
  "lewandivka sweep end" \
  "execute in lewandivka:district positioned 0 64 0 run locate structure #minecraft:village" \
  "execute in lewandivka:district positioned 0 64 0 run locate biome minecraft:desert" \
  "lewandivka step $STEP" \
  "lewandivka status" \
  "save-all flush" \
  "stop"

# the second session reads the world back from the region files (it must still be the plan), then rebuilds, resets and wipes
# every encounter once: constructors, rebuild() and reset() of all twelve flows run against the real world
echo "=== restart on the same world ==="
session "$LOG2" \
  "lewandivka status" \
  "execute in lewandivka:district run forceload add 0 0 31 31" \
  "execute in lewandivka:chromandivka run forceload add 0 0 31 31" \
  "lewandivka validate" \
  "lewandivka selftest" \
  "${RESETS[@]}" \
  "stop"

ok=1
# whatever the mod logged as a warning or an error on the server is a defect
if grep -E "/ERROR\]|/WARN\] \(lewandivka\)|Exception loading entity|Exception ticking|Ticking entity|unexpected exception" "$LOG" "$LOG2" > "${LOG%.txt}-problems.txt" 2>/dev/null; then
  echo "SMOKE: the mod logged problems:"; head -20 "${LOG%.txt}-problems.txt"; ok=0
else
  rm -f "${LOG%.txt}-problems.txt"
fi
grep -q "validate: OK" "$LOG.rcon" 2>/dev/null || { echo "SMOKE: first session did not validate"; ok=0; }
grep -q "validate: OK" "$LOG2.rcon" 2>/dev/null || { echo "SMOKE: the restarted server did not validate"; ok=0; }
# the furniture: without a furniture mod every piece is made of the blocks of the game that stand in for it
grep -h "^decor:" "$LOG.rcon" 2>/dev/null | head -2
grep -q "^decor: [1-9][0-9]* furniture keys, 0 with the blocks of a furniture mod" "$LOG.rcon" 2>/dev/null || { echo "SMOKE: the furniture is not made of the blocks of the game"; ok=0; }
# the people of the district: every place gets its citizen or trader, and every trader has his goods
grep -h "^populace:" "$LOG.rcon" 2>/dev/null | head -2
grep -Eq "^populace: ([0-9]+) places, \1 made now, 0 stood already" "$LOG.rcon" 2>/dev/null || { echo "SMOKE: not every place of the people of the district was filled"; ok=0; }
grep -Eq "vendor_baker=[1-9].* vendor_gardener=[1-9]" "$LOG.rcon" 2>/dev/null || { echo "SMOKE: the traders have no goods"; ok=0; }
# the containers of the buildings: every table of loot gives things (rolled forty times), and the chests and barrels of the world carry them
grep -h "^loot:" "$LOG.rcon" 2>/dev/null | cut -c1-1500
grep -q "^loot: OK" "$LOG.rcon" 2>/dev/null || { echo "SMOKE: the containers of the buildings do not hold what the tables say"; ok=0; }
# the cars: without the mod of the cars (a dedicated server cannot run it) they stay built of blocks
grep -h "^cars:" "$LOG.rcon" 2>/dev/null | head -2
grep -Eq "^cars: [1-9][0-9]* places, the mod trepscars is not in this game" "$LOG.rcon" 2>/dev/null || { echo "SMOKE: the cars are not the cars built of blocks"; ok=0; }
# the self test needs the real dimensions: generated world = plan, free first arrival, players can cross (see SelfTest)
grep -h "selftest:" "$LOG.rcon" "$LOG2.rcon" 2>/dev/null | cut -c1-1800
grep -q "selftest: OK" "$LOG.rcon" 2>/dev/null || { echo "SMOKE: the self test failed in the first session"; ok=0; }
grep -q "selftest: OK" "$LOG2.rcon" 2>/dev/null || { echo "SMOKE: the self test failed after the restart"; ok=0; }
# the join replay must find nothing: no chunk of another world after the arrival, the player in exactly one world, no
# exception in the movement of the third player
grep -q "joinreplay: OK" "$LOG.rcon" 2>/dev/null || { echo "SMOKE: the join replay found problems"; ok=0; }
grep -q "torture: OK" "$LOG.rcon" 2>/dev/null || { echo "SMOKE: the movement of the third player found problems"; ok=0; }
grep -h "^lewandivka:district 2,-3:" "$LOG.rcon" 2>/dev/null | head -2
# the open country: three 9 x 9 chunk areas around the city were generated; the game's own features must have made forests, ores,
# water, caves and animals there (a world to survive in), and the chunks must have come out without a single logged error
echo "--- wilderness"
python3 - "$LOG.rcon" <<'PY' || { echo "SMOKE: the wilderness is not a world to survive in"; ok=0; }
import re, sys
lines = [l for l in open(sys.argv[1], encoding="utf-8", errors="replace").read().splitlines() if l.startswith("survey ")]
totals = {}
for l in lines:
    print(l[:600])
    for key, value in re.findall(r"([a-z_]+)=(\d+)", l.split(": ", 1)[1]):
        totals[key] = totals.get(key, 0) + int(value)
need = {"chunks": 200, "logs": 1, "leaves": 1, "water": 1, "cave_air": 1, "coal_ore": 1, "iron_ore": 1, "grass_block": 1}
missing = [k for k, v in need.items() if totals.get(k, 0) < v]
animals = sum(totals.get(k, 0) for k in ("cow", "pig", "sheep", "chicken", "horse", "rabbit", "fox", "wolf", "llama", "goat"))
print("totals:", {k: totals.get(k, 0) for k in need}, "animals:", animals)
if len(lines) < 4 or missing or animals < 1:
    print("missing:", missing, "animals:", animals, "reports:", len(lines))
    sys.exit(1)
PY
# under the streets of the city the rock is the rock of the ordinary game: bedrock at the bottom (the world is 384 blocks deep),
# deepslate below y = 0, ore veins, caves
echo "--- under the city"
python3 - "$LOG.rcon" <<'PY' || { echo "SMOKE: the rock under the city is not the rock of the ordinary game"; ok=0; }
import re, sys
lines = [l for l in open(sys.argv[1], encoding="utf-8", errors="replace").read().splitlines() if l.startswith("survey 0,0 ")]
if not lines:
    print("no survey of the city centre")
    sys.exit(1)
line = lines[-1]
blocks = dict((k, int(v)) for k, v in re.findall(r"([a-z_]+)=(\d+)", line.split("blocks=", 1)[1].split(" ores=", 1)[0]))
ores = dict((k, int(v)) for k, v in re.findall(r"([a-z_]+)=(\d+)", line.split("ores=", 1)[1].split(" surface=", 1)[0]))
print("the city centre:", {k: blocks.get(k, 0) for k in ("bedrock", "deepslate", "stone", "cave_air", "lava")}, "ores:", ores)
problems = []
if blocks.get("bedrock", 0) < 20000:
    problems.append("bedrock %d" % blocks.get("bedrock", 0))
if blocks.get("deepslate", 0) < 100000:
    problems.append("deepslate %d" % blocks.get("deepslate", 0))
if blocks.get("stone", 0) < 100000:
    problems.append("stone %d" % blocks.get("stone", 0))
if sum(ores.values()) < 100:
    problems.append("ores %d" % sum(ores.values()))
if not any(k.startswith("deepslate_") for k in ores):
    problems.append("no ore in the deepslate")
if blocks.get("cave_air", 0) < 1000:
    problems.append("caves %d" % blocks.get("cave_air", 0))
if problems:
    print("missing under the city:", problems)
    sys.exit(1)
PY
# 24 small squares of chunks spread over 6000 x 6000 blocks went through the generator (the biomes with their features), and the
# structures of the game (villages, mineshafts, strongholds ...) were generated whole around the places the game found for them
grep -h "^sweep " "$LOG.rcon" 2>/dev/null | cut -c1-1800
[ "$(grep -c '^sweep loaded=.* done=true' "$LOG.rcon" 2>/dev/null)" = 2 ] || { echo "SMOKE: a sweep did not generate all its chunks"; ok=0; }
# informational: do the structures and the biomes of the ordinary game exist in the open country?
grep -h "nearest\|Could not find" "$LOG.rcon" 2>/dev/null | cut -c1-300
echo "--- join replay"
grep -h "joinreplay:\|joined (\|torture started\|RUNNING\|DONE" "$LOG.rcon" 2>/dev/null | cut -c1-3000
# the first status line of the second session is the one printed right after the restart
if head -n 3 "$LOG2.rcon" 2>/dev/null | grep -q "step=$STEP"; then
  echo "SMOKE: the campaign step survived the restart ($STEP)"
else
  echo "SMOKE: the campaign step did NOT survive the restart"
  ok=0
fi
if [ "$ok" = 1 ]; then echo "SMOKE-RESULT OK"; else echo "SMOKE-RESULT FAILED"; exit 1; fi
