package com.lewandivka.core.world;

import java.util.ArrayList;
import java.util.List;

/**
 * The movement of a walking player without any input, enough to prove that a launch lands where the level says it does.
 * It mirrors the vanilla tick: the velocity moves the 0.6 x 1.8 box (resolved against unit cubes, vertically first and then
 * along the larger horizontal move), the speed that is left is slowed down (0.91 in the air, 0.546 when the player was
 * standing at the start of the tick: the first tick of a launch still counts as standing) and gravity pulls (0.08, then 0.98).
 * Step-up assistance is ignored, which makes the simulation stricter than the game.
 */
public final class FlightSim {

    /** Whether the unit cube with this corner is a solid block. */
    public interface Solid {
        boolean at(int x, int y, int z);
    }

    public static final double GRAVITY = 0.08;
    public static final double DRAG_Y = 0.98;
    public static final double AIR = 0.91;
    public static final double GROUND = 0.6 * 0.91;
    public static final double WIDTH = 0.6;
    public static final double HEIGHT = 1.8;

    private static final double EPS = 1.0E-7;

    /** The result of a flight. */
    public static final class Flight {
        /** x, y, z of the feet after every tick (index 0 is the start). */
        public final List<double[]> path = new ArrayList<>();
        /** The first tick that ran into a wall (or -1) and where. */
        public int sideTick = -1;
        public double[] sideAt;
        /** The first tick that hit a ceiling (or -1) and where. */
        public int ceilingTick = -1;
        public double[] ceilingAt;
        /** Where and when the flight ended on the ground (landed is false when it never did within the time). */
        public boolean landed;
        public int landTick = -1;
        public double[] landAt;
        /** Highest point of the feet. */
        public double apex;

        public double[] last() {
            return path.get(path.size() - 1);
        }

        /** Whether the flight went up and came down without touching anything on the way. */
        public boolean clean() {
            return landed && sideTick < 0 && ceilingTick < 0;
        }
    }

    private FlightSim() {
    }

    /**
     * Flies from a standing start.
     *
     * @param width  width of the box (0.6 for a player; a wider box shows how much room there is to spare)
     * @param height height of the box (1.8)
     */
    public static Flight fly(Solid world, double x, double y, double z, double vx, double vy, double vz, double width, double height, int maxTicks) {
        return fly(world, x, y, z, vx, vy, vz, width, height, maxTicks, null);
    }

    /** A push on the rider every other tick (the wind regions of the game, see ZoneServices): the answer replaces the velocity. */
    public interface Wind {
        double[] adjust(double x, double y, double z, double vx, double vy, double vz);
    }

    /** The same flight with a wind that acts at the start of every second tick (or {@code null}). */
    public static Flight fly(Solid world, double x, double y, double z, double vx, double vy, double vz, double width, double height, int maxTicks, Wind wind) {
        return fly(world, x, y, z, vx, vy, vz, width, height, maxTicks, wind, 0);
    }

    /** The wind acts on the ticks with {@code tick % 2 == parity} (the client applies it every second tick; which one depends on the moment of the throw). */
    public static Flight fly(Solid world, double x, double y, double z, double vx, double vy, double vz, double width, double height, int maxTicks, Wind wind, int parity) {
        return fly(world, x, y, z, vx, vy, vz, width, height, maxTicks, wind, parity, 0.0);
    }

    /** The speed a player adds in the air by holding a key (vanilla: 0.02 per tick, along the way he faces; 0 for a rider who lets go). */
    public static final double AIR_STEERING = 0.02;

