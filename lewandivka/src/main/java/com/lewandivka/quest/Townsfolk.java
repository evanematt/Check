package com.lewandivka.quest;

import com.lewandivka.LewandivkaMod;
import com.lewandivka.core.registry.EntitySpec;
import com.lewandivka.core.registry.ModEntities;
import com.lewandivka.core.world.gen.Populace;
import com.lewandivka.entity.GameEntities;
import com.lewandivka.entity.npc.VendorEntity;
import com.lewandivka.world.dimension.Dimensions;
import com.lewandivka.world.structure.Structures;
import com.lewandivka.world.structure.Structures.Marker;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The people of the district (see {@link Populace}): wherever a player is near one of their places, somebody stands there. One
 * who has gone is made again, like the shopkeeper of the story is; the check only counts what is loaded, so it needs a player
 * close by and two empty looks in a row (the creatures of a freshly loaded chunk arrive a moment later), otherwise every restart
 * of the server would add one more baker.
 */
public final class Townsfolk {

    private Townsfolk() {
    }

    private static final Map<String, Integer> MISSING = new HashMap<>();
    /** A player this close makes the place count (its chunk is then surely loaded and ticking). */
    private static final double NEAR = 48.0;

    /** Every five seconds, one place after the other. */
    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 100 != 33) {
            return;
        }
        for (Populace.Spot spot : Populace.spots()) {
            keep(server, spot);
        }
    }

    private static void keep(MinecraftServer server, Populace.Spot spot) {
        Marker m = Structures.marker(spot.marker());
        ServerWorld world = m == null ? null : Dimensions.world(server, m.dimension());
        if (world == null || !GameEntities.has(spot.entity())) {
            return;
        }
        Vec3d at = m.stand();
        if (world.getPlayers(p -> p.squaredDistanceTo(at) < NEAR * NEAR).isEmpty()) {
            MISSING.remove(spot.marker());
            return;
        }
        if (!stands(world, spot, at)) {
            if (MISSING.merge(spot.marker(), 1, Integer::sum) >= 2) {
                MISSING.remove(spot.marker());
                make(world, spot, m);
            }
        } else {
            MISSING.remove(spot.marker());
        }
    }

    private static boolean stands(ServerWorld world, Populace.Spot spot, Vec3d at) {
        EntityType<?> type = GameEntities.type(spot.entity());
        return !world.getEntitiesByType(type, Box.of(at, 20, 16, 20), Entity::isAlive).isEmpty();
    }

    /** Makes the creature of a place at the marker. */
    private static Entity make(ServerWorld world, Populace.Spot spot, Marker m) {
        EntityType<?> type = GameEntities.type(spot.entity());
        Entity e = type.create(world);
        if (e == null) {
            return null;
        }
        Vec3d at = Travel.safe(world, m.stand());
        boolean trader = e instanceof VendorEntity;
        // the traders face the customers (the front of a stall looks north); the citizens look anywhere
        float yaw = trader ? 180.0f : world.getRandom().nextInt(360);
        e.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0.0f);
        if (e instanceof MobEntity mob) {
            mob.setPersistent();
            mob.setHeadYaw(yaw);
            mob.setBodyYaw(yaw);
        }
        world.spawnEntity(e);
        return e;
    }

    /** Development tool: makes everybody of every place now, loading the chunks (what players do by coming near). */
    public static String makeAll(MinecraftServer server) {
        int made = 0;
        int already = 0;
        int offers = 0;
        StringBuilder trades = new StringBuilder();
        for (Populace.Spot spot : Populace.spots()) {
            Marker m = Structures.marker(spot.marker());
            ServerWorld world = m == null ? null : Dimensions.world(server, m.dimension());
            if (world == null) {
                LewandivkaMod.LOGGER.warn("[populace] no place {}", spot.marker());
                continue;
            }
            world.getChunk(m.pos());
            if (stands(world, spot, m.stand())) {
                already++;
            } else {
                Entity e = make(world, spot, m);
                made += e == null ? 0 : 1;
                if (e instanceof VendorEntity v) {
                    offers += v.getOffers().size();
                    trades.append(' ').append(spot.entity()).append('=').append(v.getOffers().size());
                }
            }
        }
        List<EntitySpec> people = ModEntities.ALL.stream()
                .filter(s -> s.role == EntitySpec.Role.CITIZEN || s.role == EntitySpec.Role.VENDOR).toList();
        return "populace: " + Populace.spots().size() + " places, " + made + " made now, " + already + " stood already, " + people.size()
                + " kinds of people, " + offers + " offers of the traders made now:" + trades;
    }
}
