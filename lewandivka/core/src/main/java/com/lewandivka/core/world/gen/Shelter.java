package com.lewandivka.core.world.gen;

import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.structure.BlueprintBuilder;
import com.lewandivka.core.structure.Dir;
import com.lewandivka.core.structure.Keys;
import com.lewandivka.core.world.Noise;
import com.lewandivka.core.world.Pal;

/**
 * Shelter of Lost Names: a bunker under a grassy mound.
 *
 * <pre>
 *  z 2..6    approach platform          z 7..13   the dash pit (7 wide: needs the Dash)
 *  z 14..24  entrance hall              z 26..44  lever hall (three lever stations, shelter door)
 *  z 46..62  hall of lost names (Chinazik's food route, rug, boss door south, warehouse door east)
 *  x 63..79  box warehouse (Metadonna)  z 64..90  Collar Collector arena (round, radius 13)
 * </pre>
 */
public final class Shelter {

    private Shelter() {
    }

    public static final int SX = 80;
    public static final int SY = 30;
    public static final int SZ = 98;
    public static final int S = 10;
    public static final int ORIGIN_X = ChromaPlan.SHELTER_X - 38;
    public static final int ORIGIN_Y = ChromaPlan.SHELTER_Y - S;
    public static final int ORIGIN_Z = ChromaPlan.SHELTER_Z - 12;

    private static Blueprint cache;

    public static synchronized Blueprint blueprint() {
        if (cache == null) {
            cache = build();
        }
        return cache;
    }

    private static Blueprint build() {
        BlueprintBuilder b = new BlueprintBuilder("shelter", SX, SY, SZ);
        mound(b);
        approach(b);
        entranceHall(b);
        leverHall(b);
        nameHall(b);
        warehouse(b);
        arena(b);
        b.region("body", 8, S, 14, SX - 2, S + 8, SZ - 2);
        return b.build();
    }

    // ------------------------------------------------------------------ mound

    private static void mound(BlueprintBuilder b) {
        // footprint of the buried rooms
        int x1 = 12;
        int x2 = 72;
        int z1 = 13;
        int z2 = 90;
        for (int x = x1 - 6; x <= x2 + 6; x++) {
            for (int z = z1 - 4; z <= Math.min(z2 + 4, SZ - 1); z++) {
                int inx = Math.min(x - x1, x2 - x);
                int inz = Math.min(z - z1, z2 - z);
                int d = Math.min(inx, inz);
                if (d < -5) {
                    continue;
                }
                int h = (int) Math.round(9 * Noise.smoothstep(-5, 10, d));
                for (int y = 1; y <= h; y++) {
                    String k = y == h ? Pal.GRASS : (y > h - 3 ? Pal.DIRT : "minecraft:stone");
                    b.set(x, S + y + 6, z, k); // sits on top of the bunker roof at S + 6
                }
                if (d >= -5) {
                    b.set(x, S + 6, z, d >= 0 ? Pal.CONCRETE_DARK : Pal.DIRT);
                }
            }
        }
    }

    private static void room(BlueprintBuilder b, int x1, int z1, int x2, int z2, int h, String wall) {
        b.fill(x1, S, z1, x2, S, z2, Pal.POL_ANDESITE);
        b.room(x1, S, z1, x2, S + h, z2, wall);
        b.fill(x1 + 1, S + 1, z1 + 1, x2 - 1, S + h - 1, z2 - 1, Pal.AIR);
        b.fill(x1 + 1, S, z1 + 1, x2 - 1, S, z2 - 1, Pal.POL_ANDESITE);
    }

    /** Hanging lanterns on a grid; {@code y} is the topmost interior layer so they hang from the ceiling. */
    private static void lamps(BlueprintBuilder b, int x1, int z1, int x2, int z2, int y, int step) {
        for (int x = x1; x <= x2; x += step) {
            for (int z = z1; z <= z2; z += step) {
                b.set(x, y, z, Keys.lantern(true));
            }
        }
    }

