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
 * Small scenery pieces: trees, utility poles, benches, trash containers, cars, playground,
 * fences. Convention: block layer 0 of a prop is the first free layer above the ground
 * (place props with their origin one block above the surface).
 */
public final class Props {

    private Props() {
    }

    private static final Map<String, Blueprint> CACHE = new ConcurrentHashMap<>();

    private static Blueprint cached(String key, java.util.function.Supplier<Blueprint> s) {
        return CACHE.computeIfAbsent(key, k -> s.get());
    }

    // ------------------------------------------------------------------ trees

    public static final int TREE_VARIANTS = 6;

    /** Broadleaf trees as in the references: thick trunk, rounded dark-green crown. */
    public static Blueprint tree(int variant) {
        int v = Math.floorMod(variant, TREE_VARIANTS);
        return cached("tree" + v, () -> buildTree(v));
    }

    private static Blueprint buildTree(int v) {
        int r;
        int trunk;
        double ry;
        String log;
        String leaves;
        switch (v) {
            case 0 -> { r = 4; trunk = 6; ry = 3.4; log = Pal.LOG_OAK; leaves = Pal.LEAVES_OAK; }
            case 1 -> { r = 4; trunk = 8; ry = 5.0; log = Pal.LOG_OAK; leaves = Pal.LEAVES_OAK; }
            case 2 -> { r = 3; trunk = 7; ry = 3.6; log = "minecraft:birch_log"; leaves = Pal.LEAVES_BIRCH; }
            case 3 -> { r = 4; trunk = 5; ry = 3.2; log = Pal.LOG_OAK; leaves = Pal.LEAVES_FLOWER; }
            case 4 -> { r = 3; trunk = 10; ry = 5.5; log = "minecraft:birch_log"; leaves = Pal.LEAVES_BIRCH; }
            default -> { r = 4; trunk = 6; ry = 3.6; log = Pal.LOG_DARK; leaves = Pal.LEAVES_DARK; }
        }
        double rx = v == 4 ? 2.4 : r - 0.4;
        int height = trunk + (int) Math.ceil(ry) + 2;
        int size = 2 * r + 1;
        BlueprintBuilder b = new BlueprintBuilder("tree" + v, size, height, size);
        b.clip(true);
        for (int y = 0; y < trunk; y++) {
            b.set(r, y, r, log);
        }
        // a little root flare
        if (v != 4) {
            b.set(r + 1, 0, r, log);
            b.set(r - 1, 0, r, log);
        }
        double cy = trunk + ry * 0.45;
        for (int y = -(int) Math.ceil(ry); y <= (int) Math.ceil(ry); y++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    double d = (x * x + z * z) / (rx * rx) + (y * y) / (ry * ry);
                    if (d > 1.0) {
                        continue;
                    }
                    long h = Noise.hash(77L + v, x, y + 40, z);
                    if (d > 0.62 && (h & 7) == 0) {
                        continue; // ragged edge
                    }
                    int wy = (int) Math.round(cy) + y;
                    if (wy < 0 || wy >= height) {
                        continue;
                    }
                    if (x == 0 && z == 0 && wy < trunk) {
                        continue;
                    }
                    b.set(r + x, wy, r + z, leaves);
                }
            }
        }
        // crown trunk continues into the leaves
        b.set(r, trunk, r, log);
        if (v == 3) {
            for (int i = 0; i < 6; i++) {
                int x = Noise.range(5, i, v, size - 2) + 1;
                int z = Noise.range(6, i, v, size - 2) + 1;
                // fallen petals on the ground below the crown
                if (b.get(x, 0, z) == null) {
                    b.set(x, 0, z, "minecraft:pink_petals");
                }
            }
        }
        return b.build();
    }

    /** Bush / hedge block cluster, 3x2x3. */
    public static Blueprint bush(int variant) {
        int v = Math.floorMod(variant, 3);
        return cached("bush" + v, () -> {
            BlueprintBuilder b = new BlueprintBuilder("bush" + v, 3, 2, 3);
            String leaf = v == 0 ? Pal.LEAVES_OAK : v == 1 ? Pal.LEAVES_AZALEA : Pal.LEAVES_FLOWER;
            b.fill(0, 0, 0, 2, 0, 2, leaf);
            b.fill(1, 1, 1, 1, 1, 1, leaf);
            b.air(0, 0, 0).air(2, 0, 2);
            return b.build();
        });
    }

    // ------------------------------------------------------------------ poles & lamps

    /**
     * Wooden utility pole with a cross arm; {@code lamp} hangs a warm lantern from it
     * (the reference streets are lit by lamps on such poles). 5x10x5, pole in the middle.
     */
    public static Blueprint utilityPole(boolean lamp) {
        return cached("pole" + lamp, () -> {
            BlueprintBuilder b = new BlueprintBuilder("pole", 5, 10, 5);
            for (int y = 0; y <= 8; y++) {
                b.set(2, y, 2, Pal.LOG_DARK);
            }
            b.set(2, 9, 2, "minecraft:dark_oak_fence");
            for (int x = 0; x < 5; x++) {
                b.set(x, 8, 2, x == 2 ? Pal.LOG_DARK : "minecraft:dark_oak_slab[type=top]");
            }
            // insulators
            b.set(0, 9, 2, "minecraft:lightning_rod[facing=up]");
            b.set(4, 9, 2, "minecraft:lightning_rod[facing=up]");
            if (lamp) {
                b.set(4, 7, 2, Keys.lantern(true));
            }
            return b.build();
        });
    }

    /** Street lamp: slim black pole with a bracket and a lantern. 3x7x3, pole at (1,*,1). */
    public static Blueprint streetLamp() {
        return cached("lamp", () -> {
            BlueprintBuilder b = new BlueprintBuilder("lamp", 3, 7, 3);
            b.set(1, 0, 1, "minecraft:polished_blackstone_wall");
            for (int y = 1; y <= 5; y++) {
                b.set(1, y, 1, "minecraft:polished_blackstone_wall");
            }
            b.set(2, 5, 1, "minecraft:polished_blackstone_slab[type=top]");
            b.set(2, 4, 1, Keys.lantern(true));
            b.set(1, 6, 1, "minecraft:polished_blackstone_slab[type=bottom]");
            return b.build();
        });
    }

    // ------------------------------------------------------------------ street furniture

    /** Park bench for three, facing north (+z side is the back). 3x2x2. */
    public static Blueprint bench() {
        return cached("bench", () -> {
            BlueprintBuilder b = new BlueprintBuilder("bench", 3, 2, 2);
            for (int x = 0; x < 3; x++) {
                b.set(x, 0, 0, "minecraft:spruce_slab[type=bottom]");
                b.set(x, 1, 1, "minecraft:spruce_slab[type=top]");
                b.set(x, 0, 1, "minecraft:spruce_slab[type=top]");
            }
            b.set(0, 0, 0, "minecraft:cobblestone_wall");
            b.set(2, 0, 0, "minecraft:cobblestone_wall");
            return b.build();
        });
    }

    /**
     * Green trash container with a heap of rubbish on top. When {@code loot} is non-null the heap is a
     * lootable {@code supply_stash} block with a marker named {@code stash} carrying {@code loot=<id>}.
     */
    public static Blueprint dumpster(String loot) {
        return cached("dumpster" + loot, () -> {
            BlueprintBuilder b = new BlueprintBuilder("dumpster", 3, 3, 2);
            b.fill(0, 0, 0, 2, 1, 1, "minecraft:green_concrete");
            b.set(0, 0, 0, "minecraft:black_concrete");
            b.set(2, 0, 0, "minecraft:black_concrete");
            b.fill(0, 2, 0, 2, 2, 1, "minecraft:smooth_stone_slab[type=bottom]");
            if (loot != null) {
                b.set(1, 2, 1, Pal.stash());
                b.marker("stash", 1, 2, 1, "loot=" + loot);
            }
            return b.build();
        });
    }

    /** Boxy Soviet-era car, length along x (5), width 3, height 3. Use {@code turns} to align. */
    public static Blueprint car(String body, int turns) {
        return cached("car" + body + turns, () -> {
            boolean odd = (turns & 1) == 1;
            BlueprintBuilder b = new BlueprintBuilder("car", odd ? 3 : 5, 3, odd ? 5 : 3);
            b.orient(turns, 5, 3);
            b.fill(0, 0, 0, 4, 1, 2, body);
            for (int x : new int[] {0, 4}) {
                for (int z : new int[] {0, 2}) {
                    b.set(x, 0, z, "minecraft:black_concrete");
                }
            }
            b.fill(1, 2, 0, 3, 2, 2, "minecraft:light_blue_stained_glass");
            b.fill(1, 2, 1, 3, 2, 1, body);
            b.set(0, 1, 0, "minecraft:red_concrete").set(0, 1, 2, "minecraft:red_concrete");
            b.set(4, 1, 0, "minecraft:white_concrete").set(4, 1, 2, "minecraft:white_concrete");
            b.set(4, 0, 1, "minecraft:gray_concrete").set(0, 0, 1, "minecraft:gray_concrete");
            return b.build();
        });
    }

    public static final String CAR_BLUE = "minecraft:blue_concrete";
    public static final String CAR_WHITE = "minecraft:white_concrete";
    public static final String CAR_RUST = "minecraft:red_terracotta";
    public static final String CAR_GREEN = "minecraft:green_terracotta";
    public static final String CAR_YELLOW = "minecraft:yellow_concrete";

    /** Chain-link fence along x (length cells) with brick posts every 4 blocks. Height 3. */
    public static Blueprint chainFence(int length, boolean withGate) {
        return cached("chain" + length + withGate, () -> {
            BlueprintBuilder b = new BlueprintBuilder("chain", length, 3, 1);
            for (int x = 0; x < length; x++) {
                boolean post = x % 4 == 0 || x == length - 1;
                if (post) {
                    b.fill(x, 0, 0, x, 2, 0, "minecraft:bricks");
                } else {
                    b.fill(x, 0, 0, x, 1, 0, Pal.BARS);
                    b.set(x, 2, 0, Pal.BARS);
                }
            }
            if (withGate && length > 8) {
                int g = length / 2;
                b.fill(g - 1, 0, 0, g + 1, 2, 0, Pal.AIR);
                b.fill(g - 2, 0, 0, g - 2, 2, 0, "minecraft:bricks");
                b.fill(g + 2, 0, 0, g + 2, 2, 0, "minecraft:bricks");
                b.fill(g - 1, 0, 0, g + 1, 0, 0, Pal.AIR);
            }
            return b.build();
        });
    }

    /** Wooden plank fence along x. Height 2 (plus gate optional). */
    public static Blueprint woodFence(int length) {
        return cached("wood" + length, () -> {
            BlueprintBuilder b = new BlueprintBuilder("woodfence", length, 2, 1);
            for (int x = 0; x < length; x++) {
                b.set(x, 0, 0, x % 3 == 0 ? Pal.LOG_DARK : "minecraft:dark_oak_fence[east=true,west=true]");
                b.set(x, 1, 0, x % 3 == 0 ? "minecraft:dark_oak_fence" : "minecraft:dark_oak_fence[east=true,west=true]");
            }
            return b.build();
        });
    }

    /** Flower bed row along x. */
    public static Blueprint flowers(int length) {
        return cached("flowers" + length, () -> {
            BlueprintBuilder b = new BlueprintBuilder("flowers", length, 1, 2);
            String[] kinds = {"minecraft:poppy", "minecraft:dandelion", "minecraft:cornflower", "minecraft:allium",
                    "minecraft:azure_bluet", "minecraft:red_tulip", "minecraft:oxeye_daisy", "minecraft:grass"};
            for (int x = 0; x < length; x++) {
                for (int z = 0; z < 2; z++) {
                    long h = Noise.hash(3, x, z);
                    if ((h & 3) != 0) {
                        b.set(x, 0, z, kinds[(int) ((h >>> 8) % kinds.length)]);
                    }
                }
            }
            return b.build();
        });
    }

    /**
     * Playground: sandbox, swing set, two spring riders and a slide tower. 19x6x9.
     * Ladders face north (the wall they hang on is to the south).
     */
    public static Blueprint playground() {
        return cached("playground", () -> {
            BlueprintBuilder b = new BlueprintBuilder("playground", 19, 6, 9);
            // sandbox 5x5 with a plank frame (sand is flush with the frame)
            b.fill(0, 0, 0, 4, 0, 4, Pal.WOOD_SPRUCE);
            b.fill(1, 0, 1, 3, 0, 3, "minecraft:sand");
            // swing set: two frames, crossbar, two seats on chains
            for (int z : new int[] {1, 5}) {
                for (int x : new int[] {7, 11}) {
                    for (int y = 0; y <= 3; y++) {
                        b.set(x, y, z, Pal.LOG_SPRUCE);
                    }
                }
            }
            b.fill(7, 4, 1, 7, 4, 5, "minecraft:spruce_log[axis=z]");
            b.fill(11, 4, 1, 11, 4, 5, "minecraft:spruce_log[axis=z]");
            b.fill(8, 4, 3, 10, 4, 3, "minecraft:spruce_log[axis=x]");
            for (int z : new int[] {2, 4}) {
                b.set(9, 3, z, "minecraft:chain[axis=y]");
                b.set(9, 2, z, "minecraft:chain[axis=y]");
                b.set(9, 1, z, "minecraft:spruce_slab[type=top]");
                b.set(9, 4, z, "minecraft:spruce_log[axis=x]");
            }
            // spring riders
            b.set(2, 0, 7, "minecraft:spruce_fence").set(2, 1, 7, "minecraft:orange_concrete");
            b.set(5, 0, 7, "minecraft:spruce_fence").set(5, 1, 7, "minecraft:lime_concrete");
            // slide tower: platform at height 3, ladder on the north side, slide to the east
            for (int x : new int[] {13, 15}) {
                for (int z : new int[] {5, 7}) {
                    for (int y = 0; y <= 2; y++) {
                        b.set(x, y, z, Pal.LOG_SPRUCE);
                    }
                    b.set(x, 4, z, "minecraft:spruce_fence");
                }
            }
            b.fill(13, 3, 5, 15, 3, 7, "minecraft:spruce_slab[type=bottom]");
            b.fill(14, 0, 5, 14, 2, 5, Pal.WOOD_SPRUCE);
            for (int y = 0; y <= 3; y++) {
                b.set(14, y, 4, Keys.ladder(Dir.NORTH));
            }
            b.set(16, 2, 6, Keys.stairs("minecraft:stone_stairs", Dir.WEST, false));
            b.set(17, 1, 6, Keys.stairs("minecraft:stone_stairs", Dir.WEST, false));
            b.set(18, 0, 6, Keys.stairs("minecraft:stone_stairs", Dir.WEST, false));
            b.set(16, 3, 6, "minecraft:spruce_fence").set(16, 3, 5, "minecraft:spruce_fence").set(16, 3, 7, "minecraft:spruce_fence");
            return b.build();
        });
    }

    /** Single lootable stash block with its marker. */
    public static Blueprint stash(String loot) {
        return cached("stash" + loot, () -> {
            BlueprintBuilder b = new BlueprintBuilder("stash", 1, 1, 1);
            b.set(0, 0, 0, Pal.stash());
            b.marker("stash", 0, 0, 0, "loot=" + loot);
            return b.build();
        });
    }

    /** Clue prop with marker. */
    public static Blueprint clue(int kind) {
        return cached("clue" + kind, () -> {
            BlueprintBuilder b = new BlueprintBuilder("clue", 1, 1, 1);
            b.set(0, 0, 0, Pal.clue(kind));
            b.marker("clue", 0, 0, 0, "kind=" + kind);
            return b.build();
        });
    }

    /** 3x1x3 painted foundation for the abandoned kiosk. Place with origin at ground level (replaces the surface). */
    public static Blueprint kioskFoundation() {
        return cached("kioskfoundation", () -> {
            BlueprintBuilder b = new BlueprintBuilder("kiosk_foundation", 3, 1, 3);
            b.fill(0, 0, 0, 2, 0, 2, Pal.KIOSK_FOUNDATION);
            b.marker("spot", 1, 0, 1);
            return b.build();
        });
    }

    /** A garden plot with a hedge frame for the private houses. */
    public static Blueprint gardenPlot(int w, int d) {
        return cached("garden" + w + "x" + d, () -> {
            BlueprintBuilder b = new BlueprintBuilder("garden", w, 2, d);
            b.clip(true);
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < d; z++) {
                    boolean edge = x == 0 || z == 0 || x == w - 1 || z == d - 1;
                    long h = Noise.hash(11, x, z);
                    if (edge && (h & 3) != 0) {
                        b.set(x, 0, z, (h & 8) == 0 ? Pal.LEAVES_AZALEA : Pal.LEAVES_OAK);
                        if ((h & 12) == 0) {
                            b.set(x, 1, z, Pal.LEAVES_FLOWER);
                        }
                    } else if (!edge && (h & 7) < 3) {
                        String[] crops = {"minecraft:wheat[age=7]", "minecraft:carrots[age=7]", "minecraft:potatoes[age=7]", "minecraft:grass"};
                        b.set(x, 0, z, crops[(int) ((h >>> 9) % crops.length)]);
                    }
                }
            }
            return b.build();
        });
    }

    /** Pile of concrete slabs / pallets with black film, as in the reference yards. 3x2x3. */
    public static Blueprint pallets() {
        return cached("pallets", () -> {
            BlueprintBuilder b = new BlueprintBuilder("pallets", 3, 2, 3);
            b.fill(0, 0, 0, 2, 0, 2, "minecraft:spruce_slab[type=bottom]");
            b.fill(0, 1, 0, 2, 1, 2, "minecraft:light_gray_concrete");
            b.fill(0, 1, 0, 1, 1, 1, "minecraft:black_concrete");
            return b.build();
        });
    }
}
