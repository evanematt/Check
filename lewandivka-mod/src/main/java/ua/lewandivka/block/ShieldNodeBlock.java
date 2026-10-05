package ua.lewandivka.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import ua.lewandivka.world.Sites;

/**
 * Перемикач (підйомник / компостер / важіль дверей). Вмикається гравцем на 30 секунд,
 * потім сам повертається. Механізм спрацьовує, коли всі перемикачі локації увімкнені одночасно.
 */
public class ShieldNodeBlock extends Block {
    public static final BooleanProperty ACTIVE = BooleanProperty.of("active");
    public static final int ACTIVE_TICKS = 600;

    public ShieldNodeBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(ACTIVE, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        if (state.get(ACTIVE)) {
            player.sendMessage(Text.literal("Вже увімкнено — тримайте інші!"), true);
            return ActionResult.CONSUME;
        }
        world.setBlockState(pos, state.with(ACTIVE, true), Block.NOTIFY_ALL);
        world.scheduleBlockTick(pos, this, ACTIVE_TICKS);
        world.playSound(null, pos, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 1f, 0.6f);
        if (player instanceof ServerPlayerEntity sp) {
            Sites.onNodeActivated((ServerWorld) world, pos, sp);
        }
        return ActionResult.CONSUME;
    }

    @Override
    public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (state.get(ACTIVE)) {
            world.setBlockState(pos, state.with(ACTIVE, false), Block.NOTIFY_ALL);
            world.playSound(null, pos, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 1f, 0.4f);
            Sites.onNodeReset(world, pos);
        }
    }
}
