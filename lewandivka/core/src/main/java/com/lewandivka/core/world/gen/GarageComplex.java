package com.lewandivka.core.world.gen;

import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.structure.BlueprintBuilder;
import com.lewandivka.core.structure.Dir;
import com.lewandivka.core.structure.Keys;
import com.lewandivka.core.world.Noise;
import com.lewandivka.core.world.Pal;

/**
 * Garage cooperative with the impossible Garage No. 13 (Act 1 dungeon, 20-30 minutes).
 *
 * <pre>
 *  z 0   north wall with the main gate (x 32..36)            yard (z 1..10): cars, barrels, lamps
 *  z 11  back wall of row A   row A garages (z 12..16), door wall z 17
 *  z 18..24  alley (7 wide)    ladder to the roof, notes, false arrows
 *  z 25  door wall of row B   row B garages (z 26..30), back wall z 31
 *  z 32..44  back yard         z 45 south wall        z 46..49 outside (manhole exit at x 32)
 *  x 61..68  maintenance block: control room, ladder shaft down to the tunnels (surface layer is y = 8)
 * </pre>
 *
 * <p>Row A numbers (left to right): 7 8 9 10 11 12 [none] 14 15 16 17 and a drive lane. Row B: a drive lane, 5 4 3 2 1 19 20 21 22 23 24.
 * The slot between 12 and 14 is a blank wall until all three power points are restored.</p>
 *
 * <p>Power points: A = four breakers in garage 9 pressed in the order given by four notes;
 * B = two switch boxes (garage 3 and 22) held together (scaled window); C = three levers in the
 * tunnel set to the pattern on a clue in garage 1.</p>
 */
public final class GarageComplex {

    private GarageComplex() {
    }

    public static final int ORIGIN_X = -145;
    public static final int ORIGIN_Y = 55;
    public static final int ORIGIN_Z = 76;
    public static final int SX = 70;
    public static final int SY = 18;
    public static final int SZ = 50;
    /** Blueprint layer that coincides with the district surface. */
    public static final int S = 8;
    /** Marker value in {@link #ROW_A}: the drive-through lane that joins the yard and the alley. */
    public static final int DRIVE = -1;

    public static final int[] ROW_A = {7, 8, 9, 10, 11, 12, 0, 14, 15, 16, 17, DRIVE};
    public static final int[] ROW_B = {DRIVE, 5, 4, 3, 2, 1, 19, 20, 21, 22, 23, 24};
    /** Correct breaker order in garage 9 (breaker numbers 1..4). */
    public static final int[] BREAKER_ORDER = {3, 1, 4, 2};
    /** Lever pattern of power point C: true = lever up. */
    public static final boolean[] LEVER_PATTERN = {true, false, true};

    private static boolean isOpen(boolean rowA, int slot) {
        int[] open = rowA ? new int[] {0, 2, 4, 5, 7, 9, 11} : new int[] {1, 3, 5, 7, 9, 11};
        for (int o : open) {
            if (o == slot) {
                return true;
            }
        }
        return false;
    }

    private static Blueprint cache;

    public static synchronized Blueprint garage13() {
        if (cache == null) {
            cache = build();
        }
        return cache;
    }

    private static Blueprint build() {
        BlueprintBuilder b = new BlueprintBuilder("garage13", SX, SY, SZ);
        yardAndWalls(b);
        garageRow(b, true);
        garageRow(b, false);
        alley(b);
        tunnels(b);
        maintenanceBlock(b);
        secretGarage(b);
        puzzles(b);
        roofAndClue(b);
        checkpoints(b);
        spawns(b);
        decay(b);
        b.region("body", 0, S, 0, SX - 1, S + 5, 45);
        return b.build();
    }

    // ------------------------------------------------------------------ yard, walls, gate

