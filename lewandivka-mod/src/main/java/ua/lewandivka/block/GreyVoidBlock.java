package ua.lewandivka.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/** Сіра порожнеча: те, що лишається, коли Безбарвний Голова з'їдає колір. Розсипається за 3 секунди. */
public class GreyVoidBlock extends Block {
    public GreyVoidBlock(Settings settings) {
        super(settings);
    }

    @Override
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        if (!world.isClient) {
            world.scheduleBlockTick(pos, this, 60);
        }
    }

    @Override
    public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        world.setBlockState(pos, Blocks.AIR.getDefaultState());
    }
}
