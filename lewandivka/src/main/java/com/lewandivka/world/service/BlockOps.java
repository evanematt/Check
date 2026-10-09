package com.lewandivka.world.service;

import com.lewandivka.block.SpecBlock;
import com.lewandivka.world.structure.StateResolver;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

/** Small block edits used by the flows: change one property of a block or replace it by a catalog key. */
public final class BlockOps {

    private static final int FLAGS = Block.NOTIFY_ALL;

    private BlockOps() {
    }

    public static void setProp(ServerWorld world, BlockPos pos, String name, String value) {
        BlockState state = world.getBlockState(pos);
        BlockState next = state;
        if (state.getBlock() instanceof SpecBlock sb) {
            next = sb.with(state, name, value);
        } else {
            Property<?> p = state.getBlock().getStateManager().getProperty(name);
            if (p != null) {
                next = with(state, p, value);
            }
        }
        if (next != state) {
            world.setBlockState(pos, next, FLAGS);
        }
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> p, String value) {
        Optional<T> v = p.parse(value);
        return v.isPresent() ? state.with(p, v.get()) : state;
    }

    public static void set(ServerWorld world, BlockPos pos, String key) {
        BlockState state = StateResolver.parse(key);
        if (world.getBlockState(pos) != state) {
            world.setBlockState(pos, state, FLAGS);
        }
    }
}