    private static void yardAndWalls(BlueprintBuilder b) {
        // ground inside the compound
        for (int x = 0; x < 69; x++) {
            for (int z = 0; z <= 45; z++) {
                long h = Noise.hash(9, x, z);
                b.set(x, S, z, (h & 7) == 0 ? Pal.COBBLE : (h & 7) == 1 ? Pal.GRAVEL : (h & 15) == 2 ? Pal.COARSE_DIRT : Pal.CONCRETE_DARK);
            }
        }
        b.fill(69, S, 0, 69, S, 45, Pal.BRICKS);
        // perimeter wall with rusty bars on top
        for (int x = 0; x < SX; x++) {
            for (int y = S + 1; y <= S + 3; y++) {
                b.set(x, y, 0, wall(x, y));
                b.set(x, y, 45, wall(x, y));
            }
            // tall rusty bars on top: nobody gets over the wall from the roofs
            for (int y = S + 4; y <= S + 7; y++) {
                b.set(x, y, 0, Pal.BARS);
                b.set(x, y, 45, Pal.BARS);
            }
        }
        for (int z = 1; z < 45; z++) {
            for (int y = S + 1; y <= S + 3; y++) {
                b.set(0, y, z, wall(z, y));
                b.set(69, y, z, wall(z, y));
            }
            for (int y = S + 4; y <= S + 7; y++) {
                b.set(0, y, z, Pal.BARS);
                b.set(69, y, z, Pal.BARS);
            }
        }
        // main gate in the north wall; open until the package is taken
        b.gate("gate_main", 32, S + 1, 0, 36, S + 4, 0, Pal.GUARD_DOOR, false);
        b.fill(32, S + 1, 0, 36, S + 4, 0, Pal.AIR);
        b.set(31, S + 1, 0, Pal.IRON).set(37, S + 1, 0, Pal.IRON);
        for (int y = S + 2; y <= S + 4; y++) {
            b.set(31, y, 0, Pal.IRON).set(37, y, 0, Pal.IRON);
        }
        // guard booth beside the gate
        b.room(24, S, 1, 28, S + 3, 5, Pal.PLASTER_PEEL);
        b.fill(25, S + 1, 2, 27, S + 2, 4, Pal.AIR);
        b.fill(28, S + 1, 3, 28, S + 2, 3, Pal.AIR);
        b.set(28, S + 1, 3, Keys.door("minecraft:spruce_door", Dir.EAST, false, false, false));
        b.set(28, S + 2, 3, Keys.door("minecraft:spruce_door", Dir.EAST, true, false, false));
        b.fill(26, S + 2, 1, 26, S + 2, 1, Pal.PANE);
        b.set(26, S + 3, 3, Pal.light(9));
        b.set(26, S + 1, 4, "minecraft:spruce_planks");
        b.set(26, S + 2, 4, "minecraft:potted_dead_bush");
        // yard furniture
        b.stamp(Props.car(Props.CAR_RUST, 0), 8, S + 1, 4, 0);
        b.stamp(Props.car(Props.CAR_BLUE, 0), 50, S + 1, 6, 0);
        b.stamp(Props.car(Props.CAR_WHITE, 1), 62, S + 1, 4, 0);
        b.stamp(Props.dumpster("junk"), 4, S + 1, 8, 0);
        b.stamp(Props.dumpster("junk"), 57, S + 1, 2, 0);
        b.stamp(Props.pallets(), 14, S + 1, 8, 0);
        b.stamp(Props.streetLamp(), 29, S + 1, 8, 0);
        b.stamp(Props.streetLamp(), 40, S + 1, 8, 0);
        // the sign at the gate and the first note
        b.set(34, S + 5, 0, Pal.note(5, Dir.SOUTH));
        b.set(30, S + 2, 1, Pal.note(6, Dir.SOUTH));
        b.marker("entry", 34, S + 1, 3);
        b.marker("yard_center", 34, S + 1, 6);
    }

    private static String wall(int i, int y) {
        long h = Noise.hash(31, i, y);
        return (h & 7) == 0 ? Pal.BRICKS_MOSSY : (h & 7) == 1 ? Pal.BRICKS_CRACKED : (h & 7) == 2 ? Pal.COBBLE : Pal.BRICKS;
    }

