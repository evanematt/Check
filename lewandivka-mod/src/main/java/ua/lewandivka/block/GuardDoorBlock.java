package ua.lewandivka.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** Охоронні двері: відчиняє тільки Чіназік своїм «Відкрийте, бляха». */
public class GuardDoorBlock extends Block {
    public static final BooleanProperty OPEN = BooleanProperty.of("open");

    public GuardDoorBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(OPEN, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(OPEN);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(OPEN) ? VoxelShapes.empty() : VoxelShapes.fullCube();
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient && !state.get(OPEN)) {
            player.sendMessage(Text.literal("Замкнено. Тут потрібен хтось, хто вміє дуже переконливо мявкати під дверима."), true);
        }
        return ActionResult.success(world.isClient);
    }
}
