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
 * Natural features of Chromandivka: giant glowing mushrooms (see the reference), rainbow-leaf trees,
 * crystal spikes and floating islands with waterfalls. Layer 0 is the first block above the surface
 * for plants; islands are drawn with layer {@code depth} as their grass surface.
 */
public final class Nature {

    private Nature() {
    }

    private static final Map<String, Blueprint> CACHE = new ConcurrentHashMap<>();
    public static final String[] CAP_COLORS = {"violet", "cyan", "pink", "orange"};
    public static final int MUSHROOM_VARIANTS = 8;
    public static final int RAINBOW_TREE_VARIANTS = 4;
    public static final int CRYSTAL_VARIANTS = 6;

    // ------------------------------------------------------------------ giant glowshrooms

    /** Giant mushroom: variants 0..7 with different heights and cap colours. Trunk centred. */
    public static Blueprint glowshroom(int variant) {
        int v = Math.floorMod(variant, MUSHROOM_VARIANTS);
        return CACHE.computeIfAbsent("shroom" + v, k -> buildShroom(v));
    }

    private static Blueprint buildShroom(int v) {
        int stem = 6 + (v * 5) % 14;                // 6..19
        int capR = 4 + (v % 4) + stem / 5;          // 4..11
        String color = CAP_COLORS[v % CAP_COLORS.length];
        int size = 2 * capR + 3;
        int height = stem + capR + 3;
        BlueprintBuilder b = new BlueprintBuilder("glowshroom" + v, size, height, size);
        b.clip(true);
        int c = size / 2;
        // slightly bent stem
        for (int y = 0; y < stem; y++) {
            int off = (int) Math.round(Math.sin(y * 0.35 + v) * 1.0);
            b.fill(c + off - 0, y, c, c + off, y, c, Pal.GLOWSHROOM_STEM);
            if (y < 3) {
                b.fill(c - 1 + off, y, c - 1, c + 1 + off, y, c + 1, Pal.GLOWSHROOM_STEM);
            }
        }
        int topOff = (int) Math.round(Math.sin((stem - 1) * 0.35 + v));
        // cap: flattened dome with drooping rim
        for (int dy = 0; dy <= capR / 2 + 1; dy++) {
            double r = capR * Math.sqrt(Math.max(0, 1 - Math.pow(dy / (capR / 2.0 + 1), 2)));
            for (int x = -capR; x <= capR; x++) {
                for (int z = -capR; z <= capR; z++) {
                    double d = Math.sqrt(x * x + z * z);
                    if (d <= r + 0.3) {
                        b.set(c + topOff + x, stem + dy, c + z, Pal.cap(color));
                    }
                }
            }
        }
        // hanging glowing threads under the rim (as in the reference)
        for (int x = -capR; x <= capR; x++) {
            for (int z = -capR; z <= capR; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > capR - 1.2 && d <= capR + 0.3 && (Noise.hash(v, x, z) & 3) == 0) {
                    int len = 1 + (int) (Noise.hash(v + 9, x, z) & 3);
                    for (int i = 1; i <= len; i++) {
                        b.set(c + topOff + x, stem - i, c + z, "minecraft:end_rod[facing=down]");
                    }
                }
            }
        }
        b.marker("top", c + topOff, stem + 1, c);
        return b.build();
    }

    // ------------------------------------------------------------------ rainbow trees

    /** Round tree with rainbow leaves and a pale pink trunk. */
    public static Blueprint rainbowTree(int variant) {
        int v = Math.floorMod(variant, RAINBOW_TREE_VARIANTS);
        return CACHE.computeIfAbsent("rtree" + v, k -> {
            int trunk = 5 + v * 2;
            int r = 3 + v / 2 + (v == 3 ? 1 : 0);
            int size = 2 * r + 3;
            int height = trunk + r * 2 + 2;
            BlueprintBuilder b = new BlueprintBuilder("rainbow_tree" + v, size, height, size);
            b.clip(true);
            int c = size / 2;
            for (int y = 0; y < trunk; y++) {
                b.set(c, y, c, "minecraft:stripped_cherry_log[axis=y]");
            }
            b.set(c + 1, 0, c, "minecraft:stripped_cherry_log[axis=x]");
            b.set(c - 1, 0, c, "minecraft:stripped_cherry_log[axis=x]");
            for (int y = -r; y <= r; y++) {
                for (int x = -r; x <= r; x++) {
                    for (int z = -r; z <= r; z++) {
                        double d = x * x + z * z + y * y * 1.5;
                        if (d <= r * r + 1 && (d < r * r - 2 || (Noise.hash(v, x, y + 30, z) & 3) != 0)) {
                            b.set(c + x, trunk + r - 1 + y, c + z, Pal.RAINBOW_LEAVES);
                        }
                    }
                }
            }
            b.set(c, trunk, c, "minecraft:stripped_cherry_log[axis=y]");
            return b.build();
        });
    }

    // ------------------------------------------------------------------ crystals

    /** Spike formation of chromatic crystals with clusters growing from it. */
    public static Blueprint crystals(int variant) {
        int v = Math.floorMod(variant, CRYSTAL_VARIANTS);
        return CACHE.computeIfAbsent("crystal" + v, k -> {
            String[] colors = {"cyan", "violet", "pink", "yellow", "orange", "green"};
            String col = colors[v % colors.length];
            int h = 5 + (v * 3) % 9;
            int size = 9;
            BlueprintBuilder b = new BlueprintBuilder("crystals" + v, size, h + 3, size);
            b.clip(true);
            int c = size / 2;
            for (int i = 0; i < 3 + v % 3; i++) {
                int ox = (int) (Noise.hash(v, i, 1) % 3) - 1;
                int oz = (int) (Noise.hash(v, i, 2) % 3) - 1;
                int hh = h - i * 2;
                if (hh < 2) {
                    continue;
                }
                int bx = c + ox * (i + 1);
                int bz = c + oz * (i + 1);
                for (int y = 0; y < hh; y++) {
                    int w = Math.max(0, 1 - y * 2 / hh);
                    b.fill(bx - w, y, bz - w, bx + w, y, bz + w, Pal.crystal(col));
                }
                b.set(bx, hh, bz, Pal.cluster(col, "up"));
            }
            for (Dir d : Dir.values()) {
                b.set(c + d.dx * 2, 0, c + d.dz * 2, Pal.cluster(colors[(v + d.ordinal()) % colors.length], d.key()));
            }
            return b.build();
        });
    }

    // ------------------------------------------------------------------ floating islands

    /**
     * A floating island of the given radius: grass on top, dirt, rock cone below and an optional
     * waterfall that tumbles from one edge (decorative blocks, so it also works high above the world).
     * Layer {@code depth} is the grass surface; the surface of the island is at that layer.
     */
    public static Blueprint island(int radius, int seed, int waterfall, int trees) {
        String key = "island" + radius + "_" + seed + "_" + waterfall + "_" + trees;
        return CACHE.computeIfAbsent(key, k -> buildIsland(radius, seed, waterfall, trees));
    }

    public static int islandDepth(int radius) {
        return Math.max(6, radius * 3 / 4 + 3);
    }

    private static Blueprint buildIsland(int radius, int seed, int waterfall, int trees) {
        int depth = islandDepth(radius);
        int fall = waterfall != 0 ? 28 : 0;
        int size = 2 * radius + 5;
        int height = depth + 14 + fall;
        BlueprintBuilder b = new BlueprintBuilder("island" + radius + "_" + seed, size, height, size);
        b.clip(true);
        int c = size / 2;
        int top = depth + fall;       // layer of the grass surface (the waterfall hangs below)
        for (int x = -radius - 1; x <= radius + 1; x++) {
            for (int z = -radius - 1; z <= radius + 1; z++) {
                double ang = Math.atan2(z, x);
                double wob = 1 + 0.18 * Math.sin(ang * 3 + seed) + 0.1 * Math.sin(ang * 5 + seed * 2);
                double d = Math.sqrt(x * x + z * z) / (radius * wob);
                if (d > 1) {
                    continue;
                }
                // body depth falls off towards the rim: a flattened cone
                int thick = (int) Math.round(depth * Math.pow(1 - d, 0.7) + 1.5);
                for (int i = 0; i < thick; i++) {
                    int y = top - i;
                    String block = i == 0 ? Pal.GRASS : i < 3 ? Pal.DIRT : (Noise.hash(seed, x, y, z) & 15) == 0 ? Pal.AMETHYST : (i % 5 == 4 ? Pal.TUFF : "minecraft:stone");
                    b.set(c + x, y, c + z, block);
                }
            }
        }
        // sprinkle flowers and a tree or two on the surface
        for (int i = 0; i < radius * 2; i++) {
            int x = (int) Math.floorMod(Noise.hash(seed, i, 1), (long) (2 * radius - 1)) - radius + 1;
            int z = (int) Math.floorMod(Noise.hash(seed, i, 2), (long) (2 * radius - 1)) - radius + 1;
            if (x * x + z * z < (radius - 1) * (radius - 1) && Pal.GRASS.equals(b.get(c + x, top, c + z))) {
                String[] flowers = {"minecraft:allium", "minecraft:pink_tulip", "minecraft:azure_bluet", "minecraft:grass", "minecraft:grass", "minecraft:cornflower"};
                b.set(c + x, top + 1, c + z, flowers[(int) Math.floorMod(Noise.hash(seed, i, 3), 6L)]);
            }
        }
        for (int t = 0; t < trees; t++) {
            int x = (int) Math.floorMod(Noise.hash(seed, t, 11), (long) Math.max(1, radius - 2)) - (radius - 2) / 2;
            int z = (int) Math.floorMod(Noise.hash(seed, t, 12), (long) Math.max(1, radius - 2)) - (radius - 2) / 2;
            b.stamp(rainbowTree(t + seed), c + x - 3, top + 1, c + z - 3, 0);
        }
        if (waterfall != 0) {
            // a stream at the rim that falls (or climbs) as decorative blocks; waterfall = +1 down, -1 up
            double ang = seed * 1.3;
            int fx = (int) Math.round(Math.cos(ang) * (radius - 1));
            int fz = (int) Math.round(Math.sin(ang) * (radius - 1));
            String block = waterfall > 0 ? Pal.WATERFALL : Pal.WATERFALL_UP;
            for (int y = top; y > top - fall - depth + 2 && y >= 0; y--) {
                if (b.get(c + fx, y, c + fz) == null || y > top - 1) {
                    b.set(c + fx, y, c + fz, block);
                } else if (!Pal.GRASS.equals(b.get(c + fx, y, c + fz))) {
                    b.set(c + fx, y, c + fz, block);
                }
            }
            b.set(c + fx, top, c + fz, Pal.WATER);
        }
        b.marker("surface", c, top + 1, c);
        b.marker("rim_a", c + radius - 2, top + 1, c);
        b.marker("rim_b", c - radius + 2, top + 1, c);
        b.region("body", c - radius, top - depth, c - radius, c + radius, top + 6, c + radius);
        return b.build();
    }

    /** A tiny stepping-stone island used on the sky route (radius 3..5). */
    public static Blueprint stepping(int radius, int seed) {
        return island(radius, seed, 0, 0);
    }

    // ------------------------------------------------------------------ misc

    public static String leaves() {
        return Pal.RAINBOW_LEAVES;
    }

    public static String stem() {
        return Keys.of("lewandivka:glowshroom_stem");
    }
}
