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
# the mods whose license does not let the archive carry them are downloaded by the start script: do the same
if [ -f fetch-mods.sh ]; then sh ./fetch-mods.sh || { echo "SERVERPACK-RESULT FAILED: the mods that are downloaded at the start could not be fetched"; exit 1; }; fi
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
    "lewandivka decor" \
    "lewandivka populace" \
    "lewandivka loot" \
    "lewandivka cars" \
    "lewandivka dumpitems shield" \
    "lewandivka dumpitems more_food" \
    "lewandivka dumpitems fumee-de-bushy" \
    "lewandivka dumpitems trepscars" \
    "lewandivka dumpblocks" \
    "lewandivka dumpblocks handcrafted" \
    "lewandivka dumpblocks refurbished_furniture" \
    "execute in lewandivka:district run forceload add -32 348 32 412" \
    "!until 240 chunks=25 lewandivka survey 0 380 2" \
    "stop" | tee "$LOG.rcon"
else
  echo "SERVERPACK: the server did not finish starting"
  kill "$PID" 2>/dev/null
fi
wait "$PID" 2>/dev/null
# the furniture of the rooms must be made of the blocks of the furniture mod of the pack (Handcrafted), none of it of the stand-ins
grep -h "^decor:" "$LOG.rcon" 2>/dev/null | head -2
grep -h "^populace:" "$LOG.rcon" 2>/dev/null | head -2
PEOPLE_OK=0
if grep -Eq "^populace: ([0-9]+) places, \1 made now, 0 stood already" "$LOG.rcon" 2>/dev/null; then PEOPLE_OK=1; else echo "SERVERPACK: not every place of the people of the district was filled"; fi
# the containers of the buildings with the other mods of the pack (drinks, tobacco, food): the tables hold the things of the mods too
grep -h "^loot:\|^items of\|^entities of" "$LOG.rcon" 2>/dev/null | cut -c1-2500
LOOT_OK=0
if grep -q "^loot: OK" "$LOG.rcon" 2>/dev/null; then LOOT_OK=1; else echo "SERVERPACK: the containers of the buildings do not hold what the tables say"; fi
# every thing of the other mods that the tables ask for must exist in the mods of the pack (a wrong name would only be left out silently)
if ! grep -q "things of other mods in the tables: [1-9][0-9]* items of" "$LOG.rcon" 2>/dev/null || grep -q "not in this game: \[" "$LOG.rcon" 2>/dev/null; then
  echo "SERVERPACK: the tables ask for things of the other mods that the pack does not have"; LOOT_OK=0
fi
grep -h "^cars:" "$LOG.rcon" 2>/dev/null | head -2
FURNITURE_OK=0
if grep -q "^decor: [1-9][0-9]* furniture keys, [1-9][0-9]* with the blocks of a furniture mod, 0 with the stand-in of the game" "$LOG.rcon" 2>/dev/null; then FURNITURE_OK=1; else echo "SERVERPACK: the furniture is not (all) made of the blocks of the furniture mod"; fi
# the open country must generate with the third-party mods of the pack too (they change chunk generation and lighting)
if [ "$FURNITURE_OK" = 1 ] && [ "$PEOPLE_OK" = 1 ] && [ "$LOOT_OK" = 1 ] && grep -q "validate: OK" "$LOG.rcon" 2>/dev/null && grep -q "^survey .* chunks=25 " "$LOG.rcon" 2>/dev/null; then echo "SERVERPACK-RESULT OK"; else echo "SERVERPACK-RESULT FAILED"; exit 1; fi
