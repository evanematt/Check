package com.lewandivka.world.dimension;

import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.structure.StructurePlacement;
import com.lewandivka.core.world.TerrainColumn;
import com.lewandivka.core.world.WorldPlan;
import com.lewandivka.world.structure.StateResolver;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;

import java.util.EnumSet;

/**
 * Writes one chunk of a world plan: terrain columns first, then the scatter decoration, then the fixed quest structures
 * (which win over everything else). Every chunk is computed independently from the plan, so the world is identical
 * whatever order the chunks are generated in.
 */
final class ChunkPainter {

    private ChunkPainter() {
    }

    static void paint(WorldPlan plan, Chunk chunk) {
        ChunkPos cp = chunk.getPos();
        int x0 = cp.getStartX();
        int z0 = cp.getStartZ();
        int minY = plan.minY();
        int maxY = minY + plan.height() - 1;
        BlockPos.Mutable mp = new BlockPos.Mutable();
        TerrainColumn col = new TerrainColumn();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                plan.column(x0 + dx, z0 + dz, col);
                paintColumn(chunk, mp, x0 + dx, z0 + dz, col, minY, maxY);
            }
        }
        for (StructurePlacement p : plan.scatterIn(x0, z0, x0 + 15, z0 + 15)) {
            stamp(chunk, p, x0, z0, minY, maxY, mp);
        }
        for (StructurePlacement p : plan.fixedPlacements()) {
            if (p.intersectsXZ(x0, z0, x0 + 15, z0 + 15)) {
                stamp(chunk, p, x0, z0, minY, maxY, mp);
            }
        }
        Heightmap.populateHeightmaps(chunk, EnumSet.of(Heightmap.Type.OCEAN_FLOOR_WG, Heightmap.Type.WORLD_SURFACE_WG));
    }

    private static void paintColumn(Chunk chunk, BlockPos.Mutable mp, int x, int z, TerrainColumn col, int minY, int maxY) {
        BlockState base = StateResolver.parse(col.base);
        BlockState sub = StateResolver.parse(col.sub);
        BlockState top = StateResolver.parse(col.top);
        int surface = Math.min(col.height, maxY);
        for (int y = minY; y <= surface; y++) {
            BlockState s;
            if (y == minY) {
                s = Blocks.BEDROCK.getDefaultState();
            } else if (y == col.height) {
                s = top;
            } else if (y >= col.height - col.subDepth) {
                s = sub;
            } else {
                s = base;
            }
            chunk.setBlockState(mp.set(x, y, z), s, false);
        }
        if (col.fluidY > col.height) {
            BlockState fluid = StateResolver.parse(col.fluid);
            int top2 = Math.min(col.fluidY, maxY);
            for (int y = col.height + 1; y <= top2; y++) {
                chunk.setBlockState(mp.set(x, y, z), fluid, false);
            }
        } else if (col.decor != null && col.height + 1 <= maxY) {
            chunk.setBlockState(mp.set(x, col.height + 1, z), StateResolver.parse(col.decor), false);
        }
    }

    private static void stamp(Chunk chunk, StructurePlacement p, int x0, int z0, int minY, int maxY, BlockPos.Mutable mp) {
        Blueprint bp = p.blueprint();
        BlockState[] palette = new BlockState[bp.paletteSize()];
        int fromX = Math.max(p.x(), x0);
        int toX = Math.min(p.maxX(), x0 + 15);
        int fromZ = Math.max(p.z(), z0);
        int toZ = Math.min(p.maxZ(), z0 + 15);
        for (int by = 0; by < bp.sizeY(); by++) {
            int wy = p.y() + by;
            if (wy < minY || wy > maxY) {
                continue;
            }
            for (int wz = fromZ; wz <= toZ; wz++) {
                for (int wx = fromX; wx <= toX; wx++) {
                    int raw = bp.rawAt(wx - p.x(), by, wz - p.z());
                    if (raw == Blueprint.UNTOUCHED) {
                        continue;
                    }
                    BlockState state = palette[raw];
                    if (state == null) {
                        state = StateResolver.parse(bp.paletteKey(raw));
                        palette[raw] = state;
                    }
                    chunk.setBlockState(mp.set(wx, wy, wz), state, false);
                }
            }
        }
    }
}
