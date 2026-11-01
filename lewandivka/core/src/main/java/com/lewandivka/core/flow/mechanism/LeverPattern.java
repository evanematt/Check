package com.lewandivka.core.flow.mechanism;

import com.lewandivka.core.flow.FlowEnv;

import java.util.List;

/**
 * Levers that are toggled up or down and checked against a pattern by a confirm console (power point C of garage 13:
 * up, down, up). A wrong confirmation drops every lever back down; nothing else happens.
 */
public final class LeverPattern {

    public enum Result {
        IGNORED, TOGGLED, WRONG, SOLVED
    }

    private final FlowEnv env;
    private final List<String> levers;
    private final boolean[] pattern;
    private final String confirm;
    private final boolean[] up;
    private boolean solved;

    public LeverPattern(FlowEnv env, List<String> levers, boolean[] pattern, String confirm) {
        this.env = env;
        this.levers = List.copyOf(levers);
        this.pattern = pattern.clone();
        this.confirm = confirm;
        this.up = new boolean[levers.size()];
    }

    public Result use(String marker) {
        if (solved) {
            return Result.IGNORED;
        }
        int i = levers.indexOf(marker);
        if (i >= 0) {
            up[i] = !up[i];
            env.station(marker, "powered", String.valueOf(up[i]));
            env.sound(marker, "lever.pull");
            return Result.TOGGLED;
        }
        if (marker.equals(confirm)) {
            for (int k = 0; k < pattern.length; k++) {
                if (up[k] != pattern[k]) {
                    dropAll();
                    env.sound(confirm, "validator.fail");
                    return Result.WRONG;
                }
            }
            solved = true;
            env.station(confirm, "lit", "true");
            env.sound(confirm, "validator.ok");
            return Result.SOLVED;
        }
        return Result.IGNORED;
    }

    public void dropAll() {
        for (int k = 0; k < up.length; k++) {
            up[k] = false;
            env.station(levers.get(k), "powered", "false");
        }
    }

    public boolean leverIsUp(int index) {
        return up[index];
    }

    public boolean solved() {
        return solved;
    }

    public void restoreSolved() {
        solved = true;
        for (int k = 0; k < up.length; k++) {
            up[k] = pattern[k];
            env.station(levers.get(k), "powered", String.valueOf(pattern[k]));
        }
        env.station(confirm, "lit", "true");
    }

    public void reset() {
        solved = false;
        dropAll();
        env.station(confirm, "lit", "false");
    }
}
