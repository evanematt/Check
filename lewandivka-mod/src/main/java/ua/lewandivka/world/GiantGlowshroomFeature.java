package ua.lewandivka.world;

import com.mojang.serialization.Codec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CaveVines;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;
import ua.lewandivka.registry.ModBlocks;

/** Гігантські фіолетові гриби зі світними нитками, що звисають з капелюха. */
public class GiantGlowshroomFeature extends Feature<DefaultFeatureConfig> {
    public GiantGlowshroomFeature(Codec<DefaultFeatureConfig> codec) {
        super(codec);
    }

    private static void put(StructureWorldAccess w, BlockPos p, BlockState s) {
        BlockState cur = w.getBlockState(p);
        if (cur.isAir() || cur.isReplaceable() || cur.isIn(BlockTags.LEAVES)) {
            w.setBlockState(p, s, Block.NOTIFY_LISTENERS);
        }
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> ctx) {
        StructureWorldAccess w = ctx.getWorld();
        Random r = ctx.getRandom();
        BlockPos o = ctx.getOrigin();
        if (!w.getBlockState(o.down()).isIn(BlockTags.DIRT)) {
            return false;
        }
        int h = 8 + r.nextInt(9);
        boolean thick = h >= 12;
        BlockState stem = ModBlocks.GLOWSHROOM_STEM.getDefaultState();
        BlockState cap = ModBlocks.GLOWSHROOM_CAP.getDefaultState();
        for (int y = 0; y < h; y++) {
            put(w, o.up(y), stem);
            if (thick) {
                put(w, o.add(1, y, 0), stem);
                put(w, o.add(0, y, 1), stem);
                put(w, o.add(1, y, 1), stem);
            }
        }
        int radius = 4 + r.nextInt(3);
        int[] layers = {radius, radius - 1, Math.max(1, radius - 3)};
        for (int i = 0; i < layers.length; i++) {
            int rad = layers[i];
            for (int dx = -rad; dx <= rad + (thick ? 1 : 0); dx++) {
                for (int dz = -rad; dz <= rad + (thick ? 1 : 0); dz++) {
                    double cx = thick ? dx - 0.5 : dx;
                    double cz = thick ? dz - 0.5 : dz;
                    if (cx * cx + cz * cz <= rad * rad + 1) {
                        put(w, o.add(dx, h + i, dz), cap);
                    }
                }
            }
        }
        // Світні нитки під краєм капелюха.
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > radius - 1.2 && d <= radius + 0.5 && r.nextInt(3) == 0) {
                    int len = 1 + r.nextInt(4);
                    for (int k = 1; k <= len; k++) {
                        BlockPos p = o.add(dx, h - k, dz);
                        if (!w.isAir(p)) {
                            break;
                        }
                        BlockState vine = k == len ? Blocks.CAVE_VINES.getDefaultState().with(CaveVines.BERRIES, true)
                                : Blocks.CAVE_VINES_PLANT.getDefaultState().with(CaveVines.BERRIES, r.nextBoolean());
                        w.setBlockState(p, vine, Block.NOTIFY_LISTENERS);
                    }
                }
            }
        }
        return true;
    }
}
