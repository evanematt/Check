package com.lewandivka.core.world.gen;

import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.structure.BlueprintBuilder;
import com.lewandivka.core.structure.Dir;
import com.lewandivka.core.structure.Keys;
import com.lewandivka.core.world.Noise;
import com.lewandivka.core.world.Pal;

/**
 * The Dry Lake Aquapark. A faded blue-and-yellow water park at the bottom of a dried-up crater:
 * the entrance looks like an ordinary wall (the cats show where it is), inside are locker rooms,
 * maintenance corridors, three empty pools, slides, the pump room and the square arena of Lady Vortex
 * with four quarters that each carry a colour AND a symbol.
 *
 * <p>Quarters (arena centre 42,52): NW red + cross, NE blue + circle, SW yellow + triangle,
 * SE green + square.</p>
 */
public final class Aquapark {

    private Aquapark() {
    }

    public static final int SX = 84;
    public static final int SY = 26;
    public static final int SZ = 76;
    public static final int S = 8;
    public static final int ORIGIN_X = ChromaPlan.LAKE_X - 42;
    public static final int ORIGIN_Y = ChromaPlan.LAKE_Y - S;
    public static final int ORIGIN_Z = ChromaPlan.LAKE_Z - 40;

    public static final int ARENA_CX = 42;
    public static final int ARENA_CZ = 52;

    private static Blueprint cache;

    public static synchronized Blueprint blueprint() {
        if (cache == null) {
            cache = build();
        }
        return cache;
    }

    private static Blueprint build() {
        BlueprintBuilder b = new BlueprintBuilder("aquapark", SX, SY, SZ);
        shell(b);
        lobby(b);
        lockerRooms(b);
        pumpRoom(b);
        pools(b);
        arena(b);
        b.region("body", 2, S, 6, SX - 3, S + 9, SZ - 2);
        return b.build();
    }

    // ------------------------------------------------------------------ shell and facade

    private static void shell(BlueprintBuilder b) {
        // plaza in front (z 0..5), the building z 6..72
        b.fill(0, S, 0, SX - 1, S, 5, "minecraft:red_sandstone");
        b.fill(2, S, 6, SX - 3, S, 72, Pal.POL_ANDESITE);
        b.room(2, S, 6, SX - 3, S + 9, 72, "minecraft:light_blue_concrete");
        b.fill(3, S + 1, 7, SX - 4, S + 8, 71, Pal.AIR);
        // faded stripes on the facade, wave pattern and the sign
        for (int x = 2; x < SX - 2; x++) {
            b.set(x, S + 7, 6, (x / 4) % 2 == 0 ? "minecraft:yellow_concrete" : "minecraft:light_blue_concrete");
            b.set(x, S + 8, 6, (x / 4) % 2 == 0 ? "minecraft:cyan_concrete" : "minecraft:white_concrete");
            long h = Noise.hash(2, x, 1);
            if ((h & 3) == 0) {
                b.set(x, S + 4, 6, "minecraft:white_concrete");
            }
        }
        for (int x = 8; x < SX - 8; x += 8) {
            b.fill(x, S + 3, 6, x + 2, S + 5, 6, Pal.PANE);
        }
        b.fill(30, S + 4, 6, 53, S + 6, 6, "minecraft:white_concrete");
        b.set(41, S + 5, 5, Pal.note(50, Dir.NORTH));
        b.set(42, S + 5, 5, Pal.note(51, Dir.NORTH));
        // the hidden entrance: looks like the rest of the facade (light blue concrete) until the cats show the way
        b.gate("gate_hidden", 40, S + 1, 6, 42, S + 3, 6, "minecraft:light_blue_concrete", true);
        b.region("hidden_area", 38, S, 0, 44, S + 4, 5);
        b.marker("area", 41, S + 1, 2);
        b.marker("hint_spot", 41, S + 1, 4);
        b.marker("cat_hint_a", 39, S + 1, 4);
        b.marker("cat_hint_b", 43, S + 1, 4);
        b.marker("lake_entry", 41, S + 1, 1);
        // roof lights and the ceiling grid
        for (int x = 8; x < SX - 6; x += 10) {
            for (int z = 12; z < 70; z += 10) {
                b.set(x, S + 8, z, Keys.lantern(true));
            }
        }
    }

    // ------------------------------------------------------------------ lobby and lockers

