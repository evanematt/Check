package com.lewandivka.core.structure;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Walkability analysis of a blueprint: can a player get from one marker to another on foot?
 *
 * <p>This is how the build proves that dungeons are connected without launching the game. The
 * analysis treats untouched cells as air above {@code groundY} and as solid ground at or below it,
 * allows steps up of one block, drops of up to {@link #MAX_DROP} blocks, ladders and water.</p>
 */
public final class Walk {

    public static final int MAX_DROP = 4;

    private final Blueprint bp;
    private final boolean gatesOpen;
    private final int groundY;
    private final Map<String, Materials.Kind> cache = new HashMap<>();
    private final java.util.List<int[]> links = new java.util.ArrayList<>();
    private final java.util.List<int[]> openBoxes = new java.util.ArrayList<>();
    private boolean halfSteps;

    /**
     * @param gatesOpen treat quest gates as open doors (the intended route after the puzzle is solved)
     * @param groundY   untouched cells with y &lt;= groundY count as solid terrain (use -1 for none)
     */
    public Walk(Blueprint bp, boolean gatesOpen, int groundY) {
        this.bp = bp;
        this.gatesOpen = gatesOpen;
        this.groundY = groundY;
    }

    /**
     * Stairs rise by half a block at a time, so the head of someone climbing needs only 0.3 blocks of the cell above the one he
     * would step into: a slab in the upper half of that cell does not stop him. (A plain jump up one block would; this is for
     * stairwells and has to be asked for.)
     */
    public Walk halfSteps() {
        this.halfSteps = true;
        return this;
    }

    private boolean headroom(int x, int y, int z) {
        if (free(x, y, z)) {
            return true;
        }
        if (!halfSteps) {
            return false;
        }
        String key = x < 0 || z < 0 || x >= bp.sizeX() || z >= bp.sizeZ() || y < 0 || y >= bp.sizeY() ? null : bp.keyAt(x, y, z);
        return key != null && Keys.fallback(key).contains("_slab[type=top");
    }

    /** Cells inside this box count as air when gates are open (region markers of quest gates). */
    public Walk openBox(int x, int y, int z, int sx, int sy, int sz) {
        openBoxes.add(new int[] {x, y, z, x + sx - 1, y + sy - 1, z + sz - 1});
        return this;
    }

    /**
     * Connects two standing cells in both directions (an elevator/lift ride, a teleport).
     * The cells must be standable in this blueprint for the link to be used.
     */
    public Walk link(int x1, int y1, int z1, int x2, int y2, int z2) {
        links.add(new int[] {x1, y1, z1, x2, y2, z2});
        return this;
    }

    private Materials.Kind kind(int x, int y, int z) {
        if (x < 0 || z < 0 || x >= bp.sizeX() || z >= bp.sizeZ() || y >= bp.sizeY()) {
            return Materials.Kind.PASS;
        }
        if (y < 0) {
            return Materials.Kind.SOLID;
        }
        if (gatesOpen) {
            for (int[] box : openBoxes) {
                if (x >= box[0] && x <= box[3] && y >= box[1] && y <= box[4] && z >= box[2] && z <= box[5]) {
                    return Materials.Kind.PASS;
                }
            }
        }
        String key = bp.keyAt(x, y, z);
        if (key == null) {
            return y <= groundY ? Materials.Kind.SOLID : Materials.Kind.PASS;
        }
        return cache.computeIfAbsent(key, Materials::classify);
    }

    private boolean free(int x, int y, int z) {
        Materials.Kind k = kind(x, y, z);
        return k == Materials.Kind.PASS || k == Materials.Kind.CLIMB || k == Materials.Kind.LIQUID
                || (k == Materials.Kind.GATE && gatesOpen);
    }

    private boolean solidFloor(int x, int y, int z) {
        Materials.Kind k = kind(x, y, z);
        return k == Materials.Kind.SOLID || (k == Materials.Kind.GATE && !gatesOpen)
                || k == Materials.Kind.CLIMB || k == Materials.Kind.LIQUID;
    }

    /** True if a player (2 blocks tall) can stand with feet in cell (x,y,z). */
    public boolean canStand(int x, int y, int z) {
        if (!free(x, y, z) || !free(x, y + 1, z)) {
            return false;
        }
        Materials.Kind here = kind(x, y, z);
        if (here == Materials.Kind.CLIMB || here == Materials.Kind.LIQUID) {
            return true;
        }
        return solidFloor(x, y - 1, z);
    }

    /**
     * Breadth-first search over standing cells.
     *
     * @return reachable standing cells as a set of packed coordinates
     */
    public Reach from(int sx, int sy, int sz) {
        Reach r = new Reach(bp);
        if (!canStand(sx, sy, sz)) {
            return r;
        }
        Deque<int[]> queue = new ArrayDeque<>();
        r.add(sx, sy, sz);
        queue.add(new int[] {sx, sy, sz});
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            int x = c[0];
            int y = c[1];
            int z = c[2];
            // Ladders and vines: climb up and down.
            if (kind(x, y, z) == Materials.Kind.CLIMB || kind(x, y - 1, z) == Materials.Kind.CLIMB) {
                for (int dy : new int[] {1, -1}) {
                    if (canStand(x, y + dy, z) && r.add(x, y + dy, z)) {
                        queue.add(new int[] {x, y + dy, z});
                    }
                }
            }
            // lifts and other scripted rides
            for (int[] l : links) {
                if (l[0] == x && l[1] == y && l[2] == z && canStand(l[3], l[4], l[5]) && r.add(l[3], l[4], l[5])) {
                    queue.add(new int[] {l[3], l[4], l[5]});
                }
                if (l[3] == x && l[4] == y && l[5] == z && canStand(l[0], l[1], l[2]) && r.add(l[0], l[1], l[2])) {
                    queue.add(new int[] {l[0], l[1], l[2]});
                }
            }
            // Swimming: move up/down freely in water.
            if (kind(x, y, z) == Materials.Kind.LIQUID) {
                for (int dy : new int[] {1, -1}) {
                    if (canStand(x, y + dy, z) && r.add(x, y + dy, z)) {
                        queue.add(new int[] {x, y + dy, z});
                    }
                }
            }
            for (int[] d : dirs) {
                // running jump across a gap of up to two blocks (landing at the same height or lower)
                if (free(x, y + 2, z)) {
                    for (int dist = 2; dist <= 3; dist++) {
                        int jx = x + d[0] * dist;
                        int jz = z + d[1] * dist;
                        boolean clear = true;
                        for (int k = 1; k < dist && clear; k++) {
                            clear = free(x + d[0] * k, y, z + d[1] * k) && free(x + d[0] * k, y + 1, z + d[1] * k)
                                    && free(x + d[0] * k, y + 2, z + d[1] * k);
                        }
                        if (!clear) {
                            break;
                        }
                        for (int drop = 0; drop <= 1; drop++) {
                            if (canStand(jx, y - drop, jz) && free(jx, y - drop + 2, jz) && r.add(jx, y - drop, jz)) {
                                queue.add(new int[] {jx, y - drop, jz});
                            }
                        }
                    }
                }
                int nx = x + d[0];
                int nz = z + d[1];
                // step up one block (needs headroom above the starting cell)
                if (headroom(x, y + 2, z) && canStand(nx, y + 1, nz) && free(nx, y + 2, nz) && r.add(nx, y + 1, nz)) {
                    queue.add(new int[] {nx, y + 1, nz});
                }
                // level
                if (canStand(nx, y, nz) && r.add(nx, y, nz)) {
                    queue.add(new int[] {nx, y, nz});
                }
                // drop down
                if (free(nx, y, nz) && free(nx, y + 1, nz)) {
                    for (int drop = 1; drop <= MAX_DROP; drop++) {
                        if (canStand(nx, y - drop, nz)) {
                            if (r.add(nx, y - drop, nz)) {
                                queue.add(new int[] {nx, y - drop, nz});
                            }
                            break;
                        }
                        if (!free(nx, y - drop, nz)) {
                            break;
                        }
                    }
                }
            }
        }
        return r;
    }

    /** Result of a search. */
    public static final class Reach {
        private final Blueprint bp;
        private final java.util.BitSet seen;

        Reach(Blueprint bp) {
            this.bp = bp;
            this.seen = new java.util.BitSet(bp.sizeX() * (bp.sizeY() + 8) * bp.sizeZ());
        }

        private int idx(int x, int y, int z) {
            return ((y + 4) * bp.sizeZ() + z) * bp.sizeX() + x;
        }

        boolean add(int x, int y, int z) {
            if (x < 0 || z < 0 || x >= bp.sizeX() || z >= bp.sizeZ() || y < -4 || y >= bp.sizeY() + 4) {
                return false;
            }
            int i = idx(x, y, z);
            if (seen.get(i)) {
                return false;
            }
            seen.set(i);
            return true;
        }

        public boolean contains(int x, int y, int z) {
            if (x < 0 || z < 0 || x >= bp.sizeX() || z >= bp.sizeZ() || y < -4 || y >= bp.sizeY() + 4) {
                return false;
            }
            return seen.get(idx(x, y, z));
        }

        /** True if any standing cell within a small box around the point is reachable. */
        public boolean containsNear(int x, int y, int z, int radiusXZ, int radiusY) {
            for (int dy = -radiusY; dy <= radiusY; dy++) {
                for (int dz = -radiusXZ; dz <= radiusXZ; dz++) {
                    for (int dx = -radiusXZ; dx <= radiusXZ; dx++) {
                        if (contains(x + dx, y + dy, z + dz)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        public int count() {
            return seen.cardinality();
        }
    }
}