    // ------------------------------------------------------------------ garage rows

    private static void garageRow(BlueprintBuilder b, boolean rowA) {
        // Row A: back wall z=11, interior z=12..16, door wall z=17 (faces south).
        // Row B: door wall z=25 (faces north), interior z=26..30, back wall z=31.
        int back = rowA ? 11 : 31;
        int door = rowA ? 17 : 25;
        int zFrom = rowA ? 12 : 26;
        int zTo = rowA ? 16 : 30;
        int[] numbers = rowA ? ROW_A : ROW_B;
        Dir facing = rowA ? Dir.SOUTH : Dir.NORTH;       // direction the door wall faces (towards the alley)
        Dir inward = rowA ? Dir.NORTH : Dir.SOUTH;       // direction from the door into the garage
        // shell of the whole row: floor, roof, back wall, door wall, pier walls
        b.fill(0, S, back, 60, S, back, Pal.BRICKS);
        for (int slot = 0; slot < 12; slot++) {
            int x0 = 5 * slot;
            if (numbers[slot] == DRIVE) {
                // drive-through lane from the yard to the alley: open floor and air, the roof deck stays as a bridge
                for (int z = Math.min(back, door); z <= Math.max(back, door); z++) {
                    b.fill(x0, S + 1, z, x0, S + 4, z, Pal.CONCRETE_GREY);
                }
                b.fill(x0 + 1, S + 1, Math.min(back, door), x0 + 4, S + 4, Math.max(back, door), Pal.AIR);
                b.fill(x0 + 1, S, Math.min(back, door), x0 + 4, S, Math.max(back, door), Pal.CONCRETE_DARK);
                b.set(x0 + 2, S + 4, Math.min(back, door), Pal.light(11));
                continue;
            }
            // piers (left wall of the slot) full height
            for (int z = Math.min(back, door); z <= Math.max(back, door); z++) {
                for (int y = S + 1; y <= S + 4; y++) {
                    b.set(x0, y, z, Pal.CONCRETE_GREY);
                }
            }
            // interior volume
            b.fill(x0 + 1, S + 1, zFrom, x0 + 4, S + 3, zTo, Pal.AIR);
            b.fill(x0 + 1, S, zFrom, x0 + 4, S, zTo, Pal.POL_ANDESITE);
            b.fill(x0 + 1, S + 4, zFrom, x0 + 4, S + 4, zTo, Pal.CONCRETE_DARK);
            // back wall
            b.fill(x0 + 1, S + 1, back, x0 + 4, S + 4, back, Pal.CONCRETE_GREY);
            // door wall: lintel above, opening below (closed with a steel door when locked)
            b.fill(x0 + 1, S + 4, door, x0 + 4, S + 4, door, Pal.CONCRETE_GREY);
            if (isOpen(rowA, slot) && numbers[slot] != 0) {
                b.fill(x0 + 1, S + 1, door, x0 + 4, S + 3, door, Pal.AIR);
                b.set(x0 + 2, S + 3, zFrom + (rowA ? 2 : -2), Pal.light(11));
            } else if (numbers[slot] != 0) {
                b.fill(x0 + 1, S + 1, door, x0 + 4, S + 3, door, Pal.GUARD_DOOR);
                b.set(x0 + 2, S + 3, zFrom + (rowA ? 2 : -2), Pal.light(8));
            } else {
                // the missing garage: blank concrete (a gate marker lets the encounter open it later)
                b.fill(x0 + 1, S + 1, door, x0 + 4, S + 3, door, Pal.CONCRETE_GREY);
            }
            // plate with the garage number (or a blank plate for the missing one)
            int plate = numbers[slot] == 0 ? 36 : numbers[slot];
            b.set(x0 + 2, S + 4, door, Pal.plate(plate, facing));
            b.set(x0 + 3, S + 4, door, Pal.CONCRETE_GREY);
            // contents of open garages
            if (isOpen(rowA, slot) && numbers[slot] != 0) {
                furnish(b, x0, zFrom, zTo, inward, numbers[slot]);
            }
        }
        // closing pier at the east end of the row
        for (int z = Math.min(back, door); z <= Math.max(back, door); z++) {
            for (int y = S + 1; y <= S + 4; y++) {
                b.set(60, y, z, Pal.CONCRETE_GREY);
            }
        }
        // a flat roof deck over the whole row with a low parapet
        int rz1 = Math.min(back, door);
        int rz2 = Math.max(back, door);
        b.fill(0, S + 5, rz1, 60, S + 5, rz2, Pal.CONCRETE_DARK);
        for (int x = 0; x <= 60; x++) {
            b.set(x, S + 6, back, "minecraft:stone_brick_slab[type=bottom]");
        }
    }

