package ua.lewandivka.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import ua.lewandivka.logic.Bosses;

/** Вівтар виклику боса: 0 гараж, 1 аквапарк, 2 депо, 3 притулок, 4 вежа. */
public class BossAltarBlock extends Block {
    public static final IntProperty BOSS = IntProperty.of("boss", 0, 4);

    public BossAltarBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(BOSS, 0));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(BOSS);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient && player instanceof ServerPlayerEntity sp) {
            Bosses.summon((ServerWorld) world, pos, state.get(BOSS), sp);
        }
        return ActionResult.success(world.isClient);
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        world.addParticle(ParticleTypes.WITCH, pos.getX() + random.nextDouble(), pos.getY() + 1.1, pos.getZ() + random.nextDouble(), 0, 0.05, 0);
    }
}
