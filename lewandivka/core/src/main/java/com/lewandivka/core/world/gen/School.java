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
 * The school: two storeys of classrooms on both sides of a corridor, an entrance hall with the stairs in the middle, a library
 * among the classrooms. 40 x 16 blocks; every room is a grid of {@link RoomKit} (desks with chairs, a blackboard, shelves).
 */
public final class School {

    private School() {
    }

    private static final Map<String, Blueprint> CACHE = new ConcurrentHashMap<>();

    static final int W = 40;
    static final int D = 16;
    static final int FRONT = 3;
    /** The first step of the flight in the hall (its cell in front, the one to the west of it, stays free). */
    static final int STAIRS_X = 18;

    /** A classroom of 7 x 5 for the west end of a wing: the blackboard on the west wall, pairs of desks and chairs, the door in the corner. */
    static final String[] CLASSROOM = {
            "P  .  .  T  c< T  c<",
            "N> .  l  T  c< T  c<",
            "N> T  .  T  c< T  c<",
            "N> .  l  T  c< T  c<",
            "H> .  .  T  c< T  c<",
    };

    static final String[] LIBRARY = {
            "H> .  .  .  .  .  H<",
            "H> .  T  T  T  .  H<",
            "H> .  c^ c^ c^ .  H<",
            "H> .  .  l  .  .  H<",
            "P  .  .  .  .  .  P",
    };

    /** The entrance hall of 6 x 14: the door to the street at the top, the stairs at the bottom. */
    static final String[] HALL = {
            "P  .  .  .  .  P",
            ".  .  .  .  .  .",
            ".  .  l  .  .  .",
            ".  .  .  .  .  .",
            ".  .  .  .  .  .",
            ".  .  .  .  .  .",
            ".  .  .  .  .  .",
            ".  .  .  .  .  .",
            ".  .  .  .  .  .",
            ".  .  l  .  .  .",
            ".  .  .  .  .  .",
            ".  .  .  .  .  .",
            ".  .  l  .  .  .",
            ".  %  %  %  %  .",
    };

    // ------------------------------------------------------------------ assembling

    private static String[] tokens(String row) {
        return row.trim().split("\\s+");
    }

    private static String flipDirection(String token) {
        if (token.length() < 2) {
            return token;
        }
        char d = token.charAt(1);
        char flipped = d == '<' ? '>' : d == '>' ? '<' : d == '^' ? 'v' : d == 'v' ? '^' : d;
        return token.charAt(0) + "" + flipped;
    }

    /** The row seen in a mirror: the tokens in the other order and the arrows turned round. */
    private static String[] mirror(String[] row) {
        String[] out = new String[row.length];
        for (int i = 0; i < row.length; i++) {
            String t = row[row.length - 1 - i];
            out[i] = t.length() > 1 && (t.charAt(1) == '<' || t.charAt(1) == '>') ? flipDirection(t) : t;
        }
        return out;
    }

    /** The room turned over: the last row first, the arrows along the second axis turned round. */
    private static String[][] flip(String[] room) {
        String[][] out = new String[room.length][];
        for (int r = 0; r < room.length; r++) {
            String[] row = tokens(room[room.length - 1 - r]);
            String[] t = new String[row.length];
            for (int c = 0; c < row.length; c++) {
                t[c] = row[c].length() > 1 && (row[c].charAt(1) == '^' || row[c].charAt(1) == 'v') ? flipDirection(row[c]) : row[c];
            }
            out[r] = t;
        }
        return out;
    }

    private static String[] concat(String[]... parts) {
        int n = 0;
        for (String[] p : parts) {
            n += p.length;
        }
        String[] out = new String[n];
        int i = 0;
        for (String[] p : parts) {
            System.arraycopy(p, 0, out, i, p.length);
            i += p.length;
        }
        return out;
    }

