package com.lewandivka.quest;

import com.lewandivka.ability.Abilities;
import com.lewandivka.block.BlockActions;
import com.lewandivka.block.SpecBlock;
import com.lewandivka.campaign.Campaign;
import com.lewandivka.core.campaign.PlayerProgress;
import com.lewandivka.core.campaign.QuestStep;
import com.lewandivka.core.campaign.WorldProgress;
import com.lewandivka.core.flow.Flow;
import com.lewandivka.core.flow.dungeon.Garage13Flow;
import com.lewandivka.core.quest.QuestItems;
import com.lewandivka.core.quest.StashLoot;
import com.lewandivka.core.registry.BlockSpec.Behaviour;
import com.lewandivka.core.story.Events;
import com.lewandivka.flow.FlowHost;
import com.lewandivka.item.GameItems;
import com.lewandivka.sound.GameSounds;
import com.lewandivka.world.dimension.Dimensions;
import com.lewandivka.world.structure.Structures;
import com.lewandivka.world.structure.Structures.Marker;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Random;

/** What the interactive blocks of the mod do. Everything that belongs to an encounter is passed on to its flow. */
public final class Stations {

    private Stations() {
    }

    public static void register() {
        BlockActions.onUse(Behaviour.STATION, Stations::station);
        BlockActions.onUse(Behaviour.PEDESTAL, Stations::station);
        BlockActions.onUse(Behaviour.NOTE, Stations::note);
        BlockActions.onUse(Behaviour.STASH, Stations::stash);
        BlockActions.onUse(Behaviour.CHECKPOINT, Stations::checkpoint);
        BlockActions.onUse(Behaviour.PACKAGE, Stations::packageBlock);
        BlockActions.onUse(Behaviour.KETTLE, Stations::kettleBlock);
        BlockActions.onUse(Behaviour.KIOSK, (w, p, s, b, pl, h) -> ActionResult.PASS);
        BlockActions.onTouch(Behaviour.PORTAL, (w, p, s, b, e) -> {
            if (e instanceof ServerPlayerEntity sp) {
                Portals.enter(sp);
            }
        });
        BlockActions.onTouch(Behaviour.SPRING, Stations::spring);
        BlockActions.onTouch(Behaviour.VOID, Stations::greyVoid);
        BlockActions.onKioskPlaced(Stations::kioskPlaced);
    }

    private static String heldId(ServerPlayerEntity player, Hand hand) {
        ItemStack held = player.getStackInHand(hand);
        return held.isEmpty() ? "" : GameItems.idOf(held);
    }

    // ------------------------------------------------------------------ levers, panels, pedestals

    private static ActionResult station(ServerWorld world, BlockPos pos, BlockState state, SpecBlock block, ServerPlayerEntity player, Hand hand) {
        MinecraftServer server = world.getServer();
        Marker m = Structures.markerAt(Dimensions.idOf(world), pos);
        if (m == null) {
            return ActionResult.PASS;
        }
        if (m.name().equals("clue") || m.name().startsWith("clue_")) {
            return clue(server, m, player);
        }
        Flow flow = FlowHost.flow(server, m.structure());
        if (flow == null) {
            return ActionResult.PASS;
        }
        boolean handled = flow.useWith(m.name(), player.getUuid(), heldId(player, hand));
        return handled ? ActionResult.SUCCESS : ActionResult.PASS;
    }

    /** A trace of the Debtor: tea package, bench, playground, rubbish, garage roof. */
    private static ActionResult clue(MinecraftServer server, Marker m, ServerPlayerEntity player) {
        WorldProgress w = Campaign.world(server);
        QuestStep step = w.step();
        if (step != QuestStep.DEBTOR_CLUES) {
            player.sendMessage(Text.translatable(step.isBefore(QuestStep.DEBTOR_CLUES) ? "message.lewandivka.not_yet" : "message.lewandivka.debtor.clue_seen"), true);
            return ActionResult.SUCCESS;
        }
        if (w.setFlag("clue." + m.id())) {
            player.getServerWorld().playSound(null, player.getBlockPos(), GameSounds.get("ui.quest_update"), SoundCategory.PLAYERS, 0.8f, 1.0f);
            Story.event(server, Events.DEBTOR_CLUE);
        } else {
            player.sendMessage(Text.translatable("message.lewandivka.debtor.clue_seen"), true);
        }
        return ActionResult.SUCCESS;
    }

    // ------------------------------------------------------------------ notes and stashes

    private static ActionResult note(ServerWorld world, BlockPos pos, BlockState state, SpecBlock block, ServerPlayerEntity player, Hand hand) {
        int index = block.getInt(state, "note");
        PlayerProgress progress = Campaign.player(player);
        boolean fresh = progress.collect("note." + index);
        player.sendMessage(Text.translatable("note.lewandivka." + index), false);
        if (fresh) {
            player.sendMessage(Text.translatable("message.lewandivka.note_found"), true);
            world.playSound(null, pos, GameSounds.get("note.read"), SoundCategory.BLOCKS, 0.8f, 1.0f);
            Campaign.dirty(world.getServer());
        }
        return ActionResult.SUCCESS;
    }