    // ------------------------------------------------------------------ approach & the dash pit

    private static void approach(BlueprintBuilder b) {
        b.fill(30, S, 2, 46, S, 6, Pal.BRICKS);
        b.fill(30, S, 14, 46, S, 16, Pal.BRICKS);
        // the pit: 7 long, 6 deep, water at the bottom; a ladder on the near side lets players climb out
        b.fill(30, S - 6, 7, 46, S - 1, 13, Pal.AIR);
        b.fill(30, S - 7, 7, 46, S - 7, 13, Pal.CONCRETE_DARK);
        b.fill(30, S - 6, 7, 46, S - 5, 13, Pal.WATER);
        b.fill(29, S - 7, 7, 29, S, 13, Pal.CONCRETE_DARK);
        b.fill(47, S - 7, 7, 47, S, 13, Pal.CONCRETE_DARK);
        for (int y = S - 6; y <= S; y++) {
            b.set(31, y, 7, Keys.ladder(Dir.SOUTH));
        }
        b.fill(31, S - 6, 6, 31, S, 6, Pal.CONCRETE_DARK);
        // rails to guide the eye, signs and lamps
        for (int x = 30; x <= 46; x += 4) {
            b.set(x, S + 1, 2, "minecraft:polished_blackstone_wall");
            b.set(x, S + 2, 2, Keys.lantern(false));
        }
        b.set(36, S + 2, 6, Pal.note(40, Dir.SOUTH));
        b.interact("cp_entrance", 38, S + 1, 3, Pal.checkpoint());
        b.marker("entrance", 38, S + 1, 4);
        b.marker("platform_a", 38, S + 1, 5);
        b.marker("platform_b", 38, S + 1, 15);
        b.region("pit", 30, S - 7, 7, 46, S - 1, 13);
        b.marker("pit_exit", 31, S + 1, 5);
    }

    // ------------------------------------------------------------------ entrance & lever hall

    private static void entranceHall(BlueprintBuilder b) {
        room(b, 31, 14, 45, 25, 6, Pal.CONCRETE_GREY);
        b.fill(37, S + 1, 25, 39, S + 3, 25, Pal.AIR);
        b.fill(37, S + 1, 14, 39, S + 3, 14, Pal.AIR);
        b.fill(37, S + 1, 15, 39, S + 3, 15, Pal.AIR);
        lamps(b, 34, 17, 42, 22, S + 5, 4);
        b.set(33, S + 2, 14, Pal.note(41, Dir.SOUTH));
        b.marker("hall_entrance", 38, S + 1, 18);
    }

    private static void leverHall(BlueprintBuilder b) {
        room(b, 20, 26, 56, 45, 7, Pal.CONCRETE_GREY);
        b.fill(37, S + 1, 25, 39, S + 3, 26, Pal.AIR);
        // three lever stations along the north wall, 8 blocks apart so one player can reach them in sequence
        for (int i = 0; i < 3; i++) {
            int x = 30 + i * 8;
            b.fill(x - 1, S, 28, x + 1, S, 30, Pal.CONCRETE_DARK);
            b.interact("lever_" + (i + 1), x, S + 1, 28, Pal.lever(Dir.SOUTH));
            b.fill(x, S + 2, 28, x, S + 3, 28, Pal.CONCRETE_DARK);
            b.set(x, S + 4, 28, "minecraft:redstone_lamp[lit=false]");
            b.marker("lamp_" + (i + 1), x, S + 4, 28);
        }
        b.set(38, S + 3, 27, Pal.note(42, Dir.SOUTH));
        // south door of the hall: a shelter door that opens when the three levers are held together
        b.gate("gate_levers", 37, S + 1, 45, 39, S + 4, 45, Pal.SHELTER_DOOR, true);
        // dividing pillars and benches
        for (int x : new int[] {26, 50}) {
            for (int z : new int[] {33, 40}) {
                b.fill(x, S + 1, z, x, S + 6, z, Pal.CONCRETE_DARK);
            }
        }
        lamps(b, 24, 31, 52, 43, S + 6, 7);
        b.interact("cp_levers", 38, S + 1, 33, Pal.checkpoint());
        b.marker("lever_hall", 38, S + 1, 36);
    }

