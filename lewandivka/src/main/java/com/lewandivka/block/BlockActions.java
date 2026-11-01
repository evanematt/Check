package com.lewandivka.block;

import com.lewandivka.core.registry.BlockSpec.Behaviour;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.EnumMap;
import java.util.Map;

/**
 * Server-side behaviour of the mod blocks. The blocks themselves are thin; the services of the game (quests, flows,
 * abilities, travel) register the handlers for the behaviours they own, so no block class knows the story.
 */
public final class BlockActions {

    /** Right click on a block with the given behaviour. */
    @FunctionalInterface
    public interface UseHandler {
        ActionResult use(ServerWorld world, BlockPos pos, BlockState state, SpecBlock block, ServerPlayerEntity player, Hand hand);
    }

    /** An entity touches (stands on, walks into) a block with the given behaviour. */
    @FunctionalInterface
    public interface TouchHandler {
        void touch(ServerWorld world, BlockPos pos, BlockState state, SpecBlock block, Entity entity);
    }

    /** A player placed the kiosk. */
    @FunctionalInterface
    public interface PlaceHandler {
        void placed(ServerWorld world, BlockPos pos, BlockState state, SpecBlock block, ServerPlayerEntity player);
    }

    private static final Map<Behaviour, UseHandler> USE = new EnumMap<>(Behaviour.class);
    private static final Map<Behaviour, TouchHandler> TOUCH = new EnumMap<>(Behaviour.class);
    private static PlaceHandler kioskPlaced = (w, p, s, b, pl) -> { };

    private BlockActions() {
    }

    public static void onUse(Behaviour behaviour, UseHandler handler) {
        USE.put(behaviour, handler);
    }

    public static void onTouch(Behaviour behaviour, TouchHandler handler) {
        TOUCH.put(behaviour, handler);
    }

    public static void onKioskPlaced(PlaceHandler handler) {
        kioskPlaced = handler;
    }

    static ActionResult use(ServerWorld world, BlockPos pos, BlockState state, SpecBlock block, ServerPlayerEntity player, Hand hand) {
        UseHandler h = USE.get(block.spec.behaviour);
        return h == null ? ActionResult.PASS : h.use(world, pos, state, block, player, hand);
    }

    static void spring(ServerWorld world, BlockPos pos, BlockState state, SpecBlock block, Entity entity) {
        touch(Behaviour.SPRING, world, pos, state, block, entity);
    }

    static void portal(ServerWorld world, BlockPos pos, BlockState state, SpecBlock block, Entity entity) {
        touch(Behaviour.PORTAL, world, pos, state, block, entity);
    }

    static void greyVoid(ServerWorld world, BlockPos pos, Entity entity) {
        TouchHandler h = TOUCH.get(Behaviour.VOID);
        if (h != null) {
            h.touch(world, pos, world.getBlockState(pos), (SpecBlock) world.getBlockState(pos).getBlock(), entity);
        }
    }

    static void kioskPlaced(ServerWorld world, BlockPos pos, BlockState state, SpecBlock block, ServerPlayerEntity player) {
        kioskPlaced.placed(world, pos, state, block, player);
    }

    private static void touch(Behaviour behaviour, ServerWorld world, BlockPos pos, BlockState state, SpecBlock block, Entity entity) {
        TouchHandler h = TOUCH.get(behaviour);
        if (h != null) {
            h.touch(world, pos, state, block, entity);
        }
    }
}
