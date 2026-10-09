package com.lewandivka.quest;

import com.lewandivka.world.dimension.Dimensions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameRules;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the time of day of a campaign dimension while a story beat needs it: the endless first evening of the
 * exploration, the first night of the token hunt, the night of the last tram. A hold that is ahead of the clock is
 * reached like a time-lapse (a few seconds), one that is behind is a cut. Several reasons can hold a dimension at once;
 * the latest time of day wins, and the clock runs again when the last reason lets go. The hold only works while somebody is
 * in town (see {@link Town}): the open country around the city has the ordinary days and nights of the survival game.
 *
 * <p>The campaign dimensions share the clock of the overworld (derived worlds ignore writes to their own time), so it is
 * the overworld clock that is moved.</p>
 */
public final class TimeControl {

    /** The sky is turning orange: the evening of the first arrival. */
    public static final long DUSK = 12600L;
    public static final long NIGHT = 18000L;

    private static final Map<String, Map<String, Long>> HELD = new HashMap<>();

    private TimeControl() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(TimeControl::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> HELD.clear());
    }

    /** The night of an encounter flow (the last tram). */
    public static void holdNight(MinecraftServer server, String dimension, boolean on) {
        holdTime(dimension, "flow", NIGHT, on);
    }

    public static void holdNight(MinecraftServer server, String dimension, String reason, boolean on) {
        holdTime(dimension, reason, NIGHT, on);
    }

    public static void holdTime(String dimension, String reason, long timeOfDay, boolean on) {
        Map<String, Long> reasons = HELD.computeIfAbsent(dimension, k -> new HashMap<>());
        if (on) {
            reasons.put(reason, timeOfDay);
        } else {
            reasons.remove(reason);
        }
    }

    /** Puts the clock at a time of day of the current day without any time-lapse (the first arrival). */
    public static void set(MinecraftServer server, long timeOfDay) {
        ServerWorld clock = server.getOverworld();
        clock.setTimeOfDay(Math.floorDiv(clock.getTimeOfDay(), 24000L) * 24000L + timeOfDay);
    }

    /**
     * Beds work in the open country. The world wakes everybody up when all of them have slept long enough, but it cannot move
     * the clock of a campaign dimension (it is the clock of the overworld, see above), so the night would simply go on: the
     * clock is moved here, to the next morning.
     */
    private static void sleep(MinecraftServer server) {
        if (server.getTicks() % 4 != 0) {
            return;
        }
        ServerWorld world = Dimensions.district(server);
        if (world == null || !world.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE)) {
            return;
        }
        List<ServerPlayerEntity> players = world.getPlayers(p -> !p.isSpectator());
        if (players.isEmpty() || !players.stream().allMatch(PlayerEntity::canResetTimeBySleeping)) {
            return;
        }
        ServerWorld clock = server.getOverworld();
        long now = clock.getTimeOfDay();
        clock.setTimeOfDay(now - Math.floorMod(now, 24000L) + 24000L);
        server.getPlayerManager().sendToDimension(new WorldTimeUpdateS2CPacket(world.getTime(), clock.getTimeOfDay(), true), world.getRegistryKey());
    }

    private static void tick(MinecraftServer server) {
        sleep(server);
        if (HELD.isEmpty() || server.getTicks() % 2 != 0) {
            return;
        }
        for (Map.Entry<String, Map<String, Long>> held : HELD.entrySet()) {
            if (!held.getValue().isEmpty()) {
                follow(server, held.getKey(), Collections.max(held.getValue().values()));
            }
        }
    }

    private static void follow(MinecraftServer server, String dimension, long target) {
        ServerWorld world = Dimensions.world(server, dimension);
        if (world == null) {
            return;
        }
        // the story holds the evening and the night for the people in town; out in the open country the days run as usual
        if (Dimensions.DISTRICT_ID.equals(dimension) && world.getPlayers(p -> !p.isSpectator() && Town.contains(p)).isEmpty()) {
            return;
        }
        ServerWorld clock = server.getOverworld();
        long now = clock.getTimeOfDay();
        long day = Math.floorDiv(now, 24000L);
        long tod = Math.floorMod(now, 24000L);
        long diff = target - tod;
        if (diff == 0) {
            return;
        }
        // ahead: a time-lapse of about four seconds; behind (or a hair ahead): a cut, which also freezes the clock
        long next = diff > 3 ? tod + Math.min(diff, Math.max(100L, diff / 40L)) : target;
        clock.setTimeOfDay(day * 24000L + next);
        server.getPlayerManager().sendToDimension(new WorldTimeUpdateS2CPacket(world.getTime(), world.getTimeOfDay(),
                world.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE)), world.getRegistryKey());
    }
}
