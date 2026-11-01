package com.lewandivka.core.boss;

import com.lewandivka.core.boss.BossEvent.Type;

import java.util.List;

/**
 * Collar Collector. The cats' names return to "???" when the fight starts.
 *
 * <ul>
 *   <li>Phase 0: the collector is shielded. Eight collar stands hold two real collars (Chinazik's and Metadonna's) and six
 *       decoys; the cats react to the real ones. Both real collars drop the shield for 20 seconds, then the stands are
 *       shuffled again.</li>
 *   <li>Phase 1 (below 66 %): the collector leashes one player to an anchor. The others break the anchor; when nobody can,
 *       Chinazik bites the leash after a party-scaled interval, so a lone player is never stuck.</li>
 *   <li>Phase 2 (below 33 %): clones appear; only the real collector keeps the cats' attention.</li>
 * </ul>
 */
public final class CollarCollectorRules extends BossRules {

    public static final int STANDS = 8;
    public static final int VULNERABLE_TICKS = 400;

    private final boolean[] real = new boolean[STANDS];
    private final boolean[] taken = new boolean[STANDS];
    private int foundReal;
    private long vulnerableUntil = -1;
    private long nextLeash;
    private long leashUntil = -1;
    private int leashTarget = -1;
    private long nextClones;
    private long clonesUntil = -1;
    private long nextHint;

    public CollarCollectorRules(long seed) {
        super(seed, 0.66, 0.33);
    }

    public boolean isRealStand(int index) {
        return real[index];
    }

    public int foundRealCollars() {
        return foundReal;
    }

    public boolean leashed() {
        return leashTarget >= 0;
    }

    @Override
    public double damageFactor(long now) {
        if (phase() == 0) {
            return now < vulnerableUntil ? 1.0 : 0.0;
        }
        return 1.0;
    }

    @Override
    protected void onStart(long now) {
        emit(BossEvent.of(Type.NAMES_HIDDEN));
        emit(BossEvent.of(Type.SHIELD_ON));
        shuffle();
    }

    private void shuffle() {
        java.util.Arrays.fill(real, false);
        java.util.Arrays.fill(taken, false);
        foundReal = 0;
        int a = rng.nextInt(STANDS);
        int b;
        do {
            b = rng.nextInt(STANDS);
        } while (b == a);
        real[a] = true;
        real[b] = true;
        emit(BossEvent.of(Type.COLLARS_SHUFFLED, a, b));
    }

    /** A player took the collar from a stand; the cats react. */
    public List<BossEvent> useStand(int index, long now) {
        if (!started() || defeated || index < 0 || index >= STANDS || taken[index] || phase() != 0 || now < vulnerableUntil) {
            return List.of();
        }
        taken[index] = true;
        emit(BossEvent.of(Type.COLLAR_REACTION, index, real[index] ? 1 : 0));
        if (real[index]) {
            foundReal++;
            if (foundReal >= 2) {
                vulnerableUntil = now + VULNERABLE_TICKS;
                emit(BossEvent.of(Type.SHIELD_OFF));
                emit(BossEvent.of(Type.VULNERABLE_START, VULNERABLE_TICKS));
            }
        }
        return drain();
    }

    /** The anchor entity was destroyed by the players. */
    public List<BossEvent> anchorDestroyed(long now) {
        if (leashTarget >= 0) {
            emit(BossEvent.of(Type.LEASH_END, leashTarget, 0));
            leashTarget = -1;
            leashUntil = -1;
            nextLeash = now + party.deadlineTicks(500);
        }
        return drain();
    }

    @Override
    protected void onPhase(int phase, long now) {
        if (phase == 1) {
            emit(BossEvent.of(Type.SHIELD_OFF));
            emit(BossEvent.of(Type.VULNERABLE_START, 0));
            nextLeash = now + 100;
        } else if (phase == 2) {
            nextClones = now + 100;
            nextHint = now + 200;
        }
    }

    @Override
    protected void onTick(long now, double health) {
        if (phase() == 0 && vulnerableUntil >= 0 && now >= vulnerableUntil) {
            vulnerableUntil = -1;
            emit(BossEvent.of(Type.VULNERABLE_END));
            emit(BossEvent.of(Type.SHIELD_ON));
            shuffle();
        }
        if (phase() >= 1) {
            if (leashTarget >= 0 && now >= leashUntil) {
                emit(BossEvent.of(Type.LEASH_BROKEN_BY_CAT, leashTarget, 0));
                emit(BossEvent.of(Type.LEASH_END, leashTarget, 1));
                leashTarget = -1;
                leashUntil = -1;
                nextLeash = now + party.deadlineTicks(500);
            } else if (leashTarget < 0 && now >= nextLeash && phase() == 1) {
                leashTarget = rng.nextInt(Math.max(1, party.size()));
                leashUntil = now + party.deadlineTicks(400);
                emit(BossEvent.of(Type.LEASH, leashTarget, party.deadlineTicks(400)));
            }
        }
        if (phase() >= 2) {
            if (clonesUntil >= 0 && now >= clonesUntil) {
                clonesUntil = -1;
                emit(BossEvent.of(Type.CLONES_GONE));
                nextClones = now + party.deadlineTicks(300);
            } else if (clonesUntil < 0 && now >= nextClones) {
                clonesUntil = now + party.deadlineTicks(500);
                emit(BossEvent.of(Type.CLONES, Math.max(2, party.adds(4)), party.deadlineTicks(500)));
                nextHint = now + 40;
            }
            if (clonesUntil >= 0 && now >= nextHint) {
                nextHint = now + 100;
                emit(BossEvent.of(Type.REAL_BOSS_HINT));
            }
        }
    }

    public boolean clonesActive() {
        return clonesUntil >= 0;
    }

    @Override
    protected void onReset() {
        vulnerableUntil = -1;
        leashTarget = -1;
        leashUntil = -1;
        clonesUntil = -1;
        foundReal = 0;
        java.util.Arrays.fill(taken, false);
    }
}
