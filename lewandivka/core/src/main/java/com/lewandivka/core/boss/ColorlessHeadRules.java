package com.lewandivka.core.boss;

import com.lewandivka.core.boss.BossEvent.Type;
import com.lewandivka.core.puzzle.PlatformDecay;
import com.lewandivka.core.puzzle.ZonePlanner;
import com.lewandivka.core.puzzle.ZonePlanner.Zone;
import com.lewandivka.core.scale.SyncGroup;
import com.lewandivka.core.sync.ChargeRelay;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The Colorless Head (final boss).
 *
 * <ul>
 *   <li>Phase 0 (above 70 %): the Chromatic Charge. Only its holder hurts the boss, it overloads after twelve seconds
 *       unless it is passed on (Q / drop). Telegraphed attacks teach the passing rhythm; one attack per cycle is aimed at
 *       the holder.</li>
 *   <li>Phase 1 (70 % to 35 %): the arena platforms lose their colour (coloured, grey, cracked, collapsed) but never all
 *       of them: anchors stay and a minimum always remains standing.</li>
 *   <li>Phase 2 (35 % to 5 %): the exam of the whole campaign. Four short segments, one at a time and in order: garage
 *       lifts (everybody stands on a lift), aquapark (two colour quarters flood), ticket (one player must reach a
 *       validator) and clones (the cats show the real one).</li>
 *   <li>Phase 3 (5 %): the Head steals the charge and the world turns grey. "Ось так краще." Silence. The two cats enter,
 *       one knocks the charge loose, the other redirects it to the players: the final vulnerability window. If the window
 *       runs out the scene repeats, so the fight can never soft-lock.</li>
 * </ul>
 *
 * The rules never touch the world: they only emit {@link BossEvent}s and answer questions of the boss entity.
 */
public final class ColorlessHeadRules extends BossRules {

    public static final int PLATFORMS = 14;
    public static final int RELAYS = 2;
    public static final int LIFTS = 3;
    public static final int VALIDATORS = 2;
    public static final int MIN_STANDING = 9;
    public static final int SILENCE_TICKS = 100;
    public static final int FINAL_WINDOW_BASE_TICKS = 400;
    public static final int STAGGER_TICKS = 200;
    private static final boolean[] ANCHORS = new boolean[PLATFORMS];

    static {
        ANCHORS[0] = true;   // the centre platform
        ANCHORS[8] = true;   // platforms that carry the relay devices
        ANCHORS[12] = true;
    }

    public enum Segment {
        NONE, GARAGE, AQUAPARK, TICKET, CLONES
    }

    private enum Finale {
        NONE, SILENCE, CATS, WINDOW
    }

    /** Attack kinds of the TELEGRAPH / ATTACK events (argument {@code a}). */
    public static final int ATTACK_SWEEP = 0;
    public static final int ATTACK_SLAM = 1;
    public static final int ATTACK_BEAM = 2;
    public static final int ATTACK_WAVE = 3;

    private final ChargeRelay relay = new ChargeRelay();
    private final PlatformDecay decay = new PlatformDecay(PLATFORMS, ANCHORS, MIN_STANDING);
    private final ZonePlanner planner;

    private long nextAttack;
    private int attackCounter;
    private long nextDecay;
    private long nextRegrow;

    private Segment segment = Segment.NONE;
    private int nextSegmentIndex;
    private long segmentEnds;
    private long nextSegmentAt;
    private SyncGroup lifts;
    private boolean liftsDone;
    private List<Zone> flooded = List.of();
    private int zoneStep;
    private long zoneStepAt;
    private int calledPlayer = -1;
    private int realClone = -1;
    private int clones;
    private long nextHint;
    private long staggerUntil = -1;

    private Finale finale = Finale.NONE;
    private long windowEnds = -1;

    public ColorlessHeadRules(long seed) {
        super(seed, 0.70, 0.35, 0.05);
        this.planner = new ZonePlanner(seed ^ 0x77);
    }

    // ------------------------------------------------------------------ queries