    private static void lobby(BlueprintBuilder b) {
        // lobby x 34..50, z 7..16; the arena corridor continues south at x 41..43
        b.fill(34, S + 1, 7, 50, S + 6, 16, Pal.AIR);
        b.fill(34, S, 7, 50, S, 16, "minecraft:white_concrete");
        b.fill(36, S + 1, 9, 48, S + 1, 9, "minecraft:spruce_planks");
        b.fill(36, S + 2, 9, 48, S + 2, 9, "minecraft:spruce_slab[type=bottom]");
        for (int x = 36; x <= 48; x += 4) {
            b.set(x, S + 3, 9, "minecraft:potted_fern");
        }
        // turnstiles
        for (int x : new int[] {38, 40, 44, 46}) {
            b.set(x, S + 1, 12, "minecraft:iron_bars");
            b.set(x, S + 2, 12, "minecraft:iron_bars");
        }
        b.set(42, S + 2, 8, Pal.note(52, Dir.SOUTH));
        // door cut from the lobby to the arena corridor
        b.fill(41, S + 1, 17, 43, S + 4, 36, Pal.AIR);
        b.fill(40, S + 1, 17, 40, S + 4, 36, "minecraft:white_concrete");
        b.fill(44, S + 1, 17, 44, S + 4, 36, "minecraft:white_concrete");
        b.fill(41, S, 17, 43, S, 36, "minecraft:white_concrete");
        b.gate("boss_door", 41, S + 1, 18, 43, S + 3, 18, Pal.GUARD_DOOR, true);
        // side doors to the locker rooms
        b.fill(34, S + 1, 11, 34, S + 3, 12, Pal.AIR);
        b.fill(50, S + 1, 11, 50, S + 3, 12, Pal.AIR);
        b.interact("cp_lobby", 42, S + 1, 11, Pal.checkpoint());
        b.marker("lobby", 42, S + 1, 13);
    }

    private static void lockerRooms(BlueprintBuilder b) {
        // locker rooms share their walls with the lobby (x 34 / 50) and with the maintenance corridors (x 9 / 75)
        for (int side = 0; side < 2; side++) {
            int x1 = side == 0 ? 10 : 51;
            int x2 = side == 0 ? 33 : 74;
            b.fill(x1, S, 8, x2, S, 24, "minecraft:white_concrete");
            b.fill(x1, S + 1, 8, x2, S + 6, 24, Pal.AIR);
            b.walls(x1 - 1, S + 1, 7, x2 + 1, S + 6, 25, Pal.CONCRETE_GREY);
            // rows of lockers (iron bars over barrels) with benches in between
            for (int x = x1 + 3; x <= x2 - 3; x += 6) {
                for (int z = 10; z <= 20; z += 2) {
                    b.set(x, S + 1, z, Keys.barrel(Dir.NORTH));
                    b.set(x, S + 2, z, "minecraft:iron_bars");
                }
                b.fill(x + 2, S + 1, 10, x + 2, S + 1, 20, "minecraft:spruce_slab[type=bottom]");
            }
            // doorways: to the lobby and to the maintenance corridor
            int lobbyWall = side == 0 ? x2 + 1 : x1 - 1;
            int outerWall = side == 0 ? x1 - 1 : x2 + 1;
            b.fill(lobbyWall, S + 1, 11, lobbyWall, S + 3, 12, Pal.AIR);
            b.fill(outerWall, S + 1, 15, outerWall, S + 3, 16, Pal.AIR);
            b.set(side == 0 ? x1 + 1 : x2 - 1, S + 5, 16, Pal.light(10));
        }
        // maintenance corridors running north-south on both outer sides
        for (int side = 0; side < 2; side++) {
            int x1 = side == 0 ? 4 : 76;
            int x2 = side == 0 ? 8 : 80;
            b.fill(x1, S, 8, x2, S, 70, Pal.CONCRETE_DARK);
            b.fill(x1, S + 1, 8, x2, S + 4, 70, Pal.AIR);
            b.walls(x1 - 1, S + 1, 7, x2 + 1, S + 5, 71, Pal.CONCRETE_GREY);
            b.fill(x1, S + 5, 8, x2, S + 5, 70, Pal.CONCRETE_DARK);
            for (int z = 12; z <= 68; z += 8) {
                b.set((x1 + x2) / 2, S + 4, z, Keys.lantern(true));
            }
            for (int z = 9; z <= 69; z += 4) {
                b.set(side == 0 ? x1 : x2, S + 3, z, (Noise.hash(4, x1, z) & 3) == 0 ? Pal.CHAIN : "minecraft:copper_block");
            }
        }
        // the lockers' side doors lead into both corridors; the corridors meet around the arena through the south
        b.fill(4, S + 1, 69, 80, S + 3, 70, Pal.AIR);
        b.fill(4, S, 69, 80, S, 70, Pal.CONCRETE_DARK);
        b.marker("corridor_west", 6, S + 1, 40);
        b.marker("corridor_east", 78, S + 1, 40);
    }

