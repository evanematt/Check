package com.lewandivka.world.service;

import com.lewandivka.campaign.Campaign;
import com.lewandivka.core.flow.Checkpoints;
import com.lewandivka.world.dimension.Dimensions;
import com.lewandivka.world.structure.Structures;
import com.lewandivka.world.structure.Structures.Marker;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Two kinds of marked regions that act on players every few ticks:
 * <ul>
 *   <li>{@code wind_*}: an updraft with a push ({@code dir=east,speed=0.3}), used by the sky tube, the approach shaft
 *       and the service shaft of the tower</li>
 *   <li>{@code fall_zone*}: the gorge under the approach and the pits of the tower. Falling in is never fatal: the
 *       player is put back at the last checkpoint of that structure</li>
 * </ul>
 */
public final class ZoneServices {

    private record Wind(String dimension, Box box, double dx, double dz, double speed) {
    }

    private record Pit(String structure, String dimension, Box box) {
    }

    private static List<Wind> winds;
    private static List<Pit> pits;
    /** Where a player was at the previous check, and when. */
    private record Sample(Vec3d pos, long tick, String dimension) {
    }

    /**
     * The previous checks of every player. The server does not simulate the motion of a player (the client does), so
     * {@code getVelocity()} is stale: a wind that built on it sent a rising rider the velocity of a standing one and
     * killed the throw of a spring hatch. The real velocity is estimated from the movement between two checks.
     */
    private static final Map<UUID, Sample> LAST = new HashMap<>();
    /** Until when a player counts as "on the way up": a pit only catches whoever is not (a spring throw crosses one, and stops for a moment at the top). */
    private static final Map<UUID, Long> CLIMBING_UNTIL = new HashMap<>();

    private ZoneServices() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ZoneServices::tick);
    }

    private static synchronized void index() {
        if (winds != null) {
            return;
        }
        List<Wind> w = new ArrayList<>();
        List<Pit> p = new ArrayList<>();
        for (Structures.Site site : Structures.sites()) {
            String structure = site.placement().id();
            for (Marker m : Structures.markersOf(structure)) {
                if (m.name().startsWith("wind_") && m.isRegion()) {
                    double dx = 0;
                    double dz = 0;
                    switch (m.data("dir", "up")) {
                        case "east" -> dx = 1;
                        case "west" -> dx = -1;
                        case "south" -> dz = 1;
                        case "north" -> dz = -1;
                        default -> {
                        }
                    }
                    w.add(new Wind(m.dimension(), m.box(), dx, dz, Double.parseDouble(m.data("speed", "0.3"))));
                } else if (m.name().startsWith("fall_zone") && m.isRegion()) {
                    p.add(new Pit(structure, m.dimension(), m.box()));
                }
            }
        }
        pits = p;
        winds = w;
    }

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 2 != 0) {
            return;
        }
        index();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.isSpectator() || !player.isAlive() || !Dimensions.isOurs(player.getWorld())) {
                continue;
            }
            String dimension = Dimensions.idOf(player.getWorld());
            Vec3d at = player.getPos();
            long now = server.getTicks();
            Sample before = LAST.put(player.getUuid(), new Sample(at, now, dimension));
            Vec3d velocity = player.getVelocity();
            if (before != null && before.dimension().equals(dimension) && now - before.tick() <= 4) {
                Vec3d moved = at.subtract(before.pos());
                // a teleport is not a flight
                if (moved.lengthSquared() < 64.0) {
                    double ticks = Math.max(1, now - before.tick());
                    velocity = moved.multiply(1.0 / ticks);
                    if (moved.y > 0.05 * ticks && !player.isTouchingWater()) {
                        CLIMBING_UNTIL.put(player.getUuid(), now + 10);
                    }
                }
            }
            boolean climbing = CLIMBING_UNTIL.getOrDefault(player.getUuid(), 0L) > now;
            for (Wind wind : winds) {
                if (wind.dimension().equals(dimension) && wind.box().contains(at)) {
                    push(player, wind, velocity);
                }
            }
            for (Pit pit : pits) {
                if (!climbing && pit.dimension().equals(dimension) && pit.box().contains(at)) {
                    recover(server, player, pit);
                    break;
                }
            }
        }
        if (LAST.size() > 64) {
            LAST.keySet().removeIf(id -> server.getPlayerManager().getPlayer(id) == null);
            CLIMBING_UNTIL.keySet().removeIf(id -> server.getPlayerManager().getPlayer(id) == null);
        }
    }

    /** Blends the motion of the player towards the wind; whoever rises fast keeps the rise (v is the real velocity, see {@link #LAST}). */
    private static void push(ServerPlayerEntity player, Wind wind, Vec3d v) {
        double tx = wind.dx() * wind.speed();
        double tz = wind.dz() * wind.speed();
        double ny = v.y < 0.32 ? Math.min(0.42, v.y + 0.11) : v.y;
        player.setVelocity(v.x + (tx - v.x) * 0.25, ny, v.z + (tz - v.z) * 0.25);
        player.fallDistance = 0.0f;
        // one packet and no tracker update: the tracker would send the same numbers a tick later, when they are already out of date
        player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
    }

    private static void recover(MinecraftServer server, ServerPlayerEntity player, Pit pit) {
        int cp = Campaign.world(server).encounter(pit.structure()).checkpoint();
        String name = Checkpoints.marker(pit.structure(), cp);
        Marker m = Structures.marker(pit.structure() + ":" + (name == null ? "spawn" : name));
        if (m == null) {
            return;
        }
        com.lewandivka.quest.Travel.toMarker(player, m.id());
        player.setVelocity(Vec3d.ZERO);
        player.velocityModified = true;
        player.fallDistance = 0.0f;
        if (player.getHealth() > 6.0f) {
            player.damage(player.getDamageSources().fall(), 2.0f);
        }
        player.sendMessage(Text.translatable("message.lewandivka.fall_recovered"), true);
        player.getServerWorld().playSound(null, player.getBlockPos(), com.lewandivka.sound.GameSounds.get("ui.checkpoint"), SoundCategory.PLAYERS, 0.8f, 1.0f);
    }
}
