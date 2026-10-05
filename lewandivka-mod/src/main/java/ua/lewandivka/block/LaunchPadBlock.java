package ua.lewandivka.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** Пружний люк: підкидає вгору, гасить падіння. */
public class LaunchPadBlock extends Block {
    private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 4, 16);

    public LaunchPadBlock(Settings settings) {
        super(settings);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public void onSteppedOn(World world, BlockPos pos, BlockState state, Entity entity) {
        if (!entity.bypassesSteppingEffects()) {
            Vec3d v = entity.getVelocity();
            entity.setVelocity(v.x, 1.6, v.z);
            entity.velocityModified = true;
            world.playSound(null, pos, SoundEvents.BLOCK_SLIME_BLOCK_FALL, SoundCategory.BLOCKS, 1f, 1.4f);
        }
        super.onSteppedOn(world, pos, state, entity);
    }

    @Override
    public void onLandedUpon(World world, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        entity.handleFallDamage(fallDistance, 0.0f, world.getDamageSources().fall());
    }
}
