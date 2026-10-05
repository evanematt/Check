package ua.lewandivka.world;

import com.mojang.serialization.Codec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;
import ua.lewandivka.registry.ModBlocks;

/** Кольорові кристалічні шпилі. */
public class CrystalSpikeFeature extends Feature<DefaultFeatureConfig> {
    public CrystalSpikeFeature(Codec<DefaultFeatureConfig> codec) {
        super(codec);
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> ctx) {
        StructureWorldAccess w = ctx.getWorld();
        Random r = ctx.getRandom();
        BlockPos o = ctx.getOrigin();
        while (o.getY() > w.getBottomY() + 2 && w.isAir(o.down())) {
            o = o.down();
        }
        if (!w.getBlockState(o.down()).isOpaqueFullCube(w, o.down())) {
            return false;
        }
        Block[] crystals = {ModBlocks.CRYSTAL_ROSE, ModBlocks.CRYSTAL_AQUA, ModBlocks.CRYSTAL_CITRINE};
        BlockState s = crystals[r.nextInt(crystals.length)].getDefaultState();
        int h = 3 + r.nextInt(7);
        for (int y = 0; y < h; y++) {
            int rad = y < h / 3 ? 1 : 0;
            for (int dx = -rad; dx <= rad; dx++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) <= rad) {
                        BlockPos p = o.add(dx, y, dz);
                        if (w.isAir(p) || w.getBlockState(p).isReplaceable()) {
                            w.setBlockState(p, s, Block.NOTIFY_LISTENERS);
                        }
                    }
                }
            }
        }
        if (r.nextBoolean()) {
            BlockPos side = o.add(r.nextBoolean() ? 2 : -2, 0, r.nextBoolean() ? 1 : -1);
            for (int y = 0; y < 2 + r.nextInt(2); y++) {
                if (w.isAir(side.up(y))) {
                    w.setBlockState(side.up(y), crystals[r.nextInt(crystals.length)].getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }
        return true;
    }
}
