package com.lewandivka.command;

import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * Developer tool: makes the game generate a few hundred chunks all over the open country (a fixed random pattern) so that every
 * biome, its features and the structures of the ordinary game run through the generator. {@code start} forces the chunks to load,
 * {@code status} says how many are done with the biomes and structures they contain, {@code end} lets them go. The server smoke
 * test uses it: any exception of the generation is a defect that a player who explores would meet.
 */
public final class Sweep {

    private static final List<ChunkPos> CHUNKS = new ArrayList<>();

    private Sweep() {
    }

    public static synchronized String start(ServerWorld world, int count, int range) {
        end(world);
        Random random = new Random(42);
        for (int i = 0; i < count; i++) {
            ChunkPos pos = new ChunkPos(random.nextInt(2 * range + 1) - range, random.nextInt(2 * range + 1) - range);
            CHUNKS.add(pos);
            world.setChunkForced(pos.x, pos.z, true);
        }
        return "sweep started: " + CHUNKS.size() + " chunks within " + range + " chunks of the origin";
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
                var id = world.getRegistryManager().get(RegistryKeys.STRUCTURE).getId(structure);
                structures.merge(id == null ? "?" : id.getPath(), 1, Integer::sum);
            }
        }
        return "sweep loaded=" + loaded + " of " + CHUNKS.size() + " biomes=" + biomes + " structures=" + structures;
    }

    public static synchronized String end(ServerWorld world) {
        int n = CHUNKS.size();
        for (ChunkPos pos : CHUNKS) {
            world.setChunkForced(pos.x, pos.z, false);
        }
        CHUNKS.clear();
        return "sweep ended: " + n + " chunks released";
    }
}
