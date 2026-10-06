package com.lewandivka.core.boss;

import com.lewandivka.core.scale.PartyScale;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Base class of the pure boss rules (the "state machine" of the reusable boss framework). A rules object knows nothing
 * about Minecraft: the boss entity calls {@link #start}, {@link #tick} every server tick and the interaction methods of
 * the concrete fight, and applies the returned {@link BossEvent}s.
 *
 * <p>The party size is fixed when the fight starts (an encounter's {@code activePartySize}); every timer is derived
 * from it, so nothing ever fails because there are not exactly three players.</p>
 */
public abstract class BossRules {

    protected PartyScale party = PartyScale.of(3);
    protected final PhaseTracker phases;
    protected final Random rng;
    protected long startedAt = Long.MIN_VALUE;
    protected boolean defeated;
    private final List<BossEvent> out = new ArrayList<>();

    protected BossRules(long seed, double... phaseThresholds) {
        this.rng = new Random(seed);
        this.phases = new PhaseTracker(phaseThresholds);
    }

    public final PartyScale party() {
        return party;
    }

    public final int phase() {
        return phases.phase();
    }

    public final boolean started() {
        return startedAt != Long.MIN_VALUE;
    }

    public final boolean defeated() {
        return defeated;
    }

    /** Begins the fight with the party size fixed at this moment. */
    public final List<BossEvent> start(PartyScale party, long now) {
        this.party = party;
        this.startedAt = now;
        this.defeated = false;
        phases.reset();
        out.clear();
        onStart(now);
        return drain();
    }

    /** One server tick. {@code health} is the fraction 0..1 of the boss. */
    public final List<BossEvent> tick(long now, double health) {
        if (!started() || defeated) {
            return List.of();
        }
        int next;
        while ((next = phases.advanceIfNeeded(health)) >= 0) {
            emit(BossEvent.of(BossEvent.Type.PHASE, next));
            onPhase(next, now);
        }
        onTick(now, health);
        return drain();
    }

    /** The boss died. */
    public final List<BossEvent> defeat(long now) {
        if (!defeated) {
            defeated = true;
            emit(BossEvent.of(BossEvent.Type.DEFEATED));
            onDefeat(now);
        }
        return drain();
    }

    /** Clean reset (party wipe, boss unloaded): everything starts over from the first phase. */
    public final void reset() {
        startedAt = Long.MIN_VALUE;
        defeated = false;
        phases.reset();
        out.clear();
        onReset();
    }

    /** 0 = invulnerable, 1 = normal damage; other values for partial vulnerability. */
    public abstract double damageFactor(long now);

    protected abstract void onStart(long now);

    protected abstract void onTick(long now, double health);

    protected void onPhase(int phase, long now) {
    }

    protected void onDefeat(long now) {
    }

    protected abstract void onReset();

    protected final void emit(BossEvent e) {
        out.add(e);
    }

    /** Events produced by an interaction (levers, drains ...) are collected the same way. */
    protected final List<BossEvent> drain() {
        if (out.isEmpty()) {
            return List.of();
        }
        List<BossEvent> copy = List.copyOf(out);
        out.clear();
        return copy;
    }

    protected final int jitter(int base, int spread) {
        return base + (spread <= 0 ? 0 : rng.nextInt(spread + 1));
    }
}
