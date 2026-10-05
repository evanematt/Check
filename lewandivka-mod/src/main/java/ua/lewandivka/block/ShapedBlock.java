package ua.lewandivka.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

public class ShapedBlock extends Block {
    private final VoxelShape shape;

    public ShapedBlock(Settings settings, double x1, double y1, double z1, double x2, double y2, double z2) {
        super(settings);
        this.shape = Block.createCuboidShape(x1, y1, z1, x2, y2, z2);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return shape;
    }
}
