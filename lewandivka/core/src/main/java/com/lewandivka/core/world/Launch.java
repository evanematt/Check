package com.lewandivka.core.world;

/**
 * The springs of Chromandivka. Speeds are in blocks per tick; the level design (the islands of the sky ascent, the
 * shafts of the tower) is built for exactly these numbers under vanilla physics, and {@code LaunchTest} proves it.
 *
 * <ul>
 *   <li>a spring pad throws the rider up (apex about +27 blocks) and, when it has an arrow, along its facing (the rider
 *       comes down about 12.5 blocks further, see {@link #landing});</li>
 *   <li>a spring hatch throws the rider straight up (apex about +31 blocks);</li>
 *   <li>without the Spring Insoles a pad is only an ordinary jump.</li>
 * </ul>
 *
 * <p>Why a strong launch: the rider has to pass the edge of the next island while still rising, so the horizontal speed
 * must not carry him under the island before he is above its top, and has to bring him down on it afterwards. The
 * higher the throw, the earlier he is above the top and the more room there is between the two ({@link #clearance} and
 * {@link #landing}); the flight is simulated tick by tick with {@link FlightSim}.</p>
 */
public final class Launch {

    public static final double PAD_UP = 2.4;
    public static final double PAD_DRIFT = 1.8;
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
     * height, with the air friction from the very first tick (a rider who is already flying); NaN when that height is
     * never reached on the way down. A rider who starts standing on a pad is {@link #landing}.
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

    private static final FlightSim.Solid NOTHING = (x, y, z) -> false;

    /**
     * Where the rider of a pad comes down: the horizontal distance from the pad to the place where he falls through
     * {@code heightDifference} blocks above the pad (he starts standing, so the first tick is slowed by the ground).
     */
    public static double landing(double up, double drift, double heightDifference) {
        FlightSim.Flight f = FlightSim.fly(NOTHING, 0, 0, 0, drift, up, 0, FlightSim.WIDTH, FlightSim.HEIGHT, 300);
        for (int i = 1; i < f.path.size(); i++) {
            if (f.path.get(i)[1] <= heightDifference && f.path.get(i)[1] < f.path.get(i - 1)[1]) {
                return f.path.get(i)[0];
            }
        }
        return Double.NaN;
    }

    /**
     * How far the east face of the rider has travelled when he first rises through {@code heightDifference}: everything
     * of an island that is nearer than that and higher than the pad is in his way, so the edge of the island's top
     * must be further away.
     */
    public static double clearance(double up, double drift, double heightDifference) {
        FlightSim.Flight f = FlightSim.fly(NOTHING, 0, 0, 0, drift, up, 0, FlightSim.WIDTH, FlightSim.HEIGHT, 300);
        for (int i = 1; i < f.path.size(); i++) {
            if (f.path.get(i)[1] >= heightDifference) {
                return f.path.get(i)[0] + FlightSim.WIDTH / 2.0;
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
