package com.lewandivka.core.scale;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * "Hold every station at the same time" mechanic: levers, lifts, validators, composters, pumps ...
 *
 * <p>Pressing a station keeps it lit for {@code windowTicks}. The group is satisfied at the moment
 * every station is lit simultaneously. The window comes from {@link PartyScale#windowTicks(int)}:
 * short for three players (everybody presses almost together), long for one player (walk from
 * station to station). The group itself never cares how many players there are.</p>
 */
public final class SyncGroup {

    private final int stations;
    private int windowTicks;
    private final long[] litUntil;
    private boolean satisfied;

    public SyncGroup(int stations, int windowTicks) {
        if (stations < 1) {
            throw new IllegalArgumentException("stations must be >= 1");
        }
        this.stations = stations;
        this.windowTicks = Math.max(1, windowTicks);
        this.litUntil = new long[stations];
        Arrays.fill(litUntil, Long.MIN_VALUE);
    }

    public int stations() {
        return stations;
    }

    public int windowTicks() {
        return windowTicks;
    }

    /** Changes the window (e.g. when the party size changes mid-encounter). Already lit stations keep their deadline. */
    public void setWindowTicks(int ticks) {
        this.windowTicks = Math.max(1, ticks);
    }

    /**
     * Activates a station.
     *
     * @return true when this press completed the group (all stations lit at {@code now})
     */
    public boolean activate(int station, long now) {
        if (station < 0 || station >= stations) {
            return false;
        }
        litUntil[station] = now + windowTicks;
        if (!satisfied && activeCount(now) == stations) {
            satisfied = true;
            return true;
        }
        return false;
    }

    public boolean isLit(int station, long now) {
        return station >= 0 && station < stations && litUntil[station] > now;
    }

    public int activeCount(long now) {
        int n = 0;
        for (long u : litUntil) {
            if (u > now) {
                n++;
            }
        }
        return n;
    }

    /** Ticks until the station goes dark again (0 when dark). */
    public long remaining(int station, long now) {
        if (station < 0 || station >= stations) {
            return 0;
        }
        return Math.max(0, litUntil[station] - now);
    }

    /** Stations that just went dark, useful for feedback ("that lever released"). Does not mutate state. */
    public List<Integer> lit(long now) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < stations; i++) {
            if (isLit(i, now)) {
                out.add(i);
            }
        }
        return out;
    }

    public boolean satisfied() {
        return satisfied;
    }

    /** Clears every station and the latched success. */
    public void reset() {
        Arrays.fill(litUntil, Long.MIN_VALUE);
        satisfied = false;
    }
}
