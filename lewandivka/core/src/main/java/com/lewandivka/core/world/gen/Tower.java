package com.lewandivka.core.world.gen;

import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.structure.BlueprintBuilder;
import com.lewandivka.core.structure.Dir;
import com.lewandivka.core.structure.Keys;
import com.lewandivka.core.world.Noise;
import com.lewandivka.core.world.Pal;

/**
 * Head of District Tower: grey, bureaucratic, concrete, office lighting.
 *
 * <pre>
 *  F0 lobby (ticket machine, queue display, hidden "ТЕРМІНОВІ ПИТАННЯ" door)   -> cats
 *  F1 Archive (three levers in the reading room)                              -> lever sync of the shelter
 *  F2 Office 404 (the office that does not exist)                              -> hidden route
 *  F3 Department of Color Violations (paint taps, three grates)                -> colour mixing
 *  F4 Service Shaft (spring hatch + updraft to the ledge)                      -> springs
 *  F5 Collapsed Floor (platforms over a pit)                                   -> dash
 *  Arena: fourteen crumbling platforms around a centre, two relays             -> everything
 * </pre>
 *
 * <p>Floors are 16 apart, joined by 16-step stair shafts that are walled on both sides, so every
 * floor can only be left through its puzzle gate. All local, layer {@link #S} is the surface.</p>
 */
public final class Tower {

    private Tower() {
    }

    public static final int SX = 61;
    public static final int SY = 236;
    public static final int SZ = 61;
    public static final int C = 30;
    public static final int S = 2;
    public static final int ORIGIN_X = ChromaPlan.TOWER_X - C;
    public static final int ORIGIN_Y = ChromaPlan.TOWER_Y - S;
    public static final int ORIGIN_Z = ChromaPlan.TOWER_Z - C;

    public static final int[] FLOOR = {2, 18, 34, 50, 66, 96};
    public static final int ARENA = 120;

    private static final String WALL = Pal.CONCRETE_GREY;
    private static final String SLAB = Pal.POL_ANDESITE;

    private static Blueprint cache;

    public static synchronized Blueprint blueprint() {
        if (cache == null) {
            cache = build();
        }
        return cache;
    }

    private static Blueprint build() {
        BlueprintBuilder b = new BlueprintBuilder("tower", SX, SY, SZ);
        shell(b);
        lobby(b);
        archive(b);
        office(b);
        colorDepartment(b);
        serviceShaft(b);
        collapsedFloor(b);
        arena(b);
        spire(b);
        b.region("body", 3, S, 3, SX - 4, S + 12, SZ - 4);
        return b.build();
    }

    // ------------------------------------------------------------------ shell and slabs

    private static void shell(BlueprintBuilder b) {
        b.disc(C, S, C, 29, Pal.CONCRETE_DARK);
        b.disc(C, S - 1, C, 29, Pal.CONCRETE_DARK);
        for (int y = S + 1; y <= ARENA + 29; y++) {
            b.disc(C, y, C, 26.5, Pal.AIR);
            // band every sixth row, dirty streaks to keep the grey readable
            String wall = y % 6 == 0 ? Pal.CONCRETE_DARK : (Noise.hash(2, y, 7) & 15) == 0 ? Pal.ANDESITE : WALL;
            b.ring(C, y, C, 27.5, wall);
        }
        // window slits in every floor: gray glass panes in the wall ring
        for (int f = 0; f < FLOOR.length; f++) {
            int y = FLOOR[f] + 5;
            for (int k = 0; k < 24; k++) {
                double a = k * Math.PI / 12 + 0.13;
                int x = C + (int) Math.round(Math.cos(a) * 27);
                int z = C + (int) Math.round(Math.sin(a) * 27);
                if (b.get(x, y, z) != null && !Pal.AIR.equals(b.get(x, y, z))) {
                    b.set(x, y, z, "minecraft:gray_stained_glass_pane");
                    b.set(x, y + 1, z, "minecraft:gray_stained_glass_pane");
                }
            }
        }
        // floor slabs
        for (int i = 1; i < FLOOR.length; i++) {
            b.disc(C, FLOOR[i], C, 27, SLAB);
        }
        b.disc(C, FLOOR[0], C, 27, SLAB);
        // main entrance on the south side (the approach arrives from +z)
        b.fill(27, S + 1, 56, 33, S + 5, 58, Pal.AIR);
        b.fill(26, S + 6, 56, 34, S + 6, 58, Pal.CONCRETE_DARK);
        b.interact("cp_gate", 30, S + 1, 61 - 1, Pal.checkpoint());
        b.marker("entrance", 30, S + 1, 56);
        // forecourt
        b.fill(24, S, 58, 36, S, 60, Pal.POL_ANDESITE);
    }

