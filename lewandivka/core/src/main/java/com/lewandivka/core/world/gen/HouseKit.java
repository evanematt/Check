package com.lewandivka.core.world.gen;

import com.lewandivka.core.structure.BlueprintBuilder;
import com.lewandivka.core.structure.Dir;
import com.lewandivka.core.structure.Keys;

import java.util.ArrayList;
import java.util.List;

/**
 * The inside of a two-storey house and of the kindergarten: the floors, the rooms with their furniture, the stairs. The walls
 * and the roof are the business of {@link Buildings}; here lies what a visitor finds behind the door.
 *
 * <p>The interior of a house of width {@code w} and depth 9 is {@code (w - 2) x 7} cells, the stairs run along the back wall
 * from the left corner.</p>
 */
final class HouseKit {

    private HouseKit() {
    }

    // a cottage of width 12 (10 x 7 cells): one open room downstairs, two bedrooms and a bathroom upstairs
    static final String[] HOUSE_GROUND = {
            "P  .  .  Vv .  .  .  .  .  P",
            ".  R  R  R  .  .  .  .  .  K<",
            ".  R  R  R  .  .  cv .  l  s<",
            ".  S^ S^ S^ l  .  T  .  .  X<",
            ".  .  .  .  .  .  T  .  .  K<",
            ".  .  l  .  .  .  c^ .  .  F",
            ".  %  %  %  %  .  .  W< .  .",
    };

    static final String[] HOUSE_UPPER = {
            "~  T  T  .  W< #  P  .  Wv H<",
            "B^ c^ .  .  .  #  .  .  .  ~",
            ".  .  R  R  .  #  R  R  .  B^",
            "H> .  .  l  .  #  .  l  .  .",
            "#  #  #  #  d  #  d  #  #  #",
            "P  P  |  |  .  l  .  d  .  t<",
            ".  %  %  %  %  .  .  #  u  u",
    };

    /** How many columns the cottage grows by for every block of width beyond 12 (they are put in before this column). */
    static final int GROUND_WIDEN_AT = 5;
    static final int UPPER_WIDEN_AT = 6;

    // the kindergarten: two group rooms of 9 x 8 on both sides of a hall of 2 x 8, upstairs a music room and a sleeping room
    static final String[] KINDER_GROUND_LEFT = {
            "H> .  .  P  .  .  .  P  .",
            ".  .  .  .  .  .  .  .  .",
            "y  .  R  R  R  R  R  .  Q<",
            ".  .  R  y  y  R  R  .  Q<",
            "H> .  R  R  R  R  R  .  Q<",
            ".  .  .  .  l  .  .  .  y",
            ".  .  .  .  .  T  T  y  .",
            ".  %  %  %  %  c^ c^ l  .",
    };

    static final String[] KINDER_GROUND_RIGHT = {
            ".  .  P  l  .  l  P  .  .",
            ".  .  .  .  R  .  .  .  .",
            "~  B< .  .  R  .  .  B> ~",
            ".  .  .  .  R  .  .  .  .",
            "~  B< .  .  R  .  .  B> ~",
            ".  .  .  .  R  .  .  .  .",
            "~  B< .  .  R  .  .  B> ~",
            "W> W> .  l  .  .  .  W< W<",
    };

    static final String[] KINDER_UPPER_LEFT = {
            "P  .  .  .  .  .  .  .  P",
            ".  y  .  R  R  R  .  y  .",
            "H> .  .  R  l  R  .  .  .",
            ".  .  .  R  R  R  .  .  H<",
            "H> .  .  .  .  .  .  .  H<",
            ".  .  |  |  .  .  y  .  .",
            ".  .  .  .  l  .  .  .  .",
            ".  %  %  %  %  .  .  .  .",
    };

    static final String[] KINDER_UPPER_RIGHT = {
            "P  .  T  T  .  T  T  .  P",
            ".  .  c^ c^ .  c^ c^ .  .",
            ".  .  .  .  l  .  .  .  .",
            "H> .  T  T  .  T  T  .  .",
            ".  .  c^ c^ .  c^ c^ .  .",
            "H> .  .  .  l  .  .  .  .",
            ".  .  .  .  .  .  .  .  P",
            "W> W> .  .  .  .  .  .  .",
    };

    // ------------------------------------------------------------------ assembling

