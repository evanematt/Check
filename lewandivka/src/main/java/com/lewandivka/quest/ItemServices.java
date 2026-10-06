package com.lewandivka.quest;

import com.lewandivka.campaign.Campaign;
import com.lewandivka.core.campaign.QuestStep;
import com.lewandivka.core.quest.QuestItems;
import com.lewandivka.core.registry.ItemSpec.Use;
import com.lewandivka.core.story.Events;
import com.lewandivka.item.GameItems;
import com.lewandivka.item.ItemActions;
import com.lewandivka.item.SpecItem;
import com.lewandivka.network.Net;
import com.lewandivka.sound.GameSounds;
import com.lewandivka.util.Scheduler;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;

/** What the mod's items do when they are used. */
public final class ItemServices {

    private static final DustParticleEffect PINK = new DustParticleEffect(new Vector3f(1.0f, 0.15f, 0.68f), 1.8f);

    private ItemServices() {
    }

    public static void register() {
        ItemActions.onUse(Use.NOTEBOOK, (player, hand, item) -> {
            player.getServerWorld().playSound(null, player.getBlockPos(), GameSounds.get("ui.notebook_open"), SoundCategory.PLAYERS, 0.8f, 1.0f);
            Net.sendNotebook(player);
            return TypedActionResult.success(player.getStackInHand(hand));
        });
        ItemActions.onUse(Use.QUEST_NOTE, ItemServices::questNote);
        ItemActions.onUse(Use.COMPASS, ItemServices::compass);
        ItemActions.onUse(Use.KETTLE, ItemServices::kettleFill);
        ItemActions.onUseOnBlock(Use.KETTLE, ItemServices::kettlePlace);
        ItemActions.onUseOnBlock(Use.PACKAGE, ItemServices::packagePlace);
        ItemActions.onUse(Use.CHROMA, (player, hand, item) -> ChromaService.use(player, hand));
    }

    // ------------------------------------------------------------------ notes that start a quest

    private static TypedActionResult<ItemStack> questNote(ServerPlayerEntity player, Hand hand, SpecItem item) {
        MinecraftServer server = player.getServer();
        ItemStack stack = player.getStackInHand(hand);
        switch (item.spec.id) {
            case QuestItems.DEBTOR_NOTE -> {
                if (Campaign.step(server) == QuestStep.DEBTOR_NOTE) {
                    Story.event(server, Events.DEBTOR_NOTE_USED);
                } else {
                    player.sendMessage(Text.translatable("message.lewandivka.wrong_stage"), true);
                }
            }
            case QuestItems.GARAGE_NOTE, QuestItems.TRAM_NOTE -> player.sendMessage(Text.translatable("item.lewandivka." + item.spec.id + ".lore"), false);
            default -> {
            }
        }
        Net.sendNotebook(player);
        return TypedActionResult.success(stack);
    }

    // ------------------------------------------------------------------ chromatic compass

    private static TypedActionResult<ItemStack> compass(ServerPlayerEntity player, Hand hand, SpecItem item) {
        BlockPos target = Compass.target(player);
        ItemStack stack = player.getStackInHand(hand);
        if (target == null) {
            player.sendMessage(Text.translatable("message.lewandivka.compass.none"), true);
        } else if (!Compass.dimension(player).equals(com.lewandivka.world.dimension.Dimensions.idOf(player.getWorld()))) {
            player.sendMessage(Text.translatable("message.lewandivka.compass.other_world"), true);
        } else {
            Vec3d d = Vec3d.ofCenter(target).subtract(player.getPos());
            int meters = (int) Math.round(Math.sqrt(d.x * d.x + d.z * d.z));
            double bearing = Math.toDegrees(Math.atan2(-d.x, d.z));
            double relative = ((bearing - player.getYaw()) % 360 + 540) % 360 - 180;
            String arrow = relative > 135 || relative < -135 ? "↓" : relative > 45 ? "→" : relative < -45 ? "←" : "↑";
            player.sendMessage(Text.translatable("message.lewandivka.compass.reading", arrow, meters), true);
        }
        player.getServerWorld().playSound(null, player.getBlockPos(), GameSounds.get("crystal.chime"), SoundCategory.PLAYERS, 0.6f, 1.3f);
        return TypedActionResult.success(stack);
    }

    // ------------------------------------------------------------------ the kettle

