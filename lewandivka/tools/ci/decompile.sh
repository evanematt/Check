#!/usr/bin/env bash
# Decompiles the Minecraft classes the mod depends on most (chunk loading and watching, teleports, the player network
# handler, the client's chunk and world classes) with CFR, so their code can be read when a vanilla mechanism is unclear:
# the Minecraft jars cannot be downloaded in the environment where the mod is written, CI can.
# Usage (after a Gradle build, from the lewandivka directory):  tools/ci/decompile.sh ../logs/vanilla-src
set -u
OUT="${1:-vanilla-src}"
rm -rf "$OUT" "$OUT.tgz"
mkdir -p "$OUT"
# the jar with the Yarn names (the intermediary one only has class_1234); the candidates are listed so the next run can be adjusted
echo "minecraft jars in the Gradle caches:"
find "$HOME/.gradle" -name 'minecraft-*.jar' -not -name '*sources*' 2>/dev/null | sed 's/^/  /'
COMMON=$(find "$HOME/.gradle" -name 'minecraft-common-*.jar' -not -name '*sources*' 2>/dev/null | grep -v intermediary | head -1)
CLIENT=$(find "$HOME/.gradle" -name 'minecraft-clientOnly-*.jar' -not -name '*sources*' 2>/dev/null | grep -v intermediary | head -1)
echo "common jar: ${COMMON:-none}"
echo "client jar: ${CLIENT:-none}"
curl -fsSL -o "$OUT/cfr.jar" "https://github.com/leibnitz27/cfr/releases/download/0.152/cfr-0.152.jar" || { echo "could not download CFR"; exit 1; }
decompile() {
  local jar="$1" filter="$2" dir="$3"
  [ -n "$jar" ] || return 0
  java -jar "$OUT/cfr.jar" "$jar" --jarfilter "$filter" --outputdir "$OUT/$dir" --silent true --comments false > "$OUT/$dir.log" 2>&1
  echo "$dir: $(find "$OUT/$dir" -name '*.java' 2>/dev/null | wc -l) classes"
}
decompile "$COMMON" 'net\.minecraft\.(server\.world\..*|server\.network\..*|server\.PlayerManager.*|entity\.(Entity|LivingEntity|player\.PlayerEntity)|world\.(entity\..*|chunk\.(WorldChunk|ChunkSection|PalettedContainer.*)|World)|network\.ClientConnection.*|network\.packet\.s2c\.play\.(ChunkData.*|PlayerRespawn.*|GameJoin.*|UnloadChunk.*|ChunkRenderDistanceCenter.*))' common
decompile "$CLIENT" 'net\.minecraft\.client\.(network\.ClientPlayNetworkHandler.*|world\.(ClientWorld.*|ClientChunkManager.*)|network\.ClientPlayerEntity|MinecraftClient)' client
rm -f "$OUT/cfr.jar"
tar czf "$OUT.tgz" -C "$OUT" . 2>/dev/null
ls -la "$OUT.tgz"
