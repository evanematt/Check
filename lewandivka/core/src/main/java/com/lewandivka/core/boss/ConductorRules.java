package com.lewandivka.core.boss;

import com.lewandivka.core.boss.BossEvent.Type;
import com.lewandivka.core.scale.SyncGroup;

import java.util.List;

/**
 * The Conductor. Trams cross the arena on three lanes with a clear warning (bell, lamps, sound, moving rails). The
 * Conductor shouts "КВИТОК!" and the target must reach a validator in time. The shield falls when the three composters
 * are punched inside a synchronisation window (scaled: three players one each, two players one covers two, a lone player
 * walks them one after another).
 */
public final class ConductorRules extends BossRules {

    public static final int LANES = 3;
    public static final int COMPOSTERS = 3;
    public static final int VULNERABLE_TICKS = 400;
    private static final int BASE_SYNC_TICKS = 140;

    private SyncGroup sync;
    private long vulnerableUntil = -1;
    private long nextTram;
    private int lastLane = -1;
    private long nextCall;
    private int calledTarget = -1;
    private long callUntil = -1;
    private long runEnds = -1;

    public ConductorRules(long seed) {
        super(seed, 0.5);
    }

    public int syncWindowTicks() {
        return sync.windowTicks();
    }

    public boolean shielded(long now) {
        return now >= vulnerableUntil;
    }

    @Override
    public double damageFactor(long now) {
        return shielded(now) ? 0.0 : 1.0;
    }

    public int calledTarget() {
        return calledTarget;
    }

    @Override
    protected void onStart(long now) {
        sync = new SyncGroup(COMPOSTERS, party.windowTicks(BASE_SYNC_TICKS));
        vulnerableUntil = -1;
        nextTram = now + 120;
        nextCall = now + party.deadlineTicks(400);
        emit(BossEvent.of(Type.SHIELD_ON));
    }

    public List<BossEvent> useComposter(int index, long now) {
        if (!started() || defeated || !shielded(now)) {
            return List.of();
        }
        if (sync.activate(index, now)) {
            vulnerableUntil = now + VULNERABLE_TICKS;
            sync = new SyncGroup(COMPOSTERS, party.windowTicks(BASE_SYNC_TICKS));
            emit(BossEvent.of(Type.SHIELD_OFF));
            emit(BossEvent.of(Type.VULNERABLE_START, VULNERABLE_TICKS));
        }
        return drain();
    }

    public boolean composterLit(int index, long now) {
        return sync.isLit(index, now);
    }

    /** The called player reached a validator. */
    public List<BossEvent> ticketValidated(int target, long now) {
        if (calledTarget == target) {
            calledTarget = -1;
            callUntil = -1;
            nextCall = now + party.deadlineTicks(500);
        }
        return drain();
    }

    @Override
    protected void onTick(long now, double health) {
        if (vulnerableUntil >= 0 && now >= vulnerableUntil) {
            emit(BossEvent.of(Type.VULNERABLE_END));
            emit(BossEvent.of(Type.SHIELD_ON));
            vulnerableUntil = -1;
        }
        if (runEnds >= 0 && now >= runEnds) {
            runEnds = -1;
        }
        if (now >= nextTram && runEnds < 0) {
            int lane;
            do {
                lane = rng.nextInt(LANES);
            } while (lane == lastLane);
            lastLane = lane;
            int warn = party.deadlineTicks(80);
            emit(BossEvent.of(Type.TRAM_WARN, lane, warn));
            emit(BossEvent.of(Type.TRAM_RUN, lane, warn));
            if (phase() >= 1) {
                int second;
                do {
                    second = rng.nextInt(LANES);
                } while (second == lane);
                emit(BossEvent.of(Type.TRAM_WARN, second, warn + 20));
                emit(BossEvent.of(Type.TRAM_RUN, second, warn + 20));
            }
            runEnds = now + warn + 120;
            nextTram = now + party.deadlineTicks(phase() >= 1 ? 260 : 340);
        }
        if (calledTarget < 0 && now >= nextCall) {
            calledTarget = rng.nextInt(Math.max(1, party.size()));
            callUntil = now + party.deadlineTicks(300);
            emit(BossEvent.of(Type.TICKET_CALL, calledTarget, party.deadlineTicks(300)));
        } else if (calledTarget >= 0 && now >= callUntil) {
            emit(BossEvent.of(Type.TICKET_FAIL, calledTarget, 0));
            calledTarget = -1;
            callUntil = -1;
            nextCall = now + party.deadlineTicks(500);
        }
    }

    @Override
    protected void onReset() {
        vulnerableUntil = -1;
        calledTarget = -1;
        callUntil = -1;
        runEnds = -1;
        lastLane = -1;
        sync = null;
    }
}