    private static void furnish(BlueprintBuilder b, int x0, int zFrom, int zTo, Dir inward, int number) {
        boolean rowA = inward == Dir.NORTH;
        int zBack = rowA ? zFrom : zTo;   // interior cell next to the back wall
        long h = Noise.hash(77, number, 3);
        // shelves along the back wall
        b.set(x0 + 1, S + 1, zBack, Keys.barrel(inward.opposite()));
        b.set(x0 + 1, S + 2, zBack, "minecraft:bookshelf");
        b.set(x0 + 4, S + 1, zBack, "minecraft:chest[facing=" + inward.opposite().key() + "]");
        if ((h & 1) == 0) {
            b.set(x0 + 4, S + 2, zBack, "minecraft:potted_dead_bush");
        }
        // a workbench or a jacked-up car for flavour
        if ((h & 6) == 0) {
            b.set(x0 + 2, S + 1, zBack, "minecraft:crafting_table");
            b.set(x0 + 3, S + 1, zBack, "minecraft:anvil[facing=east]");
        } else if ((h & 6) == 2) {
            b.set(x0 + 2, S + 1, zBack, "minecraft:cobweb");
            b.set(x0 + 3, S + 3, zBack, "minecraft:cobweb");
        }
    }

    // ------------------------------------------------------------------ alley

    private static void alley(BlueprintBuilder b) {
        // lamp posts and the first false arrows: they point away from the real way
        b.stamp(Props.streetLamp(), 14, S + 1, 20, 0);
        b.stamp(Props.streetLamp(), 44, S + 1, 22, 0);
        b.stamp(Props.dumpster("junk"), 53, S + 1, 19, 0);
        // arrows hang on the piers: ids 32 = left, 33 = right
        b.set(10, S + 3, 18, Pal.plate(33, Dir.SOUTH));
        b.set(25, S + 3, 18, Pal.plate(32, Dir.SOUTH));
        b.set(40, S + 3, 18, Pal.plate(33, Dir.SOUTH));
        b.set(55, S + 3, 18, Pal.plate(32, Dir.SOUTH));
        b.set(15, S + 3, 24, Pal.plate(34, Dir.NORTH));
        b.set(50, S + 3, 24, Pal.plate(35, Dir.NORTH));
        // notes
        b.set(30, S + 3, 18, Pal.note(7, Dir.SOUTH));       // "12 ... 14"
        b.set(35, S + 3, 18, Pal.note(8, Dir.SOUTH));
        b.set(20, S + 3, 24, Pal.note(9, Dir.NORTH));
        // a stair run of crates leads up to the roof of row A (west end), ascending towards the east
        for (int i = 0; i < 5; i++) {
            b.set(1 + i, S + 1 + i, 18, Keys.stairs("minecraft:stone_brick_stairs", Dir.EAST, false));
            for (int y = S + 1; y < S + 1 + i; y++) {
                b.set(1 + i, y, 18, Pal.BRICKS);
            }
        }
        b.marker("alley_center", 34, S + 1, 21);
        b.marker("roof_access", 1, S + 1, 19);
    }