    /** The 38 x 14 grid of one storey. */
    static String[] floor() {
        String[][] southA = flip(CLASSROOM);
        String[][] southB = flip(CLASSROOM);
        String[][] wing = new String[14][];
        for (int r = 0; r < 14; r++) {
            if (r < 5) {
                wing[r] = concat(tokens(CLASSROOM[r]), new String[] {"#"}, tokens(LIBRARY[r]));
            } else if (r == 5 || r == 8) {
                wing[r] = new String[15];
                java.util.Arrays.fill(wing[r], "#");
                wing[r][1] = "d";
                wing[r][9] = "d";
            } else if (r == 6 || r == 7) {
                wing[r] = new String[15];
                java.util.Arrays.fill(wing[r], ".");
                wing[r][r == 6 ? 3 : 11] = "l";
                wing[r][r == 6 ? 11 : 3] = "l";
            } else {
                wing[r] = concat(southA[r - 9], new String[] {"#"}, southB[r - 9]);
            }
        }
        String[] rows = new String[14];
        for (int r = 0; r < 14; r++) {
            String gate = r == 6 || r == 7 ? "d" : "#";
            String[] right = mirror(wing[r]);
            rows[r] = String.join(" ", concat(wing[r], new String[] {gate}, tokens(HALL[r]), new String[] {gate}, right));
        }
        return rows;
    }

    static String[][] floors() {
        return new String[][] {floor(), floor()};
    }

    // ------------------------------------------------------------------ the building

    public static Blueprint build(int turns, long seed) {
        return CACHE.computeIfAbsent("school_" + turns + "_" + seed, k -> buildSchool(turns, seed));
    }

