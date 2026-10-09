package com.lewandivka.world.service;

import com.lewandivka.campaign.Campaign;
import com.lewandivka.core.flow.Checkpoints;
import com.lewandivka.world.dimension.Dimensions;
import com.lewandivka.world.structure.Structures;
import com.lewandivka.world.structure.Structures.Marker;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
 * Marked regions of the structures that act on players every few ticks:
 * <ul>
 *   <li>{@code fall_zone*}: the gorge under the approach and the pits of the tower. Falling in is never fatal: the
 *       player is put back at the last checkpoint of that structure</li>
 * </ul>
 * The {@code wind_*} regions (updrafts that carry a rider out of a shaft) are applied by the client, see {@link WindRegions}.
 */
public final class ZoneServices {

    private record Pit(String structure, String dimension, Box box) {
    }

    private static List<Pit> pits;
    /** Where a player was at the previous check, and when. */
    private record Sample(Vec3d pos, long tick, String dimension) {
    }

    /**
     * The previous checks of every player: the server does not simulate the motion of a player (the client does), so what a
     * player is doing is read from how far the positions it is sent have moved.
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
        if (pits != null) {
            return;
        }
        List<Pit> p = new ArrayList<>();
        for (Structures.Site site : Structures.sites()) {
            String structure = site.placement().id();
            for (Marker m : Structures.markersOf(structure)) {
                if (m.name().startsWith("fall_zone") && m.isRegion()) {
                    p.add(new Pit(structure, m.dimension(), m.box()));
                }
            }
        }
        pits = p;
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
            if (before != null && before.dimension().equals(dimension) && now - before.tick() <= 4) {
                Vec3d moved = at.subtract(before.pos());
                // a teleport is not a flight
                if (moved.lengthSquared() < 64.0) {
                    double ticks = Math.max(1, now - before.tick());
                    if (moved.y > 0.05 * ticks && !player.isTouchingWater()) {
                        CLIMBING_UNTIL.put(player.getUuid(), now + 10);
                    }
                }
            }
            boolean climbing = CLIMBING_UNTIL.getOrDefault(player.getUuid(), 0L) > now;
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
