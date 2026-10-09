package com.lewandivka.core.world.gen;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The room grids: well formed, a bed is a pair, every free cell can be reached from the door. */
class RoomKitTest {

    private static final String KNOWN = ".#dl%~BTcWHSVKXsFPRLtuyQ|NCO";

    static void dump(String name, String[] rows) {
        StringBuilder sb = new StringBuilder(name + "\n");
        for (String row : rows) {
            sb.append("   ").append(row).append('\n');
        }
        System.out.print(sb);
    }

    /** Checks of a grid: tokens known, beds in pairs, arrows where things face. */
    static void wellFormed(String name, RoomKit.Grid grid) {
        for (int r = 0; r < grid.rows(); r++) {
            for (int c = 0; c < grid.cols(); c++) {
                char k = grid.kind(c, r);
                assertTrue(KNOWN.indexOf(k) >= 0, name + " cell " + c + "," + r + ": unknown token " + k);
                if ("cWSVKXtBQCO".indexOf(k) >= 0) {
                    assertTrue("<>^v".indexOf(grid.cells()[r][c].dir()) >= 0, name + " cell " + c + "," + r + " (" + k + ") needs a direction");
                }
            }
        }
        int heads = 0;
        for (RoomKit.Token[] row : grid.cells()) {
            for (RoomKit.Token t : row) {
                heads += t.kind() == '~' ? 1 : 0;
            }
        }
        assertEquals(RoomKit.beds(grid).size(), heads, name + ": one head cell for every bed");
        for (int[] bed : RoomKit.beds(grid)) {
            assertEquals('~', grid.kind(bed[2], bed[3]), name + ": the head of the bed at " + bed[0] + "," + bed[1]);
        }
    }

    /** Every free cell of the grid can be reached from the cell (c, r) without climbing over furniture. */
    static void reachableFrom(String name, RoomKit.Grid grid, int c0, int r0) {
        boolean[][] free = RoomKit.standable(grid);
        assertTrue(free[r0][c0], name + ": the start cell " + c0 + "," + r0 + " is free");
        boolean[][] seen = new boolean[grid.rows()][grid.cols()];
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[] {c0, r0});
        seen[r0][c0] = true;
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int c = cur[0] + d[0];
                int r = cur[1] + d[1];
                if (c >= 0 && r >= 0 && c < grid.cols() && r < grid.rows() && free[r][c] && !seen[r][c]) {
                    seen[r][c] = true;
                    queue.add(new int[] {c, r});
                }
            }
        }
        for (int r = 0; r < grid.rows(); r++) {
            for (int c = 0; c < grid.cols(); c++) {
                assertTrue(!free[r][c] || seen[r][c], name + ": the free cell " + c + "," + r + " cannot be reached from " + c0 + "," + r0);
            }
        }
    }

    @Test
    void everyFlatGridIsWellFormedAndWalkable() {
        for (int i = 0; i < RoomKit.FLATS.length; i++) {
            String name = "flat " + i;
            dump(name, RoomKit.FLATS[i]);
            RoomKit.Grid grid = RoomKit.parse(RoomKit.FLATS[i]);
            assertEquals(5, grid.cols());
            assertEquals(5, grid.rows());
            wellFormed(name, grid);
            assertEquals('.', grid.kind(RoomKit.ENTRY_A, RoomKit.ENTRY_B), name + ": the cell inside the door is free");
            reachableFrom(name, grid, RoomKit.ENTRY_A, RoomKit.ENTRY_B);
        }
    }
}
