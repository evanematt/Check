package com.lewandivka.core.world;

/**
 * The springs of Chromandivka. Speeds are in blocks per tick; the level design (the islands of the sky ascent, the
 * shafts of the tower) is built for exactly these numbers under vanilla physics, and {@code LaunchTest} proves it.
 *
 * <ul>
 *   <li>a spring pad throws the rider up (apex about +20 blocks) and, when it has an arrow, along its facing (about
 *       15 blocks of drift);</li>
 *   <li>a spring hatch throws the rider straight up (apex about +31 blocks);</li>
 *   <li>without the Spring Insoles a pad is only an ordinary jump.</li>
 * </ul>
 */
public final class Launch {

    public static final double PAD_UP = 2.0;
    public static final double PAD_DRIFT = 1.4;
    public static final double HATCH_UP = 2.6;
    public static final double HOP = 0.42;
    /** The glider (simulated by the client, validated by the server): horizontal target speed, how fast it is reached, sink speed. */
    public static final double GLIDE_SPEED = 0.42;
    public static final double GLIDE_BLEND = 0.12;
    public static final double GLIDE_SINK = 0.09;

    private static final double GRAVITY = 0.08;
    private static final double DRAG_Y = 0.98;
    private static final double DRAG_XZ = 0.91;

    private Launch() {
    }

    /** Highest point of a vertical launch, in blocks above the launch height. */
    public static double apex(double up) {
        double y = 0;
        double v = up;
        double best = 0;
        for (int t = 0; t < 400 && !(v < 0 && y < best - 1.0E-6); t++) {
            y += v;
            best = Math.max(best, y);
            v = (v - GRAVITY) * DRAG_Y;
        }
        return best;
    }

    /**
     * Horizontal distance covered when the rider comes down through {@code heightDifference} blocks above the launch
     * height (a negative difference is a landing below it); NaN when that height is never reached on the way down.
     */
    public static double reach(double up, double drift, double heightDifference) {
        double y = 0;
        double x = 0;
        double vy = up;
        double vx = drift;
        for (int t = 0; t < 600; t++) {
            y += vy;
            x += vx;
            vy = (vy - GRAVITY) * DRAG_Y;
            vx *= DRAG_XZ;
            if (vy < 0 && y <= heightDifference) {
                return x;
            }
        }
        return Double.NaN;
    }

    /** Ticks of gliding needed to cover a horizontal distance from a start speed, or -1 when the energy would not last. */
    public static int glideTicks(double distance, double startSpeed, int maxTicks) {
        double v = startSpeed;
        double covered = 0;
        for (int t = 1; t <= maxTicks; t++) {
            v += (GLIDE_SPEED - v) * GLIDE_BLEND;
            covered += v;
            if (covered >= distance) {
                return t;
            }
        }
        return -1;
    }

    /** Ticks until the rider comes down through that height, or -1. */
    public static int flightTicks(double up, double heightDifference) {
        double y = 0;
        double vy = up;
        for (int t = 1; t < 600; t++) {
            y += vy;
            vy = (vy - GRAVITY) * DRAG_Y;
            if (vy < 0 && y <= heightDifference) {
                return t;
            }
        }
        return -1;
    }
}