    private static Blueprint buildSchool(int turns, long seed) {
        final int wallTop = 8;
        final int sx = W;
        final int sz = D + FRONT + 1;
        boolean odd = (turns & 1) == 1;
        BlueprintBuilder b = new BlueprintBuilder("school", odd ? sz : sx, wallTop + 6, odd ? sx : sz);
        b.orient(turns, sx, sz);
        b.at(0, 0, FRONT);
        String wood = Furnish.WOODS[0];
        for (int x = 0; x < W; x++) {
            for (int z = 0; z < D; z++) {
                b.set(x, 0, z, Pal.BRICKS);
            }
        }
        for (int y = 1; y <= wallTop; y++) {
            for (int x = 0; x < W; x++) {
                for (int z = 0; z < D; z++) {
                    boolean outer = x == 0 || x == W - 1 || z == 0 || z == D - 1;
                    b.set(x, y, z, outer ? wall(seed, x, y, z) : y % 4 == 0 ? Furnish.planks(wood) : Keys.AIR);
                }
            }
        }
        b.fill(0, wallTop, 0, W - 1, wallTop, D - 1, Pal.CONCRETE_DARK);
        for (int x = 0; x < W; x++) {
            b.set(x, wallTop + 1, 0, "minecraft:stone_brick_slab[type=bottom]");
            b.set(x, wallTop + 1, D - 1, "minecraft:stone_brick_slab[type=bottom]");
        }
        for (int z = 1; z < D - 1; z++) {
            b.set(0, wallTop + 1, z, "minecraft:stone_brick_slab[type=bottom]");
            b.set(W - 1, wallTop + 1, z, "minecraft:stone_brick_slab[type=bottom]");
        }
        // the rooms and the stairs of the hall
        String[][] floors = floors();
        long h = Noise.hash(seed, 5, 5);
        Furnish.Style style = new Furnish.Style(wood, Furnish.planks(wood), "minecraft:white_terracotta", Furnish.BEDS[0], Furnish.SOFAS[0], Furnish.RUGS[0], true, 0);
        for (int storey = 0; storey < 2; storey++) {
            RoomKit.place(b, new Plot(1, 1, Dir.EAST, Dir.SOUTH), 4 * storey, RoomKit.parse(floors[storey]), style, h + storey);
        }
        HouseKit.stairs(b, STAIRS_X, D - 2, style);
        // windows: three panes between white piers for every room, on both long sides
        for (int[] room : new int[][] {{3, 5}, {11, 13}, {26, 28}, {34, 36}}) {
            for (int f : new int[] {0, 4}) {
                for (int z : new int[] {0, D - 1}) {
                    window(b, room[0], room[1], f, z, z == 0 ? -1 : 1);
                }
            }
        }
        // the entrance in the middle, with a canopy; a window above it
        int cx = W / 2 - 1;
        b.fill(cx - 1, 1, 0, cx + 2, 3, 0, Pal.CONCRETE_WHITE);
        b.fill(cx, 1, 0, cx + 1, 2, 0, Keys.AIR);
        b.set(cx, 1, 0, Keys.door("minecraft:dark_oak_door", Dir.NORTH, false, false, false));
        b.set(cx, 2, 0, Keys.door("minecraft:dark_oak_door", Dir.NORTH, true, false, false));
        b.set(cx + 1, 1, 0, Keys.door("minecraft:dark_oak_door", Dir.NORTH, false, false, true));
        b.set(cx + 1, 2, 0, Keys.door("minecraft:dark_oak_door", Dir.NORTH, true, false, true));
        b.fill(cx - 1, 4, -1, cx + 2, 4, -3, "minecraft:stone_brick_slab[type=bottom]");
        for (int yy = 1; yy <= 3; yy++) {
            b.set(cx - 1, yy, -3, "minecraft:stone_brick_wall");
            b.set(cx + 2, yy, -3, "minecraft:stone_brick_wall");
        }
        b.set(cx, 3, -1, Keys.lantern(true));
        b.set(cx + 1, 3, -1, Keys.lantern(true));
        b.fill(cx, 0, -1, cx + 1, 0, -1, Pal.BRICKS);
        b.fill(cx - 1, 0, -3, cx + 2, 0, -2, "minecraft:stone_brick_slab[type=bottom]");
        b.fill(cx, 6, 0, cx + 1, 7, 0, Pal.WINDOW);
        b.marker("entrance", cx, 1, 1);
        b.marker("classroom", 3, 1, 3);
        b.marker("library", 12, 1, 4);
        b.marker("upper", 3, 5, 3);
        b.region("body", 0, 0, 0, W - 1, wallTop + 1, D - 1);
        return b.build();
    }

    private static String wall(long seed, int x, int y, int z) {
        long h = Noise.hash(seed, x / 3, y / 2, z / 3);
        double r = (h >>> 11) * (1.0 / (1L << 53));
        if (y <= 1) {
            return Pal.BRICKS;
        }
        if (r < 0.12) {
            return Pal.PLASTER_WHITE;
        }
        if (r < 0.2) {
            return Pal.PLASTER_PEEL;
        }
        return Pal.PLASTER_YELLOW;
    }

    private static void window(BlueprintBuilder b, int x1, int x2, int f, int z, int out) {
        for (int dy = 2; dy <= 3; dy++) {
            b.set(x1 - 1, f + dy, z, Pal.CONCRETE_WHITE);
            b.set(x2 + 1, f + dy, z, Pal.CONCRETE_WHITE);
            for (int x = x1; x <= x2; x++) {
                b.set(x, f + dy, z, Pal.WINDOW);
            }
        }
        // the frame: a sill under the glass and, under the first floor, a lintel over it (the top of the upper wall is the edge of the roof)
        for (int x = x1 - 1; x <= x2 + 1; x++) {
            b.set(x, f + 1, z, Pal.CONCRETE_WHITE);
            if (f == 0) {
                b.set(x, f + 4, z, Pal.CONCRETE_WHITE);
            }
        }
        for (int x = x1; x <= x2; x++) {
            b.set(x, f + 1, z + out, "minecraft:stone_brick_slab[type=top]");
        }
    }
}