    /** The grids of a house of width {@code w}: ground floor, upper floor. */
    static String[][] house(int w) {
        int extra = w - 12;
        if (extra < 0 || extra % 2 != 0) {
            throw new IllegalArgumentException("a cottage is 12, 14 ... blocks wide: " + w);
        }
        return new String[][] {
                extra == 0 ? HOUSE_GROUND : RoomKit.widen(HOUSE_GROUND, GROUND_WIDEN_AT, extra, new String[] {".", ".", ".", ".", ".", ".", "."}),
                extra == 0 ? HOUSE_UPPER : RoomKit.widen(HOUSE_UPPER, UPPER_WIDEN_AT, extra, new String[] {".", ".", ".", ".", "#", ".", "."})
        };
    }

    /** Left room, wall with a door, the hall, wall with a door, right room. */
    static String[] join(String[] left, String[] hall, String[] right, int doorRow) {
        String[] rows = new String[left.length];
        for (int r = 0; r < left.length; r++) {
            String wall = r == doorRow ? "d" : "#";
            rows[r] = left[r].trim() + " " + wall + " " + hall[r].trim() + " " + wall + " " + right[r].trim();
        }
        return rows;
    }

    static final String[] KINDER_HALL_GROUND = {". l", ". .", ". .", ". .", ". .", ". .", ". .", ". ."};
    static final String[] KINDER_HALL_UPPER = {". .", ". l", ". .", ". .", ". .", ". .", ". l", ". ."};

    static String[][] kindergarten() {
        return new String[][] {
                join(KINDER_GROUND_LEFT, KINDER_HALL_GROUND, KINDER_GROUND_RIGHT, 1),
                join(KINDER_UPPER_LEFT, KINDER_HALL_UPPER, KINDER_UPPER_RIGHT, 1)
        };
    }

    // ------------------------------------------------------------------ building

    /**
     * Furnishes the interior of a house or of the kindergarten whose shell stands: the floors of both storeys, the rooms and the
     * stairs. {@code d} is the depth of the house; the cells {@code 1 .. w - 2} x {@code 1 .. d - 2} are the inside.
     */
    static void furnish(BlueprintBuilder b, int w, int d, Furnish.Style s, long seed, boolean kindergarten) {
        String[][] floors = kindergarten ? kindergarten() : house(w);
        for (int storey = 0; storey < 2; storey++) {
            RoomKit.Grid grid = RoomKit.parse(floors[storey]);
            if (grid.cols() != w - 2 || grid.rows() != d - 2) {
                throw new IllegalStateException("the grid of storey " + storey + " is " + grid.cols() + " x " + grid.rows() + " but the inside is " + (w - 2) + " x " + (d - 2));
            }
            RoomKit.place(b, new Plot(1, 1, Dir.EAST, Dir.SOUTH), 4 * storey, grid, s, seed + storey);
        }
        stairs(b, d - 2, s);
    }

    /**
     * Four steps along the back wall, the first one a cell away from the corner (a flight can only be entered from the end, so the
     * cell in front of it stays free): a slab in the upper half of the floor over the first step, a hole over the next two, the
     * last step in the floor of the upper storey.
     */
    private static void stairs(BlueprintBuilder b, int z, Furnish.Style s) {
        stairs(b, 2, z, s);
    }

    /** The same flight with its first step at x = {@code x0}. */
    static void stairs(BlueprintBuilder b, int x0, int z, Furnish.Style s) {
        for (int step = 0; step < 4; step++) {
            b.set(x0 + step, 1 + step, z, Keys.stairs(s.stairs(), Dir.EAST, false));
        }
        b.set(x0, 4, z, Keys.slab(s.slab(), true));
        b.set(x0 + 1, 4, z, Keys.AIR);
        b.set(x0 + 2, 4, z, Keys.AIR);
        // under the steps: planks (nothing is to be passed there)
        b.set(x0 + 1, 1, z, s.planks());
        b.fill(x0 + 2, 1, z, x0 + 2, 2, z, s.planks());
        b.fill(x0 + 3, 1, z, x0 + 3, 3, z, s.planks());
    }

    /** The cells (x, z) of the inside that stand free on the ground floor, for the walkability checks of the houses. */
    static List<int[]> freeCells(String[] rows) {
        RoomKit.Grid g = RoomKit.parse(rows);
        boolean[][] free = RoomKit.standable(g);
        List<int[]> out = new ArrayList<>();
        for (int r = 0; r < g.rows(); r++) {
            for (int c = 0; c < g.cols(); c++) {
                if (free[r][c]) {
                    out.add(new int[] {1 + c, 1 + r});
                }
            }
        }
        return out;
    }
}
