package com.lewandivka.world.dimension;

import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.structure.StructurePlacement;
import com.lewandivka.core.world.Noise;
import com.lewandivka.core.world.TerrainColumn;
import com.lewandivka.core.world.WorldPlan;
import com.lewandivka.world.structure.StateResolver;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ProtoChunk;

import java.util.EnumSet;

/**
 * Writes one chunk of a world plan: terrain columns first, then the scatter decoration, then the fixed quest structures
 * (which win over everything else). Every chunk is computed independently from the plan, so the world is identical
 * whatever order the chunks are generated in.
 */
public final class ChunkPainter {

    private ChunkPainter() {
    }

    public static void paint(WorldPlan plan, Chunk chunk) {
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
                paintColumn(plan, chunk, mp, x0 + dx, z0 + dz, col, minY, maxY);
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

    /** Caves that reach down this far above the bottom are filled with lava (the lava lakes of the deep, as in the ordinary overworld). */
    private static final int LAVA_ABOVE_FLOOR = 10;
    private static final long SEED_BEDROCK = 0xBED20C4L;
    private static final long SEED_DEEPSLATE = 0xDEE95A7EL;
    private static final BlockState BEDROCK = Blocks.BEDROCK.getDefaultState();
    private static final BlockState LAVA = Blocks.LAVA.getDefaultState();
    private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.getDefaultState();

    private static void paintColumn(WorldPlan plan, Chunk chunk, BlockPos.Mutable mp, int x, int z, TerrainColumn col, int minY, int maxY) {
        BlockState base = StateResolver.parse(col.base);
        // the deep world (the district) has the layers of the ordinary overworld; the dimension of the other side keeps its flat floor
        boolean stone = minY < 0 && col.base.equals("minecraft:stone");
        boolean deepFloor = minY < 0;
        BlockState sub = StateResolver.parse(col.sub);
        BlockState top = StateResolver.parse(col.top);
        int surface = Math.min(col.height, maxY);
        for (int y = minY; y <= surface; y++) {
            BlockState s;
            if (y == minY || deepFloor && y < minY + 5 && Noise.hash01(SEED_BEDROCK, x, y, z) < (5 - (y - minY)) / 5.0) {
                // the bedrock floor of the ordinary world: solid at the bottom, thinning out over five layers
                s = BEDROCK;
            } else if (plan.carved(x, y, z, col.height)) {
                if (y > minY + LAVA_ABOVE_FLOOR) {
                    continue;
                }
                s = LAVA;
            } else if (y == col.height) {
                s = top;
            } else if (y >= col.height - col.subDepth) {
                s = sub;
            } else if (stone && deepslate(x, y, z)) {
                s = DEEPSLATE;
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

    /** Stone turns into deepslate below y = 8, gradually down to y = 0 and entirely below that (as in the ordinary overworld). */
    private static boolean deepslate(int x, int y, int z) {
        return y < 0 || y < 8 && Noise.hash01(SEED_DEEPSLATE, x, y, z) < (8 - y) / 8.0;
    }

    /**
     * Blocks that take their shape from the neighbours (a pane joins the pane or the wall next to it, a fence the next fence).
     * A key of a blueprint names the block without these joins, so they are worked out when the chunk comes alive, the way the
     * game does it for the fences and bars of its own structures: without it every window would be a thin post.
     */
    private static boolean joins(BlockState state) {
        Block b = state.getBlock();
        return b instanceof PaneBlock || b instanceof FenceBlock || b instanceof WallBlock;
    }

    private static void stamp(Chunk chunk, StructurePlacement p, int x0, int z0, int minY, int maxY, BlockPos.Mutable mp) {
        Blueprint bp = p.blueprint();
        BlockState[] palette = new BlockState[bp.paletteSize()];
        boolean[] join = new boolean[bp.paletteSize()];
        // only a chunk under construction keeps the list of the blocks to be joined (the others would only complain)
        boolean building = chunk instanceof ProtoChunk;
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
                        join[raw] = building && joins(state);
                    }
                    chunk.setBlockState(mp.set(wx, wy, wz), state, false);
                    if (join[raw]) {
                        chunk.markBlockForPostProcessing(mp);
                    }
                }
            }
        }
    }
}
