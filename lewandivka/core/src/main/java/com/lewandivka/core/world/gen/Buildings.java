package com.lewandivka.core.world.gen;

import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.structure.BlueprintBuilder;
import com.lewandivka.core.structure.Dir;
import com.lewandivka.core.structure.Keys;
import com.lewandivka.core.world.Noise;
import com.lewandivka.core.world.Pal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Residential and utility buildings of the district, in the spirit of the reference builds:
 * Soviet five-storey panel blocks, two-storey plaster houses with tile roofs and white window frames,
 * an old shop and a brick tram depot.
 *
 * <p>Every building is drawn once in a local frame where the front facade is on the north side
 * (z = 0) and rotated with {@code turns} when placed. Layer 0 is the foundation layer that replaces
 * the ground surface (use the surface height as the placement y).</p>
 */
public final class Buildings {

    private Buildings() {
    }

    private static final Map<String, Blueprint> CACHE = new ConcurrentHashMap<>();

    /** Margin in front of the facade for canopies, steps and window sills. */
    private static final int FRONT = 3;

    public enum Theme {
        /** Grey concrete panels with peeling white patches (the typical khrushchyovka). */
        PANEL_GREY(Pal.CONCRETE_GREY, Pal.CONCRETE_WHITE, Pal.CONCRETE_DARK, Pal.CONCRETE_WHITE),
        /** Pale yellow/beige panels. */
        PANEL_BEIGE(Pal.PLASTER_PEEL, Pal.PLASTER_WHITE, Pal.PLASTER_OCHRE, Pal.PLASTER_WHITE),
        /** Orange-brown plaster as in the second reference street. */
        PANEL_ORANGE(Pal.PLASTER_PEACH, Pal.PLASTER_WHITE, Pal.PLASTER_CLAY, Pal.CONCRETE_WHITE);

        final String wall;
        final String patch;
        final String dirty;
        final String frame;

        Theme(String wall, String patch, String dirty, String frame) {
            this.wall = wall;
            this.patch = patch;
            this.dirty = dirty;
            this.frame = frame;
        }
    }

    // ================================================================== panel block

    /**
     * A panel apartment block of {@code sections} sections (17 blocks each, with an entrance, a stairwell and four furnished
     * flats on every floor) and {@code floors} storeys. Footprint {@code (17 * sections + 1) x 13}, plus the margin in front.
     */
    public static Blueprint panelBlock(int sections, int floors, Theme theme, int turns, long seed) {
        String key = "panel" + sections + "_" + floors + "_" + theme + "_" + turns + "_" + seed;
        return CACHE.computeIfAbsent(key, k -> PanelBlock.build(sections, floors, theme, turns, seed));
    }

    static String panelWall(Theme t, long seed, int x, int y, int z) {
        long h = Noise.hash(seed, x / 4, y / 3, z / 6);
        double r = (h >>> 11) * (1.0 / (1L << 53));
        long hf = Noise.hash(seed + 1, x, y, z);
        if (y <= 2 && (hf & 7) == 0) {
            return Pal.BRICKS_MOSSY;
        }
        if (r < 0.14) {
            return t.patch;
        }
        if (r < 0.24) {
            return t.dirty;
        }
        if ((x % 4 == 0 || y % 3 == 1) && (hf & 3) == 0) {
            return Pal.ANDESITE;
        }
        return t.wall;
    }

    // ================================================================== plaster houses

    public enum HouseStyle {
        /** Ochre smooth-sandstone with brown tiles. */
        OCHRE(Pal.PLASTER_OCHRE, Pal.PLASTER_PEEL, Pal.RED_BRICKS, "minecraft:mud_brick_stairs", "minecraft:mud_brick_slab", "minecraft:mud_bricks"),
        /** Orange plaster with chipped white and dark tiles. */
        ORANGE(Pal.PLASTER_PEACH, Pal.PLASTER_WHITE, Pal.RED_BRICKS, "minecraft:deepslate_tile_stairs", "minecraft:deepslate_tile_slab", "minecraft:deepslate_tiles"),
        /** White upper floor with a lilac plinth, as in the third reference street. */
        LILAC(Pal.CONCRETE_WHITE, Pal.PLASTER_WHITE, "minecraft:purple_terracotta", "minecraft:stone_brick_stairs", "minecraft:stone_brick_slab", "minecraft:stone_bricks");

