package com.lewandivka.core.flow.mechanism;

import com.lewandivka.core.flow.Checkpoints;
import com.lewandivka.core.flow.FlowEnv;

import java.util.List;

/**
 * Saves a checkpoint when somebody stands at its marker for the first time. The checkpoint lamps are physical
 * blocks the players can also use; this keeps the encounter record in step even when nobody touches them.
 */
public final class CheckpointSensors {

    private final FlowEnv env;
    private final List<String> names;
    private final double radius;

    public CheckpointSensors(FlowEnv env, String structure, double radius) {
        this.env = env;
        this.names = Checkpoints.names(structure);
        this.radius = radius;
    }

    public void tick() {
        for (int i = names.size() - 1; i >= 1; i--) {
            if (env.record().flag("cp." + i)) {
                return;                                    // nothing further back can be news
            }
            if (!env.playersAt(names.get(i), radius).isEmpty()) {
                for (int k = 1; k <= i; k++) {
                    env.record().setFlag("cp." + k);
                }
                env.checkpoint(i);
                return;
            }
        }
    }
}
