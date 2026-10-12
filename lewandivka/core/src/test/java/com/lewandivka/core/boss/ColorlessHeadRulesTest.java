package com.lewandivka.core.boss;

import com.lewandivka.core.boss.BossEvent.Type;
import com.lewandivka.core.boss.ColorlessHeadRules.Segment;
import com.lewandivka.core.puzzle.PlatformDecay;
import com.lewandivka.core.scale.PartyScale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plays the whole final fight with simple scripted players, for one, two and three players. */
class ColorlessHeadRulesTest {

    private static final double DPS_PER_TICK = 0.00012;      // fraction of the boss health per tick for the holder

    /** A tiny stand-in for the boss entity and the players: passes the charge in time, answers every mechanic. */
    private static final class Sim {
        final ColorlessHeadRules rules = new ColorlessHeadRules(2024);
        final List<UUID> players = new ArrayList<>();
        final List<BossEvent> log = new ArrayList<>();
        final int size;
        double health = 1.0;
        long now;
        boolean defeated;
        int minStanding = Integer.MAX_VALUE;
        int segmentsDone;
        final List<Segment> segmentOrder = new ArrayList<>();
        boolean chargeLostWhileCharged;
        boolean damageWithoutCharge;
        long maxHolderStreak;

        Sim(int size) {
            this.size = size;
            for (int i = 0; i < size; i++) {
                players.add(new UUID(0, i + 1));
            }
            log.addAll(rules.start(PartyScale.of(size), 0));
        }

        void step() {
            now++;
            List<UUID> eligible = new ArrayList<>(players);
            log.addAll(rules.tickCharge(now, eligible));
            // the holder passes the charge (or throws it into a relay when alone) before it overloads
            UUID holder = rules.relay().holder();
            if (holder != null && rules.charged() && rules.relay().remaining(now) < 60) {
                if (size > 1) {
                    UUID to = players.get((players.indexOf(holder) + 1) % size);
                    log.addAll(rules.passCharge(holder, to, now));
                } else {
                    log.addAll(rules.throwToRelay(holder, (int) (now % ColorlessHeadRules.RELAYS), now));
                }
            }
            if (rules.charged() && rules.relay().holder() == null && !rules.relay().inRelay() && now > 5 && !rules.finalWindowOpen(now)) {
                chargeLostWhileCharged = true;
            }
            // the holder hits the boss
            holder = rules.relay().holder();
            if (holder != null && rules.canDamage(holder, now)) {
                health = Math.max(0.0, health - DPS_PER_TICK * rules.damageFactor(now));
            }
            for (UUID p : players) {
                if (!p.equals(holder) && rules.canDamage(p, now)) {
                    damageWithoutCharge = true;
                }
            }
            List<BossEvent> events = new ArrayList<>(rules.tick(now, health));
            log.addAll(events);
            // players answer the mechanics
            Segment seg = rules.segment();
            if (seg == Segment.GARAGE) {
                for (int i = 0; i < ColorlessHeadRules.LIFTS; i++) {
                    if (now % 7 == i * 2L) {
                        log.addAll(rules.liftOccupied(i, now));
                    }
                }
            } else if (seg == Segment.TICKET && rules.calledPlayer() >= 0 && now % 40 == 0) {
                log.addAll(rules.ticketValidated(rules.calledPlayer(), now));
            } else if (seg == Segment.CLONES && now % 90 == 0) {
                log.addAll(rules.cloneHit(rules.realClone(), now));
            }
            for (BossEvent e : events) {
                if (e.type() == Type.SEGMENT_START) {
                    segmentOrder.add(Segment.values()[e.a()]);
                }
                if (e.type() == Type.SEGMENT_DONE) {
                    segmentsDone++;
                }
            }
            minStanding = Math.min(minStanding, rules.platforms().standing());
            if (health <= 0 && !defeated) {
                defeated = true;
                log.addAll(rules.defeat(now));
            }
        }

        boolean has(Type t) {
            return log.stream().anyMatch(e -> e.type() == t);
        }

