package com.lewandivka.core.flow.dungeon;

import com.lewandivka.core.flow.Flow;
import com.lewandivka.core.flow.FlowEnv;

import java.util.UUID;

/**
 * Garage No. 0 (postgame): three waves of gopniks in a parking hall below the district. The party is brought here by the
 * wrong tram; the way out is the ladder. When the third wave is down the reward stash appears.
 */
public final class Garage0Flow implements Flow {

    public static final String ID = "garage0";
    public static final String ENEMIES = "g0.enemy";
    public static final int WAVES = 3;

    private final FlowEnv env;
    private int wave;
    private long nextWaveAt = -1;

    public Garage0Flow(FlowEnv env) {
        this.env = env;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String structure() {
        return "garage0";
    }

    public int wave() {
        return wave;
    }

    @Override
    public void playerEntered(UUID player) {
        if (env.record().setFlag("entered")) {
            env.checkpoint(0);
            env.say(null, "message.lewandivka.postgame.garage0");
        }
    }

    @Override
    public boolean use(String marker, UUID player) {
        return false;
    }

    private void spawnWave(int n) {
        wave = n;
        env.say(null, "message.lewandivka.tram.wave", n);
        env.sound("entry", "boss.bell");
        int adds = Math.max(1, env.party().adds(2 + 2 * n));
        for (int i = 0; i < adds; i++) {
            String entity = n == 1 ? "gopnik" : n == 2 ? (i % 2 == 0 ? "gopnik" : "seed_thrower") : (i == 0 ? "senior_yard_gopnik" : "seed_thrower");
            env.spawn(entity, "spawn_" + (1 + i % 4), 1, ENEMIES);
        }
    }

    @Override
    public void tick() {
        if (env.record().flag("done")) {
            return;
        }
        long now = env.now();
        if (wave == 0) {
            if (!env.playersAt("arena", 1).isEmpty()) {
                spawnWave(1);
            }
            return;
        }
        if (env.alive(ENEMIES) > 0) {
            return;
        }
        if (wave >= WAVES) {
            finish();
        } else if (nextWaveAt < 0) {
            nextWaveAt = now + 100;
        } else if (now >= nextWaveAt) {
            nextWaveAt = -1;
            spawnWave(wave + 1);
        }
    }

    private void finish() {
        if (env.record().setFlag("done")) {
            env.block("reward", "lewandivka:supply_stash");
            env.sound("reward", "garage.door");
            env.fx("reward", "reveal");
            env.award("garage0");
            env.checkpoint(0);
        }
    }

    @Override
    public void rebuild() {
        if (env.record().flag("done")) {
            env.block("reward", "lewandivka:supply_stash");
        }
    }

    @Override
    public void reset() {
        env.despawn(ENEMIES);
        wave = 0;
        nextWaveAt = -1;
    }

    @Override
    public boolean complete() {
        return env.record().flag("done");
    }
}