    private static void ceilingLights(BlueprintBuilder b, int yCeil, int step) {
        for (int x = C - 24; x <= C + 24; x += step) {
            for (int z = C - 24; z <= C + 24; z += step) {
                if ((x - C) * (x - C) + (z - C) * (z - C) < 22 * 22) {
                    b.set(x, yCeil - 1, z, "minecraft:sea_lantern");
                }
            }
        }
    }

    /**
     * 16 steps climbing one floor, walled on both sides. {@code dz = -1}: starts at {@code zStart} and
     * climbs towards smaller z; {@code dz = +1}: the other way.
     */
    private static void stairShaft(BlueprintBuilder b, int x1, int x2, int floorY, int zStart, int dz) {
        Dir up = dz < 0 ? Dir.NORTH : Dir.SOUTH;
        int zEnd = zStart + dz * 15;
        for (int i = 0; i < 16; i++) {
            int z = zStart + dz * i;
            int y = floorY + 1 + i;
            for (int x = x1; x <= x2; x++) {
                if (i > 0) {
                    b.fill(x, floorY + 1, z, x, y - 1, z, Pal.CONCRETE_DARK);
                }
                b.fill(x, y + 1, z, x, y + 4, z, Pal.AIR);
                b.set(x, y, z, Keys.stairs("minecraft:stone_brick_stairs", up, false));
            }
        }
        // side walls from the floor up to three blocks above the next floor, along the whole run
        int zLo = Math.min(zStart, zEnd);
        int zHi = Math.max(zStart, zEnd);
        b.fill(x1 - 1, floorY + 1, zLo, x1 - 1, floorY + 19, zHi, WALL);
        b.fill(x2 + 1, floorY + 1, zLo, x2 + 1, floorY + 19, zHi, WALL);
        // lamp at the top
        b.set((x1 + x2) / 2, floorY + 19, zEnd, "minecraft:sea_lantern");
        // the entry end of the shaft is a wall; the caller opens it with a puzzle gate
        int zGate = zStart - dz;
        b.fill(x1 - 1, floorY + 1, zGate, x2 + 1, floorY + 7, zGate, WALL);
    }

    /** Standing position (feet) on the i-th stair of a shaft. */
    private static int stairStand(int floorY, int i) {
        return floorY + 2 + i;
    }

    // ------------------------------------------------------------------ F0: lobby

