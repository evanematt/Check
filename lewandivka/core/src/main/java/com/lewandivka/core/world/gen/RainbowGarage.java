package com.lewandivka.core.world.gen;

import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.structure.BlueprintBuilder;
import com.lewandivka.core.structure.Dir;
import com.lewandivka.core.world.Pal;

/**
 * Cooperative «Веселковий гараж» (the Rainbow Garage): a hub with three wings and the Garage King's
 * arena. 35-45 minutes.
 *
 * <pre>
 *   z 1..22   Paint Workshop (north): colour-mixing taps, three colour-coded grates, battery
 *   z 24..48  Hub (x 22..46): entrance from the west, boss door east, three battery sockets,
 *             stairs down to the Industrial Press basement
 *   z 50..70  Lift Room (south): platform traversal with three lifts, battery at the top
 *   basement  Industrial Press: four timed presses, battery at the end
 *   x 47..91  Garage King arena (round, radius 19, three lifts)
 * </pre>
 *
 * <p>The surface layer is y = {@link #S}. Lifts are movable at runtime; {@link #walkVariant()}
 * draws them at both heights for the reachability checks.</p>
 */
public final class RainbowGarage {

    private RainbowGarage() {
    }

    public static final int SX = 92;
    public static final int SY = 38;
    public static final int SZ = 72;
    public static final int S = 8;
    public static final int ORIGIN_X = ChromaPlan.GARAGE_X - 34;
    public static final int ORIGIN_Y = ChromaPlan.GARAGE_Y - S;
    public static final int ORIGIN_Z = ChromaPlan.GARAGE_Z - 36;

    private static Blueprint cache;
    private static Blueprint variantCache;

    public static synchronized Blueprint blueprint() {
        if (cache == null) {
            cache = build(false);
        }
        return cache;
    }

    public static synchronized Blueprint walkVariant() {
        if (variantCache == null) {
            variantCache = build(true);
        }
        return variantCache;
    }

    private static Blueprint build(boolean extended) {
        BlueprintBuilder b = new BlueprintBuilder("rainbow_garage", SX, SY, SZ);
        b.clip(false);
        hub(b);
        entrance(b);
        paintWing(b);
        liftWing(b, extended);
        pressWing(b);
        arena(b, extended);
        b.region("body", 0, S, 0, SX - 1, S + 12, SZ - 1);
        return b.build();
    }

    private static final String[] RAINBOW = {
            Pal.RED_CONCRETE, Pal.ORANGE_CONCRETE, Pal.YELLOW_CONCRETE, Pal.LIME_CONCRETE,
            Pal.CYAN_CONCRETE, Pal.BLUE_CONCRETE, Pal.PURPLE_CONCRETE};

    // ------------------------------------------------------------------ hub & entrance

    private static void hub(BlueprintBuilder b) {
        // 25 x 25 hall, walls x 22 / 46, z 24 / 48, height 12
        b.fill(22, S, 24, 46, S, 48, Pal.METAL_PLATE);
        b.room(22, S, 24, 46, S + 12, 48, Pal.CONCRETE_GREY);
        b.fill(23, S + 1, 25, 45, S + 11, 47, Pal.AIR);
        b.fill(23, S, 25, 45, S, 47, Pal.POL_ANDESITE);
        // floor pattern: a rainbow ring in the middle
        b.ring(34, S, 36, 5, Pal.PINK_CONCRETE);
        b.ring(34, S, 36, 6, Pal.CYAN_CONCRETE);
        b.ring(34, S, 36, 7, Pal.YELLOW_CONCRETE);
        // ceiling lamps and columns
        for (int x : new int[] {27, 41}) {
            for (int z : new int[] {29, 43}) {
                b.fill(x, S + 1, z, x, S + 11, z, Pal.CONCRETE_DARK);
                b.set(x, S + 10, z, "minecraft:sea_lantern");
            }
        }
        b.set(34, S + 11, 36, Pal.light(15));
        b.set(34, S + 10, 36, "minecraft:sea_lantern");
        // doors: west (entrance corridor), north (paint), south (lift), east (boss)
        b.fill(22, S + 1, 34, 22, S + 3, 38, Pal.AIR);
        b.fill(33, S + 1, 24, 35, S + 3, 24, Pal.AIR);
        b.fill(33, S + 1, 48, 35, S + 3, 48, Pal.AIR);
        // boss door: closed until three batteries are inserted into the sockets above it
        b.gate("boss_door", 46, S + 1, 34, 46, S + 3, 38, Pal.GUARD_DOOR, true);
        for (int i = 0; i < 3; i++) {
            b.interact("socket_" + (i + 1), 45, S + 4, 34 + i * 2, Pal.socket(Dir.WEST));
        }
        b.set(45, S + 5, 36, Pal.note(30, Dir.WEST));
        // stairs down to the press basement (south-west corner of the hub): 9 steps to y = 3
        for (int i = 0; i < 6; i++) {
            b.fill(24 + i, S - i, 44, 24 + i, S - i, 46, Pal.AIR);
        }
        // rainbow banner on the outer facade is added by entrance()
        b.interact("cp_hub", 28, S + 1, 36, Pal.checkpoint());
        b.marker("hub", 34, S + 1, 36);
        b.marker("boss_door_front", 44, S + 1, 36);
    }

