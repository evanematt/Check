package com.lewandivka.core.world;

/**
 * Small deterministic hash + value-noise toolkit. Everything the world generator needs is a pure
 * function of (seed, coordinates), so any chunk can be generated in any order, on any thread, and
 * always looks the same.
 */
public final class Noise {

    private Noise() {
    }

    /** SplitMix64 finaliser. */
    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public static long hash(long seed, int x, int z) {
        return mix(seed * 0x9E3779B97F4A7C15L + x * 0xC2B2AE3D27D4EB4FL + z * 0x165667B19E3779F9L);
    }

    public static long hash(long seed, int x, int y, int z) {
        return mix(hash(seed, x, z) + y * 0x27D4EB2F165667C5L);
    }

    /** Deterministic integer in [0, bound) derived from the position (never negative). */
    public static int range(long seed, int x, int z, int bound) {
        return (int) Math.floorMod(hash(seed, x, z), (long) bound);
    }

    public static int range(long seed, int x, int y, int z, int bound) {
        return (int) Math.floorMod(hash(seed, x, y, z), (long) bound);
    }

    /** Uniform in [0, 1). */
    public static double hash01(long seed, int x, int z) {
        return (hash(seed, x, z) >>> 11) * (1.0 / (1L << 53));
    }

    public static double hash01(long seed, int x, int y, int z) {
        return (hash(seed, x, y, z) >>> 11) * (1.0 / (1L << 53));
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    public static double smoothstep(double edge0, double edge1, double x) {
        double t = clamp((x - edge0) / (edge1 - edge0), 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** 2D value noise in [0, 1]. */
    public static double value2(long seed, double x, double z) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double fx = smooth(x - x0);
        double fz = smooth(z - z0);
        double a = hash01(seed, x0, z0);
        double b = hash01(seed, x0 + 1, z0);
        double c = hash01(seed, x0, z0 + 1);
        double d = hash01(seed, x0 + 1, z0 + 1);
        return lerp(lerp(a, b, fx), lerp(c, d, fx), fz);
    }

    /** Fractal sum of value noise in [0, 1]. */
    public static double fbm2(long seed, double x, double z, int octaves) {
        double sum = 0;
        double amp = 1;
        double norm = 0;
        double f = 1;
        for (int i = 0; i < octaves; i++) {
            sum += value2(seed + i * 101L, x * f, z * f) * amp;
            norm += amp;
            amp *= 0.5;
            f *= 2;
        }
        return sum / norm;
    }

    /** 3D value noise in [0, 1] (floating islands, cave pockets). */
    public static double value3(long seed, double x, double y, double z) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int z0 = (int) Math.floor(z);
        double fx = smooth(x - x0);
        double fy = smooth(y - y0);
        double fz = smooth(z - z0);
        double c000 = hash01(seed, x0, y0, z0);
        double c100 = hash01(seed, x0 + 1, y0, z0);
        double c010 = hash01(seed, x0, y0 + 1, z0);
        double c110 = hash01(seed, x0 + 1, y0 + 1, z0);
        double c001 = hash01(seed, x0, y0, z0 + 1);
        double c101 = hash01(seed, x0 + 1, y0, z0 + 1);
        double c011 = hash01(seed, x0, y0 + 1, z0 + 1);
        double c111 = hash01(seed, x0 + 1, y0 + 1, z0 + 1);
        double x00 = lerp(c000, c100, fx);
        double x10 = lerp(c010, c110, fx);
        double x01 = lerp(c001, c101, fx);
        double x11 = lerp(c011, c111, fx);
        return lerp(lerp(x00, x10, fy), lerp(x01, x11, fy), fz);
    }
}
