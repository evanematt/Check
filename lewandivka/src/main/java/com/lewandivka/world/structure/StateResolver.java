package com.lewandivka.world.structure;

import com.lewandivka.LewandivkaMod;
import com.lewandivka.block.SpecBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns the block keys of the blueprints ({@code minecraft:oak_stairs[facing=north,half=bottom]},
 * {@code lewandivka:heavy_lever[facing=south,powered=false]}) into block states. Results are cached; the resolver is
 * used from the chunk generation threads.
 */
public final class StateResolver {

    private static final Map<String, BlockState> CACHE = new ConcurrentHashMap<>();

    private StateResolver() {
    }

    public static BlockState parse(String key) {
        return CACHE.computeIfAbsent(key, StateResolver::resolve);
    }

    private static BlockState resolve(String key) {
        int bracket = key.indexOf('[');
        String id = bracket < 0 ? key : key.substring(0, bracket);
        Identifier identifier = Identifier.tryParse(id);
        Block block = identifier == null ? Blocks.AIR : Registries.BLOCK.get(identifier);
        if (block == Blocks.AIR && !"minecraft:air".equals(id) && !"minecraft:cave_air".equals(id)) {
            LewandivkaMod.LOGGER.error("Unknown block '{}' in a structure blueprint", key);
            return Blocks.AIR.getDefaultState();
        }
        BlockState state = block.getDefaultState();
        if (bracket >= 0 && key.endsWith("]")) {
            for (String pair : key.substring(bracket + 1, key.length() - 1).split(",")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    state = apply(state, pair.substring(0, eq).trim(), pair.substring(eq + 1).trim(), key);
                }
            }
        }
        return state;
    }

    private static BlockState apply(BlockState state, String name, String value, String key) {
        if (state.getBlock() instanceof SpecBlock block) {
            return block.with(state, name, value);
        }
        Property<?> property = state.getBlock().getStateManager().getProperty(name);
        if (property == null) {
            LewandivkaMod.LOGGER.warn("Block '{}' has no property '{}'", key, name);
            return state;
        }
        return set(state, property, value);
    }

    private static <T extends Comparable<T>> BlockState set(BlockState state, Property<T> property, String value) {
        Optional<T> parsed = property.parse(value);
        return parsed.isPresent() ? state.with(property, parsed.get()) : state;
    }
}
