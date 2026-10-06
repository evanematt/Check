package com.lewandivka.quest;

import com.lewandivka.world.dimension.Dimensions;
import com.lewandivka.world.structure.Structures;
import com.lewandivka.world.structure.Structures.Marker;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Teleports between the two dimensions and to markers, always onto a free spot. */
public final class Travel {

    private Travel() {
    }

    public static boolean toMarker(ServerPlayerEntity player, String markerId) {
        Marker m = Structures.marker(markerId);
        if (m == null) {
            return false;
        }
        ServerWorld world = Dimensions.world(player.getServer(), m.dimension());
        if (world == null) {
            return false;
        }
        Vec3d at = safe(world, m.isRegion() ? m.center() : m.stand());
        player.teleport(world, at.x, at.y, at.z, player.getYaw(), 0.0f);
        player.fallDistance = 0.0f;
        return true;
    }

    public static void to(ServerPlayerEntity player, String dimension, Vec3d wanted) {
        ServerWorld world = Dimensions.world(player.getServer(), dimension);
        if (world == null) {
            return;
        }
        Vec3d at = safe(world, wanted);
        player.teleport(world, at.x, at.y, at.z, player.getYaw(), 0.0f);
        player.fallDistance = 0.0f;
    }

    /** Moves a position up until feet and head are free (markers sometimes sit inside the floor block). */
    public static Vec3d safe(ServerWorld world, Vec3d wanted) {
        world.getChunk(BlockPos.ofFloored(wanted));
        BlockPos p = BlockPos.ofFloored(wanted);
        for (int i = 0; i < 8; i++) {
            if (world.getBlockState(p).getCollisionShape(world, p).isEmpty()
                    && world.getBlockState(p.up()).getCollisionShape(world, p.up()).isEmpty()) {
                return new Vec3d(wanted.x, p.getY(), wanted.z);
            }
            p = p.up();
        }
        return wanted;
    }
}
