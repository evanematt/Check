package com.lewandivka.world.dimension;

import com.lewandivka.core.world.TerrainColumn;
import com.lewandivka.core.world.WorldPlan;
import com.lewandivka.world.structure.StateResolver;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Chunk generator of both Lewandivka dimensions. It does not use noise settings or vanilla structures at all: the whole
 * world is a pure function of the {@link WorldPlan}, which is what makes the quest structures deterministic.
 */
public final class PlanChunkGenerator extends ChunkGenerator {

    public static final Codec<PlanChunkGenerator> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("plan").forGetter(generator -> generator.planName),
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.getBiomeSource())
    ).apply(instance, instance.stable(PlanChunkGenerator::new)));

    private final String planName;
    private final WorldPlan plan;

    public PlanChunkGenerator(String planName, BiomeSource biomeSource) {
        super(biomeSource);
        this.planName = planName;
        this.plan = Plans.get(planName);
    }

    public WorldPlan plan() {
        return plan;
    }

    @Override
    protected Codec<? extends ChunkGenerator> getCodec() {
        return CODEC;
    }

    @Override
    public void carve(ChunkRegion chunkRegion, long seed, NoiseConfig noiseConfig, BiomeAccess biomeAccess, StructureAccessor structureAccessor, Chunk chunk, GenerationStep.Carver carverStep) {
    }

    @Override
    public void buildSurface(ChunkRegion region, StructureAccessor structures, NoiseConfig noiseConfig, Chunk chunk) {
    }

    @Override
    public void populateEntities(ChunkRegion region) {
    }

    @Override
    public int getWorldHeight() {
        return plan.height();
    }

    @Override
    public CompletableFuture<Chunk> populateNoise(Executor executor, Blender blender, NoiseConfig noiseConfig, StructureAccessor structureAccessor, Chunk chunk) {
        ChunkPainter.paint(plan, chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getSeaLevel() {
        return 62;
    }

    @Override
    public int getMinimumY() {
        return plan.minY();
    }

    @Override
    public int getHeight(int x, int z, Heightmap.Type heightmap, HeightLimitView world, NoiseConfig noiseConfig) {
        TerrainColumn col = new TerrainColumn();
        plan.column(x, z, col);
        int top = Math.max(col.height, col.fluidY);
        boolean solidOnly = heightmap == Heightmap.Type.OCEAN_FLOOR_WG || heightmap == Heightmap.Type.OCEAN_FLOOR;
        return (solidOnly ? col.height : top) + 1;
    }

    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noiseConfig) {
        TerrainColumn col = new TerrainColumn();
        plan.column(x, z, col);
        int minY = plan.minY();
        BlockState[] states = new BlockState[plan.height()];
        BlockState base = StateResolver.parse(col.base);
        BlockState sub = StateResolver.parse(col.sub);
        BlockState top = StateResolver.parse(col.top);
        BlockState fluid = StateResolver.parse(col.fluid);
        for (int i = 0; i < states.length; i++) {
            int y = minY + i;
            BlockState s;
            if (y > col.height) {
                s = y <= col.fluidY ? fluid : Blocks.AIR.getDefaultState();
            } else if (y == minY) {
                s = Blocks.BEDROCK.getDefaultState();
            } else if (y == col.height) {
                s = top;
            } else if (y >= col.height - col.subDepth) {
                s = sub;
            } else {
                s = base;
            }
            states[i] = s;
        }
        return new VerticalBlockSample(minY, states);
    }

    @Override
    public void getDebugHudText(List<String> text, NoiseConfig noiseConfig, BlockPos pos) {
        text.add("Lewandivka plan: " + planName);
    }
}
