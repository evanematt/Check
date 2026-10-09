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
 * The utility buildings of the district that you can walk into: a row of garages (every one with its car, its workbench or
 * its junk), the boiler house with its tall chimney and the transformer station. Like all buildings they are drawn with the
 * front on the north side and turned when placed; the body starts {@link #FRONT} blocks behind the margin.
 */
public final class Industry {

    private Industry() {
    }

    private static final Map<String, Blueprint> CACHE = new ConcurrentHashMap<>();
    private static final int FRONT = 3;

    // ================================================================== garages

    public static final int GARAGE_WIDTH = 4;
    public static final int GARAGE_DEPTH = 8;

    /** The cell inside the gate of garage {@code i} (for the checks): its x in the building and the row behind the gate. */
    static int garageX(int i) {
        return GARAGE_WIDTH * i + 2;
    }

    /** What the gate of garage {@code i} of the row is like: 0 open, 1 raised halfway, 2 shut. */
    static int gate(long seed, int i) {
        long h = Noise.hash(seed, i, 71);
        int r = (int) Math.floorMod(h, 10L);
        return r < 6 ? 0 : r < 8 ? 1 : 2;
    }

    /** A row of {@code n} garages of brick with flat roofs, one wall between neighbours; the gates face north. */
    public static Blueprint garageRow(int n, int turns, long seed) {
        String key = "garages" + n + "_" + turns + "_" + seed;
        return CACHE.computeIfAbsent(key, k -> buildGarages(n, turns, seed));
    }

    private static Blueprint buildGarages(int n, int turns, long seed) {
        final int w = GARAGE_WIDTH * n + 1;
        final int d = GARAGE_DEPTH;
        final int sx = w;
        final int sz = d + FRONT + 1;
        boolean odd = (turns & 1) == 1;
        BlueprintBuilder b = new BlueprintBuilder("garage_row", odd ? sz : sx, 7, odd ? sx : sz);
        b.orient(turns, sx, sz);
        b.at(0, 0, FRONT);
        // the apron in front of the gates, the floor, the walls and the roof
        b.fill(0, 0, -FRONT, w - 1, 0, -1, Pal.CONCRETE_GREY);
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                b.set(x, 0, z, (Noise.hash(seed, x, z) & 7) == 0 ? Pal.CONCRETE_DARK : Pal.STONE);
            }
        }
        for (int y = 1; y <= 3; y++) {
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < d; z++) {
                    boolean outer = x == 0 || x == w - 1 || z == 0 || z == d - 1;
                    boolean party = x % GARAGE_WIDTH == 0;
                    if (outer || party) {
                        long h = Noise.hash(seed + 1, x, y * 31 + z);
                        b.set(x, y, z, (h & 7) == 0 ? Pal.BRICKS_CRACKED : (h & 7) == 1 ? Pal.BRICKS_MOSSY : (h & 15) == 2 ? Pal.CONCRETE_GREY : Pal.RED_BRICKS);
                    } else {
                        b.set(x, y, z, Keys.AIR);
                    }
                }
            }
        }
        b.fill(0, 4, 0, w - 1, 4, d - 1, Pal.CONCRETE_DARK);
        for (int x = 0; x < w; x++) {
            b.set(x, 5, 0, "minecraft:stone_brick_slab[type=bottom]");
            b.set(x, 5, d - 1, "minecraft:stone_brick_slab[type=bottom]");
        }
        for (int z = 1; z < d - 1; z++) {
            b.set(0, 5, z, "minecraft:stone_brick_slab[type=bottom]");
            b.set(w - 1, 5, z, "minecraft:stone_brick_slab[type=bottom]");
        }
        for (int i = 0; i < n; i++) {
            garage(b, i, seed);
        }
        b.marker("entrance", garageX(0), 1, -1);
        b.region("body", 0, 0, 0, w - 1, 6, d - 1);
        return b.build();
    }

    private static final String[] CARS = {Props.CAR_BLUE, Props.CAR_WHITE, Props.CAR_RUST, Props.CAR_GREEN, Props.CAR_YELLOW};

    private static void garage(BlueprintBuilder b, int i, long seed) {
        int x0 = GARAGE_WIDTH * i + 1;
        int gate = gate(seed, i);
        // the gate: open, raised so that you walk under it, or shut (rusty plates)
        b.fill(x0, 1, 0, x0 + 2, 3, 0, gate == 2 ? Pal.RUST : Keys.AIR);
        if (gate == 1) {
            b.fill(x0, 3, 0, x0 + 2, 3, 0, Pal.RUST);
        }
        long h = Noise.hash(seed + 2, i, 5);
        int kind = (int) Math.floorMod(h, 10L);
        if (kind < 5) {
            // a car, nose to the back wall
            b.stamp(Props.car(CARS[(int) Math.floorMod(h >>> 8, (long) CARS.length)], 1), x0, 1, 2);
        } else if (kind < 8) {
            // a workshop: bench, barrels, shelves
            b.set(x0, 1, 6, "minecraft:crafting_table");
            Loot.barrel(b, x0 + 1, 1, 6, Dir.NORTH, "garage");
            b.set(x0 + 2, 1, 6, "minecraft:bookshelf");
            b.set(x0 + 2, 2, 6, "minecraft:bookshelf");
            b.set(x0, 1, 5, "minecraft:anvil[facing=east]");
            b.set(x0 + 2, 1, 2, Keys.barrel(Dir.WEST));
        } else {
            // junk: pallets and barrels
            b.stamp(Props.pallets(), x0, 1, 4);
            Loot.barrel(b, x0, 1, 2, Dir.NORTH, "garage");
        }
        if ((h >>> 16) % 3 != 0) {
            b.set(x0 + 1, 3, 3, Keys.lantern(true));
        }
        if ((h >>> 20) % 4 == 0) {
            b.set(x0, 3, 1, "minecraft:cobweb");
        }
    }

    // ================================================================== boiler house

    /** The boiler house: a brick hall with two boilers, pipes under the roof and a control corner; the chimney stands beside it. */
    public static Blueprint boilerHouse(int turns, long seed) {
        String key = "boiler_" + turns + "_" + seed;
        return CACHE.computeIfAbsent(key, k -> buildBoiler(turns, seed));
    }

    static final int BOILER_W = 16;
    static final int BOILER_D = 11;
    static final int CHIMNEY_H = 34;

    private static Blueprint buildBoiler(int turns, long seed) {
        final int w = BOILER_W;
        final int d = BOILER_D;
        final int sx = w + 5;
        final int sz = d + FRONT + 1;
        boolean odd = (turns & 1) == 1;
        BlueprintBuilder b = new BlueprintBuilder("boiler_house", odd ? sz : sx, CHIMNEY_H + 3, odd ? sx : sz);
        b.orient(turns, sx, sz);
        b.at(0, 0, FRONT);
        b.fill(0, 0, -FRONT, w + 3, 0, -1, Pal.CONCRETE_GREY);
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                b.set(x, 0, z, (x + z) % 5 == 0 ? Pal.CONCRETE_DARK : Pal.METAL_PLATE);
            }
        }
        for (int y = 1; y <= 7; y++) {
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < d; z++) {
                    boolean outer = x == 0 || x == w - 1 || z == 0 || z == d - 1;
                    long h = Noise.hash(seed, x, y * 29 + z);
                    b.set(x, y, z, outer ? ((h & 7) == 0 ? Pal.BRICKS_CRACKED : (h & 7) == 1 ? Pal.RED_BRICKS : (h & 15) == 2 ? Pal.BRICKS_MOSSY : Pal.RED_BRICKS) : Keys.AIR);
                }
            }
        }
        b.fill(0, 8, 0, w - 1, 8, d - 1, Pal.CONCRETE_DARK);
        for (int x = 0; x < w; x++) {
            b.set(x, 9, 0, "minecraft:stone_brick_slab[type=bottom]");
            b.set(x, 9, d - 1, "minecraft:stone_brick_slab[type=bottom]");
        }
        for (int z = 1; z < d - 1; z++) {
            b.set(0, 9, z, "minecraft:stone_brick_slab[type=bottom]");
            b.set(w - 1, 9, z, "minecraft:stone_brick_slab[type=bottom]");
        }
        // the door of people, the gate of the trucks (shut) and the high windows with bars
        b.fill(2, 1, 0, 2, 2, 0, Keys.AIR);
        b.set(2, 1, 0, Keys.door("minecraft:dark_oak_door", Dir.NORTH, false, false, false));
        b.set(2, 2, 0, Keys.door("minecraft:dark_oak_door", Dir.NORTH, true, false, false));
        b.fill(8, 1, 0, 11, 4, 0, Pal.RUST);
        for (int x = 3; x < w - 1; x += 3) {
            if (x < 8 || x > 11) {
                b.fill(x, 5, 0, x + 1, 6, 0, Pal.BARS);
            }
            b.fill(x, 5, d - 1, x + 1, 6, d - 1, Pal.BARS);
        }
        // two boilers on the floor of the hall, with a firebox in front of each
        for (int boiler = 0; boiler < 2; boiler++) {
            int x0 = 3 + boiler * 6;
            b.fill(x0, 1, 4, x0 + 3, 3, 6, Pal.IRON);
            b.fill(x0 + 1, 4, 5, x0 + 2, 5, 5, Pal.IRON);
            b.set(x0 + 1, 1, 3, Keys.of("minecraft:blast_furnace", "facing", "north", "lit", "false"));
            b.set(x0 + 2, 1, 3, Keys.of("minecraft:blast_furnace", "facing", "north", "lit", "false"));
            b.set(x0 + 1, 2, 3, "minecraft:iron_trapdoor[facing=north,half=bottom,open=true]");
            b.set(x0 + 2, 2, 3, Keys.lever(Dir.NORTH));
            // the pipes from the boiler up to the ceiling and along it
            b.fill(x0 + 1, 6, 5, x0 + 1, 7, 5, Pal.RUST);
        }
        b.fill(4, 7, 5, 11, 7, 5, Pal.RUST);
        b.fill(11, 7, 5, 11, 7, 6, Pal.RUST);
        b.fill(w - 1, 5, 5, w - 1, 5, 5, Pal.RUST);
        // the control corner: a desk, a lectern, lamps
        b.set(13, 1, 1, "minecraft:lectern[facing=south]");
        b.set(14, 1, 1, "minecraft:barrel[facing=up]");
        b.set(14, 2, 1, "minecraft:redstone_lamp[lit=false]");
        b.set(13, 1, 8, "minecraft:crafting_table");
        b.set(14, 1, 8, Keys.barrel(Dir.WEST));
        for (int x = 3; x < w - 2; x += 4) {
            b.set(x, 7, 2, Keys.lantern(true));
            b.set(x, 7, 8, Keys.lantern(true));
        }
        // the chimney: a hollow tower of brick with white bands, joined to the hall by a pipe
        int cx = w + 1;
        for (int y = 0; y < CHIMNEY_H; y++) {
            boolean band = y >= 24 && y < 26 || y >= 29 && y < 31;
            b.fill(cx, y, 4, cx + 2, y, 6, band ? Pal.CONCRETE_WHITE : Pal.RED_BRICKS);
            b.set(cx + 1, y, 5, Keys.AIR);
        }
        for (int dx = 0; dx <= 2; dx++) {
            for (int dz = 4; dz <= 6; dz++) {
                b.set(cx + dx, CHIMNEY_H, dz, "minecraft:stone_brick_wall");
            }
        }
        b.set(cx + 1, CHIMNEY_H, 5, Keys.AIR);
        b.fill(w, 5, 5, cx, 5, 5, Pal.RUST);
        b.marker("entrance", 2, 1, -1);
        b.region("body", 0, 0, 0, w - 1, 9, d - 1);
        return b.build();
    }

    // ================================================================== transformer station

    public static Blueprint substation(int turns, long seed) {
        String key = "substation_" + turns + "_" + seed;
        return CACHE.computeIfAbsent(key, k -> buildSubstation(turns, seed));
    }

    private static Blueprint buildSubstation(int turns, long seed) {
        final int w = 9;
        final int d = 7;
        final int sx = w;
        final int sz = d + FRONT + 1;
        boolean odd = (turns & 1) == 1;
        BlueprintBuilder b = new BlueprintBuilder("substation", odd ? sz : sx, 8, odd ? sx : sz);
        b.orient(turns, sx, sz);
        b.at(0, 0, FRONT);
        b.fill(0, 0, -FRONT, w - 1, 0, -1, Pal.CONCRETE_GREY);
        b.fill(0, 0, 0, w - 1, 0, d - 1, Pal.METAL_PLATE);
        for (int y = 1; y <= 5; y++) {
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < d; z++) {
                    boolean outer = x == 0 || x == w - 1 || z == 0 || z == d - 1;
                    long h = Noise.hash(seed, x, y * 17 + z);
                    b.set(x, y, z, outer ? ((h & 7) == 0 ? Pal.CONCRETE_DARK : (h & 7) == 1 ? Pal.ANDESITE : Pal.CONCRETE_GREY) : Keys.AIR);
                }
            }
        }
        b.fill(0, 6, 0, w - 1, 6, d - 1, Pal.CONCRETE_DARK);
        // the door, with a yellow-and-black warning stripe above it
        b.fill(4, 1, 0, 4, 2, 0, Keys.AIR);
        b.set(4, 1, 0, Keys.door("minecraft:dark_oak_door", Dir.NORTH, false, false, false));
        b.set(4, 2, 0, Keys.door("minecraft:dark_oak_door", Dir.NORTH, true, false, false));
        for (int x = 1; x < w - 1; x++) {
            b.set(x, 4, 0, x % 2 == 0 ? "minecraft:yellow_concrete" : "minecraft:black_concrete");
        }
        b.fill(1, 3, d - 1, 2, 3, d - 1, Pal.BARS);
        // two transformers with insulators on the top, cables along the ceiling
        for (int x : new int[] {2, 6}) {
            b.fill(x, 1, 3, x + 1, 2, 4, Pal.IRON);
            b.set(x, 3, 3, "minecraft:lightning_rod[facing=up]");
            b.set(x + 1, 3, 4, "minecraft:lightning_rod[facing=up]");
            b.fill(x, 4, 3, x, 5, 3, Pal.CHAIN);
        }
        b.fill(1, 5, 5, w - 2, 5, 5, Pal.BARS);
        b.set(4, 5, 2, Keys.lantern(true));
        b.set(1, 1, 1, Keys.barrel(Dir.EAST));
        b.marker("entrance", 4, 1, -1);
        b.region("body", 0, 0, 0, w - 1, 6, d - 1);
        return b.build();
    }
}
