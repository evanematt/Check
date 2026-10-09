package com.lewandivka.command;

import com.mojang.datafixers.util.Pair;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.structure.Structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;

/**
 * Developer tool: makes the game generate a good part of the open country so that its biomes with their features and the structures of
 * the ordinary game run through the generator. {@code start} forces small squares of chunks at fixed random places to load,
 * {@code structures} looks for each structure of the game from the middle of the world and forces the chunks around what it finds,
 * {@code status} says how many are done with the biomes and structures they contain, {@code end} lets them go. The server smoke test
 * uses it: any exception of the generation is a defect that a player who explores would meet.
 */
public final class Sweep {

    /** The structures of the ordinary overworld that can stand in the open country (the ancient city needs the deep dark, the district has none). */
    private static final String[] STRUCTURES = {
            "village_plains", "village_desert", "village_savanna", "village_snowy", "village_taiga",
            "mineshaft", "mineshaft_mesa", "stronghold", "pillager_outpost", "desert_pyramid", "jungle_pyramid", "swamp_hut", "igloo",
            "ruined_portal", "ruined_portal_desert", "ruined_portal_jungle", "ruined_portal_swamp", "ruined_portal_mountain",
            "ruined_portal_ocean", "shipwreck", "shipwreck_beached", "ocean_ruin_cold", "ocean_ruin_warm", "ocean_monument", "mansion",
            "trail_ruins"};

    private static final List<ChunkPos> CHUNKS = new ArrayList<>();
    /** What {@code structures} found: the name of the structure and the position the game gave. */
    private static final List<Map.Entry<String, BlockPos>> LOCATED = new ArrayList<>();
    private static final List<String> NOT_FOUND = new ArrayList<>();

    private Sweep() {
    }

    private static void force(ServerWorld world, int x, int z) {
        CHUNKS.add(new ChunkPos(x, z));
        world.setChunkForced(x, z, true);
    }

    /**
     * {@code count} squares of {@code side} x {@code side} chunks at fixed random places within {@code range} chunks of the origin. A
     * chunk needs its neighbours up to eight chunks away to be generated (the game decorates a chunk only when the land around it exists),
     * so a few small squares are far cheaper than many single chunks and show as much of the world.
     */
    public static synchronized String start(ServerWorld world, int count, int side, int range) {
        end(world);
        Random random = new Random(42);
        for (int i = 0; i < count; i++) {
            int x = random.nextInt(2 * range + 1) - range;
            int z = random.nextInt(2 * range + 1) - range;
            for (int dx = 0; dx < side; dx++) {
                for (int dz = 0; dz < side; dz++) {
                    force(world, x + dx, z + dz);
                }
            }
        }
        return "sweep started: " + count + " squares of " + side + " x " + side + " chunks, " + CHUNKS.size() + " chunks within " + range + " chunks of the origin";
    }

    /**
     * Looks for every structure of the game within {@code radius} chunks of the middle of the world (what {@code /locate} does) and forces
     * the chunks around the place found, so that the structure is generated whole, with its pieces and its loot.
     */
    public static synchronized String structures(ServerWorld world, int radius) {
        end(world);
        Registry<Structure> registry = world.getRegistryManager().get(RegistryKeys.STRUCTURE);
        ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
        for (String name : STRUCTURES) {
            Optional<RegistryEntry.Reference<Structure>> entry = registry.getEntry(RegistryKey.of(RegistryKeys.STRUCTURE, new Identifier("minecraft", name)));
            if (entry.isEmpty()) {
                NOT_FOUND.add(name + "(unknown)");
                continue;
            }
            Pair<BlockPos, RegistryEntry<Structure>> hit = generator.locateStructure(world, RegistryEntryList.of(entry.get()), BlockPos.ORIGIN, radius, false);
            if (hit == null) {
                NOT_FOUND.add(name);
                continue;
            }
            LOCATED.add(Map.entry(name, hit.getFirst()));
            ChunkPos at = new ChunkPos(hit.getFirst());
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    force(world, at.x + dx, at.z + dz);
                }
            }
        }
        StringBuilder sb = new StringBuilder("sweep located:");
        LOCATED.forEach(e -> sb.append(' ').append(e.getKey()).append('@').append(e.getValue().getX()).append(',').append(e.getValue().getZ()));
        sb.append(" | not found: ").append(NOT_FOUND).append(" | forced ").append(CHUNKS.size()).append(" chunks");
        return sb.toString();
    }

    public static synchronized String status(ServerWorld world) {
        int loaded = 0;
        Map<String, Integer> biomes = new TreeMap<>();
        Map<String, Integer> structures = new TreeMap<>();
        for (ChunkPos pos : CHUNKS) {
            WorldChunk chunk = world.getChunkManager().getWorldChunk(pos.x, pos.z);
            if (chunk == null) {
                continue;
            }
            loaded++;
            biomes.merge(world.getBiome(pos.getCenterAtY(64)).getKey().map(k -> k.getValue().getPath()).orElse("?"), 1, Integer::sum);
            for (Structure structure : chunk.getStructureStarts().keySet()) {
                Identifier id = world.getRegistryManager().get(RegistryKeys.STRUCTURE).getId(structure);
                structures.merge(id == null ? "?" : id.getPath(), 1, Integer::sum);
            }
        }
        StringBuilder sb = new StringBuilder("sweep loaded=" + loaded + " of " + CHUNKS.size() + " done=" + (loaded == CHUNKS.size())
                + " biomes=" + biomes + " structures=" + structures);
        // where the structures that were located stand against the ground of the chunk: a village in the sky or in the rock is a defect
        for (Map.Entry<String, BlockPos> e : LOCATED) {
            WorldChunk chunk = world.getChunkManager().getWorldChunk(e.getValue().getX() >> 4, e.getValue().getZ() >> 4);
            if (chunk == null) {
                continue;
            }
            for (Map.Entry<Structure, StructureStart> s : chunk.getStructureStarts().entrySet()) {
                Identifier id = world.getRegistryManager().get(RegistryKeys.STRUCTURE).getId(s.getKey());
                if (id != null && id.getPath().equals(e.getKey())) {
                    int ground = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, e.getValue().getX(), e.getValue().getZ());
                    sb.append("\nsweep ").append(e.getKey()).append(" y ").append(s.getValue().getBoundingBox().getMinY()).append("..")
                            .append(s.getValue().getBoundingBox().getMaxY()).append(" ground ").append(ground)
                            .append(" pieces ").append(s.getValue().getChildren().size());
                }
            }
        }
        return sb.toString();
    }

    public static synchronized String end(ServerWorld world) {
        int n = CHUNKS.size();
        for (ChunkPos pos : CHUNKS) {
            world.setChunkForced(pos.x, pos.z, false);
        }
        CHUNKS.clear();
        LOCATED.clear();
        NOT_FOUND.clear();
        return "sweep ended: " + n + " chunks released";
    }
}
