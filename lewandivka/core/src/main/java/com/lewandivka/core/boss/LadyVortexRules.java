package com.lewandivka.core.boss;

import com.lewandivka.core.boss.BossEvent.Type;
import com.lewandivka.core.puzzle.ZonePlanner;
import com.lewandivka.core.puzzle.ZonePlanner.Zone;

import java.util.ArrayList;
import java.util.List;

/**
 * Lady Vortex. The arena has four quarters, each with a colour AND a symbol. Phase 0: one quarter is announced
 * (warning), then flooded. Phase 1 (below 55 %): two quarters are flooded and the boss is only vulnerable after the
 * party routed enough water cores into the drains; then the cycle repeats.
 */
public final class LadyVortexRules extends BossRules {

    public static final int DRAINS = 4;
    public static final int VULNERABLE_TICKS = 400;

    private final ZonePlanner planner;
    private enum Step { REST, WARN, FLOOD, DRAIN }

    private Step step = Step.REST;
    private long stepUntil;
    private List<Zone> zones = List.of();
    private final boolean[] drained = new boolean[DRAINS];
    private int drainedCount;
    private long vulnerableUntil = -1;

    public LadyVortexRules(long seed) {
        super(seed, 0.55);
        this.planner = new ZonePlanner(seed ^ 0x5a5a);
    }

    /** How many different drains have to be used in phase 1: three for groups, two for a lone player. */
    public int drainsRequired() {
        return party.size() >= 2 ? 3 : 2;
    }

    public List<Zone> floodedZones() {
        return step == Step.FLOOD || step == Step.DRAIN ? zones : List.of();
    }

    public List<Zone> announcedZones() {
        return step == Step.WARN ? zones : List.of();
    }

    public int drainedCount() {
        return drainedCount;
    }

    @Override
    public double damageFactor(long now) {
        if (phase() == 0) {
            return 1.0;
        }
        return now < vulnerableUntil ? 1.0 : 0.0;
    }

    @Override
    protected void onStart(long now) {
        step = Step.REST;
        stepUntil = now + 100;
        vulnerableUntil = -1;
        zones = List.of();
    }

    @Override
    protected void onPhase(int phase, long now) {
        if (phase == 1) {
            step = Step.REST;
            stepUntil = now + 60;
            emit(BossEvent.of(Type.SHIELD_ON));
            emit(BossEvent.of(Type.MESSAGE, "message.lewandivka.boss.enrage"));
        }
    }

    /** A player poured a water core into a drain. */
    public List<BossEvent> useDrain(int index, long now) {
        if (step != Step.DRAIN || index < 0 || index >= DRAINS || drained[index]) {
            return List.of();
        }
        drained[index] = true;
        drainedCount++;
        if (drainedCount >= drainsRequired()) {
            step = Step.REST;
            stepUntil = now + VULNERABLE_TICKS + 60;
            vulnerableUntil = now + VULNERABLE_TICKS;
            emit(BossEvent.of(Type.ZONE_CLEAR));
            emit(BossEvent.of(Type.SHIELD_OFF));
            emit(BossEvent.of(Type.VULNERABLE_START, VULNERABLE_TICKS));
        }
        return drain();
    }

    @Override
    protected void onTick(long now, double health) {
        if (vulnerableUntil >= 0 && now >= vulnerableUntil) {
            vulnerableUntil = -1;
            emit(BossEvent.of(Type.VULNERABLE_END));
            emit(BossEvent.of(Type.SHIELD_ON));
        }
        if (now < stepUntil) {
            return;
        }
        switch (step) {
            case REST -> {
                zones = new ArrayList<>(planner.next(phase() == 0 ? 1 : 2));
                step = Step.WARN;
                int warn = party.deadlineTicks(70);
                stepUntil = now + warn;
                for (Zone z : zones) {
                    emit(BossEvent.of(Type.ZONE_WARN, z.ordinal(), warn));
                }
            }
            case WARN -> {
                int flood = party.deadlineTicks(phase() == 0 ? 160 : 220);
                step = phase() == 0 ? Step.FLOOD : Step.DRAIN;
                stepUntil = now + (phase() == 0 ? flood : party.deadlineTicks(900));
                for (Zone z : zones) {
                    emit(BossEvent.of(Type.ZONE_FLOOD, z.ordinal(), flood));
                }
                if (phase() >= 1) {
                    java.util.Arrays.fill(drained, false);
                    drainedCount = 0;
                    emit(BossEvent.of(Type.CORES_SPAWN, party.adds(4) + 1));
                    emit(BossEvent.of(Type.DRAIN_PHASE_START, drainsRequired(), party.deadlineTicks(900)));
                }
            }
            case FLOOD -> {
                emit(BossEvent.of(Type.ZONE_CLEAR));
                step = Step.REST;
                stepUntil = now + party.deadlineTicks(100);
            }
            case DRAIN -> {
                emit(BossEvent.of(Type.DRAIN_PHASE_FAILED));
                emit(BossEvent.of(Type.ZONE_CLEAR));
                step = Step.REST;
                stepUntil = now + party.deadlineTicks(160);
            }
            default -> { }
        }
    }

    @Override
    protected void onReset() {
        step = Step.REST;
        zones = List.of();
        vulnerableUntil = -1;
        java.util.Arrays.fill(drained, false);
        drainedCount = 0;
        planner.reset();
    }
}
