package com.lewandivka.core.world.gen;

import com.lewandivka.core.structure.BlueprintBuilder;
import com.lewandivka.core.structure.Dir;
import com.lewandivka.core.structure.Keys;
import com.lewandivka.core.world.Noise;

import java.util.ArrayList;
import java.util.List;

/**
 * Furnished rooms, written as small text grids: a flat of five by five blocks, the floor of a house, the group room of a
 * kindergarten. A grid is a list of rows, every row a list of tokens separated by blanks; {@code (c, r)} is the cell in column
 * {@code c} and row {@code r}. A token is a letter and, for things that face somewhere, an arrow ({@code >} is along the first
 * axis of the plot, {@code v} along the second, as in {@link Plot}).
 *
 * <pre>
 *   .   free floor            #   a wall inside                d   a door in such a wall
 *   l   free floor with a lamp under the ceiling                %   left alone (the caller builds the stairs there)
 *   B^  a bed, its foot here and its head in the direction of the arrow (the head cell is marked ~)
 *   T   one cell of a table   c&lt;  a chair (the arrow is where the sitter looks)     y  a toy      Q&gt;  a cubby with coats
 *   W&gt;  a wardrobe            H   a bookcase       S^  a sofa        V&lt;  a television        |   a railing
 *   K^  a kitchen cupboard    X^  a stove          s   a sink        F   a fridge       N&gt;  a blackboard
 *   t&gt;  a toilet              u   a bathtub        P   a plant       R   a rug       L   a lamp on a stand
 *   C&lt;  a chest (the arrow is where its front looks)       O&lt;  a barrel (the arrow is where its opening looks)
 * </pre>
 * The chest, the barrel and the stand of the television hold what the loot table of the kind of place says (see {@link Loot}).
 * They are never left out of a room that nobody lives in.
 */
final class RoomKit {

    private RoomKit() {
    }

    record Token(char kind, char dir) {
    }

    record Grid(int cols, int rows, Token[][] cells) {
        char kind(int c, int r) {
            return c < 0 || r < 0 || c >= cols || r >= rows ? '#' : cells[r][c].kind();
        }
    }

    static Grid parse(String[] rows) {
        Token[][] cells = new Token[rows.length][];
        int cols = -1;
        for (int r = 0; r < rows.length; r++) {
            String[] parts = rows[r].trim().split("\\s+");
            if (cols < 0) {
                cols = parts.length;
            } else if (parts.length != cols) {
                throw new IllegalArgumentException("row " + r + " has " + parts.length + " cells, the first has " + cols + ": " + rows[r]);
            }
            cells[r] = new Token[cols];
            for (int c = 0; c < cols; c++) {
                String t = parts[c];
                cells[r][c] = new Token(t.charAt(0), t.length() > 1 ? t.charAt(1) : ' ');
            }
        }
        return new Grid(cols, rows.length, cells);
    }

    /** The same grid with {@code count} columns put in before column {@code at}, every row continued with its own {@code fill} token. */
    static String[] widen(String[] rows, int at, int count, String[] fill) {
        String[] out = new String[rows.length];
        for (int r = 0; r < rows.length; r++) {
            String[] parts = rows[r].trim().split("\\s+");
            StringBuilder sb = new StringBuilder();
            for (int c = 0; c < parts.length; c++) {
                if (c == at) {
                    sb.append((fill[r] + " ").repeat(count));
                }
                sb.append(parts[c]).append(' ');
            }
            out[r] = sb.toString().trim();
        }
        return out;
    }

    /** The cells a person can stand on (not blocked by furniture), for the checks of the grids. */
    static boolean[][] standable(Grid g) {
        boolean[][] free = new boolean[g.rows()][g.cols()];
        for (int r = 0; r < g.rows(); r++) {
            for (int c = 0; c < g.cols(); c++) {
                char k = g.kind(c, r);
                free[r][c] = k == '.' || k == 'R' || k == 'l' || k == 'd';
            }
        }
        return free;
    }

    // ------------------------------------------------------------------ the flats

    /** Where the door of a flat is and the cell inside it. */
    static final int ENTRY_A = 4;
    static final int ENTRY_B = 3;

