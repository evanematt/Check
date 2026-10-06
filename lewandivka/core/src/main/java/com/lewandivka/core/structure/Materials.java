package com.lewandivka.core.structure;

import com.lewandivka.core.registry.BlockSpec;
import com.lewandivka.core.registry.ModBlocks;

import java.util.Set;

/**
 * Coarse physical classification of block keys. Used by the structure validator to decide whether
 * a player could stand or walk somewhere. It is a conservative approximation of Minecraft's real
 * collision shapes; anything unknown counts as solid.
 */
public final class Materials {

    private Materials() {
    }

    public enum Kind {
        /** Nothing there: walk through. */
        PASS,
        /** Full collision (players cannot enter, but can stand on top). */
        SOLID,
        /** Water: can swim through, cannot be stood on. */
        LIQUID,
        /** Ladders/vines: walk-through cells that allow climbing. */
        CLIMB,
        /** Gate block that is closed until the encounter opens it. Passable when gates are open. */
        GATE,
        /** Deadly (lava, fire ...) - never a valid route. */
        HAZARD
    }

    /** Vanilla blocks that are used as quest gates in some structures. */
    private static final Set<String> VANILLA_GATES = Set.of("minecraft:iron_door", "minecraft:iron_trapdoor");

    private static final Set<String> PASS_EXACT = Set.of(
            "air", "cave_air", "void_air", "light", "structure_void", "barrier_none",
            "torch", "wall_torch", "soul_torch", "soul_wall_torch", "redstone_torch", "redstone_wall_torch",
            "lantern", "soul_lantern", "chain", "end_rod", "lever", "rail", "powered_rail", "detector_rail",
            "activator_rail", "redstone_wire", "tripwire", "tripwire_hook", "string", "cobweb", "snow",
            "moss_carpet", "glow_lichen", "sculk_vein", "spore_blossom", "hanging_roots", "flower_pot",
            "grass", "tall_grass", "fern", "large_fern", "dead_bush", "dandelion", "poppy",
            "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip", "pink_tulip",
            "oxeye_daisy", "cornflower", "lily_of_the_valley", "sunflower", "lilac", "rose_bush", "peony",
            "torchflower", "pitcher_plant", "pink_petals", "sweet_berry_bush", "brown_mushroom", "red_mushroom",
            "crimson_fungus", "warped_fungus", "crimson_roots", "warped_roots", "nether_sprouts", "kelp",
            "seagrass", "tall_seagrass", "amethyst_cluster", "small_amethyst_bud", "medium_amethyst_bud",
            "large_amethyst_bud", "wheat", "carrots", "potatoes", "beetroots", "sugar_cane", "item_frame",
            "glow_item_frame", "painting", "lily_pad", "frogspawn", "turtle_egg", "bamboo_sapling",
            "pointed_dripstone", "candle");

    private static final String[] PASS_SUFFIX = {
            "_sign", "_wall_sign", "_hanging_sign", "_wall_hanging_sign", "_button", "_pressure_plate",
            "_carpet", "_banner", "_wall_banner", "_head", "_wall_head", "_skull", "_wall_skull",
            "_sapling", "_candle", "_door", "_fence_gate", "_bud"
    };

    public static Kind classify(String key) {
        if (key == null) {
            return Kind.PASS;
        }
        String id = Keys.blockId(key);
        if (VANILLA_GATES.contains(id)) {
            return Kind.GATE;
        }
        if (id.startsWith("lewandivka:")) {
            BlockSpec spec = ModBlocks.forKey(id);
            if (spec == null) {
                return Kind.SOLID;
            }
            if (spec.behaviour == BlockSpec.Behaviour.GATE || spec.behaviour == BlockSpec.Behaviour.DASH_GATE) {
                return Kind.GATE;
            }
            return spec.passable ? Kind.PASS : Kind.SOLID;
        }
        String name = id.substring(id.indexOf(':') + 1);
        switch (name) {
            case "water", "bubble_column" -> {
                return Kind.LIQUID;
            }
            case "lava", "magma_block", "fire", "soul_fire", "cactus", "campfire", "soul_campfire",
                    "wither_rose", "sweet_berry_bush_hazard", "powder_snow" -> {
                return Kind.HAZARD;
            }
            case "ladder", "vine", "scaffolding", "twisting_vines", "weeping_vines", "cave_vines" -> {
                return Kind.CLIMB;
            }
            default -> {
                // fall through to generic rules
            }
        }
        if (PASS_EXACT.contains(name) || name.startsWith("potted_")) {
            return Kind.PASS;
        }
        for (String suffix : PASS_SUFFIX) {
            if (name.endsWith(suffix)) {
                return Kind.PASS;
            }
        }
        return Kind.SOLID;
    }

    public static boolean isGate(String key) {
        return classify(key) == Kind.GATE;
    }
}
