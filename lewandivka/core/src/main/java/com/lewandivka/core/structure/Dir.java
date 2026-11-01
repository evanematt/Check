package com.lewandivka.core.structure;

import java.util.Locale;

/** Horizontal directions. Minecraft convention: north = -z, east = +x, south = +z, west = -x. */
public enum Dir {
    NORTH(0, 0, -1),
    EAST(1, 0, 0),
    SOUTH(0, 0, 1),
    WEST(-1, 0, 0);

    public final int dx;
    public final int dy;
    public final int dz;

    Dir(int dx, int dy, int dz) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Rotates clockwise (seen from above) by the given number of quarter turns. */
    public Dir turn(int quarterTurns) {
        return values()[((ordinal() + quarterTurns) % 4 + 4) % 4];
    }

    public Dir opposite() {
        return turn(2);
    }

    public Dir left() {
        return turn(-1);
    }

    public Dir right() {
        return turn(1);
    }

    public static Dir byKey(String key) {
        for (Dir d : values()) {
            if (d.key().equals(key)) {
                return d;
            }
        }
        return null;
    }
}
