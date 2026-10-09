package com.lewandivka.core.world.gen;

import com.lewandivka.core.world.Noise;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The ground of the district: a flat core at the level of the tram line, gentle hills towards the edge, roads that are graded
 * flat through the hills and a plateau (with a sloping margin where it cuts into or fills up the land) under every building, so
 * that nothing hangs in the air or sinks into a hillside.
 *
 * <p>The natural land is a pure function of the position. The plateaus are registered while the plan lays the district out and
 * frozen afterwards; the final height of a column is the natural land bent towards every plateau whose margin reaches it, in the
 * order in which they were registered.</p>
 */
final class CityGround {

    private static final long S_BROAD = 0x51C0FFEEL;
    private static final long S_LOCAL = 0x51C0FFEFL;
    private static final long S_WARP = 0x51C0FFF0L;

    /** How high the hills of the district get (the noise is about -1..1 and is scaled by the masks below). */
    static final double AMPLITUDE = 15.0;
    /** Where the flat core ends and the hills begin to rise (distance from the tram stop, square metric). */
    private static final double CORE_FLAT = 40;
    private static final double CORE_FULL = 95;
    /** The land stays level this close to a road and reaches its natural height this far from it. */
    private static final double ROAD_FLAT = 7;
    private static final double ROAD_FREE = 26;
    private static final int CELL = 16;

    private record Plateau(int x1, int z1, int x2, int z2, int level, int margin) {
        double weight(int x, int z) {
            int dx = Math.max(Math.max(x1 - x, 0), x - x2);
            int dz = Math.max(Math.max(z1 - z, 0), z - z2);
            if (dx == 0 && dz == 0) {
                return 1.0;
            }
            double d = Math.sqrt((double) dx * dx + (double) dz * dz);
            return d >= margin || margin <= 0 ? 0.0 : 1.0 - Noise.smoothstep(0, margin, d);
        }

        /** The distance between two plateaus (0 when they touch or overlap). */
        double gap(Plateau o) {
            int dx = Math.max(Math.max(o.x1 - x2, 0), x1 - o.x2);
            int dz = Math.max(Math.max(o.z1 - z2, 0), z1 - o.z2);
            return Math.sqrt((double) dx * dx + (double) dz * dz);
        }

        Plateau withMargin(int m) {
            return new Plateau(x1, z1, x2, z2, level, m);
        }
    }

    private final int level;
    /** Roads, sidewalks and the tram bed as inclusive rectangles {x1, z1, x2, z2}. */
    private final List<int[]> roads;
    private final List<Plateau> plateaus = new ArrayList<>();
    private Map<Long, int[]> index;

    CityGround(int level, List<int[]> roads) {
        this.level = level;
        this.roads = roads;
    }

    // ------------------------------------------------------------------ the land

    /** The hills of the district around the flat level, before any road or building bends them. */
    double natural(int x, int z) {
        double broad = Noise.fbmPerlin2(S_BROAD, x / 130.0, z / 130.0, 3);
        double local = Noise.fbmPerlin2(S_LOCAL, x / 38.0, z / 38.0, 2);
        double n = Noise.clamp(1.9 * broad + 0.45 * local, -1.0, 1.0);
        // hills rise higher than the hollows sink: the district lies in a shallow basin with gentle vales, not in a crater field
        n = n >= 0 ? n : 0.45 * n;
        // the edge of the flat core wanders a little, so that it is not a square on the map
        double warp = 16.0 * Noise.perlin2(S_WARP, x / 70.0, z / 70.0);
        double r = Math.max(Math.abs(x), Math.abs(z)) + warp;
        double core = Noise.smoothstep(CORE_FLAT, CORE_FULL, r);
        double road = Noise.smoothstep(ROAD_FLAT, ROAD_FREE, distanceToRoad(x, z));
        return level + AMPLITUDE * n * core * road;
    }

    double distanceToRoad(int x, int z) {
        double best = Double.MAX_VALUE;
        for (int[] r : roads) {
            int dx = Math.max(Math.max(r[0] - x, 0), x - r[2]);
            int dz = Math.max(Math.max(r[1] - z, 0), z - r[3]);
            best = Math.min(best, Math.sqrt((double) dx * dx + (double) dz * dz));
        }
        return best;
    }

    // ------------------------------------------------------------------ plateaus

    /** A flat place at {@code level} with a margin in which the land slopes back to its natural height. */
    void plateau(int x1, int z1, int x2, int z2, int plateauLevel, int margin) {
        if (index != null) {
            throw new IllegalStateException("the ground is frozen");
        }
        plateaus.add(new Plateau(Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2), plateauLevel, margin));
    }

    /** Makes the plateaus searchable; no plateau can be added afterwards. */
    void freeze() {
        // the margin of a plateau never reaches another plateau, so that the land inside each of them is exactly its level
        for (int i = 0; i < plateaus.size(); i++) {
            Plateau p = plateaus.get(i);
            int margin = p.margin;
            for (int j = 0; j < plateaus.size(); j++) {
                if (j != i) {
                    margin = Math.min(margin, (int) Math.max(0, Math.ceil(p.gap(plateaus.get(j))) - 1));
                }
            }
            if (margin != p.margin) {
                plateaus.set(i, p.withMargin(margin));
            }
        }
        Map<Long, List<Integer>> cells = new HashMap<>();
        for (int i = 0; i < plateaus.size(); i++) {
            Plateau p = plateaus.get(i);
            for (int cx = Math.floorDiv(p.x1 - p.margin, CELL); cx <= Math.floorDiv(p.x2 + p.margin, CELL); cx++) {
                for (int cz = Math.floorDiv(p.z1 - p.margin, CELL); cz <= Math.floorDiv(p.z2 + p.margin, CELL); cz++) {
                    cells.computeIfAbsent(key(cx, cz), k -> new ArrayList<>()).add(i);
                }
            }
        }
        Map<Long, int[]> frozen = new HashMap<>();
        cells.forEach((k, list) -> frozen.put(k, list.stream().mapToInt(Integer::intValue).toArray()));
        this.index = frozen;
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    // ------------------------------------------------------------------ the result

    /** The height of the ground (as a real number: the wilderness blends from it before it is rounded). */
    double exact(int x, int z) {
        double h = natural(x, z);
        if (index == null) {
            return h;
        }
        int[] near = index.get(key(Math.floorDiv(x, CELL), Math.floorDiv(z, CELL)));
        if (near != null) {
            for (int i : near) {
                Plateau p = plateaus.get(i);
                double w = p.weight(x, z);
                if (w > 0) {
                    h = Noise.lerp(h, p.level, w);
                }
            }
        }
        return h;
    }

    int at(int x, int z) {
        return (int) Math.round(exact(x, z));
    }
}
