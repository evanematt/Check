package ua.lewandivka.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import ua.lewandivka.logic.ShelterQuest;

/** Найменша, зовсім непропорційна коробочка. Там Метадонна. */
public class TinyBoxBlock extends Block {
    private static final VoxelShape SHAPE = Block.createCuboidShape(4, 0, 4, 12, 6, 12);

    public TinyBoxBlock(Settings settings) {
        super(settings);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient) {
            ShelterQuest.onTinyBox((ServerWorld) world, pos);
        }
        return ActionResult.success(world.isClient);
    }

    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        super.onBreak(world, pos, state, player);
        if (!world.isClient) {
            ShelterQuest.onTinyBox((ServerWorld) world, pos);
        }
    }
}
