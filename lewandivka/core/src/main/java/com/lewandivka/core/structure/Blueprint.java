package com.lewandivka.core.structure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A finished, immutable voxel structure. Cells hold indexes into a palette of block keys such as
 * {@code minecraft:stone_bricks} or {@code minecraft:oak_stairs[facing=north,half=bottom]}.
 * Index 0 means "untouched": whatever the terrain generator produced stays.
 *
 * <p>Blueprints know nothing about Minecraft classes, so they can be built, validated and unit
 * tested anywhere. The game side only resolves palette keys to block states and pastes the part
 * that intersects the chunk being generated, which makes every structure fully deterministic.</p>
 */
public final class Blueprint {

    /**
     * A named point or region. Regions use {@code sx,sy,sz > 1}; all coordinates are blueprint-local.
     *
     * @param data free-form payload, e.g. {@code "facing=north"} or an encounter label
     */
    public record Marker(String name, int x, int y, int z, int sx, int sy, int sz, String data) {
        public boolean isRegion() {
            return sx > 1 || sy > 1 || sz > 1;
        }
    }

    public static final int UNTOUCHED = 0;

    private final String id;
    private final int sx;
    private final int sy;
    private final int sz;
    private final String[] palette;
    private final short[] cells;
    private final List<Marker> markers;

    Blueprint(String id, int sx, int sy, int sz, String[] palette, short[] cells, List<Marker> markers) {
        this.id = id;
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.palette = palette;
        this.cells = cells;
        this.markers = Collections.unmodifiableList(new ArrayList<>(markers));
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

    public boolean inBounds(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < sx && y < sy && z < sz;
    }

    /** Palette index at a cell; 0 when untouched or out of bounds. */
    public int rawAt(int x, int y, int z) {
        return inBounds(x, y, z) ? cells[(y * sz + z) * sx + x] : UNTOUCHED;
    }

    /** Block key at a cell, or null when untouched. */
    public String keyAt(int x, int y, int z) {
        int i = rawAt(x, y, z);
        return i == UNTOUCHED ? null : palette[i];
    }

    public String paletteKey(int index) {
        return palette[index];
    }

    public int paletteSize() {
        return palette.length;
    }

    public List<String> paletteKeys() {
        List<String> keys = new ArrayList<>(palette.length - 1);
        for (int i = 1; i < palette.length; i++) {
            keys.add(palette[i]);
        }
        return Collections.unmodifiableList(keys);
    }

    public List<Marker> markers() {
        return markers;
    }

    public Marker marker(String name) {
        for (Marker m : markers) {
            if (m.name().equals(name)) {
                return m;
            }
        }
        return null;
    }

    public List<Marker> markersWithPrefix(String prefix) {
        List<Marker> out = new ArrayList<>();
        for (Marker m : markers) {
            if (m.name().startsWith(prefix)) {
                out.add(m);
            }
        }
        return out;
    }

    public int nonEmptyCells() {
        int n = 0;
        for (short c : cells) {
            if (c != UNTOUCHED) {
                n++;
            }
        }
        return n;
    }

    /** Number of cells that hold exactly this key. */
    public int count(String key) {
        int n = 0;
        for (int i = 1; i < palette.length; i++) {
            if (palette[i].equals(key)) {
                for (short c : cells) {
                    if (c == i) {
                        n++;
                    }
                }
            }
        }
        return n;
    }
}