    // ------------------------------------------------------------------ the missing garage

    private static void secretGarage(BlueprintBuilder b) {
        // row A, slot 6: x 31..34, door wall z=17 is blank concrete until power point C is done
        b.gate("gate_garage13", 31, S + 1, 17, 34, S + 3, 17, Pal.CONCRETE_GREY, true);
        b.marker("plate_13", 32, S + 4, 17);
        // inside: a dusty office with a table and the parcel
        b.fill(31, S + 1, 12, 34, S + 3, 16, Pal.AIR);
        b.fill(31, S, 12, 34, S, 16, "minecraft:dark_oak_planks");
        b.fill(32, S + 1, 12, 33, S + 1, 12, "minecraft:spruce_planks");
        b.fill(32, S + 2, 12, 33, S + 2, 12, "minecraft:spruce_slab[type=bottom]");
        b.set(32, S + 3, 13, Pal.light(10));
        b.set(31, S + 1, 15, "minecraft:cobweb");
        b.set(34, S + 3, 15, "minecraft:cobweb");
        b.set(31, S + 1, 13, "minecraft:chest[facing=east]");
        b.set(34, S + 1, 13, Keys.barrel(Dir.WEST));
        b.set(33, S + 1, 13, "minecraft:potted_cactus");
        // the parcel sits on the table; it is a block entity-free marker spot
        b.set(32, S + 3, 12, Pal.AIR);
        b.marker("package_spawn", 32, S + 3, 12);
        b.marker("garage13_inside", 33, S + 1, 15);
        // the floor hatch to the maintenance tunnel: opens when the parcel is lifted
        b.fill(32, 6, 14, 33, S - 1, 15, Pal.AIR);          // shaft through the terrain down to the tunnel roof
        b.fill(32, 6, 14, 33, 6, 15, Pal.AIR);
        b.gate("hatch", 32, S, 14, 33, S, 15, Pal.GUARD_DOOR, true);
        for (int y = 3; y <= S - 1; y++) {
            b.set(32, y, 14, Keys.ladder(Dir.SOUTH));       // supported by the lining/terrain at z = 13
        }
    }

    // ------------------------------------------------------------------ control room (east)

    private static void maintenanceBlock(BlueprintBuilder b) {
        // x 61..68, z 11..31
        b.fill(61, S, 11, 68, S, 31, Pal.METAL_PLATE);
        b.room(61, S, 11, 68, S + 5, 31, Pal.CONCRETE_GREY);
        b.fill(62, S + 1, 12, 67, S + 4, 30, Pal.AIR);
        b.fill(62, S, 12, 67, S, 30, Pal.POL_ANDESITE);
        // door from the alley (west wall, z 21..22)
        b.fill(61, S + 1, 21, 61, S + 3, 22, Pal.AIR);
        // consoles on the east wall: indicator lamps for power points A, B, C
        for (int i = 0; i < 3; i++) {
            int z = 16 + i * 4;
            b.set(68, S + 3, z, Pal.panel("console", Dir.WEST));
            b.marker("indicator_" + (char) ('a' + i), 67, S + 3, z);
            b.set(68, S + 2, z, Pal.CONCRETE_DARK);
        }
        // the operator's lever: opens the tunnel gate for a limited time
        b.interact("escape_lever", 62, S + 2, 25, Pal.lever(Dir.EAST));
        b.set(62, S + 3, 25, Pal.note(10, Dir.EAST));
        b.set(64, S + 4, 18, Pal.light(12));
        b.set(64, S + 4, 26, Pal.light(12));
        // shaft down to the tunnels at x 65..66, z 21..22 (walls are the terrain itself)
        b.fill(65, 3, 21, 66, S, 22, Pal.AIR);
        b.set(67, S, 21, Pal.CONCRETE_GREY).set(67, S + 1, 21, Pal.CONCRETE_GREY);
        for (int y = 3; y <= S + 1; y++) {
            b.set(66, y, 21, Keys.ladder(Dir.WEST));
        }
        b.marker("control_room", 63, S + 1, 21);
        b.marker("shaft_top", 66, S + 1, 20);
        b.marker("shaft_bottom", 65, 3, 22);
    }