    public ChargeRelay relay() {
        return relay;
    }

    public PlatformDecay platforms() {
        return decay;
    }

    public Segment segment() {
        return segment;
    }

    public boolean inFinale() {
        return finale != Finale.NONE;
    }

    public boolean finalWindowOpen(long now) {
        return finale == Finale.WINDOW && now < windowEnds;
    }

    public List<Zone> floodedZones() {
        return zoneStep == 2 ? flooded : List.of();
    }

    public List<Zone> announcedZones() {
        return zoneStep == 1 ? flooded : List.of();
    }

    public int calledPlayer() {
        return calledPlayer;
    }

    public int realClone() {
        return realClone;
    }

    public boolean charged() {
        return finale != Finale.SILENCE && finale != Finale.CATS;
    }

    /** True while the boss can be hurt at all (never during the monologue and silence of the finale). */
    @Override
    public double damageFactor(long now) {
        if (finale == Finale.SILENCE || finale == Finale.CATS) {
            return 0.0;
        }
        if (finale == Finale.WINDOW) {
            return now < windowEnds ? 1.0 : 0.0;
        }
        if (phase() == 2 && segment == Segment.CLONES) {
            return 0.0;                                   // hit the real clone: the boss itself is out of reach
        }
        return now < staggerUntil ? 1.5 : 1.0;
    }

    /** Only the charge holder hurts the boss. */
    public boolean canDamage(UUID attacker, long now) {
        return charged() && relay.canDamageBoss(attacker) && damageFactor(now) > 0.0;
    }

    // ------------------------------------------------------------------ the charge

    public List<BossEvent> passCharge(UUID from, UUID to, long now) {
        if (!charged()) {
            return List.of();
        }
        ChargeRelay.Result r = relay.pass(from, to, now);
        if (r.event() == ChargeRelay.Event.PASSED) {
            emit(new BossEvent(Type.CHARGE_PASS, 0, 0, to.toString()));
        }
        return drain();
    }

    /** Throws the charge into relay {@code relayIndex} (0 or 1). */
    public List<BossEvent> throwToRelay(UUID from, int relayIndex, long now) {
        if (!charged() || relayIndex < 0 || relayIndex >= RELAYS) {
            return List.of();
        }
        ChargeRelay.Result r = relay.throwToRelay(from, relayIndex, now);
        if (r.event() == ChargeRelay.Event.RELAYED) {
            emit(BossEvent.of(Type.CHARGE_RELAY, relayIndex));
        }
        return drain();
    }