    // ------------------------------------------------------------------ hall of lost names

    private static void nameHall(BlueprintBuilder b) {
        room(b, 14, 46, 62, 62, 8, Pal.CONCRETE_GREY);
        b.fill(37, S + 1, 46, 39, S + 4, 46, Pal.AIR);
        // overgrown: moss floor patches, vines on the walls, glowing lanterns
        for (int x = 15; x <= 61; x++) {
            for (int z = 47; z <= 61; z++) {
                long h = Noise.hash(5, x, z);
                if ((h & 7) < 2) {
                    b.set(x, S, z, "minecraft:moss_block");
                    if ((h & 31) == 3) {
                        b.set(x, S + 1, z, "minecraft:moss_carpet");
                    }
                }
            }
        }
        for (int x = 16; x <= 60; x += 4) {
            b.set(x, S + 5, 47, Keys.vine(Dir.SOUTH));
            b.set(x + 1, S + 4, 61, Keys.vine(Dir.NORTH));
        }
        // columns carrying plaques with names (some crossed out): notes 43..49
        int[][] cols = {{22, 50}, {22, 58}, {34, 52}, {44, 56}, {54, 50}, {54, 58}};
        for (int i = 0; i < cols.length; i++) {
            b.fill(cols[i][0], S + 1, cols[i][1], cols[i][0], S + 7, cols[i][1], Pal.CONCRETE_DARK);
            b.set(cols[i][0], S + 3, cols[i][1] + 1, Pal.note(43 + i % 4, Dir.SOUTH));
            if (i < 4) {
                // the fish for Chinazik's route lie in rubbish heaps at the feet of four columns
                b.set(cols[i][0] + 1, S + 1, cols[i][1], Pal.stash());
                b.marker("stash_fish_" + (i + 1), cols[i][0] + 1, S + 1, cols[i][1], "loot=fish");
            }
        }
        lamps(b, 18, 49, 58, 59, S + 7, 8);
        // Chinazik's route: start, four food points and the old rug on the east side
        b.marker("cat_hall", 38, S + 1, 54);
        b.marker("cat_spawn", 36, S + 1, 60);
        b.marker("cat_start", 38, S + 1, 49);
        int[][] food = {{46, 52}, {52, 55}, {50, 60}, {42, 58}};
        for (int i = 0; i < 4; i++) {
            b.fill(food[i][0] - 1, S, food[i][1] - 1, food[i][0] + 1, S, food[i][1] + 1, "minecraft:dark_oak_planks");
            b.interact("cat_food_" + (i + 1), food[i][0], S + 1, food[i][1], "minecraft:stone_pressure_plate");
        }
        b.set(58, S, 54, Pal.OLD_RUG);
        b.marker("cat_rug", 58, S + 1, 54);
        // doors: east to the warehouse (opens when Chinazik reaches the rug), south to the boss arena
        b.gate("gate_warehouse", 62, S + 1, 53, 62, S + 3, 55, Pal.SHELTER_DOOR, true);
        b.gate("boss_door", 37, S + 1, 62, 39, S + 4, 62, Pal.COLLECTOR_DOOR, true);
        b.interact("cp_names", 38, S + 1, 50, Pal.checkpoint());
    }

    // ------------------------------------------------------------------ box warehouse

