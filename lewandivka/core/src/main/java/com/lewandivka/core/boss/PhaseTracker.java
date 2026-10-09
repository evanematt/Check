package com.lewandivka.core.boss;

import java.util.Arrays;

/**
 * Monotonic boss phase tracking from the health fraction.
 *
 * <p>Thresholds are descending health fractions at which the next phase starts, e.g.
 * {@code {0.70, 0.35, 0.05}} gives phases 0 (above 70 %), 1, 2 and 3. A phase never goes back
 * when the boss heals; skipping several thresholds in one hit advances through all of them one by
 * one so scripted transitions are never skipped.</p>
 */
public final class PhaseTracker {

    private final double[] thresholds;
    private int phase;

    public PhaseTracker(double... thresholds) {
        this.thresholds = thresholds.clone();
        for (int i = 1; i < this.thresholds.length; i++) {
            if (this.thresholds[i] >= this.thresholds[i - 1]) {
                throw new IllegalArgumentException("thresholds must be strictly descending: " + Arrays.toString(thresholds));
            }
        }
    }

    public int phase() {
        return phase;
    }

    public int phaseCount() {
        return thresholds.length + 1;
    }

    public boolean isLastPhase() {
        return phase == thresholds.length;
    }

    /**
     * @return the next phase number if the fraction crossed the next threshold, otherwise -1.
     *         Call repeatedly (until -1) to process every crossed threshold.
     */
    public int advanceIfNeeded(double healthFraction) {
        if (phase < thresholds.length && healthFraction <= thresholds[phase]) {
            phase++;
            return phase;
        }
        return -1;
    }

    /** Restores a phase after a reload (boss reset logic uses {@link #reset()} instead). */
    public void setPhase(int phase) {
        this.phase = Math.max(0, Math.min(thresholds.length, phase));
    }

    public void reset() {
        phase = 0;
    }

    /** Phase the given fraction belongs to, without mutating the tracker. */
    public int phaseFor(double healthFraction) {
        int p = 0;
        while (p < thresholds.length && healthFraction <= thresholds[p]) {
            p++;
        }
        return p;
    }
}
