package ua.lewandivka.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import ua.lewandivka.logic.Travel;

public class ChromaPortalBlock extends Block {
    public ChromaPortalBlock(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient && player instanceof ServerPlayerEntity sp) {
            Travel.usePortal(sp);
        }
        return ActionResult.success(world.isClient);
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        for (int i = 0; i < 3; i++) {
            world.addParticle(ParticleTypes.END_ROD, pos.getX() + random.nextDouble(), pos.getY() + 1.0 + random.nextDouble() * 0.5,
                    pos.getZ() + random.nextDouble(), 0, 0.03, 0);
        }
    }
}