    // ------------------------------------------------------------------ tunnels (surface layer is S = 8)

    private static void tunnels(BlueprintBuilder b) {
        // T1 north-south: x 31..33, interior y 3..5, z 14..46, lined with concrete
        b.fill(30, 2, 13, 34, 6, 47, Pal.CONCRETE_DARK);
        b.fill(31, 3, 14, 33, 5, 46, Pal.AIR);
        b.fill(31, 2, 14, 33, 2, 46, Pal.METAL_PLATE);
        // T2 east-west under the alley: x 34..66, z 20..22
        b.fill(34, 2, 19, 67, 6, 23, Pal.CONCRETE_DARK);
        b.fill(34, 3, 20, 66, 5, 22, Pal.AIR);
        b.fill(34, 2, 20, 66, 2, 22, Pal.METAL_PLATE);
        // tunnel gate (closed until the operator pulls the lever)
        b.gate("tunnel_gate", 31, 3, 36, 33, 5, 36, Pal.GUARD_DOOR, true);
        // exit shaft to the street behind the compound: x 32..33, z 47..48, ladder on the south wall
        b.fill(32, 3, 47, 33, S + 1, 48, Pal.AIR);
        b.fill(31, 2, 47, 34, 2, 49, Pal.CONCRETE_DARK);
        b.fill(30, 3, 49, 34, S, 49, Pal.CONCRETE_DARK);
        for (int y = 3; y <= S + 1; y++) {
            b.set(32, y, 48, Keys.ladder(Dir.NORTH));
        }
        b.set(32, S + 1, 49, Pal.AIR);
        b.fill(32, S, 49, 33, S, 49, Pal.CONCRETE_DARK);
        b.marker("exit", 32, S + 1, 49);
        b.marker("tunnel_start", 32, 3, 16);
        // tunnel lighting and pipes
        for (int z = 18; z <= 46; z += 7) {
            b.set(32, 5, z, Pal.light(9));
        }
        for (int x = 36; x <= 64; x += 7) {
            b.set(x, 5, 21, Pal.light(9));
        }
        for (int x = 35; x <= 65; x++) {
            b.set(x, 5, 20, (Noise.hash(3, x, 4) & 3) == 0 ? "minecraft:chain[axis=x]" : Pal.RUST_CUT);
        }
    }

    // ------------------------------------------------------------------ puzzles

