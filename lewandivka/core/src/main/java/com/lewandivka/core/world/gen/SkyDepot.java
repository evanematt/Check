package com.lewandivka.core.world.gen;

import com.lewandivka.core.puzzle.TramNetwork;
import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.structure.BlueprintBuilder;
import com.lewandivka.core.structure.Dir;
import com.lewandivka.core.structure.Keys;
import com.lewandivka.core.world.Noise;
import com.lewandivka.core.world.Pal;

/**
 * Tram Depot above the sky: arrival stop, a dispatcher room with the timetable clue and the
 * four-switch desk, a rail yard whose switch network is the puzzle (see {@link TramNetwork#depot()}),
 * the ticket office at the winning end and the Conductor's arena crossed by three active tram lanes.
 *
 * <p>The yard layout mirrors the network: nodes lie on the line z = 30; every wrong exit is a stub with
 * a buffer stop. Arrows on the timetable read left (A) / right (B): →, ←, →, ←.</p>
 */
public final class SkyDepot {

    private SkyDepot() {
    }

    public static final int SX = 92;
    public static final int SY = 40;
    public static final int SZ = 92;
    public static final int S = 12;
    public static final int ORIGIN_X = 128;
    public static final int ORIGIN_Y = 190;
    public static final int ORIGIN_Z = -214;

    /** Positions of the network nodes in local x/z (index = node id of {@link TramNetwork#depot()}). */
    public static final int[][] NODES = {
            {26, 30}, {34, 30}, {42, 30}, {50, 30},   // s0..s3
            {26, 38}, {34, 22}, {42, 38}, {50, 22},   // dead ends d0..d3
            {60, 30}                                  // goal
    };
    public static final int[] LANE_Z = {58, 66, 74};

    private static Blueprint cache;

    public static synchronized Blueprint blueprint() {
        if (cache == null) {
            cache = build();
        }
        return cache;
    }

    private static Blueprint build() {
        BlueprintBuilder b = new BlueprintBuilder("sky_depot", SX, SY, SZ);
        platform(b);
        arrival(b);
        dispatcher(b);
        yard(b);
        ticketOffice(b);
        arena(b);
        b.region("body", 2, S, 2, SX - 3, S + 10, SZ - 3);
        return b.build();
    }

    // ------------------------------------------------------------------ floating platform