    /**
     * Server tick for the charge object.
     *
     * @param eligible players that are alive and inside the arena, the best receiver first
     */
    public List<BossEvent> tickCharge(long now, Collection<UUID> eligible) {
        if (!started() || defeated || !charged()) {
            return List.of();
        }
        for (ChargeRelay.Result r : relay.tick(now, eligible)) {
            switch (r.event()) {
                case GRANTED -> emit(new BossEvent(Type.CHARGE_GRANT, 0, 0, r.player().toString()));
                case REASSIGNED -> emit(new BossEvent(Type.CHARGE_GRANT, 0, 1, r.player().toString()));
                case RETURNED -> emit(new BossEvent(Type.CHARGE_RETURN, r.relay(), 0, r.player().toString()));
                case OVERLOAD -> {
                    emit(new BossEvent(Type.CHARGE_OVERLOAD, 0, 0, r.player().toString()));
                    // the overload never destroys the charge: it jumps to somebody else, or into a relay for a lone player
                    UUID other = null;
                    for (UUID id : eligible) {
                        if (!id.equals(r.player())) {
                            other = id;
                            break;
                        }
                    }
                    if (other != null) {
                        relay.pass(r.player(), other, now);
                        emit(new BossEvent(Type.CHARGE_PASS, 0, 0, other.toString()));
                    } else {
                        relay.throwToRelay(r.player(), attackCounter % RELAYS, now);
                        emit(BossEvent.of(Type.CHARGE_RELAY, attackCounter % RELAYS));
                    }
                }
                default -> { }
            }
        }
        return drain();
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onStart(long now) {
        relay.clear();
        decay.reset();
        planner.reset();
        segment = Segment.NONE;
        finale = Finale.NONE;
        windowEnds = -1;
        staggerUntil = -1;
        nextSegmentIndex = 0;
        calledPlayer = -1;
        realClone = -1;
        zoneStep = 0;
        flooded = List.of();
        nextAttack = now + 100;
        attackCounter = 0;
        emit(BossEvent.of(Type.MESSAGE, "dialogue.lewandivka.head.start"));
    }

    @Override
    protected void onPhase(int phase, long now) {
        switch (phase) {
            case 1 -> {
                nextDecay = now + party.deadlineTicks(120);
                nextRegrow = now + party.deadlineTicks(500);
                emit(BossEvent.of(Type.MESSAGE, "message.lewandivka.head.decay"));
            }
            case 2 -> {
                for (int i = 0; i < PLATFORMS; i++) {
                    while (decay.stage(i) != PlatformDecay.COLOURED) {
                        decay.restore(i);
                    }
                }
                emit(BossEvent.of(Type.PLATFORM_REGROW, -1));   // -1: the whole arena regrows
                nextSegmentAt = now + party.deadlineTicks(160);
                emit(BossEvent.of(Type.MESSAGE, "message.lewandivka.head.exam"));
            }
            case 3 -> beginFinale(now, true);
            default -> { }
        }
    }

    // ------------------------------------------------------------------ the fight

    @Override
    protected void onTick(long now, double health) {
        if (finale != Finale.NONE) {
            tickFinale(now);
            return;
        }
        if (now >= nextAttack) {
            attack(now);
        }
        if (phase() == 1) {
            tickDecay(now);
        } else if (phase() == 2) {
            tickExam(now);
        }
    }

    private void attack(long now) {
        int kind = switch (attackCounter % 4) {
            case 0 -> ATTACK_SWEEP;
            case 1 -> ATTACK_BEAM;
            case 2 -> ATTACK_SLAM;
            default -> ATTACK_WAVE;
        };
        attackCounter++;
        int warn = party.deadlineTicks(kind == ATTACK_BEAM ? 60 : 50);
        // one attack per cycle is aimed at the holder, so the charge has to move
        emit(BossEvent.of(Type.TELEGRAPH, kind, warn));
        schedule(now + warn, BossEvent.of(Type.ATTACK, kind, 0));
        int gap = phase() == 0 ? 160 : phase() == 1 ? 140 : 180;
        nextAttack = now + warn + party.deadlineTicks(jitter(gap, 40));
        if (phase() == 2 && segment != Segment.NONE) {
            nextAttack += party.deadlineTicks(60);          // the boss never competes with a segment for attention
        }
    }

    private void tickDecay(long now) {
        if (now >= nextDecay) {
            nextDecay = now + party.deadlineTicks(jitter(120, 60));
            int platform = decay.advance(rng);
            if (platform >= 0) {
                emit(BossEvent.of(Type.PLATFORM_DECAY, platform, decay.stage(platform)));
            }
        }
        if (now >= nextRegrow) {
            nextRegrow = now + party.deadlineTicks(500);
            int platform = decay.regrowOne();
            if (platform >= 0) {
                emit(BossEvent.of(Type.PLATFORM_REGROW, platform));
            }
        }
    }

    // ------------------------------------------------------------------ phase 2: the exam

    private void tickExam(long now) {
        if (segment == Segment.NONE) {
            if (now >= nextSegmentAt) {
                startSegment(now);
            }
            return;
        }
        switch (segment) {
            case GARAGE -> {
                if (liftsDone) {
                    endSegment(now, true);
                } else if (now >= segmentEnds) {
                    endSegment(now, false);
                }
            }
            case AQUAPARK -> {
                if (now >= zoneStepAt) {
                    if (zoneStep == 1) {
                        zoneStep = 2;
                        int flood = party.deadlineTicks(180);
                        zoneStepAt = now + flood;
                        for (Zone z : flooded) {
                            emit(BossEvent.of(Type.ZONE_FLOOD, z.ordinal(), flood));
                        }
                    } else {
                        emit(BossEvent.of(Type.ZONE_CLEAR));
                        zoneStep = 0;
                        flooded = List.of();
                        endSegment(now, true);
                    }
                }
            }
            case TICKET -> {
                if (now >= segmentEnds) {
                    emit(BossEvent.of(Type.TICKET_FAIL, calledPlayer, 0));
                    calledPlayer = -1;
                    endSegment(now, false);
                }
            }
            case CLONES -> {
                if (now >= nextHint) {
                    nextHint = now + 100;
                    emit(BossEvent.of(Type.REAL_BOSS_HINT, realClone));
                }
                if (now >= segmentEnds) {
                    emit(BossEvent.of(Type.CLONES_GONE));
                    realClone = -1;
                    endSegment(now, false);
                }
            }
            default -> { }
        }
    }

    private void startSegment(long now) {
        Segment[] order = {Segment.GARAGE, Segment.AQUAPARK, Segment.TICKET, Segment.CLONES};
        segment = order[nextSegmentIndex % order.length];
        nextSegmentIndex++;
        switch (segment) {
            case GARAGE -> {
                lifts = new SyncGroup(LIFTS, party.windowTicks(80));
                liftsDone = false;
                int total = party.deadlineTicks(600);
                segmentEnds = now + total;
                emit(BossEvent.of(Type.SEGMENT_START, segment.ordinal(), total));
                emit(BossEvent.of(Type.LIFTS, 1));
            }
            case AQUAPARK -> {
                flooded = new ArrayList<>(planner.next(2));
                zoneStep = 1;
                int warn = party.deadlineTicks(80);
                zoneStepAt = now + warn;
                segmentEnds = now + warn + party.deadlineTicks(260);
                emit(BossEvent.of(Type.SEGMENT_START, segment.ordinal(), warn + party.deadlineTicks(180)));
                for (Zone z : flooded) {
                    emit(BossEvent.of(Type.ZONE_WARN, z.ordinal(), warn));
                }
            }
            case TICKET -> {
                calledPlayer = rng.nextInt(Math.max(1, party.size()));
                int total = party.deadlineTicks(300);
                segmentEnds = now + total;
                emit(BossEvent.of(Type.SEGMENT_START, segment.ordinal(), total));
                emit(BossEvent.of(Type.TICKET_CALL, calledPlayer, total));
            }
            case CLONES -> {
                clones = Math.max(2, party.adds(3)) + 1;      // fakes plus the real one
                realClone = rng.nextInt(clones);
                int total = party.deadlineTicks(700);
                segmentEnds = now + total;
                nextHint = now + 60;
                emit(BossEvent.of(Type.SEGMENT_START, segment.ordinal(), total));
                emit(BossEvent.of(Type.CLONES, clones, total));
            }
            default -> { }
        }
    }

    private void endSegment(long now, boolean success) {
        Segment done = segment;
        segment = Segment.NONE;
        emit(BossEvent.of(Type.SEGMENT_DONE, done.ordinal(), success ? 1 : 0));
        if (done == Segment.GARAGE) {
            emit(BossEvent.of(Type.LIFTS, 0));
        }
        if (success) {
            staggerUntil = now + STAGGER_TICKS;
            emit(BossEvent.of(Type.VULNERABLE_START, STAGGER_TICKS));
            schedule(now + STAGGER_TICKS, BossEvent.of(Type.VULNERABLE_END));
        }
        nextSegmentAt = now + party.deadlineTicks(success ? 260 : 200);
    }

    /** A player stands on lift {@code index} (call every tick or every few ticks while somebody is on it). */
    public List<BossEvent> liftOccupied(int index, long now) {
        if (segment != Segment.GARAGE || lifts == null || liftsDone) {
            return List.of();
        }
        if (lifts.activate(index, now)) {
            liftsDone = true;
        }
        return drain();
    }

    public boolean liftLit(int index, long now) {
        return lifts != null && lifts.isLit(index, now);
    }

    /** The called player reached a validator platform. */
    public List<BossEvent> ticketValidated(int player, long now) {
        if (segment == Segment.TICKET && player == calledPlayer) {
            calledPlayer = -1;
            endSegment(now, true);
        }
        return drain();
    }

    /** A clone was hit. Hitting the real one ends the segment; a fake one pops. */
    public List<BossEvent> cloneHit(int index, long now) {
        if (segment != Segment.CLONES || index < 0 || index >= clones) {
            return List.of();
        }
        if (index == realClone) {
            emit(BossEvent.of(Type.CLONES_GONE));
            realClone = -1;
            endSegment(now, true);
        } else {
            emit(BossEvent.of(Type.CLONES_GONE, index, 1));
        }
        return drain();
    }

    // ------------------------------------------------------------------ phase 3: the final moment

    private void beginFinale(long now, boolean fresh) {
        cancelScheduled();
        if (segment != Segment.NONE) {
            if (segment == Segment.CLONES) {
                emit(BossEvent.of(Type.CLONES_GONE));
            }
            if (segment == Segment.AQUAPARK) {
                emit(BossEvent.of(Type.ZONE_CLEAR));
            }
            segment = Segment.NONE;
            flooded = List.of();
            zoneStep = 0;
            calledPlayer = -1;
            realClone = -1;
        }
        finale = Finale.SILENCE;
        windowEnds = -1;
        staggerUntil = -1;
        relay.clear();
        relay.freeze(now, SILENCE_TICKS + 120);
        if (fresh) {
            emit(BossEvent.of(Type.PLATFORM_REGROW, -1));
        }
        emit(BossEvent.of(Type.STEAL_CHARGE));
        emit(BossEvent.of(Type.MONOCHROME, 1));
        emit(BossEvent.of(Type.MESSAGE, "dialogue.lewandivka.head.better"));
        int catsAt = fresh ? SILENCE_TICKS : 20;
        if (fresh) {
            schedule(now + catsAt, BossEvent.of(Type.CATS_ENTER));
        }
        schedule(now + catsAt + 60, BossEvent.of(Type.CATS_PASS_CHARGE, 0));
        schedule(now + catsAt + 100, BossEvent.of(Type.CATS_PASS_CHARGE, 1));
        finaleWindow = party.vulnerableTicks(FINAL_WINDOW_BASE_TICKS);
        schedule(now + catsAt + 100, BossEvent.of(Type.FINAL_WINDOW, finaleWindow, 0));
        schedule(now + catsAt + 100, BossEvent.of(Type.MONOCHROME, 0));
        finaleCatsAt = now + catsAt;
        finaleOpensAt = now + catsAt + 100;
    }

    private long finaleCatsAt;
    private long finaleOpensAt;
    private int finaleWindow;

    private void tickFinale(long now) {
        if (finale == Finale.SILENCE && now >= finaleCatsAt) {
            finale = Finale.CATS;
        }
        if (finale == Finale.CATS && now >= finaleOpensAt) {
            finale = Finale.WINDOW;
            windowEnds = now + finaleWindow;
            relay.clear();                                  // the next tickCharge hands the charge to a player
        } else if (finale == Finale.WINDOW && now >= windowEnds) {
            // The Head takes it back; the cats repeat their trick quicker.
            emit(BossEvent.of(Type.VULNERABLE_END));
            beginFinale(now, false);
        }
    }

    @Override
    protected void onDefeat(long now) {
        cancelScheduled();
        relay.clear();
        finale = Finale.NONE;
        segment = Segment.NONE;
        emit(BossEvent.of(Type.MONOCHROME, 0));
    }

    @Override
    protected void onReset() {
        relay.clear();
        decay.reset();
        planner.reset();
        segment = Segment.NONE;
        finale = Finale.NONE;
        windowEnds = -1;
        staggerUntil = -1;
        flooded = List.of();
        zoneStep = 0;
        calledPlayer = -1;
        realClone = -1;
    }
}
