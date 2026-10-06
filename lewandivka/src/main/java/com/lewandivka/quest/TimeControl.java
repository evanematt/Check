package com.lewandivka.quest;

import com.lewandivka.world.dimension.Dimensions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

import java.util.HashSet;
import java.util.Set;

/** Keeps a dimension at cinematic night while an encounter needs it (the last tram). */
public final class TimeControl {

    private static final long NIGHT = 18000L;
    private static final Set<String> HELD = new HashSet<>();

    private TimeControl() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(TimeControl::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> HELD.clear());
    }

    public static void holdNight(MinecraftServer server, String dimension, boolean on) {
        if (on) {
            HELD.add(dimension);
            apply(server, dimension);
        } else {
            HELD.remove(dimension);
        }
    }

    private static void tick(MinecraftServer server) {
        if (!HELD.isEmpty() && server.getTicks() % 40 == 0) {
            for (String dimension : HELD) {
                apply(server, dimension);
            }
        }
    }

    /**
     * The campaign dimensions share the clock of the overworld (derived worlds ignore writes to their own time), so the
     * night is held on the overworld; Chromandivka has a fixed time of day and does not care.
     */
    private static void apply(MinecraftServer server, String dimension) {
        if (Dimensions.world(server, dimension) != null) {
            ServerWorld clock = server.getOverworld();
            long day = clock.getTimeOfDay() / 24000L;
            clock.setTimeOfDay(day * 24000L + NIGHT);
        }
    }
}