    private static void entrance(BlueprintBuilder b) {
        // corridor x 0..21 at z 34..38, a painted door facade at x = 0..2
        b.fill(0, S, 33, 21, S, 39, Pal.METAL_PLATE);
        b.room(0, S, 33, 21, S + 5, 39, Pal.CONCRETE_GREY);
        b.fill(1, S + 1, 34, 21, S + 4, 38, Pal.AIR);
        b.fill(0, S + 1, 34, 0, S + 3, 38, Pal.AIR);
        // rainbow stripes over the entrance (x 0), seven colours climbing the wall
        for (int i = 0; i < 7; i++) {
            b.fill(0, S + 1 + i, 30, 0, S + 1 + i, 32, RAINBOW[i]);
            b.fill(0, S + 1 + i, 40, 0, S + 1 + i, 42, RAINBOW[i]);
        }
        b.fill(0, S, 30, 0, S + 7, 42, Pal.CONCRETE_GREY);
        b.fill(0, S + 1, 34, 0, S + 3, 38, Pal.AIR);
        for (int i = 0; i < 7; i++) {
            for (int z = 30; z <= 42; z++) {
                if (z >= 34 && z <= 38 && i < 3) {
                    continue;
                }
                b.set(0, S + 1 + i, z, RAINBOW[(i + (z / 3)) % 7]);
            }
        }
        for (int x = 4; x <= 20; x += 8) {
            b.set(x, S + 4, 36, Pal.light(12));
            b.set(x, S + 4, 35, "minecraft:sea_lantern");
        }
        b.set(10, S + 2, 33, Pal.note(31, Dir.SOUTH));
        b.interact("cp_entrance", 3, S + 1, 36, Pal.checkpoint());
        b.marker("entrance", 1, S + 1, 36);
    }

    // ------------------------------------------------------------------ paint workshop (north)

    private static void paintWing(BlueprintBuilder b) {
        // workshop hall z 13..23, x 22..46
        b.fill(22, S, 12, 46, S, 24, Pal.METAL_PLATE);
        b.room(22, S, 12, 46, S + 9, 24, Pal.CONCRETE_GREY);
        b.fill(23, S + 1, 13, 45, S + 8, 23, Pal.AIR);
        b.fill(33, S + 1, 24, 35, S + 3, 24, Pal.AIR);
        b.fill(23, S, 13, 45, S, 23, Pal.POL_ANDESITE);
        // paint taps on the west wall: red, yellow, blue and the release valve
        String[] colors = {"red", "yellow", "blue", "release"};
        for (int i = 0; i < 4; i++) {
            b.interact("tap_" + colors[i], 23, S + 2, 14 + i * 3, Pal.tap(colors[i]));
        }
        // vats as decoration, one per primary
        b.fill(26, S + 1, 15, 27, S + 2, 16, "minecraft:red_concrete");
        b.fill(26, S + 1, 18, 27, S + 2, 19, "minecraft:yellow_concrete");
        b.fill(26, S + 1, 21, 27, S + 2, 22, "minecraft:blue_concrete");
        for (int z : new int[] {15, 18, 21}) {
            b.set(26, S + 3, z, Pal.light(10));
        }
        // pipes along the ceiling
        for (int x = 24; x <= 44; x++) {
            b.set(x, S + 8, 14, RAINBOW[(x / 3) % 7]);
            b.set(x, S + 8, 22, RAINBOW[(x / 3 + 3) % 7]);
        }
        b.set(40, S + 2, 14, Pal.note(32, Dir.SOUTH));
        // the corridor north: x 33..35, z 1..11, three grates at z 10, 7, 4 (orange circle, green triangle, purple square)
        b.fill(32, S, 0, 36, S, 12, Pal.METAL_PLATE);
        b.room(32, S, 0, 36, S + 5, 12, Pal.CONCRETE_GREY);
        b.fill(33, S + 1, 1, 35, S + 4, 11, Pal.AIR);
        b.fill(33, S + 1, 12, 35, S + 4, 12, Pal.AIR);
        String[] grate = {"orange", "green", "purple"};
        int[] gz = {10, 7, 4};
        for (int i = 0; i < 3; i++) {
            b.gate("grate_" + (i + 1), 33, S + 1, gz[i], 35, S + 3, gz[i], Pal.grate(grate[i]), true);
            b.set(34, S + 4, gz[i], Pal.light(12));
        }
        // battery dispenser at the end
        b.interact("battery_wing_p", 34, S + 1, 1, Pal.socket(Dir.SOUTH).replace("filled=false", "filled=true"));
        b.interact("cp_paint", 34, S + 1, 14, Pal.checkpoint());
        b.marker("paint_hall", 34, S + 1, 20);
        // a few gremlins' spawn spots
        b.marker("mob_paint_1", 40, S + 1, 18);
        b.marker("mob_paint_2", 28, S + 1, 20);
    }

