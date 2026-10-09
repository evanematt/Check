package com.lewandivka.quest;

import com.lewandivka.campaign.Campaign;
import com.lewandivka.campaign.PartyService;
import com.lewandivka.core.story.Events;
import com.lewandivka.network.Net;
import com.lewandivka.util.Scheduler;
import com.lewandivka.world.structure.Structures;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.Map;

/**
 * Cutscenes. The client draws the letterbox, the title and the fade; the server decides how long a scene lasts, what
 * happens at its end (the transition to Chromandivka, the credits) and never blocks the players.
 */
public final class Cinematics {

    private static final Map<String, Integer> LENGTH = Map.of(
            "last_tram", 120,
            "transition", 160,
            "ring_restored", 200,
            "rescue_ring", 200,
            "credits", 1500);

    private Cinematics() {
    }

    public static void play(MinecraftServer server, String id) {
        List<ServerPlayerEntity> players = PartyService.players(server);
        int ticks = LENGTH.getOrDefault(id, 100);
        Net.cinematic(players, id, ticks);
        switch (id) {
            case "transition" -> Scheduler.later(ticks - 20, () -> transition(server));
            case "ring_restored" -> Scheduler.later(ticks, () -> Story.event(server, Events.RING_RESTORED));
            case "rescue_ring" -> Scheduler.later(ticks, () -> {
                for (ServerPlayerEntity p : PartyService.players(server)) {
                    Travel.toMarker(p, "base:spawn");
                }
            });
            default -> {
            }
        }
    }

    /** Everybody who is alive wakes up in the base of Chromandivka. */
    private static void transition(MinecraftServer server) {
        if (!Structures.has("base:spawn")) {
            return;
        }
        for (ServerPlayerEntity p : PartyService.players(server)) {
            Travel.toMarker(p, "base:spawn");
            p.setSpawnPoint(com.lewandivka.world.dimension.Dimensions.CHROMA, p.getBlockPos(), 0.0f, true, false);
        }
        Scheduler.later(60, () -> {
            Story.event(server, Events.TRANSITION_DONE);
            Campaign.dirty(server);
        });
    }
}
