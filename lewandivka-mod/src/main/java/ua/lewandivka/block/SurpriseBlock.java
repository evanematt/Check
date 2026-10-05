package ua.lewandivka.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShovelItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import ua.lewandivka.logic.Advancements;

/** «Сюрприз біля лотка» від Чіназіка. Прибирається совком. */
public class SurpriseBlock extends Block {
    private static final VoxelShape SHAPE = Block.createCuboidShape(5, 0, 5, 11, 4, 11);

    public SurpriseBlock(Settings settings) {
        super(settings);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public void afterBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
        super.afterBreak(world, player, pos, state, blockEntity, tool);
        if (!world.isClient && player instanceof ServerPlayerEntity sp) {
            if (tool.getItem() instanceof ShovelItem) {
                sp.sendMessage(Text.literal("Прибрано совком. Чіназік дивиться так, ніби це був не він."), false);
                Advancements.grant(sp, "almost_hit");
            } else {
                sp.sendMessage(Text.literal("Фу. Наступного разу візьми совок."), true);
            }
        }
    }
}
