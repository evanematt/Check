package com.lewandivka.core.scale;

/**
 * Co-op scaling. The active party size is determined once when an encounter begins
 * ({@code activePartySize}) and every mechanic derives its numbers from it. Nothing in the game
 * ever fails because the party is not exactly three players.
 *
 * <p>Design rules:</p>
 * <ul>
 *   <li>3 players: short simultaneous windows.</li>
 *   <li>2 players: medium windows; one player can naturally cover two stations.</li>
 *   <li>1 player: long windows that are large enough to complete every station sequentially.</li>
 *   <li>Boss health is NOT multiplied by the number of players (sub-linear instead).</li>
 * </ul>
 */
public record PartyScale(int size) {

    public static final int MAX_SIZE = 3;

    public PartyScale {
        size = Math.max(1, Math.min(MAX_SIZE, size));
    }

    public static PartyScale of(int onlinePlayers) {
        return new PartyScale(onlinePlayers);
    }

    public boolean solo() {
        return size == 1;
    }

    /** Boss health factor: 1.00 / 1.40 / 1.75 (not 1 / 2 / 3). */
    public double bossHealthFactor() {
        return switch (size) {
            case 1 -> 1.0;
            case 2 -> 1.4;
            default -> 1.75;
        };
    }

    /** Damage dealt by bosses/adds: slightly gentler for a lone player. */
    public double damageFactor() {
        return switch (size) {
            case 1 -> 0.8;
            case 2 -> 0.92;
            default -> 1.0;
        };
    }

    /**
     * Number of adds for an encounter that is tuned for three players.
     * 3 -> base, 2 -> about 70 %, 1 -> about 45 % (never below 1 when base &gt; 0).
     */
    public int adds(int baseForTrio) {
        if (baseForTrio <= 0) {
            return 0;
        }
        double f = switch (size) {
            case 1 -> 0.45;
            case 2 -> 0.7;
            default -> 1.0;
        };
        return Math.max(1, (int) Math.ceil(baseForTrio * f));
    }

    /**
     * Length of a "press everything within this window" mechanic, in ticks, given the length that
     * is tuned for three players. 3 -> x1, 2 -> x1.8, 1 -> x3.6 (long enough to walk between stations).
     */
    public int windowTicks(int baseTicksForTrio) {
        double f = switch (size) {
            case 1 -> 3.6;
            case 2 -> 1.8;
            default -> 1.0;
        };
        return (int) Math.round(baseTicksForTrio * f);
    }

    /** Timers of "do it before time runs out" mechanics: 3 -> x1, 2 -> x1.35, 1 -> x1.8. */
    public int deadlineTicks(int baseTicksForTrio) {
        double f = switch (size) {
            case 1 -> 1.8;
            case 2 -> 1.35;
            default -> 1.0;
        };
        return (int) Math.round(baseTicksForTrio * f);
    }

    /** How long a boss stays vulnerable (core open, shield down). Longer for smaller parties. */
    public int vulnerableTicks(int baseTicksForTrio) {
        double f = switch (size) {
            case 1 -> 1.25;
            case 2 -> 1.1;
            default -> 1.0;
        };
        return (int) Math.round(baseTicksForTrio * f);
    }

    /** Probability that a fleeing NPC trips/hesitates per check (debtor chase). */
    public double fleeStumbleChance() {
        return switch (size) {
            case 1 -> 0.30;
            case 2 -> 0.14;
            default -> 0.05;
        };
    }
}
