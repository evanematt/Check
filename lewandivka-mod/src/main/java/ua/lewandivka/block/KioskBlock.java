package ua.lewandivka.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import ua.lewandivka.logic.Shlagbaum;

/** Закинутий кіоск. Робоче місце Пана Шлагбаума; клік викликає його, якщо поруч немає. */
public class KioskBlock extends Block {
    public KioskBlock(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!player.getStackInHand(hand).isEmpty()) {
            return ActionResult.PASS;
        }
        if (!world.isClient) {
            Shlagbaum.summonAt((ServerWorld) world, pos, player);
        }
        return ActionResult.success(world.isClient);
    }
}