    private static void lobby(BlueprintBuilder b) {
        int y = FLOOR[0];
        ceilingLights(b, FLOOR[1], 6);
        // reception partition with the three-digit display "003" and a service door that never opens
        b.fill(20, y + 1, 14, 40, y + 7, 14, WALL);
        b.gate("door_main", 29, y + 1, 14, 31, y + 4, 14, Pal.officeDoor(), true);
        b.set(30, y + 7, 14, Pal.note(80, Dir.SOUTH));
        for (int i = 0; i < 3; i++) {
            b.interact("queue_" + (i + 1), 29 + i, y + 6, 14, Pal.queueDigit(i == 2 ? 3 : 0));
        }
        b.fill(21, y + 1, 12, 39, y + 1, 13, "minecraft:spruce_planks");
        // the ticket machine on the entrance axis
        b.interact("ticket_machine", 30, y + 1, 50, Pal.ticketMachine(Dir.SOUTH));
        // waiting chairs
        for (int z : new int[] {26, 30, 34}) {
            for (int x = 18; x <= 42; x += 2) {
                if (x >= 29 && x <= 31 && z >= 26) {
                    continue;
                }
                b.set(x, y + 1, z, Keys.stairs("minecraft:spruce_stairs", Dir.NORTH, false));
            }
        }
        // the urgent corridor: stair shaft on the east side behind a plain wall (x = 49)
        int sx1 = 50;
        int sx2 = 52;
        stairShaft(b, sx1, sx2, y, 42, -1);
        b.gate("hidden_urgent", sx1, y + 1, 43, sx2, y + 3, 43, WALL, true);
        b.fill(49, y + 1, 28, 49, y + 7, 42, WALL);
        b.fill(sx1 - 1, y + 4, 43, sx2 + 1, y + 4, 43, WALL);
        b.set(51, y + 3, 46, Pal.note(81, Dir.NORTH));
        b.set(55, y + 3, 44, Pal.note(82, Dir.NORTH));
        b.marker("cat_spot_urgent", 51, y + 1, 46);
        b.marker("urgent_front", 51, y + 1, 44);
        b.marker("lobby", 30, y + 1, 44);
        b.marker("f0_stairs", 51, stairStand(y, 2), 40);
        b.interact("cp_lobby", 28, y + 1, 48, Pal.checkpoint());
    }

    // ------------------------------------------------------------------ F1: archive

    private static void archive(BlueprintBuilder b) {
        int y = FLOOR[1];
        ceilingLights(b, FLOOR[2], 6);
        // shelf rows with aisles, tall archive shelves
        for (int z : new int[] {12, 18, 44, 48}) {
            for (int x = 14; x <= 46; x++) {
                if (x % 8 == 6) {
                    continue;
                }
                double dx = x - C;
                double dz = z - C;
                if (dx * dx + dz * dz > 24 * 24) {
                    continue;
                }
                for (int h = 1; h <= 4; h++) {
                    b.set(x, y + h, z, "minecraft:bookshelf");
                }
            }
        }
        // reading room with three levers, eight blocks apart
        for (int i = 0; i < 3; i++) {
            int x = 22 + i * 8;
            b.interact("lever_" + (i + 1), x, y + 1, 30, Pal.lever(Dir.SOUTH));
            b.fill(x, y + 2, 30, x, y + 3, 30, Pal.CONCRETE_DARK);
            b.set(x, y + 4, 30, "minecraft:redstone_lamp[lit=false]");
            b.marker("lamp_f1_" + (i + 1), x, y + 4, 30);
            b.fill(x - 1, y + 1, 29, x + 1, y + 1, 29, Pal.CONCRETE_DARK);
        }
        b.set(30, y + 6, 31, Pal.note(83, Dir.SOUTH));
        // the west stair shaft behind the archive's shelter door
        int sx1 = 8;
        int sx2 = 10;
        stairShaft(b, sx1, sx2, y, 18, +1);
        b.gate("gate_f1", sx1, y + 1, 17, sx2, y + 3, 17, Pal.SHELTER_DOOR, true);
        b.marker("f1_arrive", 51, y + 1, 26);
        b.marker("lever_hall_f1", 30, y + 1, 32);
        b.marker("f1_stairs", 9, stairStand(y, 2), 20);
        b.interact("cp_archive", 46, y + 1, 30, Pal.checkpoint());
    }

    // ------------------------------------------------------------------ F2: Office 404

