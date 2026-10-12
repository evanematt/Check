package com.lewandivka.core.flow.dungeon;

import com.lewandivka.core.flow.Flow;
import com.lewandivka.core.flow.FlowEnv;
import com.lewandivka.core.flow.mechanism.CatHints;
import com.lewandivka.core.flow.mechanism.CheckpointSensors;
import com.lewandivka.core.story.Events;

import java.util.List;
import java.util.UUID;

/**
 * The traversal exam in front of the tower: three dash doors, a "wall" that the cats open, the spring shaft and the
 * glider chasm. The abilities themselves are physics; the flow keeps the doors, the checkpoints and the finish. A fall
 * into the gorge puts the player back at the last checkpoint (see the fall zones of the game), never kills.
 */
public final class TowerApproachFlow implements Flow {

    public static final String ID = "tower_approach";
    public static final int DOORS = 3;

    private final FlowEnv env;
    private final CatHints cats;
    private final CheckpointSensors sensors;

    public TowerApproachFlow(FlowEnv env) {
        this.env = env;
        this.cats = new CatHints(env, List.of(new CatHints.Site("wall", "hidden_front", "cat_spot", "hidden_back", "hidden_wall")));
        this.sensors = new CheckpointSensors(env, "tower_approach", 3.5);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String structure() {
        return "tower_approach";
    }

    public int doorsOpen() {
        int n = 0;
        for (int i = 1; i <= DOORS; i++) {
            if (env.record().flag("door." + i)) {
                n++;
            }
        }
        return n;
    }

    @Override
    public void playerEntered(UUID player) {
        if (env.record().setFlag("started")) {
            env.checkpoint(0);
        }
    }

    /** A dash hit a door (the game calls this for dash doors, nothing else opens them). */
    @Override
    public boolean use(String marker, UUID player) {
        if (!marker.startsWith("dash_door_")) {
            return false;
        }
        int n = marker.charAt(marker.length() - 1) - '0';
        if (n < 1 || n > DOORS) {
            return false;
        }
        if (env.record().setFlag("door." + n)) {
            env.gate(marker, true);
            env.sound(marker, "garage.door");
            env.fx(marker, "spark");
            env.say(null, "message.lewandivka.approach.door", doorsOpen(), DOORS);
        }
        return true;
    }

    @Override
    public void tick() {
        cats.tick();
        sensors.tick();
        if (!env.record().flag("done") && !env.playersAt("tower_approach_end", 3).isEmpty() && env.record().setFlag("done")) {
            env.checkpoint(6);
            env.event(Events.APPROACH_DONE);
        }
    }

    @Override
    public void rebuild() {
        cats.rebuild();
        for (int i = 1; i <= DOORS; i++) {
            if (env.record().flag("door." + i)) {
                env.gate("dash_door_" + i, true);
            }
        }
    }

    @Override
    public void reset() {
        cats.reset();
    }

    @Override
    public boolean complete() {
        return env.record().flag("done");
    }
}