    private static void puzzles(BlueprintBuilder b) {
        // ---- power point A: four breakers in garage 9 (row A slot 2: x 11..14), back wall z = 11
        for (int i = 0; i < 4; i++) {
            b.interact("breaker_" + (i + 1), 11 + i, S + 2, 11, Pal.panel("breaker", Dir.SOUTH));
            b.set(11 + i, S + 3, 11, Pal.plate(i + 1, Dir.SOUTH));
        }
        b.marker("panel_a", 12, S + 1, 12);
        // four notes in garages 7, 11, 16 and 15 (row A back walls) give the order
        int[][] noteAt = {{2, 10}, {22, 11}, {47, 12}, {42, 13}};
        for (int[] n : noteAt) {
            b.set(n[0], S + 2, 11, Pal.note(n[1], Dir.SOUTH));
        }
        // ---- power point B: two switch boxes far apart, held together (garage 3 and garage 22, row B back wall z = 31)
        b.interact("sync_1", 17, S + 2, 31, Pal.panel("switchbox", Dir.NORTH));
        b.interact("sync_2", 47, S + 2, 31, Pal.panel("switchbox", Dir.NORTH));
        b.set(17, S + 3, 31, Pal.note(14, Dir.NORTH));
        b.set(47, S + 3, 31, Pal.note(15, Dir.NORTH));
        b.marker("panel_b", 18, S + 1, 30);
        // ---- power point C: three levers in the tunnel and a confirm console (x 44..50, north wall z = 19)
        for (int i = 0; i < 3; i++) {
            b.interact("lever_" + (i + 1), 44 + i * 2, 4, 19, Pal.lever(Dir.SOUTH));
            b.set(44 + i * 2, 5, 19, Pal.plate(i + 1, Dir.SOUTH));
        }
        b.interact("confirm_c", 50, 4, 19, Pal.panel("console", Dir.SOUTH));
        b.marker("panel_c", 47, 3, 21);
        // the clue for the lever pattern hangs in garage 1 (row B slot 5: x 26..29): up, down, up
        b.set(26, S + 2, 31, Pal.plate(34, Dir.NORTH));
        b.set(27, S + 2, 31, Pal.plate(35, Dir.NORTH));
        b.set(28, S + 2, 31, Pal.plate(34, Dir.NORTH));
        b.set(29, S + 2, 31, Pal.note(16, Dir.NORTH));
        // big lamps in the alley that light up with every restored power point
        for (int i = 0; i < 3; i++) {
            int x = 20 + i * 14;
            b.set(x, S + 6, 21, "minecraft:redstone_lamp[lit=false]");
            b.set(x, S + 5, 21, "minecraft:chain[axis=y]");
            b.marker("lamp_" + (char) ('a' + i), x, S + 6, 21);
        }
        // supplies for the diligent: junk in garage 22 and seeds in garage 24
        b.set(48, S + 1, 31, Pal.stash());
        b.marker("stash_a", 48, S + 1, 31, "loot=junk");
        b.set(58, S + 1, 31, Pal.stash());
        b.marker("stash_b", 58, S + 1, 31, "loot=seeds");
    }

    private static void roofAndClue(BlueprintBuilder b) {
        // rooftop clue, reached by the ladder at the west end of row A
        b.set(10, S + 6, 14, Pal.clue(4));
        b.marker("clue_roof", 10, S + 6, 14);
        b.stamp(Props.pallets(), 20, S + 6, 12, 0);
    }

    private static void checkpoints(BlueprintBuilder b) {
        b.interact("cp_0", 33, S + 1, 4, Pal.checkpoint());
        b.interact("cp_1", 38, S + 1, 21, Pal.checkpoint());
        b.interact("cp_2", 34, S + 1, 16, Pal.checkpoint());
        b.interact("cp_3", 31, 3, 30, Pal.checkpoint());
    }

    private static void spawns(BlueprintBuilder b) {
        b.marker("guard_spawn_1", 20, S + 1, 4);
        b.marker("guard_spawn_2", 50, S + 1, 4);
        b.marker("guard_spawn_3", 12, S + 1, 22);
        b.marker("guard_spawn_4", 56, S + 1, 22);
        b.marker("guard_spawn_5", 40, S + 1, 40);
        b.marker("guard_spawn_6", 24, S + 1, 40);
        b.marker("tunnel_guard", 32, 3, 28);
        b.region("alley", 1, S + 1, 18, 60, S + 4, 24);
        b.region("yard", 1, S + 1, 1, 68, S + 4, 10);
        b.region("tunnels", 31, 3, 13, 66, 5, 49);
    }

    /** Weathering: lighter and cracked patches in the yard concrete. Deterministic. */
    private static void decay(BlueprintBuilder b) {
        for (int x = 0; x < SX; x++) {
            for (int z = 0; z < 46; z++) {
                String k = b.get(x, S, z);
                if (Pal.CONCRETE_DARK.equals(k)) {
                    long h = Noise.hash(41, x, z);
                    if ((h & 15) == 0) {
                        b.set(x, S, z, Pal.CONCRETE_GREY);
                    } else if ((h & 31) == 1) {
                        b.set(x, S, z, Pal.BRICKS_CRACKED);
                    }
                }
            }
        }
    }
}
