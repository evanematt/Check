package com.lewandivka.command;

import com.lewandivka.world.dimension.Dimensions;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Developer tools for the open country of the district: {@code /lewandivka survey} counts what the generator and the game's
 * own features made in the loaded chunks around a point (logs, water, ores, caves, animals ...), {@code /lewandivka wild}
 * stands (or hovers) somewhere out there. The server smoke test uses the first to prove that the wilderness can be survived in.
 */
public final class Survey {

    private Survey() {
    }

    /** Counts the blocks and animals of the loaded chunks within {@code radius} chunks of the block column (x, z). */
    public static String count(ServerWorld world, int x, int z, int radius) {
        int cx = Math.floorDiv(x, 16);
        int cz = Math.floorDiv(z, 16);
        Map<String, Integer> blocks = new TreeMap<>();
        Map<String, Integer> surface = new TreeMap<>();
        Map<String, Integer> ores = new TreeMap<>();
        Map<String, Integer> animals = new TreeMap<>();
        int chunks = 0;
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        BlockPos.Mutable p = new BlockPos.Mutable();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                WorldChunk chunk = world.getChunkManager().getWorldChunk(cx + dx, cz + dz);
                if (chunk == null) {
                    continue;
                }
                chunks++;
                int x0 = chunk.getPos().getStartX();
                int z0 = chunk.getPos().getStartZ();
                minX = Math.min(minX, x0);
                minZ = Math.min(minZ, z0);
                maxX = Math.max(maxX, x0 + 15);
                maxZ = Math.max(maxZ, z0 + 15);
                for (int bx = 0; bx < 16; bx++) {
                    for (int bz = 0; bz < 16; bz++) {
                        // the topmost block of the column (the air above it, if the heightmap counts it, is skipped)
                        int ground = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, bx, bz);
                        while (ground > 1 && chunk.getBlockState(p.set(x0 + bx, ground, z0 + bz)).isAir()) {
                            ground--;
                        }
                        for (int y = 1; y <= ground; y++) {
                            BlockState state = chunk.getBlockState(p.set(x0 + bx, y, z0 + bz));
                            String id = Registries.BLOCK.getId(state.getBlock()).getPath();
                            if (y == ground) {
                                surface.merge(id, 1, Integer::sum);
                            }
                            if (state.isAir()) {
                                if (y < ground - 6) {
                                    blocks.merge("cave_air", 1, Integer::sum);
                                }
                            } else if (id.endsWith("_ore")) {
                                ores.merge(id, 1, Integer::sum);
                            } else if (id.endsWith("_log")) {
                                blocks.merge("logs", 1, Integer::sum);
                            } else if (id.endsWith("_leaves")) {
                                blocks.merge("leaves", 1, Integer::sum);
                            } else if (id.equals("water")) {
                                blocks.merge("water", 1, Integer::sum);
                            } else if (id.equals("lava")) {
                                blocks.merge("lava", 1, Integer::sum);
                            } else if (id.equals("stone") && y < ground - 3) {
                                blocks.merge("stone", 1, Integer::sum);
                            }
                        }
                    }
                }
            }
        }
        if (chunks > 0) {
            for (Entity e : world.iterateEntities()) {
                if (e instanceof AnimalEntity && e.getX() >= minX && e.getX() <= maxX + 1 && e.getZ() >= minZ && e.getZ() <= maxZ + 1) {
                    animals.merge(Registries.ENTITY_TYPE.getId(e.getType()).getPath(), 1, Integer::sum);
                }
            }
        }
        return String.format(Locale.ROOT, "survey %d,%d r=%d: chunks=%d blocks=%s ores=%s surface=%s animals=%s", x, z, radius, chunks, blocks, ores, top(surface, 6), animals);
    }

    /** The six most common entries of a counter (the surface of a chunk area is dozens of kinds). */
    private static String top(Map<String, Integer> counts, int n) {
        return counts.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(n)
                .map(e -> e.getKey() + "=" + e.getValue()).collect(java.util.stream.Collectors.joining(", ", "{", "}"));
    }

    /** Stands on the ground of the open country at (x, z), or hovers high above it looking down at it. */
    public static String visit(ServerPlayerEntity player, int x, int z, boolean hover) {
        ServerWorld world = Dimensions.district(player.getServer());
        if (world == null) {
            return "the district is not loaded";
        }
        world.getChunk(x >> 4, z >> 4);
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (hover) {
            player.teleport(world, x + 0.5, y + 34, z + 0.5 + 24, 180.0f, 38.0f);
            if (player.getAbilities().allowFlying) {
                player.getAbilities().flying = true;
                player.sendAbilitiesUpdate();
            }
        } else {
            player.teleport(world, x + 0.5, y, z + 0.5, 180.0f, 8.0f);
        }
        player.fallDistance = 0.0f;
        return "at " + x + "," + y + "," + z + " " + world.getBiome(new BlockPos(x, y, z)).getKey().map(k -> k.getValue().toString()).orElse("?");
    }
}