        final String wall;
        final String patch;
        final String base;
        final String roofStairs;
        final String roofSlab;
        final String roofBlock;

        HouseStyle(String wall, String patch, String base, String roofStairs, String roofSlab, String roofBlock) {
            this.wall = wall;
            this.patch = patch;
            this.base = base;
            this.roofStairs = roofStairs;
            this.roofSlab = roofSlab;
            this.roofBlock = roofBlock;
        }
    }

    /**
     * Two-storey house with a gable tile roof. {@code width} runs along the facade (x), depth along z.
     * Footprint is {@code width x depth} plus the front margin.
     */
    public static Blueprint plasterHouse(int width, int depth, HouseStyle style, int turns, long seed) {
        String key = "house" + width + "x" + depth + "_" + style + "_" + turns + "_" + seed;
        return CACHE.computeIfAbsent(key, k -> buildHouse(width, depth, style, turns, seed, false));
    }

    /** The kindergarten: a large two-storey house (24 x 10) with two group rooms and a hall on each floor. */
    public static Blueprint kindergarten(HouseStyle style, int turns, long seed) {
        String key = "kindergarten_" + style + "_" + turns + "_" + seed;
        return CACHE.computeIfAbsent(key, k -> buildHouse(24, 10, style, turns, seed, true));
    }