    private static TypedActionResult<ItemStack> kettleFill(ServerPlayerEntity player, Hand hand, SpecItem item) {
        ItemStack stack = player.getStackInHand(hand);
        ServerWorld world = player.getServerWorld();
        Vec3d from = player.getEyePos();
        Vec3d to = from.add(player.getRotationVec(1.0f).multiply(4.5));
        BlockHitResult hit = world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.SOURCE_ONLY, player));
        if (hit.getType() == HitResult.Type.BLOCK && world.getFluidState(hit.getBlockPos()).isOf(Fluids.WATER)) {
            if (!stack.getOrCreateNbt().getBoolean("Water")) {
                stack.getOrCreateNbt().putBoolean("Water", true);
                world.playSound(null, player.getBlockPos(), GameSounds.get("kettle.pour"), SoundCategory.PLAYERS, 0.9f, 1.0f);
                player.sendMessage(Text.translatable("message.lewandivka.kettle.fill"), true);
            }
            return TypedActionResult.success(stack);
        }
        return TypedActionResult.pass(stack);
    }

    private static ActionResult kettlePlace(ServerPlayerEntity player, ItemUsageContext ctx, SpecItem item) {
        ItemStack stack = ctx.getStack();
        ServerWorld world = player.getServerWorld();
        BlockPos at = ctx.getBlockPos().offset(ctx.getSide());
        BlockState current = world.getBlockState(at);
        if (!current.isReplaceable() || !world.getBlockState(at.down()).isSolidBlock(world, at.down())) {
            return ActionResult.PASS;
        }
        boolean water = stack.hasNbt() && stack.getNbt().getBoolean("Water");
        world.setBlockState(at, com.lewandivka.block.GameBlocks.get("kettle_block").getDefaultState());
        world.playSound(null, at, GameSounds.get("kettle.place"), SoundCategory.BLOCKS, 0.9f, 1.0f);
        if (!player.isCreative()) {
            stack.decrement(1);
            // the kettle is a quest item: it comes back into the hand when picked up from the ground (see Stations)
        }
        Campaign.world(player.getServer()).setFlag("kettle.placed");
        if (water) {
            pinkWater(world, at, player);
            Story.event(player.getServer(), Events.KETTLE_PLACED);
        } else {
            player.sendMessage(Text.translatable("message.lewandivka.kettle.empty"), true);
        }
        return ActionResult.SUCCESS;
    }

    /** The water around the kettle turns pink for a moment. Particles and a sound: no block is ever changed. */
    private static void pinkWater(ServerWorld world, BlockPos at, ServerPlayerEntity player) {
        world.playSound(null, at, GameSounds.get("kettle.pink"), SoundCategory.BLOCKS, 1.2f, 1.0f);
        player.sendMessage(Text.translatable("message.lewandivka.kettle.pink"), true);
        for (int round = 0; round < 12; round++) {
            Scheduler.later(round * 5L, () -> {
                for (int dx = -4; dx <= 4; dx++) {
                    for (int dz = -4; dz <= 4; dz++) {
                        for (int dy = -2; dy <= 1; dy++) {
                            BlockPos p = at.add(dx, dy, dz);
                            if (world.getFluidState(p).isOf(Fluids.WATER) && world.getFluidState(p.up()).isEmpty()) {
                                world.spawnParticles(PINK, p.getX() + 0.5, p.getY() + 1.02, p.getZ() + 0.5, 2, 0.3, 0.02, 0.3, 0.0);
                                world.spawnParticles(ParticleTypes.BUBBLE_POP, p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5, 1, 0.3, 0.02, 0.3, 0.0);
                            }
                        }
                    }
                }
            });
        }
    }

    // ------------------------------------------------------------------ the package

    private static ActionResult packagePlace(ServerPlayerEntity player, ItemUsageContext ctx, SpecItem item) {
        ServerWorld world = player.getServerWorld();
        BlockPos at = ctx.getBlockPos().offset(ctx.getSide());
        if (!world.getBlockState(at).isReplaceable() || !world.getBlockState(at.down()).isSolidBlock(world, at.down())) {
            return ActionResult.PASS;
        }
        world.setBlockState(at, com.lewandivka.block.GameBlocks.get("package_block").getDefaultState());
        world.playSound(null, at, GameSounds.get("package.place"), SoundCategory.BLOCKS, 0.9f, 1.0f);
        ctx.getStack().decrement(1);
        Campaign.world(player.getServer()).setFlag("package.placed");
        if (com.lewandivka.flow.FlowHost.flow(player.getServer(), "garage13") instanceof com.lewandivka.core.flow.dungeon.Garage13Flow flow) {
            flow.packagePlaced();
        }
        return ActionResult.SUCCESS;
    }

    @SuppressWarnings("unused")
    private static ItemStack stack(String id) {
        return GameItems.stack(id);
    }
}
