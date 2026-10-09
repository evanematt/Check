package com.lewandivka.core.structure;

import java.util.ArrayList;
import java.util.List;

/**
 * A blueprint placed at a fixed world position. {@code (x, y, z)} is the world position of the
 * blueprint cell (0, 0, 0), i.e. the minimum corner.
 */
public record StructurePlacement(String id, Blueprint blueprint, int x, int y, int z) {

    public int maxX() {
        return x + blueprint.sizeX() - 1;
    }

    public int maxY() {
        return y + blueprint.sizeY() - 1;
    }

    public int maxZ() {
        return z + blueprint.sizeZ() - 1;
    }

    public boolean intersectsXZ(int minX, int minZ, int maxXq, int maxZq) {
        return x <= maxXq && maxX() >= minX && z <= maxZq && maxZ() >= minZ;
    }

    public boolean contains(int wx, int wy, int wz) {
        return wx >= x && wx <= maxX() && wy >= y && wy <= maxY() && wz >= z && wz <= maxZ();
    }

    /** Absolute markers of this placement, named {@code <structure id>:<marker name>}. */
    public List<MarkerPos> markers() {
        List<MarkerPos> out = new ArrayList<>();
        for (Blueprint.Marker m : blueprint.markers()) {
            out.add(new MarkerPos(id + ":" + m.name(), x + m.x(), y + m.y(), z + m.z(), m.sx(), m.sy(), m.sz(), m.data()));
        }
        return out;
    }

    /** Absolute marker position; {@code (x, y, z)} is the minimum corner for regions. */
    public record MarkerPos(String id, int x, int y, int z, int sx, int sy, int sz, String data) {
        public boolean isRegion() {
            return sx > 1 || sy > 1 || sz > 1;
        }

        public int maxX() {
            return x + sx - 1;
        }

        public int maxY() {
            return y + sy - 1;
        }

        public int maxZ() {
            return z + sz - 1;
        }
    }
}