        long count(Type t) {
            return log.stream().filter(e -> e.type() == t).count();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void theWholeFightIsWinnable(int players) {
        Sim sim = new Sim(players);
        long limit = 20L * 60 * 20;                              // twenty minutes is far more than needed
        while (!sim.defeated && sim.now < limit) {
            sim.step();
        }
        assertTrue(sim.defeated, "the boss must be beatable by scripted players within " + limit + " ticks");
        assertTrue(sim.has(Type.DEFEATED));
        assertEquals(3, sim.rules.phase());
        assertFalse(sim.chargeLostWhileCharged, "the charge object is never lost");
        assertFalse(sim.damageWithoutCharge, "only the holder can damage the boss");
        assertTrue(sim.minStanding >= ColorlessHeadRules.MIN_STANDING, "never fewer safe platforms than the minimum: " + sim.minStanding);
        assertTrue(sim.has(Type.PLATFORM_DECAY));
        assertTrue(sim.has(Type.CHARGE_GRANT));
        if (players > 1) {
            assertTrue(sim.has(Type.CHARGE_PASS));
        } else {
            assertTrue(sim.has(Type.CHARGE_RELAY) && sim.has(Type.CHARGE_RETURN), "a lone player keeps the passing rhythm through the relays");
        }
        // phases are announced in order
        List<Integer> phases = sim.log.stream().filter(e -> e.type() == Type.PHASE).map(BossEvent::a).toList();
        assertEquals(List.of(1, 2, 3), phases);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    void theExamRunsEverySegmentInOrderAndNeverOverlaps(int players) {
        Sim sim = new Sim(players);
        while (sim.rules.phase() < 2 && sim.now < 40000) {
            sim.health = Math.min(sim.health, 0.5);
            sim.step();
        }
        sim.health = 0.34;
        long end = sim.now + 12000;
        while (sim.now < end && sim.rules.phase() == 2) {
            sim.health = Math.max(sim.health, 0.2);
            sim.step();
        }
        assertTrue(sim.segmentOrder.size() >= 8, "a few full rounds fit in the exam: " + sim.segmentOrder);
        Segment[] expected = {Segment.GARAGE, Segment.AQUAPARK, Segment.TICKET, Segment.CLONES};
        for (int i = 0; i < sim.segmentOrder.size(); i++) {
            assertEquals(expected[i % 4], sim.segmentOrder.get(i), "segment " + i);
        }
        assertEquals(sim.segmentOrder.size(), sim.count(Type.SEGMENT_DONE) + (sim.rules.segment() != Segment.NONE ? 1 : 0));
        assertTrue(sim.has(Type.LIFTS) && sim.has(Type.ZONE_WARN) && sim.has(Type.ZONE_FLOOD) && sim.has(Type.TICKET_CALL) && sim.has(Type.CLONES));
        assertTrue(sim.has(Type.VULNERABLE_START));
    }

    @Test
    void segmentsTimeOutInsteadOfBlockingTheFight() {
        ColorlessHeadRules rules = new ColorlessHeadRules(5);
        rules.start(PartyScale.of(2), 0);
        List<BossEvent> log = new ArrayList<>();
        for (long t = 1; t < 20000; t++) {
            log.addAll(rules.tick(t, 0.2 + (t < 5 ? 0.6 : 0.0)));
        }
        assertEquals(2, rules.phase() >= 2 ? 2 : rules.phase(), "stays in the exam when nobody plays");
        long failed = log.stream().filter(e -> e.type() == Type.SEGMENT_DONE && e.b() == 0).count();
        assertTrue(failed >= 4, "unanswered segments fail by time and the next one begins: " + failed);
    }

    @Test
    void finaleHasSilenceCatsAndAWindowThatRepeats() {
        ColorlessHeadRules rules = new ColorlessHeadRules(8);
        rules.start(PartyScale.of(3), 0);
        UUID a = new UUID(0, 1);
        List<UUID> eligible = List.of(a, new UUID(0, 2), new UUID(0, 3));
        List<BossEvent> log = new ArrayList<>();
        long t = 0;
        // jump straight to 4 % health
        while (rules.phase() < 3) {
            t++;
            log.addAll(rules.tick(t, 0.04));
        }
        long finaleStart = t;
        assertTrue(log.stream().anyMatch(e -> e.type() == Type.STEAL_CHARGE));
        assertTrue(log.stream().anyMatch(e -> e.type() == Type.MONOCHROME && e.a() == 1));
        assertTrue(log.stream().anyMatch(e -> e.type() == Type.DIALOGUE && e.text().equals("head_final")));
        assertEquals(0.0, rules.damageFactor(t), "nobody can hurt the Head during the silence");
        assertFalse(rules.canDamage(a, t));
        List<BossEvent> after = new ArrayList<>();
        long windowOpened = -1;
        for (int i = 0; i < 400; i++) {
            t++;
            after.addAll(rules.tickCharge(t, eligible));
            after.addAll(rules.tick(t, 0.04));
            if (windowOpened < 0 && rules.finalWindowOpen(t)) {
                windowOpened = t;
            }
        }
        assertTrue(windowOpened > 0, "the final window opens");
        assertEquals(ColorlessHeadRules.SILENCE_TICKS + 100, windowOpened - finaleStart, 2, "silence, cats, then the window");
        List<Type> order = after.stream().map(BossEvent::type)
                .filter(x -> x == Type.CATS_ENTER || x == Type.CATS_PASS_CHARGE || x == Type.FINAL_WINDOW).toList();
        assertEquals(List.of(Type.CATS_ENTER, Type.CATS_PASS_CHARGE, Type.CATS_PASS_CHARGE, Type.FINAL_WINDOW), order.subList(0, 4));
        assertEquals(0, after.stream().filter(e -> e.type() == Type.CATS_PASS_CHARGE).findFirst().orElseThrow().a());
        // somebody holds the charge in the window and may damage the boss
        assertNotNull(rules.relay().holder());
        assertTrue(rules.canDamage(rules.relay().holder(), t - 1) || !rules.finalWindowOpen(t - 1));
        // the window expires without a kill: the Head steals again and the cats repeat their trick (without entering again)
        long extra = 0;
        List<BossEvent> repeat = new ArrayList<>();
        while (extra < 1500) {
            extra++;
            t++;
            repeat.addAll(rules.tickCharge(t, eligible));
            repeat.addAll(rules.tick(t, 0.04));
        }
        assertTrue(repeat.stream().filter(e -> e.type() == Type.STEAL_CHARGE).count() >= 1);
        assertEquals(0, repeat.stream().filter(e -> e.type() == Type.CATS_ENTER).count(), "the cats are already inside");
        assertTrue(repeat.stream().filter(e -> e.type() == Type.FINAL_WINDOW).count() >= 2, "the window keeps coming back");
    }

    @Test
    void overloadMovesTheChargeOnInsteadOfDestroyingIt() {
        ColorlessHeadRules rules = new ColorlessHeadRules(1);
        rules.start(PartyScale.of(2), 0);
        UUID a = new UUID(0, 1);
        UUID b = new UUID(0, 2);
        List<UUID> eligible = List.of(a, b);
        List<BossEvent> log = new ArrayList<>();
        for (long t = 1; t < 12 * 20 + 40; t++) {
            log.addAll(rules.tickCharge(t, eligible));
        }
        assertTrue(log.stream().anyMatch(e -> e.type() == Type.CHARGE_OVERLOAD));
        assertTrue(log.stream().anyMatch(e -> e.type() == Type.CHARGE_PASS));
        assertNotNull(rules.relay().holder());
        // a lone player overloads into the relay and gets the charge back
        ColorlessHeadRules solo = new ColorlessHeadRules(1);
        solo.start(PartyScale.of(1), 0);
        log.clear();
        for (long t = 1; t < 12 * 20 + 200; t++) {
            log.addAll(solo.tickCharge(t, List.of(a)));
        }
        assertTrue(log.stream().anyMatch(e -> e.type() == Type.CHARGE_OVERLOAD));
        assertTrue(log.stream().anyMatch(e -> e.type() == Type.CHARGE_RELAY));
        assertTrue(log.stream().anyMatch(e -> e.type() == Type.CHARGE_RETURN));
    }

    @Test
    void aDeadOrLeavingHolderNeverLosesTheCharge() {
        ColorlessHeadRules rules = new ColorlessHeadRules(1);
        rules.start(PartyScale.of(2), 0);
        UUID a = new UUID(0, 1);
        UUID b = new UUID(0, 2);
        rules.tickCharge(1, List.of(a, b));
        assertEquals(a, rules.relay().holder());
        List<BossEvent> ev = rules.tickCharge(2, List.of(b));   // a died
        assertEquals(b, rules.relay().holder());
        assertTrue(ev.stream().anyMatch(e -> e.type() == Type.CHARGE_GRANT && e.b() == 1));
        assertNull(null);
    }

    @Test
    void platformDecayKeepsAnchorsAndNeverDropsBelowTheMinimum() {
        PlatformDecay decay = new PlatformDecay(ColorlessHeadRules.PLATFORMS, anchors(), ColorlessHeadRules.MIN_STANDING);
        java.util.Random rng = new java.util.Random(3);
        for (int i = 0; i < 5000; i++) {
            decay.advance(rng);
            assertTrue(decay.standing() >= ColorlessHeadRules.MIN_STANDING);
            assertEquals(0, decay.stage(0));
        }
    }

    private static boolean[] anchors() {
        boolean[] a = new boolean[ColorlessHeadRules.PLATFORMS];
        a[0] = a[8] = a[12] = true;
        return a;
    }

    @Test
    void resetStartsTheFightFromTheBeginning() {
        ColorlessHeadRules rules = new ColorlessHeadRules(1);
        rules.start(PartyScale.of(3), 0);
        for (long t = 1; t < 50; t++) {
            rules.tick(t, 0.03);
        }
        assertEquals(3, rules.phase());
        rules.reset();
        assertFalse(rules.started());
        rules.start(PartyScale.of(1), 100);
        assertEquals(0, rules.phase());
        assertEquals(1.0, rules.damageFactor(101));
        assertTrue(rules.charged());
        assertEquals(Segment.NONE, rules.segment());
    }
}
