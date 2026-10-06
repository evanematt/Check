package com.lewandivka.world.service;

import com.lewandivka.LewandivkaMod;
import com.lewandivka.world.structure.StateResolver;
import com.lewandivka.world.structure.Structures;
import com.lewandivka.world.structure.Structures.Marker;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Quest gates: region markers that remember the block they are made of when closed ({@code closed=<key>}). Opening
 * clears the region, closing fills it again. Flows call this for every door, grate and hatch of the dungeons.
 */
public final class Gates {

    private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

    private Gates() {
    }

    /** The block a gate is made of. */
    public static BlockState closedState(Marker gate) {
        String data = gate.data();
        String key = data.startsWith("closed=") ? data.substring("closed=".length()) : "minecraft:stone";
        return StateResolver.parse(key);
    }

    /** @return false when the gate does not exist */
    public static boolean set(ServerWorld world, String structure, String gate, boolean open) {
        Marker m = Structures.marker(structure + ":" + gate);
        if (m == null || !m.isRegion()) {
            LewandivkaMod.LOGGER.warn("Unknown gate {}:{}", structure, gate);
            return false;
        }
        BlockState state = open ? Blocks.AIR.getDefaultState() : closedState(m);
        BlockPos.Mutable p = new BlockPos.Mutable();
        for (int x = m.x(); x < m.x() + m.sx(); x++) {
            for (int y = m.y(); y < m.y() + m.sy(); y++) {
                for (int z = m.z(); z < m.z() + m.sz(); z++) {
                    p.set(x, y, z);
                    BlockState current = world.getBlockState(p);
                    if (current != state) {
                        world.setBlockState(p, state, FLAGS);
                    }
                }
            }
        }
        return true;
    }

    public static boolean isOpen(ServerWorld world, String structure, String gate) {
        Marker m = Structures.marker(structure + ":" + gate);
        return m != null && world.getBlockState(m.pos()).isAir();
    }
}
