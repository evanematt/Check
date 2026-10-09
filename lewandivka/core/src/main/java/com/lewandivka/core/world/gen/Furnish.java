package com.lewandivka.core.world.gen;

import com.lewandivka.core.structure.BlueprintBuilder;
import com.lewandivka.core.structure.Dir;
import com.lewandivka.core.structure.Keys;

/**
 * The furniture of the district's rooms, spoken in terms of what it is (a bed, a table, a sofa, a stove) and not of the blocks it
 * is made of. Every piece is built of ordinary blocks of the game here; {@link Decor} is the one place that may swap a piece for
 * the model of a furniture mod when such a mod is installed, with the blocks below as the fallback.
 *
 * <p>All coordinates are in the frame of the builder; {@code y} is the first layer above the floor.</p>
 */
final class Furnish {

    private Furnish() {
    }

    /** What a flat or a room is made of and how it is lived in. */
    record Style(String wood, String floor, String wall, String bed, String sofa, String rug, boolean lit, int mood) {
        String planks() {
            return "minecraft:" + wood + "_planks";
        }

        String slab() {
            return "minecraft:" + wood + "_slab";
        }

        String stairs() {
            return "minecraft:" + wood + "_stairs";
        }

        String fence() {
            return "minecraft:" + wood + "_fence";
        }

        String door() {
            return "minecraft:" + wood + "_door";
        }

        Style withMood(int newMood) {
            return new Style(wood, floor, wall, bed, sofa, rug, lit, newMood);
        }
    }

    /** The kinds of wood the furniture and the floors are made of (the names of the game without the namespace). */
    static final String[] WOODS = {"spruce", "oak", "birch", "dark_oak", "acacia"};

    static String planks(String wood) {
        return "minecraft:" + wood + "_planks";
    }

    static String door(String wood) {
        return "minecraft:" + wood + "_door";
    }
    static final String[] BEDS = {"red", "blue", "green", "yellow", "light_gray", "brown", "cyan", "pink", "orange", "purple"};
    static final String[] SOFAS = {"minecraft:red_nether_brick_stairs", "minecraft:mud_brick_stairs", "minecraft:crimson_stairs",
            "minecraft:warped_stairs", "minecraft:deepslate_tile_stairs", "minecraft:purpur_stairs", "minecraft:spruce_stairs"};
    static final String[] RUGS = {"red", "green", "blue", "brown", "gray", "orange", "purple", "cyan"};
    /** The wall colours of the flats: a muted wallpaper palette. */
    static final String[] WALLS = {"minecraft:white_terracotta", "minecraft:yellow_terracotta", "minecraft:orange_terracotta",
            "minecraft:light_blue_terracotta", "minecraft:light_gray_terracotta", "minecraft:pink_terracotta"};

    // ------------------------------------------------------------------ sleeping and sitting

    /** A bed: {@code toHead} is the direction from the foot to the head. */
    static void bed(BlueprintBuilder b, int x, int y, int z, Dir toHead, Style s) {
        String block = Decor.pick("bed", "minecraft:" + s.bed() + "_bed");
        b.set(x, y, z, Keys.bed(block, toHead, false));
        b.set(x + toHead.dx, y, z + toHead.dz, Keys.bed(block, toHead, true));
    }

    /** One cell of a table: a slab in the upper half of the cell. */
    static void table(BlueprintBuilder b, int x, int y, int z, Style s) {
        b.set(x, y, z, Keys.slab(Decor.pick("table", s.slab()), true));
    }

    /** A chair; {@code front} is the direction the sitter looks in. */
    static void chair(BlueprintBuilder b, int x, int y, int z, Dir front, Style s) {
        b.set(x, y, z, Keys.stairs(Decor.pick("chair", s.stairs()), front.opposite(), false));
    }

    /** One cell of a sofa; the back is behind the sitter. */
    static void sofa(BlueprintBuilder b, int x, int y, int z, Dir front, Style s) {
        b.set(x, y, z, Keys.stairs(Decor.pick("sofa", s.sofa()), front.opposite(), false));
    }

