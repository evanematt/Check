package com.lewandivka.core.flow.mechanism;

import com.lewandivka.core.flow.FlowEnv;

import java.util.List;

/**
 * Stations that must be used in a given order (the four breakers of garage 9: 3, 1, 4, 2). A wrong press switches
 * every station off again; nothing is lost and nobody is hurt.
 */
public final class OrderedSequence {

    public enum Result {
        IGNORED, CORRECT, WRONG, SOLVED
    }

    private final FlowEnv env;
    private final List<String> stations;
    private final int[] order;
    private final String property;
    private int progress;
    private boolean solved;

    /**
     * @param stations marker names in numeric order (breaker_1 .. breaker_4)
     * @param order    1-based station numbers in the order they have to be pressed
     * @param property block-state property that shows the station as switched on ({@code lit})
     */
    public OrderedSequence(FlowEnv env, List<String> stations, int[] order, String property) {
        this.env = env;
        this.stations = List.copyOf(stations);
        this.order = order.clone();
        this.property = property;
    }

    public Result use(String marker) {
        int index = stations.indexOf(marker);
        if (index < 0 || solved) {
            return Result.IGNORED;
        }
        if (index + 1 == order[progress]) {
            progress++;
            env.station(marker, property, "true");
            env.sound(marker, "garage.clack");
            if (progress == order.length) {
                solved = true;
                return Result.SOLVED;
            }
            return Result.CORRECT;
        }
        switchAllOff();
        env.sound(marker, "validator.fail");
        return Result.WRONG;
    }

    public void switchAllOff() {
        progress = 0;
        for (String s : stations) {
            env.station(s, property, "false");
        }
    }

    public int progress() {
        return progress;
    }

    public boolean solved() {
        return solved;
    }

    /** Marks the puzzle solved (restoring saved progress) and lights every station. */
    public void restoreSolved() {
        solved = true;
        progress = order.length;
        for (String s : stations) {
            env.station(s, property, "true");
        }
    }

    public void reset() {
        solved = false;
        switchAllOff();
    }
}
