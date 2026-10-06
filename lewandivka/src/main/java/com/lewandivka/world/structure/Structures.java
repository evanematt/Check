package com.lewandivka.world.structure;

import com.lewandivka.LewandivkaMod;
import com.lewandivka.core.structure.StructurePlacement;
import com.lewandivka.core.structure.StructurePlacement.MarkerPos;
import com.lewandivka.core.world.WorldPlan;
import com.lewandivka.core.world.gen.ChromaPlan;
import com.lewandivka.core.world.gen.DistrictPlan;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lookup of the quest structures and their markers ({@code garage13:breaker_1}). The positions come from the
 * deterministic world plans, so nothing about them is stored: the marker registry is the same on every server and in
 * every save, and no gameplay class contains world coordinates.
 */
public final class Structures {

    /** A marker with its dimension. For regions {@code (x, y, z)} is the minimum corner. */
    public record Marker(String id, String structure, String name, String dimension, int x, int y, int z, int sx, int sy, int sz, String data) {

        public boolean isRegion() {
            return sx > 1 || sy > 1 || sz > 1;
        }

        public BlockPos pos() {
            return new BlockPos(x, y, z);
        }

        public BlockPos max() {
            return new BlockPos(x + sx - 1, y + sy - 1, z + sz - 1);
        }

        public Vec3d center() {
            return new Vec3d(x + sx / 2.0, y + sy / 2.0, z + sz / 2.0);
        }

        /** Standing position on top of a point marker. */
        public Vec3d stand() {
            return new Vec3d(x + 0.5, y, z + 0.5);
        }

        public Box box() {
            return new Box(x, y, z, x + sx, y + sy, z + sz);
        }

        public boolean contains(BlockPos p) {
            return p.getX() >= x && p.getX() < x + sx && p.getY() >= y && p.getY() < y + sy && p.getZ() >= z && p.getZ() < z + sz;
        }

        /** Value of a {@code key=value} entry of the marker data, or the default. */
        public String data(String key, String def) {
            for (String part : data.split(",")) {
                int eq = part.indexOf('=');
                if (eq > 0 && part.substring(0, eq).trim().equals(key)) {
                    return part.substring(eq + 1).trim();
                }
            }
            return def;
        }
    }

    public record Site(String dimension, WorldPlan plan, StructurePlacement placement) {
    }

    private static final class Index {
        final Map<String, Marker> markers = new LinkedHashMap<>();
        final Map<String, List<Marker>> byStructure = new HashMap<>();
        final Map<String, Site> sites = new LinkedHashMap<>();
        final Map<String, Map<Long, Marker>> pointAt = new HashMap<>();
    }

    private static volatile Index index;

    private Structures() {
    }

    private static Index index() {
        Index i = index;
        if (i == null) {
            synchronized (Structures.class) {
                i = index;
                if (i == null) {
                    i = build();
                    index = i;
                }
            }
        }
        return i;
    }

    private static Index build() {
        Index idx = new Index();
        long start = System.currentTimeMillis();
        add(idx, DistrictPlan.ID, DistrictPlan.get());
        add(idx, ChromaPlan.ID, ChromaPlan.get());
        LewandivkaMod.LOGGER.info("Structure index: {} structures, {} markers ({} ms)", idx.sites.size(), idx.markers.size(), System.currentTimeMillis() - start);
        return idx;
    }

    private static void add(Index idx, String dimension, WorldPlan plan) {
        Map<Long, Marker> points = idx.pointAt.computeIfAbsent(dimension, k -> new HashMap<>());
        for (StructurePlacement placement : plan.fixedPlacements()) {
            idx.sites.put(placement.id(), new Site(dimension, plan, placement));
            for (MarkerPos m : placement.markers()) {
                register(idx, dimension, m, points);
            }
        }
        for (MarkerPos m : plan.planMarkers()) {
            register(idx, dimension, m, points);
        }
    }

    private static void register(Index idx, String dimension, MarkerPos m, Map<Long, Marker> points) {
        int colon = m.id().indexOf(':');
        String structure = colon < 0 ? "" : m.id().substring(0, colon);
        String name = colon < 0 ? m.id() : m.id().substring(colon + 1);
        Marker marker = new Marker(m.id(), structure, name, dimension, m.x(), m.y(), m.z(), m.sx(), m.sy(), m.sz(), m.data());
        idx.markers.put(m.id(), marker);
        idx.byStructure.computeIfAbsent(structure, k -> new ArrayList<>()).add(marker);
        if (!marker.isRegion()) {
            points.put(BlockPos.asLong(m.x(), m.y(), m.z()), marker);
        }
    }

    /** Builds the index now (server start) so the first player does not pay for it. */
    public static void warmUp() {
        index();
    }

    public static Marker marker(String id) {
        return index().markers.get(id);
    }

    public static Marker require(String id) {
        Marker m = marker(id);
        if (m == null) {
            throw new IllegalArgumentException("unknown marker " + id);
        }
        return m;
    }

    public static boolean has(String id) {
        return index().markers.containsKey(id);
    }

    public static List<Marker> markersOf(String structure) {
        return index().byStructure.getOrDefault(structure, List.of());
    }

    /** Markers of a structure whose name starts with the prefix, in a stable order. */
    public static List<Marker> withPrefix(String structure, String prefix) {
        List<Marker> out = new ArrayList<>();
        for (Marker m : markersOf(structure)) {
            if (m.name().startsWith(prefix)) {
                out.add(m);
            }
        }
        return out;
    }

    public static Site site(String structure) {
        return index().sites.get(structure);
    }

    public static Iterable<Site> sites() {
        return index().sites.values();
    }

    public static String dimensionOf(String structure) {
        Site s = site(structure);
        return s == null ? null : s.dimension();
    }

    /** The point marker exactly at a block (stations: levers, panels, pedestals), or null. */
    public static Marker markerAt(String dimension, BlockPos pos) {
        Map<Long, Marker> points = index().pointAt.get(dimension);
        return points == null ? null : points.get(pos.asLong());
    }

    /** The structure whose bounding box contains the position, or null. */
    public static String structureAt(String dimension, BlockPos pos) {
        for (Site s : index().sites.values()) {
            if (s.dimension().equals(dimension) && s.placement().contains(pos.getX(), pos.getY(), pos.getZ())) {
                return s.placement().id();
            }
        }
        return null;
    }

    public static boolean contains(String structure, BlockPos pos) {
        Site s = site(structure);
        return s != null && s.placement().contains(pos.getX(), pos.getY(), pos.getZ());
    }

    public static Box bounds(String structure) {
        StructurePlacement p = site(structure).placement();
        return new Box(p.x(), p.y(), p.z(), p.maxX() + 1, p.maxY() + 1, p.maxZ() + 1);
    }
}