    /**
     * The same flight of a rider who holds a movement key: {@code steerX} is added to the horizontal speed along x at the start of
     * every tick in which he is in the air (the game does it before the move, and the air slows the sum down afterwards).
     */
    public static Flight fly(Solid world, double x, double y, double z, double vx, double vy, double vz, double width, double height, int maxTicks, Wind wind, int parity,
                             double steerX) {
        Flight f = new Flight();
        f.path.add(new double[] {x, y, z});
        f.apex = y;
        boolean onGround = true;
        double half = width / 2.0;
        for (int tick = 1; tick <= maxTicks; tick++) {
            if (wind != null && tick % 2 == parity) {
                double[] v = wind.adjust(x, y, z, vx, vy, vz);
                if (v != null) {
                    vx = v[0];
                    vy = v[1];
                    vz = v[2];
                }
            }
            if (!onGround) {
                vx += steerX;
            }
            double friction = onGround ? GROUND : AIR;
            double dx = vx;
            double dy = vy;
            double dz = vz;
            // vertical first, then the larger horizontal move
            double ry = clampY(world, x, y, z, half, height, dy);
            y += ry;
            double rx;
            double rz;
            if (Math.abs(dx) >= Math.abs(dz)) {
                rx = clampX(world, x, y, z, half, height, dx);
                x += rx;
                rz = clampZ(world, x, y, z, half, height, dz);
                z += rz;
            } else {
                rz = clampZ(world, x, y, z, half, height, dz);
                z += rz;
                rx = clampX(world, x, y, z, half, height, dx);
                x += rx;
            }
            boolean hitY = Math.abs(ry - dy) > 1.0E-9;
            boolean hitX = Math.abs(rx - dx) > 1.0E-9;
            boolean hitZ = Math.abs(rz - dz) > 1.0E-9;
            if ((hitX || hitZ) && f.sideTick < 0) {
                f.sideTick = tick;
                f.sideAt = new double[] {x, y, z};
            }
            if (hitY && dy > 0 && f.ceilingTick < 0) {
                f.ceilingTick = tick;
                f.ceilingAt = new double[] {x, y, z};
            }
            if (hitX) {
                vx = 0;
            }
            if (hitZ) {
                vz = 0;
            }
            if (hitY) {
                vy = 0;
            }
            onGround = hitY && dy < 0;
            f.path.add(new double[] {x, y, z});
            f.apex = Math.max(f.apex, y);
            if (onGround) {
                f.landed = true;
                f.landTick = tick;
                f.landAt = new double[] {x, y, z};
                break;
            }
            vy = (vy - GRAVITY) * DRAG_Y;
            vx *= friction;
            vz *= friction;
        }
        return f;
    }

    // ------------------------------------------------------------------ collision of the box with unit cubes

    private static int lo(double v) {
        return (int) Math.floor(v + EPS);
    }

    private static int hi(double v) {
        return (int) Math.floor(v - EPS);
    }

    private static double clampY(Solid w, double x, double y, double z, double half, double height, double d) {
        if (d == 0) {
            return 0;
        }
        int x0 = lo(x - half);
        int x1 = hi(x + half);
        int z0 = lo(z - half);
        int z1 = hi(z + half);
        if (d > 0) {
            double top = y + height;
            for (int yi = (int) Math.floor(top - EPS); yi <= (int) Math.floor(top + d); yi++) {
                if (yi + 1 <= top + EPS) {
                    continue;
                }
                if (any(w, x0, x1, yi, yi, z0, z1)) {
                    return Math.max(0, Math.min(d, yi - top));
                }
            }
            return d;
        }
        for (int yi = (int) Math.floor(y - EPS); yi >= (int) Math.floor(y + d); yi--) {
            if (any(w, x0, x1, yi, yi, z0, z1)) {
                return Math.min(0, Math.max(d, yi + 1 - y));
            }
        }
        return d;
    }

    private static double clampX(Solid w, double x, double y, double z, double half, double height, double d) {
        if (d == 0) {
            return 0;
        }
        int y0 = lo(y);
        int y1 = hi(y + height);
        int z0 = lo(z - half);
        int z1 = hi(z + half);
        if (d > 0) {
            double face = x + half;
            for (int xi = (int) Math.floor(face - EPS); xi <= (int) Math.floor(face + d); xi++) {
                if (xi + 1 <= face + EPS) {
                    continue;
                }
                if (any(w, xi, xi, y0, y1, z0, z1)) {
                    return Math.max(0, Math.min(d, xi - face));
                }
            }
            return d;
        }
        double face = x - half;
        for (int xi = (int) Math.floor(face - EPS); xi >= (int) Math.floor(face + d); xi--) {
            if (any(w, xi, xi, y0, y1, z0, z1)) {
                return Math.min(0, Math.max(d, xi + 1 - face));
            }
        }
        return d;
    }

    private static double clampZ(Solid w, double x, double y, double z, double half, double height, double d) {
        if (d == 0) {
            return 0;
        }
        int y0 = lo(y);
        int y1 = hi(y + height);
        int x0 = lo(x - half);
        int x1 = hi(x + half);
        if (d > 0) {
            double face = z + half;
            for (int zi = (int) Math.floor(face - EPS); zi <= (int) Math.floor(face + d); zi++) {
                if (zi + 1 <= face + EPS) {
                    continue;
                }
                if (any(w, x0, x1, y0, y1, zi, zi)) {
                    return Math.max(0, Math.min(d, zi - face));
                }
            }
            return d;
        }
        double face = z - half;
        for (int zi = (int) Math.floor(face - EPS); zi >= (int) Math.floor(face + d); zi--) {
            if (any(w, x0, x1, y0, y1, zi, zi)) {
                return Math.min(0, Math.max(d, zi + 1 - face));
            }
        }
        return d;
    }

    private static boolean any(Solid w, int x0, int x1, int y0, int y1, int z0, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    if (w.at(x, y, z)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
