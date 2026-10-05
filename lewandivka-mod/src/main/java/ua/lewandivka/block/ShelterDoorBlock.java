package ua.lewandivka.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import ua.lewandivka.logic.ShelterQuest;

/** Двері Притулку загублених імен — всередині він набагато більший, ніж зовні. */
public class ShelterDoorBlock extends Block {
    public ShelterDoorBlock(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient && player instanceof ServerPlayerEntity sp) {
            ShelterQuest.useDoor((ServerWorld) world, pos, sp);
        }
        return ActionResult.success(world.isClient);
    }
}
