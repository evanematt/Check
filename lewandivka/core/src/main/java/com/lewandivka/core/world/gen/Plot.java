package com.lewandivka.core.world.gen;

import com.lewandivka.core.structure.Dir;

/**
 * A rectangular area with axes of its own: local {@code (a, b)} is mapped to the building's {@code (x, z)}. A flat on the left of a
 * stairwell and its mirror image on the right are described by one template, only the plot differs.
 *
 * <p>Local directions are written as characters: {@code >} is +a, {@code <} is -a, {@code v} is +b and {@code ^} is -b.</p>
 */
final class Plot {

    final int originX;
    final int originZ;
    private final int ax;
    private final int az;
    private final int bx;
    private final int bz;

    Plot(int originX, int originZ, Dir along, Dir across) {
        if (along.dx * across.dx + along.dz * across.dz != 0) {
            throw new IllegalArgumentException("the axes of a plot are perpendicular");
        }
        this.originX = originX;
        this.originZ = originZ;
        this.ax = along.dx;
        this.az = along.dz;
        this.bx = across.dx;
        this.bz = across.dz;
    }

    int x(int a, int b) {
        return originX + a * ax + b * bx;
    }

    int z(int a, int b) {
        return originZ + a * az + b * bz;
    }

    /** The direction in the building that a local direction character points to. */
    Dir dir(char c) {
        return switch (c) {
            case '>' -> dirOf(ax, az);
            case '<' -> dirOf(-ax, -az);
            case 'v' -> dirOf(bx, bz);
            case '^' -> dirOf(-bx, -bz);
            default -> throw new IllegalArgumentException("not a direction: " + c);
        };
    }

    static char opposite(char c) {
        return switch (c) {
            case '>' -> '<';
            case '<' -> '>';
            case 'v' -> '^';
            case '^' -> 'v';
            default -> throw new IllegalArgumentException("not a direction: " + c);
        };
    }

    static int da(char c) {
        return c == '>' ? 1 : c == '<' ? -1 : 0;
    }

    static int db(char c) {
        return c == 'v' ? 1 : c == '^' ? -1 : 0;
    }

    private static Dir dirOf(int dx, int dz) {
        for (Dir d : Dir.values()) {
            if (d.dx == dx && d.dz == dz) {
                return d;
            }
        }
        throw new IllegalArgumentException("no direction for " + dx + "," + dz);
    }
}