    private static void office(BlueprintBuilder b) {
        int y = FLOOR[2];
        ceilingLights(b, FLOOR[3], 6);
        // a block of offices in the middle: solid, with numbered doors that stay shut
        b.fill(19, y + 1, 19, 41, y + 6, 41, WALL);
        for (int i = 0; i < 10; i++) {
            int num = 401 + i;
            int x = 21 + (i % 5) * 4;
            int z = i < 5 ? 19 : 41;
            b.fill(x, y + 1, z, x + 1, y + 3, z, Pal.officeDoor());
            b.set(x, y + 4, z, Pal.plate(1 + i, i < 5 ? Dir.NORTH : Dir.SOUTH));
            if (num == 404) {
                b.set(x + 1, y + 4, z, Pal.plate(36, i < 5 ? Dir.NORTH : Dir.SOUTH));
            }
        }
        // office 404 is not on the map: a blank wall on the west side hides it
        b.gate("hidden_404", 19, y + 1, 29, 19, y + 3, 31, WALL, true);
        b.set(18, y + 3, 30, Pal.note(84, Dir.WEST));
        b.marker("cat_spot_404", 17, y + 1, 30);
        b.marker("front_404", 17, y + 1, 29);
        b.fill(20, y + 1, 28, 24, y + 4, 32, Pal.AIR);
        b.fill(20, y, 28, 24, y, 32, "minecraft:dark_oak_planks");
        b.interact("lever_404", 24, y + 2, 30, Pal.lever(Dir.WEST));
        b.set(24, y + 4, 29, Pal.light(12));
        b.marker("office_404", 22, y + 1, 30);
        // stair shaft on the east, gated by the lever of office 404
        int sx1 = 50;
        int sx2 = 52;
        stairShaft(b, sx1, sx2, y, 42, -1);
        b.gate("gate_f2", sx1, y + 1, 43, sx2, y + 3, 43, Pal.SHELTER_DOOR, true);
        b.fill(49, y + 1, 28, 49, y + 7, 42, WALL);
        b.marker("f2_arrive", 9, y + 1, 34);
        b.marker("f2_stairs", 51, stairStand(y, 2), 40);
        b.marker("office_hall", 30, y + 1, 12);
        b.interact("cp_office", 12, y + 1, 30, Pal.checkpoint());
        b.set(46, y + 1, 36, Pal.note(85, Dir.WEST));
    }

    // ------------------------------------------------------------------ F3: Department of Color Violations

    private static void colorDepartment(BlueprintBuilder b) {
        int y = FLOOR[3];
        ceilingLights(b, FLOOR[4], 6);
        // counters with the four taps along the north-east wall
        String[] colors = {"red", "yellow", "blue", "release"};
        for (int i = 0; i < 4; i++) {
            int x = 24 + i * 4;
            b.fill(x - 1, y + 1, 12, x + 1, y + 1, 12, Pal.CONCRETE_DARK);
            b.interact("tap_f3_" + colors[i], x, y + 2, 12, Pal.tap(colors[i]));
        }
        for (int x = 20; x <= 40; x += 4) {
            b.set(x, y + 8, 13, i2c(x));
        }
        b.set(30, y + 4, 11, Pal.note(86, Dir.SOUTH));
        // the west stair shaft closed by three coloured grates side by side
        int sx1 = 8;
        int sx2 = 10;
        stairShaft(b, sx1, sx2, y, 18, +1);
        // the shaft is closed by three coloured grates side by side
        String[] grate = {"orange", "green", "purple"};
        for (int i = 0; i < 3; i++) {
            b.gate("grate_f3_" + (i + 1), sx1 + i, y + 1, 17, sx1 + i, y + 3, 17, Pal.grate(grate[i]), true);
        }
        b.marker("f3_arrive", 51, y + 1, 26);
        b.marker("f3_stairs", 9, stairStand(y, 2), 20);
        b.marker("dept_hall", 30, y + 1, 24);
        b.interact("cp_dept", 46, y + 1, 30, Pal.checkpoint());
    }

    private static String i2c(int x) {
        String[] cols = {Pal.RED_CONCRETE, Pal.YELLOW_CONCRETE, Pal.BLUE_CONCRETE, Pal.ORANGE_CONCRETE, Pal.GREEN_CONCRETE, Pal.PURPLE_CONCRETE};
        return cols[(x / 4) % cols.length];
    }

    // ------------------------------------------------------------------ F4: service shaft