    /** Studios of 5 x 5 for the panel blocks: all of them leave the entry cell and a way from it to every free cell. */
    static final String[][] FLATS = {
            // 0: a studio with a kitchen row along the partition
            {"~  P  L  T  H<",
             "B^ .  c> T  c<",
             ".  .  l  .  .",
             "W> .  .  .  .",
             "K^ s  X^ F  C<"},
            // 1: a studio with a closet for the toilet and a kitchen corner
            {"W> P  L  T  H<",
             "~  .  c> T  c<",
             "B^ .  l  .  C<",
             "#  d  #  .  .",
             "t> .  #  K^ s"},
            // 2: the bed under the window, a sofa facing the television
            {"~  B< .  .  P",
             "C> .  l  .  .",
             "S> .  R  .  V<",
             "S> .  R  .  .",
             "K^ s  X^ F  W<"},
            // 3: two small beds, a table at the window
            {"~  ~  .  T  H<",
             "B^ B^ l  T  c<",
             ".  .  .  .  .",
             "W> .  R  R  .",
             "K^ s  X^ F  C<"},
            // 4: the closet again, with an armchair and a television instead of the table
            {"~  B< .  .  P",
             "C> .  l  .  .",
             "S> .  R  .  V<",
             "#  d  #  .  .",
             "t> .  #  K^ s"},
    };

    // ------------------------------------------------------------------ placing

    private static final String[] TOYS = {"minecraft:red_wool", "minecraft:blue_wool", "minecraft:yellow_wool", "minecraft:lime_wool",
            "minecraft:orange_wool", "minecraft:light_blue_wool", "minecraft:pink_wool"};

    /**
     * Builds the room: floor, partitions, doors, furniture and light. The walls around it and the doors into it belong to the
     * caller. {@code floorY} is the floor layer, the room is the three layers above it.
     */
    static void place(BlueprintBuilder b, Plot plot, int floorY, Grid g, Furnish.Style s, long hash) {
        int y = floorY + 1;
        for (int r = 0; r < g.rows(); r++) {
            for (int c = 0; c < g.cols(); c++) {
                if (g.kind(c, r) == '%') {
                    continue;
                }
                int x = plot.x(c, r);
                int z = plot.z(c, r);
                b.set(x, floorY, z, s.floor());
                b.fill(x, y, z, x, y + 2, z, Keys.AIR);
            }
        }
        boolean ruin = s.mood() > 0;
        // what is left standing in a room nobody lives in (the pieces next to each other join, so they have to be known first)
        boolean[][] standing = new boolean[g.rows()][g.cols()];
        for (int r = 0; r < g.rows(); r++) {
            for (int c = 0; c < g.cols(); c++) {
                long h = Noise.hash(hash, c, r);
                standing[r][c] = !(ruin && (h & 3) < 2 && "BTcWHSVKXsFPRLtuyQN".indexOf(g.kind(c, r)) >= 0);
            }
        }
        for (int r = 0; r < g.rows(); r++) {
            for (int c = 0; c < g.cols(); c++) {
                Token t = g.cells[r][c];
                int x = plot.x(c, r);
                int z = plot.z(c, r);
                long h = Noise.hash(hash, c, r);
                if (!standing[r][c]) {
                    continue;
                }
                switch (t.kind()) {
                    case '#' -> b.fill(x, y, z, x, y + 2, z, s.wall());
                    case 'd' -> door(b, plot, g, c, r, y, s);
                    case 'B' -> Furnish.bed(b, x, y, z, plot.dir(t.dir()), s);
                    case 'T' -> {
                        String shape = Decor.tableShape(joins(g, plot, standing, c, r, Dir.NORTH, 'T', ' '), joins(g, plot, standing, c, r, Dir.EAST, 'T', ' '),
                                joins(g, plot, standing, c, r, Dir.SOUTH, 'T', ' '), joins(g, plot, standing, c, r, Dir.WEST, 'T', ' '));
                        Furnish.table(b, x, y, z, s, shape);
                        if ((h >>> 8) % 5 == 0) {
                            Furnish.tableSetting(b, x, y + 1, z, 1 + (int) ((h >>> 12) % 3), h);
                        }
                    }
                    case 'c' -> Furnish.chair(b, x, y, z, plot.dir(t.dir()), s);
                    case 'W' -> Furnish.wardrobe(b, x, y, z, plot.dir(t.dir()), s);
                    case 'H' -> Furnish.shelf(b, x, y, z, t.dir() == ' ' ? plot.dir('^') : plot.dir(t.dir()), s, h >>> 6);
                    case 'S' -> {
                        Dir front = plot.dir(t.dir());
                        Furnish.sofa(b, x, y, z, front, s, Decor.couchShape(joins(g, plot, standing, c, r, front.left(), 'S', t.dir()),
                                joins(g, plot, standing, c, r, front.right(), 'S', t.dir())));
                    }
                    case 'V' -> Furnish.tv(b, x, y, z, plot.dir(t.dir()), s);
                    case 'C' -> Furnish.chest(b, x, y, z, plot.dir(t.dir()), s);
                    case 'O' -> Furnish.barrel(b, x, y, z, plot.dir(t.dir()), s);
                    case 'K' -> Furnish.counter(b, x, y, z, plot.dir(t.dir()), s, h >>> 4);
                    case 'X' -> Furnish.stove(b, x, y, z, plot.dir(t.dir()));
                    case 's' -> Furnish.sink(b, x, y, z);
                    case 'F' -> Furnish.fridge(b, x, y, z);
                    case 'P' -> Furnish.plant(b, x, y, z, h >>> 5);
                    case 'R' -> Furnish.rug(b, x, y, z, s);
                    case 'L' -> Furnish.floorLamp(b, x, y, z, s);
                    case 't' -> Furnish.toilet(b, x, y, z, plot.dir(t.dir()));
                    case 'u' -> Furnish.tub(b, x, y, z);
                    case 'y' -> Furnish.toy(b, x, y, z, TOYS[(int) Math.floorMod(h >>> 9, (long) TOYS.length)]);
                    case 'Q' -> {
                        b.set(x, y, z, Keys.barrel(plot.dir(t.dir())));
                        b.set(x, y + 1, z, TOYS[(int) Math.floorMod(h >>> 11, (long) TOYS.length)]);
                    }
                    case '|' -> b.set(x, y, z, s.fence());
                    case 'N' -> {
                        // a blackboard on the wall: a chalk tray and the green board over it
                        b.set(x, y, z, Keys.slab("minecraft:spruce_slab", true));
                        b.set(x, y + 1, z, "minecraft:green_concrete");
                    }
                    default -> {
                        // '.', 'l', '%', '~' (the head of a bed, placed with its foot): nothing here
                    }
                }
            }
        }
        // light under the ceiling, or the dust of a room nobody lives in
        if (s.lit()) {
            for (int[] lamp : lamps(g)) {
                Furnish.ceilingLamp(b, plot.x(lamp[0], lamp[1]), floorY + 3, plot.z(lamp[0], lamp[1]));
            }
        }
        if (ruin) {
            Furnish.cobweb(b, plot.x(0, 0), floorY + 3, plot.z(0, 0));
            Furnish.cobweb(b, plot.x(g.cols() - 1, g.rows() - 1), floorY + 3, plot.z(g.cols() - 1, g.rows() - 1));
        }
    }