    // ------------------------------------------------------------------ pump room

    private static void pumpRoom(BlueprintBuilder b) {
        // x 62..75, z 26..42, entered from the east maintenance corridor through the shared wall at x = 75
        b.fill(62, S, 26, 75, S, 42, Pal.CONCRETE_DARK);
        b.fill(63, S + 1, 27, 74, S + 5, 41, Pal.AIR);
        b.walls(62, S + 1, 26, 75, S + 5, 42, Pal.CONCRETE_GREY);
        b.fill(62, S + 6, 26, 75, S + 6, 42, Pal.CONCRETE_DARK);
        b.fill(75, S + 1, 33, 75, S + 3, 34, Pal.AIR);
        for (int i = 0; i < 3; i++) {
            int z = 29 + i * 5;
            b.interact("pump_" + (i + 1), 63, S + 2, z, Pal.pump(Dir.EAST));
            b.set(63, S + 1, z, Pal.CONCRETE_DARK);
            b.fill(64, S + 1, z, 66, S + 1, z, "minecraft:copper_block");
            b.set(66, S + 2, z, Keys.lantern(false));
        }
        // rusty tanks
        b.fill(69, S + 1, 28, 71, S + 4, 30, Pal.RUST_CUT);
        b.fill(69, S + 1, 37, 71, S + 4, 39, Pal.RUST_CUT);
        b.set(66, S + 5, 33, Keys.lantern(true));
        b.interact("cp_pumps", 73, S + 1, 33, Pal.checkpoint());
        b.marker("pump_room", 68, S + 1, 33);
        b.set(68, S + 3, 41, Pal.note(53, Dir.NORTH));
    }

    // ------------------------------------------------------------------ pools and slides

    private static void pools(BlueprintBuilder b) {
        // three dry pools carved 3 deep; the water is added by the pumps
        int[][] pools = {{11, 28, 24, 38}, {11, 42, 24, 64}, {60, 44, 73, 64}};
        for (int i = 0; i < 3; i++) {
            int[] p = pools[i];
            b.fill(p[0] - 1, S, p[1] - 1, p[2] + 1, S, p[3] + 1, "minecraft:white_concrete");
            b.fill(p[0], S - 3, p[1], p[2], S - 1, p[3], Pal.AIR);
            b.fill(p[0], S - 4, p[1], p[2], S - 4, p[3], Pal.CONCRETE_DARK);
            b.fill(p[0], S - 3, p[1], p[2], S - 3, p[3], "minecraft:light_blue_concrete");
            b.fill(p[0], S, p[1], p[2], S, p[3], Pal.AIR);
            // steps down on one side
            for (int k = 0; k < 3; k++) {
                b.set(p[0] + k, S - 1 - k, p[1] + 1, Keys.stairs("minecraft:smooth_quartz_stairs", Dir.EAST, false));
                b.fill(p[0] + k, S - 3, p[1] + 1, p[0] + k, S - 2 - k, p[1] + 1, "minecraft:smooth_quartz");
            }
            b.region("fill_" + (i + 1), p[0], S - 2, p[1], p[2], S - 1, p[3]);
            b.marker("pool_" + (i + 1), (p[0] + p[2]) / 2, S + 1, p[1] - 1);
        }
        // a slide tower into pool 2: stairs descending from a platform at y = S + 8
        b.fill(7, S + 8, 40, 9, S + 8, 42, "minecraft:spruce_planks");
        for (int y = S + 1; y <= S + 7; y++) {
            b.set(7, y, 40, Pal.LOG_SPRUCE);
            b.set(9, y, 42, Pal.LOG_SPRUCE);
        }
        for (int k = 0; k < 8; k++) {
            b.set(9 + k / 2, S + 7 - k, 43 + k, Keys.stairs("minecraft:smooth_quartz_stairs", Dir.SOUTH, true));
        }
        b.set(5, S + 6, 52, Pal.note(54, Dir.EAST));
        // wave pool decorations: a lifeguard chair
        b.fill(66, S + 1, 41, 66, S + 3, 41, "minecraft:white_concrete");
        b.set(66, S + 4, 41, "minecraft:red_carpet");
    }

    // ------------------------------------------------------------------ the arena of Lady Vortex

