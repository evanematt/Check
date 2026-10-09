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
import com.lewandivka.util.Scheduler;
import com.lewandivka.world.dimension.Dimensions;
import com.lewandivka.world.structure.Structures;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Joining, respawning and the reaction to every step of the campaign. */
public final class Lifecycle {

    /**
     * Ticks between the join of a new player and the first arrival in the district. Never 0: Fabric's JOIN event fires
     * right after the game-join packet, before {@code PlayerManager.onPlayerConnect} has added the player to its world; a
     * teleport in there moves the player to the district and then vanilla adds the same player to the overworld as well,
     * so two worlds tick and watch it: the overworld sends its chunks (24 sections, the client decodes them as the 16
     * sections of the district: stone and ore 64 blocks too high), and the next teleport to another dimension throws in
     * {@code ChunkTicketManager.handleChunkLeave}. {@code /lewandivka joinreplay} shows both cases; the delay is a knob of
     * it ({@link #DEFAULT_ARRIVAL_DELAY} is what the game uses).
     */
    public static final int DEFAULT_ARRIVAL_DELAY = 3;
    public static int arrivalDelayTicks = DEFAULT_ARRIVAL_DELAY;
    private static final Map<UUID, Integer> ARRIVALS = new HashMap<>();

    private Lifecycle() {
    }

    public static void register() {
        Abilities.register();
        NetherGate.register();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> join(handler.player, server));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Abilities.forget(handler.player.getUuid()));
        ServerPlayerEvents.AFTER_RESPAWN.register((old, player, alive) -> respawned(player));
        // a Nether portal of the open country leads back to the overworld of the ordinary game, which is another world than the district:
        // whoever comes out of the Nether is brought home to the district at the same coordinates (the Nether scales them by 8 as usual)
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            if (origin.getRegistryKey() == World.NETHER && destination.getRegistryKey() == World.OVERWORLD) {
                Scheduler.later(3, () -> {
                    if (!player.isRemoved() && player.getWorld().getRegistryKey() == World.OVERWORLD) {
                        Travel.toSurface(player, Dimensions.DISTRICT_ID, player.getX(), player.getZ());
                    }
                });
            }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(Lifecycle::serverStarted);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Dialogues.tick(server);
            Abilities.tick(server);
            NpcSpawns.tick(server);
            Townsfolk.tick(server);
            arrivals(server);
        });
        Campaign.addListener(Lifecycle::stepEntered);
    }

    private static void serverStarted(MinecraftServer server) {
        if (Campaign.world(server).started()) {
            Portals.apply(server);
            holdDistrictTime(server, Campaign.step(server));
        }
        LewandivkaMod.LOGGER.info("Campaign: {} / {}", Campaign.stage(server), Campaign.step(server));
    }

    /**
     * The first evening lasts as long as the district is explored, the first night as long as the tokens are collected
     * (the gopniks only come out at night); after that the day and the night run as usual.
     */
    private static void holdDistrictTime(MinecraftServer server, QuestStep step) {
        TimeControl.holdTime(Dimensions.DISTRICT_ID, "evening", TimeControl.DUSK, step == QuestStep.EXPLORE_DISTRICT);
        TimeControl.holdTime(Dimensions.DISTRICT_ID, "first_night", TimeControl.NIGHT, step == QuestStep.COLLECT_TOKENS);
    }

    // ------------------------------------------------------------------ joining

    private static void join(ServerPlayerEntity player, MinecraftServer server) {
        PlayerProgress progress = Campaign.player(player);
        WorldProgress world = Campaign.world(server);
        if (!progress.tutorialDone("arrived")) {
            progress.setParticipating(true);
            if (!world.started()) {
                world.setStarted(true);
                Advancements.grant(player, "terminus");
                // "players spawn near an old tram stop at dusk": the evening stays as long as the district is explored
                TimeControl.set(server, TimeControl.DUSK);
                holdDistrictTime(server, Campaign.step(server));
            }
            if (!QuestInventory.has(player, QuestItems.NOTEBOOK)) {
                QuestInventory.give(player, QuestItems.NOTEBOOK, 1);
            }
            if (arrivalDelayTicks <= 0) {
                arriveWithWelcome(player, server);
            } else {
                ARRIVALS.put(player.getUuid(), arrivalDelayTicks);
            }
            Campaign.dirty(server);
        }
        Story.catchUp(player);
        Net.sendCampaign(player);
    }

    private static void arriveWithWelcome(ServerPlayerEntity player, MinecraftServer server) {
        // marked here, not at the join: a server that stops before the arrival must bring the player there at the next join
        Campaign.player(player).completeTutorial("arrived");
        Campaign.dirty(server);
        arrive(player, server);
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.translatable("title.lewandivka.district")));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.translatable("title.lewandivka.district.sub")));
        player.sendMessage(Text.translatable("message.lewandivka.welcome"), false);
        player.sendMessage(Text.translatable("message.lewandivka.open_world"), false);
    }

    /** Brings every player who is waiting for the arrival to the district now (the self test, which is not inside a tick of the game). */
    public static void flushArrivals(MinecraftServer server) {
        for (UUID id : new java.util.ArrayList<>(ARRIVALS.keySet())) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            ARRIVALS.remove(id);
            if (player != null) {
                arriveWithWelcome(player, server);
            }
        }
    }

    private static void arrivals(MinecraftServer server) {
        if (ARRIVALS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Integer>> it = ARRIVALS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> e = it.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(e.getKey());
            if (player == null) {
                it.remove();
            } else if (e.getValue() <= 1) {
                it.remove();
                arriveWithWelcome(player, server);
            } else {
                e.setValue(e.getValue() - 1);
            }
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
        } else if (player.getWorld().getRegistryKey() == World.OVERWORLD) {
            // the bed of this player is gone: the game falls back on the spawn of the ordinary overworld, which is another world than
            // the district (a bed replaces the spawn point the player was given on arrival); the spawn of the district is the way home
            Scheduler.later(3, () -> {
                if (!player.isRemoved() && player.getWorld().getRegistryKey() == World.OVERWORLD) {
                    int[] s = DistrictPlan.get().spawn();
                    Vec3d at = new Vec3d(s[0] + 0.5, s[1], s[2] + 0.5);
                    Travel.to(player, Dimensions.DISTRICT_ID, at);
                    player.setSpawnPoint(Dimensions.DISTRICT, BlockPos.ofFloored(at), 0.0f, true, false);
                    player.sendMessage(Text.translatable("message.lewandivka.respawn_home"), true);
                }
            });
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
        holdDistrictTime(server, step);
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
