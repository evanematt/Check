package com.lewandivka.core.world.gen;

import com.lewandivka.core.structure.Dir;
import com.lewandivka.core.structure.Keys;

/**
 * The one place that decides which block of a furniture mod stands for a piece of furniture. The district is furnished with
 * the blocks of the ordinary game; where the mod <b>Handcrafted</b> is installed alongside, the same piece is drawn with its
 * model (a chair that looks like a chair, a sofa with arms, a bed with a headboard). Every key here is a
 * {@link Keys#either} of the block of the mod and the block of the game that stands in for it: the glue puts the block of the
 * mod where the game has it and the stand-in where it has not, so a world generated without the mod is complete too, and every
 * analysis of the core (walls, floors, ways) looks at the stand-in.
 *
 * <p>The blocks and properties are those of Handcrafted 3.0.6 for Minecraft 1.20.1 as the server pack run of the build listed
 * them ({@code /lewandivka dumpblocks handcrafted}; the list lies in the test resources, and a test checks every key made here
 * against it). Where a furniture block has a front, {@code facing} is the direction it looks to: the sitter of a chair, the
 * person at the cupboard.</p>
 */
final class Decor {

    private Decor() {
    }

    static final String MOD = "handcrafted:";

    private static String mod(String block, String... kv) {
        return Keys.of(MOD + block, kv);
    }

    // ------------------------------------------------------------------ sleeping and sitting

    /** One half of a bed; {@code vanillaBed} is the block of the game ({@code minecraft:red_bed}). */
    static String bed(String wood, String color, Dir toHead, boolean head, String vanillaBed) {
        return Keys.either(mod(wood + "_fancy_bed", "color", color, "facing", toHead.key(), "occupied", "false",
                "part", head ? "head" : "foot", "shape", "single"), Keys.bed(vanillaBed, toHead, head));
    }

    /** A chair with a cushion of the given colour ({@code none} for bare wood). */
    static String chair(String wood, String color, Dir front, String vanillaStairs) {
        return Keys.either(mod(wood + "_chair", "color", color, "facing", front.key()), Keys.stairs(vanillaStairs, front.opposite(), false));
    }

    /** One seat of a sofa; {@code shape} is {@link #couchShape}. */
    static String couch(String wood, String color, Dir front, String shape, String vanillaStairs) {
        return Keys.either(mod(wood + "_couch", "color", color, "facing", front.key(), "shape", shape), Keys.stairs(vanillaStairs, front.opposite(), false));
    }

    /**
     * How a seat joins its neighbours: {@code left} when another seat of the sofa is on the left hand of the sitter and none on
     * the right, and so on. (The names are those of the mod, which draws the arm on the other side from the neighbour.)
     */
    static String couchShape(boolean neighbourLeft, boolean neighbourRight) {
        return neighbourLeft ? (neighbourRight ? "middle" : "left") : (neighbourRight ? "right" : "single");
    }

    /** The colour of the sofa made of the given stairs of the game. */
    static String sofaColor(String stairs) {
        String name = stairs.substring(stairs.indexOf(':') + 1);
        if (name.startsWith("red_nether_brick")) {
            return "red";
        }
        if (name.startsWith("crimson")) {
            return "magenta";
        }
        if (name.startsWith("warped")) {
            return "cyan";
        }
        if (name.startsWith("deepslate")) {
            return "gray";
        }
        if (name.startsWith("purpur")) {
            return "purple";
        }
        return "brown";
    }

    /**
     * One seat of a park bench (a seat with a back that joins the seats next to it like a sofa, {@code shape} is {@link #couchShape});
     * the stand-in is a stair with its back to the back of the bench.
     */
    static String bench(String wood, Dir front, String shape, String vanillaStairs) {
        return Keys.either(mod(wood + "_bench", "color", "none", "facing", front.key(), "shape", shape), Keys.stairs(vanillaStairs, front.opposite(), false));
    }

    /** A table; {@code shape} is {@link #tableShape}; the stand-in is a slab in the upper half of the cell. */
    static String table(String wood, String shape, String vanillaSlab) {
        return Keys.either(mod(wood + "_table", "color", "none", "shape", shape), Keys.slab(vanillaSlab, true));
    }

    /**
     * How a table joins the tables around it, from which sides there is another table: none is a table with four legs, one
     * neighbour is a {@code *_side}, two on opposite sides a {@code *_center} named after the two free sides, two on adjacent
     * sides a {@code *_corner} named after the free corner, three a {@code *_center} named after the free side, four the
     * {@code center}. (Read off the models of the mod: the legs and the aprons stand where the table is free.)
     */
    static String tableShape(boolean north, boolean east, boolean south, boolean west) {
        int n = (north ? 1 : 0) + (east ? 1 : 0) + (south ? 1 : 0) + (west ? 1 : 0);
        switch (n) {
            case 0:
                return "single";
            case 1:
                return (north ? "north" : east ? "east" : south ? "south" : "west") + "_side";
            case 2:
                if (north && south) {
                    return "east_west_center";
                }
                if (east && west) {
                    return "north_south_center";
                }
                return (north ? "south" : "north") + "_" + (east ? "west" : "east") + "_corner";
            case 3:
                return (!north ? "north" : !east ? "east" : !south ? "south" : "west") + "_center";
            default:
                return "center";
        }
    }

    // ------------------------------------------------------------------ storage

    /** One cell of a bookcase: the shelves with books ({@code type} 2), potions (4), pots (5), cobwebs (3) or nothing (1). */
    static String shelf(String wood, Dir front, int type) {
        return Keys.either(mod(wood + "_shelf", "facing", front.key(), "shape", "single", "type", String.valueOf(type)), "minecraft:bookshelf");
    }

    /** One cell of a tall cupboard; the stand-in is a barrel. */
    static String cupboard(String wood, Dir front, int type) {
        return Keys.either(mod(wood + "_cupboard", "facing", front.key(), "type", String.valueOf(type)), Keys.barrel(front));
    }

    /** A kitchen counter with a top of the given material (a block of the game: {@code smooth_stone}, {@code quartz_block} ...). */
    static String counter(String wood, String top, Dir front, int type) {
        return Keys.either(mod(wood + "_counter", "counter", top, "facing", front.key(), "type", String.valueOf(type)), Keys.barrel(front));
    }

    /** An oven; the stand-in is a smoker. */
    static String oven(Dir front) {
        return Keys.either(mod("oven", "facing", front.key(), "lit", "false"), Keys.of("minecraft:smoker", "facing", front.key(), "lit", "false"));
    }

    // ------------------------------------------------------------------ small things

    /** A cushion on the floor for the block of wool of the game of the same colour. */
    static String cushion(String woolBlock) {
        String name = woolBlock.substring(woolBlock.indexOf(':') + 1);
        String color = name.substring(0, name.length() - "_wool".length());
        return Keys.either(mod(color + "_cushion"), woolBlock);
    }

    /** The kinds of crockery that stand on a table. */
    static final String[] CROCKERY = {"white", "blue", "yellow", "terracotta", "wood"};

    /** A set of plates and cups on a table; the stand-in is a candle. */
    static String crockery(int kind, Dir facing, String vanillaCandle) {
        return Keys.either(mod(CROCKERY[Math.floorMod(kind, CROCKERY.length)] + "_crockery_combo", "facing", facing.key()), vanillaCandle);
    }
}
