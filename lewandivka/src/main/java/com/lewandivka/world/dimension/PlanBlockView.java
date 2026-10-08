package com.lewandivka.world.dimension;

import com.lewandivka.core.world.WorldPlan;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.chunk.UpgradeData;

import java.util.HashMap;
import java.util.Map;

/**
 * A world that exists only as the chunks the plan paints. GameTests run on a vanilla test server that does not load the
 * custom dimensions, so this view lets them check the chunk painter without a dimension; the in-server self test
 * ({@code /lewandivka selftest}) compares it with the chunks the real dimension generated.
 */
public final class PlanBlockView implements BlockView {

    private final WorldPlan plan;
    private final DynamicRegistryManager registries;
    private final HeightLimitView limits;
    private final Map<Long, Chunk> chunks = new HashMap<>();

    public PlanBlockView(WorldPlan plan, DynamicRegistryManager registries) {
        this.plan = plan;
        this.registries = registries;
        this.limits = HeightLimitView.create(plan.minY(), plan.height());
    }

    private Chunk chunk(BlockPos pos) {
        ChunkPos cp = new ChunkPos(pos);
        return chunks.computeIfAbsent(cp.toLong(), k -> {
            ProtoChunk chunk = new ProtoChunk(cp, UpgradeData.NO_UPGRADE_DATA, limits, registries.get(RegistryKeys.BIOME), null);
            ChunkPainter.paint(plan, chunk);
            return chunk;
        });
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        if (limits.isOutOfHeightLimit(pos)) {
            return Blocks.AIR.getDefaultState();
        }
        return chunk(pos).getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return Fluids.EMPTY.getDefaultState();
    }

    @Override
    public int getHeight() {
        return limits.getHeight();
    }

    @Override
    public int getBottomY() {
        return limits.getBottomY();
    }
}
