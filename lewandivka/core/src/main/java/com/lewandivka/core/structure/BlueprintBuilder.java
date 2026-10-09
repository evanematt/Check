package com.lewandivka.core.structure;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable builder for {@link Blueprint}s with a tiny turtle-style coordinate frame:
 * {@link #at}, {@link #turn}, {@link #push}/{@link #pop}. Every drawing call takes coordinates in the
 * current frame, so a building can be written once ("front is north") and placed facing any way.
 * Direction-dependent block properties ({@code facing}, {@code axis}, fence connections, sign
 * rotation) are rotated automatically.
 */
public final class BlueprintBuilder {

    private final String id;
    private final int sx;
    private final int sy;
    private final int sz;
    private final short[] cells;
    private final List<String> palette = new ArrayList<>();
    private final Map<String, Integer> paletteIndex = new HashMap<>();
    private final List<Blueprint.Marker> markers = new ArrayList<>();

    private int ox;
    private int oy;
    private int oz;
    private int rot;
    private boolean clip;
    private final Deque<int[]> frames = new ArrayDeque<>();

    public BlueprintBuilder(String id, int sx, int sy, int sz) {
        if (sx < 1 || sy < 1 || sz < 1) {
            throw new IllegalArgumentException("size must be positive: " + id);
        }
        this.id = id;
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.cells = new short[sx * sy * sz];
        palette.add(null); // index 0 = untouched
    }

    public String id() {
        return id;
    }

    public int sizeX() {
        return sx;
    }

    public int sizeY() {
        return sy;
    }

    public int sizeZ() {
        return sz;
    }

    /** When true, writes outside the blueprint are silently ignored (used for organic scatter features). */
    public BlueprintBuilder clip(boolean value) {
        this.clip = value;
        return this;
    }

    // ------------------------------------------------------------------ frame

    public BlueprintBuilder push() {
        frames.push(new int[] {ox, oy, oz, rot});
        return this;
    }

    public BlueprintBuilder pop() {
        int[] f = frames.pop();
        ox = f[0];
        oy = f[1];
        oz = f[2];
        rot = f[3];
        return this;
    }

    /** Moves the frame origin by (x, y, z) expressed in the current frame. */
    public BlueprintBuilder at(int x, int y, int z) {
        int[] w = rotate(x, z, rot);
        ox += w[0];
        oy += y;
        oz += w[1];
        return this;
    }

    /** Rotates the frame clockwise (seen from above) by quarter turns around its origin. */
    public BlueprintBuilder turn(int quarterTurns) {
        rot = ((rot + quarterTurns) % 4 + 4) % 4;
        return this;
    }

    public int rotation() {
        return rot;
    }

    /**
     * Aligns a fresh builder so that a structure drawn in a local footprint of
     * {@code localSx x localSz} (front = local north) ends up rotated by {@code turns} clockwise quarter
     * turns inside a blueprint whose size is already the rotated footprint.
     */
    public BlueprintBuilder orient(int turns, int localSx, int localSz) {
        rot = ((turns % 4) + 4) % 4;
        switch (rot) {
            case 1 -> {
                ox = localSz - 1;
                oz = 0;
            }
            case 2 -> {
                ox = localSx - 1;
                oz = localSz - 1;
            }
            case 3 -> {
                ox = 0;
                oz = localSx - 1;
            }
            default -> {
                ox = 0;
                oz = 0;
            }
        }
        return this;
    }

    /** Direction in blueprint space that frame-direction {@code d} points to. */
    public Dir world(Dir d) {
        return d.turn(rot);
    }

    /** Converts frame coordinates to blueprint coordinates. */
    public int[] toBlueprint(int x, int y, int z) {
        int[] w = rotate(x, z, rot);
        return new int[] {ox + w[0], oy + y, oz + w[1]};
    }

    static int[] rotate(int x, int z, int turns) {
        return switch (((turns % 4) + 4) % 4) {
            case 1 -> new int[] {-z, x};
            case 2 -> new int[] {-x, -z};
            case 3 -> new int[] {z, -x};
            default -> new int[] {x, z};
        };
    }

    // ------------------------------------------------------------------ cells

    public BlueprintBuilder set(int x, int y, int z, String key) {
        int[] p = toBlueprint(x, y, z);
        put(p[0], p[1], p[2], rotateKey(key, rot));
        return this;
    }

    public BlueprintBuilder air(int x, int y, int z) {
        return set(x, y, z, Keys.AIR);
    }

    /** Writes only where nothing has been drawn yet. */
    public BlueprintBuilder setSoft(int x, int y, int z, String key) {
        int[] p = toBlueprint(x, y, z);
        if (inside(p[0], p[1], p[2]) && cells[idx(p[0], p[1], p[2])] == Blueprint.UNTOUCHED) {
            put(p[0], p[1], p[2], rotateKey(key, rot));
        }
        return this;
    }

    /** Key at a frame position, or null if untouched/outside. */
    public String get(int x, int y, int z) {
        int[] p = toBlueprint(x, y, z);
        if (!inside(p[0], p[1], p[2])) {
            return null;
        }
        short c = cells[idx(p[0], p[1], p[2])];
        return c == 0 ? null : palette.get(c);
    }

    public BlueprintBuilder fill(int x1, int y1, int z1, int x2, int y2, int z2, String key) {
        String rk = rotateKey(key, rot);
        forBox(x1, y1, z1, x2, y2, z2, (x, y, z) -> {
            int[] p = toBlueprint(x, y, z);
            put(p[0], p[1], p[2], rk);
        });
        return this;
    }

    /** Fills only untouched cells of the box. */
    public BlueprintBuilder fillSoft(int x1, int y1, int z1, int x2, int y2, int z2, String key) {
        String rk = rotateKey(key, rot);
        forBox(x1, y1, z1, x2, y2, z2, (x, y, z) -> {
            int[] p = toBlueprint(x, y, z);
            if (inside(p[0], p[1], p[2]) && cells[idx(p[0], p[1], p[2])] == Blueprint.UNTOUCHED) {
                put(p[0], p[1], p[2], rk);
            }
        });
        return this;
    }

    /** Replaces every cell holding {@code from} (frame-independent exact key) with {@code to}. */
    public BlueprintBuilder replace(int x1, int y1, int z1, int x2, int y2, int z2, String from, String to) {
        String rf = rotateKey(from, rot);
        String rt = rotateKey(to, rot);
        forBox(x1, y1, z1, x2, y2, z2, (x, y, z) -> {
            int[] p = toBlueprint(x, y, z);
            if (inside(p[0], p[1], p[2])) {
                short c = cells[idx(p[0], p[1], p[2])];
                if (c != 0 && rf.equals(palette.get(c))) {
                    put(p[0], p[1], p[2], rt);
                }
            }
        });
        return this;
    }

    /** Clears a box to air. */
    public BlueprintBuilder clear(int x1, int y1, int z1, int x2, int y2, int z2) {
        return fill(x1, y1, z1, x2, y2, z2, Keys.AIR);
    }

    /** Six-sided shell, interior left untouched. */
    public BlueprintBuilder shell(int x1, int y1, int z1, int x2, int y2, int z2, String key) {
        int ax = Math.min(x1, x2);
        int bx = Math.max(x1, x2);
        int ay = Math.min(y1, y2);
        int by = Math.max(y1, y2);
        int az = Math.min(z1, z2);
        int bz = Math.max(z1, z2);
        fill(ax, ay, az, bx, ay, bz, key);
        fill(ax, by, az, bx, by, bz, key);
        fill(ax, ay, az, ax, by, bz, key);
        fill(bx, ay, az, bx, by, bz, key);
        fill(ax, ay, az, bx, by, az, key);
        fill(ax, ay, bz, bx, by, bz, key);
        return this;
    }

    /** Shell plus air inside: a ready-made room. */
    public BlueprintBuilder room(int x1, int y1, int z1, int x2, int y2, int z2, String key) {
        int ax = Math.min(x1, x2);
        int bx = Math.max(x1, x2);
        int ay = Math.min(y1, y2);
        int by = Math.max(y1, y2);
        int az = Math.min(z1, z2);
        int bz = Math.max(z1, z2);
        if (bx - ax >= 2 && by - ay >= 2 && bz - az >= 2) {
            fill(ax + 1, ay + 1, az + 1, bx - 1, by - 1, bz - 1, Keys.AIR);
        }
        return shell(ax, ay, az, bx, by, bz, key);
    }

    /** Four vertical walls (no floor, no ceiling). */
    public BlueprintBuilder walls(int x1, int y1, int z1, int x2, int y2, int z2, String key) {
        int ax = Math.min(x1, x2);
        int bx = Math.max(x1, x2);
        int az = Math.min(z1, z2);
        int bz = Math.max(z1, z2);
        fill(ax, y1, az, bx, y2, az, key);
        fill(ax, y1, bz, bx, y2, bz, key);
        fill(ax, y1, az, ax, y2, bz, key);
        fill(bx, y1, az, bx, y2, bz, key);
        return this;
    }

    public BlueprintBuilder line(int x1, int y1, int z1, int x2, int y2, int z2, String key) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int dz = Math.abs(z2 - z1);
        int n = Math.max(dx, Math.max(dy, dz));
        if (n == 0) {
            return set(x1, y1, z1, key);
        }
        for (int i = 0; i <= n; i++) {
            int x = x1 + Math.round((x2 - x1) * (i / (float) n));
            int y = y1 + Math.round((y2 - y1) * (i / (float) n));
            int z = z1 + Math.round((z2 - z1) * (i / (float) n));
            set(x, y, z, key);
        }
        return this;
    }

    /** Filled disc of radius r in the horizontal plane at height y. */
    public BlueprintBuilder disc(int cx, int y, int cz, double r, String key) {
        int ri = (int) Math.ceil(r);
        for (int x = -ri; x <= ri; x++) {
            for (int z = -ri; z <= ri; z++) {
                if (x * x + z * z <= r * r + 0.5) {
                    set(cx + x, y, cz + z, key);
                }
            }
        }
        return this;
    }

    /** One block thick ring (outline of a disc). */
    public BlueprintBuilder ring(int cx, int y, int cz, double r, String key) {
        int ri = (int) Math.ceil(r) + 1;
        double inner = (r - 1) * (r - 1);
        for (int x = -ri; x <= ri; x++) {
            for (int z = -ri; z <= ri; z++) {
                double d = x * x + z * z;
                if (d <= r * r + 0.5 && d > inner) {
                    set(cx + x, y, cz + z, key);
                }
            }
        }
        return this;
    }

    public BlueprintBuilder cylinder(int cx, int y1, int y2, int cz, double r, String key) {
        for (int y = y1; y <= y2; y++) {
            disc(cx, y, cz, r, key);
        }
        return this;
    }

    /** Hollow cylinder wall with an air interior. */
    public BlueprintBuilder tube(int cx, int y1, int y2, int cz, double r, String wallKey) {
        for (int y = y1; y <= y2; y++) {
            disc(cx, y, cz, r - 1, Keys.AIR);
            ring(cx, y, cz, r, wallKey);
        }
        return this;
    }

    // ------------------------------------------------------------------ markers

    public BlueprintBuilder marker(String name, int x, int y, int z) {
        return marker(name, x, y, z, "");
    }

    public BlueprintBuilder marker(String name, int x, int y, int z, String data) {
        int[] p = toBlueprint(x, y, z);
        if (!inside(p[0], p[1], p[2])) {
            throw new IllegalArgumentException(id + ": marker '" + name + "' outside blueprint at " + p[0] + "," + p[1] + "," + p[2]);
        }
        markers.add(new Blueprint.Marker(name, p[0], p[1], p[2], 1, 1, 1, data == null ? "" : rotateData(data, rot)));
        return this;
    }

    /** Region marker (door/gate volume, hazard zone, arena bounds). */
    public BlueprintBuilder region(String name, int x1, int y1, int z1, int x2, int y2, int z2) {
        return region(name, x1, y1, z1, x2, y2, z2, "");
    }

    /**
     * Quest gate: a region marker that remembers the block it is made of when closed
     * ({@code closed=<key>}). The region is filled with that block now (initially closed) or left open.
     */
    public BlueprintBuilder gate(String name, int x1, int y1, int z1, int x2, int y2, int z2, String closedKey, boolean initiallyClosed) {
        if (initiallyClosed) {
            fill(x1, y1, z1, x2, y2, z2, closedKey);
        }
        return region(name, x1, y1, z1, x2, y2, z2, "closed=" + closedKey);
    }

    /** Places an interactable block and registers a point marker on it. */
    public BlueprintBuilder interact(String name, int x, int y, int z, String key) {
        set(x, y, z, key);
        return marker(name, x, y, z);
    }

    public BlueprintBuilder region(String name, int x1, int y1, int z1, int x2, int y2, int z2, String data) {
        int[] a = toBlueprint(x1, y1, z1);
        int[] b = toBlueprint(x2, y2, z2);
        int minX = Math.min(a[0], b[0]);
        int minY = Math.min(a[1], b[1]);
        int minZ = Math.min(a[2], b[2]);
        int maxX = Math.max(a[0], b[0]);
        int maxY = Math.max(a[1], b[1]);
        int maxZ = Math.max(a[2], b[2]);
        if (!inside(minX, minY, minZ) || !inside(maxX, maxY, maxZ)) {
            throw new IllegalArgumentException(id + ": region '" + name + "' outside blueprint");
        }
        markers.add(new Blueprint.Marker(name, minX, minY, minZ, maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1, data == null ? "" : data));
        return this;
    }

    // ------------------------------------------------------------------ composition

    /**
     * Copies another blueprint into this one.
     *
     * @param turns extra quarter turns applied to the copied blueprint around its own footprint
     *              (the rotated footprint's minimum corner is placed at the given frame position)
     */
    public BlueprintBuilder stamp(Blueprint other, int x, int y, int z, int turns) {
        int t = ((turns % 4) + 4) % 4;
        int osx = other.sizeX();
        int osz = other.sizeZ();
        for (int cy = 0; cy < other.sizeY(); cy++) {
            for (int cz = 0; cz < osz; cz++) {
                for (int cx = 0; cx < osx; cx++) {
                    String k = other.keyAt(cx, cy, cz);
                    if (k == null) {
                        continue;
                    }
                    int[] r = rotCell(t, cx, cz, osx, osz);
                    set(x + r[0], y + cy, z + r[1], rotateKey(k, t));
                }
            }
        }
        for (Blueprint.Marker m : other.markers()) {
            int[] r = rotCell(t, m.x(), m.z(), osx, osz);
            int[] r2 = rotCell(t, m.x() + m.sx() - 1, m.z() + m.sz() - 1, osx, osz);
            int minX = Math.min(r[0], r2[0]);
            int maxX = Math.max(r[0], r2[0]);
            int minZ = Math.min(r[1], r2[1]);
            int maxZ = Math.max(r[1], r2[1]);
            String name = uniqueName(other.id() + "." + m.name());
            if (m.isRegion()) {
                region(name, x + minX, y + m.y(), z + minZ, x + maxX, y + m.y() + m.sy() - 1, z + maxZ, m.data());
            } else {
                marker(name, x + r[0], y + m.y(), z + r[1], rotateData(m.data(), t));
            }
        }
        return this;
    }

    public BlueprintBuilder stamp(Blueprint other, int x, int y, int z) {
        return stamp(other, x, y, z, 0);
    }

    /** Marker names are unique inside a blueprint: later duplicates get a {@code #n} suffix. */
    private String uniqueName(String base) {
        String name = base;
        int n = 2;
        while (hasMarker(name)) {
            name = base + "#" + n++;
        }
        return name;
    }

    private boolean hasMarker(String name) {
        for (Blueprint.Marker m : markers) {
            if (m.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** Footprint size of a blueprint after {@code turns} quarter turns. */
    public static int[] rotatedSize(Blueprint bp, int turns) {
        return (turns & 1) == 0 ? new int[] {bp.sizeX(), bp.sizeZ()} : new int[] {bp.sizeZ(), bp.sizeX()};
    }

    static int[] rotCell(int turns, int lx, int lz, int sx, int sz) {
        return switch (((turns % 4) + 4) % 4) {
            case 1 -> new int[] {sz - 1 - lz, lx};
            case 2 -> new int[] {sx - 1 - lx, sz - 1 - lz};
            case 3 -> new int[] {lz, sx - 1 - lx};
            default -> new int[] {lx, lz};
        };
    }

    // ------------------------------------------------------------------ output

    public Blueprint build() {
        return new Blueprint(id, sx, sy, sz, palette.toArray(new String[0]), cells.clone(), markers);
    }

    // ------------------------------------------------------------------ internals

    private boolean inside(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < sx && y < sy && z < sz;
    }

    private int idx(int x, int y, int z) {
        return (y * sz + z) * sx + x;
    }

    private void put(int x, int y, int z, String key) {
        if (!inside(x, y, z)) {
            if (clip) {
                return;
            }
            throw new IllegalArgumentException(id + ": write outside blueprint (" + x + "," + y + "," + z
                    + ") size " + sx + "x" + sy + "x" + sz + " key " + key);
        }
        Integer i = paletteIndex.get(key);
        if (i == null) {
            i = palette.size();
            if (i >= Short.MAX_VALUE) {
                throw new IllegalStateException(id + ": palette overflow");
            }
            palette.add(key);
            paletteIndex.put(key, i);
        }
        cells[idx(x, y, z)] = (short) (int) i;
    }

    @FunctionalInterface
    private interface Cell {
        void accept(int x, int y, int z);
    }

    private void forBox(int x1, int y1, int z1, int x2, int y2, int z2, Cell c) {
        int ax = Math.min(x1, x2);
        int bx = Math.max(x1, x2);
        int ay = Math.min(y1, y2);
        int by = Math.max(y1, y2);
        int az = Math.min(z1, z2);
        int bz = Math.max(z1, z2);
        for (int y = ay; y <= by; y++) {
            for (int z = az; z <= bz; z++) {
                for (int x = ax; x <= bx; x++) {
                    c.accept(x, y, z);
                }
            }
        }
    }

    // ------------------------------------------------------------------ key rotation

    /**
     * Rotates direction-dependent properties of a block key by quarter turns clockwise.
     * Handles {@code facing}, {@code axis}, the four connection sides of fences/panes/walls and
     * the 16-step {@code rotation} of signs and banners.
     */
    public static String rotateKey(String key, int turns) {
        int t = ((turns % 4) + 4) % 4;
        if (t == 0 || key == null) {
            return key;
        }
        int bar = key.indexOf('|');
        if (bar >= 0) {
            // a block of a furniture mod and its stand-in: both are turned
            return rotateKey(key.substring(0, bar), turns) + "|" + rotateKey(key.substring(bar + 1), turns);
        }
        int br = key.indexOf('[');
        if (br < 0) {
            return key;
        }
        String block = key.substring(0, br);
        String body = key.substring(br + 1, key.length() - 1);
        Map<String, String> props = new LinkedHashMap<>();
        for (String part : body.split(",")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                props.put(part.substring(0, eq), part.substring(eq + 1));
            }
        }
        Map<String, String> out = new LinkedHashMap<>();
        Map<Dir, String> sides = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : props.entrySet()) {
            String k = e.getKey();
            String v = e.getValue();
            Dir sideDir = Dir.byKey(k);
            if (k.equals("facing")) {
                Dir d = Dir.byKey(v);
                out.put(k, d == null ? v : d.turn(t).key());
            } else if (k.equals("axis")) {
                out.put(k, (t & 1) == 1 ? (v.equals("x") ? "z" : v.equals("z") ? "x" : v) : v);
            } else if (k.equals("shape") && block.startsWith("handcrafted:") && block.endsWith("_table")) {
                out.put(k, rotateTableShape(v, t));
            } else if (k.equals("rotation")) {
                try {
                    out.put(k, String.valueOf(((Integer.parseInt(v) + 4 * t) % 16 + 16) % 16));
                } catch (NumberFormatException ex) {
                    out.put(k, v);
                }
            } else if (sideDir != null) {
                sides.put(sideDir, v);
            } else {
                out.put(k, v);
            }
        }
        for (Map.Entry<Dir, String> e : sides.entrySet()) {
            out.put(e.getKey().turn(t).key(), e.getValue());
        }
        StringBuilder sb = new StringBuilder(block).append('[');
        boolean first = true;
        for (Map.Entry<String, String> e : out.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.append(']').toString();
    }

    private static final Dir[] CANONICAL = {Dir.NORTH, Dir.SOUTH, Dir.EAST, Dir.WEST};

    /**
     * The shape of a table of the mod Handcrafted names the sides of the table that are free or joined
     * ({@code north_east_corner}, {@code east_west_center}, {@code south_side}); turned by quarter turns the words change
     * and the mod wants them in its own order again: north or south first, then east or west.
     */
    static String rotateTableShape(String shape, int turns) {
        List<Dir> dirs = new ArrayList<>();
        List<String> rest = new ArrayList<>();
        for (String word : shape.split("_")) {
            Dir d = Dir.byKey(word);
            if (d == null) {
                rest.add(word);
            } else {
                dirs.add(d.turn(turns));
            }
        }
        StringBuilder sb = new StringBuilder();
        for (Dir c : CANONICAL) {
            if (dirs.contains(c)) {
                sb.append(c.key()).append('_');
            }
        }
        sb.append(String.join("_", rest));
        return sb.toString();
    }

    /** Rotates {@code facing=<dir>} entries inside a marker data string. */
    static String rotateData(String data, int turns) {
        if (data == null || data.isEmpty() || turns % 4 == 0) {
            return data == null ? "" : data;
        }
        StringBuilder sb = new StringBuilder();
        for (String part : data.split(",")) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            if (part.startsWith("facing=")) {
                Dir d = Dir.byKey(part.substring(7));
                sb.append("facing=").append(d == null ? part.substring(7) : d.turn(turns).key());
            } else {
                sb.append(part);
            }
        }
        return sb.toString();
    }
}
