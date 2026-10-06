package com.lewandivka.core.flow.dungeon;

import com.lewandivka.core.campaign.QuestStep;
import com.lewandivka.core.flow.Flow;
import com.lewandivka.core.flow.FlowEnv;
import com.lewandivka.core.quest.QuestItems;
import com.lewandivka.core.scale.SyncGroup;
import com.lewandivka.core.story.Events;

import java.util.List;
import java.util.UUID;

/**
 * The Last Tram (end of act 1). At night the tram comes out of the fog and three waves attack the stop:
 * fare dodgers, then enemies from several entrances while the tram has to be defended, then the Fare Dodger Leader,
 * who is shielded until the players punch the three old validators within a (party-scaled) window by showing a
 * district token. The reward is the composter. Completed waves stay completed, so a wipe never costs more than the
 * wave that was running.
 */
public final class LastTramFlow implements Flow {

    public static final String ID = "last_tram";
    public static final String STRUCTURE = "tram_stop";
    public static final String W1 = "tram.w1";
    public static final String W2 = "tram.w2";
    public static final String W3 = "tram.w3";
    public static final String LEADER = "fare_dodger_leader";
    public static final double SHIELDED = 0.15;
    public static final int VALIDATORS = 3;
    public static final int VALIDATOR_WINDOW = 300;
    public static final int TRAM_INTEGRITY = 100;

    /** Markers of the tram stop used by the flow. */
    public static final List<String> MARKERS = List.of("validator_1", "validator_2", "validator_3");
    /** Markers of the district plan used by the flow. */
    public static final List<String> DISTRICT_MARKERS = List.of("tram_origin", "tram_fog_start", "tram_stop_x",
            "wave_1", "wave_2", "wave_3", "wave_4", "wave_5", "wave_6", "wave_7", "wave_8");

    private enum Phase {
        IDLE, INTRO, WAVE1, BREAK1, WAVE2, BREAK2, WAVE3, DONE
    }

    private final FlowEnv env;
    private Phase phase = Phase.IDLE;
    private long phaseAt;
    private long nextCheck;
    private int integrity = TRAM_INTEGRITY;
    private SyncGroup validators;
    private long shieldDownUntil = -1;

