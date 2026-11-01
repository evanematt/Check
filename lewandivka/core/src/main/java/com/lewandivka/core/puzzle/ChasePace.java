package com.lewandivka.core.puzzle;

/**
 * How fast a walking creature really runs, and how fast the Debtor should run.
 *
 * <p>A mob does not move at "attribute times multiplier" blocks per tick. The movement control stores that product as
 * the movement speed <em>and</em> as the forward input, and the walking physics multiply the two, so the pace grows with
 * the square: every tick adds {@code 0.98 * s * s} to the speed ({@code s = attribute * multiplier}), the ground keeps
 * 0.546 of it (block slipperiness 0.6 times 0.91) and the steady state is {@code 0.98 * s * s / (1 - 0.546)} blocks per
 * tick. A player is different: the input is the key press, 1.0, so a player moves with the attribute alone (0.1 gives
 * 4.317 blocks per second).</p>
 *
 * <p>The chase must be fair against exactly these players, so the multiplier of the navigation call is derived from the
 * pace the Debtor should have. A game test lets the real navigation run and compares it with this model.</p>
 */
public final class ChasePace {

    /** Blocks per second of a walking and of a sprinting player. */
    public static final double PLAYER_WALK = 4.317;
    public static final double PLAYER_SPRINT = 5.612;

    /** One pursuer: the Debtor runs about as fast as a walking player, so only sprinting (or his stumbles) closes in. */
    public static final double SOLO = 4.4;
    /** Two or three pursuers: a little faster, the party has to cut off routes instead of just following him. */
    public static final double PARTY = 5.0;

    /** After this long of active pursuit the Debtor starts to tire, so a slow or hungry player can still finish the quest. */
    public static final long TIRED_AFTER_TICKS = 1800;
    /** ... and after this long he is as tired as he gets (70 % of his pace). */
    public static final long TIRED_FULLY_TICKS = 4200;
    public static final double TIRED_FLOOR = 0.7;

    private static final double INPUT = 0.98;
    private static final double GROUND_KEEP = 0.6 * 0.91;

    private ChasePace() {
    }

    /** Steady pace on level ground in blocks per second. */
    public static double blocksPerSecond(double attribute, double multiplier) {
        double s = attribute * multiplier;
        return 20.0 * INPUT * s * s / (1.0 - GROUND_KEEP);
    }

    /** The multiplier of the navigation call that gives a creature with this movement attribute the wanted pace. */
    public static double multiplierFor(double attribute, double blocksPerSecond) {
        return Math.sqrt(blocksPerSecond / 20.0 * (1.0 - GROUND_KEEP) / INPUT) / attribute;
    }

    /** The pace asked for by the number of pursuers, before tiring. */
    public static double pace(int pursuers) {
        return pursuers > 1 ? PARTY : SOLO;
    }

    /** 1.0 while fresh, falling to {@link #TIRED_FLOOR} as the pursuit goes on (measured in ticks of active pursuit). */
    public static double freshness(long pursuitTicks) {
        if (pursuitTicks <= TIRED_AFTER_TICKS) {
            return 1.0;
        }
        double t = Math.min(1.0, (pursuitTicks - TIRED_AFTER_TICKS) / (double) (TIRED_FULLY_TICKS - TIRED_AFTER_TICKS));
        return 1.0 - (1.0 - TIRED_FLOOR) * t;
    }
}