    // ------------------------------------------------------------------ storage

    /** A tall cupboard of two cells. */
    static void wardrobe(BlueprintBuilder b, int x, int y, int z, Dir front) {
        b.set(x, y, z, Keys.barrel(front));
        b.set(x, y + 1, z, Keys.barrel(front));
    }

    /** Shelves with books, two cells high. */
    static void shelf(BlueprintBuilder b, int x, int y, int z) {
        b.set(x, y, z, Decor.pick("shelf", "minecraft:bookshelf"));
        b.set(x, y + 1, z, Decor.pick("shelf", "minecraft:bookshelf"));
    }

    /** A television on a stand. */
    static void tv(BlueprintBuilder b, int x, int y, int z, Dir front) {
        b.set(x, y, z, Keys.barrel(front));
        b.set(x, y + 1, z, Decor.pick("tv", "minecraft:black_concrete"));
    }

    // ------------------------------------------------------------------ kitchen and bath

    static void counter(BlueprintBuilder b, int x, int y, int z, Dir front) {
        b.set(x, y, z, Keys.barrel(front));
    }

    static void stove(BlueprintBuilder b, int x, int y, int z, Dir front) {
        b.set(x, y, z, Keys.of(Decor.pick("stove", "minecraft:smoker"), "facing", front.key(), "lit", "false"));
    }

    static void sink(BlueprintBuilder b, int x, int y, int z) {
        b.set(x, y, z, Keys.of(Decor.pick("sink", "minecraft:water_cauldron"), "level", "3"));
    }

    static void fridge(BlueprintBuilder b, int x, int y, int z) {
        b.set(x, y, z, Decor.pick("fridge", "minecraft:white_concrete"));
        b.set(x, y + 1, z, Decor.pick("fridge", "minecraft:white_concrete"));
    }

    static void toilet(BlueprintBuilder b, int x, int y, int z, Dir front) {
        b.set(x, y, z, Keys.stairs(Decor.pick("toilet", "minecraft:quartz_stairs"), front.opposite(), false));
    }

    /** One cell of a bathtub (a basin of water). */
    static void tub(BlueprintBuilder b, int x, int y, int z) {
        b.set(x, y, z, Keys.of(Decor.pick("tub", "minecraft:water_cauldron"), "level", "3"));
    }

    // ------------------------------------------------------------------ decoration

    static final String[] POTS = {"minecraft:potted_fern", "minecraft:potted_azalea_bush", "minecraft:potted_red_tulip", "minecraft:potted_dandelion",
            "minecraft:potted_bamboo", "minecraft:potted_cactus", "minecraft:potted_blue_orchid", "minecraft:potted_oxeye_daisy"};

    static void plant(BlueprintBuilder b, int x, int y, int z, long hash) {
        b.set(x, y, z, POTS[(int) Math.floorMod(hash, (long) POTS.length)]);
    }

    static void rug(BlueprintBuilder b, int x, int y, int z, Style s) {
        b.set(x, y, z, "minecraft:" + s.rug() + "_carpet");
    }

    /** A lamp on a stand. */
    static void floorLamp(BlueprintBuilder b, int x, int y, int z, Style s) {
        b.set(x, y, z, s.fence());
        b.set(x, y + 1, z, Keys.lantern(false));
    }

    /** A lamp under the ceiling (the ceiling is at {@code y + 1}). */
    static void ceilingLamp(BlueprintBuilder b, int x, int y, int z) {
        b.set(x, y, z, Decor.pick("lamp", Keys.lantern(true)));
    }

    static void candle(BlueprintBuilder b, int x, int y, int z, int count) {
        b.set(x, y, z, Keys.of("minecraft:candle", "candles", String.valueOf(count), "lit", "true"));
    }

    static void cobweb(BlueprintBuilder b, int x, int y, int z) {
        b.set(x, y, z, "minecraft:cobweb");
    }
}