    // ------------------------------------------------------------------ lift room (south)

    private static void liftWing(BlueprintBuilder b, boolean extended) {
        // hall x 22..46, z 49..70, tall: y up to S + 27
        b.fill(22, S, 48, 46, S, 71, Pal.METAL_PLATE);
        b.room(22, S, 48, 46, S + 28, 71, Pal.CONCRETE_GREY);
        b.fill(23, S + 1, 49, 45, S + 27, 70, Pal.AIR);
        b.fill(33, S + 1, 48, 35, S + 3, 48, Pal.AIR);
        b.fill(23, S, 49, 45, S, 70, Pal.POL_ANDESITE);
        // a pool in the middle of the floor: the safe way down from the top (3 deep, 7 x 7)
        b.fill(30, S - 3, 61, 38, S - 1, 68, Pal.AIR);
        b.fill(30, S - 4, 61, 38, S - 4, 68, Pal.CONCRETE_DARK);
        b.fill(30, S - 3, 61, 38, S - 1, 68, Pal.WATER);
        b.fill(30, S, 61, 38, S, 68, Pal.WATER);
        // entry ledge and the floor path from the door to the first lift
        b.fill(33, S, 49, 35, S, 52, Pal.CONCRETE_GREY);
        // ---- lift 1 at (24..26, 52..54): rises 8 from S to S + 8; call levers on both levels
        liftShaft(b, "lift_1", 24, 52, S, 8, extended);
        b.interact("lift_1_low", 27, S + 1, 53, Pal.lever(Dir.WEST));
        b.fill(27, S, 52, 31, S, 54, Pal.CONCRETE_GREY);
        // ledge L1 at y = S + 8 next to the lifted platform, then three stepping platforms across the pit
        b.fill(27, S + 8, 50, 31, S + 8, 56, Pal.CONCRETE_GREY);
        b.interact("lift_1_high", 28, S + 9, 54, Pal.lever(Dir.WEST));
        for (int x : new int[] {33, 36, 39}) {
            b.fill(x, S + 8, 54, x + 1, S + 8, 55, Pal.CONCRETE_GREY);
        }
        // ledge L2 (y = S + 8) with lift 2 rising to S + 16
        b.fill(41, S + 8, 52, 45, S + 8, 60, Pal.CONCRETE_GREY);
        liftShaft(b, "lift_2", 42, 57, S + 8, 8, extended);
        b.interact("lift_2_low", 41, S + 9, 56, Pal.lever(Dir.EAST));
        // ledge L3 at y = S + 16 right next to the lifted platform, then platforms west along z 58..59
        b.fill(38, S + 16, 56, 41, S + 16, 60, Pal.CONCRETE_GREY);
        b.interact("lift_2_high", 40, S + 17, 56, Pal.lever(Dir.EAST));
        for (int x : new int[] {35, 32, 29}) {
            b.fill(x, S + 16, 58, x + 1, S + 16, 59, Pal.CONCRETE_GREY);
        }
        // ledge L4 at y = S + 16 on the west side with lift 3 rising to S + 24
        b.fill(23, S + 16, 58, 28, S + 16, 66, Pal.CONCRETE_GREY);
        liftShaft(b, "lift_3", 24, 63, S + 16, 8, extended);
        b.interact("lift_3_low", 26, S + 17, 62, Pal.lever(Dir.WEST));
        // top gallery at y = S + 24 with the battery
        b.fill(27, S + 24, 63, 31, S + 24, 66, Pal.CONCRETE_GREY);
        b.fill(23, S + 24, 67, 31, S + 24, 70, Pal.CONCRETE_GREY);
        b.interact("lift_3_high", 27, S + 25, 64, Pal.lever(Dir.WEST));
        b.interact("battery_wing_l", 24, S + 25, 69, Pal.socket(Dir.EAST).replace("filled=false", "filled=true"));
        b.interact("cp_lift", 34, S + 1, 51, Pal.checkpoint());
        b.interact("cp_lift_top", 29, S + 25, 65, Pal.checkpoint());
        b.marker("lift_hall", 34, S + 1, 56);
        b.marker("lift_top", 28, S + 25, 68);
        b.marker("lift_pool", 34, S + 1, 62);
        // lights up the hall
        for (int y = S + 4; y <= S + 24; y += 8) {
            for (int x : new int[] {24, 44}) {
                b.set(x, y, 50, "minecraft:sea_lantern");
                b.set(x, y, 69, "minecraft:sea_lantern");
            }
        }
    }

