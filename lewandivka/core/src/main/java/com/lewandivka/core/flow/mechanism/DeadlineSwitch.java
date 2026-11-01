package com.lewandivka.core.flow.mechanism;

import com.lewandivka.core.flow.FlowEnv;

/**
 * A lever that opens a gate for a limited time (the operator's lever in garage 13). The time comes from the party size,
 * so a lone player has long enough to run to the gate and a trio has to coordinate.
 */
public final class DeadlineSwitch {

    private final FlowEnv env;
    private final String station;
    private final String gate;
    private final int baseTicks;
    private long openUntil = -1;

    public DeadlineSwitch(FlowEnv env, String station, String gate, int baseTicks) {
        this.env = env;
        this.station = station;
        this.gate = gate;
        this.baseTicks = baseTicks;
    }

    public boolean use(String marker) {
        if (!marker.equals(station)) {
            return false;
        }
        openUntil = env.now() + env.party().deadlineTicks(baseTicks);
        env.gate(gate, true);
        env.station(station, "powered", "true");
        env.sound(station, "lever.pull");
        env.say(null, "message.lewandivka.tunnel.open");
        return true;
    }

    public void tick() {
        if (openUntil >= 0 && env.now() >= openUntil) {
            openUntil = -1;
            env.gate(gate, false);
            env.station(station, "powered", "false");
        }
    }

    public boolean isOpen() {
        return openUntil >= 0;
    }

    public long remaining() {
        return openUntil < 0 ? 0 : Math.max(0, openUntil - env.now());
    }

    public void reset() {
        openUntil = -1;
        env.gate(gate, false);
        env.station(station, "powered", "false");
    }
}