    private static void warehouse(BlueprintBuilder b) {
        room(b, 63, 44, 79, 62, 6, Pal.RUST_CUT);
        b.fill(63, S + 1, 53, 63, S + 3, 55, Pal.AIR);
        // a grid of cardboard boxes with aisles; exactly one is unusually small
        int smallX = 73;
        int smallZ = 49;
        for (int x = 65; x <= 77; x++) {
            for (int z = 46; z <= 60; z++) {
                boolean aisle = x % 3 == 0 || z % 4 == 1 || (x >= 64 && x <= 66 && z >= 52 && z <= 56);
                if (aisle) {
                    continue;
                }
                long h = Noise.hash(8, x, z);
                if ((h & 7) == 0) {
                    continue;
                }
                boolean isSmall = x == smallX && z == smallZ;
                b.set(x, S + 1, z, Pal.box(isSmall));
                if (!isSmall && (h & 7) >= 6) {
                    b.set(x, S + 2, z, Pal.box(false));
                }
            }
        }
        // make sure the little one is accessible from an aisle and not buried
        b.set(smallX, S + 1, smallZ, Pal.box(true));
        b.air(smallX, S + 2, smallZ);
        b.marker("box_small", smallX, S + 1, smallZ);
        b.region("boxes", 65, S + 1, 46, 77, S + 2, 60);
        b.marker("box_warehouse", 66, S + 1, 54);
        b.set(70, S + 5, 54, Pal.light(10));
        b.set(66, S + 5, 50, Pal.light(10));
        b.set(76, S + 5, 58, Pal.light(10));
        b.interact("cp_warehouse", 65, S + 1, 54, Pal.checkpoint());
    }

    // ------------------------------------------------------------------ boss arena

    private static void arena(BlueprintBuilder b) {
        int cx = 38;
        int cz = 77;
        double r = 13.5;
        // corridor from the boss door
        b.fill(36, S, 62, 40, S, 66, Pal.POL_ANDESITE);
        b.room(36, S, 62, 40, S + 5, 66, Pal.CONCRETE_GREY);
        b.fill(37, S + 1, 62, 39, S + 4, 66, Pal.AIR);
        b.disc(cx, S, cz, r, Pal.POL_ANDESITE);
        b.ring(cx, S, cz, 9, "minecraft:purple_concrete");
        b.ring(cx, S, cz, 4, "minecraft:magenta_concrete");
        for (int y = S + 1; y <= S + 12; y++) {
            b.disc(cx, y, cz, r - 1, Pal.AIR);
            b.ring(cx, y, cz, r, Pal.CONCRETE_GREY);
        }
        b.disc(cx, S + 13, cz, r, Pal.CONCRETE_DARK);
        b.fill(37, S + 1, 64, 39, S + 4, 66, Pal.AIR);
        // eight collar stands around the rim and three leash anchor posts
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + Math.PI / 8;
            int sx = cx + (int) Math.round(Math.cos(a) * 10);
            int sz = cz + (int) Math.round(Math.sin(a) * 10);
            b.interact("stand_" + (i + 1), sx, S + 1, sz, Pal.collarStand(Dir.NORTH));
        }
        for (int i = 0; i < 3; i++) {
            double a = i * Math.PI * 2 / 3 + 0.4;
            int ax = cx + (int) Math.round(Math.cos(a) * 6);
            int az = cz + (int) Math.round(Math.sin(a) * 6);
            b.fill(ax, S + 1, az, ax, S + 3, az, "minecraft:purple_stained_glass");
            b.marker("anchor_" + (i + 1), ax, S + 1, az);
        }
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            b.set(cx + (int) Math.round(Math.cos(a) * 12), S + 11, cz + (int) Math.round(Math.sin(a) * 12), "minecraft:sea_lantern");
        }
        b.interact("cp_arena", 38, S + 1, 65, Pal.checkpoint());
        b.marker("arena_center", cx, S + 1, cz);
        b.marker("boss_spawn", cx, S + 1, cz);
        b.marker("arena_entry", 38, S + 1, 68);
        b.region("arena", cx - 14, S, cz - 14, cx + 14, S + 13, cz + 14);
    }
}
