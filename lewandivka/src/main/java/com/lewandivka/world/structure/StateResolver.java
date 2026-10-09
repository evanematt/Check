package com.lewandivka.world.structure;

import com.lewandivka.LewandivkaMod;
import com.lewandivka.block.SpecBlock;
import com.lewandivka.core.structure.Keys;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns the block keys of the blueprints ({@code minecraft:oak_stairs[facing=north,half=bottom]},
 * {@code lewandivka:heavy_lever[facing=south,powered=false]}) into block states. Results are cached; the resolver is
 * used from the chunk generation threads.
 *
 * <p>The furniture of the rooms is named twice, {@code handcrafted:oak_chair[facing=north]|minecraft:oak_stairs[...]}: the
 * block of the furniture mod and, after the bar, the block of the game that stands in for it. The block of the mod is used
 * when the game has it (and, for a bed, when it is a bed of the game's own kind, so that it can be slept in) and all its
 * properties fit; otherwise the stand-in.</p>
 *
 * <p>A key that starts with a bang, {@code !trepscars|minecraft:blue_concrete}, is a block that is only there while the mod before the
 * bar is <i>not</i> installed: with the mod there is air (the cars of the district are creatures of Trep's Cars, made by
 * {@code CarPark}, and the blocks are the cars of a game without the mod).</p>
 */
public final class StateResolver {

    private static final Map<String, BlockState> CACHE = new ConcurrentHashMap<>();
    /** The furniture keys that became a block of the mod and those that became the stand-in. */
    private static final Set<String> FURNITURE_OF_MOD = ConcurrentHashMap.newKeySet();
    private static final Set<String> FURNITURE_STAND_IN = ConcurrentHashMap.newKeySet();

    private StateResolver() {
    }

    public static BlockState parse(String key) {
        return CACHE.computeIfAbsent(key, StateResolver::resolve);
    }

    /** How the furniture keys that were looked at so far came out: with the blocks of a furniture mod or those of the game. */
    public static String decorSummary() {
        int mod = FURNITURE_OF_MOD.size();
        int standIn = FURNITURE_STAND_IN.size();
        Set<String> kinds = new TreeSet<>();
        for (String key : FURNITURE_OF_MOD) {
            kinds.add(Keys.blockId(Keys.preferred(key)));
        }
        return "decor: " + (mod + standIn) + " furniture keys, " + mod + " with the blocks of a furniture mod, " + standIn
                + " with the stand-in of the game" + (kinds.isEmpty() ? "" : " (" + kinds.size() + " kinds of blocks: " + kinds + ")");
    }

    private static BlockState resolve(String key) {
        int bar = key.indexOf('|');
        if (bar < 0) {
            return resolveBlock(key);
        }
        if (Keys.isUnlessMod(key)) {
            // a block that is only there while the mod is not: with the mod loaded the creature of the mod stands in its place (the cars)
            return FabricLoader.getInstance().isModLoaded(Keys.modOf(key)) ? Blocks.AIR.getDefaultState() : resolveBlock(key.substring(bar + 1));
        }
        BlockState standIn = resolveBlock(key.substring(bar + 1));
        BlockState mod = resolveFurniture(key.substring(0, bar), standIn);
        if (mod != null) {
            FURNITURE_OF_MOD.add(key);
            return mod;
        }
        FURNITURE_STAND_IN.add(key);
        return standIn;
    }

    /** The block of a furniture mod, or null when the game does not have it or when something about the key does not fit. */
    private static BlockState resolveFurniture(String key, BlockState standIn) {
        int bracket = key.indexOf('[');
        String id = bracket < 0 ? key : key.substring(0, bracket);
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null || !Registries.BLOCK.containsId(identifier)) {
            return null;   // the mod is not installed
        }
        Block block = Registries.BLOCK.get(identifier);
        if (standIn.getBlock() instanceof BedBlock && !(block instanceof BedBlock)) {
            LewandivkaMod.LOGGER.warn("Furniture block '{}' stands for a bed but is not one: the bed of the game is used", id);
            return null;
        }
        BlockState state = block.getDefaultState();
        if (bracket >= 0 && key.endsWith("]")) {
            for (String pair : key.substring(bracket + 1, key.length() - 1).split(",")) {
                int eq = pair.indexOf('=');
                Property<?> property = eq > 0 ? block.getStateManager().getProperty(pair.substring(0, eq).trim()) : null;
                BlockState next = property == null ? null : with(state, property, pair.substring(eq + 1).trim());
                if (next == null) {
                    LewandivkaMod.LOGGER.warn("Furniture block '{}' does not fit the mod installed ({} is not a property with such a value): the stand-in is used", key, pair);
                    return null;
                }
                state = next;
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
        Optional<T> parsed = property.parse(value);
        return parsed.isPresent() ? state.with(property, parsed.get()) : null;
    }

    private static BlockState resolveBlock(String key) {
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
