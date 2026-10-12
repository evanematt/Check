package com.lewandivka.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/** Small helpers for "who is around here" queries (bounded searches only, never world scans). */
public final class Aabbs {

    private Aabbs() {
    }

    public static List<ServerPlayerEntity> players(ServerWorld world, Box box) {
        return world.getEntitiesByClass(ServerPlayerEntity.class, box, p -> p.isAlive() && !p.isSpectator());
    }

    public static List<ServerPlayerEntity> playersAround(ServerWorld world, Vec3d center, double radius) {
        return players(world, new Box(center.x - radius, center.y - radius, center.z - radius, center.x + radius, center.y + radius, center.z + radius));
    }

    public static <T extends Entity> List<T> around(ServerWorld world, Class<T> type, Vec3d center, double radius) {
        return world.getEntitiesByClass(type, new Box(center.x - radius, center.y - radius, center.z - radius, center.x + radius, center.y + radius, center.z + radius), Entity::isAlive);
    }

    public static PlayerEntity nearestPlayer(ServerWorld world, Vec3d center, double radius) {
        PlayerEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (PlayerEntity p : playersAround(world, center, radius)) {
            double d = p.squaredDistanceTo(center);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    public static boolean isLiving(Entity e) {
        return e instanceof LivingEntity;
    }
}
