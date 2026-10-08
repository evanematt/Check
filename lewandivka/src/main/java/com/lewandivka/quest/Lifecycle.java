package com.lewandivka.quest;

import com.lewandivka.LewandivkaMod;
import com.lewandivka.ability.Abilities;
import com.lewandivka.campaign.Campaign;
import com.lewandivka.campaign.PartyService;
import com.lewandivka.core.campaign.PlayerProgress;
import com.lewandivka.core.campaign.QuestStep;
import com.lewandivka.core.campaign.WorldProgress;
import com.lewandivka.core.quest.QuestItems;
import com.lewandivka.core.world.gen.DistrictPlan;
import com.lewandivka.network.Net;
import com.lewandivka.sound.GameSounds;
import com.lewandivka.world.dimension.Dimensions;
import com.lewandivka.world.structure.Structures;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/** Joining, respawning and the reaction to every step of the campaign. */
public final class Lifecycle {

    private Lifecycle() {
    }

    public static void register() {
        Abilities.register();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> join(handler.player, server));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Abilities.forget(handler.player.getUuid()));
        ServerPlayerEvents.AFTER_RESPAWN.register((old, player, alive) -> respawned(player));
        ServerLifecycleEvents.SERVER_STARTED.register(Lifecycle::serverStarted);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Dialogues.tick(server);
            Abilities.tick(server);
        });
        Campaign.addListener(Lifecycle::stepEntered);
    }

    private static void serverStarted(MinecraftServer server) {
        if (Campaign.world(server).started()) {
            Portals.apply(server);
            NpcSpawns.ensure(server);
        }
        LewandivkaMod.LOGGER.info("Campaign: {} / {}", Campaign.stage(server), Campaign.step(server));
    }

    // ------------------------------------------------------------------ joining

    private static void join(ServerPlayerEntity player, MinecraftServer server) {
        PlayerProgress progress = Campaign.player(player);
        WorldProgress world = Campaign.world(server);
        if (!progress.tutorialDone("arrived")) {
            progress.completeTutorial("arrived");
            progress.setParticipating(true);
            if (!world.started()) {
                world.setStarted(true);
                Advancements.grant(player, "terminus");
            }
            arrive(player, server);
            QuestInventory.give(player, QuestItems.NOTEBOOK, 1);
            player.networkHandler.sendPacket(new TitleS2CPacket(Text.translatable("title.lewandivka.district")));
            player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.translatable("title.lewandivka.district.sub")));
            player.sendMessage(Text.translatable("message.lewandivka.welcome"), false);
            Campaign.dirty(server);
        }
        Story.catchUp(player);
        Net.sendCampaign(player);
        if (world.started()) {
            NpcSpawns.ensure(server);
        }
    }

    /** The first arrival: the district at the start of the story, the base of Chromandivka when the party is already there. */
    private static void arrive(ServerPlayerEntity player, MinecraftServer server) {
        if (Campaign.step(server).isAtLeast(QuestStep.BASE_WAKE) && Structures.has("base:spawn")) {
            Travel.toMarker(player, "base:spawn");
            return;
        }
        int[] s = DistrictPlan.get().spawn();
        Vec3d at = new Vec3d(s[0] + 0.5, s[1], s[2] + 0.5);
        Travel.to(player, Dimensions.DISTRICT_ID, at);
        player.setSpawnPoint(Dimensions.DISTRICT, net.minecraft.util.math.BlockPos.ofFloored(at), 0.0f, true, false);
    }

    private static void respawned(ServerPlayerEntity player) {
        if (Dimensions.isOurs(player.getWorld())) {
            player.sendMessage(Text.translatable("message.lewandivka.respawn_checkpoint"), true);
        }
    }

    // ------------------------------------------------------------------ steps

    private static void stepEntered(MinecraftServer server, QuestStep step, boolean forced) {
        List<ServerPlayerEntity> players = PartyService.players(server);
        Text title = Text.translatable(step.titleKey());
        for (ServerPlayerEntity p : players) {
            p.sendMessage(Text.translatable("message.lewandivka.quest_started", title), true);
            p.getServerWorld().playSound(null, p.getBlockPos(), GameSounds.get("ui.quest_update"), SoundCategory.PLAYERS, 0.8f, 1.0f);
        }
        Net.broadcastCampaign(server);
        String advancement = switch (step) {
            case TALK_SHLAHBAUM -> "kiosk";
            case KETTLE_TEST -> "debtor";
            case GARAGE_PANELS -> "garage13";
            case TRAM_REPORT -> "last_tram";
            case BASE_WAKE -> "other_side";
            case RG_TRAVEL -> "portal";
            case SH_DASH -> "dash";
            case AQ_FIND -> "two_who_know";
            case SKY_ASCENT -> "spring";
            case TOWER_RING -> "glider";
            case TOWER_APPROACH -> "ring";
            case TRAM_WAIT -> "local";
            case POST_FREE -> "district_colorful";
            default -> null;
        };
        if (advancement != null && !forced) {
            Advancements.grant(players, advancement);
        }
        if (step == QuestStep.RG_TRAVEL || forced) {
            Portals.apply(server);
        }
        Campaign.dirty(server);
    }
}