    /** Lift platform 3 x 3 (x, z = min corner) at height y, with a region marker carrying the rise. */
    private static void liftShaft(BlueprintBuilder b, String name, int x, int z, int y, int rise, boolean extended) {
        b.fill(x, y, z, x + 2, y, z + 2, Pal.GARAGE_LIFT);
        b.region(name, x, y, z, x + 2, y, z + 2, "rise=" + rise);
        if (extended) {
            b.fill(x, y + rise, z, x + 2, y + rise, z + 2, Pal.GARAGE_LIFT);
        }
        // guide rails on both sides (decoration only)
        for (int yy = y + 1; yy <= y + rise + 3; yy++) {
            b.set(x - 1, yy, z + 1, "minecraft:iron_bars");
            b.set(x + 3, yy, z + 1, "minecraft:iron_bars");
        }
    }

    // ------------------------------------------------------------------ press basement

    private static void pressWing(BlueprintBuilder b) {
        // corridor along z 42..44 from x 24 to x 88, floor y = 2, interior y = 3..7 (surface layer is S = 8)
        b.fill(23, 2, 41, 89, S - 1, 45, Pal.CONCRETE_DARK);
        b.fill(24, 3, 42, 88, 7, 44, Pal.AIR);
        b.fill(24, 2, 42, 88, 2, 44, Pal.METAL_PLATE);
        // stairs from the hub land at x 29, z 42..44 (the staircase carved in hub() descends from x 24 to 29)
        for (int i = 0; i < 6; i++) {
            int x = 24 + i;
            int y = S - 1 - i;
            b.fill(x, y, 44, x, y, 46, Pal.CONCRETE_GREY);
            b.fill(x, y + 1, 44, x, y + 3, 46, Pal.AIR);
        }
        b.fill(24, 3, 44, 24, 7, 46, Pal.AIR);
        b.fill(25, 3, 45, 29, 7, 46, Pal.AIR);
        b.fill(24, 2, 44, 29, 2, 46, Pal.METAL_PLATE);
        // four presses: the crush zone is 3 wide, 3 high (y 3..5) at x = 40, 50, 60, 70
        int[] px = {40, 50, 60, 70};
        for (int i = 0; i < 4; i++) {
            int x = px[i];
            b.fill(x - 1, 6, 42, x + 1, 7, 44, "minecraft:iron_block");          // housing in the ceiling
            b.gate("press_" + (i + 1), x - 1, 3, 42, x + 1, 5, 44, Pal.PRESS_HEAD, false);
            b.fill(x - 1, 3, 42, x + 1, 5, 44, Pal.AIR);
            // warning lamps on both sides of every press
            b.set(x - 2, 5, 42, "minecraft:redstone_lamp[lit=false]");
            b.set(x + 2, 5, 42, "minecraft:redstone_lamp[lit=false]");
            // safe alcoves in the wall between the presses
            b.fill(x + 5, 3, 45, x + 6, 5, 46, Pal.AIR);
            b.set(x + 5, 6, 45, Pal.light(9));
        }
        for (int x = 26; x <= 86; x += 6) {
            b.set(x, 7, 43, Pal.light(9));
        }
        // end room with the battery
        b.fill(80, 3, 42, 88, 7, 44, Pal.AIR);
        b.interact("battery_wing_i", 88, 3, 43, Pal.socket(Dir.WEST).replace("filled=false", "filled=true"));
        b.interact("cp_press", 30, 3, 43, Pal.checkpoint());
        b.marker("press_start", 31, 3, 43);
        b.marker("press_end", 84, 3, 43);
    }