    private static void arena(BlueprintBuilder b) {
        int cx = ARENA_CX;
        int cz = ARENA_CZ;
        // square hall 31 x 31 with a high ceiling and a glass roof
        b.fill(cx - 15, S, cz - 15, cx + 15, S, cz + 15, "minecraft:white_concrete");
        b.fill(cx - 15, S + 1, cz - 15, cx + 15, S + 12, cz + 15, Pal.AIR);
        b.walls(cx - 16, S + 1, cz - 16, cx + 16, S + 12, cz + 16, "minecraft:light_blue_concrete");
        b.fill(cx - 16, S + 13, cz - 16, cx + 16, S + 13, cz + 16, Pal.CONCRETE_DARK);
        b.fill(cx - 8, S + 13, cz - 8, cx + 8, S + 13, cz + 8, Pal.PANE);
        // the corridor from the lobby enters at the north wall
        b.fill(41, S + 1, cz - 16, 43, S + 4, cz - 16, Pal.AIR);
        // four quarters: colour AND symbol on the floor
        quarter(b, cx - 15, cz - 15, cx - 1, cz - 1, "red", "cross", Pal.RED_CONCRETE);
        quarter(b, cx + 1, cz - 15, cx + 15, cz - 1, "blue", "circle", Pal.BLUE_CONCRETE);
        quarter(b, cx - 15, cz + 1, cx - 1, cz + 15, "yellow", "triangle", Pal.YELLOW_CONCRETE);
        quarter(b, cx + 1, cz + 1, cx + 15, cz + 15, "green", "square", Pal.GREEN_CONCRETE);
        // neutral cross in the middle
        b.fill(cx, S, cz - 15, cx, S, cz + 15, "minecraft:white_concrete");
        b.fill(cx - 15, S, cz, cx + 15, S, cz, "minecraft:white_concrete");
        // lamps
        for (int x = cx - 12; x <= cx + 12; x += 12) {
            for (int z = cz - 12; z <= cz + 12; z += 12) {
                b.set(x, S + 12, z, "minecraft:sea_lantern");
            }
        }
        b.interact("cp_arena", 42, S + 1, cz - 14, Pal.checkpoint());
        b.marker("arena_center", cx, S + 1, cz);
        b.marker("boss_spawn", cx, S + 1, cz);
        b.marker("arena_entry", 42, S + 1, 30);
        b.region("arena", cx - 15, S, cz - 15, cx + 15, S + 12, cz + 15);
        // drains: one per quarter at the outer corner side
        b.interact("drain_1", cx - 13, S, cz - 13, Pal.drain());
        b.interact("drain_2", cx + 13, S, cz - 13, Pal.drain());
        b.interact("drain_3", cx - 13, S, cz + 13, Pal.drain());
        b.interact("drain_4", cx + 13, S, cz + 13, Pal.drain());
        // water core pickup points near the middle of each quarter edge
        b.marker("core_spawn_1", cx - 8, S + 1, cz - 4);
        b.marker("core_spawn_2", cx + 8, S + 1, cz - 4);
        b.marker("core_spawn_3", cx - 8, S + 1, cz + 4);
        b.marker("core_spawn_4", cx + 8, S + 1, cz + 4);
    }

    private static void quarter(BlueprintBuilder b, int x1, int z1, int x2, int z2, String colorName, String symbol, String floor) {
        b.fill(x1, S, z1, x2, S, z2, floor);
        int cx = (x1 + x2) / 2;
        int cz = (z1 + z2) / 2;
        String w = "minecraft:white_concrete";
        switch (symbol) {
            case "cross" -> {
                for (int i = -4; i <= 4; i++) {
                    b.set(cx + i, S, cz + i, w);
                    b.set(cx + i, S, cz - i, w);
                }
            }
            case "circle" -> b.ring(cx, S, cz, 4.5, w);
            case "triangle" -> {
                for (int i = 0; i <= 8; i++) {
                    b.set(cx - i / 2, S, cz - 4 + i, w);
                    b.set(cx + i / 2, S, cz - 4 + i, w);
                }
                for (int x = cx - 4; x <= cx + 4; x++) {
                    b.set(x, S, cz + 4, w);
                }
            }
            default -> {
                for (int i = -4; i <= 4; i++) {
                    b.set(cx + i, S, cz - 4, w);
                    b.set(cx + i, S, cz + 4, w);
                    b.set(cx - 4, S, cz + i, w);
                    b.set(cx + 4, S, cz + i, w);
                }
            }
        }
        // the zone region used when the quarter floods (water fills y = S + 1 .. S + 2)
        b.region("zone_" + colorName + "_" + symbol, x1, S + 1, z1, x2, S + 2, z2);
        // corner posts with the same symbol colour so each quarter can be read from the doorway
        b.fill(x1, S + 1, z1, x1, S + 4, z1, floor);
        b.fill(x2, S + 1, z2, x2, S + 4, z2, floor);
    }
}