    private static void platform(BlueprintBuilder b) {
        int x1 = 2;
        int x2 = SX - 3;
        int z1 = 2;
        int z2 = SZ - 3;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                int d = Math.min(Math.min(x - x1, x2 - x), Math.min(z - z1, z2 - z));
                b.set(x, S, z, (Noise.hash(3, x, z) & 7) == 0 ? Pal.CONCRETE_DARK : Pal.CONCRETE_GREY);
                int thick = (int) Math.round(10 * Noise.smoothstep(0, 18, d));
                for (int i = 1; i <= thick; i++) {
                    b.set(x, S - i, z, i <= 2 ? Pal.CONCRETE_DARK : i % 3 == 0 ? Pal.TUFF : "minecraft:stone");
                }
            }
        }
        // rail-bed stripes of the whole platform edge: a low parapet with lamps
        for (int x = x1; x <= x2; x++) {
            b.set(x, S + 1, z1, "minecraft:polished_blackstone_wall");
            b.set(x, S + 1, z2, "minecraft:polished_blackstone_wall");
        }
        for (int z = z1; z <= z2; z++) {
            b.set(x1 + 0, S + 1, z, "minecraft:polished_blackstone_wall");
            b.set(x2, S + 1, z, "minecraft:polished_blackstone_wall");
        }
    }

    private static void arrival(BlueprintBuilder b) {
        // the tram arrives from the west edge along z = 33
        b.fill(2, S + 1, 33, 2, S + 1, 33, Pal.AIR);
        for (int x = 3; x <= 17; x++) {
            b.set(x, S, 33, "minecraft:polished_andesite");
            b.set(x, S + 1, 33, "minecraft:rail[shape=east_west]");
        }
        b.fill(4, S, 28, 16, S, 31, "minecraft:smooth_stone");
        b.fill(4, S + 1, 28, 16, S + 1, 28, Pal.CONCRETE_GREY);
        for (int x : new int[] {5, 10, 15}) {
            b.fill(x, S + 2, 28, x, S + 4, 28, "minecraft:polished_blackstone_wall");
            b.set(x, S + 5, 29, Keys.lantern(true));
        }
        b.fill(4, S + 5, 28, 16, S + 5, 30, "minecraft:smooth_stone_slab[type=bottom]");
        b.set(10, S + 3, 28, Pal.note(62, Dir.SOUTH));
        b.stamp(Props.bench(), 7, S + 1, 29, 0);
        b.interact("cp_arrival", 13, S + 1, 30, Pal.checkpoint());
        b.marker("tram_arrive", 10, S + 1, 31);
        b.marker("tram_rail_start", 3, S + 1, 33);
        b.marker("tram_rail_end", 17, S + 1, 33);
        // path from the stop to the yard
        b.fill(17, S, 28, 24, S, 36, "minecraft:polished_andesite");
    }

    // ------------------------------------------------------------------ dispatcher room

    private static void dispatcher(BlueprintBuilder b) {
        // x 18..40, z 4..18 (interior 19..39 x 5..17); door on the south side at x 28..29
        b.fill(18, S, 4, 40, S, 18, "minecraft:white_concrete");
        b.room(18, S, 4, 40, S + 7, 18, Pal.CONCRETE_GREY);
        b.fill(19, S + 1, 5, 39, S + 6, 17, Pal.AIR);
        b.fill(28, S + 1, 18, 29, S + 3, 18, Pal.AIR);
        for (int x = 22; x <= 36; x += 7) {
            b.fill(x, S + 3, 4, x + 2, S + 5, 4, Pal.PANE);
            b.set(x + 1, S + 6, 10, Keys.lantern(true));
        }
        b.set(29, S + 6, 12, Keys.lantern(true));
        // the timetable: four plates with arrows (A = left, B = right)
        int[] arrows = {33, 32, 33, 32};
        for (int i = 0; i < 4; i++) {
            b.set(22 + i * 2, S + 4, 5, Pal.plate(arrows[i], Dir.SOUTH));
            b.set(22 + i * 2, S + 3, 5, Pal.plate(i + 1, Dir.SOUTH));
        }
        b.set(21, S + 4, 5, Pal.note(63, Dir.SOUTH));
        b.set(31, S + 4, 5, Pal.note(64, Dir.SOUTH));
        // the desk: four switches, the dispatch lever and a lamp strip
        b.fill(23, S + 1, 12, 35, S + 1, 12, "minecraft:spruce_planks");
        for (int i = 0; i < 4; i++) {
            b.interact("switch_" + (i + 1), 25 + i * 3, S + 2, 12, Pal.tramSwitch(Dir.SOUTH));
            b.set(25 + i * 3, S + 3, 12, "minecraft:redstone_lamp[lit=false]");
            b.marker("switch_lamp_" + (i + 1), 25 + i * 3, S + 3, 12);
        }
        b.interact("dispatch", 37, S + 2, 12, Pal.lever(Dir.SOUTH));
        b.set(37, S + 1, 12, Pal.CONCRETE_DARK);
        b.interact("cp_dispatcher", 30, S + 1, 8, Pal.checkpoint());
        b.marker("dispatcher", 29, S + 1, 14);
        b.marker("desk", 30, S + 1, 14);
    }

    // ------------------------------------------------------------------ rail yard

    private static void yard(BlueprintBuilder b) {
        b.fill(18, S, 20, 72, S, 43, "minecraft:gravel");
        for (int x = 18; x <= 72; x++) {
            for (int z = 20; z <= 43; z++) {
                if ((Noise.hash(6, x, z) & 7) == 0) {
                    b.set(x, S, z, Pal.COBBLE);
                }
            }
        }
        // main line along z = 30 from the start node to the goal
        for (int x = 20; x <= 60; x++) {
            b.set(x, S + 1, 30, "minecraft:rail[shape=east_west]");
        }
        // stubs with buffer stops
        int[][] stubs = {{26, 31, 38}, {34, 29, 22}, {42, 31, 38}, {50, 29, 22}};
        for (int[] st : stubs) {
            int x = st[0];
            int from = st[1];
            int to = st[2];
            int step = to > from ? 1 : -1;
            for (int z = from; z != to + step; z += step) {
                b.set(x, S + 1, z, "minecraft:rail[shape=north_south]");
            }
            b.set(x, S + 1, to + step, "minecraft:red_concrete");
            b.set(x, S + 2, to + step, "minecraft:red_concrete");
        }
        // junction machines next to each switch
        for (int i = 0; i < 4; i++) {
            int x = NODES[i][0];
            b.set(x + 1, S + 1, 32, "minecraft:iron_block");
            b.set(x + 1, S + 2, 32, Pal.light(9));
        }
        // network nodes as markers (for the hand car and the effects)
        String[] names = {"s0", "s1", "s2", "s3", "d0", "d1", "d2", "d3", "goal"};
        for (int i = 0; i < NODES.length; i++) {
            b.marker("net_" + names[i], NODES[i][0], S + 1, NODES[i][1]);
        }
        b.marker("net_start", 20, S + 1, 30);
        b.marker("yard", 40, S + 1, 36);
        b.interact("cp_yard", 22, S + 1, 36, Pal.checkpoint());
        for (int x = 24; x <= 70; x += 8) {
            b.set(x, S + 4, 20, "minecraft:polished_blackstone_wall");
            b.set(x, S + 5, 20, "minecraft:polished_blackstone_wall");
            b.set(x, S + 6, 20, Keys.lantern(false));
        }
    }

    private static void ticketOffice(BlueprintBuilder b) {
        // x 62..70, z 24..36; door faces west towards the goal node
        b.fill(62, S, 24, 70, S, 36, Pal.POL_ANDESITE);
        b.room(62, S, 24, 70, S + 5, 36, Pal.PLASTER_PEEL);
        b.fill(63, S + 1, 25, 69, S + 4, 35, Pal.AIR);
        b.fill(62, S + 1, 29, 62, S + 3, 31, Pal.AIR);
        b.fill(65, S + 1, 27, 65, S + 1, 33, "minecraft:spruce_planks");
        b.fill(65, S + 2, 27, 65, S + 2, 33, "minecraft:spruce_slab[type=bottom]");
        b.set(68, S + 4, 30, Pal.light(12));
        b.set(63, S + 2, 25, Pal.note(65, Dir.EAST));
        b.interact("ticket_window", 64, S + 1, 30, Pal.ticketMachine(Dir.WEST));
        b.marker("ticket_office", 61, S + 1, 30);
        b.interact("cp_office", 60, S + 1, 33, Pal.checkpoint());
    }

    // ------------------------------------------------------------------ the Conductor's arena

    private static void arena(BlueprintBuilder b) {
        // the arena is a walled yard (x 17..73, z 44..87); its only way in is the boss door
        b.fill(17, S, 44, 73, S, 87, Pal.CONCRETE_DARK);
        b.walls(17, S + 1, 44, 73, S + 5, 87, Pal.CONCRETE_GREY);
        b.gate("boss_door", 44, S + 1, 44, 46, S + 4, 44, Pal.COLLECTOR_DOOR, true);
        // podium of the Conductor in the north-middle of the arena
        b.fill(40, S + 1, 49, 50, S + 1, 53, Pal.CONCRETE_DARK);
        b.fill(41, S + 2, 50, 49, S + 2, 52, "minecraft:red_carpet");
        b.marker("podium", 45, S + 2, 51);
        b.marker("boss_spawn", 45, S + 2, 51);
        // tram lanes: track bed 3 wide, rail in the middle, lights on both ends
        for (int i = 0; i < 3; i++) {
            int z = LANE_Z[i];
            b.fill(18, S, z - 1, 72, S, z + 1, "minecraft:gravel");
            for (int x = 18; x <= 72; x++) {
                b.set(x, S + 1, z, "minecraft:rail[shape=east_west]");
            }
            b.marker("lane_" + (i + 1) + "_a", 18, S + 1, z);
            b.marker("lane_" + (i + 1) + "_b", 72, S + 1, z);
            // barred tunnel mouths on both sides: the trams come out of them once the fight starts
            b.gate("lane_gate_" + (i + 1) + "_a", 17, S + 1, z - 1, 17, S + 3, z + 1, "minecraft:iron_bars", true);
            b.gate("lane_gate_" + (i + 1) + "_b", 73, S + 1, z - 1, 73, S + 3, z + 1, "minecraft:iron_bars", true);
            b.set(18, S + 4, z, "minecraft:redstone_lamp[lit=false]");
            b.set(72, S + 4, z, "minecraft:redstone_lamp[lit=false]");
            b.marker("lane_lamp_" + (i + 1) + "_a", 18, S + 4, z);
            b.marker("lane_lamp_" + (i + 1) + "_b", 72, S + 4, z);
        }
        // composter stations (one per player in a full party): west, east and south-middle
        b.interact("composter_1", 21, S + 1, 62, Pal.validator(Dir.EAST, "cross"));
        b.interact("composter_2", 69, S + 1, 62, Pal.validator(Dir.WEST, "circle"));
        b.interact("composter_3", 45, S + 1, 80, Pal.validator(Dir.NORTH, "triangle"));
        // validators for the "КВИТОК!" mechanic at the safe edges
        b.interact("validator_a", 21, S + 1, 70, Pal.validator(Dir.EAST, "none"));
        b.interact("validator_b", 69, S + 1, 70, Pal.validator(Dir.WEST, "none"));
        b.interact("validator_c", 21, S + 1, 54, Pal.validator(Dir.EAST, "none"));
        b.interact("validator_d", 69, S + 1, 54, Pal.validator(Dir.WEST, "none"));
        for (int x = 24; x <= 66; x += 14) {
            for (int z : new int[] {49, 62, 70, 84}) {
                b.set(x, S + 6, z, Keys.lantern(false));
                b.fill(x, S + 1, z, x, S + 5, z, "minecraft:polished_blackstone_wall");
            }
        }
        b.interact("cp_arena", 45, S + 1, 47, Pal.checkpoint());
        b.marker("arena_center", 45, S + 1, 62);
        b.marker("arena_entry", 45, S + 1, 47);
        b.region("arena", 18, S, 47, 72, S + 12, 86);
    }
}