    private static void serviceShaft(BlueprintBuilder b) {
        int y = FLOOR[4];
        int top = FLOOR[5];
        // the F4 ceiling (outside the shaft) sits at y = top - 8 so the shaft looks tall; the hall around it is a ring corridor
        b.disc(C, top - 8, C, 27, SLAB);
        // the central shaft wall: radius 11 from the floor to the F5 level, hole radius 5 in the F5 floor above
        for (int yy = y + 1; yy <= top; yy++) {
            b.disc(C, yy, C, 10.5, Pal.AIR);
            b.ring(C, yy, C, 11.5, Pal.CONCRETE_DARK);
        }
        b.fill(40, y + 1, 29, 42, y + 3, 31, Pal.AIR);                       // door in the shaft wall (east)
        // ring corridor lights and pipes
        for (int k = 0; k < 16; k++) {
            double a = k * Math.PI / 8;
            int x = C + (int) Math.round(Math.cos(a) * 20);
            int z = C + (int) Math.round(Math.sin(a) * 20);
            b.set(x, y + 6, z, "minecraft:sea_lantern");
            b.fill(x, y + 1, z, x, y + 5, z, Pal.CONCRETE_DARK);
        }
        // spring hatch in the middle and the updraft
        b.interact("hatch_f4", C, y, C, Pal.springHatch("up"));
        b.marker("hatch_f4_top", C, y + 1, C);
        b.region("wind_f4", C - 10, top - 8, C - 10, C + 10, top + 4, C + 10, "dir=east,speed=0.35");
        // the ledge in the F5 floor around the hole (r 5..10)
        b.disc(C, top, C, 10.5, SLAB);
        b.disc(C, top, C, 4.5, Pal.AIR);
        b.marker("f4_ledge", C + 8, top + 1, C);
        // a parapet on the east rim of the ledge: a rider who keeps pressing forward on the way up would fly over the edge of the
        // ledge (LaunchTest flies him); he bumps into the wall instead and drops onto the ledge. The rim north of it stays free for
        // the way to the first platform of the fifth floor
        b.fill(C + 11, top + 1, C - 4, C + 11, top + 5, C + 4, Pal.CONCRETE_DARK);
        // arrival from F3 (stair shaft top on the west side at z = 33) and the way to the shaft door
        b.marker("f4_arrive", 9, y + 1, 34);
        b.marker("f4_door", 39, y + 1, 30);
        b.interact("cp_shaft", 14, y + 1, 30, Pal.checkpoint());
        b.set(36, y + 2, 28, Pal.note(87, Dir.EAST));
    }

    // ------------------------------------------------------------------ F5: collapsed floor