    private static ActionResult stash(ServerWorld world, BlockPos pos, BlockState state, SpecBlock block, ServerPlayerEntity player, Hand hand) {
        MinecraftServer server = world.getServer();
        Marker m = Structures.markerAt(Dimensions.idOf(world), pos);
        String kind = m == null ? "junk" : m.data("loot", "junk");
        String key = "stash." + (m == null ? Dimensions.idOf(world) + pos.asLong() : m.id()) + "." + player.getUuid();
        WorldProgress w = Campaign.world(server);
        if (!w.setFlag(key)) {
            player.sendMessage(Text.translatable("message.lewandivka.stash.empty"), true);
            return ActionResult.SUCCESS;
        }
        Random rng = new Random(pos.asLong() * 31 + player.getUuid().getLeastSignificantBits());
        List<StashLoot.Drop> drops = StashLoot.roll(kind, rng);
        if (drops.isEmpty()) {
            player.sendMessage(Text.translatable("message.lewandivka.stash.empty"), true);
        }
        for (StashLoot.Drop d : drops) {
            QuestInventory.give(player, d.item(), d.min());
            Text name = QuestInventory.item(d.item()).getName();
            player.sendMessage(Text.translatable("message.lewandivka.stash.found", Text.literal(d.min() + "x ").append(name)), true);
            if (d.item().equals(QuestItems.TOKEN)) {
                Campaign.player(player).addTokens(d.min());
                for (int i = 0; i < d.min(); i++) {
                    Story.event(server, Events.TOKEN_COLLECTED);
                }
            }
        }
        world.playSound(null, pos, GameSounds.get("stash.open"), SoundCategory.BLOCKS, 0.9f, 1.0f);
        Campaign.dirty(server);
        return ActionResult.SUCCESS;
    }

    // ------------------------------------------------------------------ checkpoint lamp

    private static ActionResult checkpoint(ServerWorld world, BlockPos pos, BlockState state, SpecBlock block, ServerPlayerEntity player, Hand hand) {
        player.setSpawnPoint(world.getRegistryKey(), pos.up(), player.getYaw(), true, false);
        player.sendMessage(Text.translatable("hud.lewandivka.checkpoint"), true);
        world.playSound(null, pos, GameSounds.get("checkpoint.set"), SoundCategory.BLOCKS, 0.9f, 1.0f);
        return ActionResult.SUCCESS;
    }

    // ------------------------------------------------------------------ package and kettle on the ground

    private static ActionResult packageBlock(ServerWorld world, BlockPos pos, BlockState state, SpecBlock block, ServerPlayerEntity player, Hand hand) {
        MinecraftServer server = world.getServer();
        world.removeBlock(pos, false);
        Campaign.world(server).clearFlag("package.placed");
        QuestInventory.give(player, QuestItems.PACKAGE, 1);
        if (FlowHost.flow(server, "garage13") instanceof Garage13Flow flow) {
            flow.packageLifted(player.getUuid());
        }
        return ActionResult.SUCCESS;
    }

    private static ActionResult kettleBlock(ServerWorld world, BlockPos pos, BlockState state, SpecBlock block, ServerPlayerEntity player, Hand hand) {
        world.removeBlock(pos, false);
        Campaign.world(world.getServer()).clearFlag("kettle.placed");
        ItemStack back = GameItems.stack(QuestItems.KETTLE);
        back.getOrCreateNbt().putBoolean("Water", true);
        if (!player.getInventory().insertStack(back)) {
            player.dropItem(back, false);
        }
        return ActionResult.SUCCESS;
    }

    // ------------------------------------------------------------------ kiosk

    private static void kioskPlaced(ServerWorld world, BlockPos pos, BlockState state, SpecBlock block, ServerPlayerEntity player) {
        MinecraftServer server = world.getServer();
        Marker spot = Structures.marker("district:kiosk_spot");
        boolean onFoundation = spot != null && Dimensions.idOf(world).equals(spot.dimension())
                && Math.abs(pos.getX() - spot.x()) <= 2 && Math.abs(pos.getZ() - spot.z()) <= 2 && Math.abs(pos.getY() - spot.y()) <= 3;
        if (!onFoundation) {
            world.removeBlock(pos, false);
            ItemStack kiosk = new ItemStack(GameItems.blockItem("abandoned_kiosk"));
            if (!player.getInventory().insertStack(kiosk)) {
                player.dropItem(kiosk, false);
            }
            player.sendMessage(Text.translatable("message.lewandivka.kiosk.need_foundation"), true);
            return;
        }
        world.playSound(null, pos, GameSounds.get("kiosk.place"), SoundCategory.BLOCKS, 1.0f, 1.0f);
        player.sendMessage(Text.translatable("message.lewandivka.kiosk.placed"), true);
        Story.event(server, Events.KIOSK_PLACED);
    }

    // ------------------------------------------------------------------ springs and the grey void

    private static void spring(ServerWorld world, BlockPos pos, BlockState state, SpecBlock block, Entity entity) {
        if (!(entity instanceof LivingEntity living) || entity.age % 6 != 0 || entity.getVelocity().y > 0.2) {
            return;
        }
        boolean full = !(entity instanceof ServerPlayerEntity sp) || Abilities.canSpring(sp);
        double power = block.spec.id.equals("spring_hatch") ? 1.45 : 1.05;
        Vec3d v = entity.getVelocity();
        entity.setVelocity(v.x, full ? power : 0.42, v.z);
        entity.velocityModified = true;
        entity.fallDistance = 0.0f;
        if (entity instanceof ServerPlayerEntity sp) {
            sp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(sp));
        }
        world.playSound(null, pos, GameSounds.get("spring.boing"), SoundCategory.BLOCKS, 0.8f, full ? 1.0f : 0.7f);
        living.fallDistance = 0.0f;
    }

    private static void greyVoid(ServerWorld world, BlockPos pos, BlockState state, SpecBlock block, Entity entity) {
        if (entity instanceof LivingEntity living && entity.age % 20 == 0 && !(entity instanceof ServerPlayerEntity p && p.isCreative())) {
            living.damage(world.getDamageSources().magic(), 2.0f);
            living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1, false, false));
        }
    }

    @SuppressWarnings("unused")
    private static DamageSource unused(ServerWorld world) {
        return world.getDamageSources().generic();
    }
}
