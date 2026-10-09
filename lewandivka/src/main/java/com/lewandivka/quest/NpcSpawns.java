package com.lewandivka.quest;

import com.lewandivka.LewandivkaMod;
import com.lewandivka.entity.GameEntities;
import com.lewandivka.entity.LewMob;
import com.lewandivka.world.dimension.Dimensions;
import com.lewandivka.world.structure.Structures;
import com.lewandivka.world.structure.Structures.Marker;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

/** Story npcs: spawned once when the story calls for them and brought back if they ever disappear. */
public final class NpcSpawns {

    private NpcSpawns() {
    }

    /** Story key (see the campaign director) to the creatures and the markers they appear at. */
    public static void spawn(MinecraftServer server, String npc) {
        switch (npc) {
            case "pan_shlahbaum" -> once(server, "pan_shlahbaum", "district:shlahbaum_spot");
            case "debtor" -> once(server, "debtor", "district:debtor_spawn");
            case "chinazik" -> once(server, "chinazik", "shelter:cat_start");
            case "metadonna" -> once(server, "metadonna", "shelter:cat_spot_metadonna");
            case "base_cats" -> {
                once(server, "chinazik", "base:cat_spot_chinazik");
                once(server, "metadonna", "base:cat_spot_metadonna");
            }
            case "base_epilogue" -> {
                once(server, "chinazik", "base:cat_spot_chinazik");
                once(server, "metadonna", "base:cat_spot_metadonna");
            }
            case "base_empty" -> {
            }
            default -> LewandivkaMod.LOGGER.warn("Unknown story npc {}", npc);
        }
    }

    /** Spawns the creature at the marker unless one of its kind lives within 160 blocks of it. */
    public static Entity once(MinecraftServer server, String entityId, String markerId) {
        Marker m = Structures.marker(markerId);
        if (m == null || !GameEntities.has(entityId)) {
            LewandivkaMod.LOGGER.warn("Cannot spawn {} at {}", entityId, markerId);
            return null;
        }
        ServerWorld world = Dimensions.world(server, m.dimension());
        if (world == null) {
            return null;
        }
        EntityType<?> type = GameEntities.type(entityId);
        Box near = Box.of(m.stand(), 320, 200, 320);
        for (Entity e : world.getEntitiesByType(type, near, Entity::isAlive)) {
            return e;
        }
        world.getChunk(m.pos());
        Entity e = type.create(world);
        if (e == null) {
            return null;
        }
        var at = Travel.safe(world, m.isRegion() ? m.center() : m.stand());
        e.refreshPositionAndAngles(at.x, at.y, at.z, 0.0f, 0.0f);
        if (e instanceof MobEntity mob) {
            mob.setPersistent();
        }
        world.spawnEntity(e);
        return e;
    }

    private static final java.util.Map<String, Integer> MISSING = new java.util.HashMap<>();

    /**
     * Story npcs that must always stand somewhere are looked after every five seconds while somebody is near: one that
     * has gone is created again, a second one is removed. The check only counts what is loaded, so it needs a player in
     * the neighbourhood and two empty looks in a row (entities of a freshly loaded chunk arrive a moment later); without
     * that every server restart would add one more shopkeeper.
     */
    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 100 != 7) {
            return;
        }
        if (com.lewandivka.campaign.Campaign.step(server).isAtLeast(com.lewandivka.core.campaign.QuestStep.TALK_SHLAHBAUM)) {
            keep(server, "pan_shlahbaum", "district:shlahbaum_spot");
        }
    }

    private static void keep(MinecraftServer server, String entityId, String markerId) {
        Marker m = Structures.marker(markerId);
        ServerWorld world = m == null ? null : Dimensions.world(server, m.dimension());
        if (world == null || !GameEntities.has(entityId)) {
            return;
        }
        net.minecraft.util.math.Vec3d at = m.stand();
        if (world.getPlayers(p -> p.squaredDistanceTo(at) < 80 * 80).isEmpty()) {
            MISSING.remove(entityId);
            return;
        }
        java.util.List<? extends Entity> found = world.getEntitiesByType(GameEntities.type(entityId), Box.of(at, 320, 200, 320), Entity::isAlive);
        for (int i = 1; i < found.size(); i++) {
            found.get(i).discard();
        }
        if (!found.isEmpty()) {
            MISSING.remove(entityId);
        } else if (MISSING.merge(entityId, 1, Integer::sum) >= 2) {
            MISSING.remove(entityId);
            once(server, entityId, markerId);
        }
    }

    /** Used by the cat entities to find out which creature a lewandivka mob is. */
    public static boolean isStory(Entity e) {
        return e instanceof LewMob mob && switch (mob.spec().role) {
            case CAT, SHLAHBAUM, DEBTOR -> true;
            default -> false;
        };
    }
}
