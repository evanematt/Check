package ua.lewandivka.world;

import com.mojang.serialization.Codec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;
import ua.lewandivka.registry.ModBlocks;

/** Летючі острівці, з яких іноді падають водоспади. */
public class FloatingIslandFeature extends Feature<DefaultFeatureConfig> {
    public FloatingIslandFeature(Codec<DefaultFeatureConfig> codec) {
        super(codec);
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> ctx) {
        StructureWorldAccess w = ctx.getWorld();
        Random r = ctx.getRandom();
        BlockPos c = ctx.getOrigin().up(26 + r.nextInt(26));
        if (c.getY() > w.getTopY() - 10) {
            return false;
        }
        int radius = 3 + r.nextInt(4);
        int depth = radius + 1;
        for (int y = 0; y >= -depth; y--) {
            double rad = radius * Math.pow(1.0 - (-y) / (double) (depth + 1), 0.7);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz > rad * rad + r.nextInt(2)) {
                        continue;
                    }
                    BlockPos p = c.add(dx, y, dz);
                    if (!w.isAir(p)) {
                        continue;
                    }
                    BlockState s;
                    if (y == 0) {
                        s = Blocks.GRASS_BLOCK.getDefaultState();
                    } else if (y >= -2) {
                        s = Blocks.DIRT.getDefaultState();
                    } else {
                        s = r.nextInt(14) == 0 ? ModBlocks.CRYSTAL_AQUA.getDefaultState() : Blocks.STONE.getDefaultState();
                    }
                    w.setBlockState(p, s, Block.NOTIFY_LISTENERS);
                }
            }
        }
        for (int i = 0; i < radius * 2; i++) {
            BlockPos p = c.add(r.nextInt(radius * 2 + 1) - radius, 1, r.nextInt(radius * 2 + 1) - radius);
            if (w.isAir(p) && w.getBlockState(p.down()).isOf(Blocks.GRASS_BLOCK)) {
                BlockState deco = switch (r.nextInt(5)) {
                    case 0 -> Blocks.ALLIUM.getDefaultState();
                    case 1 -> Blocks.CORNFLOWER.getDefaultState();
                    case 2 -> Blocks.PINK_TULIP.getDefaultState();
                    default -> Blocks.GRASS.getDefaultState();
                };
                w.setBlockState(p, deco, Block.NOTIFY_LISTENERS);
            }
        }
        if (r.nextInt(3) != 0) {
            // Водоспад з краю острова.
            double a = r.nextDouble() * Math.PI * 2;
            BlockPos edge = c.add((int) Math.round(Math.cos(a) * (radius - 1)), 0, (int) Math.round(Math.sin(a) * (radius - 1)));
            w.setBlockState(edge, Blocks.WATER.getDefaultState(), Block.NOTIFY_LISTENERS);
            w.scheduleFluidTick(edge, Fluids.WATER, 0);
        }
        return true;
    }
}
