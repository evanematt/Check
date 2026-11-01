package com.lewandivka.quest;

import com.lewandivka.world.dimension.Dimensions;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FireChargeItem;
import net.minecraft.item.FlintAndSteelItem;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.dimension.NetherPortal;

import java.util.Optional;

/**
 * Nether portals in the district. The game lights a portal only in the overworld and in the Nether
 * ({@code AbstractFireBlock.isOverworldOrNether}), and the district is neither, so in the open country a frame of obsidian would
 * stay dead. Flint and steel and the fire charge light it here exactly as there: a frame of obsidian around the clicked spot
 * becomes a portal (the way back leads to the overworld of the ordinary game and from there to the district, see {@link Lifecycle}).
 */
public final class NetherGate {

    private NetherGate() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register(NetherGate::use);
    }

    private static ActionResult use(PlayerEntity player, World world, Hand hand, BlockHitResult hit) {
        if (world.isClient || player.isSpectator() || !Dimensions.isOurs(world)) {
            return ActionResult.PASS;
        }
        ItemStack stack = player.getStackInHand(hand);
        boolean flint = stack.getItem() instanceof FlintAndSteelItem;
        if (!flint && !(stack.getItem() instanceof FireChargeItem)) {
            return ActionResult.PASS;
        }
        BlockPos fire = hit.getBlockPos().offset(hit.getSide());
        if (!light(world, fire, player.getHorizontalFacing())) {
            return ActionResult.PASS;   // the game's own rules: an ordinary fire, or nothing
        }
        if (flint) {
            world.playSound(null, fire, SoundEvents.ITEM_FLINTANDSTEEL_USE, SoundCategory.BLOCKS, 1.0f, world.getRandom().nextFloat() * 0.4f + 0.8f);
            stack.damage(1, player, p -> p.sendToolBreakStatus(hand));
        } else {
            world.playSound(null, fire, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.BLOCKS, 1.0f, (world.getRandom().nextFloat() - world.getRandom().nextFloat()) * 0.2f + 1.0f);
            if (!player.isCreative()) {
                stack.decrement(1);
            }
        }
        return ActionResult.SUCCESS;
    }

    /**
     * Lights the portal whose interior contains {@code fire}, if there is a complete frame around it (what
     * {@code AbstractFireBlock.shouldLightPortalAt} does in the overworld). Returns whether a portal came to be.
     */
    public static boolean light(World world, BlockPos fire, Direction facing) {
        if (!world.getBlockState(fire).isAir()) {
            return false;
        }
        boolean obsidian = false;
        for (Direction d : Direction.values()) {
            obsidian |= world.getBlockState(fire.offset(d)).isOf(Blocks.OBSIDIAN);
        }
        if (!obsidian) {
            return false;
        }
        Direction.Axis axis = facing.getAxis().isHorizontal() ? facing.rotateYCounterclockwise().getAxis() : Direction.Axis.X;
        Optional<NetherPortal> portal = NetherPortal.getNewPortal(world, fire, axis);   // tries the other axis as well
        portal.ifPresent(NetherPortal::createPortal);
        return portal.isPresent();
    }
}
