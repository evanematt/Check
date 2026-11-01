package com.lewandivka.command;

import com.lewandivka.LewandivkaMod;
import com.lewandivka.world.dimension.Dimensions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;
import java.util.UUID;

/**
 * Development tool ({@code /lewandivka trace <player> <ticks>}): logs what the server knows about a player on every tick
 * (position, velocity, ground flag, fall distance), to compare with what the client saw when a launch went wrong.
 */
public final class ServerTrace {

    private static UUID target;
    private static int left;

    private ServerTrace() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ServerTrace::tick);
    }

    public static String start(MinecraftServer server, String name, int ticks) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(name);
        if (player == null) {
            return "no player " + name;
        }
        target = player.getUuid();
        left = Math.max(1, ticks);
        return "tracing " + name + " for " + left + " ticks";
    }

    private static void tick(MinecraftServer server) {
        if (left <= 0 || target == null) {
            return;
        }
        left--;
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(target);
        if (p == null) {
            left = 0;
            return;
        }
        Vec3d v = p.getVelocity();
        LewandivkaMod.LOGGER.info("[trace] t={} {} pos=({}, {}, {}) vel=({}, {}, {}) ground={} fall={}", server.getTicks(), Dimensions.idOf(p.getServerWorld()),
                String.format(Locale.ROOT, "%.2f", p.getX()), String.format(Locale.ROOT, "%.2f", p.getY()), String.format(Locale.ROOT, "%.2f", p.getZ()),
                String.format(Locale.ROOT, "%.2f", v.x), String.format(Locale.ROOT, "%.2f", v.y), String.format(Locale.ROOT, "%.2f", v.z),
                p.isOnGround(), String.format(Locale.ROOT, "%.2f", p.fallDistance));
    }
}
