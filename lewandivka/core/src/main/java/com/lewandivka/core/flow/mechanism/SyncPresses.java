package com.lewandivka.core.flow.mechanism;

import com.lewandivka.core.flow.FlowEnv;
import com.lewandivka.core.scale.SyncGroup;

import java.util.List;

/**
 * "Press everything within a window" mechanic with party scaling: a station stays lit for a window that is short
 * for three players and long enough for one player to walk from station to station.
 */
public final class SyncPresses {

    private final FlowEnv env;
    private final List<String> stations;
    private final String property;
    private final int baseWindowTicks;
    private SyncGroup group;
    private boolean solved;

    public SyncPresses(FlowEnv env, List<String> stations, String property, int baseWindowTicks) {
        this.env = env;
        this.stations = List.copyOf(stations);
        this.property = property;
        this.baseWindowTicks = baseWindowTicks;
        this.group = newGroup();
    }

    private SyncGroup newGroup() {
        return new SyncGroup(stations.size(), env.party().windowTicks(baseWindowTicks));
    }

    public int windowTicks() {
        return group.windowTicks();
    }

    /** Re-reads the party size (called when an encounter begins). */
    public void rescale() {
        group.setWindowTicks(env.party().windowTicks(baseWindowTicks));
    }

    /** @return true when this press completed the group */
    public boolean use(String marker) {
        int i = stations.indexOf(marker);
        if (i < 0 || solved) {
            return false;
        }
        boolean done = group.activate(i, env.now());
        env.station(marker, property, "true");
        env.sound(marker, "lever.pull");
        if (done) {
            solved = true;
            env.say(null, "hud.lewandivka.sync", stations.size(), stations.size());
            return true;
        }
        env.say(null, "hud.lewandivka.sync", group.activeCount(env.now()), stations.size());
        return false;
    }

    /** Switches stations whose window ran out off again. */
    public void tick() {
        if (solved) {
            return;
        }
        long now = env.now();
        for (int i = 0; i < stations.size(); i++) {
            if (!group.isLit(i, now)) {
                env.station(stations.get(i), property, "false");
            }
        }
    }

    public boolean solved() {
        return solved;
    }

    public void restoreSolved() {
        solved = true;
        for (String s : stations) {
            env.station(s, property, "true");
        }
    }

    public void reset() {
        solved = false;
        group = newGroup();
        for (String s : stations) {
            env.station(s, property, "false");
        }
    }
}
