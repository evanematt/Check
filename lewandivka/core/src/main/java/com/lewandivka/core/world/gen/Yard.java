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
 * Small things of the yards and the streets: market stalls, tool sheds of the allotments, a football field. Like the buildings
 * they are drawn with the front on the north side and rotated when placed.
 */
public final class Yard {

    private Yard() {
    }

    private static final Map<String, Blueprint> CACHE = new ConcurrentHashMap<>();

    /** The same blueprint turned by quarter turns (the fences and gardens of houses that face east or west). */
    public static Blueprint rotated(Blueprint bp, int turns) {
        if ((turns & 3) == 0) {
            return bp;
        }
        return CACHE.computeIfAbsent("rot" + bp.id() + "_" + bp.sizeX() + "x" + bp.sizeZ() + "_" + turns + "_" + System.identityHashCode(bp), k -> {
            int[] size = BlueprintBuilder.rotatedSize(bp, turns);
            BlueprintBuilder b = new BlueprintBuilder(bp.id(), size[0], bp.sizeY(), size[1]);
            b.stamp(bp, 0, 0, 0, turns);
            return b.build();
        });
    }

    // ================================================================== market stall

    private static final String[] GOODS = {"minecraft:melon", "minecraft:pumpkin", "minecraft:hay_block", "minecraft:red_mushroom_block", "minecraft:barrel[facing=up]"};

    /** A stall of the market: four posts, a striped awning, a counter with goods. {@code width} x 3 x 4 blocks, the front on the north side. */
    public static Blueprint stall(int width, int turns, long seed) {
        String key = "stall" + width + "_" + turns + "_" + seed;
        return CACHE.computeIfAbsent(key, k -> {
            boolean odd = (turns & 1) == 1;
            BlueprintBuilder b = new BlueprintBuilder("stall", odd ? 3 : width, 4, odd ? width : 3);
            b.orient(turns, width, 3);
            for (int x : new int[] {0, width - 1}) {
                for (int z : new int[] {0, 2}) {
                    b.fill(x, 0, z, x, 2, z, "minecraft:spruce_fence");
                }
            }
            for (int x = 0; x < width; x++) {
                boolean red = x % 2 == 0;
                for (int z = 0; z < 3; z++) {
                    b.set(x, 3, z, (red ? "minecraft:red_nether_brick_slab" : "minecraft:quartz_slab") + "[type=top]");
                }
                b.set(x, 0, 1, "minecraft:spruce_slab[type=top]");
                long h = Noise.hash(seed, x, 3);
                // the middle of the counter stays open: the trader stands behind it, and a block of goods (two blocks tall, with the
                // slab) would hide him from the customer completely
                if (x > 0 && x < width - 1 && x != width / 2 && (h & 3) != 0) {
                    b.set(x, 1, 1, GOODS[(int) Math.floorMod(h >>> 4, (long) GOODS.length)]);
                }
            }
            b.fill(1, 0, 2, width - 2, 0, 2, Keys.AIR);
            b.set(width - 2, 0, 2, Keys.barrel(Dir.NORTH));
            // the trader stands behind the open middle of the counter, in front of the customer (the people of the district: see Populace)
            b.marker("vendor", width / 2, 0, 2);
            // where a customer stands (the pictures of the client test go there)
            b.marker("customer", width / 2, 0, 0);
            return b.build();
        });
    }

    // ================================================================== tool shed

    /** A shed of boards of an allotment garden, big enough to walk into: 5 x 4, a door, a window, a bench and tools. */
    public static Blueprint shed(int turns, long seed) {
        String key = "shed" + turns + "_" + seed;
        return CACHE.computeIfAbsent(key, k -> {
            final int w = 5;
            final int d = 4;
            boolean odd = (turns & 1) == 1;
            BlueprintBuilder b = new BlueprintBuilder("shed", odd ? d + 2 : w, 5, odd ? w : d + 2);
            b.orient(turns, w, d + 2);
            b.at(0, 0, 2);
            b.room(0, 0, 0, w - 1, 3, d - 1, Pal.WOOD_SPRUCE);
            b.fill(0, 4, 0, w - 1, 4, d - 1, "minecraft:spruce_slab[type=bottom]");
            for (int x = 0; x < w; x += 4) {
                b.fill(x, 1, 0, x, 3, 0, Pal.LOG_SPRUCE);
                b.fill(x, 1, d - 1, x, 3, d - 1, Pal.LOG_SPRUCE);
            }
            b.fill(2, 1, 0, 2, 2, 0, Keys.AIR);
            b.set(2, 1, 0, Keys.door("minecraft:spruce_door", Dir.NORTH, false, false, false));
            b.set(2, 2, 0, Keys.door("minecraft:spruce_door", Dir.NORTH, true, false, false));
            b.fill(1, 2, d - 1, 3, 2, d - 1, Pal.WINDOW);
            b.set(1, 1, 2, "minecraft:crafting_table");
            b.set(3, 1, 3, Keys.barrel(Dir.NORTH));
            b.set(1, 1, 3, "minecraft:composter[level=3]");
            b.set(3, 1, 1, "minecraft:flower_pot");
            b.set(2, 3, 2, Keys.lantern(true));
            b.marker("entrance", 2, 1, -1);
            b.region("body", 0, 0, 0, w - 1, 4, d - 1);
            return b.build();
        });
    }

    // ================================================================== football field

    /** A pitch with white lines and a goal at both ends: {@code w} (along x) by {@code d}; the lines replace the grass (placed on the ground). */
    public static Blueprint field(int w, int d) {
        return CACHE.computeIfAbsent("field" + w + "x" + d, k -> {
            BlueprintBuilder b = new BlueprintBuilder("field", w, 4, d);
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < d; z++) {
                    boolean edge = x == 0 || z == 0 || x == w - 1 || z == d - 1;
                    boolean alongX = w > d;
                    boolean middle = alongX ? x == w / 2 : z == d / 2;
                    boolean circle = Math.abs(Math.hypot(x - w / 2.0, z - d / 2.0) - 3.5) < 0.6;
                    boolean box = alongX
                            ? (z == d / 2 - 4 || z == d / 2 + 4) && (x <= 4 || x >= w - 5) || (x == 4 || x == w - 5) && Math.abs(z - d / 2) <= 4
                            : (x == w / 2 - 4 || x == w / 2 + 4) && (z <= 4 || z >= d - 5) || (z == 4 || z == d - 5) && Math.abs(x - w / 2) <= 4;
                    if (edge || middle || circle || box) {
                        b.set(x, 0, z, Pal.CONCRETE_WHITE);
                    } else {
                        b.set(x, 0, z, Pal.GRASS);
                    }
                }
            }
            b.region("body", 0, 0, 0, w - 1, 3, d - 1);
            // the goals: two posts and a bar, at the ends of the long side
            boolean alongX = w > d;
            for (int end : new int[] {0, (alongX ? w : d) - 1}) {
                for (int off : new int[] {-2, 2}) {
                    int x = alongX ? end : w / 2 + off;
                    int z = alongX ? d / 2 + off : end;
                    b.fill(x, 1, z, x, 3, z, Pal.CONCRETE_WHITE);
                }
                if (alongX) {
                    b.fill(end, 3, d / 2 - 2, end, 3, d / 2 + 2, Pal.CONCRETE_WHITE);
                } else {
                    b.fill(w / 2 - 2, 3, end, w / 2 + 2, 3, end, Pal.CONCRETE_WHITE);
                }
            }
            return b.build();
        });
    }
}