    // ------------------------------------------------------------------ boss arena (east)

    private static void arena(BlueprintBuilder b, boolean extended) {
        int cx = 70;
        int cz = 36;
        double r = 19.5;
        // approach corridor from the boss door
        b.fill(47, S, 33, 52, S, 39, Pal.METAL_PLATE);
        b.room(46, S, 33, 52, S + 6, 39, Pal.CONCRETE_GREY);
        b.fill(47, S + 1, 34, 52, S + 4, 38, Pal.AIR);
        b.fill(46, S + 1, 34, 46, S + 3, 38, Pal.AIR);
        b.gate("boss_door_inner", 46, S + 1, 34, 46, S + 3, 38, Pal.GUARD_DOOR, true);
        // dome walls and floor
        b.disc(cx, S, cz, r, Pal.METAL_PLATE);
        b.disc(cx, S, cz, 14, Pal.CONCRETE_DARK);
        b.ring(cx, S, cz, 14, Pal.YELLOW_CONCRETE);
        b.ring(cx, S, cz, 6, Pal.YELLOW_CONCRETE);
        for (int y = S + 1; y <= S + 15; y++) {
            b.disc(cx, y, cz, r - 1, Pal.AIR);
            b.ring(cx, y, cz, r, y % 4 == 0 ? Pal.RUST_CUT : Pal.CONCRETE_GREY);
        }
        b.disc(cx, S + 16, cz, r, Pal.CONCRETE_DARK);
        b.disc(cx, S + 16, cz, 6, Pal.PANE);
        // re-open the corridor mouth in the ring
        b.fill(51, S + 1, 34, 52, S + 3, 38, Pal.AIR);
        // three lifts at 120 degrees, radius 11: lift_a (north), lift_b (south-west), lift_c (south-east)
        double[] ang = {-Math.PI / 2, Math.PI * 5 / 6, Math.PI / 6};
        String[] names = {"a", "b", "c"};
        for (int i = 0; i < 3; i++) {
            int lx = cx + (int) Math.round(Math.cos(ang[i]) * 11) - 1;
            int lz = cz + (int) Math.round(Math.sin(ang[i]) * 11) - 1;
            b.fill(lx - 1, S, lz - 1, lx + 3, S, lz + 3, Pal.CONCRETE_GREY);
            b.fill(lx, S, lz, lx + 2, S, lz + 2, Pal.GARAGE_LIFT);
            b.region("lift_" + names[i], lx, S, lz, lx + 2, S, lz + 2, "rise=3");
            if (extended) {
                b.fill(lx, S + 3, lz, lx + 2, S + 3, lz + 2, Pal.GARAGE_LIFT);
            }
            // the lever stands at the lift's foot, always on the side facing the centre
            int lvx = lx + 1 + (int) Math.round(-Math.cos(ang[i]) * 3);
            int lvz = lz + 1 + (int) Math.round(-Math.sin(ang[i]) * 3);
            b.interact("lever_" + names[i], lvx, S + 1, lvz, Pal.lever(Dir.NORTH));
            b.fill(lvx, S + 2, lvz, lvx, S + 2, lvz, Pal.CONCRETE_DARK);
            b.set(lvx, S + 3, lvz, Pal.light(12));
        }
        // lamps around the rim, a gantry crane over the middle
        for (int k = 0; k < 12; k++) {
            double a = k * Math.PI / 6;
            int lx = cx + (int) Math.round(Math.cos(a) * 17);
            int lz = cz + (int) Math.round(Math.sin(a) * 17);
            b.set(lx, S + 12, lz, "minecraft:sea_lantern");
        }
        for (int x = cx - 14; x <= cx + 14; x++) {
            b.set(x, S + 14, cz, "minecraft:iron_bars");
        }
        b.interact("cp_arena", 53, S + 1, 36, Pal.checkpoint());
        b.marker("arena_center", cx, S + 1, cz);
        b.marker("boss_spawn", cx, S + 1, cz);
        b.region("arena", cx - 19, S, cz - 19, cx + 19, S + 15, cz + 19);
        b.marker("car_lane_a", cx, S + 1, cz - 17);
        b.marker("car_lane_b", cx, S + 1, cz + 17);
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 4 + i * Math.PI / 2;
            b.marker("minion_spawn_" + (i + 1), cx + (int) Math.round(Math.cos(a) * 15), S + 1, cz + (int) Math.round(Math.sin(a) * 15));
        }
        b.marker("arena_entry", 54, S + 1, 36);
    }
}
