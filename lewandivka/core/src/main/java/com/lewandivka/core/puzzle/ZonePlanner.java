package com.lewandivka.core.puzzle;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Lady Vortex arena quarters. Each quarter has a colour AND a symbol so the information never
 * depends on colour alone. The planner chooses which quarters become dangerous next: one in the
 * first phase, two in the second, never repeating the previous selection and never more than two
 * at once (there is always a safe place to stand).
 */
public final class ZonePlanner {

    public enum Zone {
        RED_CROSS("red", "cross"),
        BLUE_CIRCLE("blue", "circle"),
        YELLOW_TRIANGLE("yellow", "triangle"),
        GREEN_SQUARE("green", "square");

        public final String color;
        public final String symbol;

        Zone(String color, String symbol) {
            this.color = color;
            this.symbol = symbol;
        }

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private final Random rng;
    private List<Zone> last = List.of();

    public ZonePlanner(long seed) {
        this.rng = new Random(seed);
    }

    /** @param count 1 or 2 quarters to flood */
    public List<Zone> next(int count) {
        int n = Math.max(1, Math.min(2, count));
        List<Zone> pick;
        int guard = 0;
        do {
            pick = new ArrayList<>(n);
            List<Zone> pool = new ArrayList<>(List.of(Zone.values()));
            for (int i = 0; i < n; i++) {
                pick.add(pool.remove(rng.nextInt(pool.size())));
            }
        } while (guard++ < 16 && sameSet(pick, last));
        last = pick;
        return pick;
    }

    public void reset() {
        last = List.of();
    }

    private static boolean sameSet(List<Zone> a, List<Zone> b) {
        return a.size() == b.size() && a.containsAll(b);
    }
}