    private static void collapsedFloor(BlueprintBuilder b) {
        int base = FLOOR[5];
        int top = ARENA;
        // the floor collapsed: clear the slab everywhere, then rebuild the ledge ring around the shaft
        b.disc(C, base, C, 26.5, Pal.AIR);
        b.disc(C, base, C, 10.5, SLAB);
        b.disc(C, base, C, 4.5, Pal.AIR);
        // ledge ring (inside the shaft) is the start; platforms radiate towards the north ladder
        double[][] plats = {{C + 12, C - 8}, {C + 14, C - 15}, {C + 6, C - 20}, {C - 4, C - 22}};
        int[][] spots = new int[plats.length][2];
        for (int i = 0; i < plats.length; i++) {
            int px = (int) plats[i][0];
            int pz = (int) plats[i][1];
            b.disc(px, base, pz, 2.6, Pal.crumbling(0));
            spots[i][0] = px;
            spots[i][1] = pz;
            b.marker("f5_p" + (i + 1), px, base + 1, pz);
        }
        // a safe landing before the ladder alcove at the north wall
        b.fill(26, base, 5, 34, base, 10, SLAB);
        b.marker("f5_end", 30, base + 1, 8);
        b.marker("f5_start", C + 8, base + 1, C);
        // lamps over the hall
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            b.set(C + (int) Math.round(Math.cos(a) * 21), base + 14, C + (int) Math.round(Math.sin(a) * 21), "minecraft:sea_lantern");
        }
        // fall zone: whoever falls is teleported back to the start
        b.region("fall_zone_f5", 3, base - 8, 3, 57, base - 1, 57);
        // the ladder alcove to the arena at the north wall, behind the boss door
        b.fill(28, base + 1, 4, 32, base + 4, 5, Pal.AIR);
        b.gate("boss_door", 28, base + 1, 6, 32, base + 4, 6, Pal.GUARD_DOOR, true);
        for (int y = base + 1; y <= top + 1; y++) {
            b.fill(29, y, 4, 31, y, 4, Pal.AIR);
            b.set(30, y, 4, Keys.ladder(Dir.SOUTH));
            b.fill(28, y, 3, 32, y, 3, Pal.CONCRETE_DARK);
            b.set(28, y, 4, Pal.CONCRETE_DARK).set(32, y, 4, Pal.CONCRETE_DARK);
        }
        b.interact("cp_collapsed", 26, base + 1, 8, Pal.checkpoint());
        b.set(30, base + 6, 7, Pal.note(88, Dir.SOUTH));
    }

    // ------------------------------------------------------------------ arena

    private static void arena(BlueprintBuilder b) {
        int y = ARENA;
        // entry platform and fourteen crumbling platforms
        b.fill(27, y, 5, 33, y, 9, SLAB);
        b.marker("arena_entry", 30, y + 1, 7);
        b.interact("cp_arena", 28, y + 1, 7, Pal.checkpoint());
        b.disc(C, y, C, 6, Pal.crumbling(0));
        b.region("plat_0", C - 6, y, C - 6, C + 6, y, C + 6, "ring=0");
        b.marker("arena_center", C, y + 1, C);
        b.marker("boss_spawn", C, y + 7, C);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + Math.PI / 6;
            int px = C + (int) Math.round(Math.cos(a) * 15);
            int pz = C + (int) Math.round(Math.sin(a) * 15);
            b.disc(px, y, pz, 3.4, Pal.crumbling(0));
            b.region("plat_" + (i + 1), px - 3, y, pz - 3, px + 3, y, pz + 3, "ring=1");
            b.marker("plat_pt_" + (i + 1), px, y + 1, pz);
            if (i % 2 == 0) {
                b.interact("pad_" + (i + 1), px, y + 1, pz, Keys.mod("spring_pad", "facing", "up"));
            }
        }
        for (int i = 0; i < 7; i++) {
            double a = i * 2 * Math.PI / 7;
            int px = C + (int) Math.round(Math.cos(a) * 23);
            int pz = C + (int) Math.round(Math.sin(a) * 23);
            int py = y + (i % 2 == 0 ? 0 : 3);
            b.disc(px, py, pz, 3.8, Pal.crumbling(0));
            b.region("plat_" + (i + 7), px - 4, py, pz - 4, px + 4, py, pz + 4, "ring=2");
            b.marker("plat_pt_" + (i + 7), px, py + 1, pz);
            if (i == 1) {
                b.interact("relay_a", px + 1, py + 1, pz, Pal.relay(Dir.NORTH));
            }
            if (i == 5) {
                b.interact("relay_b", px + 1, py + 1, pz, Pal.relay(Dir.NORTH));
            }
        }
        // roof of the arena
        b.disc(C, y + 30, C, 28, Pal.CONCRETE_DARK);
        for (int k = 0; k < 12; k++) {
            double a = k * Math.PI / 6;
            b.set(C + (int) Math.round(Math.cos(a) * 25), y + 14, C + (int) Math.round(Math.sin(a) * 25), "minecraft:sea_lantern");
        }
        b.region("arena", 3, y, 3, 57, y + 29, 57);
        b.region("fall_zone_arena", 3, y - 12, 3, 57, y - 1, 57);
        b.marker("cat_entry_a", 27, y + 1, 7);
        b.marker("cat_entry_b", 33, y + 1, 7);
    }

    private static void spire(BlueprintBuilder b) {
        int base = ARENA + 31;
        for (int y = base; y < SY - 1; y++) {
            double r = Math.max(1, 12 - (y - base) * 0.9);
            if (r <= 1.2) {
                b.set(C, y, C, Pal.CONCRETE_DARK);
                continue;
            }
            b.ring(C, y, C, r, y % 5 == 0 ? Pal.CONCRETE_DARK : WALL);
        }
    }
}