    private static Blueprint buildHouse(int w, int d, HouseStyle st, int turns, long seed, boolean kinder) {
        final int storeys = 2;
        final int storeyH = 4;
        final int wallTop = storeys * storeyH;       // 8
        final int ridgeLayers = d / 2 + 2;
        final int height = wallTop + ridgeLayers + 3;
        final int sx = w + 2;                          // roof overhang on both gable ends
        final int sz = d + FRONT + 1;
        boolean odd = (turns & 1) == 1;
        BlueprintBuilder b = new BlueprintBuilder("plaster_house", odd ? sz : sx, height, odd ? sx : sz);
        b.orient(turns, sx, sz);
        b.at(1, 0, FRONT);

        // foundation and body
        b.fill(0, 0, 0, w - 1, 0, d - 1, Pal.BRICKS);
        for (int y = 1; y <= wallTop; y++) {
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < d; z++) {
                    boolean outer = x == 0 || x == w - 1 || z == 0 || z == d - 1;
                    if (outer) {
                        b.set(x, y, z, houseWall(st, seed, x, y, z));
                    } else {
                        // the floors of the storeys are planks, the rooms between them air (furnished below)
                        b.set(x, y, z, y % storeyH == 0 ? Furnish.planks(Furnish.WOODS[(int) Math.floorMod(seed, (long) Furnish.WOODS.length)]) : Keys.AIR);
                    }
                }
            }
        }
        // gable roof running along x
        roof(b, w, d, wallTop, st);

        // windows on front and back
        for (int side = 0; side < 2; side++) {
            int zWall = side == 0 ? 0 : d - 1;
            int inward = side == 0 ? 1 : -1;
            int out = -inward;
            for (int storey = 0; storey < storeys; storey++) {
                int y0 = storeyH * storey + 2;
                for (int wx = 2; wx + 1 < w - 1; wx += 4) {
                    if (side == 0 && storey == 0 && Math.abs(wx - (w / 2 - 1)) <= 2) {
                        continue; // door bay
                    }
                    long h = Noise.hash(seed, wx, storey + 10 * side);
                    window(b, wx, y0, zWall, inward, out, (h & 3) != 0, (h >>> 3) % 5, side == 0);
                }
            }
        }
        // side windows (gable walls)
        for (int storey = 0; storey < storeys; storey++) {
            int y0 = storeyH * storey + 2;
            for (int z : new int[] {d / 2 - 1}) {
                b.fill(0, y0, z, 0, y0 + 1, z + 1, Pal.PANE);
                b.fill(w - 1, y0, z, w - 1, y0 + 1, z + 1, Pal.PANE);
            }
        }
        // front door, porch, steps, lamp (as in the reference photographs)
        int dx = w / 2 - 1;
        b.fill(dx - 1, 1, 0, dx + 2, 3, 0, Pal.CONCRETE_WHITE);
        b.fill(dx, 1, 0, dx + 1, 2, 0, Pal.AIR);
        b.set(dx, 1, 0, Keys.door("minecraft:dark_oak_door", Dir.NORTH, false, false, false));
        b.set(dx, 2, 0, Keys.door("minecraft:dark_oak_door", Dir.NORTH, true, false, false));
        b.set(dx + 1, 1, 0, Keys.door("minecraft:dark_oak_door", Dir.NORTH, false, false, true));
        b.set(dx + 1, 2, 0, Keys.door("minecraft:dark_oak_door", Dir.NORTH, true, false, true));
        b.fill(dx, 1, 1, dx + 1, 3, 2, Pal.AIR);
        // porch roof: a lean-to of dark wood with a lantern
        for (int x = dx - 2; x <= dx + 3; x++) {
            b.set(x, 4, -2, Keys.stairs("minecraft:dark_oak_stairs", Dir.SOUTH, false));
            b.set(x, 5, -1, Keys.stairs("minecraft:dark_oak_stairs", Dir.SOUTH, false));
            b.set(x, 5, 0, st.wall);
        }
        b.set(dx - 2, 1, -2, "minecraft:dark_oak_fence").set(dx - 2, 2, -2, "minecraft:dark_oak_fence").set(dx - 2, 3, -2, "minecraft:dark_oak_fence");
        b.set(dx + 3, 1, -2, "minecraft:dark_oak_fence").set(dx + 3, 2, -2, "minecraft:dark_oak_fence").set(dx + 3, 3, -2, "minecraft:dark_oak_fence");
        b.set(dx, 3, -1, Keys.lantern(true));
        b.fill(dx - 1, 0, -2, dx + 2, 0, -1, "minecraft:stone_brick_slab[type=bottom]");
        b.fill(dx, 0, -3, dx + 1, 0, -3, "minecraft:stone_brick_slab[type=bottom]");
        // house number plate (the reference shows a red year plaque)
        b.set(dx + 3, 6, 0, Pal.note(2, Dir.NORTH));
        // the rooms
        long h = Noise.hash(seed, w, d);
        String wood = Furnish.WOODS[(int) Math.floorMod(seed, (long) Furnish.WOODS.length)];
        Furnish.Style inside = new Furnish.Style(wood, Furnish.planks(wood), Furnish.WALLS[(int) Math.floorMod(h, (long) Furnish.WALLS.length)],
                Furnish.BEDS[(int) Math.floorMod(h >>> 8, (long) Furnish.BEDS.length)], Furnish.SOFAS[(int) Math.floorMod(h >>> 16, (long) Furnish.SOFAS.length)],
                Furnish.RUGS[(int) Math.floorMod(h >>> 24, (long) Furnish.RUGS.length)], !(!kinder && (h >>> 32) % 5 == 0), !kinder && (h >>> 40) % 6 == 0 ? 1 : 0);
        HouseKit.furnish(b, w, d, inside, seed, kinder);
        // chimney
        int cx = Math.min(w - 3, w / 2 + 3);
        b.fill(cx, wallTop, d / 2, cx + 1, wallTop + ridgeLayers + 1, d / 2, Pal.RED_BRICKS);
        b.set(cx, wallTop + ridgeLayers + 2, d / 2, "minecraft:stone_brick_wall");
        b.set(cx + 1, wallTop + ridgeLayers + 2, d / 2, "minecraft:stone_brick_wall");
        b.marker("entrance", dx, 1, 1);
        if (kinder) {
            b.marker("playroom", 6, 1, 4);
            b.marker("bedroom", 18, 1, 4);
            b.marker("upper", 6, 5, 4);
        } else {
            b.marker("living", 3, 1, 3);
            b.marker("bedroom", 4, 5, 3);
        }
        b.region("body", 0, 0, 0, w - 1, wallTop, d - 1);
        return b.build();
    }

    private static void window(BlueprintBuilder b, int wx, int y0, int zWall, int inward, int out, boolean lit, long deco, boolean front) {
        // white surround
        for (int x = wx - 1; x <= wx + 2; x++) {
            b.set(x, y0 - 1, zWall, "minecraft:smooth_quartz_slab[type=top]");
            b.set(x, y0 + 2, zWall, Pal.CONCRETE_WHITE);
        }
        for (int dy = 0; dy < 2; dy++) {
            b.set(wx - 1, y0 + dy, zWall, Pal.CONCRETE_WHITE);
            b.set(wx + 2, y0 + dy, zWall, Pal.CONCRETE_WHITE);
        }
        for (int dx = 0; dx < 2; dx++) {
            for (int dy = 0; dy < 2; dy++) {
                b.set(wx + dx, y0 + dy, zWall, Pal.PANE);
            }
        }
        if (front && deco <= 2) {
            b.set(wx + (int) (deco % 2), y0, zWall + out, deco == 1 ? "minecraft:potted_poppy" : "minecraft:potted_azure_bluet");
            b.set(wx + (int) (deco % 2), y0 - 1, zWall + out, "minecraft:stone_brick_slab[type=bottom]");
        }
    }

    private static void roof(BlueprintBuilder b, int w, int d, int wallTop, HouseStyle st) {
        // Layers from the eaves up to the ridge. Stairs face the direction of ascent: the north slope
        // rises towards the south, the south slope rises towards the north.
        int layer = 0;
        int zn = -1;
        int zs = d;
        while (zn < zs) {
            int y = wallTop + 1 + layer;
            if (zn + 1 >= zs - 1) {
                // ridge row (one or two cells wide)
                for (int z = zn; z <= zs; z++) {
                    for (int x = -1; x <= w; x++) {
                        b.set(x, y, z, z == zn || z == zs ? st.roofSlab + "[type=bottom]" : st.roofBlock);
                    }
                }
                break;
            }
            for (int x = -1; x <= w; x++) {
                b.set(x, y, zn, Keys.stairs(st.roofStairs, Dir.SOUTH, false));
                b.set(x, y, zs, Keys.stairs(st.roofStairs, Dir.NORTH, false));
            }
            // fill the wall-gable triangle and the roof body underneath
            for (int z = zn + 1; z < zs; z++) {
                for (int x = 0; x < w; x++) {
                    boolean gable = x == 0 || x == w - 1;
                    b.set(x, y, z, gable ? st.wall : Pal.PLASTER_CLAY);
                }
                b.set(-1, y, z, Pal.AIR);
                b.set(w, y, z, Pal.AIR);
            }
            zn++;
            zs--;
            layer++;
        }
    }

    private static String houseWall(HouseStyle st, long seed, int x, int y, int z) {
        long h = Noise.hash(seed, x, y, z);
        double r = (h >>> 11) * (1.0 / (1L << 53));
        long p = Noise.hash(seed + 5, x / 3, y / 2, z / 3);
        double pr = (p >>> 11) * (1.0 / (1L << 53));
        if (y <= 1) {
            return st.base;
        }
        if (y == 2 && r < 0.35) {
            return st.base;
        }
        if (pr < 0.16) {
            return Pal.RED_BRICKS; // plaster has fallen off, brick shows through
        }
        if (pr < 0.34) {
            return st.patch;
        }
        if (r < 0.04) {
            return Pal.PLASTER_CLAY;
        }
        return st.wall;
    }

    // ================================================================== shop

    /** Old corner shop with a striped awning, display window and an interior with counters. 16x7x11. */
    public static Blueprint oldShop(int turns) {
        return CACHE.computeIfAbsent("shop" + turns, k -> {
            final int w = 14;
            final int d = 9;
            final int sx = w + 2;
            final int sz = d + FRONT;
            boolean odd = (turns & 1) == 1;
            BlueprintBuilder b = new BlueprintBuilder("old_shop", odd ? sz : sx, 8, odd ? sx : sz);
            b.orient(turns, sx, sz);
            b.at(1, 0, FRONT);
            b.fill(0, 0, 0, w - 1, 0, d - 1, Pal.BRICKS);
            b.room(0, 0, 0, w - 1, 5, d - 1, Pal.PLASTER_OCHRE);
            b.fill(0, 0, 0, w - 1, 0, d - 1, Pal.POL_ANDESITE);
            b.fill(0, 1, 0, w - 1, 1, 0, Pal.BRICKS_MOSSY);
            // flat roof with a lip
            b.fill(0, 5, 0, w - 1, 5, d - 1, Pal.CONCRETE_DARK);
            for (int x = 0; x < w; x++) {
                b.set(x, 6, 0, "minecraft:stone_brick_slab[type=bottom]");
                b.set(x, 6, d - 1, "minecraft:stone_brick_slab[type=bottom]");
            }
            for (int z = 1; z < d - 1; z++) {
                b.set(0, 6, z, "minecraft:stone_brick_slab[type=bottom]");
                b.set(w - 1, 6, z, "minecraft:stone_brick_slab[type=bottom]");
            }
            // display windows and the door
            b.fill(2, 2, 0, 5, 3, 0, Pal.PANE);
            b.fill(8, 2, 0, 11, 3, 0, Pal.PANE);
            b.set(6, 1, 0, Keys.door("minecraft:dark_oak_door", Dir.NORTH, false, false, false));
            b.set(6, 2, 0, Keys.door("minecraft:dark_oak_door", Dir.NORTH, true, false, false));
            b.set(7, 1, 0, Keys.door("minecraft:dark_oak_door", Dir.NORTH, false, false, true));
            b.set(7, 2, 0, Keys.door("minecraft:dark_oak_door", Dir.NORTH, true, false, true));
            b.fill(6, 3, 0, 7, 3, 0, Pal.WOOD_DARK);
            // awning: red and white stripes sloping out over the pavement
            for (int x = 1; x < w - 1; x++) {
                boolean red = x % 2 == 0;
                b.set(x, 4, -1, Keys.stairs(red ? "minecraft:red_nether_brick_stairs" : "minecraft:quartz_stairs", Dir.SOUTH, false));
                b.set(x, 3, -2, red ? "minecraft:red_nether_brick_slab[type=top]" : "minecraft:quartz_slab[type=top]");
            }
            b.set(1, 1, -2, "minecraft:dark_oak_fence").set(1, 2, -2, "minecraft:dark_oak_fence");
            b.set(w - 2, 1, -2, "minecraft:dark_oak_fence").set(w - 2, 2, -2, "minecraft:dark_oak_fence");
            // sign "МАГАЗИН"
            b.set(6, 4, 0, Pal.note(3, Dir.NORTH)).set(7, 4, 0, Pal.note(4, Dir.NORTH));
            // interior: counter, shelves, stash, lights
            b.fill(2, 1, 3, 11, 1, 3, "minecraft:spruce_planks");
            b.fill(2, 2, 3, 11, 2, 3, "minecraft:spruce_slab[type=bottom]");
            for (int x = 2; x <= 11; x += 3) {
                b.set(x, 1, d - 2, Keys.barrel(Dir.NORTH));
                b.set(x, 2, d - 2, "minecraft:bookshelf");
            }
            b.set(w / 2, 4, d / 2, Pal.light(10));
            b.set(3, 1, d - 2, Pal.stash());
            b.marker("stash", 3, 1, d - 2, "loot=kiosk_sign");
            b.marker("door", 6, 1, 0);
            b.marker("forecourt", 6, 0, -3);
            b.region("body", 0, 0, 0, w - 1, 6, d - 1);
            return b.build();
        });
    }

    // ================================================================== tram depot

    /** Old brick tram depot at the terminus: a long hall with a barred arch. 18x9x22. */
    public static Blueprint tramDepot(int turns) {
        return CACHE.computeIfAbsent("depot" + turns, k -> {
            final int w = 18;
            final int d = 22;
            final int sx = w;
            final int sz = d + FRONT;
            boolean odd = (turns & 1) == 1;
            BlueprintBuilder b = new BlueprintBuilder("tram_depot", odd ? sz : sx, 12, odd ? sx : sz);
            b.orient(turns, sx, sz);
            b.at(0, 0, FRONT);
            b.fill(0, 0, 0, w - 1, 0, d - 1, Pal.BRICKS);
            b.room(0, 0, 0, w - 1, 7, d - 1, Pal.RED_BRICKS);
            b.fill(1, 0, 1, w - 2, 0, d - 2, Pal.COBBLE);
            // flat roof with a parapet and a raised glazed monitor over the middle of the hall
            b.fill(0, 8, 0, w - 1, 8, d - 1, Pal.CONCRETE_DARK);
            for (int x = 0; x < w; x++) {
                b.set(x, 9, 0, "minecraft:stone_brick_slab[type=bottom]");
                b.set(x, 9, d - 1, "minecraft:stone_brick_slab[type=bottom]");
            }
            for (int z = 1; z < d - 1; z++) {
                b.set(0, 9, z, "minecraft:stone_brick_slab[type=bottom]");
                b.set(w - 1, 9, z, "minecraft:stone_brick_slab[type=bottom]");
            }
            b.fill(5, 9, 3, 12, 10, d - 4, Pal.AIR);
            b.walls(5, 9, 3, 12, 9, d - 4, Pal.RED_BRICKS);
            b.walls(5, 10, 3, 12, 10, d - 4, Pal.PANE);
            b.fill(5, 11, 3, 12, 11, d - 4, Pal.CONCRETE_DARK);
            b.fill(6, 8, 4, 11, 8, d - 5, Pal.PANE);
            // big arch at the front, closed by rusty bars
            b.fill(4, 1, 0, 13, 5, 0, Pal.AIR);
            b.fill(5, 6, 0, 12, 6, 0, Pal.AIR);
            b.fill(4, 1, 0, 13, 5, 0, "minecraft:iron_bars");
            b.fill(5, 6, 0, 12, 6, 0, "minecraft:iron_bars");
            b.fill(3, 1, 0, 3, 6, 0, Pal.BRICKS).fill(14, 1, 0, 14, 6, 0, Pal.BRICKS);
            b.fill(4, 7, 0, 13, 7, 0, Pal.BRICKS);
            // skylights and lamps inside
            for (int z = 3; z < d - 2; z += 5) {
                b.set(w / 2, 7, z, Pal.light(8));
                b.fill(w / 2 - 1, 7, z, w / 2 + 1, 7, z, Pal.PANE);
            }
            // rails inside (rusty, abandoned)
            for (int z = 1; z < d - 1; z++) {
                b.set(w / 2 - 1, 1, z, "minecraft:rail[shape=north_south]");
                b.set(w / 2, 1, z, Pal.AIR);
            }
            // parked old tram body: just a sealed box so nobody expects an interior
            b.fill(w / 2 - 3, 1, 6, w / 2 + 2, 3, 15, "minecraft:red_terracotta");
            b.fill(w / 2 - 3, 2, 6, w / 2 + 2, 2, 15, Pal.PANE);
            b.fill(w / 2 - 3, 4, 6, w / 2 + 2, 4, 15, Pal.CONCRETE_DARK);
            b.region("body", 0, 0, 0, w - 1, 9, d - 1);
            return b.build();
        });
    }
}