    public LastTramFlow(FlowEnv env) {
        this.env = env;
        restore();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String structure() {
        return STRUCTURE;
    }

    public int integrity() {
        return integrity;
    }

    public String phaseName() {
        return phase.name();
    }

    private void restore() {
        long now = env.now();
        phaseAt = now + 40;
        if (env.record().flag("wave.3")) {
            phase = Phase.DONE;
        } else if (env.record().flag("wave.2")) {
            phase = Phase.BREAK2;
        } else if (env.record().flag("wave.1")) {
            phase = Phase.BREAK1;
        } else if (env.record().flag("started")) {
            phase = Phase.INTRO;
        }
    }

    // ------------------------------------------------------------------ validators

    @Override
    public boolean use(String marker, UUID player) {
        return useWith(marker, player, "");
    }

    @Override
    public boolean useWith(String marker, UUID player, String item) {
        int i = MARKERS.indexOf(marker);
        if (i < 0) {
            return false;
        }
        if (phase != Phase.WAVE3) {
            env.say(player, "message.lewandivka.not_yet");
            return true;
        }
        if (!QuestItems.TOKEN.equals(item)) {
            env.say(player, "message.lewandivka.ticket.invalid");
            return true;
        }
        long now = env.now();
        if (now < shieldDownUntil) {
            return true;                                   // the shield is down already
        }
        boolean done = validators.activate(i, now);
        env.station(marker, "active", "true");
        env.sound(marker, "tram.validate");
        env.say(null, "message.lewandivka.tram.shield", validators.activeCount(now), VALIDATORS);
        if (done) {
            shieldDownUntil = now + env.party().vulnerableTicks(400);
            env.resistance(LEADER, 1.0);
            env.sound(marker, "tram.compost");
            validators = new SyncGroup(VALIDATORS, env.party().windowTicks(VALIDATOR_WINDOW));
        }
        return true;
    }

    // ------------------------------------------------------------------ the event

    @Override
    public void tick() {
        long now = env.now();
        switch (phase) {
            case IDLE -> {
                if (env.world().step() == QuestStep.TRAM_WAIT && !env.players().isEmpty()) {
                    env.record().setFlag("started");
                    env.forceNight(true);
                    env.sound("validator_2", "tram.arrive");
                    env.event(Events.TRAM_STARTED);
                    phase = Phase.INTRO;
                    phaseAt = now + 200;
                }
            }
            case INTRO, BREAK1, BREAK2 -> {
                if (now >= phaseAt) {
                    spawnWave(phase == Phase.INTRO ? 1 : phase == Phase.BREAK1 ? 2 : 3, now);
                }
            }
            case WAVE1 -> {
                if (now >= phaseAt && env.alive(W1) == 0) {
                    waveDone(1);
                    phase = Phase.BREAK1;
                    phaseAt = now + 100;
                }
            }
            case WAVE2 -> tickWave2(now);
            case WAVE3 -> tickWave3(now);
            default -> { }
        }
    }

    private void spawnWave(int wave, long now) {
        env.forceNight(true);
        env.say(null, "message.lewandivka.tram.wave", wave);
        env.sound("validator_2", "tram.bell");
        phaseAt = now + 40;
        switch (wave) {
            case 1 -> {
                phase = Phase.WAVE1;
                int n = env.party().adds(6);
                for (int i = 0; i < n; i++) {
                    env.spawn("fare_dodger", "district:wave_" + (1 + i % 2), 1, W1);
                }
            }
            case 2 -> {
                phase = Phase.WAVE2;
                integrity = TRAM_INTEGRITY;
                nextCheck = now + 60;
                int n = env.party().adds(9);
                for (int i = 0; i < n; i++) {
                    env.spawn(i % 3 == 2 ? "seed_thrower" : "fare_dodger", "district:wave_" + (3 + i % 6), 1, W2);
                }
            }
            default -> {
                phase = Phase.WAVE3;
                validators = new SyncGroup(VALIDATORS, env.party().windowTicks(VALIDATOR_WINDOW));
                shieldDownUntil = -1;
                env.spawnBoss(LEADER, "district:wave_5");
                env.resistance(LEADER, SHIELDED);
                int n = env.party().adds(4);
                for (int i = 0; i < n; i++) {
                    env.spawn("fare_dodger", "district:wave_" + (3 + i % 6), 1, W3);
                }
                env.dialogue("tram_start");
            }
        }
    }

    private void tickWave2(long now) {
        if (now >= nextCheck) {
            nextCheck = now + 20;
            integrity -= env.near(W2, "district:tram_stop_x", 5.0) * 2;
            if (integrity <= 0) {
                // the stop was overrun: the wave starts over, nothing else is lost
                env.despawn(W2);
                env.say(null, "message.lewandivka.encounter_reset");
                spawnWave(2, now + 100);
                return;
            }
        }
        if (now >= phaseAt && env.alive(W2) == 0) {
            waveDone(2);
            phase = Phase.BREAK2;
            phaseAt = now + 100;
        }
    }

    private void tickWave3(long now) {
        if (shieldDownUntil >= 0 && now >= shieldDownUntil) {
            shieldDownUntil = -1;
            env.resistance(LEADER, SHIELDED);
            for (int i = 1; i <= VALIDATORS; i++) {
                env.station("validator_" + i, "active", "false");
            }
            return;
        }
        if (shieldDownUntil < 0 && now % 20 == 0) {
            for (int i = 0; i < VALIDATORS; i++) {
                if (!validators.isLit(i, now)) {
                    env.station("validator_" + (i + 1), "active", "false");
                }
            }
        }
    }

    private void waveDone(int wave) {
        if (env.record().setFlag("wave." + wave)) {
            env.event(Events.TRAM_WAVE);
            env.checkpoint(wave);
        }
    }

    @Override
    public void bossDefeated(String bossId) {
        if (!LEADER.equals(bossId) || phase == Phase.DONE) {
            return;
        }
        env.despawn(W3);
        waveDone(3);
        phase = Phase.DONE;
        env.say(null, "message.lewandivka.tram.reward");
        env.forceNight(false);
    }

    // ------------------------------------------------------------------ recovery

    @Override
    public void rebuild() {
        env.forceNight(phase != Phase.IDLE && phase != Phase.DONE);
    }

    @Override
    public void reset() {
        if (phase == Phase.DONE) {
            return;
        }
        env.despawn(W1);
        env.despawn(W2);
        env.despawn(W3);
        env.despawnBoss(LEADER);
        validators = null;
        shieldDownUntil = -1;
        integrity = TRAM_INTEGRITY;
        // finished waves stay finished; the running one starts again after a short pause
        phase = env.record().flag("wave.2") ? Phase.BREAK2 : env.record().flag("wave.1") ? Phase.BREAK1
                : env.record().flag("started") ? Phase.INTRO : Phase.IDLE;
        phaseAt = env.now() + 100;
    }

    @Override
    public boolean complete() {
        return phase == Phase.DONE;
    }
}