    /**
     * Whether the cell next to (c, r) in the direction {@code d} of the building is a piece of the same kind that is really there
     * (and, for a piece that faces somewhere, faces the same way when {@code facing} is an arrow): then the two join.
     */
    private static boolean joins(Grid g, Plot plot, boolean[][] standing, int c, int r, Dir d, char kind, char facing) {
        char arrow = plot.arrow(d);
        int c2 = c + Plot.da(arrow);
        int r2 = r + Plot.db(arrow);
        if (g.kind(c2, r2) != kind || !standing[r2][c2]) {
            return false;
        }
        return facing == ' ' || g.cells[r2][c2].dir() == facing;
    }

    /** The cells under which a lamp hangs: the ones marked {@code l}. */
    static List<int[]> lamps(Grid g) {
        List<int[]> out = new ArrayList<>();
        for (int r = 0; r < g.rows(); r++) {
            for (int c = 0; c < g.cols(); c++) {
                if (g.kind(c, r) == 'l') {
                    out.add(new int[] {c, r});
                }
            }
        }
        return out;
    }

    /** A door in a partition: it turns its face to the side where there is more room. */
    private static void door(BlueprintBuilder b, Plot plot, Grid g, int c, int r, int y, Furnish.Style s) {
        boolean wallsAlongA = g.kind(c - 1, r) == '#' && g.kind(c + 1, r) == '#';
        char face;
        if (wallsAlongA) {
            face = g.kind(c, r - 1) != '#' ? '^' : 'v';
        } else {
            face = g.kind(c - 1, r) != '#' ? '<' : '>';
        }
        Dir facing = plot.dir(face);
        int x = plot.x(c, r);
        int z = plot.z(c, r);
        b.set(x, y, z, Keys.door(s.door(), facing, false, false, false));
        b.set(x, y + 1, z, Keys.door(s.door(), facing, true, false, false));
        b.set(x, y + 2, z, s.wall());
    }

    /** The beds of a grid as foot/head pairs, for checks. */
    static List<int[]> beds(Grid g) {
        List<int[]> out = new ArrayList<>();
        for (int r = 0; r < g.rows(); r++) {
            for (int c = 0; c < g.cols(); c++) {
                if (g.kind(c, r) == 'B') {
                    char d = g.cells[r][c].dir();
                    out.add(new int[] {c, r, c + Plot.da(d), r + Plot.db(d)});
                }
            }
        }
        return out;
    }
}
