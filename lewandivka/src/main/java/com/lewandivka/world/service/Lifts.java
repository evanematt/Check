package com.lewandivka.world.service;

import com.lewandivka.block.GameBlocks;
import com.lewandivka.sound.GameSounds;
import com.lewandivka.util.Scheduler;
import com.lewandivka.world.structure.Structures;
import com.lewandivka.world.structure.Structures.Marker;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/**
 * Garage lifts: a 3 x 3 plate of {@code garage_lift} blocks marked by a region with {@code rise=<n>}. Moving a lift
 * shifts the plate one block every four ticks and lifts everything that stands on it, so the lift never crushes anybody
 * and never leaves a gap that is not visible.
 */
public final class Lifts {

    public static final int STEP_TICKS = 4;

    private Lifts() {
    }

    private static int rise(Marker m) {
        try {
            return Integer.parseInt(m.data("rise", "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** True when the plate is at its upper position. */
    public static boolean isUp(ServerWorld world, Marker m) {
        BlockState lift = GameBlocks.get("garage_lift").getDefaultState();
        return world.getBlockState(new BlockPos(m.x(), m.y() + rise(m), m.z())) == lift;
    }

    /** Moves the lift to the upper or lower position. @return false when it is there already or does not exist */
    public static boolean move(ServerWorld world, String structure, String lift, boolean up) {
        Marker m = Structures.marker(structure + ":" + lift);
        if (m == null || rise(m) <= 0) {
            return false;
        }
        int rise = rise(m);
        if (isUp(world, m) == up) {
            return false;
        }
        BlockState plate = GameBlocks.get("garage_lift").getDefaultState();
        world.playSound(null, new BlockPos(m.x(), m.y(), m.z()), GameSounds.get("garage.lift"), SoundCategory.BLOCKS, 1.0f, 1.0f);
        for (int step = 0; step < rise; step++) {
            final int fromY = up ? m.y() + step : m.y() + rise - step;
            final int toY = up ? fromY + 1 : fromY - 1;
            Scheduler.later((long) step * STEP_TICKS + 1, () -> shift(world, m, plate, fromY, toY));
        }
        return true;
    }

    private static void shift(ServerWorld world, Marker m, BlockState plate, int fromY, int toY) {
        boolean up = toY > fromY;
        Box above = new Box(m.x(), Math.min(fromY, toY) + 1, m.z(), m.x() + m.sx(), Math.max(fromY, toY) + 3, m.z() + m.sz());
        BlockPos.Mutable p = new BlockPos.Mutable();
        // lift what stands on the plate first when going up (so nobody is inside the new plate), after it when going down
        if (up) {
            carry(world, above, 1);
        }
        for (int x = m.x(); x < m.x() + m.sx(); x++) {
            for (int z = m.z(); z < m.z() + m.sz(); z++) {
                world.setBlockState(p.set(x, fromY, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                world.setBlockState(p.set(x, toY, z), plate, Block.NOTIFY_LISTENERS);
            }
        }
        if (!up) {
            carry(world, new Box(m.x(), toY + 1, m.z(), m.x() + m.sx(), toY + 3, m.z() + m.sz()), -1);
        }
    }

    private static void carry(ServerWorld world, Box box, int dy) {
        for (Entity e : world.getOtherEntities(null, box, x -> !x.isSpectator())) {
            e.requestTeleport(e.getX(), e.getY() + dy, e.getZ());
        }
    }
}
