package com.lewandivka.ability;

import com.lewandivka.block.SpecBlock;
import com.lewandivka.core.registry.BlockSpec.Behaviour;
import com.lewandivka.flow.FlowHost;
import com.lewandivka.world.dimension.Dimensions;
import com.lewandivka.world.structure.Structures;
import com.lewandivka.world.structure.Structures.Marker;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** A dash that hits a dash door opens it: the door marker is handed to the flow of the structure. */
final class DashDoors {

    private DashDoors() {
    }

    static void check(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Vec3d v = player.getVelocity();
        Vec3d dir = new Vec3d(v.x, 0, v.z);
        if (dir.lengthSquared() < 0.01) {
            Vec3d look = player.getRotationVec(1.0f);
            dir = new Vec3d(look.x, 0, look.z);
        }
        if (dir.lengthSquared() < 1.0E-4) {
            return;
        }
        dir = dir.normalize();
        for (double d = 0.6; d <= 2.0; d += 0.7) {
            Vec3d at = player.getPos().add(dir.multiply(d));
            for (int dy = 0; dy <= 1; dy++) {
                BlockPos pos = BlockPos.ofFloored(at.x, at.y + dy, at.z);
                BlockState state = world.getBlockState(pos);
                if (state.getBlock() instanceof SpecBlock b && b.spec.behaviour == Behaviour.DASH_GATE) {
                    open(player, world, pos);
                    return;
                }
            }
        }
    }

    private static void open(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        String structure = Structures.structureAt(Dimensions.idOf(world), pos);
        if (structure == null) {
            return;
        }
        for (Marker m : Structures.withPrefix(structure, "dash_door_")) {
            if (m.contains(pos)) {
                com.lewandivka.LewandivkaMod.LOGGER.info("dash door {} hit by {}", m.id(), player.getGameProfile().getName());
                var flow = FlowHost.flow(world.getServer(), structure);
                if (flow != null) {
                    flow.use(m.name(), player.getUuid());
                }
                return;
            }
        }
    }
}
