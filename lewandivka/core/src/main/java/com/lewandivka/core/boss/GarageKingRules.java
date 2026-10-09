package com.lewandivka.core.boss;

import com.lewandivka.core.boss.BossEvent.Type;
import com.lewandivka.core.scale.SyncGroup;

import java.util.List;

/**
 * Garage King. Phase 0: the king is shielded; the three arena levers must be pulled inside a synchronisation window
 * (scaled by the party), which raises the lifts and opens the core for 15 seconds. Phase 2 (below half health) adds
 * wheel projectiles, mechanic minions, cars driving through the arena and lift malfunctions that need a repair
 * interaction before the lever counts.
 */
public final class GarageKingRules extends BossRules {

    public static final int LEVERS = 3;
    public static final int CORE_OPEN_TICKS = 15 * 20;
    private static final int BASE_SYNC_TICKS = 100;

    private SyncGroup sync;
    private long coreOpenUntil = -1;
    private final long[] malfunctionUntil = new long[LEVERS];
    private long nextWheel;
    private long nextMinions;
    private long nextMalfunction;
    private long nextCar;
    private int coresOpened;

    public GarageKingRules(long seed) {
        super(seed, 0.5);
    }

    public boolean shielded(long now) {
        return now >= coreOpenUntil;
    }

    public int coresOpened() {
        return coresOpened;
    }

    public int syncWindowTicks() {
        return sync.windowTicks();
    }

    @Override
    public double damageFactor(long now) {
        return shielded(now) ? 0.0 : 1.0;
    }

    @Override
    protected void onStart(long now) {
        sync = new SyncGroup(LEVERS, party.windowTicks(BASE_SYNC_TICKS));
        coreOpenUntil = -1;
        coresOpened = 0;
        java.util.Arrays.fill(malfunctionUntil, 0);
        emit(BossEvent.of(Type.SHIELD_ON));
    }

    /** A player pulled arena lever {@code index} (0..2). */
    public List<BossEvent> useLever(int index, long now) {
        if (!started() || defeated || index < 0 || index >= LEVERS) {
            return List.of();
        }
        if (malfunctionUntil[index] > now) {
            malfunctionUntil[index] = 0;
            emit(BossEvent.of(Type.REPAIRED, index));
            return drain();
        }
        if (!shielded(now)) {
            return List.of();                              // the core is already open
        }
        boolean done = sync.activate(index, now);
        if (done) {
            coreOpenUntil = now + CORE_OPEN_TICKS;
            coresOpened++;
            sync = new SyncGroup(LEVERS, party.windowTicks(BASE_SYNC_TICKS));
            emit(BossEvent.of(Type.SHIELD_OFF));
            emit(BossEvent.of(Type.CORE_OPEN, CORE_OPEN_TICKS));
            emit(BossEvent.of(Type.LIFTS, 1));
        }
        return drain();
    }

    public boolean leverLit(int index, long now) {
        return sync != null && sync.isLit(index, now);
    }

    public boolean malfunctioning(int index, long now) {
        return malfunctionUntil[index] > now;
    }

    @Override
    protected void onPhase(int phase, long now) {
        if (phase == 1) {
            nextWheel = now + party.deadlineTicks(120);
            nextMinions = now + 60;
            nextMalfunction = now + party.deadlineTicks(500);
            nextCar = now + party.deadlineTicks(300);
            emit(BossEvent.of(Type.MESSAGE, "message.lewandivka.boss.enrage"));
        }
    }

    @Override
    protected void onTick(long now, double health) {
        if (coreOpenUntil >= 0 && now >= coreOpenUntil) {
            coreOpenUntil = -1;
            emit(BossEvent.of(Type.CORE_CLOSE));
            emit(BossEvent.of(Type.SHIELD_ON));
            emit(BossEvent.of(Type.LIFTS, 0));
        }
        if (phase() >= 1) {
            if (now >= nextWheel) {
                nextWheel = now + party.deadlineTicks(jitter(140, 40));
                emit(BossEvent.of(Type.WHEEL, rng.nextInt(Math.max(1, party.size()))));
            }
            if (now >= nextMinions) {
                nextMinions = now + jitter(500, 100);
                emit(BossEvent.of(Type.ADDS, party.adds(3), 0));
            }
            if (now >= nextMalfunction) {
                nextMalfunction = now + party.deadlineTicks(jitter(560, 160));
                int lever = rng.nextInt(LEVERS);
                malfunctionUntil[lever] = now + party.deadlineTicks(300);
                emit(BossEvent.of(Type.MALFUNCTION, lever));
            }
            if (now >= nextCar) {
                nextCar = now + party.deadlineTicks(jitter(380, 120));
                emit(BossEvent.of(Type.CAR, rng.nextInt(2)));
            }
        }
    }

    @Override
    protected void onReset() {
        coreOpenUntil = -1;
        java.util.Arrays.fill(malfunctionUntil, 0);
        sync = null;
    }
}
